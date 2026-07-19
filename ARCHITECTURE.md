# Boxlore architecture

Cross-module contract for the Android app. Module-local detail lives in each module’s folder `README.md`. Testing goals live in [`docs/TESTING.md`](docs/TESTING.md). Analytics event contracts live in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](docs/ANALYTICS_EVENT_GLOSSARY.md).

## Status legend

| Status | Meaning |
| :--- | :--- |
| **Done** | Implemented and verified in the codebase |
| **WIP** | Partially in place; gaps remain |
| **Yet to start** | Targeted end state; not begun |

## Capability status

| Capability | Status |
| :--- | :--- |
| 21-module Gradle map (`:app`, `:core:*`, `:feature:*`) | Done |
| Package root matches Gradle module id (`cx.aswin.boxlore.*`) | Done |
| Single `AppContainer` composition root (no Hilt/Koin) | Done |
| Catalog / playback / prefs / analytics / rss / ranking / downloads extracted | Done |
| Permanent upgrade failsafes (`LegacyWorkerFactory`, `core.data.*` stubs, prefs migrator, dual deep links) | Done |
| No feature → feature Gradle edges (Konsist) | Done |
| `:core:catalog` ↛ `:core:designsystem` | Done |
| Features do not import PostHog directly | Done |
| Fat-file policy: no `src/main` Kotlin file ≥ 1000 LOC | Done |
| Folder READMEs accurate vs template | Done |
| Testing depth (see [`docs/TESTING.md`](docs/TESTING.md)) | WIP |

## Product invariants

| Invariant | Rule |
| :--- | :--- |
| `applicationId` | `cx.aswin.boxlore` |
| Code packages / namespaces | `cx.aswin.boxlore.*` |
| SharedPreferences **files** | Migrate `boxcast_*` → `boxlore_*` via `PrefsFileMigrator`; **key** strings inside files unchanged |
| DataStore | Name `user_preferences` unchanged |
| Room | Main DB filename/tables unchanged; ranking uses separate adaptive DB |
| WorkManager | `LegacyWorkerFactory` + old-FQCN stubs under `core.data.*` are permanent upgrade bridges |
| Deep links | Accept `boxlore://` and `boxcast://` (and both https share path prefixes) |
| BuildConfig | Prefer `BOXLORE_*`; Gradle dual-reads `BOXCAST_*` fallbacks |
| Playback | One UI `PlaybackRepository` — never recreate per route or worker |
| Construction order | DB → `PodcastRepository` → `QueueRepository` → `PlaybackRepository` → `QueueManager` → `SmartDownloadManager` |
| Smart Queue refill | Service-owned only (`BoxLorePlaybackService`) |
| ID / cache schemes | Do not rename `rss:` / negative IDs, mediaId prefixes, `customCacheKey` |

## Fat-file policy

No Kotlin file under `*/src/main/**` may exceed **1000 lines**. Extract cohesive helpers or composables in the same package. Do not change public APIs or identity/FQCN invariants to satisfy the cap.

## Gradle modules

```text
:app
:core:model | :core:network | :core:domain | :core:database | :core:prefs
:core:analytics | :core:catalog | :core:rss | :core:downloads | :core:playback | :core:ranking
:core:designsystem | :core:testing
:feature:home | :feature:player | :feature:info | :feature:explore
:feature:library | :feature:onboarding | :feature:briefing
```

Folder path equals Gradle id (`core/playback` → `:core:playback`).

### Module ownership

| Module | Owns | README |
| :--- | :--- | :--- |
| `:app` | Application, `AppContainer`, nav host, FCM, WorkerFactory | [`app/README.md`](app/README.md) |
| `:core:model` | Shared models / enums used across layers | [`core/model/README.md`](core/model/README.md) |
| `:core:network` | HTTP API client (`BoxLoreApi` / `NetworkModule`) + network DTOs | [`core/network/README.md`](core/network/README.md) |
| `:core:domain` | Thin ports + small results (no Room / repos) | [`core/domain/README.md`](core/domain/README.md) |
| `:core:database` | Main Room (`BoxLoreDatabase`, entities, DAOs, migrations) | [`core/database/README.md`](core/database/README.md) |
| `:core:prefs` | DataStore + SharedPreferences façades (`UserPreferencesRepository`, `BoxcastPrefs`) | [`core/prefs/README.md`](core/prefs/README.md) |
| `:core:analytics` | Analytics façade (`AnalyticsHelper`, `Analytics`, `RecordingAnalytics`); PostHog init stays in `:app` | [`core/analytics/README.md`](core/analytics/README.md) |
| `:core:catalog` | Catalog/orchestration — `PodcastRepository`, subscriptions, content sections, backup/restore, shared-deps bridge | [`core/catalog/README.md`](core/catalog/README.md) |
| `:core:rss` | RSS fetch/parse, `RssPodcastRepository`, `rss:` / negative IDs | [`core/rss/README.md`](core/rss/README.md) |
| `:core:ranking` | Adaptive scoring, LinUCB, feedback, `AdaptiveRankingDatabase` | [`core/ranking/README.md`](core/ranking/README.md) |
| `:core:downloads` | `DownloadRepository`, Smart Downloads, WorkManager workers (+ permanent `core.data` stubs) | [`core/downloads/README.md`](core/downloads/README.md) |
| `:core:playback` | `PlaybackRepository`, queue, Media3 services, smart-queue helpers (+ permanent service stubs) | [`core/playback/README.md`](core/playback/README.md) |
| `:core:designsystem` | Theme, shared composables; no data/network ownership | [`core/designsystem/README.md`](core/designsystem/README.md) |
| `:core:testing` | Shared fixtures, dispatcher extensions, architecture guards | [`core/testing/README.md`](core/testing/README.md) |
| `:feature:home` | Home + Settings hub + Add RSS + Debug | [`feature/home/README.md`](feature/home/README.md) |
| `:feature:player` | Player overlay UI (`PlayerSheetScaffold`) — not a NavHost route | [`feature/player/README.md`](feature/player/README.md) |
| `:feature:info` | Podcast / episode detail (+ deep links) | [`feature/info/README.md`](feature/info/README.md) |
| `:feature:explore` | Explore + Learn / LearnHistory | [`feature/explore/README.md`](feature/explore/README.md) |
| `:feature:library` | Library hub, subs, downloads, history, liked | [`feature/library/README.md`](feature/library/README.md) |
| `:feature:onboarding` | First-run flows (AI / genre / search / import) | [`feature/onboarding/README.md`](feature/onboarding/README.md) |
| `:feature:briefing` | Daily briefing screen | [`feature/briefing/README.md`](feature/briefing/README.md) |

### Package roots (extracted cores)

| Module | Package root |
| :--- | :--- |
| `:core:prefs` | `cx.aswin.boxlore.core.prefs` |
| `:core:analytics` | `cx.aswin.boxlore.core.analytics` |
| `:core:rss` | `cx.aswin.boxlore.core.rss` |
| `:core:ranking` | `cx.aswin.boxlore.core.ranking` |
| `:core:downloads` | `cx.aswin.boxlore.core.downloads` |
| `:core:playback` | `cx.aswin.boxlore.core.playback` |
| `:core:database` | `cx.aswin.boxlore.core.database` |
| `:core:catalog` | `cx.aswin.boxlore.core.catalog` |

### Dependency direction

```mermaid
flowchart TB
  app[:app]
  subgraph features [feature]
    home[:feature:home]
    player[:feature:player]
    info[:feature:info]
    explore[:feature:explore]
    library[:feature:library]
    onboarding[:feature:onboarding]
    briefing[:feature:briefing]
  end
  playback[:core:playback]
  catalog[:core:catalog]
  rss[:core:rss]
  analytics[:core:analytics]
  downloads[:core:downloads]
  ranking[:core:ranking]
  prefs[:core:prefs]
  domain[:core:domain]
  database[:core:database]
  network[:core:network]
  design[:core:designsystem]
  model[:core:model]

  app --> features
  app --> playback
  app --> catalog
  app --> downloads
  app --> design
  features --> playback
  features --> catalog
  features --> downloads
  features --> design
  features --> model
  playback --> catalog
  playback --> downloads
  downloads --> catalog
  catalog --> rss
  catalog --> prefs
  catalog --> domain
  catalog --> database
  catalog --> ranking
  catalog --> network
  catalog --> model
  features --> analytics
  features --> ranking
  playback --> ranking
  playback --> analytics
  design --> analytics
  rss --> database
  rss --> domain
  rss --> model
  ranking --> database
  ranking --> domain
  ranking --> prefs
  ranking --> model
  analytics --> model
  prefs --> model
  domain --> network
  domain --> model
  database --> network
  database --> model
  design --> model
  network --> model
```

Primary stack: **playback → catalog → prefs / domain / database / network / model**.

### Dependency rules

- No feature → feature Gradle dependencies.
- `:core:playback` → `:core:catalog` (not the reverse).
- `:core:catalog` must not depend on `:core:designsystem`.
- Features that need analytics or ranking declare `:core:analytics` / `:core:ranking` directly (catalog does not re-export analytics; ranking is not an `api` edge).
- Zero feature-module `PostHog.capture` / `import com.posthog` — guarded by `scripts/ci/check-feature-no-posthog.sh`.
- Domain enums used by both catalog and UI (e.g. `AutoTranscriptState`) belong in `:core:model`.
- `:core:domain` holds ports only; `:core:catalog` implements catalog-facing ports.
- `:core:network` is HTTP/API only; `RssFeedClient` lives in `:core:rss` (re-exported via `:core:catalog` → `api(rss)`).

## Upgrade failsafes (permanent)

### Workers

| Old FQCN | Current FQCN | Failsafe |
| :--- | :--- | :--- |
| `cx.aswin.boxcast.core.data.SmartDownloadWorker` | `cx.aswin.boxlore.core.downloads.SmartDownloadWorker` | `LegacyWorkerFactory` |
| `cx.aswin.boxcast.core.data.AutoDownloadWorker` | `cx.aswin.boxlore.core.downloads.AutoDownloadWorker` | `LegacyWorkerFactory` |
| `cx.aswin.boxcast.core.data.PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker` | `LegacyWorkerFactory` |
| `cx.aswin.boxlore.core.data.SmartDownloadWorker` | `cx.aswin.boxlore.core.downloads.SmartDownloadWorker` | factory + stub |
| `cx.aswin.boxlore.core.data.AutoDownloadWorker` | `cx.aswin.boxlore.core.downloads.AutoDownloadWorker` | factory + stub |
| `cx.aswin.boxlore.core.data.PurgeSmartDownloadsWorker` | `cx.aswin.boxlore.core.downloads.PurgeSmartDownloadsWorker` | factory + stub |

### Services / providers

| Old FQCN | Current FQCN | Failsafe |
| :--- | :--- | :--- |
| `cx.aswin.boxlore.core.data.service.BoxLorePlaybackService` | `cx.aswin.boxlore.core.playback.service.BoxLorePlaybackService` | Manifest + stub |
| `cx.aswin.boxlore.core.data.service.MediaDownloadService` | `cx.aswin.boxlore.core.playback.service.MediaDownloadService` | Manifest + stub |
| `cx.aswin.boxlore.core.data.service.AutoCollageProvider` | `cx.aswin.boxlore.core.playback.service.AutoCollageProvider` | Manifest + stub |

### SharedPreferences files

| Old file | New file | Opener |
| :--- | :--- | :--- |
| `boxcast_prefs` | `boxlore_prefs` | `BoxcastPrefs` via `PrefsFileMigrator` |
| `boxcast_theme_fast_cache` | `boxlore_theme_fast_cache` | `UserPreferencesRepository` |
| `boxcast_analytics_prefs` | `boxlore_analytics_prefs` | `AnalyticsHelper` |
| `boxcast_player` | `boxlore_player` | `PlaybackRepository` / `PodcastRepository` |
| `boxcast_api_config` | `boxlore_api_config` | `BoxLoreAppRoot` |
| `boxcast_referrer_prefs` | `boxlore_referrer_prefs` | `InstallReferrerManager` |

ProGuard dual-keeps permanent `core.data.**` stubs and aligned `core.catalog|prefs|analytics|rss|ranking|downloads|playback|database.**`.

## Composition root

There is no Hilt/Koin. `AppContainer` (in `:app`) owns the shared graph and is **created only in `BoxLoreApplication`**. `MainActivity`, `BoxLoreNavHost`, and feature assemblers **consume** `application.container` — they do not construct repositories or a parallel graph.

Home / Settings / Info construct VMs via assemblers (`HomeViewModelAssembler`, `SettingsViewModelAssembler`, `InfoViewModelAssembler`). Narrow ports under `core.domain.ports` exist so ViewModels and workers can take fakes without full repositories / `BoxLoreDatabase`. Production: `RoomLocalCatalog` / `RoomEpisodeOfflineLookup` from `AppContainer`. `ListeningHistoryBackupPort` lives in `core.catalog.ports`.

## Notable surfaces

| Surface | Module | Notes |
| :--- | :--- | :--- |
| Home + Settings hub + Add RSS | `:feature:home` | Settings includes RSS dialog |
| Learn / LearnHistory (bottom nav) | `:feature:explore` | Learn is a tab |
| Player overlay | `:feature:player` | `PlayerSheetScaffold` — not a NavHost route |
| Podcast / Episode info | `:feature:info` | Dual episode routes + deep links |
| Playback / queue / Media3 | `:core:playback` | Permanent service stubs under `core.data.service` |
| HTTP API | `:core:network` | Not RSS |
| Ranking | `:core:ranking` | Own adaptive Room DB |
| RSS catalog | `:core:rss` | Negative / `rss:` IDs; re-exported through catalog |

## Testing layers (summary)

| Layer | Purpose | Detail |
| :--- | :--- | :--- |
| JVM unit | Logic, ports, ViewModel slices | [`docs/TESTING.md`](docs/TESTING.md) |
| Compose UI | Controls, tags, nav wiring | [`docs/TESTING.md`](docs/TESTING.md) |
| Maestro | Device E2E smoke | [`maestro/README.md`](maestro/README.md) |
| Architecture-as-code | Konsist / filesystem guards | `:core:testing` |
| Screenshots | Visual baselines | [`docs/screenshots/README.md`](docs/screenshots/README.md) |

No MockK / Hilt unless this contract is amended. Shared fixtures belong in `:core:testing`.

## Related docs

| Doc | Role |
| :--- | :--- |
| [`docs/TESTING.md`](docs/TESTING.md) | End-state testing plan and status |
| [`docs/ANALYTICS_EVENT_GLOSSARY.md`](docs/ANALYTICS_EVENT_GLOSSARY.md) | Formal analytics events (name + meaning + properties) |
| [`docs/MODULE_README_TEMPLATE.md`](docs/MODULE_README_TEMPLATE.md) | Module README contract |
| [`docs/recommendation-system.md`](docs/recommendation-system.md) | Ranking / recommendation product detail |
| [`maestro/README.md`](maestro/README.md) | Local Maestro flows |
