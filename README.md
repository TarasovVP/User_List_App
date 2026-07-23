# User List App

User List App is an offline-first, read-only Android directory backed by the DummyJSON users API. Remote profiles stay immutable; favorites and personal notes are local user-owned data.

## Features

- User list with local case-insensitive search, A–Z/Z–A sorting, favorites filter, manual refresh, and pull-to-refresh
- Cached offline content with distinct initial-loading, refresh, empty, and error behavior
- User details with accessible avatar fallback, favorite toggle, and explicitly saved local notes
- System/light/dark theme selection and persistent background-sync setting
- Unique network-constrained daily WorkManager synchronization with visible work state and last-success time
- Transactional Room refresh that preserves notes and favorites, including a real version 1 → 2 migration

## Screenshots

Runtime screenshots require launching the application on an emulator or physical device.

| Users | User details | Settings |
|---|---|---|
| _Screenshot placeholder_ | _Screenshot placeholder_ | _Screenshot placeholder_ |

## Architecture

The app is a single Gradle application module with package boundaries. Compose has no data-source access; all screens use hoisted state and lifecycle-aware `StateFlow` collection.

```mermaid
flowchart LR
    UI[Compose UI] --> VM[ViewModels]
    VM --> UC[Use cases]
    UC --> RI[Repository interfaces]
    RI --> R[Repository implementations]
    R --> API[Retrofit / DummyJSON]
    R --> DB[(Room)]
    DB --> R
    R --> UC
    DS[Preferences DataStore] --> VM
    WM[WorkManager] --> UC
```

Room is the single source of truth for displayed users. A refresh maps the limited remote DTO into entities and updates the snapshot in a transaction. Stale remote users are removed only when they have no local favorite or note. Preferences DataStore persists theme, background-sync enablement, and the last successful sync timestamp.

## Technology

Kotlin, Coroutines/Flow, Jetpack Compose Material 3, Navigation Compose, Hilt, Retrofit/OkHttp, Kotlin Serialization, Coil, Room, Preferences DataStore, WorkManager, JUnit, coroutine test, Turbine, AndroidX Test, and Compose UI test.

## Project structure

- `core/common`: typed results and application errors
- `domain`: models, repository contracts, and use cases
- `data/remote`: constrained API DTOs and Retrofit source
- `data/local`: Room entities, DAO, database, migration, transactional source
- `data/preferences`: DataStore settings repository
- `data/repository`: offline-first repository and mappings
- `feature`: list, details, and settings UI/ViewModels
- `worker`: periodic sync worker and scheduler
- `di`: dependency graph
- `docs`: AI-assisted development and performance review records

## Build and run

Requirements: JDK 17 and Android SDK 37. Open the repository in Android Studio or run:

```bash
./gradlew assembleDebug
```

Install `app/build/outputs/apk/debug/app-debug.apk`, then launch while online for the initial refresh. Cached profiles, favorites, and notes remain usable offline.

## Testing

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew compileDebugAndroidTestKotlin
./gradlew connectedDebugAndroidTest
```

The connected test command requires a running emulator or attached device. Runtime performance profiling and visual review also require an Android runtime; see [docs/performance-review.md](docs/performance-review.md).
