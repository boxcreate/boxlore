# `:core:downloads`

## Purpose

Owns the download stack for Boxlore: `DownloadRepository` (Media3 offline cache + DB sync), `SmartDownloadManager` (mixtape-style auto-download scheduling), `ThrottlingDataSource` (speed-limited download I/O), and the three WorkManager workers that drive background downloads.

Does **not** own playback (Media3 session / `BoxLorePlaybackService` — those stay in `:core:playback`), prefs (`UserPreferencesRepository` — that stays in `:core:prefs` / `:core:data`), or catalog/ranking (those stay in `:core:data`).

## Public API

- **`DownloadRepository`** — Media3 `DownloadManager` + Room DB sync for user and smart downloads. Static helpers (`getDownloadManager`, `getDownloadCache`, `getStreamCache`, `relinkDownloadCache`) used by `:core:playback` services.
- **`SmartDownloadManager`** — mixtape-style auto-download scheduler; call `performSync` or use the companion scheduling helpers (`schedulePeriodicSync`, `cancelPeriodicSync`, `purgeAllSmartDownloads`).
- **`ThrottlingDataSource`** + **`DownloadSpeedLimiter`** — speed-limited `DataSource` for background downloads.
- **`DownloadsDependencies`** — interface of Application-scoped download instances.
- **`DownloadsDependenciesHolder`** — `@Volatile` install + `require()` (throws if unset). Workers call this for download types; catalog/prefs/ranking types come from `SharedAppDependenciesHolder`.

## Worker FQCN table

These FQCNs are persisted in WorkManager's database and must **not** change without extending `LegacyWorkerFactory`:

| Worker class | FQCN | Registered under |
|---|---|---|
| `SmartDownloadWorker` | `cx.aswin.boxlore.core.data.SmartDownloadWorker` | `:core:downloads` source, package `cx.aswin.boxlore.core.data` |
| `AutoDownloadWorker` | `cx.aswin.boxlore.core.data.AutoDownloadWorker` | `:core:downloads` source, package `cx.aswin.boxlore.core.data` |
| `PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.data.PurgeSmartDownloadsWorker` | `:core:downloads` source, package `cx.aswin.boxlore.core.data` |

**Note:** The package is intentionally `cx.aswin.boxlore.core.data` (not `cx.aswin.boxlore.core.downloads`) so that WorkManager persisted requests from previous releases continue to resolve the correct class via `LegacyWorkerFactory`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/
  data/                        # package cx.aswin.boxlore.core.data (FQCN-stable)
    DownloadRepository.kt      # Media3 DownloadManager + DB sync
    SmartDownloadManager.kt    # mixtape auto-download scheduler
    SmartDownloadWorker.kt     # periodic WorkManager worker
    AutoDownloadWorker.kt      # release-triggered auto-download worker
    PurgeSmartDownloadsWorker.kt
    ThrottlingDataSource.kt
  downloads/                   # package cx.aswin.boxlore.core.downloads
    DownloadsDependencies.kt   # interface + DownloadsDependenciesHolder
```

## Dependencies

Gradle edges:

- → `:core:data` (api — re-exports PodcastRepository, DB, prefs, ranking, domain ports)
- → `:core:database` (implementation — BoxLoreDatabase entities/DAOs)
- → `:core:domain` (implementation — HistoryRecommendationSource port)
- → `:core:model` (implementation — Episode, Podcast)
- Media3 exoplayer (DownloadManager, Cache, DataSource)
- WorkManager (CoroutineWorker)

Forbidden reverse edges:

- `:core:data` must **not** depend on `:core:downloads` (cycle — data owns catalog/ranking/rss, downloads depends on data)
- `:core:downloads` must **not** depend on `:core:playback` (use `Class.forName` for `MediaDownloadService` to avoid the cycle)

## Threading / lifecycle

- Workers run on WorkManager's default executor threads.
- `DownloadRepository` uses `CoroutineScope(Dispatchers.IO)` for DB operations.
- `SmartDownloadManager` is Application-scoped; call only via `DownloadsDependenciesHolder.require()` in workers — never construct a second instance.

## Persistence & identity

| Stable | Why |
|:---|:---|
| Worker FQCNs (see table above) | Persisted in WorkManager DB across updates |
| Media3 download cache dir `filesDir/downloads` | Cached episode bytes |
| Media3 stream cache dir `cacheDir/stream_cache` | LRU streaming cache |
| `customCacheKey` = `episodeId` | Playback + download cache identity |

## Testing notes

- JVM tests under `src/test` — `DownloadsDependenciesHolderTest` (holder throws when unset, resets in `@AfterEach`)
- Workers require Android instrumentation for full end-to-end; unit test via holder/fake injection.

```bash
./gradlew :core:downloads:testDebugUnitTest
```

## CI relevance

Exercised by the unit-test CI job. Worker integration tests are local/later.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A3)
- [`:core:data` README](../data/README.md)
- [`:core:playback` README](../playback/README.md)
