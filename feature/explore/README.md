# `:feature:explore`

## Purpose

Owns the Explore tab (For You and Top browse, genre and mood topics, show and episode search) and the Lore tab (the curiosity card deck and its history), with their local UI state.

It does not own recommendation engines, ranking models, network clients, preference storage, playback services or other feature screens; those come from core modules through app wiring. Routes belong to `:app`.

## Public API

Used by `:app` navigation:

- `ExploreScreen` / `ExploreViewModel`: the `explore` route. The ViewModel takes the initial `category` and `tab` route arguments.
- `ExploreScreen.searchRequested` / `onSearchRequestHandled`: set by the shell when the Explore tab is tapped again on Explore. Search opens focused (closing an open topic first; an open search keeps its query and mode), and the request is handled once.
- `LearnScreen` / `LearnViewModel`: the Lore deck on the `learn` route. The screen receives the app's shared `PlaybackRepository` and an `onQueueEpisode` callback; it never creates playback objects.
- `LearnHistoryScreen` / `LearnHistoryViewModel`: Lore history on `learn/history`, with restore, remove and clear all.
- `LearnCuriosityHistoryStore`: Lore history and dismissed IDs, stored through `BoxcastPrefs`. The app creates it per route and passes it to `LearnViewModel`.
- `LearnCuriosityCard`: the curiosity card UI model, mapped from network DTOs and history entries.

## Browse

- Browse pages in visual order For You → Top, keeping tab IDs `1` / `0` (`ExploreTabPagerLogic`). Swipes and tab taps drive the same pager, and each tab keeps its own grid position.
- The first tab is For You unless Appearance **Default tabs** says Top, a genre category is given (always Top), or the route names `tab` (`trending`, `top`, `for_you`). `ExploreDefaultTab.resolveIndex` in `:core:prefs` decides.
- With the **Picked for you** switch off (`DiscoverySections.pickedForYou`), Explore shows Top alone with no switcher and makes no recommendations request; a fetch already running cannot publish.
- For You reads the same recommendations payload as Home: it hydrates from the shared `BoxcastPrefs` cache, refreshes through `PodcastRepository.getHomeRecommendations` with region and content languages, and writes the cache back. A failed request keeps the picks already shown.
- Top shows charts (**Top charts**) or a genre's top shows. The genre row (`ExploreGenreSelector`) shows All and the top genres, with **More** opening the full list.
- Mood topics (`ExploreVibeChipRow`, titles from `CuratedMoods`) open a topic destination loaded with `getCuratedPodcasts` for the listener's region and languages. Back, toolbar or predictive, returns to the saved browse position.
- The header collapses as the feed scrolls down and returns on upward scroll, at the top or on a mode change (`ExploreChromeScrollLogic`); the For You / Top switcher stays visible.
- Cards are the shared discovery cards from `:core:designsystem`; list keys go through `LazyListKeyPolicy` so duplicates never collide.

## Search

- **Find a show** searches show titles as you type after a short debounce. Catalog matches arriving later are prepended, and the grid stays pinned to the top (`ProgressiveSearchScrollLogic` in `:core:designsystem`); results split into **Matches** and **Also found**.
- **Ask anything** searches shows and episodes by meaning. It waits longer before sending, so partial queries are not sent, and shows one complete result set behind a loader: a **Related shows** rail, then episodes.
- The search landing lists **Explore collections** for Find a show and fixed example questions for Ask anything (`ConceptSearchIdleLogic`). Empty and no-match states explain what to try.
- Search mode and open search state survive resizing across the tablet gates.

## Lore

- `LearnViewModel` loads curiosity pages through `PodcastRepository.getCuratedCuriosity`, skipping dismissed IDs, and shows `LearnUiState.CaughtUp` when the pages run out.
- Swiping left skips a card; swiping right adds its episode to the queue. Both are recorded in history (`LearnHistoryAction.DISMISS` / `QUEUE`).
- Ranking feedback records each card shown, every queue, and a skip only after the card stayed on screen long enough to be read.
- Card buttons mirror the swipes: the source row opens the show or details; the bottom row has Skip, Play/Pause and Add to queue. Play starts the episode with the `LEARN` entry point, or toggles it when it is already playing.
- Cards show their full question, explanation and show name; `LoreCardText` shrinks spacing first, then headings to a floor, and never clips.
- Card and page colours come from the cover. `LoreArtworkAccentStore`, owned by the ViewModel, prepares upcoming cards' colours ahead, and `LoreAccentTransition` blends the page colour with the swipe.
- Cards behind the active one move toward the next pose as it is swiped, so advancing the deck never snaps (`LoreDeckMotion`). Swipe cues (`LoreSwipeFeedback`) are decorative; the labelled buttons remain the accessible actions.
- Restoring a history entry puts the card back into the deck the next time Lore resumes.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/explore/
  ExploreScreen.kt, ExploreViewModel.kt   Explore route
  ExploreFeaturedRows.kt                  featured rows on browse and search
  LearnScreen.kt, LearnViewModel.kt, LearnPagination.kt
  LearnHistoryScreen.kt, LearnHistoryViewModel.kt
  LearnCuriosityCard.kt, LearnCuriosityHistoryStore.kt
  CuriosityCardStack.kt, SwipeableCardState.kt
  Lore*.kt                                card text, colours, deck motion, swipe cues,
                                          control feedback, player clearance
  components/   browse headers, cards, genre and tab selectors, search results and
                suggestions, topic results and transition, skeletons, empty states
  logic/        pure decisions: browse, tab pager, header scroll, shared recommendations,
                search landing examples, Lore deck shuffle
src/main/res/values/strings.xml
```

## Dependencies

- Project: `:core:designsystem`, `:core:catalog`, `:core:playback`, `:core:model`, `:core:network`, `:core:analytics`, `:core:ranking`, `:core:prefs`.
- Libraries: Compose Material 3, Activity Compose (predictive Back), Navigation, lifecycle, Coil, AndroidX Palette, kotlinx serialization. Tests add `:core:testing`, JUnit 5, Turbine, coroutines-test, Robolectric and Compose UI test.
- No feature → feature edges. Analytics go through `:core:analytics` (`AnalyticsHelper`), never PostHog directly. See [`ARCHITECTURE.md`](../../ARCHITECTURE.md#dependency-direction).

## Threading / lifecycle

- ViewModels are scoped to their navigation entries; repositories, playback, ranking feedback and prefs are application-scoped instances from app wiring.
- UI runs on the main thread; search, recommendations and Lore pages use suspend APIs. Reading the shared recommendations cache runs on `Dispatchers.IO`.
- Search queries are debounced in the ViewModel, so only the latest query in each mode is sent.
- Lore palette extraction runs on `Dispatchers.Default`. The palette cache and its jobs stay on the ViewModel scope: requests are deduplicated, obsolete ones cancelled, and a cancelled request never publishes a stale colour.
- Lore motion and colour are read in draw and graphics layers, so swipes don't recompose the deck each frame.
- Explore and Lore follow the window shell's pane on tablets and foldables; see [adaptive UI and state](../../ARCHITECTURE.md#adaptive-ui-and-state).

## Persistence & identity

- This module owns no storage files. Through `:core:prefs` it uses `BoxcastPrefs` keys `cached_recommendations` (shared with Home), `learn_curiosity_history` and `dismissed_curiosities`, and DataStore keys `explore_default_tab` and `discovery_picked_for_you`.
- Network DTOs map to feature UI models before entering UI state; episode and show IDs come from core modules.
- Pager tab IDs `0` (Top) and `1` (For You) and the route's `tab` values are stable.
- Compose test tags used by JVM tests: `explore_browse_pager`, `explore_feed_<tab>`, `explore_header_container`, `explore_search_switcher`, `explore_search_loading`, `explore_topic_toolbar`, `explore_topic_back`, `explore_topic_search`, `lore_active_card`.
- Page views (`TrackScreenView`): `explore` and `learn`, then `explore_top`, `explore_for_you`, `explore_search_shows`, `explore_search_episodes`, `explore_topic`, `learn_history` and `learn_history_clear_confirmation`. For You impressions and taps are separate events. See [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Testing notes

Hermetic JVM tests live in `feature/explore/src/test`, logic on plain JVM and screens on Robolectric with offline fixtures. `ExplorePhoneGoldenTest` and `ExploreTabletAuditCaptureTest` are local-only captures; see [`docs/TESTING.md`](../../docs/TESTING.md#screenshots).

| Area | Test classes |
| :--- | :--- |
| Browse tabs, pager and Picked for you | `ExploreTabPagerTest`, `ExploreTabPagerLogicTest`, `ExploreBrowseLogicTest` |
| Shared recommendations | `ExploreSharedRecommendationsLogicTest` |
| Header scroll | `ExploreChromeScrollLogicTest` |
| Topics | `ExploreTopicResultsTest` |
| Search | `ExploreSearchRequestTest`, `ExploreSearchLandingTest`, `ExploreSearchResultsTest`, `ConceptSearchIdleLogicTest` |
| Tablet grids and resizing | `ExploreGridResizeTest`, `ExploreTabletFeaturedTest` |
| Lore deck and history | `LearnDeckLogicTest`, `LearnPaginationTest`, `LearnCuriosityHistoryStoreTest`, `LearnHistoryActionTest` |
| Lore cards and motion | `LoreCardTextTest`, `LoreCardColorsTest`, `LoreDeckMotionTest`, `LoreMotionTest`, `LoreSwipeFeedbackTest` |
| Lore colours | `LoreArtworkAccentsTest` |
| Lore on tablets | `LoreTabletPaneSizeTest`, `TabletLoreLayoutTest` |

```bash
./gradlew :feature:explore:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs this module's JVM tests in the features-b shard and includes it in merged coverage; local captures stay out of it.
- `scripts/ci/check-feature-no-posthog.sh` guards direct PostHog use.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`:app` README](../../app/README.md)
- [`:core:prefs` README](../../core/prefs/README.md)
- [`:core:designsystem` README](../../core/designsystem/README.md)
- [`:feature:home` README](../home/README.md)
