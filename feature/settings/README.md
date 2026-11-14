# `:feature:settings`

## Purpose

Owns the Settings hub and its pages, sign-in, the Smart and automatic download screens, and Feedback with its support conversations. It presents data from injected core dependencies and does not own catalog engines, ranking persistence, playback services, download workers, preference storage or Room schemas.

## Public API

- `SettingsScreen`: the hub and every page, on the app's `settings?page=…` route. It takes `SettingsScreenConfig`, `SettingsRepositories`, `RegionSettings`, `AppearanceSettings`, `PlaybackSettings`, `LibraryBackupWriters` and `DownloadsNavigation`.
- `SettingsScreenConfig.initialPage` opens a page directly (`account`, `sync`, `library`, `recommendations`, `appearance`, `theme`, `playback`, `downloads`, `privacy`, `about`, `support`); anything else opens the hub. `isOnboarding` makes Back leave Settings at once.
- Shell callbacks on `SettingsScreenConfig`: `onSendFeedback`, `onCheckForUpdates` (the shell owns release checks and installation), and `onSupportPageVisibilityChanged` / `onAccountPageVisibilityChanged`, used to hide player chrome.
- `SettingsViewModel`, built by `SettingsViewModelAssembler` (`create` / `factory`): the Add RSS flow and Reset recommendations.
- `AppearanceUiState`, `AppearanceActions`, `AddableWidget`, `AppearanceHeroPreview` and `AppearanceWidgetPreview`; `PlaybackUiState` and `PlaybackActions`; `LibraryDiscoveryPreferences` and `SavedShowsControl`. App wiring persists every value through `:core:prefs`.
- `FeedbackScreen(supportRepository, boxcastPrefs, onBack, bottomContentPadding, openThreadId)` on the app's `feedback?thread=<id>` route, plus `FeedbackViewModel`, `DiagnosticCollector`, `LogcatCollector` and `SharedPrefsRetryKeyStore`.
- `AutoDownloadSettingsScreen` and `SmartDownloadsSettingsScreen`, each taking `UserPreferencesRepository` and `onBack`.
- `LogsPreviewDialog` (in `feedback/FeedbackDialogs.kt`).

## Navigation and tablet split

- `ProfileSettingsDestination` lists every page; its `parent` is where Back goes (Appearance's pages → Appearance, the custom theme editor → Theme & colors, LeaveMeAlone → Library). `resolveSettingsBackAction` decides Back; a page opened directly leaves Settings.
- On tablet windows `AdaptiveSettingsLayout` puts a category list beside the page, highlighting its top-level category (`settingsCategoryOf`; Account sits under Cloud Sync). Onboarding and Support stay full-page.
- Window gates and state retention follow [`ARCHITECTURE.md#adaptive-ui-and-state`](../../ARCHITECTURE.md#adaptive-ui-and-state).

## Appearance

- The overview shows Display mode, then one row per page with its current choice (`appearanceSummary`): **Theme & colors**, **Home**, **Player**, **Navigation**, **Text** and **Widgets**.
- Theme & colors offers four looks (boxlore classic, Pure, Material 3, Custom) and **Use artwork colors** (on by default). Legacy palettes show as Custom without changing stored values (`ThemeSettingsLogic.kt`).
- The custom theme editor is a full page. Drafts change only its preview; Save applies the palette in one write, and Back discards it.
- Widgets holds the home screen widgets' colours (App theme or System) and every widget (`AddableWidget`, supplied by the app) as a card: its real look drawn by the app through `AppearanceWidgetPreview`, scaled to fit and never enlarged, its name, and an Add button that asks the home screen app to add it. The two round players share a row, and the Your Shows card carries the cover choice (whole covers or shapes) under its picture. When nothing answers the latest request within `ADD_WIDGET_ANSWER_MS` (the app is not paused by a confirmation and `AppearanceActions.addedWidgets` reports nothing), that card offers help and a way to the app's permission settings; the help goes away when an answer arrives. When the home screen app cannot add widgets (`AppearanceActions.canAddWidgets`), the cards stay without buttons, with the steps for adding one by hand.
- Home holds the hero card style and its colour choice, with live previews drawn by the app through `AppearanceHeroPreview`. Hero styles are described in [`feature/home/README.md`](../home/README.md).
- Player holds **Player style** (Standard or bunpod) and the miniplayer's **Seek buttons** (off by default). The bunpod option credits Kamran Bekirov's BunPod design. Video always uses Standard.
- Navigation holds the bar style, **Blur behind the bar** (Floating bar only) and **Start page & tabs** (**Explore opens to** hides while Picked for you is off). Text holds the letter shape with a preview.
- Visual choices are `SettingsPictureTile` radio tiles in `SettingsPictureGrid` or the sideways `SettingsPictureRow`.

## Account and sync

- `AccountSettingsPage` serves Settings and onboarding. `accountPageMode` picks one state: signed in, signing in with a link, link failed, confirm email, check email, or signed out.
- Signed out offers Google, an emailed sign-in link and email with password; the chosen method's form replaces the cards (`AccountAuthState.methodChosen`, kept in saved state).
- Opening a sign-in link shows its progress or failure through `AccountEmailLinkUi`, fed by `SettingsRepositories.emailLinkSignIn`.
- Signed in from Settings shows the sync panel (`AccountSyncPanel`) and the account group (sign out, reset password for password accounts, delete account). Backend error text is never shown.
- Echo (`AccountEcho`, the shared `EchoCharacter`) is decorative; tapping it cycles outfits (`AccountEchoOutfits`), except while syncing.
- Cloud Sync & Backups exports and imports a full JSON backup or subscriptions only (OPML) through `LibraryBackupWriters`; its copy names exactly what the JSON holds (`FULL_LIBRARY_EXPORT_SUMMARY`).
- Account copy lives in `res/values/account.xml`.

## Feedback and support conversations

- `FeedbackScreen` opens on the `openThreadId` conversation, else the list once conversations exist, else the form (`initialFeedbackPane`). Back walks conversation → list → out (`feedbackBack`).
- Categories, message limit, photos and the pause notice come from the server's support metadata; built-in categories are the offline fallback (`FeedbackChoice`). The server can refuse a send, and the draft stays.
- Drafts persist through `BoxcastPrefs` and lock while sending. The phone model goes only with diagnostics on.
- Each send carries a retry key (`RetryKeys`), so a resend after a lost answer is saved once; an edited message gets a new key.
- `FeedbackSentScreen` offers a notifications prompt only when the message became a conversation and notifications are off (`canAskForNotifications`).
- A conversation (`FeedbackThreadScreen`, `FeedbackThreadViewModel`) loads fresh each time it shows, reloads on a reply push, and is marked read only once on screen. Replies keep their draft and retry key in saved state.
- Wide landscape windows show the list beside the conversation (`feedbackUsesSplit`); `FeedbackPanesLayout` keeps both composed, so crossing the gate keeps scroll, text and focus.
- Feedback keeps the player compact and reclaims the room it frees (`feedbackBottomSpace`, `feedbackChromePadding`).

## Support page

- `SupportPageHost` is the Support destination. Once per visit it reads the `support-world` experiment flag and shows one of six worlds: `control` (the original page), `neon`, `orbit`, `alchemy`, `abyss` or `sky`.
- A missing or unknown flag shows the control and is reported as a fallback, never as an exposure. Debug builds add a world switcher that lasts for the process.
- Every world is `SupportDevelopmentPage` with a `SupportPageStyle`; a world changes only art, fonts and wording. Prices and tiers come from `SUPPORT_OFFERS` in every world.
- Payments are off (`SUPPORT_PAYMENTS_LIVE`): every pay tap opens `SupportPaymentNotice`, which says nothing was charged.
- `SupportPageSession` reports one view and one exit per visit, surviving configuration changes. `SupportEntryImpression` reports each time Settings shows the Support entry.
- The page never scrolls; `SupportPageLayout.kt` drops or rearranges parts to fit the window.
- `SupportPageEnvironment` holds the flag reader, analytics sink, clock and configuration-change check so tests can replace them.

## Library and LeaveMeAlone

- Library holds Charts › Country (`ContentRegionPickerSheet`), Recommendations › Languages with Include English (`ContentLanguagesSheet`), Add podcast by RSS feed, and Storage › Clear saved shows.
- Clear saved shows counts unfollowed shows with saved episode lists through `SavedShowsControl` (wired by the app); `ClearSavedShowsDialog` confirms that history, downloads and queue stay.
- LeaveMeAlone (`ProfileSettingsDestination.Recommendations`, entry `recommendations`) holds every switchable section, saved through `trackedDiscoverySectionSetter`. Appearance › Home links to it and becomes its Back target. The sections are described in [`feature/home/README.md`](../home/README.md).

## Playback, downloads and privacy

- Playback holds trimming, seek lengths (also used by the miniplayer's seek buttons), skip behaviour, restarting forgotten episodes, Smart queue and where completed episodes hide.
- Automatic downloads offers 1, 2, 3 or 5 episodes per show. Backup background checks are off by default; their network and charging controls show only when on and keep their values while hidden.
- Privacy explains analytics and offers Reset recommendations, the analytics ID (copy or start a new one) and a request to delete analytics data.
- Wear OS (`ProfileSettingsDestination.WearOs`, entry `wear_os`) sets the watch's Your shows layout and download storage limit and shows what the watch last reported. The hub and the tablet split list it only when `SettingsScreenConfig.wearOs` is set, which the app does once boxlore on a watch has linked.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/settings/
  SettingsScreen.kt              # SettingsScreen, config types, analytics wrappers
  SettingsBackNavigation.kt, ProfileSettingsDestination.kt, TabletSettingsLayout.kt
  SettingsViewModel.kt, SettingsViewModelAssembler.kt, SettingsExternalActions.kt
  components/                    # SettingsRows, SettingsScaffold, SettingsHeaderTitle,
                                 #   SettingsPictureTile, ThemeLookPicture
  dialogs/                       # AddRssFeedDialog, ClearSavedShowsDialog, ResetAnalyticsDialog
  downloads/                     # AutoDownload* and SmartDownloadsSettingsScreen
  feedback/                      # FeedbackScreen, panes, list, thread, sent screen,
                                 #   view models, diagnostics and logcat collectors
  pages/
    SettingsHub.kt, *SettingsPage.kt, SyncAndBackupsPage.kt, RecommendationsSettingsPage.kt
    WearOsSettingsPage.kt        # WearOsSettings, WearOsUiState, WearOsCopy, the page
    Account*.kt                  # Account & sync: auth state, sign-in, email link, sync panel, Echo
    Appearance*Page.kt, AppearancePictures.kt
    CustomTheme*.kt, ThemeSettingsLogic.kt
    Support*.kt                  # host, page, layout, style, tiers, platform, atmosphere
    supportworlds/               # worlds, skins, stage, notices, session, entry impression, debug menu
```

## Dependencies

- → `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:network`, `:core:auth`, `:core:prefs`, `:core:designsystem`, `:core:analytics`, `:core:ranking`, `:core:rss`, `:core:support`.
- Libraries: Compose, Material 3, Navigation, lifecycle, Coil, Lottie, Kotlin serialization, AndroidX Credentials and Google ID; tests add JUnit 5, Turbine, Robolectric, Roborazzi and Mockito.
- Support worlds bundle art (`res/drawable-nodpi/support_*`) and SIL Open Font License fonts (`res/font/support_*`); licences are in `licenses/`, and Reserved Font Name fonts ship unmodified.
- Feature modules never depend on other features, and never call PostHog directly; analytics go through `:core:analytics`.

## Threading / lifecycle

- ViewModels are scoped to their navigation entry; repositories, auth, support and preferences are application-scoped instances from app wiring.
- `FeedbackViewModel` reads logs and photos on an injected IO dispatcher; conversation photos decode on `Dispatchers.Default`.
- Network, auth and database work goes through injected suspend APIs; UI state is exposed as flows and collected by Compose on the main thread.
- The Support visit ends only on a real exit, not on a configuration change.

## Persistence & identity

- `boxlore_feedback_retry` (SharedPreferences, keys `fingerprint` and `request_id`): the pending feedback retry key, kept until the message is saved or discarded so it survives the app being killed. Do not rename.
- Everything else Settings changes is stored by `:core:prefs` (`UserPreferencesRepository`, `BoxcastPrefs`).
- The Support world comes only from the `support-world` flag; nothing is stored locally, and the debug override lives in memory.
- `initialPage` keys above are deep-link contracts.
- Stable Compose test tags: `settings_add_rss_url`, `settings_add_rss_confirm`, `settings_add_rss_cancel` (used by Maestro), `settings_downloads_smart`, `settings_downloads_auto`, `settings_reset_analytics_confirm`, `settings_reset_analytics_cancel`, `settings_clear_saved_shows_confirm`, `settings_clear_saved_shows_cancel`.
- Analytics: every page sends `screen_viewed` through `TrackScreenView`; controls send `settings_interaction` only when a value changes. Events are listed in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Testing notes

Hermetic JVM tests live in `feature/settings/src/test`, using fakes from [`:core:testing`](../../core/testing/README.md) and the Robolectric Compose host for UI.

| Area | Tests |
| :--- | :--- |
| Hub, Back and tablet split | `SettingsBackNavigationTest`, `TabletSettingsLayoutTest`, `TabletSettingsDialogTest`, `TabletSettingsCaptureTest`, `SettingsHeaderTitleTest` |
| RSS and reset dialogs | `SettingsViewModelTest`, `SettingsViewModelDialogTest`, `SettingsGoldenRoborazziTest` |
| Appearance | `AppearancePagesTest`, `AppearanceActionsTrackedTest`, `SettingsPictureRowTest`, `PlayerStyleSectionTest`, `AppearanceWidgetsPageTest` |
| Themes | `CustomThemeEditorTest`, `CustomThemeEditorOptionsTest`, `ThemeEditorPressFeedbackTest`, `ThemeSettingsLogicTest`, `ThemePresetResolutionTest`, `AccentSwatchLayoutTest` |
| Account and sync | `AccountPagePresentationTest`, `AccountPageAdaptiveTest`, `AccountSettingsPageTest`, `AccountAuthHelpersTest`, `AccountLinkFailedContentTest`, `SyncAndBackupsPageTest` |
| Library | `LibraryLanguagesAdaptiveTest`, `ClearSavedShowsAdaptiveTest` |
| Downloads | `AutoDownloadSettingsActionsTest` |
| Wear OS | `WearOsSettingsTest` |
| Feedback | `FeedbackViewModelTest`, `FeedbackLayoutPolicyTest`, `FeedbackAdaptiveTransitionTest`, `FeedbackNotificationSettingsTest`, `DiagnosticCollectorTest`, `LogcatCollectorTest` |
| Conversations | `FeedbackPanesTest`, `FeedbackThreadViewModelTest`, `FeedbackConversationsAdaptiveTest` |
| Support page | `SupportWorldsModelTest`, `SupportPageHostTest`, `SupportPageLayoutTest`, `SupportPageSessionTest`, `SupportEntryImpressionTest`, `SupportDevelopmentPageTest` |

- `SettingsPhoneGoldenTest` is an opt-in phone golden; see [`docs/TESTING.md`](../../docs/TESTING.md).
- Tests hold Echo still with `LocalEchoMotion provides false` so screens can go idle.

```bash
./gradlew :feature:settings:testDebugUnitTest
./gradlew :feature:settings:recordRoborazziDebug   # optional
```

## CI relevance

- `unit-tests.yml` runs Settings JVM tests in the features-b shard and includes the module in merged coverage verification.
- `scripts/ci/check-feature-no-posthog.sh` and `scripts/ci/check-feature-no-boxlore-database.sh` guard feature modules.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`:app` README](../../app/README.md)
- [`:feature:home` README](../home/README.md)
- [`:feature:library` README](../library/README.md)
- [`:core:prefs` README](../../core/prefs/README.md)
- [`:core:support` README](../../core/support/README.md)
- [`:core:auth` README](../../core/auth/README.md)
