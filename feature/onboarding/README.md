# `:feature:onboarding`

## Purpose

Owns first-run onboarding presentation: genre selection, search-based onboarding, import prompts, AI-guided onboarding, suggestion cards, and completion actions. It does not own preference storage, catalog engines, network client construction, or app navigation registration.

## Public API

- Show covers pass their available artwork alternatives to the shared loader without changing search, suggestions or onboarding decisions.

- Suggestion cards and selectable rows use steady Material feedback. Custom compact actions and genre chips opt into the shared restrained control response, clipped to their actual shapes. Primary action backgrounds contract with their content inside the shared press layer. Selection/subscription callbacks are unchanged.

- `OnboardingScreen` and `OnboardingViewModel` for the flow shell.
- Welcome uses a solid rounded cover panel with 12 curated bundled covers in two opposing, continuously tiled rows. The opening holds a brisk glide for 600ms, then slows over 1.8s directly into a continuous 16dp/s loop. The invitation “You bring the curiosity.” enters before a fixed “We’ve got the” prefix with rotating mysteries, laughs, headlines, big ideas, stories, discoveries and lore. The word and its matching Rounded icon use the theme's primary accent; masked word reveals and a short icon settle share the cover clock, with readable holds and a longer pause on lore. The subtitle reserves the longest localized word's width and height; large text stacks the prefix above the slot rather than shrinking it. TalkBack reads one stable “We’ve got the lore.” description. Headline type size adapts to the window width. The wordmark and all actions remain available from the first frame. The compact, single-line “Find shows for me” action includes an AI sparkle and opens the existing AI-guided discovery flow; connected “Search podcasts” / “Import library” actions include their search/download icons. These actions and “Skip setup” retain their existing callbacks. The normal phone layout fits without scrolling or overscroll stretch; short windows and larger system text can scroll only when content needs more room.
- Onboarding hero titles use `rememberCondensedGoogleSansFamily()` from `:core:designsystem` so condensed Google Sans Flex follows Appearance lettering roundness; AI onboarding uses centralized Google Sans Flex weight tokens.
- `GenreOnboardingScreen`, `SearchOnboardingScreen`, `ImportOnboardingScreen`, `AiOnboardingScreen`, `AiChatOnboardingScreen`, and `AiSuggestionsScreen`. `ImportOnboardingScreen` presents library restoration options (OPML/JSON file import and Sync from Cloud account) with inbound download iconography (`Icons.Rounded.Download`). Search uses progressive Meili typeahead + hybrid `/search` (`searchPodcastsGrouped`) with **Matches** / **Also found** sections. When catalog hits prepend over local substring matches, the result list pins to the top via shared `ProgressiveSearchScrollLogic` so the best match stays on screen. Search genre chips use shared `PillFilterChip` from `:core:designsystem`. Search and suggestion grids safely deduplicate and disambiguate keys via `LazyListKeyPolicy` to protect Lazy layouts from collision crashes. AI suggestions use the shared 11-country `RegionSegmentedSelector` (persisted via `UserPreferencesRepository.setRegion`, which also seeds recommended content languages). Curriculum / genre-synth / similar-shows requests use `discoveryLocaleForRegion` so `country` and `languages` stay canonical together; the proxy expands and `startsWith`-filters podcast languages.
- Suggestions (“Designed for you”) is a **single screen**: one compact headline, a single-row scrollable taste-lane chip strip (full titles, no ellipsis; per-lane selection badges), a one-line purpose + Select all toolbar, and a 2-column select grid. Descriptions open in a Material bottom sheet. Finish CTA stays pinned. Shared by genre, AI, search, and OPML flows.
- `AiSuggestionCards`, AI onboarding components, option icons, chat input, and chat message list logic.
- Pure helpers including `OnboardingGenreLimits`, `OnboardingSearchBackStep`, `OnboardingCurriculumLogic`, `OnboardingSuggestionsLanes`, `OnboardingSuggestionsPresentation`, and `OnboardingDiscoveryLocale`.
- Search / OPML → suggestions handoff: continue clears stale curriculum/charts, shows a loader while `isAiLoading` / `isSynthesizing` (via `OnboardingSuggestionsPresentation.isLoading`), subscribes seed picks, then filters those seed IDs out of similar-shows lanes so the grid only offers new shows. Each PI subscription immediately requests the application-scoped publisher-feed ingest; it does not wait for Home or the first periodic foreground pass. Seeds stay in `selectedPodcasts` for finish counting; similar shows are **not** auto-selected. Search CTA is “Continue with N”; suggestions finish uses “Start without adding” / “Add N & start”. Back to SEARCH / Welcome clears suggestion payloads but keeps picks and search query.

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
  OnboardingSuggestionsLanes.kt
  OnboardingSuggestionsPresentation.kt
  OnboardingUiModels.kt
  OnboardingViewModel.kt
  OnboardingViewModelAi.kt
  OnboardingViewModelGenre.kt
  OnboardingViewModelSearch.kt
  OnboardingViewModelSimilarShows.kt
  SearchOnboardingScreen.kt
  WelcomeCoverReel.kt
  WelcomeMotionClock.kt
  WelcomeReelMotion.kt
  WelcomeRotatingReply.kt
  WelcomeScreen.kt
  WelcomeWordLoopMotion.kt
```
## Dependencies

- Project dependencies: `:core:model`, `:core:catalog`, `:core:designsystem`, `:core:network`, and `:core:analytics`.
- Onboarding owns step / import / AI glossary events through `:core:analytics` (no PostHog direct).
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Coil, coroutines, Retrofit, Kotlin serialization, and Material icons.
- Reverse-edge rule: feature modules must not depend on other feature modules or read raw preference files.

## Threading / lifecycle

- `OnboardingViewModel` is scoped to the onboarding route or host owner.
- Preference completion writes go through `UserPreferencesRepository` and `BoxcastPrefs` supplied by app wiring.
- Catalog and network actions use injected dependencies and suspend APIs; first publisher-feed persist is handed to application-scoped `SubscriptionForegroundSync` so navigation cannot cancel it.
- UI runs on the main thread. Welcome motion shares one lifecycle-aware frame clock, pauses below STARTED, and resumes without replaying the entrance. The system animation-duration scale is honored; disabling animations reveals the full copy, shows static lore and freezes the reel. Travel and reveal values are read only by drawing layers, avoiding per-frame composition; the rotating subtitle composes only when its selected word changes. Bundled covers require no network request.

## Persistence & identity

- This module owns no storage files.
- Onboarding completion flags, selected genres, and recommendation/onboarding caches are owned by `:core:prefs`.
- Preference key names must remain stable in the prefs module.

## Testing notes

- Unit tests live under `feature/onboarding/src/test`.
- Existing coverage includes genre limits, search back-step behavior, curriculum logic, suggestions presentation (loading gate / seed filter / back-clear), AI option icons, and AI chat message list logic. Progressive search pin-to-top is covered in `:core:designsystem` (`ProgressiveSearchScrollLogicTest`).
- `WelcomeReelMotionTest` covers the entrance-to-loop handoff, reverse direction, repeated seams, long-running clocks, phase offsets and invalid inputs. `WelcomeWordLoopMotionTest` checks term order, the longer lore hold, entrance/exit timing, bounded poses, long-running clocks and the static reduced-motion state. Manually verify welcome on first launch: observe a full subtitle loop without layout shifts; discovery, search, import and skip route to their existing destinations. Backgrounding and returning resumes the loop, and disabled system animations leave the complete lore reply visible. Check narrow windows, RTL and larger text for scroll access to every action.
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
