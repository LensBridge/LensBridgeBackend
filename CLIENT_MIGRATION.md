# Client migration: `/api/refresh-musallahboard` now requires authentication

**Applies to:** the MusallahBoard kiosk agent (Raspberry Pi) and anything else that opens the
board refresh stream.
**Breaking:** yes. An existing client connects and then sits silent; after ~15 s the server
closes it with `4008 auth_timeout`. It will never receive a refresh again.

---

## 1. What changed, in one paragraph

The refresh stream used to accept `ws://host/api/refresh-musallahboard?deviceId=<uuid>` and
treat the presence of a known, unrevoked device id as sufficient. A device id is an
identifier, not a secret — it is in the board's own URL, on the physical screen, and in
`GET /api/admin/board/devices` — so anyone who had it could take a kiosk's place on the
channel and read its pushes. The endpoint now runs the same Ed25519 challenge–response the
agent command channel (`/api/agent/ws`) already used. **The `?deviceId=` query parameter is
ignored entirely**; the device a connection speaks for is decided by a signature the server
verifies against the public key stored on that device's enrollment row.

No new credential is issued. The agent already holds the Ed25519 private key whose public
half it registered at `POST /api/agent/enroll`. That is the credential. If your device
enrolled successfully, you have everything you need.

---

## 2. The handshake

```
client                                          server
  │  WS upgrade  GET /api/refresh-musallahboard    │
  ├───────────────────────────────────────────────►│
  │                                                │
  │◄──────────────  hello  (seq 1) ────────────────┤   32-byte challenge
  │                                                │
  ├──────────────►  auth   (seq 1) ────────────────┤   Ed25519 signature
  │                                                │
  │◄──────────────  auth_ok (seq 2) ───────────────┤   stream is now live
  │                                                │
  │◄──────────────  refresh (seq 3..) ─────────────┤   0..n, indefinitely
```

Every frame in both directions is a single JSON text frame. Binary frames are not used.

### 2.1 Connect

```
ws://<host>/api/refresh-musallahboard          (wss:// in production)
```

- Query parameters are ignored. Keep sending `?deviceId=` if it is convenient for your logs;
  it grants nothing.
- **Origin header:** the endpoint restricts browser origins to the configured frontend and
  board base URLs. Send **no** `Origin` header at all (the default for Go's
  `gorilla/websocket` and `nhooyr.io/websocket`, and for Python `websockets`) — Spring allows
  originless upgrades. If your HTTP stack forces one, it must exactly match
  `musallahboard.baseurl` or `frontend.baseurl`, or the upgrade fails with HTTP 403 before
  any WebSocket frame is exchanged.
- No `Authorization` header is required or read. Do not send the enrollment token.

### 2.2 `hello` (server → client, always the first frame)

```json
{
  "type": "hello",
  "seq": 1,
  "sessionId": "0d0a2e6a-...-a1b2",
  "challenge": "5Xy2...c9Q",
  "serverTime": 1774483200123
}
```

| field        | type        | meaning                                                     |
|--------------|-------------|-------------------------------------------------------------|
| `sessionId`  | UUID string | Server-issued. Echo it on every frame you send.              |
| `challenge`  | string      | Base64 (**standard alphabet, no padding**) of 32 random bytes. Opaque — do **not** decode it; sign the string exactly as received. |
| `serverTime` | int64       | Server clock, Unix milliseconds. Use it to detect your own clock skew. |

`sessionId` and `challenge` are fresh per connection. Nothing from a previous connection is
reusable.

### 2.3 `auth` (client → server)

Send this as soon as `hello` arrives. You have `musallahboard.stream.authTimeoutMs`
(default **15 000 ms**) from connection open. The deadline is enforced by a sweep every
`musallahboard.stream.authReaperIntervalMs` (default 5 000 ms), so in the worst case a silent
connection survives about 20 s — treat 15 s as the budget, not 20.

```json
{
  "type": "auth",
  "seq": 1,
  "sessionId": "0d0a2e6a-...-a1b2",
  "deviceId": "9f14c0b1-...-77de",
  "timestamp": 1774483200456,
  "signature": "MEUCIQ...=="
}
```

| field       | type        | rule                                                                    |
|-------------|-------------|-------------------------------------------------------------------------|
| `type`      | string      | Must be `"auth"`. It is the JSON type discriminator; the subtype set is closed and `"auth"` is currently the only member. Anything else is `4004`. |
| `seq`       | int64       | Must be `1` on your first frame, strictly increasing thereafter.        |
| `sessionId` | UUID string | Exactly the `sessionId` from `hello`. A mismatch is `4001`.             |
| `deviceId`  | UUID string | The device id returned by `POST /api/agent/enroll`.                    |
| `timestamp` | int64       | Unix **milliseconds**, not seconds. Must be within ±5 min of server time. |
| `signature` | string      | Base64 (standard alphabet, padding optional) of the 64-byte raw Ed25519 signature — detached, **not** ASN.1-wrapped. |

#### The signed bytes

UTF-8 encoding of exactly this string, `\n` (LF, `0x0A`) separators, **no trailing newline**:

```
musallahboard-stream-auth-v1
<sessionId>
<challenge>
<deviceId>
<timestamp>
```

- UUIDs are lowercase, hyphenated, canonical 36-character form.
- `<challenge>` is the base64 **string** from `hello`, copied verbatim.
- `<timestamp>` is the decimal integer, no separators, and must equal the `timestamp` field
  in the frame byte for byte.

The version prefix is `musallahboard-stream-auth-v1`. It differs from the agent channel's
`musallahboard-auth-v1` on purpose: a signature made for one channel must not verify on the
other. **Do not reuse your agent-channel signing helper without changing the prefix** — the
server will reject it with `4001` and it will look, unhelpfully, like a key problem.

Worked example (Go, `crypto/ed25519`):

```go
payload := fmt.Sprintf("musallahboard-stream-auth-v1\n%s\n%s\n%s\n%d",
    hello.SessionID, hello.Challenge, deviceID, ts)
sig := ed25519.Sign(privKey, []byte(payload))
frame := authFrame{
    Type: "auth", Seq: 1,
    SessionID: hello.SessionID, DeviceID: deviceID,
    Timestamp: ts,
    Signature: base64.StdEncoding.EncodeToString(sig),
}
```

Python (`cryptography`):

```python
payload = f"musallahboard-stream-auth-v1\n{session_id}\n{challenge}\n{device_id}\n{ts}".encode()
signature = base64.b64encode(private_key.sign(payload)).decode()
```

### 2.4 `auth_ok` (server → client)

```json
{ "type": "auth_ok", "seq": 2, "sessionId": "0d0a...", "deviceId": "9f14...", "serverTime": 1774483200460 }
```

The stream is live. Reset your reconnect backoff here, not on socket open — an open socket
that never authenticates is worthless and you would otherwise hammer the server on a
credential bug.

### 2.5 `refresh` (server → client, 0..n)

**Unchanged in every field an existing client reads.** `seq` and `sessionId` are additive.

```json
{
  "type": "refresh",
  "seq": 7,
  "sessionId": "0d0a...",
  "reason": "posters",
  "deviceId": "9f14...",
  "at": "2026-08-25T18:40:00.123Z"
}
```

| field      | notes                                                                     |
|------------|---------------------------------------------------------------------------|
| `reason`   | `posters` \| `events` \| `socials` \| `weekly-content` \| `config` \| `manual-refresh`. Treat unknown values as "refetch everything". |
| `deviceId` | Present only when the push is addressed to one board (`reason: "config"`). **Omitted, not null**, on a fleet-wide broadcast. |
| `at`       | ISO-8601 instant, UTC.                                                    |

Reaction is unchanged: refetch your payload in place. Do not reload the page.

### 2.6 Frame size

Anything you send longer than **4096 characters** is closed with `4004` before it is parsed.
A correct `auth` frame is roughly 300 characters, so this only bites a malformed client.

### 2.7 Client → server after `auth_ok`

Nothing. There are no heartbeats on this channel — heartbeats live on `/api/agent/ws`. Any
frame sent after `auth_ok`, including a second `auth`, closes the connection with `4003`.
Rely on WebSocket ping/pong for liveness.

---

## 3. Close codes

| code   | reason             | when                                                                                 | what to do |
|--------|--------------------|--------------------------------------------------------------------------------------|------------|
| `4001` | `auth_failed`      | Bad signature, unknown `deviceId`, revoked device, device has no key on file, timestamp skew > 5 min, `sessionId` mismatch, or malformed base64 signature. **Deliberately one code for all of these** — the server will not tell an unauthenticated caller which device ids exist. | Back off hard (minutes). Check clock sync and the signing prefix before assuming key loss. Do not fast-retry; it will not start working. |
| `4002` | `seq_violation`    | `seq` not strictly increasing.                                                        | Bug in your client. Reconnect and restart `seq` at 1. |
| `4003` | `bad_state`        | Any frame after `auth_ok`.                                                            | Bug in your client. |
| `4004` | `bad_frame`        | Unparseable JSON, a `type` this channel does not know, or a frame over 4096 characters. | Bug in your client. |
| `4008` | `auth_timeout`     | Connected but did not authenticate within the deadline (default 15 s, swept every 5 s). | Reconnect with normal backoff. |
| `1000` | `superseded`       | The same device authenticated on a newer connection.                                   | **Expected during your own reload.** If you did not open a second socket, someone else holds your private key — escalate. |
| `1008` | `device_revoked`   | An operator revoked this device while the session was live. Also closes your `/api/agent/ws` session. | Stop reconnecting. The device needs re-enrollment; retrying will only produce `4001`. |
| `1000` | (no reason) / `1006` | Ordinary close or transport drop.                                                    | Reconnect with jittered backoff. |

`4001` and `4008` are the two you will hit during migration. `4008` means the new frames are
not being sent at all; `4001` means they are being sent wrong.

Codes changed meaning. `4001` used to be `device_id_required`, `4002` `unknown_device`,
`4003` `device_revoked`. Anything matching on the old numbers must be updated.

### Reconnect behaviour

Keep full-jitter exponential backoff, capped at 60 s. Add two rules:

1. Restart the whole handshake on every reconnect. `sessionId`, `challenge` and the signature
   are single-connection values.
2. Treat `4001` as a distinct, slower class of failure. A misconfigured fleet retrying a
   rejected credential every second is a self-inflicted outage.

---

## 4. Migration order

The server rejects unauthenticated connections the moment it is deployed; there is no
grace period and no dual-accept mode.

1. Ship the agent change first, or accept a refresh-stream outage between the two deploys.
   Boards keep working during an outage — they fall back to the ten-minute poll. Screens go
   stale, they do not go blank.
2. Confirm each board's clock is disciplined (`chronyd`/`systemd-timesyncd`). The ±5 min skew
   cap is the most likely field failure, and it presents as `4001`.
3. Verify with `journalctl -u musallahboard-agent | grep auth_ok` (or your equivalent) rather
   than by watching for a socket to stay open.

## 5. Browser-hosted boards

The kiosk page at `MusallahBoard/frontend/src/api/refreshSocket.js` cannot complete this
handshake — a browser has no access to the device private key, and putting one there would
recreate the flaw in a new place. A page-hosted board needs the local agent to own the socket
and relay refresh events to the page over the loopback interface. That work belongs in the
board repo and is out of scope for this change; until it lands, a browser-only board falls
back to its poll.
