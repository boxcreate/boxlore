# `:core:prefs`

## Purpose

Owns user preference persistence and migration helpers: DataStore-backed user preferences, theme fast-cache preferences, the `BoxcastPrefs` facade over app preferences, and SharedPreferences file migration. It does not own analytics event storage, playback session behavior, ranking model storage, catalog cache contents, or feature UI.

## Public API

- `miniPlayerSeekButtonsEnabledStream` / `setMiniPlayerSeekButtonsEnabled` persist the opt-in `mini_player_seek_buttons_enabled` flag (default off) in `user_preferences`. It only controls regular miniplayer button visibility; global seek durations and full-player controls remain available.
- `ThemeSelection` is an opaque brand/surface/wallpaper input bundle. `setThemeSelection` applies it atomically and optionally remembers Custom in `custom_theme_brand`, `custom_theme_surface`, and `custom_theme_dynamic`; existing theme/storage keys remain intact. `customThemeStream` and `cachedCustomTheme` restore the saved selection after switching defaults. `artworkColorsEnabledStream` / `setArtworkColorsEnabled` persist `artwork_colors_enabled` (default on). Theme fast-cache and restore hydration include both new preferences, with existing DataStore values taking precedence.
- `setThemePreset` atomically writes a ready-made theme's opaque key to existing `surface_style` and `theme_brand` preferences and disables wallpaper colors, including one matching fast-cache update. It preserves theme mode, lettering, navigation and all unrelated preferences. Palette definitions stay in designsystem; prefs owns no color tokens or catalog dependency.
- `UserPreferencesRepository` exposes DataStore-backed settings such as theme, lettering roundness (`font_roundness`: `crisp` / `soft` / `round`, default `round`), navigation style (`navigation_style`: `floating` / `classic`), cold-start landing (`open_app_to`: `home` / `subscriptions` / `downloads`, default `home`; Back from launch-Subscriptions or launch-Downloads goes to Home), Explore default tab (`explore_default_tab`: `for_you` / `top`, default `for_you`), Subscriptions default tab (`subscriptions_default_tab`: `shows` / `new_episodes`, default `shows`; omitted `tab` on the Subscriptions route uses this), content region (11 chart storefronts), `content_languages` (English forced, max 4; resets to recommended on region change), skip durations, smart downloads, playback preferences, `restart_forgotten_episodes` (default on — soft-expires mid-episode seek for implicit plays after 7 days; see `:core:playback` `PlaybackSkipPolicy`), `same_show_queue_only` (default off — Settings → Playback **Smart queue** stays on; when the switch is off, Smart Queue continues the current show only), `home_shortcuts_in_library` (default off — Settings → Appearance **Cleaner Home**; when on, Settings and Feedback sit on Library instead of Home), `widget_appearance` (`app` / `system`, default `app` — Settings → Appearance **Widgets**; App theme paints home-screen widgets from Theme / Background / Colors, System keeps launcher light/dark and wallpaper accents), `subscription_sort` (including `Manual`), `subscription_folder_sort`, `subscription_intra_folder_sort`, `subscription_manual_order` (ordered podcast ids, unit-separator encoded so `rss:` URLs can contain commas), `subscription_folder_manual_order` (ordered folder ids for manual folder inter-sort), `home_pinned_podcast_ids` (max 5; lead Your Shows), and `home_mix_mode` (`daily` / `offline`, default `daily`). `theme_brand` may be a named palette, `#RRGGBB` Material 3 seed, or `exact:#RRGGBB` (pins primary as-is). Theme fast-cache (`boxlore_theme_fast_cache`) mirrors `theme_config`, `surface_style`, `theme_brand`, `use_dynamic_color`, `font_roundness`, `navigation_style`, `open_app_to`, `widget_appearance`, `explore_default_tab`, and `subscriptions_default_tab` for cold-start / non-Compose readers. After Google Backup, `hydrateMissingDataStoreFromFastCache` copies those cache values into any missing DataStore keys so appearance streams do not reset to defaults.
- The legacy RSS repair APIs persist a version gate plus one pending old→new podcast-id journal. Finishing the journal rewrites Manual order, Home pins, recommendation override, and per-show last-seen keys in one DataStore transaction.
- `HomePinnedShows` sanitizes pin lists (distinct, max 5) and toggle results (`Pinned` / `Unpinned` / `AtCapacity`). `UserPreferencesRepository.toggleHomePinnedPodcastId` sanitizes, toggles, and persists inside one DataStore write; at capacity the stored list is unchanged.
- `PreferenceIdList` encodes ordered id lists for DataStore string prefs.
- `FontRoundnessAxis` centralizes lettering preset keys and ROND axis values for prefs, playback (Android Auto collage badges), and other non-Compose readers.
- `WidgetAppearance` sanitizes home-screen widget chrome (`app` default vs `system`) and reads it from theme fast-cache for RemoteViews.
- `ExploreDefaultTab` / `SubscriptionsDefaultTab` sanitize Appearance **Default tabs** (`for_you`/`top`, `shows`/`new_episodes`) and resolve the pager index when a route does not already pick a tab.
- `SubscriptionsTabStyle` defines and sanitizes Subscriptions tab layout choices: `top` (default) vs `floating` (FAB style).
- `BoxcastPrefs` stores the permanent Home video-showcase dismissal in the canonical `boxlore_prefs` file (`featured_video_showcase_dismissed`). The showcase asks for confirmation before writing it and does not reappear afterward.
- `Announcement` persists the existing title/body/route/image/action/category plus optional compact/fullscreen presentation, accent/image roles, release/Play/test flags, release code and GitHub release URL. Missing keys keep legacy compact defaults and category-based release detection; clearing removes all fields in one transaction. Rendering and delivery policy belong to app.
- `Context.userPreferencesDataStore` defines the `user_preferences` DataStore delegate.
- `BoxcastPrefs` is the typed facade for `boxlore_prefs` values such as onboarding, genres, recommendation caches, time-of-day rotation slot keys (`cached_byl_slot`), Learn history, learner-log gates, pending magic link authentication email (`pending_auth_email`), sticky feedback drafts (`FeedbackDraft` via `getFeedbackDraft()`, `saveFeedbackDraft()`, and `clearFeedbackDraft()`), the stable sync installation ID (`sync_device_id` via `getOrCreateSyncDeviceId()`), sync cursor state (`sync_last_timestamp`, `sync_last_user_id`, `sync_metadata_version`), and the notification-permission prompt gate (`has_requested_notification_permission`). `clearBylCacheIfPodcastId` invalidates a Because-you-like cache (including its cached slot key) when its seed show adopts a new catalog id.
- `resolveLearnerLogEnabled(isDebugBuild)`: debug defaults on when unset; **release is always off** unless the user explicitly persisted `true` via the debug-screen toggle.
- `UserPreferenceKeys` centralizes DataStore preference keys.
- `PrefsFileMigrator` opens canonical SharedPreferences files and migrates from legacy file names.
- `PlaybackSkipBounds` and `EngagementPromptConstants` provide shared preference-related bounds and thresholds.

- `AutoDownloadBackgroundSettings` is installation-local consent in a separate `auto_download_background` DataStore, excluded from Android backup/device transfer and absent from library/cloud settings. Defaults: disabled, unmetered-only, charging optional. Only the explicit settings setter enables checks; existing auto-download preferences and per-show flags do not grant consent.

## Internal structure

`UserPreferencesRepository` delegates appearance/fast-cache, library ordering and RSS repair, engagement prompts/tips, and announcement storage to same-package helpers. They share the original DataStore; the repository API, nested `Announcement` type, file names, preference keys and defaults remain unchanged.

```text
src/main/java/cx/aswin/boxlore/core/prefs/
  AnnouncementPreferences.kt
  AppearancePreferences.kt
  BoxcastPrefs.kt
  DefaultLandingTabs.kt
  EngagementPreferences.kt
  EngagementPromptConstants.kt
  FontRoundnessAxis.kt
  HomePinnedShows.kt
  LibraryPreferences.kt
  PlaybackSkipBounds.kt
  PreferenceIdList.kt
  PrefsFileMigrator.kt
  SubscriptionsTabStyle.kt
  UserPreferenceKeys.kt
  UserPreferencesRepository.kt
  WidgetAppearance.kt
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

- `AnnouncementPreferencesTest` round-trips rich presentation/release fields and clears them, while retaining legacy defaults and blank-title behavior.
- Unit tests live under `core/prefs/src/test`.
- `ThemeSelectionPreferencesTest` covers artwork-color defaults, saved custom themes, atomic theme selection and fast-cache restoration.
- `BoxcastPrefsTest` covers facade behavior, including targeted Because-you-like cache invalidation, time-of-day rotation slot persistence, permanent featured-video showcase dismissal, and sync state cursors.
- `PrefsFileMigratorTest` covers legacy-to-canonical file migration behavior.
- `PreferenceIdListTest` and `HomePinnedShowsTest` cover id-list encoding, pin cap/toggle, and the at-capacity snackbar copy.
- `UserPreferencesRepositoryTest` round-trips Manual order and Home pins, including atomic pin toggle, unsubscribe cleanup, and journaled podcast-id replacement.
- Preference tests also verify miniplayer seeks default off, survive repository recreation, and preserve global seek durations when toggled.
- `UserPreferencesRepositoryTest` verifies complete preset persistence/fast-cache restoration, repeated preset selection, later accent customisation and preservation of mode, lettering and navigation.
- `UserPreferencesRestoreHydrationTest` covers Google Backup restore: appearance streams keep theme fast-cache when DataStore is empty, and `hydrateMissingDataStoreFromFastCache` writes those values into DataStore. Concurrent readers retain fast-cache appearance while application hydration runs in the background, and existing DataStore choices win over missing-key restoration.
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
