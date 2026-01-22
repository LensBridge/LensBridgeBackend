# Musallah Board API Specification

This document describes the public API endpoints consumed by the Musallah Board display hardware. These endpoints return data formatted specifically for rendering on the board screens.

> **Authentication**: These endpoints are **publicly accessible** (no authentication required) as they are consumed by the board display hardware.

**Base URL**: `/api/musallah`

---

## Data Models

### BoardLocation (Enum)
Used as query parameter to specify which board is requesting data:
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
- The API automatically filters content based on the requesting board:
  - `BROTHERS_MUSALLAH` → Returns content with audience `BROTHERS` or `BOTH`
  - `SISTERS_MUSALLAH` → Returns content with audience `SISTERS` or `BOTH`

### BoardConfig
Display settings for the board:
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
- `posterCycleInterval`: Milliseconds between poster transitions
- `location.method`: Prayer calculation method (for Adhan times)

### FrameDefinition (Poster Frame)
Poster data formatted for display:
```json
{
  "frameType": "POSTER",
  "durationInSeconds": 10,
  "frameConfig": {
    "type": "poster",
    "posterUrl": "https://media.lensbridge.tech/posters/poster-uuid.jpg",
    "title": "Poster Title"
  }
}
```
- `frameType`: Always `"POSTER"` for poster frames
- `durationInSeconds`: How long to display this poster
- `frameConfig.posterUrl`: Full URL to the poster image

### Event
Calendar event for display:
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
- Timestamps are in **milliseconds** since Unix epoch

### WeeklyContent
Weekly rotating content (verse, hadith, Jummah info):
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

### MusallahBoardPayload
Combined payload containing all data needed for the board:
```json
{
  "boardConfig": { ... },
  "posterFrames": [ ... ],
  "upcomingEvents": [ ... ],
  "weeklyContent": { ... }
}
```

---

## Endpoints

### Get Board Configuration
```
GET /config?board={boardLocation}
```
**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `board` | string | Yes | `BROTHERS_MUSALLAH` or `SISTERS_MUSALLAH` |

**Response**: `200 OK` - `BoardConfig`

**Response**: `404 Not Found` - If no config exists for this board

---

### Get Current Weekly Content
```
GET /weekly-content
```
Returns the weekly content for the **current week** (based on server date).

**Response**: `200 OK` - `WeeklyContent`

**Response**: `404 Not Found` - If no content is set for the current week

---

### Get Specific Week's Content
```
GET /weekly-content/{year}/{weekNumber}
```
**Path Parameters**:
| Parameter | Type | Description |
|-----------|------|-------------|
| `year` | int | Year (e.g., 2026) |
| `weekNumber` | int | ISO week number (1-53) |

**Response**: `200 OK` - `WeeklyContent`

**Response**: `404 Not Found` - If no content exists for this week

---

### Get Active Poster Frames
```
GET /posters?board={boardLocation}
```
Returns poster frames that are:
1. Currently active (today is within `startDate` to `endDate`)
2. Match the board's audience (or `BOTH`)

Sorted by `startDate` descending (newest posters first).

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `board` | string | Yes | `BROTHERS_MUSALLAH` or `SISTERS_MUSALLAH` |

**Response**: `200 OK` - Array of `FrameDefinition`

**Example Response**:
```json
[
  {
    "frameType": "POSTER",
    "durationInSeconds": 10,
    "frameConfig": {
      "type": "poster",
      "posterUrl": "https://media.lensbridge.tech/posters/poster-1.jpg",
      "title": "Friday Lecture"
    }
  },
  {
    "frameType": "POSTER",
    "durationInSeconds": 15,
    "frameConfig": {
      "type": "poster",
      "posterUrl": "https://media.lensbridge.tech/posters/poster-2.jpg",
      "title": "Community Event"
    }
  }
]
```

---

### Get Upcoming Events
```
GET /events?board={boardLocation}
```
Returns events that:
1. Have `startTimestamp >= now`
2. Match the board's audience (or `BOTH`)

Sorted by `startTimestamp` ascending (soonest events first).

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `board` | string | Yes | `BROTHERS_MUSALLAH` or `SISTERS_MUSALLAH` |

**Response**: `200 OK` - Array of `Event`

---

### Get Events in Time Range
```
GET /events/range?board={boardLocation}&start={startTimestamp}&end={endTimestamp}
```
Returns events that **overlap** with the given time range. Useful for "week at a glance" or daily schedule views.

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `board` | string | Yes | `BROTHERS_MUSALLAH` or `SISTERS_MUSALLAH` |
| `start` | long | Yes | Range start (milliseconds since epoch) |
| `end` | long | Yes | Range end (milliseconds since epoch) |

**Response**: `200 OK` - Array of `Event`

**Example**: Get events for the current week
```
GET /events/range?board=BROTHERS_MUSALLAH&start=1737072000000&end=1737676800000
```

---

### Get Full Board Payload (Recommended)
```
GET /payload?board={boardLocation}
```
Returns **all data** needed for the board in a single request. This is the **recommended endpoint** for initial load and periodic refresh as it reduces the number of API calls.

**Query Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| `board` | string | Yes | `BROTHERS_MUSALLAH` or `SISTERS_MUSALLAH` |

**Response**: `200 OK` - `MusallahBoardPayload`

**Example Response**:
```json
{
  "boardConfig": {
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
  },
  "posterFrames": [
    {
      "frameType": "POSTER",
      "durationInSeconds": 10,
      "frameConfig": {
        "type": "poster",
        "posterUrl": "https://media.lensbridge.tech/posters/poster-1.jpg",
        "title": "Friday Lecture"
      }
    }
  ],
  "upcomingEvents": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "Friday Prayer",
      "description": "Weekly Jummah prayer",
      "location": "Main Hall",
      "startTimestamp": 1737135000000,
      "endTimestamp": 1737138600000,
      "allDay": false,
      "audience": "BOTH"
    }
  ],
  "weeklyContent": {
    "weekId": {
      "year": 2026,
      "weekNumber": 3
    },
    "verse": {
      "arabic": "...",
      "transliteration": "...",
      "translation": "...",
      "reference": "..."
    },
    "hadith": null,
    "jummahPrayer": {
      "time": "13:30",
      "khatib": "Sheikh Ahmad",
      "location": "Main Musallah",
      "date": "2026-01-17"
    }
  }
}
```

**Note**: Fields may be `null` if no data is configured:
- `boardConfig`: `null` if no config exists for the board
- `weeklyContent`: `null` if no content is set for the current week
- `posterFrames`: Empty array `[]` if no active posters
- `upcomingEvents`: Empty array `[]` if no upcoming events

---

## Usage Recommendations

### Initial Load
On application start, call `/payload` to get all data at once:
```javascript
const response = await fetch('/api/musallah/payload?board=BROTHERS_MUSALLAH');
const payload = await response.json();
```

### Periodic Refresh
Use the `refreshAfterIshaaMinutes` from `boardConfig` to determine when to refresh data after Isha prayer. Call `/payload` again to get updated content.

### Poster Display Loop
1. Use `posterFrames` array from the payload
2. Display each poster for its `durationInSeconds`
3. Loop back to the first poster after displaying all
4. If `posterFrames` is empty, show a default frame

### Dark Mode
Use `boardConfig.darkModeAfterIsha` and `boardConfig.darkModeMinutesAfterIsha` along with Isha prayer time to enable dark mode automatically.

### Prayer Times
Use `boardConfig.location` (latitude, longitude, method) with a prayer time calculation library (e.g., Adhan.js) to calculate prayer times locally.
