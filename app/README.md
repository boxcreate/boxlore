# `:app`

## Purpose

`:app` is the Android application shell and only production composition root. It owns `BoxLoreApplication`, `MainActivity`, `AppContainer`, the navigation host and adaptive shell, push and App Link entry points, announcements and updates, the library import overlay, API-session setup, and WorkManager jobs that need the whole graph. Screens and engines live in `:feature:*` and `:core:*`; `:app` only wires them.

## Public API

- `BoxLoreApplication.container` exposes the process-scoped `AppContainer`. `MainActivity`, `BoxLoreNavHost` and feature assemblers read it; nothing else builds repositories or a second graph.
- `AppContainer` implements `SharedAppDependencies` and `DownloadsDependencies` and is installed in `SharedAppDependenciesHolder` and `DownloadsDependenciesHolder`, so workers, widgets and Media3 services reuse the same graph.
- `ui/BoxLoreAppRoot` is the root composable: theme, NavHost, player overlay, startup effects and the single Turnstile host.
- `BoxLoreNavHost` and the `NavGraph*Destinations` files register every route and delegate screen bodies to features.
- `LegacyWorkerFactory` resolves historical worker class names for WorkManager.
- `PushTargetRouteAllowlist` is the only list of in-app routes a push may open.

## Composition root and startup

- `AppContainer` is created once in `BoxLoreApplication.onCreate` and wires every repository in the [object graph order](../ARCHITECTURE.md#identity-and-storage).
- `ui/rememberOnboardingViewModel` (the `OnboardingViewModel` all onboarding flows share), `ui/rememberLibraryImportViewModel` (imports and their overlay) and `ui/rememberShellPendingDialogs` (the pending Lore queue question) live in the activity's `ViewModelStore`; never create them with `remember`.
- The one `PlaybackRepository` is built lazily. Cast state waits in `DeferredCastSessionState` until it exists, so widget, worker and notification starts never build playback on the main thread.
- The container gives core modules ports instead of reverse dependencies: `feedTelemetry` for feed events, `rolloutSwitches`, and a `DownloadServiceLauncherHolder` that starts `MediaDownloadService`.
- `onCreate` order: API sessions first, then the Coil image loader (before widgets and services), Media3 Cast from `BoxLoreCastOptionsProvider`, the shared `UserPreferencesRepository`, the container and its holders. PostHog starts later in the background (`PostHogStartup`), after `AnalyticsHelper.initPolicy`.
- `StartupWorkGate` holds optional work (cloud sync, Smart Downloads reconciliation, address repair scheduling, foreground subscription sync, legacy RSS repair, the first widget preview) until the first screen commits, one initializer at a time.
- Home releases the gate after its content and two frames, other routes after two frames, a headless worker or widget launch at the first idle main queue; an early Activity stop also releases it, recreation does not.
- Missing DataStore appearance keys are filled from the theme fast cache off the main thread, never overwriting existing choices.
- `TabletAlphaStartupGate` shows a one-time notice on tablet-sized windows and displays before the root composes; update and survey prompts wait behind it.

## Navigation and adaptive shell

- `MainActivity` owns edge-to-edge setup, the splash (`SplashHoldPolicy`), the single window and posture collector, `SafeUriHandler` as `LocalUriHandler`, email-link intents and the import overlay.
- `resolveStartDestination` picks the cold-start route: incomplete onboarding, then offline Downloads, then **Open app to**, then Home. Deep links and push `target_route` win over Open app to; a landing it opened goes Back to Home.
- Floating and Classic navigation share Home, Explore, Library and Lore (`learn`). Reselecting Explore while it shows opens search (`ExploreSearchRequestKey`).
- `AdaptivePlayerChrome` compacts the Floating bar and docks the mini player while browsing; Lore forces compact. Screens read `LocalAdaptivePlayerCompactProgress` ([product surfaces](../ARCHITECTURE.md#product-surfaces)).
- `TabletAppShell` keeps both NavHosts (`TabletDetailNavigation`) alive while resizing, with a rail on tablets and a player and detail pane in split windows and on half-open folds; a hidden detail host drops to CREATED and can't take Back.
- `ArtworkNavigationHostTheme` lets detail destinations publish artwork colours per back-stack entry; regular pages use the saved theme from their first frame, including during predictive Back.
- Email sign-in opens Settings on Account (`ACCOUNT_PAGE_ROUTE`) once per request; Account and onboarding hide the player overlay.
- Window gates, state retention and transition coverage follow the [adaptive UI and state contract](../ARCHITECTURE.md#adaptive-ui-and-state).
- `AppLanguageController` (`AppContainer.appLanguage`) applies the App language: the per-app language on Android 13+; on 12/12L a stored choice applied in `MainActivity.attachBaseContext`, where notifications, widgets and Android Auto keep the phone's language ([translating](../docs/i18n/TRANSLATING.md)).

## Push, links and sharing

- `navigation/WebAppLinks` lists the verified hosts and paths and must match the manifest's `autoVerify` filters (one host per filter): `boxlore.in` `/share`, `/auth`, `/__/auth/links`, and `aswin.cx` `/boxlore/share`, `/boxcast/share`, `/boxlore/auth` for links older builds shared.
- `boxlore://` and `boxcast://` stay registered. Episode deep links live in `navigation/EpisodeShowLinks`; patterns are only added, and `show=` fills the route's `podcastId` ([episode IDs](../docs/EPISODE_IDS.md)).
- `MainActivity` handles email sign-in links (`AppContainer.emailLinkSignIn`): it clears the intent data, refuses replay across recreation, and asks for the address (`CrossDeviceEmailDialog`) when opened on another device.
- `BoxLoreFcmService` saves each new-episode push as unique `NewEpisodeDeliveryWorker` work before any network call. Download scheduling and notification display fail independently.
- `NewEpisodePushHydration` matches a push by GUID or enclosure, then catalog key, then show and recipe ID; without a match the notification opens the push's own IDs.
- New-episode notifications are tagged by show ID and grouped under `boxlore_new_episodes` (silent summary from two; at most 16, newest replacing oldest). Taps open `MainActivity` with `target_route`; the show ID in the intent identifier keeps each PendingIntent distinct; without quota it posts with no tap action.
- `dispatchPushAction` routes push actions on cold and warm launches: allowlisted routes navigate, registered links use Navigation, update targets open the updater, and other web links open externally with boxlore excluded from the chooser.
- An `rss:` push shows only if it matches the saved feed URL and local consent; `RssNotificationSyncWorker` reconciles registrations and topics.
- Announcements are native Compose surfaces with a Markdown subset; payload HTML stays text. Only release alerts are filtered by install channel and version.
- `AndroidAutoTip` shows a non-Play install once how to put boxlore on the car's screen (a `TIP` announcement if opened while driving, else a notification when a drive starts mid-playback), never after Android Auto opened boxlore's lists; its record (`boxlore_android_auto_tip`) isn't backed up.
- `SupportPushTopicSync` keeps support topics in step with each conversation refresh. A reply push opens `feedback?thread=<id>`, closing an open tablet detail first.
- `ShareManager.episodeShareTargets` comes from `AppContainer.episodeSharePreparer`; `EpisodeShareRegistrationWorker` retries a registration that misses its short wait.

## In-app updates

- `InstallChannel` picks the flow: the Play build or a Play install uses Play Core (`PlayAppUpdateHelper`), anything else reads a versioned `update.json` from the GitHub Latest release; the same rule sets the API channel header.
- Foreground checks are rate-limited with failure backoff; Settings → Check for updates bypasses the limit. Background checks never prompt or download.
- Direct updates download, verify and install as separate steps, rechecking checksum, package, version, minimum Android version and signer before each handoff; sessions survive permission screens and restarts, outside backups.
- Build types: `release` is the direct APK with the installer and `REQUEST_INSTALL_PACKAGES` (`src/direct`); `playRelease` is the Play AAB without either; `updateTest` is isolated and never published ([isolated testing](../docs/TESTING.md#isolated-update-and-announcement-testing)).

## API sessions

- `ApiSessionSetup.install` starts the `:core:network` session manager behind the splash, sets version and channel headers, supplies App Check (`FirebaseAppCheckTokenSource`) to the session exchange only, and forwards foreground and connectivity. Details: [`core/network/README.md`](../core/network/README.md).
- `session/turnstile` runs the check (sign-in without App Check, or a route asking for verification) while on screen: an invisible, origin-locked WebView, in the shared sheet when it needs interaction. Failure, timeout or Skip continues without it; a remote switch stops step-ups.

## Sync, switches and repair jobs

- `CloudSyncTriggerCoordinator` syncs on process lifecycle, auth, connectivity, playback milestones and database changes, and exposes `syncStatusFlow` and `triggerManualSync` to Account. `CloudSyncWorker` adds a periodic sync and a one-shot flush.
- Feed fix switches (`FeedFixSwitches`) fail open: a fix runs unless its switch is on.
- Job pause switches (`SavedKillSwitch`) follow the live answer and save it; until then the saved answer decides (none: the job runs).
- The feed-first switch (`FeedFirstSwitch`) fails closed (saved answer, else off) and is fixed for the process. `RolloutFlagMemory` saves answers whenever flags load; all reach `:core:catalog` through `AppContainer.rolloutSwitches`.
- `UpgradeRepairWorker.scheduleIfNeeded` runs on every launch (headless too) and enqueues the feed address repair only while stored state says work is left; a restore enqueues it again.
- `PodcastNotificationRegistrationWorker` registers a Podcast Index show's notifications again after its saved address changes.
- `LegacyRssRepairLaunchGate` enables the legacy RSS repair only for upgraded installs of allow-listed versions; fresh installs settle without changes and anything else fails closed.
- `AutoDownloadLifecycle` scans cached release claims only while the process is in the foreground; background discovery keeps its own consent.
- `AutoBackupWorker` writes JSON backups to the picked folder, pruning older `boxlore_auto_backup_*` files; folder loss isn't retried.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/
  BoxLoreApplication.kt  AppContainer.kt  MainActivity.kt  LegacyWorkerFactory.kt
  analytics/      PostHogWiring, FeatureAdoptionWiring
  connectivity/   AndroidConnectivityObserver
  language/       AppLanguageController
  fcm/            BoxLoreFcmService, FcmPayloadParser, NewEpisodePushHydration,
                  delivery and registration workers, SupportReplies
  backup/         automatic backups
  lifecycle/      AutoDownloadLifecycle, AutoDownloadForegroundScan
  navigation/     BoxLoreNavHost, NavGraph*Destinations, StartDestinationResolver,
                  WebAppLinks, EpisodeShowLinks, PushActionRouter,
                  PushTargetRouteAllowlist, TabletDetailNavigation, AccountPageRoute
  session/        ApiSessionSetup, InstallChannel, turnstile/
  share/          EpisodeShareRegistrationWorker
  sync/           CloudSyncWorker, CloudSyncTriggerCoordinator, UpgradeRepairWorker,
                  switches, AnalyticsFeedTelemetry
  wearlink/       WearLinkListenerService, PhoneLinkAdapters, wiring
  ui/             BoxLoreAppRoot, StartupWorkGate, TabletAppShell, TabletAppPlayer,
                  AdaptivePlayerChrome, announcement/, libraryimport/, logic/
  updates/        AppUpdates, UpdateChecker, direct and Play sources, AppUpdatesPanel
  util/           InstallSource
  widgets/        HomeScreenWidgetsInstaller, widget adapters and HomeScreenWidgetPreview (Settings' widget pictures)
src/direct/       APK downloader and installer (debug, release, updateTest)
src/playRelease/  Play stub installer
src/debug/        App Check debug provider, ADB-only notification preview receiver
```

## Dependencies

- Project: every `:feature:*` module, plus `:core:analytics`, `:core:auth`, `:core:catalog`, `:core:designsystem`, `:core:domain`, `:core:downloads`, `:core:model`, `:core:network`, `:core:playback`, `:core:ranking`, `:core:rss` and `:core:support`. Tests use `:core:testing`.
- Libraries: Compose (Material 3 Adaptive, WindowManager), Navigation, Firebase, PostHog, WorkManager, Media3 Cast and Session, Coil, Retrofit, Haze, AndroidX WebKit and Play Core.
- Core and feature modules never build an application graph; they get instances from `AppContainer` or the dependency holders.

## Threading / lifecycle

- `AppContainer`, `StartupWorkGate`, the updater and sync coordinators are application-scoped; long work runs on IO.
- Composition, navigation, import and player overlay state are Activity-scoped. Activity start and stop tell the shared `PlaybackRepository` whether to poll UI position.
- Background audio, notifications, Cast and the MediaSession stay owned by `BoxLorePlaybackService`.
- Widget transport calls hop to `Dispatchers.Main.immediate` before touching the MediaController; update UI actions are serialized on the main thread.

## Persistence & identity

Shared contracts (application ID, IDs, deep-link schemes, core-owned preference files, worker bridges, the share, re-registration and repair work names) are in [identity and storage](../ARCHITECTURE.md#identity-and-storage) and [upgrade failsafes](../ARCHITECTURE.md#upgrade-failsafes). App-owned names that must not change:

- Manifest components: `.BoxLoreApplication`, `.MainActivity`, `.fcm.BoxLoreFcmService`, `.wearlink.WearLinkListenerService` and the `BoxLoreCastOptionsProvider` metadata.
- Worker classes: `CloudSyncWorker`, `UpgradeRepairWorker`, `PodcastNotificationRegistrationWorker`, `EpisodeShareRegistrationWorker`, `NewEpisodeDeliveryWorker`, `RssNotificationSyncWorker`, `AutoBackupWorker`.
- Unique work: `boxlore_cloud_sync_periodic`, `boxlore_cloud_sync_flush`, `boxlore_auto_backup_periodic`, `boxlore_auto_backup_now`, `rss-notification-registration-sync`, `new-episode-delivery-<hash>`.
- Notification channels: `boxlore_announcements_v2`, `boxlore_new_episodes_v1`, `boxlore_silent_v1`, `boxlore_feedback_replies_v1`.
- SharedPreferences: `boxlore_support`, `boxlore_direct_updates`, `boxlore_play_updates`, `boxlore_update_session` and their `boxlore_test_*` counterparts.
- `noBackupFilesDir` files: `feed_first_flag`, `kill_switch_<switch>`, `upgrade_repair_notifications`, `rss_notification_consent`, `rss_notification_registrations`, `fcm_topics_synced`.
- Routes: `ACCOUNT_PAGE_ROUTE` (`settings?page=account`), `tablet_context_root`, and every route in `PushTargetRouteAllowlist`.
- Backup and device transfer include Room, SharedPreferences and `files/datastore/`, excluding WorkManager's database, the API session, the NPS store, update state, background download consent and `auto_backup` (`res/xml/data_extraction_rules.xml`).
- `generateLocaleConfig` (`res/resources.properties`) and `localeFilters` define the app's languages; debug adds en-XA and ar-XB pseudo-locales.
- Build config reads `BOXLORE_API_BASE_URL`, `BOXLORE_PUBLIC_KEY` (both accept `BOXCAST_*` fallbacks) and `BOXLORE_CAST_RECEIVER_ID`; debug builds may read `BOXLORE_APPCHECK_DEBUG_SECRET` from `local.properties`.

## Testing notes

JVM tests in `app/src/test` use `:core:testing` fixtures, Robolectric and real NavHosts; feature UI is mostly covered in feature modules.

| Area | Test classes |
| :--- | :--- |
| Composition root and startup | `AppContainerSmokeTest`, `StartupWorkGateTest`, `LegacyWorkerFactoryTest`, `DeferredCastSessionStateTest`, `SplashHoldPolicyTest`, `TabletAlphaStartupDecisionTest` |
| App language | `AppLanguageControllerTest`, `OnboardingAppLanguageRecreationTest` |
| Navigation | `StartDestinationResolverTest`, `LaunchSubscriptionsBackDecisionTest`, `ExploreTabReselectTest`, `EpisodeNavRouteRegressionTest`, `AccountPageRouteTest`, `ArtworkNavigationHostThemeTest` |
| Adaptive shell | `TabletAppShellTest`, `TabletPlayerReflowTest`, `TabletShellGeometryTest`, `TabletNavigationRailTest`, `BoxloreFoldAdapterTest`, `AdaptivePlayerChromeControllerTest` |
| Links and push | `WebAppLinksTest`, `WebAppLinksManifestTest`, `EpisodeShowLinksTest`, `PushActionRouterTest`, `FcmPayloadParserTest`, `NewEpisodePushHydrationTest`, `AnnouncementPolicyTest` |
| Workers and sync | `CloudSyncWorkerTest`, `CloudSyncTriggerCoordinatorTest`, `UpgradeRepairWorkerTest`, `PodcastNotificationRegistrationWorkerTest`, `EpisodeShareRegistrationWorkerTest`, `NewEpisodeDeliveryWorkerTest` |
| Switches and analytics | `FeedFirstSwitchTest`, `SavedKillSwitchTest`, `RolloutFlagMemoryTest`, `PostHogWiringTest` |
| Updates | `UpdateCheckerTest`, `AppUpdatesTest`, `AppUpdatesPanelTest`, `UpdateSessionTest`, `ApkIdentityTest` |
| API sessions | `ApiSessionAppTest`, `TurnstileStepUpControllerTest`, `TurnstileStepUpHostAdaptiveTest` |
| Library import | `ImportFlowModelTest`, `ImportDialogAdaptiveTest`, `ImportAccountHandoffTest` |
| Backup rules | `BackgroundCheckBackupRulesTest` |

`ShellPhoneGoldenTest` is opt-in ([screenshots](../docs/TESTING.md#screenshots)).

```bash
./gradlew :app:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs the app JVM tests in their own shard with a CI Firebase stub, plus detekt and `:app:dependencyGuard`.
- `release-contracts.yml` builds the optimized APK and Play bundle and checks their release contracts ([optimized release validation](../docs/TESTING.md#optimized-release-validation)).

## See also

- [`ARCHITECTURE.md`](../ARCHITECTURE.md)
- [`docs/TESTING.md`](../docs/TESTING.md)
- [`:core:network` README](../core/network/README.md)
- [`:core:catalog` README](../core/catalog/README.md)
- [`:core:playback` README](../core/playback/README.md)
- [`:core:designsystem` README](../core/designsystem/README.md)
