# `:feature:home`

## Purpose

Owns Home feed presentation, Daily and Offline mixes, and local debug surfaces for ranking diagnostics. It presents data from injected core dependencies and does not own settings (decoupled into `:feature:settings`), catalog engines, ranking persistence, playback services, download workers, or Room schemas.

## Public API

- `HomeRoute`, `HomeScreen`, `HomeFeed`, `HomeViewModel`, and `HomeViewModelAssembler` for the Home route. `HomeRoute` reports after its initial loaded feed has committed two frames so the app shell can begin nonessential launch animation without competing with first-paint work. Foreground subscription latest-episode sync is `:core:catalog` `SubscriptionForegroundSync` (started from AppRoot after onboarding; Home's `ensureStarted` is idempotent). New PI subscriptions observed by Home request the application-scoped full publisher-feed ingest instead of only refreshing a PI tip. Library Subscriptions calls `requestRefresh` on appear so open-app-to Subscriptions is a live sync, not a cache-only paint. Your Shows filter lists call `PodcastRepository.getEpisodesPaginated`, which already unions cached feed extras for PI shows opted into **Missing episodes?** so the chip stream is not PI-only. Selected-show and serial-oldest episode queries fetch with a bounded safe page limit of 100 to eliminate heap exhaustion on deep catalogs. The selected-chip signal also includes `latestEpisodeId` / `rssHasNewEpisodes` so a direct-feed tip promote reloads that list; a successful launch persist also bumps `rssRefreshVersion` for the open chip (`HomeForegroundSyncLogic`). Same-chip reloads swap the list in place (no skeleton). Home calls `preferFeedPodcast` so launch sync refreshes that show first. Your Shows and hero still reveal together via `viewportReady` (!isLoading); the filter-chip skeleton freezes its 1-row vs 2-row layout (peak sub count / 2-row placeholder when count is still 0) so Room emissions do not remorph the shimmer mid-wait. Up to five `home_pinned_podcast_ids` prepend the Your Shows cover order (mixtape stays the first chip when there is more than one show; pin 1 is the first podcast after mixtape). Long-press a cover for a Material 3 **Pin** / **Unpin** menu (does not pin on the long-press itself); a sixth pin shows a snackbar and is not added. Pinned covers show a circular pin badge. New unpinned subs still prepend in the remainder, never ahead of pins. Home and Library Smart share the canonical deterministic Your Shows score and one bounded recency floor from `:core:ranking`. Home keeps that order stable while visible, then may refresh it atomically on a later Home/app foreground entry once its snapshot is at least 30 minutes old; a 0.05 score hysteresis prevents tiny changes from moving covers. Subscription and pin changes remain immediate. Layout in `LibrarySection` is unchanged (≤4 row, 5–9 two rows of 5, >9 interleaved 2-row grid).
- Because You Like identifies its seed with an expressive editorial stamp: a solid primary-container surface with the same visible `ExpressiveShapes` language as image fallbacks, subtly tilted artwork, overlapping heart badge, and borderless swap icon. Tapping the rest of the selector opens that show.
- Because You Like anchor show selection rotates automatically across three time-of-day slots (Morning, Afternoon, Night) based on an engagement-threshold candidate pool (subscriptions and listening history) and day-part genre affinity (`BecauseYouLikeRotationLogic`). It introduces day-to-day variation so the same show is not repeatedly selected at the same slot across consecutive days when pool strength permits, and caches slot keys in `BoxcastPrefs` to limit server calls to at most 3 per day. Manual user show overrides in `ChangeRecommendationPodcastSheet` remain strictly sticky until reset to auto-detect.
- Because You Like and daypart editorial rails size cards to 41% of the available viewport, keeping two cards complete while exposing enough of card three to signal horizontal scrolling. Video Spotlight and the two-column vertical grids keep their own sizing. Because You Like rails protect show and episode lists against duplicate key collisions using `LazyListKeyPolicy`.
- Home Explore genre row (`GenreSelector`) uses shared `PillFilterChip` icon+label pills (same language as onboarding search) with Top + top genres + **More** opening the existing Browse Genres bottom sheet.
- Immediately above Explore, Home presents an editorial **Video Spotlight**: TED Talks Daily is the editor’s pick and 13 verified feeds follow in stable popularity order. TED exposes dedicated **HD** and **SD** feed buttons while the card itself opens HD. A borderless, artwork-first horizontal reel sits directly on the page, keeps the next tile visibly peeking at the edge, and states the format once in its header instead of repeating catalog labels. Catalog metadata is bundled and artwork remains lazy Coil content, so the section performs no launch-time API work. Dismiss asks for confirmation and persists `featured_video_showcase_dismissed`; once confirmed, it never returns to Home. Analytics records the section impression and each card/HD/SD target, then carries `home_video_spotlight` through Podcast Info so play taps and confirmed `playback_started` events remain attributable to the spotlight funnel.
- Hero carousel grid cards (**Jump back in** / **New episodes**) use title-only cells with a lighter scrim: resume taps play (progress + now-playing ring on the matching episode, not every cell from that show), new-episodes taps open episode info. NEW on that grid (and Your Shows covers via `isLatestEpisodeNew`) shares Room `rssHasNewEpisodes` for true-RSS and PI direct-feed tips, plus the usual 48h window.
- The Your Shows mix module keeps **Daily Mix** as the default and makes its heading a Daily/Offline dropdown only with at least two subscriptions and one downloaded episode that is not playback-completed. The listener's last selection persists in `home_mix_mode`; direction-aware title/rail transitions and staggered card entrances distinguish mode changes without starting playback. Offline includes every download provenance but filters completed listens, orders the rest by newest release, caps the Home rail and queue to 15 episodes, and links its trailing card to Library Downloads. The module remains borderless on regular surfaces, adding a subtle outline only for Pure, Pitch black, and Pure white styles where low containers merge into the page background. The separate pill-shaped header play control queues the selected mix with `PlaybackEntryPoint.HOME_MIXTAPE`.
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
    BecauseYouLikeRotationLogic.kt
    FeaturedVideoPodcasts.kt
    HomeEditorialRowsLogic.kt
```

Main Kotlin files should remain below 1000 lines; extracted Home feed, ViewModel, section-row, and logic files keep UI assembly and behavior testable.
- `FeaturedVideoPodcastsShowcase`: Curated video showcase with cohesive touch-press spring scaling, solid surface backgrounds, and independent HD/SD action targets.

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Media3, Coil, Kotlin serialization, Turbine, and Mockito.
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
- RSS IDs, ranking database rows, download cache entries, and playback media IDs are owned by core modules.
- Stable Compose test tags include `home_settings_button`.

## Testing notes

- Unit tests live under `feature/home/src/test`.
- Existing coverage includes Home listening-history formatting,
  discovery greeting, editorial-row selection and de-duplication, equal-height poster grid
  extent math, and Your Shows pin precedence, foreground refresh cadence, and score hysteresis
  (`HomeShowsOrderLogicTest`), plus other pure Home logic helpers.
- Video podcast editorial showcase catalog order, uniqueness, and TED Talks HD/SD feed routing (`FeaturedVideoPodcastsTest`).
- Time-of-day rotation slots, candidate thresholding, genre matching, and cross-day variation logic (`BecauseYouLikeRotationLogicTest`).

```bash
./gradlew :feature:home:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Home JVM tests and includes the module in merged coverage verification.
- `scripts/ci/check-feature-no-boxlore-database.sh` guards direct database usage in feature ViewModels and assemblers.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/screenshots/README.md`](../../docs/screenshots/README.md)
- [`:app` README](../../app/README.md)
