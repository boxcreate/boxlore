# `:feature:onboarding`

## Purpose

Owns first-run onboarding presentation: genre selection, search-based onboarding, import prompts, AI-guided onboarding, suggestion cards, and completion actions. It does not own preference storage, catalog engines, network client construction, or app navigation registration.

## Public API

- `OnboardingScreen` and `OnboardingViewModel` for the flow shell.
- Welcome step uses a cinematic podcast-cover marquee (`CinematicBackgroundGrid`): ~2.4s entrance with draw-phase-only chrome motion (`graphicsLayer`), static bottom scrim (no logo halo plate), modest cover glide that hands off into continuous drift (~700ms in — no pause), then logo/CTA cascade. Welcome chrome: wordmark + “Podcasts, done right.”; soft primary CTA with integrated AI cue; quieter tonal secondary actions.
- Onboarding hero titles use `rememberCondensedGoogleSansFamily()` from `:core:designsystem` so condensed Google Sans Flex follows Appearance lettering roundness; AI onboarding uses centralized Google Sans Flex weight tokens.
- `GenreOnboardingScreen`, `SearchOnboardingScreen`, `ImportOnboardingScreen`, `AiOnboardingScreen`, `AiChatOnboardingScreen`, and `AiSuggestionsScreen`. Search uses progressive Meili typeahead + hybrid `/search` (`searchPodcastsGrouped`) with **Matches** / **Also found** sections. When catalog hits prepend over local substring matches, the result list pins to the top so the best match stays on screen. Search genre chips use shared `PillFilterChip` from `:core:designsystem`. AI suggestions use the shared 11-country `RegionSegmentedSelector` (persisted via `UserPreferencesRepository.setRegion`, which also seeds recommended content languages). Curriculum / genre-synth / similar-shows requests use `discoveryLocaleForRegion` so `country` and `languages` stay canonical together; the proxy expands and `startsWith`-filters podcast languages.
- Suggestions (“Designed for you”) is a **single screen**: one compact headline, a single-row scrollable taste-lane chip strip (full titles, no ellipsis; per-lane selection badges), a one-line purpose + Select all toolbar, and a 2-column select grid. Descriptions open in a Material bottom sheet. Finish CTA stays pinned. Shared by genre, AI, search, and OPML flows.
- `AiSuggestionCards`, AI onboarding components, option icons, chat input, and chat message list logic.
- Pure helpers including `OnboardingGenreLimits`, `OnboardingSearchBackStep`, `OnboardingCurriculumLogic`, `OnboardingSuggestionsLanes`, `OnboardingSuggestionsPresentation`, and `OnboardingDiscoveryLocale`.
- Search / OPML → suggestions handoff: continue clears stale curriculum/charts, shows a loader while `isAiLoading` / `isSynthesizing` (via `OnboardingSuggestionsPresentation.isLoading`), subscribes seed picks, then filters those seed IDs out of similar-shows lanes so the grid only offers new shows. Seeds stay in `selectedPodcasts` for finish counting; similar shows are **not** auto-selected. Search CTA is “Continue with N”; suggestions finish uses “Start without adding” / “Add N & start”. Back to SEARCH / Welcome clears suggestion payloads but keeps picks and search query.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/onboarding/
  AiChatInputPanel.kt
  AiChatMessageListLogic.kt
  AiChatOnboardingScreen.kt
  AiOnboardingComponents.kt
  AiOnboardingOptionIcons.kt
  AiOnboardingScreen.kt
  AiSuggestionCards.kt
  AiSuggestionsScreen.kt
  GenreOnboardingScreen.kt
  ImportOnboardingScreen.kt
  OnboardingCurriculumLogic.kt
  OnboardingGenreLimits.kt
  OnboardingScreen.kt
  OnboardingSearchBackStep.kt
  OnboardingShowSearchCombine.kt
  ProgressiveSearchScrollLogic.kt
  OnboardingSuggestionsLanes.kt
  OnboardingSuggestionsPresentation.kt
  OnboardingUiModels.kt
  OnboardingViewModel.kt
  OnboardingViewModelAi.kt
  OnboardingViewModelGenre.kt
  OnboardingViewModelSearch.kt
  OnboardingViewModelSimilarShows.kt
  SearchOnboardingScreen.kt
```
## Dependencies

- Project dependencies: `:core:model`, `:core:catalog`, `:core:designsystem`, `:core:network`, and `:core:analytics`.
- Onboarding owns step / import / AI glossary events through `:core:analytics` (no PostHog direct).
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Coil, coroutines, Retrofit, Kotlin serialization, and Material icons.
- Reverse-edge rule: feature modules must not depend on other feature modules or read raw preference files.

## Threading / lifecycle

- `OnboardingViewModel` is scoped to the onboarding route or host owner.
- Preference completion writes go through `UserPreferencesRepository` and `BoxcastPrefs` supplied by app wiring.
- Catalog and network actions use injected dependencies and suspend APIs.
- UI runs on the main thread.

## Persistence & identity

- This module owns no storage files.
- Onboarding completion flags, selected genres, and recommendation/onboarding caches are owned by `:core:prefs`.
- Preference key names must remain stable in the prefs module.

## Testing notes

- Unit tests live under `feature/onboarding/src/test`.
- Existing coverage includes genre limits, search back-step behavior, curriculum logic, suggestions presentation (loading gate / seed filter / back-clear), progressive search pin-to-top when catalog hits prepend, AI option icons, and AI chat message list logic.
- ViewModel tests should use fakes for prefs, catalog, network, and analytics dependencies.

```bash
./gradlew :feature:onboarding:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs onboarding JVM tests with the project suite.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:prefs` README](../../core/prefs/README.md)
- [`:app` README](../../app/README.md)
