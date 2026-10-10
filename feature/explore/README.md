# `:feature:explore`

## Purpose

Owns Explore discovery and the Learn tab presentation: browse/search discovery, Learn curiosity cards, Learn history, and local UI state for those surfaces. It does not own recommendation engines, network clients, preference storage, playback services, or other feature screens.

## Public API

- `ExploreScreen` and `ExploreViewModel` for the Explore route. The initial pager tab is For You unless Appearance **Default tabs** is Top, a genre category is present (always Top), or the route already names `tab` (`trending` / `top` / `for_you`).
- Explore hero/browse headers use `rememberSectionHeaderFontFamily()` from `:core:designsystem` for section titles tied to Appearance lettering roundness; Explore and Learn use centralized Google Sans Flex weight tokens. Trending “All” uses the **Top charts** header with a leaderboard icon (genre filters still use “Top in {genre}”).
- Explore list and selector-FAB clearance uses designsystem’s shared navigation-style / mini-player padding contract. Its edge-to-edge layout explicitly passes the bottom system inset for both Classic and Floating navigation, keeping controls and the list tail above the player when Android uses gestures or navigation buttons. Shared player/selector gap regression coverage lives in designsystem's `NavigationChromePlacementTest`.
- Browse supports native horizontal paging in visual order For You → Top, keeping product tab IDs `1` / `0`. Tab taps animate the same pager, and settled swipes use the existing selection callback without changing ranking/filtering. Each browse tab and search keep separate saved grid positions. The header chips sit outside the pager, so horizontal chip scrolling cannot switch tabs; search and mood results retain their existing single-feed interaction.
- For You/Top and search tabs use the designsystem `DiscoveryTabSwitcher`, also used by Subscriptions. The shared control owns presentation and selection semantics only; page-owned callbacks and tab order are unchanged. Search mode insets its complete capsule from the rounded header edges, including bottom clearance, so the parent does not cut its ends. Its 48dp minimum height grows with text scaling, and bottom content clearance uses the same measurement.
- The floating tab selector follows the adaptive player's shared docking clock through a placement-only offset. It lowers above Floating navigation when playback becomes compact and rises again as it expands; the grid's bottom padding stays fixed during the morph. Explore initializes player visibility from the current playback snapshot so entering with an already-compact player does not briefly use the no-player spacing.
- Top genre row (`ExploreGenreSelector`) matches Home Explore’s native Material 3 `FilterChip` tokens: solid surface-container fill, primary-container selected state, borderless stadium shape, 18dp icons and 48dp minimum height with All + top genres + **More** opening the existing Browse Genres bottom sheet.
- For You and Top browse use Home's shared `ExpressiveFeatured` / `ExpressivePoster` discovery cards, with title-only three-line feet and matching font-scaled skeletons. Curated topic destinations use the same poster cards without genre overlays or artist subtitles. Find-a-show results use the same expressive poster cards, including Matches and Also found sections; generic “Podcast” genre chips are hidden in both sections, while specific genres and video badges retain their existing conditions. Ask-anything retains its compact 104dp Related shows rail and eight-show cap, while episode results use one expressive featured card followed by title-only posters with solid duration badges. The first result, source order, callbacks, impression/tap analytics, ranking, deduplication and pagination remain unchanged.
- Explore has no app-logo header. Search and selectors share a rounded tonal backing at rest. Consumed vertical feed scroll progressively clips/fades the selectors and backing, leaving the search pill floating over the feed; the For You/Top switcher stays visible above playback/navigation. Horizontal chip viewports meet the rounded backing edge, with search inset separately so chips do not disappear at an inner rectangular boundary. Upward scroll reveals controls, and reaching the top or changing browse/search mode expands them. The grid reserves the measured expanded header inset rather than changing padding on every scroll frame. Search/mood-result controls remain expanded.
- For You mood row (`ExploreVibeChipRow`) shares the collapsing header slot with genres (matched 8dp spacing). Connected tonal topic buttons use 2dp gaps, curved outer ends, tight inner corners and larger standalone primary icons. Existing mood titles can wrap to two lines with font-scaled height; they navigate rather than remain selected. Titles come from shared `CuratedMoods` (same as Home daypart rails). Topic destinations have separate floating Back to Explore and small icon-only Search pills without a shared backing and a scrollable, font-scaled topic heading/subtitle with a native expressive shape. Matching poster skeletons replace the large blocking loader; an empty result offers Back to Explore without inventing a retry or error cause. A topic uses its own grid position, starts at its heading, and returns to the saved browse position through either toolbar or system Back; predictive Back slides the topic with gesture progress over the real saved Explore pane, cancellation restores it, and toolbar/system Back finish the same 350ms slide before leaving. Repeated Back is consumed during completion; parent navigation resumes after the topic closes. Preview panes retain scroll state but suppress interaction, accessibility actions, pagination and selection callbacks. Topic rendering is independent of the last search-tab choice.
- Explore search chips: **Find a show** (search icon; Meili typeahead + hybrid **Also found**, 300ms debounce) and **Ask anything** (sparkle icon; one CF embed → podcast + episode vectors via `GET /search/semantic`, 1000ms debounce before embed; loader while waiting). Find-a-show pins the grid to the top via shared `ProgressiveSearchScrollLogic` when progressive catalog hits prepend over local substring matches or a Matches header appears; Ask anything is unchanged (one complete result set behind a loader). Search-field placeholder follows the selected chip. Example queries were checked live against `/search/semantic`. Ask-anything results: **Related shows** rail + **Episodes** header above the hero/bento feed. Discovery rails and staggered grid sections protect items against duplicate key collisions using `LazyListKeyPolicy`.
- Search landing uses the shared fixed tonal mode capsule, centered text-only headings and compact suggestions. The collections heading is **Explore collections**, without a subtitle. Other titles and hints share a center axis; hints have a narrower reading width and wrap with system text size, without an offset decorative icon. Find-a-show topics use solid low-container cards with one primary icon accent and a font-scaled two-line title slot; all topics share the same accent hierarchy. With no topics, the landing still explains how to search. Ask-anything keeps its four original query strings and order in connected tonal rows with distinct subject icons, 64dp minimum targets and expanding text geometry. Idle/recovery headings and hints are resources; empty browsing and empty searching give guidance for their respective controls. Search loading uses a compact labelled, politely announced status rather than a large blank loader panel. Query dispatch, debounce, ranking and pagination are unchanged by the presentation work.
- `LearnScreen` and `LearnViewModel` for the Learn route (curiosity cards, queue/play actions). Lore shares a ViewModel-owned artwork colour cache between the active card, stacked cards and page: the next four artwork keys are prepared with at most two loads at once, so promoting a prepared card does not restart extraction. The page halos and logo share a swipe-linked colour clock: dragging blends toward the next prepared card, cancellation reverses that blend, and promotion retains the destination without an old-colour flash. Palette endpoints stay frozen through an interrupted gesture; button changes and late palettes ease over 300ms. Cards retain their own artwork colours. Pointer updates are synchronous, disposed motions cannot apply actions, and exits keep their endpoint until deck replacement. Empty episode artwork falls back to a nonblank show image, including history and episode mappings.
- Lore cards keep the complete question, explanation and podcast title, without line limits or a scrolling reading area. Actual font measurement reclaims spacing before reducing headings from 28sp to a 24sp floor; body text stays 16sp/24sp and system font scaling remains active. Narrow/short windows use wider cards and smaller outer header gaps. The podcast link and prominent 64dp play/pause control share one footer; narrow/short windows use a 56dp control with reduced footer padding to retain reading room. Loading uses designsystem's shared Material 3 circular wavy indicator, sized to match the play glyph, while the loading label and disabled action remain intact. The top actions are **Skip**, **Details**, and **Add to queue**, with 48dp minimum targets, wrapping labels and localizable strings. Card washes/stripe/play control consistently use their own artwork accent. Touching the reading surface no longer scales the whole card: only a deliberate horizontal drag moves it. Controls use a small 3% press response over 120ms without a spring overshoot, including when a swipe cancels a press.
- A deliberate card swipe reveals the **Skip** icon from the physical left edge or **Add to queue** from the right. Both cues use the same solid 72dp artwork-tinted circle and 36dp white icon, matching the play control's tonal fill. The cue slides in, fades and grows with the same drag distance, reaching full size at the existing action threshold; cancelling or reversing retreats immediately. Minor travel stays quiet, only one direction is visible, and the cue consumes no reading space or input. Decorative overlay semantics leave the existing labelled, accessible controls as the action targets. Two smaller accent spots enrich the page's upper-right and lower-left background alongside its existing drifting halos; all use the shared swipe-linked artwork colour.
- `LearnHistoryScreen` and `LearnHistoryViewModel` for Learn history.
- `LearnCuriosityHistoryStore` for Learn history persistence through prefs APIs.
- `LearnCuriosityCard` as the feature UI model for curiosity cards.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/explore/
  ExploreScreen.kt
  ExploreViewModel.kt
  LearnCuriosityCard.kt
  LearnCuriosityHistoryStore.kt
  LearnHistoryScreen.kt
  LearnHistoryViewModel.kt
  LearnScreen.kt
  LearnViewModel.kt
  LoreArtworkAccents.kt
  LoreArtworkAccentLoader.kt
  components/
  logic/
```

## Dependencies

- Project dependencies: `:core:designsystem`, `:core:catalog`, `:core:playback`, `:core:model`, `:core:network`, `:core:analytics`, `:core:ranking`, and `:core:prefs`.
- Libraries: Compose, Activity Compose (topic predictive Back), Navigation, Coil, lifecycle runtime/ViewModel Compose, Palette, kotlinx.serialization.json, Turbine and coroutines-test for tests.
- Reverse-edge rule: feature modules must not depend on other feature modules.

## Threading / lifecycle

- ViewModels are scoped by app navigation.
- Remembered Lore swipe state reads the latest drag and completion callbacks explicitly from Compose state, retaining callback freshness when an unchanged deck recomposes. Page gradients and accent spots use a dedicated draw-cache modifier, separate from the animated halo layout; the palette values and shared color clock are unchanged.
- Lore's playback action uses a same-file helper to keep play/pause branching out of the screen layout. It reuses the injected playback repository and the screen coroutine scope, retaining current-episode toggling and single-episode Lore queue playback.
- Catalog, playback, ranking, and prefs access come through injected application-scoped dependencies.
- Analytics: Explore/Learn ViewModels and screens call `:core:analytics` façades for glossary search / Learn exhaustion events (no PostHog direct).
- UI runs on the main thread; search, recommendation, and history work use suspend APIs. Lore palette requests share the card backing's optimized URL, decode at 96px into software bitmaps, and try optimized → original → show artwork with bounded request waits. Palette quantization runs on Default; saturated artwork swatches outrank neutral logo backgrounds without promoting tiny colour specks. Cache/job bookkeeping stays on the ViewModel/main scope, retains up to 64 successful palettes across screen recreation, deduplicates in-flight work, and cancels obsolete deck requests. Cancellation cannot publish a stale palette or clear the current card's colour; failed requests remain retryable. Missing/neutral artwork uses the same theme accent for page and card. Lore halos follow `LocalEffectiveDarkTheme` (including Appearance overrides); colour is read in draw caches, and pulse/drift plus deck transforms are read in graphics layers rather than recomposing the deck each frame. Lore alone reclaims the Floating miniplayer row through the shared docking clock during measurement; its card viewport keeps system navigation insets and the baseline safety gap.

## Persistence & identity

- This module owns no raw storage files.
- Learn history and recommendation caches are accessed through `BoxcastPrefs` in `:core:prefs`.
- Explore For You uses the **same** Home bootstrap recommendations payload: hydrate from `BoxcastPrefs` shared cache, refresh via `PodcastRepository.getHomeBootstrapData` (country + content languages), and write that cache back (taste or popular-in-region fallback). It does not call the standalone empty-seed `getPersonalizedRecommendations` path.
- Explore vibe picks call `getCuratedPodcasts` with the user's content region and languages (same as Home daypart rails).
- Network DTOs map to feature UI models before entering UI state.

## Testing notes

- Unit tests live under `feature/explore/src/test`.
- `LoreCardTextTest` uses production Google Sans font measurement with real long card text at narrow portrait widths and 130% system text (including the larger play control's footer budget), checks space-first fitting, and preserves the typography floor for impossible content. It uses JVM native text layout, without screenshots or device automation.
- `LoreMotionTest` covers bounded control feedback and interrupted press release, both swipe directions, cancellation, frozen palettes, promotion without rollback, interrupted colour transitions, synchronous pointer movement, exactly-once actions, obsolete-motion cancellation, and Floating-only card clearance.
- `LoreSwipeFeedbackTest` covers the matching side, directional slide, deliberate-travel onset, cancellation/reversal, bounded growth through exit, and invalid gesture values. Feedback reads motion in graphics layers; the added background spots use draw-cache gradients, without extra animation clocks or blur layers.
- `ExploreTabPagerLogicTest` covers existing tab-ID mapping, settled selection deduplication and search/mood exclusion; `ExploreTabPagerTest` uses the actual Explore composition under JVM Compose/Robolectric for swipes, tab taps, chip isolation, scroll retention and externally selected Top. No emulator/device automation is required.
- `ExploreTopicResultsTest` exercises the actual topic shortcut, ordered tap analytics, title-only cards, search-mode retention/focus, matching loading geometry, empty recovery, browse scroll restoration, system-back interception, predictive peek/cancel/commit from both edges, interrupted/repeated Back, subsequent parent navigation, and enlarged RTL title layout under JVM Compose/Robolectric.
- `ExploreSearchLandingTest` checks opening search, empty-topic guidance, distinct browse/search recovery, mode switching, topic destinations, exact question queries, no-match recovery, bounded accessible loading states, centered title/hint geometry, fully rendered capsule edges inside the rounded header at normal and enlarged narrow RTL sizes, and connected-row geometry/callbacks with enlarged RTL text.
- `ExploreSearchResultsTest` checks ordered catalog/additional-show tap contracts, distinct compact-show/featured-episode geometry, episode navigation context, duration/zero-duration states, retained results during refresh, and long-title interaction with enlarged RTL text.
- `ExploreChromeScrollLogicTest` covers progressive down/up reveal, clamped flings, unconsumed scroll, zero-height/search expansion, invalid deltas. Phone visual checks remain necessary for drag feel, clipping, focus, enlarged text and player clearance.
- Existing coverage includes Learn pagination, Learn deck logic, Explore browse logic, and shared recommendation cache helpers (`ExploreSharedRecommendationsLogicTest`). Find-a-show pin-to-top is covered in `:core:designsystem` (`ProgressiveSearchScrollLogicTest`).
- `LoreArtworkAccentsTest` covers blank/show fallbacks, proxy/raw failures, cancellation propagation, shared prefetch and reuse, late obsolete work, retryability, bounded cache retention, and meaningful colour selection. These are JVM logic checks without screenshots or device automation.
- Prefer fakes for repository and prefs dependencies when expanding ViewModel coverage.

```bash
./gradlew :feature:explore:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Explore JVM tests with the project suite.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:app` README](../../app/README.md)
- [`:core:prefs` README](../../core/prefs/README.md)
