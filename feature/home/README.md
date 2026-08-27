# `:feature:home`

## Purpose

Owns Home feed presentation, Settings screens, RSS-add UI, and local debug surfaces for ranking diagnostics. It presents data from injected core dependencies and does not own catalog engines, ranking persistence, playback services, download workers, or Room schemas.

## Public API

- `HomeRoute`, `HomeScreen`, `HomeFeed`, `HomeViewModel`, and `HomeViewModelAssembler` for the Home route. `HomeRoute` reports after its initial loaded feed has committed two frames so the app shell can begin nonessential launch animation without competing with first-paint work. Foreground subscription latest-episode sync is `:core:catalog` `SubscriptionForegroundSync` (started from AppRoot after onboarding; Home's `ensureStarted` is idempotent). New PI subscriptions observed by Home request the application-scoped full publisher-feed ingest instead of only refreshing a PI tip. Library Subscriptions calls `requestRefresh` on appear so open-app-to Subscriptions is a live sync, not a cache-only paint. Your Shows filter lists call `PodcastRepository.getEpisodesPaginated`, which already unions cached feed extras for PI shows opted into **Missing episodes?** so the chip stream is not PI-only. The selected-chip signal also includes `latestEpisodeId` / `rssHasNewEpisodes` so a direct-feed tip promote reloads that list; a successful launch persist also bumps `rssRefreshVersion` for the open chip (`HomeForegroundSyncLogic`). Same-chip reloads swap the list in place (no skeleton). Home calls `preferFeedPodcast` so launch sync refreshes that show first. Your Shows and hero still reveal together via `viewportReady` (!isLoading); the filter-chip skeleton freezes its 1-row vs 2-row layout (peak sub count / 2-row placeholder when count is still 0) so Room emissions do not remorph the shimmer mid-wait. Up to five `home_pinned_podcast_ids` prepend the Your Shows cover order (mixtape stays the first chip when there is more than one show; pin 1 is the first podcast after mixtape). Long-press a cover for a Material 3 **Pin** / **Unpin** menu (does not pin on the long-press itself); a sixth pin shows a snackbar and is not added. Pinned covers show a circular pin badge. New unpinned subs still prepend in the remainder, never ahead of pins. Home and Library Smart share a deterministic Your Shows score; subscribe recency is a bounded three-day floor in `:core:ranking` (`YourShowsSubscriptionRecency`), not a hard front-of-list slice. Layout in `LibrarySection` is unchanged (≤4 row, 5–9 two rows of 5, >9 interleaved 2-row grid).
- Discovery from **Because You Like** downward uses calmer poster cards from `:core:designsystem` (`FeedMediaCard` / `CuratedEpisodeCard` / `EqualHeightPosterGrid`): title-only under art (no author on podcast cards, no show name on episode posters), equal-height horizontal rails and 2-col grids (`HomeFeedSpacing` / `FeedPosterSpacing`) that reserve two title lines for shows and three for episodes (vertically centering shorter titles). For You shows a featured hero plus up to eight body cards (1+4+4); Home Explore shows up to six. Daypart greeting editorial rows and Based on Your Taste use title-only section headers (no subtitles) and never expose backend terminology. Editorial mood IDs/titles come from shared `:core:catalog` `CuratedMoods` (same source as Explore For You chips).
- Because You Like identifies its seed with an expressive editorial stamp: a solid primary-container surface with the same visible `ExpressiveShapes` language as image fallbacks, subtly tilted artwork, overlapping heart badge, and borderless swap icon. Tapping the rest of the selector opens that show.
- Because You Like and daypart editorial rails size cards to 41% of the available viewport, keeping two cards complete while exposing enough of card three to signal horizontal scrolling. Video Spotlight and the two-column vertical grids keep their own sizing.
- Home Explore genre row (`GenreSelector`) uses shared `PillFilterChip` icon+label pills (same language as onboarding search) with Top + top genres + **More** opening the existing Browse Genres bottom sheet.
- Immediately above Explore, Home presents an editorial **Video Spotlight**: TED Talks Daily is the editor’s pick and 13 verified feeds follow in stable popularity order. TED exposes dedicated **HD** and **SD** feed buttons while the card itself opens HD. A borderless, artwork-first horizontal reel sits directly on the page, keeps the next tile visibly peeking at the edge, and states the format once in its header instead of repeating catalog labels. Catalog metadata is bundled and artwork remains lazy Coil content, so the section performs no launch-time API work. Dismiss asks for confirmation and persists `featured_video_showcase_dismissed`; once confirmed, it never returns to Home. Analytics records the section impression and each card/HD/SD target, then carries `home_video_spotlight` through Podcast Info so play taps and confirmed `playback_started` events remain attributable to the spotlight funnel.
- Hero carousel grid cards (**Jump back in** / **New episodes**) use title-only cells with a lighter scrim: resume taps play (progress + now-playing ring on the matching episode, not every cell from that show), new-episodes taps open episode info. NEW on that grid (and Your Shows covers via `isLatestEpisodeNew`) shares Room `rssHasNewEpisodes` for true-RSS and PI direct-feed tips, plus the usual 48h window.
- The Your Shows mix module keeps **Daily Mix** as the default and makes its heading a Daily/Offline dropdown only with at least two subscriptions and one downloaded episode that is not playback-completed. The listener's last selection persists in `home_mix_mode`; direction-aware title/rail transitions and staggered card entrances distinguish mode changes without starting playback. Offline includes every download provenance but filters completed listens, orders the rest by newest release, caps the Home rail and queue to 15 episodes, and links its trailing card to Library Downloads. The module remains borderless on regular surfaces, adding a subtle outline only for Pure, Pitch black, and Pure white styles where low containers merge into the page background. The separate pill-shaped header play control queues the selected mix with `PlaybackEntryPoint.HOME_MIXTAPE`.
- `settings.SettingsScreen`, `SettingsViewModel`, and `SettingsViewModelAssembler` for Settings. Hub category cards center title and description with the leading icon (descriptions are not forced to two lines). Shared settings rows keep icons vertically centered with their labels; playback duration sliders keep the icon on the heading row so the slider can use the full width. Section labels line up with that same inner padding. Library **Discovery** settings use a Material 3 region sheet (11 storefronts) plus grouped language chips in one block (`content_languages`; English locked, max 4; reset on region change). Full library JSON backup (v6) includes PI shows opted into **Missing episodes?**; restore re-opts those feeds in and refreshes catalog tips. Appearance includes Theme (System / Light / Dark connected toggle), Background, **Colors** (wallpaper colors or a fixed accent; custom picker can keep a Material 3 seed or pin the exact pick — not recommended / not tested for UI readability), **Lettering** (Crisp / Soft / Round connected toggle, same chip language as content region, plus an expandable sample preview), Navigation (Floating / Classic), **Open app to** (Home / Subscriptions / Downloads cold-start landing; Back from launch-Subscriptions or launch-Downloads goes to Home), **Default tabs** (Explore For You or Top; Subscriptions Shows or New episodes — used when the route does not already pick a tab), **Cleaner Home** (off by default; moves Settings and Feedback from the Home top bar to Library), and **Widgets** (App theme by default, or System for launcher light/dark and wallpaper accents). Playback includes **Restart forgotten episodes** (default on; soft-expires mid-episode seek for queue/mixtape after 7 days — see `:core:playback` `PlaybackSkipPolicy`) and **Smart queue** (default on; turning it off makes Smart Queue append only newer/next episodes of the current show). Mixtape cards hide progress chrome for soft-expired picks; Smart Queue labels those fills “Starting over”.
- `DebugScreen` and `DebugViewModel` for local learner and runtime diagnostics.
- Extracted Home UI pieces such as `LibrarySectionRows`, `LibrarySection`, and section/card components. Their Google Sans Flex emphasis uses shared centralized weight tokens.
- Pure logic helpers under `logic/` for Home assembly, discovery, hero ordering, selection, playback-state mapping, serial episodes, affinity behavior, and Daily/Offline mix eligibility and queue selection.
- Home Brief play uses `PlaybackEntryPoint.BRIEFING` so `playback_*` gets glossary `entry_point=briefing` (interactions stay on existing `daily_briefing_action`).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/home/
  HomeFeed.kt
  HomeScreen.kt
  HomeViewModel.kt
  HomeViewModelAssembler.kt
  HomeViewModelBecauseYouLike.kt
  HomeViewModelLoadData.kt
  HomeViewModelSelected.kt
  HomeViewModelSerial.kt
  HomeFeedEditorialRows.kt
  HomeFeedFeaturedVideos.kt
  HomeDataModels.kt
  HomeUiModels.kt
  DebugScreen.kt
  DebugScreenContent.kt
  DebugViewModel.kt
  AdaptiveLearnerDebugSection.kt
  components/
    FeaturedVideoPodcastsShowcase.kt
    LibrarySection.kt
    LibrarySectionRows.kt
    ...
  logic/
    FeaturedVideoPodcasts.kt
    HomeEditorialRowsLogic.kt
  settings/
    SettingsScreen.kt
    SettingsViewModel.kt
    SettingsViewModelAssembler.kt
    components/
    dialogs/
    pages/
```

Main Kotlin files should remain below 1000 lines; extracted Home feed, ViewModel, section-row, and logic files keep UI assembly and behavior testable.

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Media3, Coil, Kotlin serialization, optional Roborazzi for local visual capture, Turbine, and Mockito.
- Reverse-edge rule: feature modules must not depend on other feature modules. ViewModels and assemblers must not directly depend on `BoxLoreDatabase`.

## Threading / lifecycle

- ViewModels are scoped by app navigation or Activity owners.
- Repositories, ports, playback, downloads, prefs, and ranking dependencies are application-scoped instances supplied by app wiring.
- Home surfaces emit glossary analytics through `:core:analytics` (no PostHog direct in the feature).
- Daypart, region, or content-language changes cancel the previous editorial-row load before painting the new
  greeting’s results. Editorial rows load independently from personalized recommendations.
- UI state is exposed through flows and collected by Compose on the main thread.
- Network and database operations run through injected suspend APIs.

## Persistence & identity

- This module owns no storage files or stable preference keys; Home pins are stored as `home_pinned_podcast_ids` in `:core:prefs`. Toggles go through `UserPreferencesRepository.toggleHomePinnedPodcastId` (one DataStore write).
- Settings read and write DataStore and `BoxcastPrefs` through `:core:prefs` APIs.
- RSS IDs, ranking database rows, download cache entries, and playback media IDs are owned by core modules.
- Stable Compose test tags include `home_settings_button`, `settings_add_rss_*`, `settings_downloads_smart`, `settings_downloads_auto`, `settings_reset_analytics_confirm`, and `settings_reset_analytics_cancel`.

## Testing notes

- Unit tests live under `feature/home/src/test`.
- Existing coverage includes Settings ViewModel tests, Home listening-history formatting,
  discovery greeting, editorial-row selection and de-duplication, equal-height poster grid
  extent math, Your Shows pin precedence (`HomeShowsOrderLogicTest`), and pure Home logic helpers.
- Optional Roborazzi goldens for settings dialogs (local only; not CI-gated).

```bash
./gradlew :feature:home:testDebugUnitTest
./gradlew :feature:home:recordRoborazziDebug   # optional
```

## CI relevance

- `unit-tests.yml` runs Home JVM tests and includes the module in merged coverage verification.
- `scripts/ci/check-feature-no-boxlore-database.sh` guards direct database usage in feature ViewModels and assemblers.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/screenshots/README.md`](../../docs/screenshots/README.md)
- [`:app` README](../../app/README.md)
