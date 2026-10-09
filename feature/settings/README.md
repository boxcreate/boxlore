# `:feature:settings`

## Purpose

Owns the unified Settings hub, category pages (Account, Sync & Backups, Appearance, Playback, Privacy, Library, Downloads, About, Support), dialogs (Add RSS, Reset analytics), authentication credential management (Google One Tap, Magic Link, Password), and download preference screens (`AutoDownloadSettingsScreen`, `SmartDownloadsSettingsScreen`). It presents data from injected core dependencies and does not own catalog engines, ranking persistence, playback services, download workers, or Room schemas.

## Public API

- Custom theme creation is a nested full-page destination under Appearance. Back discards the draft and returns to Appearance; Save applies and remembers the custom palette without changing display mode. A pinned light/dark preview updates on every color change, including slider drags, and uses a compact layout for short windows, the keyboard, and large text. Incomplete hex input keeps the last valid palette visible and disables Save. `CustomThemeEditorTest` covers live updates, validation, cancellation and editor-open event volume.

- Add RSS uses a single localizable instruction to paste the podcast’s RSS feed link and add it to the library. The feed-link publication warning appears only in Podcast Info when notifications are enabled. Subscription creation enables normal foreground refresh for all supported feeds, including those without HEAD validators.

- `SettingsScreen`, `SettingsViewModel`, `SettingsViewModelAssembler`, and `ProfileSettingsDestination` for the settings hub and sub-pages.
- Shared `SettingsScaffold` headers use a smaller 36sp expanded title and naturally wrap to two lines when needed, including Cloud Sync & Backups. The expanded title smoothly reduces to 22sp on scroll; the separate compact row always uses 22sp and retains two-line support for narrow screens and larger system fonts. Material's two-row app bar measures the title height, so a hidden expanded title cannot inflate the navigation row or force a single-line ellipsis.
- `SettingsScreenConfig` includes `isOnboarding: Boolean` (used by app navigation to keep onboarding mode active and bypass the hub on return), `onSendFeedback: (() -> Unit)?` to route to feedback, and `resolveSettingsBackAction` for deterministic back-handler actions.
- `FeedbackScreen`, `FeedbackViewModel`, `DiagnosticCollector`, and `LogcatCollector` under `feedback/` for sticky draft feedback submissions, in-process sanitized logcat extraction, device diagnostics, and GitHub issue reporting.
- `AutoDownloadSettingsScreen` and `SmartDownloadsSettingsScreen` under `downloads/` for download management.
- `AccountSettingsPage`, `SyncAndBackupsPage`, `AppearanceSettingsPage`, `PlaybackSettingsPage`, `PrivacySettingsPage`, `LibrarySettingsPage`, `DownloadsSettingsPage`, `AboutSettingsPage`, `SupportDevelopmentPage`.
- Appearance → Layout exposes **Miniplayer seek buttons**, default off. `AppearanceUiState` and `AppearanceActions` carry its value and callback; app wiring owns persistence through `:core:prefs`. The existing Playback seek-duration sliders apply to the optional larger miniplayer buttons too.
- Appearance is a single page ordered as **Theme & colors**, **Text**, **Layout**, **Start page & tabs**, and **Widgets**. Theme mode and four light/dark preview choices (boxlore classic, Pure, Material 3, Custom) sit directly in Appearance. Material 3 uses wallpaper colors; Pure retains white/true-black backgrounds. The former theme entry point redirects to Appearance. Legacy presets, named accents, custom hex colors and locked modes remain readable and appear as Custom without changing persisted values. Leaving Custom remembers all its original inputs atomically so it can be restored later.
- **Use artwork colors** defaults on and scopes artwork-derived palettes to podcast/episode details and the navbar’s accents and subtly tinted surfaces. Pure retains neutral black/white backgrounds. Palette changes ease in after artwork loads; turning the option off restores the app palette. The full-page custom editor keeps its Material component preview pinned, using a compact sample on short screens, with larger text or while the keyboard is open. It has Light/Dark selection, primary/secondary/tertiary pickers, optional suggested supporting colors, hex entry, labelled sliders, and Tinted/Neutral/Pure backgrounds. Existing preset backgrounds can be retained as Saved. Save applies coordinated Material Color Utilities roles in one transaction; Back leaves app colors untouched. Text and container colors are derived for readability. Layout groups navigation, subscriptions tab placement, Home shortcuts and miniplayer seeks; startup/default-tab choices are grouped separately. Widgets still resolve the same saved app scheme independently of artwork.

- Dialogs: `AddRssFeedDialog`, `ResetAnalyticsDialog`, `LogsPreviewDialog`.

- Automatic downloads uses the shared two-line settings header and solid grouped surfaces for Downloads, Storage, and While boxlore is closed. A connected 1/2/3/5 selector replaces scrolling quota chips; a short introduction explains per-show activation. **Backup background checks** is explicitly described as optional recovery when show notifications are off or do not arrive; notifications can already trigger downloads while closed. Its network/charging controls appear only when the off-by-default switch is enabled, retaining their preferences when hidden. Concise state-dependent copy explains foreground/push discovery or battery/data costs and Android delays; the header help dialog holds the full network, consent, discovery and retention explanation. Per-show auto-download never enables background checking.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/settings/
  SettingsScreen.kt
  SettingsBackNavigation.kt
  SettingsExternalActions.kt
  SettingsViewModel.kt
  SettingsViewModelAssembler.kt
  ProfileSettingsDestination.kt
  components/
    SettingsRows.kt
    SettingsScaffold.kt (shared column or lazy-list content)
    ThemeLookCard.kt
    InlineAccentPicker.kt
    AccentSwatchGrid.kt
  dialogs/
    AddRssFeedDialog.kt
    ResetAnalyticsDialog.kt
  downloads/
    AutoDownloadSettingsScreen.kt
    SmartDownloadsSettingsScreen.kt
  feedback/
    DiagnosticCollector.kt
    FeedbackDialogs.kt
    FeedbackScreen.kt
    FeedbackSuccessView.kt
    FeedbackViewModel.kt
    LogcatCollector.kt
  pages/
    AboutSettingsPage.kt
    AccountAuthHelpers.kt
    AccountAuthUiComponents.kt
    AccountSettingsDialogs.kt
    AccountSettingsPage.kt
    AccountSignedOutContent.kt
    AnimatedBlobAvatar.kt
    BlobAvatarAccessories.kt
    BlobAvatarCharacter.kt
    BlobAvatarEnvironments.kt
    BlobAvatarGeometry.kt
    BlobAvatarGenreMood.kt
    AppearanceSettingsPage.kt
    ThemeSettingsPage.kt
    AppearanceThemeSection.kt
    CustomThemeEditor.kt
    CustomThemePreview.kt
    ThemeSettingsLogic.kt
    DownloadsSettingsPage.kt
    EmailVerificationPendingSection.kt
    LibrarySettingsPage.kt
    PlaybackSettingsPage.kt
    PrivacySettingsPage.kt
    SettingsHub.kt
    SupportAtmosphere.kt
    SupportCtaSection.kt
    SupportDevelopmentPage.kt
    SupportTierModels.kt
    SyncAndBackupsPage.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:network`, `:core:prefs`, `:core:designsystem`, `:core:analytics`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Coil, Kotlin serialization, AndroidX Credentials, Google ID, Roborazzi.
- Reverse-edge rule: feature modules must not depend on other feature modules.

## Threading / lifecycle

- ViewModels are scoped by app navigation or Activity owners.
- Repositories, ports, and preferences dependencies are application-scoped instances supplied by app wiring.
- Settings surfaces emit glossary analytics through `:core:analytics` (no PostHog direct in the feature).
- UI state is exposed through flows and collected by Compose on the main thread.
- Network and database operations run through injected suspend APIs.

## Persistence & identity

- Settings read and write DataStore and `BoxcastPrefs` through `:core:prefs` APIs.
- Stable Compose test tags include `settings_add_rss_*`, `settings_downloads_smart`, `settings_downloads_auto`, `settings_reset_analytics_confirm`, and `settings_reset_analytics_cancel`.

## Testing notes

- Unit tests live under `feature/settings/src/test`.
- `AutoDownloadSettingsActionsTest` exercises the same action callbacks and background presentation used by the screen: all four episode limits, invalid-limit rejection, help opening/dismissal, both background-control visibility states, and retaining all network/charging combinations when checks are disabled and re-enabled. No device or screenshot automation is required.
- `SettingsHeaderTitleTest` covers reduced expanded typography, two-line support throughout collapse, constant compact-row size, monotonic expanded size/line-height changes and invalid scroll fractions without rendering or device automation.
- Existing coverage includes Settings ViewModel tests, Account auth helper validation, Appearance actions tracking, back navigation action resolution tests (`SettingsBackNavigationTest`), and Roborazzi golden captures for dialogs.
- `AppearanceActionsTrackedTest` also verifies the miniplayer seek callback is forwarded in both directions through the appearance action wrapper.
- `ThemePresetResolutionTest` retains coverage for complete legacy palettes and app/widget/preview agreement. `ThemeSettingsLogicTest` covers the four-choice picker, legacy classification without resets, accurate locked modes, cohesive selection, remembering/restoring Custom, and harmless reselection. `AppearanceActionsTrackedTest` covers artwork-toggle and cohesive-theme callbacks. Back-navigation tests cover the old Theme entry point opening Appearance. Shared designsystem tests cover color encoding, contrast, artwork seed selection and entry-scoped navbar ownership; prefs tests cover cache restoration and atomic persistence. These are hermetic JVM checks without device automation.


```bash
./gradlew :feature:settings:testDebugUnitTest
./gradlew :feature:settings:recordRoborazziDebug   # optional
```

## CI relevance

- `unit-tests.yml` runs Settings JVM tests and includes the module in merged coverage verification (`kover`).

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:feature:home` README](../home/README.md)
- [`:feature:library` README](../library/README.md)
- [`:app` README](../../app/README.md)
