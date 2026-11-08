# `:feature:briefing`

## Purpose

Owns the Brief page: loading a regional edition, the cover and headline, story paging with related episodes and sources, the edition and default-edition preferences, and Brief playback actions. Catalog retrieval, chapter fetching, queue and playback services, preference storage, the Brief's bundled artwork and strings, and route registration belong to core modules and `:app`.

## Public API

- `BriefingRoute`: the destination `:app` registers on `briefing?region={region}`. It builds `BriefingViewModel` through a `viewModel` factory from injected `PodcastRepository`, `PlaybackRepository`, `QueueManager` and `UserPreferencesRepository`.
- `BriefingScreen`: the stateless page, driven by `BriefingUiState` and callbacks for edition, play/pause, seek, episode, feedback and default edition.
- `BriefingViewModel`: exposes `uiState`, `selectedRegion` and `defaultEdition`, with `selectRegion`, `setDefaultEdition`, `togglePlayPause` and `seekTo`.
- `BriefingUiState`: the sealed page state (`Loading` carries the edition being loaded).
- Everything else (`BriefingLoader`, `BriefingIdentity`, `BriefingPlaybackLogic`, story and loading composables) is `internal`.

## Editions and loading

- Editions are `global`, `in`, `us` and `gb`, resolved by `BriefingEdition` in `:core:prefs` (it maps aliases such as `uk`). An explicit route region wins over the saved default, which wins over the app region.
- Changing the edition on the page never writes the default; choosing a default in Brief preferences saves it and opens that edition.
- `BriefingLoader` cancels the previous request on every load, so retrying the same edition starts fresh and a cancelled response never publishes. A choice made while preferences are still loading wins over initialization.
- While loading, `BriefingLoadingContent` shows the edition's bundled cover with placeholders in their final places.
- When an answer carries no chapters or transcript link, the ViewModel builds one with `BriefingLinks` from `:core:catalog`; the answer's own signed links are always preferred.

## Stories and playback

- `BriefingStoriesPager` gives every story card the height of the tallest, capped, so long stories scroll inside their text. In short windows `storyCardScrollsAsOne` makes the whole card scroll instead, keeping the heading and actions reachable.
- `resolveBriefingPlaybackAction` (`BriefingPlaybackLogic.kt`) decides whether a tap pauses, resumes, starts or seeks; choosing another story seeks instead of pausing.
- Playback hands the shared `briefingShowPodcast` show from `:core:designsystem` to `QueueManager.playEpisode`, so the player and Android Auto show the Brief under one title.
- Brief playback tags its play source bundle with `entry_point=briefing`, so `playback_*` events attribute Brief listening without changing `daily_briefing_action`.
- Layout across phone, tablet, foldable and resized windows follows the [adaptive UI and state contract](../../ARCHITECTURE.md#adaptive-ui-and-state).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/briefing/
  BriefingScreen.kt              # BriefingRoute, BriefingScreen, preferences and sources sheets
  BriefingViewModel.kt
  BriefingLoader.kt              # edition resolution, cancellable loads
  BriefingLoading.kt             # loading layout
  BriefingUiState.kt
  BriefingIdentity.kt            # episode ID and cover mapping
  BriefingPlaybackLogic.kt
  BriefingStoriesPager.kt, BriefingStoryCard.kt, BriefingStoryText.kt, BriefingStoryComponents.kt
  BriefingRelatedEpisodes.kt
```

## Dependencies

- → `:core:designsystem`, `:core:catalog`, `:core:playback`, `:core:model`, `:core:network`, `:core:analytics`, `:core:prefs`.
- Libraries: Compose, Material 3, Material icons, Navigation, Coil, lifecycle runtime and ViewModel Compose, Palette; tests add JUnit 5, coroutines-test and Robolectric.
- Feature modules never depend on other features, never call PostHog directly, and never construct playback or catalog graphs.

## Threading / lifecycle

- `BriefingViewModel` is scoped to the `briefing` back-stack entry; its loader runs in `viewModelScope`.
- Repositories, `PlaybackRepository` and `QueueManager` are application-scoped instances from app wiring; the ViewModel observes `playerState` and never owns a player.
- Compose runs on the main thread; network work goes through injected suspend APIs.

## Persistence & identity

- This module owns no storage files. The default edition is read and written through `UserPreferencesRepository` (`briefingDefaultEdition`).
- Brief episode IDs are `briefing_<region>_<date>` (`BriefingIdentity.episodeId`) and the show ID is `briefing_<region>`; saved sessions depend on both.
- Same-day regenerations change only the signed audio URL's `v=` value; `EpisodeMediaCacheKey` in `:core:model` puts it in the Media3 cache key.
- Page views: `daily_briefing` moves only the screen trail (its view is `daily_briefing_action`), while `briefing_preferences` and `briefing_sources` send `screen_viewed`. Events are listed in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).
- Stable test tags include `briefing_reading_frame` and `briefingBack`.

## Testing notes

Hermetic JVM tests live in `feature/briefing/src/test`, using suspend fakes and Robolectric with native graphics for Compose.

| Area | Tests |
| :--- | :--- |
| Loading, editions, cancellation | `BriefingViewModelLoadingTest` |
| Page, sheets, status bar, RTL and large text | `BriefingScreenTest` |
| Story cards and text | `BriefingStoryCardLayoutTest`, `BriefingStoryTextTest` |
| Playback decisions | `BriefingPlaybackLogicTest` |
| Identity and the shared Brief show | `BriefingIdentityTest`, `BriefingShowTest` |

```bash
./gradlew :feature:briefing:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests in the `features-a` shard and includes the module in merged coverage.
- `:core:testing` architecture guards check this README, page-view coverage and the dependency rules.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`core/playback/README.md`](../../core/playback/README.md)
- [`core/catalog/README.md`](../../core/catalog/README.md)
- [`app/README.md`](../../app/README.md)
