# `:feature:onboarding`

## Purpose

Owns first-run onboarding: the Welcome screen, Echo (the AI-guided conversation and its show deck), the manual topic pickers, show search, the similar-shows choice after search or import, and the shared suggestions screen that finishes onboarding. It does not own preference storage, catalog or search engines, network clients, the Echo character, library import, or route registration.

## Public API

- `OnboardingScreen`: the whole flow, shown by `:app` on the `onboarding` route. It switches on `OnboardingUiState.currentStep` and calls `onComplete`, `onBack` and `onImportClick`.
- `OnboardingViewModel`: an `AndroidViewModel` built by `:app` from `PodcastRepository`, `SubscriptionRepository` and `UserPreferencesRepository`; it exposes `uiState`.
- `markOnboardingCompletedSilent` and `generateRecommendationsFromOpml`: extensions the app calls to finish without the flow, or to hand imported shows to the similar-shows choice.
- `OnboardingUiState`, `OnboardingStep` (`WELCOME`, `GENRES`, `SUB_GENRES`, `ACTIVITY_PICKER`, `LENGTH_PICKER`, `SEARCH`, `AI_ONBOARDING`, `AI_SUGGESTIONS`) and `AiLoadingStage`: the flow's state.
- `ONBOARDING_GENRES` / `GenreItem` and `SEARCH_CATEGORIES` / `SearchCategoryItem`: the picker and search chip lists.
- Everything else, including every step screen, is `internal`.

## Flow

- **Welcome** (`WelcomeScreen`): a cover reel, a rotating reply line and Echo peeking over the **Find shows for me** button; the actions start Echo, search, library import (handled by the app) or skip.
- **Echo** (`AiOnboardingScreen` → `EchoOnboardingScreen`): one question per turn with fixed areas for the top bar, Echo, the question, its answers and a buttons-only footer. The screen never scrolls; `EchoLayout` picks a height tier from the space available.
- The opening turn is app-owned and sends no request: the eight `EchoInitialTopic` answers plus **Something else**, a text box for free answers.
- `EchoScreenModel` decides the screen kind (`EchoScreenKind`), footer actions, progress and Echo's pose as pure functions.
- Finishing is always the listener's choice. `GuidedOnboardingLogic` caps a conversation at seven answers (`MAX_ANSWERS`) and offers the build nudge after four.
- **Show deck**: after two answers the app asks for two to four taste-test cards (`EchoDeckLogic`). Reactions (`like`, `dislike`, `listen`, `unknown`) travel on later requests as `probeReactions`, at most twelve. **I already listen** also adds the show to the listener's picks. One deck per conversation, with one retry.
- **Manual topics**: `GenrePickerScreen`, `SubGenrePickerScreen`, `ActivityPickerScreen` and `LengthPickerScreen`, limited by `OnboardingGenreLimits`.
- **Search** (`OnboardingSearchScreen`): progressive typeahead through `searchPodcastsGrouped`, kept at the top by `ProgressiveSearchScrollLogic`; picks are followed at the handoff.
- **Similar-shows choice** (`SimilarShowsChoice`, `SimilarShowsChoiceContent`): after search or import the listener chooses **See similar shows** or **Go to Home** before any request is made. Import leads with Go to Home, search with See similar shows; a late answer after skipping is dropped (`acceptsResult`).
- **Suggestions** (`AiSuggestionsScreen`, shared by every path): lanes of `SuggestionShowCard`s. Nothing starts selected except Echo's **Your picks** lane; `OnboardingSuggestionsPresentation` decides the origin (`SuggestionsOrigin`), loading state and what **Add N shows** counts.
- Back restores the previous question with its answer still picked, cancels requests in flight, and from loaded Echo suggestions shows `EchoReadyStage`. **Change my answers** drops results and their picks (`OnboardingAiStart.withoutResults`).

## Client contracts

- Echo calls `onboarding/next-turn`, `onboarding/probe-deck` and `onboarding/synthesize`; the topic and seed paths call `onboarding/curriculum`, `onboarding/genre-synth` and `onboarding/similar-shows` (routes in `:core:network`).
- Next-turn requests send the features `finish_hint` (`GuidedOnboardingLogic.FINISH_HINT_FEATURE`) and `show_probe` (`EchoDeckLogic.FEATURE`) with `country` and `languages`.
- Suggestion requests send `recommendationFormat = "discovery_v2"`; lanes keep server order. `discoveryLocaleForRegion` keeps `country` and `languages` canonical together.
- A failed turn whose API error code is in `ApiErrorCodes.AI_UNAVAILABLE` hands over to the topic pickers (`EchoRequestPolicy.picksTopicsAfter`), since Try again cannot succeed. Other failures offer Try again.
- Synthesis and genre builds fall back to charts on failure, so no path blocks the listener.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/onboarding/
  OnboardingScreen.kt, OnboardingUiModels.kt
  OnboardingViewModel.kt               # state, jobs, subscribe-and-ingest
  OnboardingViewModel{Ai,Deck,Genre,Search,SimilarShows}.kt
  Welcome*.kt                          # screen, cover reel, reply loop, entry timing, motion clock,
                                       #   Echo's eye script (WelcomeEchoIdle.kt)
  Echo{OnboardingScreen,TopBar,Conversation,Answers,FooterBar}.kt
  EchoScreenModel.kt, EchoLayoutMetrics.kt, EchoMotion.kt, EchoPlatformMotion.kt, EchoMoments.kt
  EchoInitialTopic.kt, EchoChoiceIcons.kt, EchoLanguageHint.kt, EchoPrivacyNotice.kt
  EchoRequestLifecycle.kt              # request jobs, question snapshots, EchoRequestPolicy
  EchoWaitMessages.kt                  # slow-request lines and countdowns
  EchoShowDeck.kt, EchoDeckViews.kt, EchoDeckPhysics.kt, EchoShowPreview.kt  # deck state, views, clips
  EchoPrism{Card,Layout,Foil,Support}.kt                                     # the deck's show card
  GuidedOnboardingLogic.kt, OnboardingAiStart.kt, OnboardingFallback.kt
  GenreOnboardingScreen.kt, OnboardingGenreLimits.kt, OnboardingCurriculumLogic.kt
  SearchOnboardingScreen.kt, OnboardingSearchBackStep.kt, OnboardingShowSearchCombine.kt
  SimilarShowsChoice.kt, SimilarShowsChoiceView.kt
  AiSuggestionsScreen.kt, AiSuggestionCards.kt, AiSuggestionIcons.kt
  OnboardingSuggestionsLanes.kt, OnboardingSuggestionsPresentation.kt, OnboardingDiscoveryLocale.kt
  AiOnboardingScreen.kt                # delegates to EchoOnboardingScreen
  AiChatOnboardingScreen.kt, AiChatInputPanel.kt, AiChatMessageListLogic.kt,
  AiOnboardingComponents.kt, AiOnboardingOptionIcons.kt, ImportOnboardingScreen.kt  # not routed by OnboardingScreen
src/main/res/values/                   # echo_onboarding.xml, onboarding_suggestions.xml, welcome.xml
```

## Dependencies

- → `:core:model`, `:core:catalog` (which exposes `:core:prefs`), `:core:designsystem`, `:core:network`, `:core:analytics`.
- Libraries: Compose, Material 3, graphics-shapes, Navigation, lifecycle, Coil, coroutines, Retrofit, Kotlin serialization, Material icons, and Media3 ExoPlayer for deck clips only; listening playback stays with `:core:playback`.
- Echo itself (`EchoCharacter`, its faces, eyes and assets) lives in `:core:designsystem`; this module keeps only its moments and eye scripts. See [`core/designsystem/README.md`](../../core/designsystem/README.md).
- Feature modules never depend on other features or call PostHog directly; analytics go through `:core:analytics`.

## Threading / lifecycle

- `OnboardingViewModel` is constructed directly in the app root's composition (`remember`), not through a `ViewModelStore`, so it outlives the onboarding route. Its `onCleared` cancels Echo and deck requests, releases the clip pool and records abandonment.
- Echo turn, synthesis, deck and similar-shows jobs cancel on Back, Search, the topic fallback, restart and skip. Cancelled work never replaces a restored question or shows an error; cancellation is rethrown, not reported.
- Genre synthesis runs `async` on `Dispatchers.IO` and catches failure inside the child (`orFallbackOnFailure`), because an exception escaping an `async` child fails its parent.
- Subscribing a show asks the application-scoped `subscriptionForegroundSync` for a catalog ingest, so leaving onboarding cannot cancel it.
- `EchoClipPool` holds one Media3 player per deck clip, prefetched when the deck arrives and released with the deck or the ViewModel.
- Welcome motion runs on one lifecycle-aware frame clock (`WelcomeMotionClock`) that pauses below `STARTED`. Every animation follows the system animation scale and shows a still pose when animations are off.
- Window sizes, folds and resizing follow the [adaptive UI and state contract](../../ARCHITECTURE.md#adaptive-ui-and-state).

## Persistence & identity

- This module owns no storage files. It writes `onboarding_completed` and `user_genres` through `BoxcastPrefs`, and the region through `UserPreferencesRepository.setRegion`; both belong to `:core:prefs`.
- Deck reaction wire values (`like`, `dislike`, `listen`, `unknown`) and request feature names (`finish_hint`, `show_probe`, `discovery_v2`) are client contracts with the API.
- Page views go through `TrackScreenView`: each step as `onboarding_<step>`, plus `onboarding_echo_about`, `onboarding_echo_finish_confirmation` and `onboarding_podcast_detail`. Events are listed in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Testing notes

Hermetic JVM tests live in `feature/onboarding/src/test`, using fakes from [`:core:testing`](../../core/testing/README.md) and Robolectric for Compose. There is no ViewModel harness; ViewModel tests should use fakes for prefs, catalog, network and analytics.

| Area | Tests |
| :--- | :--- |
| Echo turns and footer | `GuidedOnboardingLogicTest`, `EchoScreenModelTest`, `EchoInitialTopicTest`, `EchoRequestLifecycleTest`, `OnboardingAiStartTest` |
| Echo layout and screen | `EchoLayoutMetricsTest`, `EchoConversationFitTest`, `EchoOnboardingScreenTest` |
| Echo copy and motion | `EchoChoiceIconsTest`, `EchoLanguageHintTest`, `EchoWaitTimingTest`, `EchoPlatformMotionTest`, `EchoMomentsTest` |
| Show deck | `EchoShowDeckTest`, `EchoPrismLayoutTest` |
| Welcome | `WelcomeReelMotionTest`, `WelcomeEntryTimingTest`, `WelcomeWordLoopMotionTest`, `WelcomeMotionClockTest`, `WelcomeEchoIdleTest`, `WelcomeEchoPeekTest`, `WelcomeEchoFitTest` |
| Topics and search | `OnboardingGenreLimitsTest`, `OnboardingCurriculumLogicTest`, `OnboardingSearchBackStepTest`, `OnboardingShowSearchCombineTest`, `OnboardingDiscoveryLocaleTest` |
| Similar-shows choice | `SimilarShowsChoiceTest`, `SimilarShowsChoiceScreenTest` |
| Suggestions | `OnboardingSuggestionsPresentationTest`, `OnboardingSuggestionsLanesTest`, `SuggestionsScreenTest`, `AiSuggestionIconsTest` |
| Fallbacks | `OnboardingFallbackTest` |
| Unrouted chat screen | `AiChatMessageListLogicTest`, `AiOnboardingOptionIconsTest` |
| Phone goldens (opt-in) | `EchoPhoneGoldenTest`; see [`docs/TESTING.md`](../../docs/TESTING.md#screenshots) |

```bash
./gradlew :feature:onboarding:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests in the `features-b` shard and includes the module in merged coverage; `EchoPhoneGoldenTest` stays local.
- `:core:testing` architecture guards check this README, page-view coverage and the dependency rules.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`core/designsystem/README.md`](../../core/designsystem/README.md)
- [`core/prefs/README.md`](../../core/prefs/README.md)
- [`app/README.md`](../../app/README.md)
