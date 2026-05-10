# Controller and Service Rewrite Plan

## Summary

Rewrite controllers and services around clearer ownership boundaries. Controllers should stay thin and HTTP-focused; services should own business rules, persistence orchestration, authorization checks, audit logging, storage integration, and entity-to-DTO mapping.

The rewrite should proceed by vertical slice, keeping the project compiling after each slice.

## Target Structure

- Controllers handle routing, validation annotations, security annotations, request DTOs, response DTOs, and status codes only.
- Services own business rules, persistence orchestration, ownership checks, audit calls, storage calls, and mapping.
- Repositories remain data-only and expose query methods that match entity relationships.
- Errors flow through `ApiResponseException` and `GlobalExceptionHandler`; remove broad controller `try/catch` blocks.
- Use constructor injection everywhere; remove field `@Autowired`.
- Do not return JPA entities from public/user/admin media APIs. Board admin endpoints may temporarily return entities during the rewrite.

## Controllers

### `AuthController`

Auth endpoints only:

- `POST /api/auth/signup`
- `POST /api/auth/signin`
- `POST /api/auth/refresh-token`
- `POST /api/auth/logout`
- `POST /api/auth/logout-all-devices`
- `POST /api/auth/verify-email`
- `POST /api/auth/forgot-password`
- `GET /api/auth/validate-reset-token`
- `POST /api/auth/reset-password`
- `POST /api/auth/change-password`
- `GET|POST /api/auth/validate-token`

Delegate all auth flow logic to `AuthService`.

### `UserController`

Current-user endpoints only:

- `GET /api/user/profile`
- `PATCH /api/user/profile`
- `GET /api/user/stats`
- `GET /api/user/uploads`
- `DELETE /api/user/uploads/{uploadId}`

Delegate profile operations to `UserAccountService` and upload reads/deletes to `UploadQueryService` / `UploadService`.

### `MediaEventController`

Public read-only event API for uploads/gallery:

- `GET /api/events` -> list events accepting uploads.
- `GET /api/events/{eventId}` -> get one media event.

Delegate to `MediaEventService`.

### `UploadController`

Verified-user upload flow only:

- `GET /api/upload/limits`
- `GET /api/upload/{uploadId}`
- `POST /api/upload/{eventId}/direct/presign`
- `POST /api/upload/{eventId}/direct/complete`

Use request DTOs for presign and complete instead of many request params.

### `GalleryController`

Public gallery only:

- `GET /api/gallery`
- `GET /api/gallery/event/{eventId}`

Delegate to `UploadQueryService`.

### `AdminMediaController`

Admin moderation and media-event management:

- List uploads by filter: all, pending, approved, featured.
- Approve/unapprove uploads.
- Feature/unfeature uploads.
- Soft-delete uploads.
- Create/update media events.
- List users, verify users, add/remove roles.

This replaces the media/user portions of the current `AdminController`.

### `BoardAdminController`

Board content only:

- Board config CRUD.
- Weekly content CRUD.
- Poster CRUD and image replacement.
- `BoardEvent` CRUD.
- Board refresh trigger.

Do not mix media uploads or user administration into this controller.

### `DeviceAdminController`

Device fleet only:

- List/get devices.
- Issue enrollment token.
- Revoke device.
- Issue command.
- List recent commands.

Do not inject repositories directly; delegate to `DeviceService` and `DeviceCommandService`.

### `MusallahBoardController`

Board display read API only.

Preferred endpoint:

- `GET /api/musallah/payload?deviceId=...`

Keep granular config/content/poster/event endpoints only while the frontend still needs them.

### `AgentEnrollmentController`

Keep focused on unauthenticated enrollment token exchange:

- `POST /api/agent/enroll`

Delegate enrollment logic to `DeviceEnrollmentService`.

## Services

### `AuthService`

Owns login/session orchestration:

- `signIn(LoginRequest, RequestContext)`
- `refresh(TokenRefreshRequest, RequestContext)`
- `logout(rawRefreshToken)`
- `logoutAll(userId)`
- `validateCurrentUser(Authentication)`

It authenticates credentials, checks verification, records login attempts, creates JWTs, and issues/rotates refresh tokens.

### `UserAccountService`

Owns user lifecycle and role invariants:

- `createUser(SignupRequest, boolean sendEmail)`
- `verifyEmail(token)`
- `verifyDirectly(userId)`
- `updateProfile(userId, UpdateProfileRequest)`
- `changePassword(userId, ChangePasswordRequest)`
- `addRole(userId, Role)`
- `removeRole(userId, Role)`

It owns password hashing, verification-token lifecycle, email triggers, and role mutation rules.

### `RefreshTokenService`

Stores only token hashes:

- `issue(User, deviceInfo, ip)`
- `rotate(rawToken, deviceInfo, ip)`
- `revoke(rawToken)`
- `revokeAll(userId)`
- `pruneExpired()`

Return raw refresh tokens only at creation/rotation boundaries; persist hashes only.

### `MediaEventService`

Owns `MediaEvent` lifecycle and upload window policy:

- `create(request)`
- `update(id, request)`
- `get(id)`
- `listAll()`
- `listAcceptingUploads()`
- `isAcceptingUploads(id)`
- `refreshStatuses(now)`

Keep `BoardEvent` and `MediaEvent` separate for this rewrite.

### `UploadService`

Owns upload lifecycle mutations:

- `createDirectUpload(...)`
- `approve(id)`
- `unapprove(id)`
- `feature(id)`
- `unfeature(id)`
- `softDeleteByAdmin(id, adminId)`
- `softDeleteByOwner(id, userId)`

Use `User` and `MediaEvent` relationships directly, not raw `uploadedBy` or `eventId` UUID fields.

### `DirectUploadService`

Owns direct-upload orchestration:

- `presign(eventId, userId, role, request)`
- `complete(eventId, userId, request)`

It validates event acceptance, file type, size, daily limits, storage existence, and thumbnail trigger behavior.

### `UploadQueryService`

Owns upload reads and response mapping:

- `adminUploads(filter, pageable)`
- `userUploads(userId, pageable)`
- `publicGallery(pageable)`
- `publicGalleryByEvent(eventId, pageable)`
- `userStats(userId)`

It maps `Upload` to `AdminUploadDto`, `GalleryItemDto`, and user upload views.

### `BoardConfigService`

Owns board config operations:

- `get(deviceId)`
- `save(deviceId, request)`
- `patch(deviceId, request)`
- `list()`

### `WeeklyContentService`

Owns weekly board content:

- `get(year, week)`
- `getCurrent(zone)`
- `listByYear(year)`
- `save(year, week, request)`
- `delete(year, week)`

### `BoardEventService`

Owns `BoardEvent` lifecycle and display queries:

- `create(request)`
- `patch(id, request)`
- `delete(id)`
- `get(id)`
- `listAll()`
- `listForAudience(audience)`
- `listInRange(audience, start, end)`

Validate `endTime > startTime`.

### `PosterService`

Owns poster metadata and image storage:

- `create(request, image)`
- `patch(id, request)`
- `replaceImage(id, image)`
- `delete(id)`
- `listAll()`
- `listActiveForAudience(audience, now)`

Remove frame-building from this service; frame conversion belongs to board assembly.

### `BoardPayloadAssembler`

Single owner of display payload creation.

It should:

- Load device and config.
- Build `BoardContext`.
- Fetch active posters, board events, and weekly content.
- Delegate frame creation to transformers.
- Return `MusallahBoardPayload`.

### `DeviceService`

Owns device fleet operations:

- `list()`
- `get(id)`
- `revoke(id, adminEmail)`
- `recordHeartbeat(id, frame, ip)`

Keeps `DeviceAdminController` away from repositories.

### `DeviceCommandService`

Wrap or rename `CommandDispatcher`:

- `issue(deviceId, adminEmail, request)`
- `listRecent(deviceId)`
- `flushPending(deviceId, session)`
- `onAck(frame, session)`
- `onProgress(frame, session)`
- `onResult(frame, session)`

Keep command state transitions in one service.

### `AuditService`

Owns audit logging:

- `record(actor, action, targetType, targetId, requestContext)`

Controllers do not build `AuditEvent`; admin services audit after successful mutations.

## DTO and Wiring Rules

- Add compact request DTOs for upload presign, upload complete, media-event create/update, and admin upload filters.
- Add response mappers for:
  - `UserInfoResponse`
  - `AdminUploadDto`
  - `GalleryItemDto`
  - `DeviceSummary`
  - `CommandView`
  - board payload response types
- Add `CurrentUserService` or `RequestContextResolver` exposing:
  - current user id
  - email
  - roles
  - ip address
  - user agent
- Store storage object keys on entities; generate signed URLs in query/mapping services.
- Public gallery returns only approved, non-deleted uploads.
- Admin upload queries may include pending/unapproved uploads.
- Upload deletion is soft-delete via `deletedAt` and `deletedBy`.

## Rewrite Order

1. Fix repository methods to match current relationships (`Upload.uploadedBy`, `Upload.mediaEvent`, soft-delete filters).
2. Add request-context/current-user helper.
3. Split auth logic into `AuthService` and align `RefreshTokenService`.
4. Split media upload reads into `UploadQueryService`.
5. Rewrite upload mutation methods around soft-delete and relationships.
6. Replace `AdminController` with `AdminMediaController`.
7. Split board services into config, weekly content, board event, poster, and payload assembly.
8. Move device repository access behind `DeviceService`.
9. Remove entity responses from public/user/admin media controllers.
10. Delete old duplicated controller/service methods after replacements compile.

## Test Plan

- Controller tests verify routes, auth annotations, status codes, and DTO shape.
- Service tests cover:
  - signup, verification, password reset, token rotation
  - media event status and upload acceptance windows
  - presign/complete validation and daily limits
  - upload approve, feature, soft-delete, and owner checks
  - public gallery excluding unapproved/deleted uploads
  - board event range and audience filtering
  - poster date validation and image replacement
  - device revoke and command state transitions
- Mapper tests cover admin upload, gallery item, user info, command summary, and device summary.
- Keep compile green after each vertical slice.

## Assumptions

- `BoardEvent` and `MediaEvent` remain separate for this rewrite.
- Upload deletion is soft-delete, not storage deletion, for normal user/admin delete flows.
- Storage cleanup can be a later maintenance job.
- Board payload assembly remains the preferred pattern for board display output.
- Board admin entity responses are temporary and should be replaced after the core rewrite stabilizes.
