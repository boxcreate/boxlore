# `:core:prefs`

## Purpose

Owns user preference persistence and migration helpers: DataStore-backed user preferences, theme fast-cache preferences, the `BoxcastPrefs` facade over app preferences, and SharedPreferences file migration. It does not own analytics event storage, playback session behavior, ranking model storage, catalog cache contents, or feature UI.

## Public API

- `UserPreferencesRepository` exposes DataStore-backed settings such as theme, lettering roundness (`font_roundness`: `crisp` / `soft` / `round`), region, skip durations, smart downloads, and playback preferences. Theme fast-cache (`boxlore_theme_fast_cache`) mirrors `theme_config`, `surface_style`, `theme_brand`, `use_dynamic_color`, and `font_roundness` for cold-start / non-Compose readers.
- `FontRoundnessAxis` centralizes lettering preset keys and ROND axis values for prefs, playback (Android Auto collage badges), and other non-Compose readers.
- `Context.userPreferencesDataStore` defines the `user_preferences` DataStore delegate.
- `BoxcastPrefs` is the typed facade for `boxlore_prefs` values such as onboarding, genres, recommendation caches, Learn history, and learner-log gates.
- `resolveLearnerLogEnabled(isDebugBuild)`: debug defaults on when unset; **release is always off** unless the user explicitly persisted `true` via the debug-screen toggle.
- `UserPreferenceKeys` centralizes DataStore preference keys.
- `PrefsFileMigrator` opens canonical SharedPreferences files and migrates from legacy file names.
- `PlaybackSkipBounds` and `EngagementPromptConstants` provide shared preference-related bounds and thresholds.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/prefs/
  BoxcastPrefs.kt
  EngagementPromptConstants.kt
  PlaybackSkipBounds.kt
  PrefsFileMigrator.kt
  UserPreferenceKeys.kt
  FontRoundnessAxis.kt
  UserPreferencesRepository.kt
```

## Dependencies

- Project dependencies: `:core:model`.
- Libraries: AndroidX core, DataStore Preferences, and coroutines.
- Reverse-edge rule: prefs must not depend on catalog, playback, downloads, analytics, designsystem, or feature modules.

## Threading / lifecycle

- DataStore flows are cold streams collected by repositories, ViewModels, or app wiring.
- Preference reads and writes should use the repository or facade APIs instead of raw file access from feature modules.
- `PrefsFileMigrator` performs file migration during SharedPreferences open paths.

## Persistence & identity

- DataStore name `user_preferences` must remain stable.
- Canonical SharedPreferences files include `boxlore_prefs` and `boxlore_theme_fast_cache`.
- Legacy file names beginning with `boxcast_` are migrated through `PrefsFileMigrator`.
- Preference keys defined in `UserPreferenceKeys` and `BoxcastPrefs` are persisted user identity and must not be renamed casually.

## Testing notes

- Unit tests live under `core/prefs/src/test`.
- `BoxcastPrefsTest` covers facade behavior.
- `PrefsFileMigratorTest` covers legacy-to-canonical file migration behavior.

```bash
./gradlew :core:prefs:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs prefs JVM tests.
- App and feature tests depend on this module for stable preference behavior.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:analytics` README](../analytics/README.md)
