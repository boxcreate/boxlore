# `:feature:onboarding`

## Purpose

Owns first-run onboarding presentation: genre selection, search-based onboarding, import prompts, AI-guided onboarding, suggestion cards, and completion actions. It does not own preference storage, catalog engines, network client construction, or app navigation registration.

## Public API

- `OnboardingScreen` and `OnboardingViewModel` for the flow shell.
- Onboarding hero titles use `rememberCondensedGoogleSansFamily()` from `:core:designsystem` so condensed Google Sans Flex follows Appearance lettering roundness.
- `GenreOnboardingScreen`, `SearchOnboardingScreen`, `ImportOnboardingScreen`, `AiOnboardingScreen`, `AiChatOnboardingScreen`, and `AiSuggestionsScreen`.
- `AiSuggestionCards`, AI onboarding components, option icons, chat input, and chat message list logic.
- Pure helpers including `OnboardingGenreLimits`, `OnboardingSearchBackStep`, and `OnboardingCurriculumLogic`.

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
  OnboardingUiModels.kt
  OnboardingViewModel.kt
  OnboardingViewModelAi.kt
  OnboardingViewModelGenre.kt
  OnboardingViewModelSearch.kt
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
- Existing coverage includes genre limits, search back-step behavior, curriculum logic, AI option icons, and AI chat message list logic.
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
