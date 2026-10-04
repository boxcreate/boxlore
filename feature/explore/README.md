# `:feature:explore`

## Purpose

Owns Explore discovery and the Learn tab presentation: browse/search discovery, Learn curiosity cards, Learn history, and local UI state for those surfaces. It does not own recommendation engines, network clients, preference storage, playback services, or other feature screens.

## Public API

- `ExploreScreen` and `ExploreViewModel` for the Explore route. The initial pager tab is For You unless Appearance **Default tabs** is Top, a genre category is present (always Top), or the route already names `tab` (`trending` / `top` / `for_you`).
- Explore hero/browse headers use `rememberSectionHeaderFontFamily()` from `:core:designsystem` for section titles tied to Appearance lettering roundness; Explore and Learn use centralized Google Sans Flex weight tokens. Trending “All” uses the **Top charts** header with a leaderboard icon (genre filters still use “Top in {genre}”).
- Explore list and selector-FAB clearance uses designsystem’s shared navigation-style / mini-player padding contract so controls remain above either app chrome.
- The floating tab selector follows the adaptive player's shared docking clock through a placement-only offset. It lowers above Floating navigation when playback becomes compact and rises again as it expands; the grid's bottom padding stays fixed during the morph. Explore initializes player visibility from the current playback snapshot so entering with an already-compact player does not briefly use the no-player spacing.
- Trending genre row (`ExploreGenreSelector`) uses shared `PillFilterChip` icon+label pills (same language as onboarding search) with All + top genres + **More** opening the existing Browse Genres bottom sheet.
- For You mood row (`ExploreVibeChipRow`) sits in the same sticky header slot as genres (matched 8dp spacing). Soft 12dp capsules with per-mood icons + chromatic fill — distinct from stadium genre pills. Titles come from shared `CuratedMoods` (same as Home daypart rails). Mood results and search idle “Suggested for you” titles share scrollable `ExploreIconTitleHeader` (12dp top / 8dp bottom); suggestion blocks are icon + title only (no subtitle).
- Explore search chips: **Find a show** (search icon; Meili typeahead + hybrid **Also found**, 300ms debounce) and **Ask anything** (sparkle icon; one CF embed → podcast + episode vectors via `GET /search/semantic`, 1000ms debounce before embed; loader while waiting). Find-a-show pins the grid to the top via shared `ProgressiveSearchScrollLogic` when progressive catalog hits prepend over local substring matches or a Matches header appears; Ask anything is unchanged (one complete result set behind a loader). The chip row is a centered stadium (not full-width) sized for icon+label. Ask-anything idle/no-results: extra top inset, then centered title+subtitle and four full-width natural-question rows. Search-field placeholder follows the selected chip. Example queries were checked live against `/search/semantic`. Ask-anything results: **Related shows** rail + **Episodes** header above the hero/bento feed. Discovery rails and staggered grid sections protect items against duplicate key collisions using `LazyListKeyPolicy`.
- `LearnScreen` and `LearnViewModel` for the Learn route (curiosity cards, queue/play actions). Lore shares a ViewModel-owned artwork colour cache between the active card, stacked cards and page: the next four artwork keys are prepared with at most two loads at once, so promoting a prepared card does not restart extraction. The page follows that card with a single 180ms colour transition; cards retain their own prepared colours. Empty episode artwork falls back to a nonblank show image, including history and episode mappings.
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
- UI runs on the main thread; search, recommendation, and history work use suspend APIs. Lore palette requests share the card backing's optimized URL, decode at 96px into software bitmaps, and try optimized → original → show artwork with bounded request waits. Palette quantization runs on Default; saturated artwork swatches outrank neutral logo backgrounds without promoting tiny colour specks. Cache/job bookkeeping stays on the ViewModel/main scope, retains up to 64 successful palettes across screen recreation, deduplicates in-flight work, and cancels obsolete deck requests. Cancellation cannot publish a stale palette or clear the current card's colour; failed requests remain retryable. Missing/neutral artwork uses the same theme accent for page and card. Lore halos follow `LocalEffectiveDarkTheme` (including Appearance overrides), and pulse/drift values are read in graphics layers rather than recomposing the screen each frame.

## Persistence & identity

- This module owns no raw storage files.
- Learn history and recommendation caches are accessed through `BoxcastPrefs` in `:core:prefs`.
- Explore For You uses the **same** Home bootstrap recommendations payload: hydrate from `BoxcastPrefs` shared cache, refresh via `PodcastRepository.getHomeBootstrapData` (country + content languages), and write that cache back (taste or popular-in-region fallback). It does not call the standalone empty-seed `getPersonalizedRecommendations` path.
- Explore vibe picks call `getCuratedPodcasts` with the user's content region and languages (same as Home daypart rails).
- Network DTOs map to feature UI models before entering UI state.

## Testing notes

- Unit tests live under `feature/explore/src/test`.
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
