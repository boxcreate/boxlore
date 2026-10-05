# `:feature:settings`

## Purpose

Owns the unified Settings hub, category pages (Account, Sync & Backups, Appearance, Playback, Privacy, Library, Downloads, About, Support), dialogs (Add RSS, Reset analytics), authentication credential management (Google One Tap, Magic Link, Password), and download preference screens (`AutoDownloadSettingsScreen`, `SmartDownloadsSettingsScreen`). It presents data from injected core dependencies and does not own catalog engines, ranking persistence, playback services, download workers, or Room schemas.

## Public API

- `SettingsScreen`, `SettingsViewModel`, `SettingsViewModelAssembler`, and `ProfileSettingsDestination` for the settings hub and sub-pages.
- Shared `SettingsScaffold` headers use a smaller 36sp expanded title and naturally wrap to two lines when needed, including Cloud Sync & Backups. The expanded title smoothly reduces to 22sp on scroll; the separate compact row always uses 22sp and retains two-line support for narrow screens and larger system fonts. Material's two-row app bar measures the title height, so a hidden expanded title cannot inflate the navigation row or force a single-line ellipsis.
- `SettingsScreenConfig` includes `isOnboarding: Boolean` (used by app navigation to keep onboarding mode active and bypass the hub on return), `onSendFeedback: (() -> Unit)?` to route to feedback, and `resolveSettingsBackAction` for deterministic back-handler actions.
- `FeedbackScreen`, `FeedbackViewModel`, `DiagnosticCollector`, and `LogcatCollector` under `feedback/` for sticky draft feedback submissions, in-process sanitized logcat extraction, device diagnostics, and GitHub issue reporting.
- `AutoDownloadSettingsScreen` and `SmartDownloadsSettingsScreen` under `downloads/` for download management.
- `AccountSettingsPage`, `SyncAndBackupsPage`, `AppearanceSettingsPage`, `PlaybackSettingsPage`, `PrivacySettingsPage`, `LibrarySettingsPage`, `DownloadsSettingsPage`, `AboutSettingsPage`, `SupportDevelopmentPage`.
- Appearance → Miniplayer, beside Navigation, exposes **Show seek buttons in miniplayer**, default off. `AppearanceUiState` and `AppearanceActions` carry its value and callback; app wiring owns persistence through `:core:prefs`. The existing Playback seek-duration sliders apply to the optional larger miniplayer buttons too.
- Appearance → **Theme** keeps mode, theme selection and color customization on one page. Compact cards show a basic card/button UI split into labelled Light and Dark previews. Default/Minimal/Colorful/Bold each have a horizontally scrolling lazy preview row. Cards leave a visible next-card edge on phones, cap their width on wider windows, and reserve three description lines for steady row height without clipping larger text. Each row retains its browse position and initially reveals its selected theme. There are no filter strips, dialogs or sheets. Material 3's choice explicitly explains that selecting it enables wallpaper colors. **Colors for [selected theme]** stays full-width beneath the collection containing the active theme and explains buttons/icons/highlights, retained preset backgrounds, and Material 3's additional background tinting. Its wallpaper switch, named color swatches and inline custom picker explain how to override the colors; restoring a preset's original colors is an explicit action. Re-tapping the selected theme preserves customized colors. Selected previews resolve the actual active inputs in both modes, while unselected previews resolve the colors that selecting them applies. Lazy, stable-key content keeps scrolling light and selection stable. Existing keys, defaults, legacy mode locks and atomic preset persistence remain intact. Theme Back returns to Appearance with the shared reverse page transition.
- Dialogs: `AddRssFeedDialog`, `ResetAnalyticsDialog`, `LogsPreviewDialog`.

- Auto-Download Settings exposes a separate off-by-default background-check switch, unmetered-network restriction and charging option. Copy explains six-hour checks, mandatory low-battery pausing, additional battery/data use, Android delays, and foreground/push behavior when polling is off. Per-show auto-download toggles never enable the switch.

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
    ThemeSettingsControls.kt
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
- `SettingsHeaderTitleTest` covers reduced expanded typography, two-line support throughout collapse, constant compact-row size, monotonic expanded size/line-height changes and invalid scroll fractions without rendering or device automation.
- Existing coverage includes Settings ViewModel tests, Account auth helper validation, Appearance actions tracking, back navigation action resolution tests (`SettingsBackNavigationTest`), and Roborazzi golden captures for dialogs.
- `AppearanceActionsTrackedTest` also verifies the miniplayer seek callback is forwarded in both directions through the appearance action wrapper.
- `ThemePresetResolutionTest` verifies complete preset backgrounds/accents in both modes, app/widget/preview agreement, retained preset backgrounds with custom or wallpaper colors, legacy background locks, existing palette compatibility. `AppearanceActionsTrackedTest` verifies preset selection forwards one cohesive action without firing separate mode/color/background callbacks. `AccentSwatchLayoutTest` covers narrow/invalid grid constraints; `ThemeSettingsLogicTest` verifies section partitions, complete unique choices, legacy normalization, unchanged-theme color preservation, Material 3 selection and plain-language color explanations; back-navigation tests cover Theme → Appearance and direct entry. These are JVM checks without screenshots or device automation.

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
