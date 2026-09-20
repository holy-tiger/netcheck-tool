# Repository Guidelines

## Project Structure & Module Organization

- `app/` contains the Android client, written in Kotlin and Jetpack Compose.
  Core network probes are in `app/src/main/java/com/example/core/`, UI and state
  are in `ui/`, persistence uses Room under `data/`, and API upload code is in
  `network/`.
- `backend/` contains the FastAPI service: routes live in `backend/api/`, SQLite
  access in `database.py`, request models in `models.py`, and the web dashboard
  in `templates/index.html`.
- `app/src/test/` contains JVM/Robolectric and screenshot tests; instrumented
  tests are under `app/src/androidTest/`. Android resources are in
  `app/src/main/res/`.
- `server.js` is the Node development entry point; `diagnostic.db` is local
  SQLite data and should not be treated as source code.

## Build, Test, and Development Commands

- `./gradlew assembleDebug` — build the Android debug APK.
- `./gradlew test` — run JVM, Robolectric, and screenshot tests.
- `./gradlew connectedAndroidTest` — run instrumented tests on a connected
  device or emulator.
- `python -m uvicorn backend.main:app --reload --port 3000` — run the FastAPI
  backend locally (install `backend/requirements.txt` first).
- `npm run dev` — run the repository's Node development server when its
  integration behavior is needed.

## Coding Style & Naming Conventions

Use four-space indentation for Python and Kotlin, idiomatic Kotlin naming
(`PascalCase` types, `camelCase` functions/properties), and `snake_case` for
Python functions and JSON fields. Keep Android strings in resource XML, not
hard-coded in Compose UI. Preserve existing coroutine-based I/O boundaries and
use focused modules rather than adding logic to `MainActivity`.

## Testing Guidelines

Add unit or Robolectric tests under `app/src/test/` for deterministic logic and
instrumented tests under `app/src/androidTest/` for device behavior. Name tests
after the behavior under test, such as `NetworkEngineTest` or
`diagnosisUploadsReport`. Run `./gradlew test` before submitting; add or update
screenshot baselines when changing rendered UI.

## Commit & Pull Request Guidelines

Use short, imperative commit subjects with a conventional scope, matching the
existing history (for example, `feat: extend network diagnostic capabilities`).
Pull requests should describe user-visible and API changes, list validation
commands, identify configuration or database impacts, and include screenshots
for UI changes. Call out any required backend URL, schema, or migration step.

## Security & Configuration Tips

Do not commit credentials, private endpoints, generated APKs, or personal
diagnostic reports. Review target validation before expanding probe types, and
avoid logging secrets or unnecessary device identifiers. Configure the backend
database path with `SQLITE_DB_PATH` when local isolation is needed.
