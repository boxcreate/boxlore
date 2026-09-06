# `:core:downloads`

## Purpose

Owns offline download orchestration: Media3 offline cache access, download database synchronization, smart-download scheduling, bandwidth throttling, and WorkManager workers. It does not own playback session services, preferences storage, catalog retrieval, ranking model storage, or feature UI.

## Public API

- `DownloadRepository` coordinates Media3 `DownloadManager`, cache helpers, and Room download state. Its `completedDownloadItems` flow maps every completed row to listener-facing `Episode` + `Podcast` metadata, orders by release time with download-time fallback, and exposes only local playback paths. Background downloads directly invoke `DownloadManager.addDownload` rather than starting `DownloadService`, preventing `ForegroundServiceStartNotAllowedException` on Android 14+ (API 34+). In `removeDownload`, cache removal directly falls back to `DownloadManager.removeDownload` if background service commands fail, preventing silent disk leaks. `awaitDownloadCompletion` allows background workers to suspend while holding WorkManager execution until downloads finish or timeout. `reconcileDownloadStatus` and `reconcileStaleDownloads` clear or reset orphaned rows stuck in downloading state. Download completed/failed analytics omit `file_size_mb` when Media3 length is unknown and omit `source` when Room provenance is missing (never invent `0` / `"manual"`). Briefing downloads use `EpisodeMediaCacheKey` (audio `v=`) so offline keys match playback after same-day regenerations.
- `DownloadRepository.relinkDownloadCache` reports whether cached spans reached a replacement episode id and recognizes an already-populated destination on retry after process death. RSS/catalog identity repair changes the Room download row only after that success; a missing/failed move or conflicting incomplete destination keeps the legacy completed-download row and bytes intact.
- `CompletedDownloadItem` is the feature-safe model for completed offline content; filtering and deterministic ordering stay owned by this module rather than feature code.
- `DownloadAnalyticsMapping` is the pure helper for those property decisions (including allowlisted download failure codes).
- `SmartDownloadManager` selects and schedules automatic downloads; downloads run sequentially with strict cumulative and per-episode time budgets to prevent WorkManager 10-minute timeouts. Oldest serial episode resolution queries catalog episodes with a safe page limit of 100 to prevent heap pressure on large feeds. `smart_download_sync.completed_count` is completed-only (`STATUS_COMPLETED`), while the download loop still budgets against active+queued counts. `SmartDownloadSyncGate` serializes cadence admission and reconciliation, so overlapping launch catch-up and WorkManager calls cannot both recycle downloads. Automatic runs share the persisted last-success time and admit at most one sync per 24-hour cadence; future/restored timestamps fail open, while explicit manual refresh bypasses cadence but remains serialized. `reconcileScheduleWithPreferences` aligns the DataStore toggle with the periodic `SmartDownloadSync` WorkManager job after restore (restored work turns the toggle back on; a stored-on toggle reschedules missing work). Disabled sync never runs except from an explicit manual refresh.
- `DownloadArtworkUrls` drops missing local artwork paths after backup restore and falls back to the subscribed show's remote image. New downloads persist a remote URL when the local artwork copy fails.
- `SmartDownloadWorker`, `AutoDownloadWorker`, and `PurgeSmartDownloadsWorker` perform background download work. `AutoDownloadWorker` reconciles stale states, invokes background-safe direct enqueueing, and suspends on download completion.
- `DownloadsDependencies` and `DownloadsDependenciesHolder` expose application-scoped download dependencies to workers.
- `DownloadSpeedLimiter`, `ThrottlingDataSource`, and `SmartDownloadCandidateLogic` support download I/O and candidate filtering (excluding shows with auto-download enabled from mixtape candidate pools to avoid work duplication).
- `ports.DownloadServiceLauncher` and `DownloadServiceLauncherHolder` let `:app` provide the Media3 service class without a downloads-to-playback Gradle edge.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/
  downloads/
    AutoDownloadWorker.kt
    CompletedDownloadItem.kt
    DownloadRepository.kt
    DownloadsDependencies.kt
    DownloadSpeedLimiter.kt
    PurgeSmartDownloadsWorker.kt
    SmartDownloadCandidateLogic.kt
    SmartDownloadManager.kt
    SmartDownloadSyncGate.kt
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
- Existing coverage includes worker behavior, dependency-holder behavior, smart-download candidate logic, and `DownloadAnalyticsMapping` size/source/failure mapping.
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
