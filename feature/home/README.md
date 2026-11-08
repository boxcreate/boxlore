# `:feature:home`

## Purpose

Owns the Home tab: the hero carousel, Your shows (pins, Daily and Offline mixes, the selected show), the Brief card, discovery sections, the NPS survey and review prompt, and the debug screen. It presents data from injected core dependencies and does not own settings, catalog engines, ranking models, playback services, download workers, preference storage or Room schemas.

## Public API

- `HomeRoute`: the Home destination on the app's `home` route. It calls `onInitialContentReady` once the first loaded feed has committed two frames.
- `HomeScreen`, `HomeScreenCallbacks`, `HomeFeedCallbacks`, `HomeUiState`, `HomePlaybackUi` and `HomeSheetUi`: the screen and its state, used by app shell tests.
- `HomeViewModel`, built by `HomeViewModelAssembler` (`create` / `factory`) from `HomeViewModelDeps`.
- `SmartHeroItem` and `HeroType`: hero items passed to the app's navigation.
- `HeroStylePreview`: draws any hero style scaled into given bounds, for the Settings previews.
- `ModeSwitchState`: an app-wide flag the shell reads to hide the mini player during a mode switch.
- `DebugScreen` and `DebugViewModel`: learner, playback, flag and NPS diagnostics on the app's `debug` route, opened by long-pressing Settings on Home.

## Your shows

- The cover picker lists subscriptions. Up to five pinned shows lead in pin order, then the ranked order. With more than one show, Mix comes first; with one, that show is selected.
- Long-pressing a cover opens a Pin / Unpin menu. A sixth pin is refused with a snackbar. Pins go through `UserPreferencesRepository.toggleHomePinnedPodcastId`.
- The order uses the shared Your shows score from `:core:ranking`, with a small hysteresis so tiny changes don't move covers (`HomeShowsOrderLogic`). Subscription and pin changes update the covers at once without scoring.
- The mix heading defaults to **Daily Mix**. It becomes a Daily / Offline dropdown only with at least two subscriptions and one unfinished downloaded episode; the choice persists.
- Offline lists unfinished downloads newest first, capped, and links to Library Downloads. The header play button queues the selected mix with `PlaybackEntryPoint.HOME_MIXTAPE`.
- Selecting a show lists its episodes by `HomeShowEpisodesLogic`: unfinished episodes among the newest 50, or for a show listened to in order, the episode in progress and the unfinished ones after the furthest one finished (trailers skipped), loaded from the listener's place by `SeriesEpisodesLoader`. With nothing left the latest episodes show as caught up; a failed load offers Try again (`SelectedShowLoadTransitions`). Daily mix's next in-order episode follows the same rules.
- The selected show's episode list has one writer, the selected-show collector (`observeSelectedPodcast` in `HomeViewModelSelected.kt`). Base Home updates carry the list over (`withSelectedShowFrom`) instead of replacing it.
- The selected list reloads in place when that show's feed refreshes (`HomeForegroundSyncLogic`).
- Serial next-up and catch-up read listening under every ID of a show and episode (`Podcast.listenerShowIds`, `HomeSerialLogic.idInList`, `HomeCatchUpLogic.underEveryId`); see [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).

### Ranking budget

- Home never ranks Your shows, Daily Mix or discovery per playback tick or per synced row. `HomeYourShowsRanker` decides when a ranking pass runs; `HomeRankingBudgetTest` guards it.
- A pass runs on the first assembly; once after synced catalog changes settle (`settledCatalogChanges`: new or serial-next episodes, imported or synced subscriptions, rate-limited during long syncs); and when Home starts after an episode was started or finished, or on an old snapshot. A restart-setting change rebuilds Daily Mix alone.
- Discovery rows re-rank only when their candidates change or on a ranking pass, never on raw history.

## Hero styles

The carousel shows Resume, then new episodes (newest first, grouped into a collection card when there are several), then Spotlight chart or trending picks (`HomeHeroLogic`). The style comes from `HeroCardStyle` in `:core:prefs` through `LocalHeroCardStyle`; style settings are described in [`feature/settings/README.md`](../settings/README.md).

- **Block** (`HeroCard`, `HeroGridCard`): a solid colour card beside a square cover.
- **Cover** (`CoverHeroCards.kt`), the default: the cover fills the card and fades into a shade behind the words.
- **Duotone** (`DuotoneHeroCards.kt`): the cover redrawn in two tones of one colour, with the words on it.
- **Tide** (`TideHeroCards.kt`): the whole cover above a wavy panel with a Play disc ringed by progress.
- **Halo** (`HaloHeroCards.kt`): a cookie-shaped cover inside a progress ring on a tinted card.
- **Marquee** (`Marquee*.kt`): one full-width card under a floating, frosted top bar, with the next cards' covers beside Play. Home's body scrolls up over a still cover and pauses where the sheet edge meets the bar (`MarqueeDetentConnection`). Cards advance on their own except while touched, scrolled away, under TalkBack or with animations off.

Shared rules:

- Every style shares ordering, playback, details and analytics callbacks. Colours follow the listener's choice (`LocalHeroCoverShade`).
- Every Home hero style ships its own loading skeleton in `HeroSkeletonCards.kt`; `HeroSkeletonCardsTest` fails for any `HeroCardStyle` without one.
- `homeHeroStyle` shows Cover instead of Marquee on tablets; the saved choice is unchanged. Below `ShortHeroHeight` (a hero above a tabletop fold), Halo shows as Cover.
- Tablets use `ResponsiveHeroCarousel`, which keeps a small end padding to avoid a material3 carousel crash at certain widths (`HeroCarouselEndTest`).

## Discovery and switchable sections

- Below Your shows: **Because you like**, **Picked for you**, time-of-day picks under a daypart greeting, and **Explore shows** with genre chips.
- The Brief, Picked for you, Because you like and the time-of-day picks can be switched off (`DiscoverySections` in `:core:prefs`). Hero, Your shows and Explore shows always stay.
- Each switchable section loads through `collectDiscoverySection`, keyed on the region, languages or daypart it uses. An off section clears with no request.
- Picked for you loads after trending (`loadRecommendations` in `HomeViewModelSections.kt`). A failed request keeps the picks Home already shows and saves nothing (`applyHomeRecommendations`).
- The Because you like seed rotates across morning, afternoon and night slots (`BecauseYouLikeRotationLogic`); a show picked in `ChangeRecommendationPodcastSheet` stays until reset.

## Brief

- The Brief card stays on Home until the listener finishes that day's edition or hides it (for today or always). Always hide is the same switch as the Brief section.
- The edition loads apart from discovery (`HomeViewModelBriefing.kt`) and never changes the app region. A failed same-edition refresh keeps the loaded card.
- Brief play uses `briefingShowPodcast` from `:core:designsystem` (the same identity as the Brief screen) and `PlaybackEntryPoint.BRIEFING`. Missing chapter or transcript links come from `:core:catalog` `BriefingLinks`.

## NPS survey and review prompt

- Home owns the NPS survey. `EngagementPromptCoordinator` (`:core:catalog`) decides it at a finish moment; `NpsSurveyController` runs the steps (score, one follow-up by score band, thanks) and sends survey events through `:core:analytics`.
- `NpsSurveyHost` waits while something plays, drops a survey whose finish moment has passed, and counts it as shown only on screen with Home resumed. Long-pressing Feedback opens it without using up the schedule.
- On tablets the survey is a centered dialog; it keeps its step and typed answer across window changes.
- The review prompt counts only episodes played to the end since install (`countFinishedSince`). `ReviewPromptRecorder` holds a decided prompt until its sheet is on screen, so a prompt nobody saw isn't used up.
- The Play Store page opens through `:core:designsystem` `startFirstAvailable`: the Play app, else the web page, never a crash.

## Startup and loading

- `HomeCacheRestoreGate` lets local recommendation and Because you like caches restore before the first snapshot or refresh. It waits only for local storage.
- Local content can reveal Home before trending finishes; discovery keeps its own loading state.
- On a cold start `rememberHomeRevealStage` hands the skeleton over one section per frame: hero, then Your shows, then discovery.
- Loaded sections draw under an opaque loading cover that fades out (`HomeLoadingReveal`); the shared shimmer runs only while loading sections are visible.
- Serial next-episode lookups share a two-request limit (`HomeSerialResolutionLimiter`).

## Adaptive layouts

Window gates and state retention follow [`ARCHITECTURE.md#adaptive-ui-and-state`](../../ARCHITECTURE.md#adaptive-ui-and-state). Home-specific tablet behaviour: `TabletHomeLeadingSections` pairs Your shows with the Brief; on wide windows the Your shows picker becomes a sidebar beside its content, scrolling past four rows (`TabletYourShowsModule.kt`); recommendations become one bounded rail.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/home/
  HomeScreen.kt                 # HomeRoute, HomeScreen, top bar and sheets
  HomeFeed*.kt                  # feed sections: Your shows, Brief, recommendations, editorial rows
  HomeViewModel*.kt             # ViewModel split by concern: LoadData, Selected, Serial,
                                #   Sections, BecauseYouLike, Briefing
  HomeViewModelAssembler.kt
  HomeUiModels.kt, HomeDataModels.kt
  HomeCacheRestoreGate.kt, HomeRevealStage.kt, HomeSerialResolutionLimiter.kt
  NpsSurvey*.kt, ReviewPromptRecorder.kt
  TabletHomeLeadingSections.kt
  Debug*.kt, AdaptiveLearnerDebugSection.kt
  components/                   # hero styles, Your shows, discovery, Brief card, sheets
  logic/                        # pure helpers, e.g. HomeYourShowsRanker, HomeShowsOrderLogic,
                                #   HomeUiAssemblyLogic, HomeHeroLogic, HomeSerialLogic,
                                #   HomeShowEpisodesLogic, SeriesEpisodesLoader
```

## Dependencies

- → `:core:model`, `:core:domain`, `:core:catalog` (which exposes `:core:prefs`), `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, `:core:ranking`, `:core:rss`.
- Libraries: Compose, Material 3, Navigation, lifecycle, Media3, Coil, Haze (Marquee's frosted bar), Kotlin serialization; tests add JUnit 5, Turbine, Robolectric, Roborazzi and Mockito.
- Feature modules never depend on other features, and never call PostHog directly; analytics go through `:core:analytics`.
- ViewModels and assemblers must not depend on `BoxLoreDatabase` directly.

## Threading / lifecycle

- `HomeViewModel` is scoped to the Home navigation entry; repositories, playback, downloads, prefs and ranking are application-scoped instances from app wiring.
- The Home pipeline, section loaders and recommendation parsing and scoring run on `Dispatchers.Default`; network and database work goes through injected suspend APIs.
- UI state is exposed as flows and collected by Compose on the main thread.
- `collectDiscoverySection` collects with `collectLatest`, so switching a section off or changing its key cancels the request in flight.

## Persistence & identity

- This module owns no storage files. Keys it reads and writes live in `:core:prefs`: `home_pinned_podcast_ids` (pins), `home_mix_mode` (Daily / Offline) and `home_hero_card_style` (hero style).
- Episode IDs, `rss:` IDs, ranking rows, download entries and media IDs are owned by core modules.
- Stable Compose test tags: `home_settings_button` (used by Maestro), `nps_survey_sheet`, `nps_survey_answer`, `nps_score_0`…`nps_score_10`.
- Analytics: Home page views (`home`, `review_prompt`) only move the screen trail; other sheets send `screen_viewed`. Events are listed in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Testing notes

Hermetic JVM tests live in `feature/home/src/test`, using fakes from [`:core:testing`](../../core/testing/README.md) and the Robolectric Compose host for UI.

| Area | Tests |
| :--- | :--- |
| Ranking budget and order | `HomeRankingBudgetTest`, `HomeShowsOrderLogicTest`, `HomeUiAssemblyLogicTest` |
| Your shows and mixes | `HomeConnectedRailTest`, `HomeEpisodeListScrollTest`, `HomeMixModeLogicTest`, `YourShowsSkeletonTest` |
| Selected show and serials | `HomeShowEpisodesLogicTest`, `SeriesEpisodesLoaderTest`, `SelectedShowLoadTransitionsTest`, `HomeRefreshPresentationLogicTest`, `HomeSelectedPodcastLogicTest`, `HomeSerialLogicTest`, `HomeCatchUpLogicTest` |
| Hero styles | `HomeHeroCardTest`, `BlockHeroColorsTest`, `CoverHeroCardsTest`, `DuotoneHeroCardsTest`, `TideHeroCardsTest`, `HaloHeroCardsTest`, `MarqueeHeroCardsTest`, `ShortHeroCardsTest` |
| Hero skeletons and carousel | `HeroSkeletonCardsTest`, `HomeHeroSkeletonTest`, `HeroCarouselEndTest`, `HomeHeroLogicTest` |
| Discovery and sections | `HomeDiscoverySectionsFeedTest`, `HomeDiscoveryFeedSpacingTest`, `HomeRecommendationsApplyTest`, `BecauseYouLikeRotationLogicTest` |
| Brief | `HomeViewModelBriefingTest`, `HomeBriefingRefreshTest`, `DailyBriefingCardTest` |
| NPS and review prompt | `NpsSurveyControllerTest`, `NpsSurveySheetAdaptiveTest`, `ReviewPromptRecorderTest`, `HomeListeningHistoryItemTest` |
| Startup and loading | `HomeCacheRestoreGateTest`, `HomeRevealStageTest`, `HomeLoadingRevealTest` |
| Adaptive layouts | `AdaptiveCardBoundsTest`, `TabletHomeLeadingSectionsTest`, `TabletHomeRefinementTest` |

```bash
./gradlew :feature:home:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Home JVM tests in the features shard and includes the module in merged coverage verification.
- `scripts/ci/check-feature-no-boxlore-database.sh` guards direct database use in Home ViewModels and assemblers.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`:app` README](../../app/README.md)
- [`:feature:settings` README](../settings/README.md)
- [`:feature:library` README](../library/README.md)
- [`:core:ranking` README](../../core/ranking/README.md)
- [`:core:prefs` README](../../core/prefs/README.md)
