# `:feature:briefing`

## Purpose

Owns the Daily Briefing presentation flow: region-aware briefing content, story pager UI, transcript and chapter presentation, and playback actions delegated to injected core dependencies. It does not own catalog retrieval, queue persistence, playback services, or navigation registration.

## Public API

- `BriefingScreen` is the route-level composable used by `:app`.
- Section headers in briefing UI use `rememberSectionHeaderFontFamily()` from `:core:designsystem` so lettering roundness follows Appearance settings.
- `BriefingViewModel` exposes briefing state and actions using injected catalog, playback, and queue dependencies.
- `BriefingUiState` models screen state.
- `BriefingIdentity` contains pure identity helpers for tests and UI mapping.
- `BriefingStoriesPager` and `BriefingStoryComponents` provide extracted story-presentation pieces.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/briefing/
  BriefingIdentity.kt
  BriefingScreen.kt
  BriefingStoriesPager.kt
  BriefingStoryComponents.kt
  BriefingUiState.kt
  BriefingViewModel.kt
```

## Dependencies

- Project dependencies: `:core:designsystem`, `:core:catalog`, `:core:playback`, `:core:model`, `:core:network`, and `:core:analytics`.
- Libraries: Compose, Navigation, Coil, lifecycle ViewModel/runtime Compose, Palette, and Material icons.
- Reverse-edge rule: feature modules must not depend on other feature modules or construct playback/catalog graphs.

## Threading / lifecycle

- `BriefingViewModel` is scoped to the route or navigation owner that creates it.
- Catalog, playback, and queue objects are application-scoped instances supplied by app wiring.
- Compose UI runs on the main thread; repository calls use suspend APIs from injected dependencies.

## Persistence & identity

- This module owns no storage files or stable keys.
- Briefing episode and podcast identity comes from catalog/network models.
- Playback session identity is owned by `:core:playback`.

## Testing notes

- Unit tests live under `feature/briefing/src/test`.
- `BriefingIdentityTest` covers region aliases and briefing identity helpers.
- Manual smoke should verify briefing launch, story paging, and play/resume through the shared playback graph.

```bash
./gradlew :feature:briefing:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` compiles and runs briefing JVM tests with the project suite.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:playback` README](../../core/playback/README.md)
- [`:app` README](../../app/README.md)
