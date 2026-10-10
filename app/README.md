# `:app`

## Purpose

The application module owns the Android app shell: `BoxLoreApplication`, `MainActivity`, navigation wiring, FCM entry points, in-app announcements, surveys, OPML import UI, and the single production composition root in `AppContainer`. It wires feature screens and core services together; feature UI and data engines remain in their owning modules.

## Public API

- The app-scoped updater checks on foreground at most every six hours (failed requests back off for 15 minutes). Settings → Check for updates bypasses the interval. The Home/Library utility capsule gets an icon-only action only for a confirmed newer compatible version. Background checks never open a prompt or start a download. Direct installs read a bounded versioned `update.json` from GitHub Latest, cache the last valid offer and distinguish connection errors from an up-to-date result. Same-version artifact refreshes stay quiet.
- Direct APK updates use an app-private, checksum-keyed resumable transfer. Download, verification and installation are separate explicit actions; the Android installer owns confirmation. Before each handoff, the APK's checksum, package, exact version, Android minimum and signing identity are checked again. Interrupted transfers resume; failed integrity checks discard corrupt bytes. Returning from install permission settings or cancelling Android installation retains the Install action. A changed offer discards stale prepared state. Restoration separates session eligibility from verified-file recovery and retains cancellation and checksum guards.
- `assembleRelease` builds the direct APK. `bundlePlayRelease` builds the Google Play AAB with release optimization/signing, the same application identity and normal notification receiver, but no APK downloader/installer implementation or `REQUEST_INSTALL_PACKAGES` permission. Play availability uses Play Core and caches the confirmed version across process recreation; the explicit action obtains fresh eligibility before starting a flow or finishing a downloaded flexible update. In-progress immediate Play updates resume on foreground. Release artifact validation checks both variants separately, including installer exclusion in Play.
- Announcements use native Material 3 Compose surfaces, typography and buttons. Compact alerts and full-screen messages have a fixed category/close header, scrollable Markdown and optional image, and fixed actions. Controlled accent roles, banner/uncropped cover, headings, lists, callouts, links and emphasis are supported; payload HTML remains text. Release alerts have Download update (native metadata check and transfer), View on GitHub (keeps the alert on return), and Dismiss. Normal alerts retain their optional single action. The browser admin preview follows the same visual specification without executing in the app.
- Announcement payload additions are optional and persist with legacy defaults. Only release alerts are install-channel/version filtered; ordinary and new-episode notifications remain available to both channels. New Play/direct builds retain ordinary broadcast topics and register their install channel. Release sends use direct_users while excluding Play/test topics, with an explicit include-Play override via All installs. Older clients cannot be classified through their shared prod_users topic, so they do not receive these new strict direct-only release sends until updated; the legacy payload/window remains readable for normal sends.
- The updater persists its visible/prepared session through permission settings, Activity recreation and process restart. Opening it refreshes metadata automatically, retaining cached notes while checking and without starting a download. An announcement Download action performs one refresh before transfer. Available-update screens have no redundant check button; failed/current checks retain recovery controls. Its unboxed release-note paragraphs scroll between a fixed header and full-width download/install controls, keeping progress and actions accessible for long notes. Restoration verifies an existing completed file without starting a new transfer or installation. Missing files return to Download; an explicit Install tap rechecks the package/signature and retries Android's permission or installation flow. Completing an update clears the obsolete session on the next app start. Update offers/session preferences are excluded from cloud/device backup.
- `assembleUpdateTest` is signed and optimized like release, uses a loopback-only test manifest and test-only metadata, and subscribes only to test_users for announcements. Its ADB-only AnnouncementTestReceiver previews an exported message locally without FCM; this receiver and test marker are rejected by shipping artifact checks. Test update/session preferences are separate. Its mapping upload and Crashlytics collection are disabled, and analytics mark it internal. Test builds must never be published; they intentionally keep the package/signature so installation can preserve listening data.


- While native predictive Back is held over a regular destination such as Home, the shell previews the saved theme for navigation and its canvas. Cancelling restores the detail palette; committed navigation retains its existing loader handoff. Scaffold foreground ownership prevents inherited black text in dark previews. JVM tests drive real Back start/progress/cancel events without committing.
- Custom theme saves are emitted through the analytics facade after preference persistence succeeds, separately from selecting an existing theme.

- The composition root shares `SubscribedEpisodeCatalog` across foreground sync, subscriptions, automatic downloads and FCM hydration. Pure RSS push hydration fetches the saved URL and matches GUID/enclosure to the original negative ID without a PI lookup or newest-item guess. RSS notification disclosure consent is stored outside backups and is tied to the saved URL. No new on-device periodic notification worker is scheduled; existing optional background auto-download discovery remains independently consented.
- RSS pushes must match the current saved feed URL before immediate display or durable hydration; the worker rechecks subscription and URL after hydration, and notification rendering rechecks local consent after artwork loading. `RssNotificationSyncWorker` reconciles journaled RTDB registrations and FCM topics with network constraints and retry backoff. Settings changes, app-root reconciliation (even with the restore sentinel present), and token changes restart this non-polling worker without waiting behind an earlier retry's backoff; incomplete cleanup survives restart and retries without a maximum-attempt cutoff.

- `NavGraphLibraryDestinations` routes New Episodes row and Play All snapshots through `QueueManager.playContextEpisodes`, so playback follows the visible list rather than reusing a prior show queue.
- `LoreQueueConflictDialog` confirms **Start a Lore queue?** when a Lore card's queue action encounters a non-Lore queue. Localizable copy explicitly states that **Start Lore queue** stops playback and replaces the current queue with the selected episode, then explains swiping right on other Lore cards to add more. **Keep current queue** cancels that operation; the help directs listeners to tap a card to open its episode details and choose **Add to queue** to preserve the existing queue. Queue operations and analytics result keys remain unchanged.

- `BoxLoreApplication.container` exposes the process-scoped `AppContainer`.
- On startup, `BoxLoreApplication` hydrates missing DataStore appearance keys from theme fast-cache on its IO scope, keeping restoration off the first-paint path (Google Backup can restore SharedPreferences without DataStore), and after UI readiness asks `SmartDownloadManager.reconcileScheduleWithPreferences` to keep the Smart Downloads toggle and periodic WorkManager job in sync. It also configures `LearningEventLog` via `BoxcastPrefs.resolveLearnerLogEnabled`: on by default in debug when unset; **always off in release** unless the user has explicitly persisted an opt-in from the debug screen.
- `AppContainer` constructs the shared graph: database, network, auth (`authRepository`), RSS, ranking, catalog, folders (`FolderRepository`), playback, queue, downloads, prefs, and analytics dependencies. It provides the narrow `DeviceIdentityPort` (delegating to `BoxcastPrefs.getOrCreateSyncDeviceId()`) to stamp local mutations for cross-device sync. It constructs `UserSyncCoordinator` with real DAOs, conflict resolvers, and token providers, and wires `CloudSyncTriggerCoordinator` to observe process lifecycle, playback milestones, database mutations, and connectivity state. It builds `LocalEpisodeCatalogRepository` with the main Room database and the downloads-owned cache relinker, allowing a repaired full feed to transactionally re-key safely matched legacy listener ids without creating a reverse module dependency. The catalog instance is shared by `PodcastRepository`, `SubscriptionRepository`, `SubscriptionForegroundSync`, and FCM hydration. The same application scope owns `LegacyRssRepair`.
- Cloud Synchronization (`sync/`): `CloudSyncWorker` runs guaranteed background synchronization via WorkManager (`schedulePeriodicSync` every 6 hours with network constraint, `enqueueOneShotSync` on app backgrounding or network recovery), mapped in `LegacyWorkerFactory`. `CloudSyncTriggerCoordinator` binds to `ProcessLifecycleOwner`, auth state flows, network connectivity changes, and player milestones (PAUSE, COMPLETED, track changes) to trigger non-polling, battery-friendly delta pushes and pulls, and clears the active playback session on sign-out. It exposes `syncStatusFlow` (`Idle`, `Syncing`, `Success`, `Error`) and `triggerManualSync()` consumed by the Account Settings screen.
- Authentication & Deep Links: `MainActivity` handles incoming App Link intents for `/boxlore/auth` (`https://aswin.cx/boxlore/auth`) to complete passwordless email link sign-in via `authRepository.signInWithEmailLink`. It prevents one-time link replay across activity recreation by nullifying intent data and tracking consumed links, and prompts for the user's email via `CrossDeviceEmailDialog` if a magic link is opened cross-device without local pending auth state. `BoxLoreApplication` configures `NetworkModule.authenticator` with `FirebaseAuthAuthenticator` at startup to automatically refresh STS tokens on HTTP 401.
- Install attribution: `AppContainer` wires `InstallReferrerManager.onInstallReferrerResolved` → analytics person properties (`install_channel`). Catalog stays free of `:core:analytics`.
- FCM (`BoxLoreFcmService` + `NewEpisodeFcmLogic`) owns notification_received / tap extras (`notification_type`, podcast/episode ids for snake+camel keys). Push and episode notifications use bounded slot allocation (16 bounded slots for new episodes, 4 for announcements) with normalized intents (`Intent.ACTION_VIEW`, component set to `MainActivity`, `data = null`, and routes passed in `target_route`) so `Intent.filterEquals()` returns `true` across notifications. This ensures Android's `system_server` reuses `PendingIntentRecord`s via `FLAG_UPDATE_CURRENT` instead of leaking up to the 1,000 UID quota. PendingIntent creations are guarded with `try-catch (e: SecurityException)` falling back to posting notifications without `contentIntent` if quota was exhausted prior to reboot. Download scheduling and notification display fail independently. Payloads without a usable episode ID initially open the podcast page; durable hydration replaces that route with the exact local episode when matched.
- Library backup/import analytics (`trackBackupRestoreResult`, import failed) use allowlisted error codes from `LibraryBackupAnalyticsErrors` — never raw exception text.
- `MainActivity` / `BoxLoreAppRoot` own deep-link and session-restore analytics at the shell layer.
- `BoxLoreAppRoot` collects the default-off miniplayer seek preference from `:core:prefs` and supplies the same value to Appearance settings and `PlayerSheetLayout`. Appearance callbacks persist it without a feature-to-feature dependency.
- Appearance theme selection/custom editing is wired through `NavGraphSettingsDestinations` to atomic `UserPreferencesRepository.setThemeSelection`; the app root collects the artwork switch and remembered Custom inputs alongside the existing fast-cache theme values. `BoxLoreTheme` supplies the artwork preference to detail pages. Detail destinations publish temporary colors by their back-stack entry id, and only the navigation theme scope reads the active entry’s animated palette, including its gently tinted surface hierarchy. Pure keeps neutral navbar backgrounds. The mini-player, other pages and app-themed widgets retain the saved app theme. Older Theme settings links open the unified Appearance page.

- `BoxLoreAppRoot` coordinates the Floating-only adaptive player with a shared, transient chrome progress value. After 48dp of deliberate vertical browsing, Lore moves into a fourth icon slot and the mini-player docks in its former circular action position; upward browsing reverses it. `AdaptivePlayerChrome` observes only the NavHost subtree without consuming scroll, excludes fling/overscroll and player interactions, retains compactness across routes, and resets on episode changes or unavailable chrome. The exact main Lore route (`learn`) temporarily forces compact chrome to give curiosity cards more room, without changing the prior browsing state. Leaving Lore restores that state; episode changes reset it, and history/detail routes use ordinary browsing. Player interactions finish before a route retargets chrome. Classic navigation, hidden-navigation screens, and windows below 320dp retain the normal mini-player. The shared critically damped browse spring retargets from its current progress; eased contraction and docking settle without a velocity jump between phases. List tail clearance stays stable throughout the morph; Lore uses the same docking progress to grow its bounded card viewport by the released 72dp.
- The NavHost receives that same progress through designsystem's `LocalAdaptivePlayerCompactProgress`. Explore / Subscriptions floating tab selectors and library Play All controls reclaim the miniplayer row during docking, and restore it during expansion, without changing list padding or reading per-frame progress in screen composition.
- Full-screen library import (`OpmlImportDialog`) is an in-window overlay (JSON and OPML share the same selector) controlled via `OpmlImportDialogActions` (`onDismissRequest`, `onSelectionChanged`, `onConfirmCompleted`, `onSkipCompleted`, `onImportJsonSelected`, `onImportOpmlSelected`, `onSyncAccountSelected`). It supports restoring library shows via OPML/JSON file picker or directly connecting to a cloud account for automatic cross-device sync. It paints the theme background behind the status bar / cutout (`ImportDialogSystemBars`) and pads content with `WindowInsets.safeDrawing` so chrome matches the page instead of an OEM Dialog scrim.
- App-shell UI uses the centralized Google Sans Flex weight tokens from `:core:designsystem`.
- `SharedAppDependenciesHolder` and `DownloadsDependenciesHolder` are installed from the application so workers and Media3 services reuse the same graph.
- `BoxLoreApplication` eagerly initializes Media3 Cast from the manifest-backed `BoxLoreCastOptionsProvider` before playback wiring. The provider resumes saved sessions and enables Cast's reconnection service so controls recover after phone sleep/wake; application-scoped session and transfer callbacks reconcile that lifecycle with Media3, keep process-restored sessions undecided while Cast reconnects asynchronously, and clear stale remote UI only after a failed reconnect or completed remote-to-local handoff. `BOXLORE_CAST_RECEIVER_ID` selects the registered boxlore Custom Web Receiver (`2518A753`; local override supported); Android-receiver compatibility is disabled so Android TV launches the hosted receiver instead of looking for a nonexistent native Cast Connect app. The manifest declares a non-exported `MediaTransferReceiver`, enabling Android 11+ notification/lock-screen output switching without accepting third-party broadcasts. Playback ownership remains in the existing `BoxLorePlaybackService`.
- `HomeScreenWidgetsInstaller` registers lazy adapter factories so an unused widget family does not construct playback/library/ranking projections. Widget collection starts only for installed widgets (or a subsequent enabled broadcast). It installs `NowPlayingWidgetDependencies` (via `NowPlayingWidgetPlaybackAdapter`) and `LibraryWidgetDependencies` (via `WidgetLibrarySourceAdapter`) so home-screen widgets share the app-scoped `PlaybackRepository` / library ports without a second graph. Transport calls and cold-session restore hop from the IO application scope to `Dispatchers.Main.immediate` before touching MediaController. Lettering-roundness, theme, brand, and **Widgets** appearance (`app` default / `system`) changes re-render widgets so chrome matches Appearance or the launcher.
- `DownloadServiceLauncherHolder` is installed with `MediaDownloadService::class.java` so `:core:downloads` can launch the foreground download service without depending on `:core:playback`.
- `LegacyWorkerFactory` maps legacy worker class names to current worker implementations for WorkManager continuity.
- `MainActivity` hosts theme, edge-to-edge setup, app update hooks, surveys, library import state (full-screen `OpmlImportDialog` overlay so onboarding welcome never shows through), the selected floating or classic navigation presentation, and the player overlay. Its start/stop lifecycle informs the application-scoped playback repository whether UI position polling is needed; background audio, persistence, notifications, Cast, and MediaSession behavior remain service-owned.
- NPS survey UI (`surveys/`) applies `NpsSurveyBranching` after each answer so detractor / passive / promoter open-text paths end instead of falling through into the next score band (PostHog open-question `end` branching is not reliably persisted via API).
- JVM `ArtworkPredictiveBackBackgroundTest` checks actual native rendering of the Back wrapper with changing artwork backgrounds beneath the saved app theme.
- JVM `ArtworkNavigationHostThemeTest` uses a real NavHostController and the production slide transitions to verify source capture before first loader composition, stable saved-theme fallback, Back loader inheritance and separation of Home/regular-page colors from transient loader accents.
- `ArtworkNavigationHostTheme` captures source colors via a synchronous destination listener, paints the slide/fade underlay with the departing page’s background, and gives entering loaders temporary inherited colors. Regular pages (including Home) and their navbar use the saved app theme as one unit from the first frame, while only their loaders ease back after the slide; detail loaders keep their own entry-scoped handoff until artwork is resolved. Back also starts with the departing palette before restoring the returning page’s own colors, including rapid returns while its composition is still retained; cold starts, opt-out and theme changes reset the fallback safely. The root Scaffold and predictive-Back wrapper use the current page background so shrinking the page does not reveal the saved-theme surface. Navigation routes, duration, gesture handling and playback remain unchanged.
- `BoxLoreNavHost` owns app route registration and delegates screen bodies to feature modules; stack-slide transitions follow the visible Home → Explore → Library → Lore order, and Home’s first committed content frame unlocks the floating Lore launch animation.
- `NavGraphPodcastEpisodeDestinations` registers episode and podcast detail routes; episode full-path routes define `episodeDescription` as nullable with a default null value to safely handle empty, omitted, or special-character descriptions without argument bundle verification failures.
- `NavGraphSettingsDestinations` registers the settings hub, category pages, download settings, the dedicated `"feedback"` full-page destination wired to `:feature:settings:feedback:FeedbackScreen`, and the Support sub-page within the `"settings"` route (selected with `settings?page=support`), rendered by `:feature:settings:pages:SupportDevelopmentPage` (with listening-hours insights flow and support screen visibility callbacks to hide player chrome).
- Cold-start destination precedence (see `StartDestinationResolver`): incomplete onboarding → offline Downloads (no deep link) → Appearance **Open app to** Subscriptions or Downloads (no deep link) → Home. Deep links and push `target_route` still win over Open app to. When cold start opens Subscriptions or Downloads via that pref, `openedToLandingOnLaunch` makes Back navigate to Home (not Library hub); Library-tile entry still pops to Library. Offline-forced Downloads without that pref still exits on Back. That Home navigation is owned by `PredictiveBackWrapper` (NavHost has nothing to pop), so peek scale must restore after the gesture — see `:core:designsystem`.
- Process latest-episode sync (`SubscriptionForegroundSync`) and the versioned legacy RSS repair are started from `BoxLoreAppRoot` after onboarding. Repair is enabled only for upgraded installs running `0.0.18` through `0.0.30`, except `0.0.20`, starts only while online with at least one eligible pure `rss:` subscription, and admits at most one pass per app process. A fresh install on an allowed version writes the completed marker without inspecting or changing catalog rows, so updating a fresh `0.0.18` install to `0.0.19` cannot enable repair later. Unsupported versions and invalid package metadata fail closed. App foreground/screen-on events do not request another pass. While an eligible pass is active, the shell shows a persistent top popup with the boxlore loader; it cannot be dismissed and disappears when the pass settles. Returning to the app still requests normal subscription refresh (`SubscriptionResumeRefreshLogic` skips the first `ON_START` so Home keeps its 2s first-paint delay). Subscriptions-first launches still fetch because Library calls `requestRefresh` as soon as that screen is visible. Identified FCM payloads (guid/enclosure) that do not match a local episode no longer fall back to an unrelated newest tip.
- Tab destinations in `NavGraphTabDestinations` wire Home / Explore / Learn / Library / player entry points to feature screens. Home’s Offline Mix “View all downloads” action navigates directly to Library Downloads through this shell-owned wiring. Lore history deep-links use `entryPoint=learn` (canonical glossary; legacy `learn_history` still normalizes to `learn`). Explore and Subscriptions apply Appearance **Default tabs** when the route does not already pick a tab (genre Explore, `tab=trending` / `for_you`, and `library/subscriptions?tab=0|1` still win).
- Home receives the shared catalog and ranking instances it actively uses; endpoint-backed
  editorial rows are loaded inside `:feature:home` through that catalog instance.

- New-episode FCM callbacks persist GUID/enclosure hints in unique delivery work before network I/O. Push hydration still bypasses automatic freshness limits and remains independent of background polling consent. `AutoDownloadLifecycle` gates the existing subscription refresh timer with process foreground state, replays cached claims on foreground entry without another RSS sweep, and reconciles explicit background-check consent and transfer constraints. Startup cancels historical catch-up jobs and automatic transfers whose discovery source is unknown; cached claims remain recoverable on foreground refresh or push. Background consent is excluded from legacy backup, cloud backup, device transfer, library export, and cloud settings. Only foreground/manual/import/push catalog persists invoke the ordinary download callback; background discovery admits its own releases through its runtime gate.

- Delayed notification hydration updates only the matching active release; dismissed alerts and newer releases are preserved, including after artwork I/O. Hydration exceptions and unresolved releases share a five-retry limit. On startup or a Wi-Fi policy change, automatic transfer reconciliation compares persisted work constraints and cancels only mismatches, preserving correctly scheduled cold-start workers.

- Download lifecycle reconciliation scans cached release claims only while the process is foreground. Resume and preference reconciliation share `AutoDownloadForegroundScan`: process stop cancels its owned job, each show rechecks foreground state, and in-flight enqueue admission uses the same live guard. A later foreground entry replays interrupted pending claims. Push admission can replace pending background-gated transfers before lifecycle cancellation finishes.

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
    NavGraphSettingsDestinations.kt
    NavGraphLibraryDestinations.kt
    LaunchSubscriptionsBack.kt
    LaunchSubscriptionsBackDecision.kt
    StartDestinationResolver.kt
    NavGraphPodcastEpisodeDestinations.kt
    PushTargetRouteAllowlist.kt
  connectivity/
  sync/
    CloudSyncWorker.kt
    CloudSyncTriggerCoordinator.kt
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
      ImportDialogSystemBars.kt
      ImportNotificationPermissionCard.kt
      OpmlImportEffects.kt
      OpmlImportProgressContent.kt
  updates/
  util/
```

Routes include onboarding, home, learn, briefing, settings, debug, explore, library sub-routes, podcast details, and episode details. The `settings` route supports optional `page` and `fromOnboarding: Boolean` parameters (`NavGraphSettingsDestinations.kt`); when opened from onboarding, the bottom navigation bar is suppressed via `shouldShowBottomNav` and back navigation triggers completion and navigation to home if the listener is authenticated and email-verified (`handleSettingsOnBack`). Deep-link schemes remain `boxlore://`, `boxcast://`, plus HTTPS App Links `https://aswin.cx/boxlore/share` and `https://aswin.cx/boxcast/share` (`autoVerify`). Domain ownership requires Digital Asset Links at `https://aswin.cx/.well-known/assetlinks.json` for package `cx.aswin.boxlore` with the Play App signing SHA-256.

## Dependencies

- Project dependencies: all `:feature:*` modules plus `:core:analytics`, `:core:catalog`, `:core:designsystem`, `:core:domain`, `:core:downloads`, `:core:model`, `:core:network`, `:core:playback`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, Firebase, PostHog, WorkManager, Media3 client usage, Coil, Retrofit, Kotlin serialization, and Play Core.
- Reverse-edge rule: feature modules and core modules must not construct independent application graphs; they receive instances from `AppContainer` or the dependency holders.

## Startup scheduling

`StartupWorkGate` is process-scoped and releases optional cloud coordinator initialization, periodic sync scheduling, Smart Downloads scheduling/catch-up, foreground subscription sync and legacy RSS repair after the initial screen commits. Optional initialization uses a suspending mutex so ready does not release a burst of initializers. Home signals after loaded local content and two frames; other initial routes release after their first two frames. A headless worker/widget launch releases at the first idle main queue; `MainActivity` identifies a UI launch before creating content. If the first Activity stops before content is ready, background startup also releases; configuration recreation waits for the replacement UI. Recreated roots reuse the same gate. Repeated signals and cancelled initializers are safe (`StartupWorkGateTest`). Adaptive ranking telemetry also waits for readiness. Appearance flows use restored fast-cache values while background hydration fills missing DataStore keys; existing choices are never overwritten. Play update checks begin after the first two Compose frames. Playback/session restoration and preference identity remain owned by the existing composition root.

- Release runtime dependency snapshots include Jsoup through `:core:catalog`, which owns shared show-notes resource and chapter parsing. No direct parser-library dependency is added here.

## Threading / lifecycle

- `AppContainer` is created once from `BoxLoreApplication.onCreate` and is application-scoped.
- Workers resolve dependencies through installed holders before doing background work.
- Media3 services lazily resolve the shared graph after application startup.
- UI composition, navigation, OPML import state, surveys, and player overlay state are Activity-scoped.
- `BoxLoreAppRoot` stacks the mini player using designsystem's `appMiniPlayerTopOffset`, including Android's bottom system inset for both navigation styles. Classic's internally padded navbar and Floating's externally padded navbar both retain the matching player gap under gesture or three-button navigation. Compact Floating docking uses the same bottom inset. Routes remain Home / Explore / Library / Lore (`learn`) for both presentations.

## Persistence & identity

- `applicationId` is `cx.aswin.boxlore`.
- Manifest service, receiver, activity, and worker class names are system-facing identities.
- `LegacyWorkerFactory` preserves WorkManager upgrades from legacy worker class names.
- Build config reads `BOXLORE_API_BASE_URL`, `BOXLORE_PUBLIC_KEY`, and the public `BOXLORE_CAST_RECEIVER_ID`, with legacy API/key local-property names still accepted for existing developer environments.
- Core modules own Room filenames, DataStore names, SharedPreferences names, ranking identity, RSS IDs, and playback media IDs.
- Google Backup / device transfer include Room, SharedPreferences, and `files/datastore/`. WorkManager's `androidx.work.workdb*` is excluded so periodic jobs are rebuilt from DataStore.

## Testing notes

- Native announcements have no WebView, JavaScript bridge, render handshake or alternate fallback layout. Closing, scrolling and actions work immediately; image failure removes only the image. Compact surfaces use surfaceContainer and a subtle backdrop. Announcements and the updater set system-bar icon contrast on their own Dialog window without changing the underlying Activity; `NativeDialogSystemBarsTest` covers light/dark and dimmed surfaces.
- `AnnouncementPolicyTest` and `AnnouncementPreferencesTest` cover optional fields, legacy category fallback, channel/version filtering, isolated audiences and persisted styles/actions. `UpdateSessionTest` and `AppUpdatesPanelTest` cover saved files, process recreation and the explicit permission-return retry. Sender tests compare browser/workflow approval digests and reject changed drafts before delivery. `AnnouncementRenderTest` covers release/custom actions, scoped accent roles and Markdown compatibility. Shipping artifact checks still reject isolated receivers/markers.
- Push actions share cold/warm launch routing: registered share/episode/show links use Navigation, unmatched web links open externally, and update targets open the native updater. Known utility app-scheme links resolve to allowlisted routes, including Feedback and Lore. Legacy release APK actions map to the updater; general APK links remain ordinary links. Empty or non-allowlisted targets do not render action buttons. Unmatched verified App Links exclude boxlore from the external chooser to avoid a launch loop. `PushActionRouterTest` exercises a real JVM NavHost, and `PushAnnouncementActionTest` checks the notification's actual PendingIntent destination without sending FCM.
- `updates/` JVM regressions cover newer/current/incompatible manifests, six-hour checks, retry backoff, manual override, coalesced requests, offline cached offers, version refreshes, malformed/untrusted/oversized metadata, checksum failure, exact range resume and servers ignoring Range. Fake HTTP responses keep these checks away from production. `UpdateEntryPointsTest` covers the action appearing/disappearing while Settings/Feedback remain usable. `AppUpdatesTest` covers quiet checks, explicit transfer, dismissal, retries, changing offers and Play not using the APK installer. `ReleaseNotesMarkdownTest` verifies adjacent emphasis and delimiters inside links or code without swallowing later formatting. Additional fixtures cover cancelling a blocked socket, package/version/signing identity and the update screen's notes, error and installation states.

- Unit tests live under `app/src/test`, including app container smoke coverage, sync coordinator lifecycle stability (`AppContainerSmokeTest`), `CloudSyncWorker` WorkManager execution, resolution, and periodic scheduling (`CloudSyncWorkerTest`), `CloudSyncTriggerCoordinator` auth transition handling, process start/stop debouncing, playback milestone detection, and database mutation coalescing (`CloudSyncTriggerCoordinatorTest`), worker factory mapping, FCM payload parsing (type + snake/camel ids, feedUrl/guid/enclosure), new-episode route/id, bounded slot bounds, negative-modulo non-negative floor, normalized intent `filterEquals` equivalence, and auto-download WorkManager enqueueing helpers (`NewEpisodeFcmLogicTest`), opted-in feed hydration before notify, library backup analytics error codes, import-dialog system-bar edge-to-edge (`ImportDialogSystemBarsTest`), push-target route allowlisting, bottom navigation visibility, onboarding origin derivation, and settings back navigation handling (`BottomNavPresentationTest`), cold-start destination precedence (`StartDestinationResolverTest`), launch-landing Back decisions (`LaunchSubscriptionsBackDecisionTest`), and episode navigation route building and argument decoding (`EpisodeNavRouteRegressionTest`).
- Navigation and feature UI behavior are covered mainly in feature module tests and Maestro smoke flows.
- `AdaptivePlayerChromeControllerTest` additionally covers Lore-only forcing, prior-state restoration, history exclusion, episode/availability resets and player-interaction priority.
- `AdaptivePlayerScrollLogicTest` covers travel thresholds, reversal, source/axis filtering, gesture boundaries, player-interaction suppression, and reset behavior for adaptive chrome.
- Shared player-anchor regression coverage lives in `:core:designsystem` (`NavigationChromePlacementTest`), including Classic navigation with 48dp system buttons and multiple densities; app assembly verifies the shell uses that helper.

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

- Launch branding uses the white B/microphone vector on electric purple (#573DF5) for the system splash and adaptive launcher icon. The foreground is inset for launcher masks and also supplies the themed monochrome icon.

### Upright brand wordmarks

- Notification small icons use the monochrome italic B-and-microphone app mark; launcher and splash branding remain italic.

### Debug notification preview

Debug builds include an ADB-only `TestNotificationReceiver`, protected by `android.permission.DUMP`. With notification permission enabled, run `adb shell am broadcast -n cx.aswin.boxlore/.debug.TestNotificationReceiver -a cx.aswin.boxlore.DEBUG_TEST_NOTIFICATION` to preview the app notification mark. The receiver and its channel strings are excluded from release builds.

### Optimized release contracts

Release uses R8 full-mode code optimization and resource shrinking. App rules preserve the Retrofit API and its generic return types, Gson model field names/default constructors, WorkManager names/constructors and permanent component aliases. Runtime libraries own their consumer rules; repositories, playback, preference implementations and unrelated interfaces no longer have blanket package keeps. The historical OEM splash-screen bridge remains protected. App Check provider installation is variant-specific: the debug SDK is a debug-only dependency, while release retains Play Integrity. The artifact gate rejects debug-only App Check components and the test-notification receiver.

`.github/scripts/verify_release_contracts.py` compares pre-R8 release classes with the actual APK and AAB: worker/input-merger constructors, manifest and metadata providers, legacy services, generated Room implementations, nested Gson fields/defaults and generic field types, TypeToken signatures, Retrofit annotations/generic types and dynamic serializers (including widget snapshots). The APK resource table also verifies dynamically looked-up onboarding covers, the Android Auto label font and named resource URIs found in main/release Kotlin sources. Named raw-resource bytes must survive in both APK and AAB. The two notification chimes are explicitly retained because channel URIs resolve them by name; channel IDs and user settings remain unchanged. Both publication paths fail before publishing if a contract breaks. An optional unsigned release preflight uses the same optimization rules without production keys or SDK mapping uploads; full artifact builds do not run automatically on PRs. Fast contract-regression tests still run in PR CI.

Pending automatic-download ledger entries remain replayable by existing foreground reconciliation. This packaging change does not reset WorkManager or replay old notifications. Artifact checks cover optimization contracts; release-device smoke testing still covers SDK behavior, sign-in, playback, download execution and restored data. See [Testing](../docs/TESTING.md#optimized-release-validation).

Update release notes support a listener Markdown subset: headings, bullet and numbered lists, emphasis, inline code and HTTP(S) links. Plain-text notes remain compatible; HTML is not executed.

The update action footer paints behind the system navigation area; navigation insets apply inside the footer so actions stay above the gesture handle without a separate bottom color strip.

When no update is offered, the update page shows the boxlore wordmark, installed version and centered status. The no-update composition is vertically centered, and the status avoids repeating the brand name already shown in the logo. Its tonal check action shows progress and prevents duplicate checks while loading; errors keep a recovery action.

The up-to-date page offers a collapsed Coming next preview when the direct update feed contains optional `upcoming` Markdown. It is labelled Not released yet, cached independently of update availability and retained through failed checks. Older feeds omit it; Google Play update checks do not use this direct-release preview.

In-app release and upcoming notes hide GitHub pull-request links and parenthesized PR numbers at rendering time, including cached notes. Ordinary help and product links remain visible; source release notes are unchanged.

Ordinary browsing of the update page is not persisted as a startup destination. Only a matching prepared pending update can restore the install page; stale visibility flags and completed-update sessions are cleared. Returning from Android install-permission settings still preserves the pending install flow.

An initial update-check state must perform a lookup even when timestamps survived a process restart. Once a result exists, normal interval and retry limits apply; timestamps alone cannot leave the page indefinitely checking.
