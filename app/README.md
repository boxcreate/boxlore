# `:app`

## Purpose

Application shell: `BoxLoreApplication`, `MainActivity`, `BoxLoreNavHost`, FCM, surveys, and the **sole composition root** (`AppContainer`). Does not own feature UI or data logic beyond wiring. Installs the shared graph into `SharedAppDependenciesHolder` / `DownloadsDependenciesHolder` so workers and Media3 services reuse the same instances.

## Public API

- `BoxLoreApplication.container` — Application-scoped `AppContainer`
- `AppContainer` — implements `SharedAppDependencies` + `DownloadsDependencies`; constructs DB → RSS/ranking peers → `PodcastRepository` → `QueueRepository` → `PlaybackRepository` → `QueueManager` → `SmartDownloadManager`
- **Ranking / RSS install (create + install only here):**
  - `RssPodcastRepository.create(...).also(::install)`
  - `AdaptiveRankingRepository.create(...).also(::install)`
  - `RankingRuntimeControls.create(...).also(::install)`
  - `RankingFeedbackRepository.create(...).also(::install)`
  - `AdaptiveCandidateScorer.create(...).also(::install)`
  - Production callers must **not** call ranking/RSS `getInstance` — use the container / holders
- **`DownloadServiceLauncherHolder`** — installed in `AppContainer` init with `MediaDownloadService::class.java` so `:core:downloads` starts the download service **without** `Class.forName` or a compile edge onto `:core:playback`
- After container creation (`BoxLoreApplication.onCreate`):
  - `SharedAppDependenciesHolder.instance = container`
  - `DownloadsDependenciesHolder.instance = container`
- `LegacyWorkerFactory` — **permanent** WorkManager upgrade bridge (`Configuration.Provider`); maps pre-rename `cx.aswin.boxcast.*` worker FQCNs to `cx.aswin.boxlore.*`. Do not delete without verified zero legacy work.
- `MainActivity` — theme, edge-to-edge, PostHog/survey wiring, player overlay, OPML import, bottom nav
- `BoxLoreNavHost` — full nav graph; receives deps from `BoxLoreApplication.container`

## Composition / nav map (A7)

```
MainActivity.setContent
└── BoxLoreTheme
    ├── AlertDialog (lore-queue conflict)          ← activity-scoped mutable state
    ├── InAppAnnouncementDialog                    ← userPrefs.activeAnnouncementStream
    ├── Feature-announcement overlay (full screen) ← PostHog flag "active_feature_announcement"
    ├── BoxWithConstraints
    │   ├── Scaffold → PredictiveBackWrapper
    │   │   └── BoxLoreNavHost                     ← navigation/BoxLoreNavHost.kt (+ split helpers below)
    │   │       Routes owned:
    │   │         onboarding                       ← :feature:onboarding
    │   │         home                             ← :feature:home (HomeRoute)
    │   │         learn / learn/history            ← :feature:explore (LearnScreen)
    │   │         briefing                         ← :feature:briefing (BriefingRoute)
    │   │         settings                         ← :feature:home (SettingsScreen)
    │   │         debug                            ← :feature:home (DebugScreen)
    │   │         explore                          ← :feature:explore (ExploreScreen)
    │   │         library / library/history        ← :feature:library
    │   │         library/liked                    ← :feature:library
    │   │         library/subscriptions            ← :feature:library
    │   │         library/downloads (+ settings)   ← :feature:library
    │   │         library/auto_downloads/settings  ← :feature:library
    │   │         library/downloads/show           ← :feature:library
    │   │         podcast/{podcastId}              ← :feature:info  [deep links: boxlore://, boxcast://, https://aswin.cx/...]
    │   │         episode/{episodeId}/...          ← :feature:info  (full-path route)
    │   │         episode/{episodeId}              ← :feature:info  (simplified deep-link route)
    │   ├── BoxLoreNavigationBar (BottomCenter)    ← :core:designsystem
    │   ├── SleepTimerPopup (TopCenter)            ← :core:designsystem
    │   └── PlayerSheetScaffold (TopStart)         ← :feature:player (v2)
    ├── OpmlImportDialog                           ← ui/libraryimport; state owned in Activity
    └── FeedbackSheet                              ← :feature:home components
```

**Deep-link schemes** (preserved): `boxlore://`, `boxcast://`, `https://aswin.cx/boxlore/share`, `https://aswin.cx/boxcast/share`.

**NavHost helpers** (`navigation/`, `internal`): `NavGraphWiring.kt` (route constants, `NavRoutes`, transitions, encode/decode, navigate helpers, `NavSettingsState` / `NavOpmlCallbacks` / `NavHostSession` / `NavHostActions`, `NavGraphWiring`); `NavGraphTabDestinations.kt` (onboarding, home, learn, briefing, explore); `NavGraphLibrarySettingsDestinations.kt` (library, settings, debug); `NavGraphPodcastEpisodeDestinations.kt` (podcast/episode routes). `BoxLoreNavHost.kt` keeps the composable + `rememberIsOnline`. Also: `ExploreTabRoutePattern`, `resolveBottomNavTab` / `resolveBottomNavTabFromBackStack`, `bottomNavTabRoutePattern`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/
  AppContainer.kt          # composition root; ranking/RSS create+install; DownloadServiceLauncherHolder
  BoxLoreApplication.kt    # holders + LegacyWorkerFactory WorkManager config
  MainActivity.kt          # Activity shell
  LegacyWorkerFactory.kt
  navigation/
    BoxLoreNavHost.kt           # composable + rememberIsOnline
    NavGraphWiring.kt           # routes, transitions, nav helpers, wiring bag
    NavGraphTabDestinations.kt
    NavGraphLibrarySettingsDestinations.kt
    NavGraphPodcastEpisodeDestinations.kt
  fcm/ surveys/ ui/
```

`applicationId` is `cx.aswin.boxlore` (do not change with package renames).

## Dependencies

- → all `:feature:*`, `:core:catalog`, `:core:playback`, `:core:downloads`, `:core:designsystem`, `:core:model`, `:core:network`, and other core modules as needed for wiring
- Firebase, PostHog, WorkManager, Media3 session client usage via data/playback layers

Forbidden: features must not construct parallel ranking/RSS graphs; use container / holders.

## Threading / lifecycle

- `AppContainer` is Application-scoped (created once in `BoxLoreApplication.onCreate`)
- Repositories/managers are lazy; first touch may hit Room / network on the caller’s dispatcher
- Workers resolve deps via `SharedAppDependenciesHolder.require()` / `DownloadsDependenciesHolder.require()`
- WorkManager uses `LegacyWorkerFactory` from `Configuration.Provider`

## Persistence & identity

- `applicationId = cx.aswin.boxlore`
- WorkManager worker FQCNs remain stable (`LegacyWorkerFactory` is a **permanent** upgrade bridge — keep until verified zero legacy work)
- Pref file/key strings such as `boxcast_prefs` stay unchanged (identity)
- Deep links: Manifest + NavHost accept **`boxlore://` and `boxcast://`**, plus `https://aswin.cx/boxlore/share` and `/boxcast/share`
- BuildConfig: `BOXLORE_API_BASE_URL` / `BOXLORE_PUBLIC_KEY` preferred; `local.properties` may still use `BOXCAST_*` (Gradle dual-reads). Legacy `BOXCAST_*` BuildConfig fields remain populated with the same resolved values
- DataStore `user_preferences`, Room DB filenames, and ranking DB are owned by core modules — do not rename here
- CI stub `google-services.json` via `.github/actions/write-ci-google-services` (not a Firebase mock)

## Testing notes

- JVM: `src/test` (e.g. FCM payload parser)
- Holder unset behavior is covered in `:core:catalog` (`SharedAppDependenciesHolderTest`)
- Nav / feature UI covered by feature androidTest + Maestro (see those READMEs)

```bash
./gradlew :app:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` — app unit tests + stub google-services + Kover gate
- `android-instrumented-tests.yml` — feature instrumented (not app shell)
- `maestro-nightly.yml` — E2E smoke against installed app

## See also

- Root [`ARCHITECTURE.md`](../ARCHITECTURE.md)
- [`docs/TESTING.md`](../docs/TESTING.md)
- [`docs/PLAN_MODULAR_ANDROID_HARDENING.md`](../docs/PLAN_MODULAR_ANDROID_HARDENING.md) (Phase A1 / A7)
- [`:core:catalog` README](../core/catalog/README.md)
- [`:core:downloads` README](../core/downloads/README.md)
- [`:core:playback` README](../core/playback/README.md)
