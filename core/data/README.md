# `:core:data`

## Purpose

Data layer for repositories, downloads/workers, ranking, RSS, and analytics helpers. Implements ports from `:core:domain` (re-exported via `api`). Main Room DB lives in `:core:database` (re-exported via `api`). Prefs live in `:core:prefs` (re-exported via `api`). Playback/queue/Media3 services live in `:core:playback` (same Java packages under `cx.aswin.boxlore.core.data.*`).

Owns the **shared-deps entry API** for workers (`SharedAppDependencies` / `SharedAppDependenciesHolder`) so background work does not rebuild parallel repository graphs. Further splits: `downloads` / `analytics` / `ranking` / `rss` / catalog.

## Public API

- Repositories: `PodcastRepository` (ctor-injected `RssPodcastRepository`), `SubscriptionRepository`, `DownloadRepository` (ctor-injected `RankingFeedbackRepository`), `RssPodcastRepository`
- Prefs (from `:core:prefs`, via `api`): `UserPreferencesRepository`, `BoxcastPrefs`
- Domain ports (from `:core:domain`, via `api`): `RssSubscriptionPort`, `RankingResetPort`, `PodcastCatalogPort`, `HistoryRecommendationSource`, `RssSubscriptionResult`
- Data-only port: `ports.ListeningHistoryBackupPort`
- Shared helpers: `QueueMath`, `QueueSkipMemory`, `SmartQueueEngine` / `SmartQueueSources`, `PlaybackSkipBounds`
- Managers: `SmartDownloadManager` (ctor-injected `AdaptiveCandidateScorer`; uses `HistoryRecommendationSource`, not full `PlaybackRepository`)
- Ranking: `AdaptiveCandidateScorer`, `RankingFeedbackRepository`, `AdaptiveRankingRepository` — **production code must not call `getInstance`**; only `AppContainer` installs via `getInstance`
- Composition bridge:
  - `SharedAppDependencies` — interface of Application-scoped instances workers/services need (DB, podcast/download/subscription/prefs, RSS, ranking, history source, smart download). Queue stays in `:core:playback` and is built from shared DB + `podcastRepository`.
  - `SharedAppDependenciesHolder` — `@Volatile` install + `require()` (throws if unset)
- Workers: `SmartDownloadWorker`, `AutoDownloadWorker`, `PurgeSmartDownloadsWorker` — use `SharedAppDependenciesHolder.require()` in `doWork()` (FQCN + `Context`/`WorkerParameters` ctors unchanged for `LegacyWorkerFactory`)
- Backup: `backup.LibraryBackupManager` — ranking/RSS via ctor (defaults from holder)

Playback types (`PlaybackRepository`, `QueueManager`, `QueueRepository`, `BoxLorePlaybackService`, …) are in `:core:playback`.

**Must not** depend on `:core:designsystem` or `:core:playback`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/data/
  SharedAppDependencies.kt   # interface + holder
  ranking/ content/ analytics/ privacy/ backup/ crosspromo/ ports/
  *Worker.kt                 # WorkManager entry points
```

Main Room sources: `:core:database` → `cx.aswin.boxlore.core.data.database`.
Feature-facing ports: `:core:domain` → `cx.aswin.boxlore.core.domain.ports`.

## Dependencies

- → `:core:domain` (api), `:core:prefs` (api), `:core:model`, `:core:network`, `:core:database` (api)
- Media3 exoplayer (offline/cache for `DownloadRepository`), WorkManager, DataStore (privacy consent still here), Firebase Messaging pieces as needed; Room runtime via `:core:database` (ksp kept for ranking DB)
- Forbidden: → `:core:playback`, → `:core:designsystem`

## Threading / lifecycle

- Repositories are Application-scoped when obtained from the holder/container
- Workers run on WorkManager executors; they must not construct a second Podcast/ranking/RSS graph
- Room `BoxLoreDatabase.getDatabase` may still be used where a DB-only path is intentional; prefer `deps.database` from the holder in workers

## Persistence & identity

| Stable | Why |
| :--- | :--- |
| Worker FQCNs (`SmartDownloadWorker`, `AutoDownloadWorker`, `PurgeSmartDownloadsWorker`) | Persisted WorkManager requests |
| DataStore `user_preferences` / SharedPrefs `boxcast_prefs` (owned by `:core:prefs`) | Existing installs |
| Main Room DB filename + ranking Room under `ranking/database/` | User data |
| `rss:` podcast IDs | Catalog identity |

## Testing notes

- JVM tests under `src/test` (queue math, RSS, ranking, content, smart queue, **`SharedAppDependenciesHolderTest`**)
- `SharedAppDependenciesHolder.require()` throws when unset (pure unit test; resets holder in `@AfterEach`)
- Playback skip policy tests live in `:core:playback`

```bash
./gradlew :core:data:testDebugUnitTest
```

## CI relevance

Exercised by the unit-test CI job (`testDebugUnitTest` / project suite). Instrumented coverage of workers is local/later.

## See also

- Root [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A1)
- [`:core:domain` README](../domain/README.md)
- [`:core:playback` README](../playback/README.md)
- [`:app` README](../../app/README.md)
