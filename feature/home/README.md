# `:feature:home`

## Purpose

Owns Home feed presentation, Daily and Offline mixes, and local debug surfaces for ranking diagnostics. It presents data from injected core dependencies and does not own settings (decoupled into `:feature:settings`), catalog engines, ranking persistence, playback services, download workers, or Room schemas.

## Public API

- Both chapter and child heading text explicitly use `onSurface`, so an artwork-colored navigation shell cannot leak its foreground into a dark Home preview. `HomeHeaderPaletteTest` renders both levels under an inherited black content color on the JVM.

- `HomeRoute`, `HomeScreen`, `HomeFeed`, `HomeViewModel`, and `HomeViewModelAssembler` for the Home route. `HomeRoute` reports after its initial loaded feed has committed two frames so the app shell can begin nonessential launch animation without competing with first-paint work. Foreground subscription latest-episode sync is `:core:catalog` `SubscriptionForegroundSync` (started from AppRoot after onboarding and the initial screen commits). New PI subscriptions observed by Home request the application-scoped full publisher-feed ingest instead of only refreshing a PI tip. Library Subscriptions calls `requestRefresh` on appear so open-app-to Subscriptions is a live sync, not a cache-only paint. Your Shows filter lists call `PodcastRepository.getEpisodesPaginated`, which already unions cached feed extras for PI shows opted into **Missing episodes?** so the chip stream is not PI-only. Selected-show and serial-oldest episode queries fetch with a bounded safe page limit of 100 to eliminate heap exhaustion on deep catalogs. The selected-chip signal also includes `latestEpisodeId` / `rssHasNewEpisodes` so a direct-feed tip promote reloads that list; a successful launch persist also bumps `rssRefreshVersion` for the open chip (`HomeForegroundSyncLogic`). Same-chip reloads swap the list in place (no skeleton). Home calls `preferFeedPodcast` so launch sync refreshes that show first. Available local subscriptions/resume content can reveal Home before remote trending finishes; discovery keeps its own loading state. The filter-chip skeleton remembers its initial 1-row vs 2-row layout (two rows when the count is unknown) for the loading session. Up to five `home_pinned_podcast_ids` prepend the Your Shows cover order (mixtape stays the first chip when there is more than one show; pin 1 is the first podcast after mixtape). Long-press a cover for a Material 3 **Pin** / **Unpin** menu (does not pin on the long-press itself); a sixth pin shows a snackbar and is not added. Pinned covers show a circular pin badge. New unpinned subs still prepend in the remainder, never ahead of pins. Home and Library Smart share the canonical deterministic Your Shows score and one bounded recency floor from `:core:ranking`. Home keeps that order stable while visible, then may refresh it atomically on a later Home/app foreground entry once its snapshot is at least 30 minutes old; a 0.05 score hysteresis prevents tiny changes from moving covers. Subscription and pin changes remain immediate. Layout in `LibrarySection` is unchanged (≤4 row, 5–9 two rows of 5, >9 interleaved 2-row grid).
- The Home feed import library banner uses inbound download iconography (`Icons.Rounded.Download`) directing the user into the shell-managed library import overlay.
- The selected Your Shows header uses `SelectedShowTitle`: a small RSS icon beside the single-line show title, vertically centered with a fixed gap. Long titles truncate before the icon, and the header reserves spacing before the show-info arrow. The icon has a localized accessibility description and appears for RSS source metadata or a canonical `rss:` identity. JVM layout tests resolve the description from its string resource and cover narrow headers, enlarged text and RTL without screenshots or device automation.
- `TopControlBar` uses the shared `TopBarUtilityActions` capsule for Feedback and Settings, with matching icons and separate accessible touch targets. Existing header placement/collapse, click and long-press actions, and the Cleaner Home shortcut preference remain screen-owned.
- Because You Like preserves its original seed artwork and controls: an extra-large solid secondary-container surface with an 88dp minimum height, a 56dp tilted cover in a 64dp slot, and a 24dp circular on-secondary-container badge containing the shared 14dp `mood_heart_24` icon. Its body decorations use the refined asymmetric placement: a 120dp tertiary burst peeking from the upper end at 10% opacity and a 104dp tertiary cookie peeking from the lower body at 12%. Both are clipped to the opaque card, stay clear of the artwork and emoji, and mirror in RTL without randomizing on recomposition. **Because you like** uses sentence case and the native 14sp/18sp semibold labelLarge style with 0.1sp tracking and on-secondary-container text, giving it a clearer introduction above the show name. The titleMedium two-line show title, 40dp native swap button, emoji badge, original expressive click feedback and surrounding spacing retain their Git presentation. The label and **Change show for recommendations** accessibility description use string resources. Its show-opening and automatic/manual seed selection callbacks are unchanged; the **Similar shows** and **Episodes to try** rails remain below it, using standalone rounded podcast and headphone icons respectively.
- Because You Like anchor show selection rotates automatically across three time-of-day slots (Morning, Afternoon, Night) based on an engagement-threshold candidate pool (subscriptions and listening history) and day-part genre affinity (`BecauseYouLikeRotationLogic`). It introduces day-to-day variation so the same show is not repeatedly selected at the same slot across consecutive days when pool strength permits, and caches slot keys in `BoxcastPrefs` to limit server calls to at most 3 per day. Manual user show overrides in `ChangeRecommendationPodcastSheet` remain strictly sticky until reset to auto-detect.
- Lower Home discovery explicitly opts into the shared `ExpressivePoster` / `ExpressiveFeatured` card family. Media cards reserve three title-only lines with shorter titles vertically centered (episode title or show name), with font-scaled 14sp/20sp text and curved lower artwork corners. Because You Like rails use 42% of the viewport capped at 176dp; daypart rails use 52% capped at 208dp, both with 16dp gaps. On phone widths the second daypart card is visibly clipped at the viewport edge, making horizontal scrolling clear without extra labels; show-based rails retain their smaller proportions. Loaded and loading daypart rails share the same sizing. Grids retain their layout and counts. Major sections and related rails use measured 32dp / 20dp gaps including parent-grid spacing; headings inside rail/genre groups use 12dp before content, while separate lazy heading items retain the parent grid's 16dp gap. Existing content keys and `LazyListKeyPolicy` protections are unchanged. Your Shows, hero, app bar and briefing are outside this presentation change.
- Show-based discovery opens directly with its **Because you like** seed card and related rails; the duplicate **More like this** heading and its browse control are removed. **Picked for you** contains the featured episode and recommendation grid, the existing daypart greeting groups its mood rails, and **Explore shows** keeps a stable title above genre selection and the show grid, without secondary header text. Regional fallback recommendations are named **Popular in your region**, including when the seed group is absent. Major headings use wrapping headlineMedium text and 48dp native browse actions: distinct native Material shapes for each chapter and standard native press feedback. Child rails pair their titleMedium heading with a standalone 28dp icon in the primary or tertiary accent, placed 10dp from the title and mirrored in RTL. No decorative shape sits behind these icons; a 36dp minimum header height keeps the feed rhythm stable. Sections remain open, without dividers or section-wide containers. Remaining browse destinations, analytics contexts, eligibility, item order and caps are unchanged.
- Home Explore genre row (`GenreSelector`) uses native solid Material FilterChips with selected semantics and 48dp touch targets; Top + top genres + **More** still open/select through the existing Browse Genres bottom sheet. Shared onboarding/Explore pills retain their defaults.
- Video Spotlight is removed from the Home presentation. Time-based discovery now leads directly into Explore; the catalog, stored dismissal preference, and playback/ranking logic are unchanged. The first personal recommendation uses a title-first split featured card: a solid tertiary-container title panel with 16sp/24sp three-line text and a standalone forward arrow, alongside artwork flush to the end edge. The artwork takes 44% of the width (up to 176dp) with a sweeping 72dp corner; asymmetric 28dp/12dp outer corners and RTL mirroring give this card a distinct silhouette. The whole card retains the existing episode-opening action, including the artwork and arrow, and omits promotional overlays. It is 152dp tall at normal text size and grows with accessible text; its skeleton shares the same layout.
- Hero carousel grid cards (**Jump back in** / **New episodes**) use title-only cells with a lighter scrim: resume taps play (progress + now-playing ring on the matching episode, not every cell from that show), new-episodes taps open episode info. NEW on that grid (and Your Shows covers via `isLatestEpisodeNew`) shares Room `rssHasNewEpisodes` for true-RSS and PI direct-feed tips, plus the usual 48h window.
- The Your Shows mix module keeps **Daily Mix** as the default and makes its heading a Daily/Offline dropdown only with at least two subscriptions and one downloaded episode that is not playback-completed. The listener's last selection persists in `home_mix_mode`; direction-aware title/rail transitions distinguish mode changes without starting playback. Offline includes every download provenance but filters completed listens, orders the rest by newest release, caps the Home rail and queue to 15 episodes, and links its trailing card to Library Downloads. The module remains borderless on regular surfaces, adding a subtle outline only for Pure, Pitch black, and Pure white styles where low containers merge into the page background. The separate pill-shaped header play control queues the selected mix with `PlaybackEntryPoint.HOME_MIXTAPE`.
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
  HomeDataModels.kt
  HomeUiModels.kt
  DebugScreen.kt
  DebugScreenContent.kt
  DebugViewModel.kt
  AdaptiveLearnerDebugSection.kt
  components/
    LibrarySection.kt
    LibrarySectionRows.kt
    ...
  logic/
    BecauseYouLikeRotationLogic.kt
    FeaturedVideoPodcasts.kt
    HomeEditorialRowsLogic.kt
```

Main Kotlin files should remain below 1000 lines; extracted Home feed, ViewModel, section-row, and logic files keep UI assembly and behavior testable.

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, `:core:ranking`, and `:core:rss`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Media3, Coil, Kotlin serialization, Turbine, and Mockito.
- Reverse-edge rule: feature modules must not depend on other feature modules. ViewModels and assemblers must not directly depend on `BoxLoreDatabase`.

## Startup and loading performance

- The two-row Your Shows skeleton draws five cover chips in each row, including while the subscription count is unknown. `YourShowsSkeletonTest` verifies that all ten covers are visible for unknown and known two-row counts.
- Home shares one linear, draw-only `ShimmerScope`, paused when loading sections are outside the visible grid viewport after the 180ms handoff; loading/content sections retain matching keys. The sweep starts with a visible highlight and uses 8%/18% surface contrast so short cached waits still show movement. Discovery and For You poster grids are emitted as lazy two-card rows instead of eagerly composing an entire grid.
- Discovery poster and featured skeletons share the loaded card layout and font-scaled three-line title measurement; the first recommendation uses a compact horizontal featured card. `HomeMixLayout` shares the loaded and placeholder mix dimensions (324dp). Hero and Your Shows draw loaded content fully opaque underneath an opaque loading cover, then fade only that cover for 180ms. The same shimmer clock continues through the handoff. The cover uses `matchParentSize`, so it does not participate in sizing the loaded section, and is disposed after the fade. A stalled animation keeps the cover/content visible rather than exposing a blank frame. Cached-ready sections skip the cover. Mix cards appear immediately in their reserved rail; mode switches retain the whole-rail/title animation without another entrance animation on every card; empty finished sections stop showing skeletons.
- Subscription selector covers animate placement only, without insertion/removal fades that can hide artwork on an early frame. Same-region bootstrap reloads seed trending from the current snapshot and keep matching editorial rows during refresh; completed responses still replace the snapshot. Discovery cards remain visible while refreshing, and base Home publication cannot overwrite the independently selected category result/loading state.
- The bundled briefing JPEG loads through Coil with a bounded request rather than synchronous `painterResource` decoding.
- The briefing card uses a larger 72 dp white masthead and a plain, left-aligned date beneath it. A stronger header scrim keeps the dateline readable over artwork; reduced artwork spacing offsets the larger logo without expanding the card at normal text size. Date formatting and briefing controls retain their behavior.
- Recommendation cache parsing, serialization and scoring run on Default. Discovery rankings reuse the observed history snapshot and are recomputed only when candidates, history or subscription IDs change; the cache resets with region/language/daypart collectors.
- `HomeCacheRestoreGate` lets the initial local recommendation / Because You Like caches and fallback flag finish restoring before the first assembled Home snapshot or recommendation refresh. Slow cache reads cannot produce a second startup insertion or overwrite a fresh API result. Empty, malformed, or cancelled restoration releases waiters; API refreshes and their cache writes continue normally afterward. This gate waits only for local storage, never for network completion.
- Oldest-first serial lookup waits for usable Home content and shares a two-request limit. Cancellation propagates through recommendation/serial work.
- Regression coverage: `HomeLoadingRevealTest` (opaque cover/content handoff with a frozen animation clock, draw-only cover fade and disposal, immediate cached content), `HomeArtworkHandoffTest` (transparent successful artwork retains its backing, new subscription covers are immediately visible), `HomeRefreshPresentationLogicTest` (category result isolation and refresh visibility), `HomeCacheRestoreGateTest` (complete initial snapshot, delayed-cache/fresh-response ordering, empty/error/cancel handling), `HomeShimmerVisibilityTest` (visible loading sections and offscreen pause), `HomeDiscoveryRankingCacheTest` (including refreshed payloads with unchanged IDs and fresh empty responses), `HomeSerialResolutionLimiterTest`, `HomeLoadingGeometryTest`, `HomeShimmerAnimationTest` (JVM pixel changes during initial loading, a visible moving band during a short cached wait, visible navigation entry before STARTED, loading restart, movement late in the sweep, seam-free tall blocks, and unchanged composition/drawing-cache counts across animation frames), and the local/remote readiness cases in `HomeUiAssemblyLogicTest`.
- Discovery presentation regressions: `HomeDiscoveryHeaderTest` covers narrow phone openings, 2× text, RTL, standalone leading rail icons, heading semantics, four appearance modes, native press stability and genre selection without secondary header text; `HomeDiscoveryFeedSpacingTest` covers compact group spacing, the absence of a duplicate seed heading, fallback/absent-data names, keyed loading geometry, recommendation order/caps and existing browse callbacks. `HomeDiscoveryCardTest` and `HomeDiscoveryInteractionTest` cover shared three-line card geometry, split featured geometry and curved join, artwork curves, the restored seed/emoji presentation, theme surfaces and independent media/seed/genre actions. These are JVM tests; no device automation is required.

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
- Video podcast editorial showcase catalog order, uniqueness, and TED Talks HD/SD feed routing (`FeaturedVideoPodcastsTest`). `HomeDiscoveryInteractionTest` uses native Android graphics to test pointer routing through expressive shape clipping, verifies native episode/featured/seed targets invoke callbacks once, and checks selected genre semantics. `HomeDiscoveryCardTest` verifies phone-width and font-scaled card/skeleton geometry, original seed label/title/action separation and title sizing, soft theme-toned clipped body decorations and a clear artwork/emoji lane with long names, enlarged text and RTL, centered short titles, ellipsis, restored artwork curves, distinct compact rail sizing, a partial second time-rail card at phone widths with enlarged text and RTL, legacy default sizing, and light/dark/dynamic/pure-black surfaces without saved screenshots. `HomeDiscoveryFeedSpacingTest` measures the assembled feed's related and major gaps with optional recommendations, absence of Video Spotlight, and matching loaded/loading time-rail positions.
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

- Time-based editorial subheader icons and their loading placeholders consistently use the primary accent.
- The main time-of-day heading has a decorative 32 dp primary-coloured mark: sunrise, full sun, sunset or crescent/star. Its one-second reveal waits until the entire header is inside the clipped scroll viewport; an interrupted reveal restarts on the next full entry. Afterward, the sun rays turn through 28 degrees and back over 4.8 seconds. At night two stars twinkle in alternating brightness and size over 2.8 seconds; the second is smaller, and the crescent stays stationary. Ambient motion runs only while the whole header is visible and the screen lifecycle is STARTED, and follows Android's duration scale. Completed reveals survive recomposition and lazy-list re-entry. Drawing reads the animation clock without recomposing text; the greeting remains the accessible heading, and browsing/curation behavior is unchanged.

### Home accent hierarchy

- Primary: navigation, selected filters, browse actions and editorial header icons. Browse shapes vary by chapter; their colour role stays consistent.
- Secondary: supporting recommendation context (the Because you like seed card), with on-secondary-container text.
- Tertiary: featured discovery cards and subtle expressive decorations. Both seed decorations use the same tertiary role.
- Surface containers: ordinary podcast cards and unselected controls.

- Related recommendation headers use a paired cover motif: overlapping covers for Similar shows and a tilted episode cover with a play cutout for Episodes to try. Both retain the primary header accent.


- Daily/offline mix uses 176 × 224 dp cover cards with compact artwork above two-line episode titles, consistent 24 dp corners and 48 dp playback buttons at the artwork’s lower edge. Neutral card surfaces give currently playing episodes a primary-container highlight. Shared loading geometry tracks the taller rail and header.

- Your shows uses the Home heading weight and the same outlined Bookmarks library icon as the navigation bar. Its library browse action shares the 48 dp primary-container button with other Home chapter headers. Each chapter has a distinct native Material shape: the broad diagonal Pill for Your shows, Cookie4Sided for Picked for you, Sunny for time-of-day discovery, and Cookie6Sided for Explore. Native press feedback retains each silhouette; destinations and accessibility labels are unchanged.

- Subscription selectors request 320 px artwork, selected-show thumbnails 256 px and mix cards 512 px to avoid enlarging small thumbnails on dense displays.

- Mix cards put episode title first, followed by show and duration. They omit date/new badges and show at most one artwork status badge to keep the compact card uncluttered.

- Mix artwork resolves episode art → episode-carried show art → parent show art → fallback, skipping blank candidates. JVM regression tests cover missing episode art and whitespace.

- Duration/time-left appears in a small solid secondary-container chip on the artwork. Remaining whole minutes include the hours portion and show at least 1m for a sub-minute remainder; `MixTimeTest` covers both regressions. The remaining-time label and library browse accessibility description use string resources. Podcast names use label-small beneath the episode title.

- Mix-card title/show blocks centre vertically in the space below artwork. In-progress cards have a 4 dp inset track with primary fill and a solid secondary-container track. Cards without progress reserve no track space, so text centres in the full area beneath artwork.

- An empty Daily Mix collapses to its heading and caught-up status, without a disabled play button or reserved card rail. The mode dropdown remains available when offline episodes exist. Only entries containing an episode count as playable mix content.
- Nonempty Daily/Offline mix modules have two faint, cropped Material shapes drawn behind the entire module at 4–5% tertiary opacity. Cached outlines mirror in RTL and stay within the module bounds. Empty modules have no decoration; episode cards and module geometry are unchanged.

- `DiscoveryTabSwitcherTest` exercises the shared designsystem control in Home’s existing JVM Compose host: selection callbacks/semantics, fixed/floating badge behavior, RTL and text-scaled overlay clearance. Frozen-clock tests verify that foreground tint follows the indicator position without an immediate target-weight change, including rapid reversed selection in RTL. It imports no other feature and does not change Home UI.
- `TopBarUtilityActionsTest` uses the same JVM host for the shared top-bar action group. Native-graphics checks keep the outer connected corners transparent during held presses, including a shimmering Update segment, and verify the existing independent click/long-press actions. No device screenshots or production update requests are involved.
