# Board Admin API Specification

This document describes the admin endpoints for managing Musallah Board content. These endpoints are used by the LensBridge Admin Console to manage posters, calendar events, board configuration, and weekly content.

> **Authentication**: All endpoints require `ROLE_ROOT` authorization. Include the JWT token in the `Authorization: Bearer <token>` header.

**Base URL**: `/api/admin/board`

---

## Data Models

### BoardLocation (Enum)
```
BROTHERS_MUSALLAH
SISTERS_MUSALLAH
```

### Audience (Enum)
```
BROTHERS
SISTERS
BOTH
```

### BoardConfig
```json
{
  "boardLocation": "BROTHERS_MUSALLAH",
  "location": {
    "city": "Toronto",
    "country": "Canada",
    "latitude": 43.6532,
    "longitude": -79.3832,
    "timezone": "America/Toronto",
    "method": 2
  },
  "posterCycleInterval": 10000,
  "refreshAfterIshaaMinutes": 30,
  "darkModeAfterIsha": true,
  "darkModeMinutesAfterIsha": 15,
  "enableScrollingMessage": true,
  "scrollingMessage": "Welcome to the Musallah"
}
```

### Poster
```json
{
  "id": "uuid",
  "title": "Poster Title",
  "image": "posters/poster-uuid.jpg",
  "duration": 10,
  "startDate": "2026-01-15",
  "endDate": "2026-02-15",
  "audience": "BOTH"
}
```
- `duration`: Display duration in seconds
- `startDate`: Date from which the poster is active (inclusive)
- `endDate`: Date until the poster is active (exclusive)

### Event
```json
{
  "id": "uuid",
  "name": "Event Name",
  "description": "Event description",
  "location": "Main Hall",
  "startTimestamp": 1737158400000,
  "endTimestamp": 1737162000000,
  "allDay": false,
  "audience": "BROTHERS"
}
```
- Timestamps are in milliseconds since Unix epoch

### WeeklyContent
```json
{
  "weekId": {
    "year": 2026,
    "weekNumber": 3
  },
  "verse": {
    "arabic": "بِسْمِ اللَّهِ الرَّحْمَٰنِ الرَّحِيمِ",
    "transliteration": "Bismillah ir-Rahman ir-Raheem",
    "translation": "In the name of Allah, the Most Gracious, the Most Merciful",
    "reference": "Surah Al-Fatiha 1:1"
  },
  "hadith": {
    "arabic": "...",
    "transliteration": "...",
    "translation": "...",
    "reference": "Sahih Bukhari"
  },
  "jummahPrayer": {
    "time": "13:30",
    "khatib": "Sheikh Ahmad",
    "location": "Main Musallah",
    "date": "2026-01-17"
  }
}
```

---

## Board Configuration Endpoints

### Get All Board Configs
```
GET /configs
```
**Response**: `200 OK` - Array of `BoardConfig`

### Get Board Config
```
GET /configs/{boardLocation}
```
**Path Parameters**:
- `boardLocation`: `BROTHERS_MUSALLAH` | `SISTERS_MUSALLAH`

**Response**: `200 OK` - `BoardConfig`

### Create/Replace Board Config
```
PUT /configs/{boardLocation}
```
**Path Parameters**:
- `boardLocation`: `BROTHERS_MUSALLAH` | `SISTERS_MUSALLAH`

**Request Body**: `BoardConfig` (full object)

**Response**: `200 OK` - Saved `BoardConfig`

### Update Board Config (Partial)
```
PATCH /configs/{boardLocation}
```
**Path Parameters**:
- `boardLocation`: `BROTHERS_MUSALLAH` | `SISTERS_MUSALLAH`

**Request Body**: (all fields optional)
```json
{
  "location": { ... },
  "posterCycleInterval": 15000,
  "refreshAfterIshaaMinutes": 45,
  "darkModeAfterIsha": true,
  "darkModeMinutesAfterIsha": 20,
  "enableScrollingMessage": false,
  "scrollingMessage": "New message"
}
```

**Response**: `200 OK` - Updated `BoardConfig`

---

## Weekly Content Endpoints

### Get All Weekly Content
```
GET /weekly-content
```
**Response**: `200 OK` - Array of `WeeklyContent`

### Get Weekly Content by Year
```
GET /weekly-content/year/{year}
```
**Path Parameters**:
- `year`: Integer (e.g., 2026)

**Response**: `200 OK` - Array of `WeeklyContent`

### Get Specific Week's Content
```
GET /weekly-content/{year}/{weekNumber}
```
**Path Parameters**:
- `year`: Integer (e.g., 2026)
- `weekNumber`: Integer 1-53

**Response**: `200 OK` - `WeeklyContent`

### Create/Update Weekly Content
```
PUT /weekly-content
```
**Request Body**:
```json
{
  "year": 2026,
  "weekNumber": 3,
  "verse": {
    "arabic": "...",
    "transliteration": "...",
    "translation": "...",
    "reference": "..."
  },
  "hadith": {
    "arabic": "...",
    "transliteration": "...",
    "translation": "...",
    "reference": "..."
  },
  "jummahPrayer": {
    "time": "13:30",
    "khatib": "Sheikh Ahmad",
    "location": "Main Musallah",
    "date": "2026-01-17"
  }
}
```
- `year` and `weekNumber` are required
- `verse`, `hadith`, and `jummahPrayer` are optional (partial updates supported)

**Response**: `200 OK` - Saved `WeeklyContent`

### Delete Weekly Content
```
DELETE /weekly-content/{year}/{weekNumber}
```
**Path Parameters**:
- `year`: Integer
- `weekNumber`: Integer 1-53

**Response**: `200 OK`
```json
{
  "message": "Weekly content deleted successfully"
}
```

---

## Poster Endpoints

### Get All Posters
```
GET /posters
```
**Response**: `200 OK` - Array of `Poster` (sorted by startDate descending, newest first)

### Get Posters by Board
```
GET /posters/by-board?board={boardLocation}
```
**Query Parameters**:
- `board`: `BROTHERS_MUSALLAH` | `SISTERS_MUSALLAH`

**Response**: `200 OK` - Array of `Poster` matching the board's audience or `BOTH`

### Get Single Poster
```
GET /posters/{posterId}
```
**Path Parameters**:
- `posterId`: UUID

**Response**: `200 OK` - `Poster`

### Create Poster
```
POST /posters
Content-Type: multipart/form-data
```
**Form Data**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `title` | string | Yes | Poster title |
| `duration` | int | Yes | Display duration in seconds |
| `startDate` | string | Yes | ISO date (YYYY-MM-DD) |
| `endDate` | string | Yes | ISO date (YYYY-MM-DD) |
| `audience` | string | Yes | `BROTHERS`, `SISTERS`, or `BOTH` |
| `image` | file | Yes | Image file (max 10MB) |

**Response**: `201 Created` - Created `Poster`

### Update Poster Metadata
```
PATCH /posters/{posterId}
```
**Path Parameters**:
- `posterId`: UUID

**Request Body**: (all fields optional)
```json
{
  "title": "Updated Title",
  "duration": 15,
  "startDate": "2026-01-20",
  "endDate": "2026-02-20",
  "audience": "SISTERS"
}
```

**Response**: `200 OK` - Updated `Poster`

### Update Poster Image
```
PUT /posters/{posterId}/image
Content-Type: multipart/form-data
```
**Path Parameters**:
- `posterId`: UUID

**Form Data**:
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `image` | file | Yes | New image file (max 10MB) |

**Response**: `200 OK` - Updated `Poster`

### Delete Poster
```
DELETE /posters/{posterId}
```
**Path Parameters**:
- `posterId`: UUID

**Response**: `200 OK`
```json
{
  "message": "Poster deleted successfully"
}
```

---

## Calendar Event Endpoints

### Get All Events
```
GET /events
```
**Response**: `200 OK` - Array of `Event` (sorted by startTimestamp ascending)

### Get Events by Board
```
GET /events/by-board?board={boardLocation}
```
**Query Parameters**:
- `board`: `BROTHERS_MUSALLAH` | `SISTERS_MUSALLAH`

**Response**: `200 OK` - Array of `Event` matching the board's audience or `BOTH`

### Get Single Event
```
GET /events/{eventId}
```
**Path Parameters**:
- `eventId`: UUID

**Response**: `200 OK` - `Event`

### Create Event
```
POST /events
```
**Request Body**:
```json
{
  "name": "Event Name",
  "description": "Event description",
  "location": "Main Hall",
  "startTimestamp": 1737158400000,
  "endTimestamp": 1737162000000,
  "allDay": false,
  "audience": "BOTH"
}
```
- `name` and `audience` are required
- Timestamps are in milliseconds since Unix epoch

**Response**: `201 Created` - Created `Event`

### Update Event
```
PATCH /events/{eventId}
```
**Path Parameters**:
- `eventId`: UUID

**Request Body**: (all fields optional)
```json
{
  "name": "Updated Name",
  "description": "Updated description",
  "location": "New Location",
  "startTimestamp": 1737244800000,
  "endTimestamp": 1737248400000,
  "allDay": true,
  "audience": "SISTERS"
}
```

**Response**: `200 OK` - Updated `Event`

### Delete Event
```
DELETE /events/{eventId}
```
**Path Parameters**:
- `eventId`: UUID

**Response**: `200 OK`
```json
{
  "message": "Calendar event deleted successfully"
}
```

---

## Error Responses

### 400 Bad Request
```json
{
  "error": "Validation error message"
}
```

### 401 Unauthorized
Missing or invalid JWT token.

### 403 Forbidden
User does not have `ROLE_ROOT`.

### 404 Not Found
```json
{
  "error": "Resource not found with id: ..."
}
```

### 413 Payload Too Large
```json
{
  "error": "Poster image must be less than 10MB"
}
```
