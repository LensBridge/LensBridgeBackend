# Client migration — `UploadDto` no longer returns raw storage keys

**Affects:** `C:\Code\utmmsa-pwa` (and any other consumer of the generated client)
**Breaking:** yes — the *values* of `fileUrl` and `thumbnailUrl` change meaning, and `uploadedBy` /
`instagramHandle` can now be `null` where they previously were not.

## Endpoints affected

Every endpoint returning `UploadDto` or `Page<UploadDto>`:

| Method | Path | Handler |
| --- | --- | --- |
| GET | `/api/user/uploads` | `UserController.getUserUploads` |
| GET | `/api/upload/{uploadId}` | `FileUploadController.getUploadById` |
| GET | `/api/upload/event/{eventId}` | `FileUploadController.getUploadsByEvent` |

`AdminUploadDto` (the `/api/admin/uploads/**` family) is **unchanged** — it already returned presigned
URLs in `secureUrl` / `thumbnailUrl`.

## What changed

### 1. `fileUrl` and `thumbnailUrl` are now absolute presigned URLs

Before — the verbatim R2 object key:

```json
{ "fileUrl": "images/e5c6f37c-845d-4252-9a2f-16e5e68eee96",
  "thumbnailUrl": "thumbnails/e5c6f37c-845d-4252-9a2f-16e5e68eee96" }
```

After — a scoped, signed, expiring URL on the media domain:

```json
{ "fileUrl": "https://media.lensbridge.tech/images/e5c6f37c-…?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Expires=900&X-Amz-Signature=…",
  "thumbnailUrl": "https://media.lensbridge.tech/thumbnails/e5c6f37c-…?…&X-Amz-Signature=…",
  "urlExpiresAt": "2026-08-25T18:45:00Z" }
```

**Why:** `media.lensbridge.tech` is a public custom domain in front of the bucket (see
`PosterService.java:100,181`, which serves `publicUrl + "/" + objectKey` unsigned). Returning the key
was therefore equivalent to returning the media itself, to anyone who obtained the JSON, forever, with
no approval check. The presign step is also where the approval gate lives —
`R2StorageService.getSecureUrl` refuses unapproved content to unprivileged callers.

**Frontend change required:** stop concatenating. Anywhere the PWA does

```ts
const src = `${MEDIA_BASE_URL}/${upload.fileUrl}`;      // ← delete this
```

use the value directly:

```ts
const src = upload.fileUrl;                              // already absolute
```

Grep the PWA for the media base-URL constant (`VITE_MEDIA_URL` / `media.lensbridge.tech` /
any `${...}/${upload.fileUrl}` template) and remove the join at every call site, including
`<img src>`, `<video src>`, any `srcset`, download links, and share/copy-link actions.

### 2. Those URLs expire — do not cache them

The signature is valid for `cloudflare.r2.url-expiration-minutes` (default **15 minutes**). A new
field, `urlExpiresAt` (ISO-8601 instant, nullable), tells you exactly when.

Consequences for the PWA:

- **Do not persist `fileUrl` in localStorage / IndexedDB / a service-worker cache / a Redux store that
  outlives the session.** A rehydrated URL past `urlExpiresAt` returns 403 and renders a broken image.
- **Do not put the URL in a React `key`, a cache key, or a dedup key** — it changes on every fetch of
  the same upload. Use `uuid` for identity.
- Refetch the upload (or the page of uploads) when `urlExpiresAt` passes and the media is still on
  screen. A long-lived gallery tab is the realistic failure case; a simple approach is to refetch the
  current page when `Date.now() > urlExpiresAt - 60_000` on tab focus.
- Image `onError` handlers should retry once *after refetching the DTO*, not by retrying the same URL.

### 3. `fileUrl` / `thumbnailUrl` can be `null`

`null` means "you may not view these bytes" — the upload is unapproved and you are neither its
uploader nor a moderator — or the storage backend failed to sign. Previously the key was always
present regardless of approval state.

**Frontend change required:** render a placeholder / "Pending review" state when `fileUrl` is null.
`upload.approved === false` is the expected companion signal. Do not `String(upload.fileUrl)` or feed
`null` into an `<img src>`.

`thumbnailUrl` still falls back to the full-size URL when an upload has no thumbnail, so a non-null
`fileUrl` implies a non-null `thumbnailUrl`.

### 4. `uploadedBy` and `instagramHandle` are `null` on anonymous uploads

When `anon === true`, both fields are nulled unless the caller is the uploader themselves or holds
the `media:upload:read` permission. Previously `uploadedBy` was serialized unconditionally, which
silently voided the anonymity the uploader was promised at submit time — a `ROLE_USER` listing an
event's uploads could deanonymise every anonymous submission in it.

**Frontend change required:** anywhere the PWA reads `upload.uploadedBy` or `upload.instagramHandle`
for display, attribution, "is this mine?" checks, or grouping, guard for `null`. Note that on
`/api/user/uploads` the caller *is* the uploader, so those fields stay populated there — the change
bites on `/api/upload/event/{eventId}`.

## Type / generated-client updates

`UploadDto` gains one optional field:

```ts
urlExpiresAt?: string;   // ISO-8601 instant; null when no URLs were issued
```

and `fileUrl`, `thumbnailUrl`, `uploadedBy`, `instagramHandle` must all be treated as nullable.
Regenerate the client from the updated `openapi.yaml` at the repo root.

## Suggested rollout

1. Ship the frontend change that *tolerates* absolute URLs (`fileUrl.startsWith('http') ? fileUrl :
   join(base, fileUrl)`) and null media — this is compatible with both old and new backends.
2. Deploy the backend.
3. Delete the compatibility branch and the media base-URL constant.

Step 1 is optional if backend and PWA deploy together; if they do not, skip it and you will serve
`https://media.lensbridge.tech/https://media.lensbridge.tech/...` for the duration of the skew.
