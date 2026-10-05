# `:feature:explore`

## Purpose

Owns Explore discovery and the Learn tab presentation: browse/search discovery, Learn curiosity cards, Learn history, and local UI state for those surfaces. It does not own recommendation engines, network clients, preference storage, playback services, or other feature screens.

## Public API

- `ExploreScreen` and `ExploreViewModel` for the Explore route. The initial pager tab is For You unless Appearance **Default tabs** is Top, a genre category is present (always Top), or the route already names `tab` (`trending` / `top` / `for_you`).
- Explore hero/browse headers use `rememberSectionHeaderFontFamily()` from `:core:designsystem` for section titles tied to Appearance lettering roundness; Explore and Learn use centralized Google Sans Flex weight tokens. Trending “All” uses the **Top charts** header with a leaderboard icon (genre filters still use “Top in {genre}”).
- Explore list and selector-FAB clearance uses designsystem’s shared navigation-style / mini-player padding contract. Its edge-to-edge layout explicitly passes the bottom system inset for both Classic and Floating navigation, keeping controls and the list tail above the player when Android uses gestures or navigation buttons. Shared player/selector gap regression coverage lives in designsystem's `NavigationChromePlacementTest`.
- The floating tab selector follows the adaptive player's shared docking clock through a placement-only offset. It lowers above Floating navigation when playback becomes compact and rises again as it expands; the grid's bottom padding stays fixed during the morph. Explore initializes player visibility from the current playback snapshot so entering with an already-compact player does not briefly use the no-player spacing.
- Trending genre row (`ExploreGenreSelector`) uses shared `PillFilterChip` icon+label pills (same language as onboarding search) with All + top genres + **More** opening the existing Browse Genres bottom sheet.
- For You mood row (`ExploreVibeChipRow`) sits in the same sticky header slot as genres (matched 8dp spacing). Soft 12dp capsules with per-mood icons + chromatic fill — distinct from stadium genre pills. Titles come from shared `CuratedMoods` (same as Home daypart rails). Mood results and search idle “Suggested for you” titles share scrollable `ExploreIconTitleHeader` (12dp top / 8dp bottom); suggestion blocks are icon + title only (no subtitle).
- Explore search chips: **Find a show** (search icon; Meili typeahead + hybrid **Also found**, 300ms debounce) and **Ask anything** (sparkle icon; one CF embed → podcast + episode vectors via `GET /search/semantic`, 1000ms debounce before embed; loader while waiting). Find-a-show pins the grid to the top via shared `ProgressiveSearchScrollLogic` when progressive catalog hits prepend over local substring matches or a Matches header appears; Ask anything is unchanged (one complete result set behind a loader). The chip row is a centered stadium (not full-width) sized for icon+label. Ask-anything idle/no-results: extra top inset, then centered title+subtitle and four full-width natural-question rows. Search-field placeholder follows the selected chip. Example queries were checked live against `/search/semantic`. Ask-anything results: **Related shows** rail + **Episodes** header above the hero/bento feed. Discovery rails and staggered grid sections protect items against duplicate key collisions using `LazyListKeyPolicy`.
- `LearnScreen` and `LearnViewModel` for the Learn route (curiosity cards, queue/play actions). Lore shares a ViewModel-owned artwork colour cache between the active card, stacked cards and page: the next four artwork keys are prepared with at most two loads at once, so promoting a prepared card does not restart extraction. The page halos and logo share a swipe-linked colour clock: dragging blends toward the next prepared card, cancellation reverses that blend, and promotion retains the destination without an old-colour flash. Palette endpoints stay frozen through an interrupted gesture; button changes and late palettes ease over 300ms. Cards retain their own artwork colours. Pointer updates are synchronous, disposed motions cannot apply actions, and exits keep their endpoint until deck replacement. Empty episode artwork falls back to a nonblank show image, including history and episode mappings.
- Lore cards keep the complete question, explanation and podcast title, without line limits or a scrolling reading area. Actual font measurement reclaims spacing before reducing headings from 28sp to a 24sp floor; body text stays 16sp/24sp and system font scaling remains active. Narrow/short windows use wider cards and smaller outer header gaps. The podcast link and prominent 64dp play/pause control share one footer; narrow/short windows use a 56dp control with reduced footer padding to retain reading room. The top actions are **Skip**, **Details**, and **Add to queue**, with 48dp minimum targets, wrapping labels and localizable strings. Card washes/stripe/play control consistently use their own artwork accent. Touching the reading surface no longer scales the whole card: only a deliberate horizontal drag moves it. Controls use a small 3% press response over 120ms without a spring overshoot, including when a swipe cancels a press.
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
- Libraries: Compose, Navigation, Coil, lifecycle runtime/ViewModel Compose, Palette, kotlinx.serialization.json, Turbine and coroutines-test for tests.
- Reverse-edge rule: feature modules must not depend on other feature modules.

## Threading / lifecycle

- ViewModels are scoped by app navigation.
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
