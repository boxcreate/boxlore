# `:feature:settings`

## Purpose

Owns the unified Settings hub, category pages (Account, Appearance, Playback, Privacy, Library, Downloads, About), dialogs (Accent color, Add RSS, Reset analytics), authentication credential management (Google One Tap, Magic Link, Password), and download preference screens (`AutoDownloadSettingsScreen`, `SmartDownloadsSettingsScreen`). It presents data from injected core dependencies and does not own catalog engines, ranking persistence, playback services, download workers, or Room schemas.

## Public API

- `SettingsScreen`, `SettingsViewModel`, `SettingsViewModelAssembler`, and `ProfileSettingsDestination` for the settings hub and sub-pages.
- `AutoDownloadSettingsScreen` and `SmartDownloadsSettingsScreen` under `downloads/` for download management.
- `AccountSettingsPage`, `AppearanceSettingsPage`, `PlaybackSettingsPage`, `PrivacySettingsPage`, `LibrarySettingsPage`, `DownloadsSettingsPage`, `AboutSettingsPage`.
- Dialogs: `AccentColorPickerDialog`, `AddRssFeedDialog`, `ResetAnalyticsDialog`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/settings/
  SettingsScreen.kt
  SettingsViewModel.kt
  SettingsViewModelAssembler.kt
  ProfileSettingsDestination.kt
  components/
    SettingsRows.kt
    SettingsScaffold.kt
  dialogs/
    AccentColorPickerDialog.kt
    AddRssFeedDialog.kt
    ResetAnalyticsDialog.kt
  downloads/
    AutoDownloadSettingsScreen.kt
    SmartDownloadsSettingsScreen.kt
  pages/
    AboutSettingsPage.kt
    AccountAuthHelpers.kt
    AccountSettingsPage.kt
    AccountSignedOutContent.kt
    AppearanceSettingsPage.kt
    DownloadsSettingsPage.kt
    LibrarySettingsPage.kt
    PlaybackSettingsPage.kt
    PrivacySettingsPage.kt
    SettingsHub.kt
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
- Existing coverage includes Settings ViewModel tests, Account auth helper validation, Appearance actions tracking, and Roborazzi golden captures for dialogs.

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
