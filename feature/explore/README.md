# `:feature:explore`

## Purpose

Owns Explore discovery and the Learn tab presentation: browse/search discovery, Learn curiosity cards, Learn history, and local UI state for those surfaces. It does not own recommendation engines, network clients, preference storage, playback services, or other feature screens.

## Public API

- `ExploreScreen` and `ExploreViewModel` for the Explore route.
- Explore hero/browse headers use `rememberSectionHeaderFontFamily()` from `:core:designsystem` for section titles tied to Appearance lettering roundness; Explore and Learn use centralized Google Sans Flex weight tokens. Trending “All” uses the **Top charts** header with a leaderboard icon (genre filters still use “Top in {genre}”).
- Explore list and selector-FAB clearance uses designsystem’s shared navigation-style / mini-player padding contract so controls remain above either app chrome.
- Trending genre row (`ExploreGenreSelector`) uses shared `PillFilterChip` icon+label pills (same language as onboarding search) with All + top genres + **More** opening the existing Browse Genres bottom sheet.
- For You mood row (`ExploreVibeChipRow`) sits in the same sticky header slot as genres (matched 8dp spacing). Soft 12dp capsules with per-mood icons + chromatic fill — distinct from stadium genre pills. Titles come from shared `CuratedMoods` (same as Home daypart rails). Mood results and search idle “Suggested for you” titles share scrollable `ExploreIconTitleHeader` (12dp top / 8dp bottom); suggestion blocks are icon + title only (no subtitle).
- Explore search chips: **Find a show** (search icon; Meili typeahead + hybrid **Also found**, 300ms debounce) and **Ask anything** (sparkle icon; one CF embed → podcast + episode vectors via `GET /search/semantic`, 1000ms debounce before embed; loader while waiting). The chip row is a centered stadium (not full-width) sized for icon+label. Ask-anything idle/no-results: extra top inset, then centered title+subtitle and four full-width natural-question rows. Search-field placeholder follows the selected chip. Example queries were checked live against `/search/semantic`. Ask-anything results: **Related shows** rail + **Episodes** header above the hero/bento feed.
- `LearnScreen` and `LearnViewModel` for the Learn route (curiosity cards, queue/play actions).
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
- UI runs on the main thread; search, recommendation, and history work use suspend APIs.

## Persistence & identity

- This module owns no raw storage files.
- Learn history and recommendation caches are accessed through `BoxcastPrefs` in `:core:prefs`.
- Explore For You uses the **same** Home bootstrap recommendations payload: hydrate from `BoxcastPrefs` shared cache, refresh via `PodcastRepository.getHomeBootstrapData` (country + content languages), and write that cache back (taste or popular-in-region fallback). It does not call the standalone empty-seed `getPersonalizedRecommendations` path.
- Explore vibe picks call `getCuratedPodcasts` with the user's content region and languages (same as Home daypart rails).
- Network DTOs map to feature UI models before entering UI state.

## Testing notes

- Unit tests live under `feature/explore/src/test`.
- Existing coverage includes Learn pagination, Learn deck logic, Explore browse logic, and shared recommendation cache helpers (`ExploreSharedRecommendationsLogicTest`).
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
