# FocusFlow (Android)

Android-only, offline-first Pomodoro + tasks + history app.

- UI: **Kotlin + Jetpack Compose + Material 3**
- Local persistence:
  - **Room (SQLite)** for Tasks, Sessions, Timer Plans (schema **v2**, includes **v1 → v2** migration for `sessions.linked_task_id`)
  - **DataStore** for `AppSettings`
- Timer rules are enforced in a **pure Kotlin** rules engine (`TimerEngine`) to keep behavior testable.
- Backend is optional for core features; the only defined API endpoint is `GET /health` returning `{"status":"ok"}` (see root `CONTRACT.md`).

## Open in Android Studio

1. Open Android Studio.
2. **File → Open…** and select the `android/` directory.
3. Wait for Gradle sync.
4. Run the **app** configuration on an emulator/device.

### Requirements
- Android Studio (latest stable)
- JDK 17

## Run tests

From the `android/` directory:

### Unit tests (JVM)
```bash
./gradlew test
```

Key unit tests:
- `android/app/src/test/java/com/focusflow/app/domain/timer/TimerEngineTest.kt`
  - Classic defaults **25/5/15** with long break after **4** work sessions
  - Custom sequence behavior
  - Pause/resume and **max total paused minutes cap**
  - End-of-segment behavior flags (`auto_advance`, `prompt`, `ask_each_time`)
- `android/app/src/test/java/com/focusflow/app/domain/history/HistoryCalculatorTest.kt`
  - Daily/weekly totals
  - **Streak** definition: a day counts if it has **>= 1 completed `work` session** (breaks do not count)

### Instrumented tests (device/emulator)
```bash
./gradlew connectedAndroidTest
```

Includes Room DAO tests:
- `android/app/src/androidTest/java/com/focusflow/app/data/db/DaoTests.kt`
  - In-memory DB CRUD checks
  - Basic migration test for **v1 → v2**

## Local data contract notes (Room/DataStore)

The app persists these contract models locally (see root `CONTRACT.md` for the full source-of-truth):

- **Task**
  - `id` (uuid), `title`, optional `notes`
  - `estimate_pomodoros` (>=0), `completed_pomodoros` (>=0)
  - `is_completed`, `created_at_epoch_ms`, `updated_at_epoch_ms`
- **Session** (completed segments only)
  - `type` is strictly one of: `work`, `break`, `long_break`
  - `duration_ms` must equal `end_epoch_ms - start_epoch_ms` and be `> 0`
  - optional `linked_task_id`, optional `plan_label`
- **AppSettings** (DataStore)
  - `theme`: `system` / `light` / `dark`
  - notification toggles: `sound_enabled`, `vibration_enabled`, `silent_notifications`, `respect_dnd`
  - timer behavior: `max_total_paused_minutes`, `segment_end_behavior`

## CSV export

Export produces **one row per completed Session** with exactly these columns:

`id,type,start_epoch_ms,end_epoch_ms,duration_ms,linked_task_id,plan_label`

`type` is always one of: `work`, `break`, `long_break`.

## Backend (optional)

The Android app does not require backend availability for core features.

If you run the backend locally, the only guaranteed endpoint is:
- `GET /health` → `200 OK` with body exactly:
  - `{"status":"ok"}`

See root `README.md` and `CONTRACT.md` for backend run instructions and the canonical error shape.
