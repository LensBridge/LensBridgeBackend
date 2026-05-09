# MusallahBoard — Backend API Reference

## Data Model

### `IslamicQuote`
Shared type used for both Quranic verses and hadith. Each entry is self-describing via `kind`.

```json
{
  "kind": "VERSE" | "HADITH",
  "arabic": "string",
  "transliteration": "string",
  "translation": "string",
  "reference": "string"
}
```

### `WeeklyContent`
Stored once per ISO week. Does not carry an audience — content is shared across boards.

```json
{
  "weekId": { "year": 2026, "weekNumber": 19 },
  "quotes": [ IslamicQuote, ... ],
  "jummahPrayer": [
    { "prayerTime": "13:30", "khatib": "string", "location": "string" }
  ]
}
```

### `Event`
```json
{
  "id": "uuid",
  "name": "string",
  "description": "string",
  "location": "string",
  "startTimestamp": 1234567890000,
  "endTimestamp":   1234567890000,
  "allDay": false,
  "audience": "BROTHERS" | "SISTERS" | "BOTH"
}
```

### `Poster`
```json
{
  "id": "uuid",
  "title": "string",
  "image": "https://...",
  "duration": 15,
  "startDate": "2026-05-01",
  "endDate":   "2026-05-15",
  "audience": "BROTHERS" | "SISTERS" | "BOTH"
}
```

### `BoardConfig`
Full document stored per board location. Only returned on admin config endpoints.
`boardLocation` values: `BROTHERS_MUSALLAH`, `SISTERS_MUSALLAH`.

```json
{
  "boardLocation": "BROTHERS_MUSALLAH",
  "location": {
    "city": "string", "country": "string",
    "latitude": 0.0, "longitude": 0.0,
    "timezone": "America/Toronto", "method": 2
  },
  "posterCycleInterval": 15000,
  "refreshAfterIshaaMinutes": 30,
  "darkModeAfterIsha": true,
  "darkModeMinutesAfterIsha": 20,
  "enableScrollingMessage": true,
  "scrollingMessages": ["string"]
}
```

### `DisplayConfig` (payload only)
Subset of `BoardConfig` sent to the display board. Admin endpoints return the full `BoardConfig`.

```json
{
  "city": "string",
  "timezone": "America/Toronto",
  "posterCycleInterval": 15000,
  "darkModeAfterIsha": true,
  "darkModeMinutesAfterIsha": 20,
  "enableScrollingMessage": true,
  "scrollingMessages": ["string"]
}
```

---

## Frame Types

The `/payload` endpoint returns a `List<FrameDefinition>`. Each frame has:

```json
{
  "frameType": "poster" | "event_list" | "daily_schedule" | "next_prayer" | "jummah" | "islamic_quote",
  "durationInSeconds": 15,
  "slot": "PRIMARY" | "TICKER" | "SIDEBAR" | "OVERLAY",
  "priority": null,
  "frameConfig": { "type": "...", ...typeSpecificFields }
}
```

### `poster`
```json
{ "type": "poster", "posterUrl": "https://...", "title": "string" }
```

### `event_list`
```json
{
  "type": "event_list",
  "heading": "This Week",
  "events": [
    {
      "name": "string", "description": "string", "location": "string",
      "startTimestamp": 1234567890000, "endTimestamp": 1234567890000, "allDay": false
    }
  ]
}
```

### `daily_schedule`
Same shape as `event_list`, heading = `"Today"`.

### `jummah`
```json
{
  "type": "jummah",
  "prayers": [
    { "prayerTime": "13:30", "khatib": "string", "location": "string" }
  ]
}
```

### `islamic_quote`
```json
{
  "type": "islamic_quote",
  "kind": "VERSE" | "HADITH",
  "arabic": "string", "transliteration": "string",
  "translation": "string", "reference": "string"
}
```

### `next_prayer` *(placeholder)*
```json
{ "type": "next_prayer", "locationCity": "string", "timezone": "string", "calculationMethod": "string" }
```

---

## Admin API — `POST /api/admin/board/**`
All endpoints require `ROOT` role. All mutations are audit-logged.

### Board Config

| Method | Path | Body | Notes |
|--------|------|------|-------|
| `GET` | `/configs` | — | All configs |
| `GET` | `/configs/{boardLocation}` | — | Single config |
| `PUT` | `/configs/{boardLocation}` | `BoardConfig` | Full replace; `boardLocation` set from path |
| `PATCH` | `/configs/{boardLocation}` | `UpdateBoardConfigRequest` | Partial update |

`boardLocation` path values: `BROTHERS_MUSALLAH`, `SISTERS_MUSALLAH`

### Weekly Content

| Method | Path | Body | Notes |
|--------|------|------|-------|
| `GET` | `/weekly-content` | — | All weeks |
| `GET` | `/weekly-content/year/{year}` | — | All weeks in a year |
| `GET` | `/weekly-content/{year}/{weekNumber}` | — | Single week |
| `PUT` | `/weekly-content/{year}/{weekNumber}` | `WeeklyContentRequest` | Upsert; null fields are left unchanged |
| `DELETE` | `/weekly-content/{year}/{weekNumber}` | — | — |

#### `WeeklyContentRequest`
```json
{
  "quotes": [
    { "kind": "VERSE",  "arabic": "...", "transliteration": "...", "translation": "...", "reference": "..." },
    { "kind": "HADITH", "arabic": "...", "transliteration": "...", "translation": "...", "reference": "..." }
  ],
  "jummahPrayer": [
    { "prayerTime": "13:30", "khatib": "Sheikh Name", "location": "Main Hall" }
  ]
}
```
All fields optional — send only what you want to update.

### Posters

| Method | Path | Body / Params | Notes |
|--------|------|------|-------|
| `GET` | `/posters` | `?board=` (optional) | All, or filtered by board audience |
| `GET` | `/posters/{posterId}` | — | Single poster |
| `POST` | `/posters` | `multipart/form-data` | Creates poster + uploads image |
| `PATCH` | `/posters/{posterId}` | `UpdatePosterRequest` | Metadata only |
| `PUT` | `/posters/{posterId}/image` | `multipart/form-data` | Image replacement |
| `DELETE` | `/posters/{posterId}` | — | — |

**POST form fields:** `title`, `duration` (int seconds), `startDate` (ISO date), `endDate` (ISO date), `audience`, `image` (file)

### Calendar Events

| Method | Path | Body / Params | Notes |
|--------|------|------|-------|
| `GET` | `/events` | `?board=` (optional) | All, or filtered by board audience |
| `GET` | `/events/{eventId}` | — | Single event |
| `POST` | `/events` | `CreateCalendarEventRequest` | — |
| `PATCH` | `/events/{eventId}` | `UpdateCalendarEventRequest` | Null fields unchanged |
| `DELETE` | `/events/{eventId}` | — | — |

#### `CreateCalendarEventRequest`
```json
{
  "name": "string",
  "description": "string",
  "location": "string",
  "startTimestamp": 1234567890000,
  "endTimestamp":   1234567890000,
  "allDay": false,
  "audience": "BROTHERS" | "SISTERS" | "BOTH"
}
```

### Signboard

| Method | Path | Notes |
|--------|------|-------|
| `POST` | `/refresh` | Broadcasts `REFRESH` over WebSocket to all connected boards |

---

## Display API — `GET /api/musallah/**`
No authentication required. Consumed by the board display hardware.

| Method | Path | Params | Notes |
|--------|------|--------|-------|
| `GET` | `/payload` | `?board=` | **Primary endpoint.** Full frame list + display config |
| `GET` | `/config` | `?board=` | Raw board config |
| `GET` | `/weekly-content` | — | Current week's content |
| `GET` | `/weekly-content/{year}/{weekNumber}` | — | Specific week |
| `GET` | `/posters` | `?board=` | Active poster `FrameDefinition`s |
| `GET` | `/events` | `?board=` | Upcoming events |
| `GET` | `/events/range` | `?board=`, `?start=`, `?end=` | Events overlapping time range (epoch ms) |

---

## Frontend Admin — Suggested Patterns

### Weekly Content Form
The admin form should send a `PUT /weekly-content/{year}/{weekNumber}` with the full
`WeeklyContentRequest`. Because the endpoint is an upsert and null fields are left unchanged,
the form can load the existing week first (`GET /weekly-content/{year}/{weekNumber}`) and
pre-populate, then send the full payload back on save.

```ts
interface WeeklyContentRequest {
  quotes?: IslamicQuote[];
  jummahPrayer?: JummahPrayer[];
}

interface IslamicQuote {
  kind: 'VERSE' | 'HADITH';
  arabic?: string;
  transliteration?: string;
  translation?: string;
  reference?: string;
}
```

The admin form should render the `quotes` list dynamically (each item has a `kind` selector),
rather than hardcoding separate "verse" and "hadith" fields. This makes adding new quote types
(e.g. `DUAA`) a frontend-only change with no backend work.

### Poster Filtering
The `?board=` query param on `GET /posters` replaces the old `/posters/by-board` sub-path.
To show all posters: `GET /posters`. To show only brothers' content: `GET /posters?board=BROTHERS_MUSALLAH`.

### Event Filtering
Same pattern: `GET /events` (all), `GET /events?board=BROTHERS_MUSALLAH` (filtered).

### Display Board Payload
Use `GET /payload?board=BROTHERS_MUSALLAH`. The response is:

```ts
interface MusallahBoardPayload {
  displayConfig: DisplayConfig;
  frames: FrameDefinition[];
}
```

Dispatch on `frame.frameType`, group by `frame.slot` for layout positioning.
See the frame type shapes above for each `frameConfig` structure.
