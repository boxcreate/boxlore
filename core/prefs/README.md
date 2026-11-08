# `:core:prefs`

## Purpose

Owns user preference storage: the `user_preferences` DataStore behind `UserPreferencesRepository`, the theme fast cache for cold-start and non-Compose readers, the `BoxcastPrefs` facade over the `boxlore_prefs` file, a few separate stores that must stay out of backups, and SharedPreferences file migration.

It does not own analytics event storage, playback behaviour, ranking storage, catalog cache contents, colour tokens or feature UI. It decides nothing about when prompts appear beyond the pure rules it stores; the prompt coordinator lives in [`:core:catalog`](../catalog/README.md).

## Public API

- `UserPreferencesRepository`: the one entry point for DataStore settings, as Flows plus `suspend` setters. It delegates to internal same-package helpers that share the one DataStore.
- `Context.userPreferencesDataStore`: the `user_preferences` DataStore delegate.
- `BoxcastPrefs`: typed facade for `boxlore_prefs` (onboarding, genres, recommendation caches, feedback drafts, sync cursors, device IDs, one-time notices).
- `PrefsFileMigrator`: opens canonical SharedPreferences files and reads through to their legacy `boxcast_*` names.
- `ThemeSelection`: opaque brand, surface and wallpaper-colour inputs; `setThemeSelection` and `setThemePreset` write them in one update, DataStore and fast cache together.
- `FontRoundnessAxis`: lettering preset keys and ROND axis values for non-Compose readers, plus the `boxlore_theme_fast_cache` file name.
- `WidgetAppearance`: sanitizes widget chrome (`app` or `system`) and reads it from the fast cache for RemoteViews.
- `FullPlayerStyle`, `NavigationBlurLevel`, `SubscriptionsTabStyle`: stored values and sanitizers for the full player, navigation blur and Subscriptions tab layout.
- `HeroCardStyle`, `HeroCoverShade`, `HeroTideProgress`: Home hero card style, shared colour choice and Tide ring, with per-style defaults (`HeroCoverShade.forStyle`).
- `ExploreDefaultTab`, `SubscriptionsDefaultTab`: sanitize default tabs and resolve the pager index when a route names no tab.
- `BriefingEdition`: Brief edition values (follow app region, Global, India, US, UK) and alias normalization.
- `DiscoverySection`, `DiscoverySections`: switchable discovery sections; `collectDiscoverySection` loads a section while it is on and cancels or clears it when off.
- `HomePinnedShows`: sanitizes Home pins (distinct, at most `MAX`) and returns a `ToggleResult` (`Pinned`, `Unpinned`, `AtCapacity`).
- `PreferenceIdList`: encodes ordered ID lists in one string, safe for `rss:` URLs containing commas.
- `AutoDownloadBackgroundSettings`: installation-local consent for background auto-download checks (off, unmetered-only, charging optional by default).
- `NpsSurveySchedule`, `NpsSurveyRule`, `NpsSurveyStore`: pure survey timing rules and their backup-excluded store.
- `PlaybackSkipBounds`, `EngagementPromptConstants`: shared bounds and thresholds.
- `BoxcastPrefs.resolveLearnerLogEnabled(isDebugBuild)`: on by default in debug; off in release unless explicitly stored as `true`.

## Internal structure

`UserPreferencesRepository` delegates to internal helpers (appearance and fast cache, library ordering and RSS repair, engagement and review prompts, announcements, hero, full player, navigation blur, discovery sections). They share one DataStore; the repository API, file names and keys stay where they are.

```text
src/main/java/cx/aswin/boxlore/core/prefs/
  UserPreferencesRepository.kt       # public repository, user_preferences delegate
  UserPreferenceKeys.kt              # internal Keys object
  AppearancePreferences.kt           # theme, lettering, navigation, landing; fast cache and hydration
  ThemeSelection.kt, FontRoundnessAxis.kt, WidgetAppearance.kt
  FullPlayerStyle.kt, FullPlayerPreferences.kt
  NavigationBlurLevel.kt, NavigationBlurPreferences.kt
  HeroCardStyle.kt, HeroCoverShade.kt, HeroTideProgress.kt, HeroCardStylePreferences.kt
  DefaultLandingTabs.kt              # ExploreDefaultTab, SubscriptionsDefaultTab
  SubscriptionsTabStyle.kt, SubscriptionsLayoutPreferences.kt
  LibraryPreferences.kt              # sorting, manual order, pins, legacy RSS repair journal
  HomePinnedShows.kt, PreferenceIdList.kt
  DiscoverySections.kt, DiscoverySectionPreferences.kt, DiscoverySectionLoader.kt
  BriefingEdition.kt
  EngagementPreferences.kt           # tips, review prompt, engagement timing
  EngagementPromptConstants.kt
  NpsSurveySchedule.kt, NpsSurveyStore.kt
  AnnouncementPreferences.kt
  AutoDownloadBackgroundSettings.kt
  PlaybackSkipBounds.kt
  BoxcastPrefs.kt
  PrefsFileMigrator.kt               # file names, legacy reads, DualReadSharedPreferences
```

## Dependencies

- `api`: `:core:model`.
- `implementation`: AndroidX core, DataStore Preferences, coroutines.
- Forbidden: catalog, playback, downloads, analytics, designsystem or feature modules. Palette definitions stay in designsystem; prefs stores only opaque keys.

## Threading / lifecycle

- DataStore streams are cold Flows; repositories, ViewModels and app wiring collect them. Setters are `suspend`.
- Multi-key changes (theme selections and presets, pin toggles, the RSS repair journal, clearing an announcement) happen inside one DataStore `edit`.
- The theme fast cache is a SharedPreferences mirror read synchronously at cold start; DataStore values win once loaded.
- Features use the repository or facade APIs, never raw file access.
- `PrefsFileMigrator` migrates on the SharedPreferences open path.

## Persistence & identity

Storage identities are listed in [`ARCHITECTURE.md`](../../ARCHITECTURE.md#identity-and-storage). Names and keys here are stable and never renamed.

Files:

| Name | Kind | Holds |
| :--- | :--- | :--- |
| `user_preferences` | DataStore | Settings keyed in `UserPreferenceKeys.kt` (`Keys`), review and engagement keys, announcements, discovery sections |
| `boxlore_theme_fast_cache` | SharedPreferences | Cold-start mirror of appearance and landing choices |
| `boxlore_prefs` | SharedPreferences | `BoxcastPrefs` values |
| `boxlore_player`, `boxlore_api_config`, `boxlore_referrer_prefs`, `boxlore_analytics_prefs` | SharedPreferences | Canonical names opened through `PrefsFileMigrator` for other modules |
| `auto_download_background` | DataStore | Background auto-download consent; excluded from backup and device transfer |
| `boxlore_nps_survey` | DataStore | Survey last shown, launches, last finish, legacy carry-over flags; excluded from backup |
| `boxlore_listening_device_id` | File in `noBackupFilesDir` | Per-installation listening device ID |

Contracts:

- Legacy `boxcast_*` files are read through `PrefsFileMigrator`; the canonical `boxlore_*` file wins.
- The fast cache mirrors `theme_config`, `surface_style`, `theme_brand`, `use_dynamic_color`, `font_roundness`, `navigation_style`, `open_app_to`, `widget_appearance`, `explore_default_tab`, `subscriptions_default_tab`, plus the full player, navigation blur, hero, artwork-colour, custom theme and Picked for you choices.
- After an Android backup restore, `hydrateMissingDataStoreFromFastCache` copies cached values only into missing DataStore keys; an existing DataStore choice always wins.
- Hero choices are cached and hydrated only once picked, so listeners who never picked follow the current default.
- `theme_brand` holds a named palette, a `#RRGGBB` Material 3 seed, or `exact:#RRGGBB` (primary used as is).
- `HeroCardStyle` stores Block as `standard` and Cover as `classic`; Tide, Duotone, Halo and Marquee as `tide`, `duotone`, `halo`, `marquee`. `HeroCoverShade.UNSET` means not yet picked.
- `subscription_manual_order`, `subscription_folder_manual_order` and `home_pinned_podcast_ids` are `PreferenceIdList` strings in `user_preferences`.
- `content_languages` holds at most four languages, English first; `content_english_off` applies only while every pick is in `ContentRegions.ENGLISH_OFF_LANGUAGES` ([`:core:model`](../model/README.md)).
- The Brief discovery section reuses `briefing_dismissed_forever` inverted; turning it on clears `briefing_dismissed_date`. Other sections use `discovery_picked_for_you`, `discovery_because_you_like`, `discovery_curated_rows`, `discovery_more_like_this`.
- `legacy_rss_repair_version`, `legacy_rss_repair_pending_old_id` and `legacy_rss_repair_pending_new_id` are a crash-recovery journal; finishing it rewrites manual order, pins, `overridden_rec_podcast_id` and `last_seen_episode_id_*` in one transaction.
- Review prompt keys: `review_milestone_pending`, `review_milestone_consumed`, `review_prompt_count`, `review_last_prompt_at`, `review_has_reviewed`, `library_imported_at`, `engagement_last_prompt_at`.
- Survey keys kept in `user_preferences`: `nps_survey_fired`, `nps_last_score`; `has_logged_first_play` is read only.
- `seen_listening_story` stores the last monthly listening story opened as `yyyy-MM`, local to the phone.
- `sync_device_id` is backed up with `boxlore_prefs`; the listening device ID is not, so two phones restored from one backup stay distinct. `getListeningLegacyOrigin()` is `legacy-` plus the sync device ID.
- Sync cursors in `boxlore_prefs`: `sync_last_timestamp`, `sync_last_user_id`, `sync_metadata_version`, `sync_listening_pulled_until`, `sync_listening_dates_sent_after`, `sync_listening_dates_sent`, `sync_listening_totals_sent`, `sync_listening_release_known_sent`.
- Other `boxlore_prefs` keys include `onboarding_completed`, `user_genres`, `cached_byl_*`, `pending_auth_email`, `feedback_draft_*`, `featured_video_showcase_dismissed`, `tablet_ui_alpha_notice_acknowledged`, `has_requested_notification_permission`, `learner_log_enabled`.
- `AutoDownloadBackgroundSettings` is enabled only by its explicit setter; auto-download preferences and per-show flags never grant consent.

## Testing notes

Tests live in `core/prefs/src/test` (JUnit 5, Robolectric for DataStore and SharedPreferences).

| Area | Test classes |
| :--- | :--- |
| Repository, presets, full player | `UserPreferencesRepositoryTest`, `ThemeSelectionPreferencesTest` |
| Backup restore hydration | `UserPreferencesRestoreHydrationTest` |
| Appearance values | `HeroCardStylePreferencesTest`, `NavigationBlurPreferencesTest`, `NavigationStylePreferenceTest`, `FontRoundnessAxisTest`, `WidgetAppearanceTest` |
| Landing and tabs | `OpenAppToPreferenceTest`, `DefaultLandingTabsTest`, `SubscriptionsTabStylePreferenceTest`, `SubscriptionsGridViewPreferenceTest` |
| Library and folders | `HomePinnedShowsTest`, `PreferenceIdListTest`, `AutoOrganizeLookPreferenceTest` |
| Content and discovery | `ContentLanguagePreferencesTest`, `BriefingEditionTest`, `DiscoverySectionPreferencesTest`, `DiscoverySectionLoaderTest` |
| Prompts and survey | `ReviewPromptPreferencesTest`, `NpsSurveyScheduleTest`, `NpsSurveyStoreTest`, `AnnouncementPreferencesTest` |
| Files and facade | `BoxcastPrefsTest`, `PrefsFileMigratorTest`, `AutoDownloadBackgroundSettingsTest` |

```bash
./gradlew :core:prefs:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests with the project suite.
- App, catalog, playback and feature tests rely on these keys and defaults.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:analytics` README](../analytics/README.md)
- [`:core:model` README](../model/README.md)
