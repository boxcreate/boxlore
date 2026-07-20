# `:core:downloads`

## Purpose

Owns offline download orchestration: Media3 offline cache access, download database synchronization, smart-download scheduling, bandwidth throttling, and WorkManager workers. It does not own playback session services, preferences storage, catalog retrieval, ranking model storage, or feature UI.

## Public API

- `DownloadRepository` coordinates Media3 `DownloadManager`, cache helpers, and Room download state. Download completed/failed analytics omit `file_size_mb` when Media3 length is unknown and omit `source` when Room provenance is missing (never invent `0` / `"manual"`).
- `DownloadAnalyticsMapping` is the pure helper for those property decisions.
- `SmartDownloadManager` selects and schedules automatic downloads; `smart_download_sync.completed_count` is completed-only (`STATUS_COMPLETED`), while the download loop still budgets against active+queued counts.
- `SmartDownloadWorker`, `AutoDownloadWorker`, and `PurgeSmartDownloadsWorker` perform background download work.
- `DownloadsDependencies` and `DownloadsDependenciesHolder` expose application-scoped download dependencies to workers.
- `DownloadSpeedLimiter`, `ThrottlingDataSource`, and `SmartDownloadCandidateLogic` support download I/O and candidate filtering.
- `ports.DownloadServiceLauncher` and `DownloadServiceLauncherHolder` let `:app` provide the Media3 service class without a downloads-to-playback Gradle edge.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/
  downloads/
    AutoDownloadWorker.kt
    DownloadRepository.kt
    DownloadsDependencies.kt
    DownloadSpeedLimiter.kt
    PurgeSmartDownloadsWorker.kt
    SmartDownloadCandidateLogic.kt
    SmartDownloadManager.kt
    SmartDownloadWorker.kt
    ThrottlingDataSource.kt
    ports/
      DownloadServiceLauncher.kt
  data/
    AutoDownloadWorker.kt
    PurgeSmartDownloadsWorker.kt
    SmartDownloadWorker.kt
```

Files under `core/data` are compatibility stubs for previously scheduled WorkManager class names.

## Dependencies

- Project dependencies: `:core:catalog`, `:core:database`, `:core:domain`, `:core:model`, `:core:ranking`, and `:core:analytics`.
- Libraries: Media3 ExoPlayer offline/cache APIs, WorkManager, coroutines, AndroidX core, Robolectric and WorkManager testing for JVM tests.
- Reverse-edge rule: downloads must not depend on playback; service launch is supplied through `DownloadServiceLauncherHolder`.

## Threading / lifecycle

- `DownloadRepository` and `SmartDownloadManager` are application-scoped through `AppContainer`.
- Workers resolve dependencies through `DownloadsDependenciesHolder.require()`.
- Media3 download work and cache I/O run off the main thread.
- Periodic smart-download work is scheduled and cancelled through WorkManager APIs.

## Persistence & identity

- Worker class names are persisted by WorkManager; current and legacy names must remain bridgeable.
- Download cache locations and Room download rows are user data and should not be renamed casually.
- The foreground download service identity is owned by `:core:playback` and provided by `:app`.

## Testing notes

- Unit tests live under `core/downloads/src/test`.
- Existing coverage includes worker behavior, dependency-holder behavior, smart-download candidate logic, and `DownloadAnalyticsMapping` size/source mapping.
- Robolectric and WorkManager testing are enabled for worker tests.

```bash
./gradlew :core:downloads:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs downloads JVM tests.
- The root Kover merged verification includes this module.
- App assembly validates the download service launcher wiring through the production graph.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:playback` README](../playback/README.md)
- [`:app` README](../../app/README.md)
