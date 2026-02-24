# FocusFlow

Offline-first Pomodoro timer + tasks + history for Android (Kotlin, Jetpack Compose, Material 3). Includes a minimal FastAPI backend (local-only) to satisfy the API contract and provide a healthcheck endpoint.

## What’s included

### Android app (offline-first)
- **Timer**
  - Classic Pomodoro defaults: **25/5/15** with **long break after 4 work sessions** (configurable)
  - Custom sequence mode (work/break/long_break segments with durations)
  - Pause/resume with **max total paused minutes cap** (enforced in a pure rules engine)
  - End-of-segment behavior: `auto_advance`, `prompt`, `ask_each_time`
  - Optional linking of a work segment to a task
- **Tasks**
  - CRUD tasks
  - Pomodoro estimate + completed pomodoros tracking
  - Completed flag
- **History**
  - Stores one row per completed segment (Session)
  - Daily + weekly totals
  - **Streak**: counts a day if it has **>= 1 completed `work` session** (breaks do not count)
  - Simple chart placeholders
- **Settings** (DataStore)
  - Theme: `system` / `light` / `dark`
  - Notifications: sound/vibration toggles, silent notifications, respect DND
  - Max total paused minutes
  - Segment end behavior
  - Default timer plan selection
- **Notifications**
  - Notification channels created at app start
  - Timer transitions scheduled via AlarmManager/receiver
  - Silent mode and DND-friendly behavior (no sound/vibration when configured)
- **Export**
  - CSV export of all completed sessions
  - Share intent

### Backend (FastAPI)
- Minimal service with **GET `/health`** returning JSON:
  ```json
  {"status":"ok"}
  ```
- No auth (session strategy: **none**)
- Error responses are always JSON with shape:
  ```json
  {"error":{"code":"...","message":"...","details":{}}}
  ```

## Repo structure

- `android/` — Android app
- `backend/` — FastAPI backend (local demo)
- `CONTRACT.md` — API + data contract (source of truth)

## Quickstart

### Android

Prereqs:
- Android Studio (latest stable)
- JDK 17

Run:
1. Open `android/` in Android Studio.
2. Sync Gradle.
3. Run the **app** configuration on an emulator/device.

Tests:
- Unit tests: run from Android Studio or
  - `./gradlew test` (from `android/`)
- Instrumented tests (device/emulator required):
  - `./gradlew connectedAndroidTest` (from `android/`)

More details: see `android/README.md`.

### Backend

Prereqs:
- Python 3.11+

Run (from repo root):
1. Install deps (one common approach):
   - `python -m venv .venv`
   - `source .venv/bin/activate` (Windows: `.venv\\Scripts\\activate`)
   - `pip install -r backend/requirements.txt`
2. Start server:
   - `uvicorn backend.main:app --reload`
3. Healthcheck:
   - `curl http://127.0.0.1:8000/health`

Expected response:
```json
{"status":"ok"}
```

## Contract (source of truth)

This repo follows `CONTRACT.md` strictly.

### Endpoint
- `GET /health` (no auth)
  - 200 OK
  - Body: `{ "status": "ok" }`

### Canonical models (high-level)
- `HealthResponse`: `{ status: string }`
- `ErrorResponse`: `{ error: { code: string, message: string, details?: object } }`

Android local models (stored via Room/DataStore):
- `Task`
- `Session` (completed segments only; `duration_ms = end_epoch_ms - start_epoch_ms` and `> 0`)
- `TimerPlan` + `TimerSegment`
- `AppSettings`

## Architecture notes (Android)

- **UI:** Jetpack Compose + Material 3 + bottom navigation (Timer / Tasks / History / Settings)
- **State:** ViewModels per screen
- **Rules:** `TimerEngine` is a pure Kotlin component; pause cap and segment transitions are enforced here for unit-testability
- **Persistence:**
  - Room (SQLite) for tasks/sessions/timer plans
  - DataStore for app settings
  - Room schema includes migration path **v1 -> v2** (sessions adds `linked_task_id`)
- **Offline-first:** Core features do not require backend availability

## CSV export format

Export produces one row per completed Session with columns:

`id,type,start_epoch_ms,end_epoch_ms,duration_ms,linked_task_id,plan_label`

Segment `type` is always one of: `work`, `break`, `long_break`.

## CI

GitHub Actions workflow runs unit tests and builds a debug APK.

## License

TBD (add your preferred license).
