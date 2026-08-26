# `:core:prefs`

## Purpose

Owns user preference persistence and migration helpers: DataStore-backed user preferences, theme fast-cache preferences, the `BoxcastPrefs` facade over app preferences, and SharedPreferences file migration. It does not own analytics event storage, playback session behavior, ranking model storage, catalog cache contents, or feature UI.

## Public API

- `UserPreferencesRepository` exposes DataStore-backed settings such as theme, lettering roundness (`font_roundness`: `crisp` / `soft` / `round`, default `round`), navigation style (`navigation_style`: `floating` / `classic`), cold-start landing (`open_app_to`: `home` / `subscriptions` / `downloads`, default `home`; Back from launch-Subscriptions or launch-Downloads goes to Home), Explore default tab (`explore_default_tab`: `for_you` / `top`, default `for_you`), Subscriptions default tab (`subscriptions_default_tab`: `shows` / `new_episodes`, default `shows`; omitted `tab` on the Subscriptions route uses this), content region (11 chart storefronts), `content_languages` (English forced, max 4; resets to recommended on region change), skip durations, smart downloads, playback preferences, `restart_forgotten_episodes` (default on — soft-expires mid-episode seek for implicit plays after 7 days; see `:core:playback` `PlaybackSkipPolicy`), `same_show_queue_only` (default off — Settings → Playback **Smart queue** stays on; when the switch is off, Smart Queue continues the current show only), `home_shortcuts_in_library` (default off — Settings → Appearance **Cleaner Home**; when on, Settings and Feedback sit on Library instead of Home), `widget_appearance` (`app` / `system`, default `app` — Settings → Appearance **Widgets**; App theme paints home-screen widgets from Theme / Background / Colors, System keeps launcher light/dark and wallpaper accents), `subscription_sort` (including `Manual`), `subscription_manual_order` (ordered podcast ids, unit-separator encoded so `rss:` URLs can contain commas), `home_pinned_podcast_ids` (max 5; lead Your Shows), and `home_mix_mode` (`daily` / `offline`, default `daily`). `theme_brand` may be a named palette, `#RRGGBB` Material 3 seed, or `exact:#RRGGBB` (pins primary as-is). Theme fast-cache (`boxlore_theme_fast_cache`) mirrors `theme_config`, `surface_style`, `theme_brand`, `use_dynamic_color`, `font_roundness`, `navigation_style`, `open_app_to`, `widget_appearance`, `explore_default_tab`, and `subscriptions_default_tab` for cold-start / non-Compose readers. After Google Backup, `hydrateMissingDataStoreFromFastCache` copies those cache values into any missing DataStore keys so appearance streams do not reset to defaults.
- The legacy RSS repair APIs persist a version gate plus one pending old→new podcast-id journal. Finishing the journal rewrites Manual order, Home pins, recommendation override, and per-show last-seen keys in one DataStore transaction.
- `HomePinnedShows` sanitizes pin lists (distinct, max 5) and toggle results (`Pinned` / `Unpinned` / `AtCapacity`). `UserPreferencesRepository.toggleHomePinnedPodcastId` sanitizes, toggles, and persists inside one DataStore write; at capacity the stored list is unchanged.
- `PreferenceIdList` encodes ordered id lists for DataStore string prefs.
- `FontRoundnessAxis` centralizes lettering preset keys and ROND axis values for prefs, playback (Android Auto collage badges), and other non-Compose readers.
- `WidgetAppearance` sanitizes home-screen widget chrome (`app` default vs `system`) and reads it from theme fast-cache for RemoteViews.
- `ExploreDefaultTab` / `SubscriptionsDefaultTab` sanitize Appearance **Default tabs** (`for_you`/`top`, `shows`/`new_episodes`) and resolve the pager index when a route does not already pick a tab.
- `BoxcastPrefs` stores the permanent Home video-showcase dismissal in the canonical `boxlore_prefs` file (`featured_video_showcase_dismissed`). The showcase asks for confirmation before writing it and does not reappear afterward.
- `Context.userPreferencesDataStore` defines the `user_preferences` DataStore delegate.
- `BoxcastPrefs` is the typed facade for `boxlore_prefs` values such as onboarding, genres, recommendation caches, Learn history, and learner-log gates. `clearBylCacheIfPodcastId` invalidates a Because-you-like cache when its seed show adopts a new catalog id.
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
  WidgetAppearance.kt
  DefaultLandingTabs.kt
  UserPreferencesRepository.kt
  PreferenceIdList.kt
  HomePinnedShows.kt
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
- `subscription_manual_order` and `home_pinned_podcast_ids` live in the same `user_preferences` DataStore (do not rename the file). Unsubscribe drops that id from both lists.
- `legacy_rss_repair_*` keys are a crash-recovery contract; keep them until every shipping build that can begin the repair has aged out.

## Testing notes

- Unit tests live under `core/prefs/src/test`.
- `BoxcastPrefsTest` covers facade behavior, including targeted Because-you-like cache invalidation and permanent featured-video showcase dismissal.
- `PrefsFileMigratorTest` covers legacy-to-canonical file migration behavior.
- `PreferenceIdListTest` and `HomePinnedShowsTest` cover id-list encoding, pin cap/toggle, and the at-capacity snackbar copy.
- `UserPreferencesRepositoryTest` round-trips Manual order and Home pins, including atomic pin toggle, unsubscribe cleanup, and journaled podcast-id replacement.
- `UserPreferencesRestoreHydrationTest` covers Google Backup restore: appearance streams keep theme fast-cache when DataStore is empty, and `hydrateMissingDataStoreFromFastCache` writes those values into DataStore.
- `DefaultLandingTabsTest` covers Explore / Subscriptions default-tab sanitize and pager-index resolution (nav tab and genre win over the preference).

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
