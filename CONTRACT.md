# FocusFlow Contract

This document is the **source-of-truth contract artifact** for FocusFlow.

- Product: **Android-first, offline-first** (backend optional for core functionality)
- Session strategy: **none** (no login, no cookies, no JWT)
- All endpoint paths, JSON fields, and invariants here must be used consistently across clients and server.

## API

### Base
- Local dev default: `http://127.0.0.1:8000`
- Content-Type: `application/json`

### Healthcheck

`GET /health`
- Auth: **not required**
- Success:
  - Status: `200 OK`
  - Body (must be exactly):

```json
{"status":"ok"}
```

#### Response model
- `HealthResponse`

```json
{
  "status": "ok"
}
```

### Error model (canonical)

All backend errors must return JSON with a stable shape (no HTML error pages):

- `ErrorResponse`

```json
{
  "error": {
    "code": "bad_request",
    "message": "Human-readable message",
    "details": {
      "any": "optional structured details"
    }
  }
}
```

- `ErrorObject.code`: stable machine-readable code (examples: `bad_request`, `not_found`, `internal`)
- `ErrorObject.message`: human-readable message
- `ErrorObject.details`: optional object

## Data models

This section defines models used by the backend (currently minimal) and the Android app’s local persistence.

### HealthResponse
Response model for `/health`.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `status` | string | yes | Always `"ok"` on success |

### ErrorResponse
Canonical error response shape.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `error` | object (`ErrorObject`) | yes | Error container |

### ErrorObject
Error details nested inside `ErrorResponse.error`.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `code` | string | yes | Stable code: e.g. `bad_request`, `not_found`, `internal` |
| `message` | string | yes | Human-readable error message |
| `details` | object | no | Optional structured details |

---

## Android local models (offline-first)

These models are persisted locally (Room/DataStore). They are included here so exported data, DB schema, and any future sync APIs remain consistent.

### Task
Android local task model for task list, estimates, and completion tracking.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | string (uuid) | yes | Primary identifier |
| `title` | string | yes | Task title |
| `notes` | string | no | Optional notes |
| `estimate_pomodoros` | integer | yes | Estimated pomodoros (>= 0) |
| `completed_pomodoros` | integer | yes | Completed pomodoros (>= 0) |
| `is_completed` | boolean | yes | Completion flag |
| `created_at_epoch_ms` | integer | yes | Creation time (epoch ms) |
| `updated_at_epoch_ms` | integer | yes | Last update (epoch ms) |

### Session
Android local record of a **completed** timer segment.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | string (uuid) | yes | Primary identifier |
| `type` | string enum | yes | One of: `work`, `break`, `long_break` |
| `start_epoch_ms` | integer | yes | Start time (epoch ms) |
| `end_epoch_ms` | integer | yes | End time (epoch ms) |
| `duration_ms` | integer | yes | Must equal `end_epoch_ms - start_epoch_ms` and be `> 0` |
| `linked_task_id` | string (uuid) | no | Optional Task link (typically for work sessions) |
| `plan_label` | string | no | Optional plan label for analytics/export |

### TimerPlan
Android local timer configuration.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `id` | string (uuid) | yes | Primary identifier |
| `name` | string | yes | User-visible name |
| `mode` | string enum | yes | `classic` or `custom_sequence` |
| `classic_work_minutes` | integer | no | Default 25; required when mode=`classic` |
| `classic_break_minutes` | integer | no | Default 5; required when mode=`classic` |
| `classic_long_break_minutes` | integer | no | Default 15; required when mode=`classic` |
| `classic_long_break_every_work_sessions` | integer | no | Default 4; required when mode=`classic` |
| `segments` | array<TimerSegment> | no | Required when mode=`custom_sequence` |

### TimerSegment
One segment in a custom sequence.

| Field | Type | Required | Notes |
|---|---|---:|---|
| `type` | string enum | yes | One of: `work`, `break`, `long_break` |
| `duration_minutes` | integer | yes | Minutes (>= 1) |

### AppSettings
Android local settings persisted in DataStore (not Room).

| Field | Type | Required | Notes |
|---|---|---:|---|
| `theme` | string enum | yes | `system`, `light`, `dark` |
| `sound_enabled` | boolean | yes | May use sound for notifications if allowed |
| `vibration_enabled` | boolean | yes | May vibrate if allowed |
| `silent_notifications` | boolean | yes | If true, no sound/vibration regardless of other toggles |
| `respect_dnd` | boolean | yes | If true, avoid sound/vibration while device is in DND |
| `max_total_paused_minutes` | integer | yes | Max allowed paused minutes per running segment (>= 0) |
| `segment_end_behavior` | string enum | yes | `auto_advance`, `prompt`, `ask_each_time` |
| `default_timer_plan_id` | string (uuid) | no | Selected TimerPlan id |

## Database contract (Android Room / SQLite)

Canonical DDL (schema **v2**) mirrored by Room entities.

```sql
/* Android Room (SQLite) canonical tables (mirrors Entities)
Schema version v2.

-- v1
CREATE TABLE tasks (
  id TEXT PRIMARY KEY NOT NULL,
  title TEXT NOT NULL,
  notes TEXT,
  estimate_pomodoros INTEGER NOT NULL DEFAULT 0,
  completed_pomodoros INTEGER NOT NULL DEFAULT 0,
  is_completed INTEGER NOT NULL DEFAULT 0,
  created_at_epoch_ms INTEGER NOT NULL,
  updated_at_epoch_ms INTEGER NOT NULL
);
CREATE INDEX idx_tasks_is_completed ON tasks(is_completed);

-- v1 sessions without linked_task_id; v2 adds linked_task_id and index
CREATE TABLE sessions (
  id TEXT PRIMARY KEY NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('work','break','long_break')),
  start_epoch_ms INTEGER NOT NULL,
  end_epoch_ms INTEGER NOT NULL,
  duration_ms INTEGER NOT NULL,
  linked_task_id TEXT NULL,
  plan_label TEXT NULL,
  FOREIGN KEY (linked_task_id) REFERENCES tasks(id) ON DELETE SET NULL
);
CREATE INDEX idx_sessions_start_epoch_ms ON sessions(start_epoch_ms);
CREATE INDEX idx_sessions_type ON sessions(type);
CREATE INDEX idx_sessions_linked_task_id ON sessions(linked_task_id);

-- Timer plans and custom segments
CREATE TABLE timer_plans (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  mode TEXT NOT NULL CHECK (mode IN ('classic','custom_sequence')),
  classic_work_minutes INTEGER,
  classic_break_minutes INTEGER,
  classic_long_break_minutes INTEGER,
  classic_long_break_every_work_sessions INTEGER
);
CREATE TABLE timer_plan_segments (
  id TEXT PRIMARY KEY NOT NULL,
  plan_id TEXT NOT NULL,
  position INTEGER NOT NULL,
  type TEXT NOT NULL CHECK (type IN ('work','break','long_break')),
  duration_minutes INTEGER NOT NULL,
  FOREIGN KEY (plan_id) REFERENCES timer_plans(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX uq_timer_plan_segments_plan_pos ON timer_plan_segments(plan_id, position);

-- Migration requirement
-- v1->v2: ALTER TABLE sessions ADD COLUMN linked_task_id TEXT NULL; plus index idx_sessions_linked_task_id.
*/
```

## CSV export contract

CSV export contains **one row per completed `Session`** with exactly these columns:

`id,type,start_epoch_ms,end_epoch_ms,duration_ms,linked_task_id,plan_label`

Constraints:
- `type` must be one of: `work`, `break`, `long_break`
- `duration_ms` must equal `end_epoch_ms - start_epoch_ms` and be `> 0`

## Invariants (must hold everywhere)

1. All clients use the same endpoint paths and JSON fields as defined here.
2. No renaming of routes, fields, or tables without updating this contract.
3. Backend must return JSON errors in a consistent shape.
4. Session strategy is **none**: no login endpoints, no cookies, no JWTs; all endpoints have `auth_required=false`.
5. `GET /health` must always return HTTP 200 and body exactly `{"status":"ok"}` when service is healthy.
6. Error responses must be JSON: `{"error":{"code":string,"message":string,"details"?:object}}`; no HTML error pages.
7. Timer segment types are strictly one of: `work`, `break`, `long_break` across DB, export CSV, and UI.
8. A `Session` record is persisted **only for completed segments**; `duration_ms` must equal `end_epoch_ms - start_epoch_ms` and must be `> 0`.
9. Streak day counts require **>= 1 completed `work` session** per local calendar day; breaks do not count toward streak.
10. Classic mode default durations are **25/5/15** with long break after **4** completed work sessions unless overridden via `TimerPlan`/`AppSettings`.
11. Pause cap: total paused time per active segment must not exceed `AppSettings.max_total_paused_minutes`; enforcement must be in the pure `TimerEngine`.
12. Offline-first: Android app must not require backend availability for core features (timer, tasks, history, settings, export).
13. Room schema version must be incremented on any schema change and must include at least one tested migration path (v1->v2).
14. CSV export must include one row per completed `Session` with columns: `id,type,start_epoch_ms,end_epoch_ms,duration_ms,linked_task_id,plan_label`.

## Proposed future sync endpoints (non-binding)

Not implemented in v1. Listed only to inform forward compatibility.

- `GET /sync/bootstrap`
- `POST /sync/push`
- `GET /sync/pull?since_epoch_ms=...`

If/when implemented, they must:
- remain no-auth unless contract is explicitly revised
- use the models and invariants defined above
- keep error responses in the canonical error shape
