# `:app`

## Purpose

The application module owns the Android app shell: `BoxLoreApplication`, `MainActivity`, navigation wiring, FCM entry points, in-app announcements, surveys, OPML import UI, and the single production composition root in `AppContainer`. It wires feature screens and core services together; feature UI and data engines remain in their owning modules.

## Public API

- `BoxLoreApplication.container` exposes the process-scoped `AppContainer`.
- On startup, `BoxLoreApplication` configures `LearningEventLog` via `BoxcastPrefs.resolveLearnerLogEnabled`: on by default in debug when unset; **always off in release** unless the user has explicitly persisted an opt-in from the debug screen.
- `AppContainer` constructs the shared graph: database, network, RSS, ranking, catalog, playback, queue, downloads, prefs, and analytics dependencies.
- Install attribution: `AppContainer` wires `InstallReferrerManager.onInstallReferrerResolved` → analytics person properties (`install_channel`). Catalog stays free of `:core:analytics`.
- FCM (`BoxLoreFcmService`) owns notification_received / tap extras (`notification_type`, podcast/episode ids for snake+camel keys). Generic push intents propagate those extras so taps are not always `"push"`. For `type=new_episode`, opted-in PI shows run the same full `refreshFromFeed` as launch sync (`NewEpisodePushHydration`, 1000-oldest PI baseline in parallel) so extras land in Room for Home chips and Library; the payload enclosure/guid still selects the notification episode. If the payload has no usable episode id, the tap opens the podcast page.
- Library backup/import analytics (`trackBackupRestoreResult`, import failed) use allowlisted error codes from `LibraryBackupAnalyticsErrors` — never raw exception text.
- `MainActivity` / `BoxLoreAppRoot` own deep-link and session-restore analytics at the shell layer.
- App-shell UI uses the centralized Google Sans Flex weight tokens from `:core:designsystem`.
- `SharedAppDependenciesHolder` and `DownloadsDependenciesHolder` are installed from the application so workers and Media3 services reuse the same graph.
- `HomeScreenWidgetsInstaller` installs `NowPlayingWidgetDependencies` (via `NowPlayingWidgetPlaybackAdapter`) and `LibraryWidgetDependencies` (via `WidgetLibrarySourceAdapter`) so home-screen widgets share the app-scoped `PlaybackRepository` / library ports without a second graph. Transport calls hop to `Dispatchers.Main`. Lettering-roundness changes re-render bitmap labels so ROND matches Appearance.
- `DownloadServiceLauncherHolder` is installed with `MediaDownloadService::class.java` so `:core:downloads` can launch the foreground download service without depending on `:core:playback`.
- `LegacyWorkerFactory` maps legacy worker class names to current worker implementations for WorkManager continuity.
- `MainActivity` hosts theme, edge-to-edge setup, app update hooks, surveys, OPML import state (full-screen `OpmlImportDialog` so onboarding welcome never shows through), the selected floating or classic navigation presentation, and the player overlay.
- NPS survey UI (`surveys/`) applies `NpsSurveyBranching` after each answer so detractor / passive / promoter open-text paths end instead of falling through into the next score band (PostHog open-question `end` branching is not reliably persisted via API).
- `BoxLoreNavHost` owns app route registration and delegates screen bodies to feature modules; stack-slide transitions follow the visible Home → Explore → Library → Lore order, and Home’s first committed content frame unlocks the floating Lore launch animation.
- Cold-start destination precedence (see `StartDestinationResolver`): incomplete onboarding → offline Downloads (no deep link) → Appearance **Open app to** Subscriptions (no deep link) → Home. Deep links and push `target_route` still win over Open app to. When cold start opens Subscriptions, `openedToSubscriptionsOnLaunch` makes Back navigate to Home (not Library hub); Library-tile entry still pops to Library.
- Process latest-episode sync (`SubscriptionForegroundSync`) is started from `BoxLoreAppRoot` after onboarding. Returning to the app requests another refresh (first `ON_START` skipped so Home keeps its 2s first-paint delay). Subscriptions-first launches still fetch because Library calls `requestRefresh` as soon as that screen is visible.
- Tab destinations in `NavGraphTabDestinations` wire Home / Explore / Learn / Library / player entry points to feature screens. Lore history deep-links use `entryPoint=learn` (canonical glossary; legacy `learn_history` still normalizes to `learn`).
- Home receives the shared catalog and ranking instances it actively uses; endpoint-backed
  editorial rows are loaded inside `:feature:home` through that catalog instance.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/
  AppContainer.kt
  BoxLoreApplication.kt
  MainActivity.kt
  LegacyWorkerFactory.kt
  navigation/
    BoxLoreNavHost.kt
    BottomNavNavigation.kt
    NavGraphWiring.kt
    NavGraphTabDestinations.kt
    NavGraphLibrarySettingsDestinations.kt
    LaunchSubscriptionsBack.kt
    LaunchSubscriptionsBackDecision.kt
    StartDestinationResolver.kt
    NavGraphPodcastEpisodeDestinations.kt
    PushTargetRouteAllowlist.kt
  connectivity/
  fcm/
    BoxLoreFcmService.kt
    FcmPayloadParser.kt
    NewEpisodeFcmLogic.kt
    NewEpisodePushHydration.kt
  lifecycle/
  surveys/
  ui/
    announcement/
    libraryimport/
      OpmlImportDialog.kt
      OpmlImportEffects.kt
      OpmlImportProgressContent.kt
  updates/
  util/
```

Routes include onboarding, home, learn, briefing, settings, debug, explore, library sub-routes, podcast details, and episode details. Deep-link schemes remain `boxlore://`, `boxcast://`, plus HTTPS App Links `https://aswin.cx/boxlore/share` and `https://aswin.cx/boxcast/share` (`autoVerify`). Domain ownership requires Digital Asset Links at `https://aswin.cx/.well-known/assetlinks.json` for package `cx.aswin.boxlore` with the Play App signing SHA-256.

## Dependencies

- Project dependencies: all `:feature:*` modules plus `:core:analytics`, `:core:catalog`, `:core:designsystem`, `:core:domain`, `:core:downloads`, `:core:model`, `:core:network`, `:core:playback`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, Firebase, PostHog, WorkManager, Media3 client usage, Coil, Retrofit, Kotlin serialization, and Play Core.
- Reverse-edge rule: feature modules and core modules must not construct independent application graphs; they receive instances from `AppContainer` or the dependency holders.

## Threading / lifecycle

- `AppContainer` is created once from `BoxLoreApplication.onCreate` and is application-scoped.
- Workers resolve dependencies through installed holders before doing background work.
- Media3 services lazily resolve the shared graph after application startup.
- UI composition, navigation, OPML import state, surveys, and player overlay state are Activity-scoped.
- `BoxLoreAppRoot` stacks the mini player above designsystem’s selected navigation-style clearance contract; routes remain Home / Explore / Library / Lore (`learn`) for both presentations.

## Persistence & identity

- `applicationId` is `cx.aswin.boxlore`.
- Manifest service, receiver, activity, and worker class names are system-facing identities.
- `LegacyWorkerFactory` preserves WorkManager upgrades from legacy worker class names.
- Build config reads `BOXLORE_API_BASE_URL` and `BOXLORE_PUBLIC_KEY`, with legacy local-property names still accepted for existing developer environments.
- Core modules own Room filenames, DataStore names, SharedPreferences names, ranking identity, RSS IDs, and playback media IDs.

## Testing notes

- Unit tests live under `app/src/test`, including app container smoke coverage, worker factory mapping, FCM payload parsing (type + snake/camel ids, feedUrl/guid/enclosure), new-episode route/id helpers, opted-in feed hydration before notify, library backup analytics error codes, push-target route allowlisting, cold-start destination precedence (`StartDestinationResolverTest`), and launch-Subscriptions Back decisions (`LaunchSubscriptionsBackDecisionTest`).
- Navigation and feature UI behavior are covered mainly in feature module tests and Maestro smoke flows.

```bash
./gradlew :app:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs app JVM tests and uses the CI Firebase configuration stub.
- `maestro-nightly.yml` exercises installed-app smoke flows when the optional device-farm secrets are available.
- App assembly validates dependency wiring for all feature and core modules.

## See also

- [`ARCHITECTURE.md`](../ARCHITECTURE.md)
- [`docs/TESTING.md`](../docs/TESTING.md)
- [`:core:catalog` README](../core/catalog/README.md)
- [`:core:downloads` README](../core/downloads/README.md)
- [`:core:playback` README](../core/playback/README.md)
