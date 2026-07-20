# `:feature:info`

## Purpose

Owns podcast and episode detail presentation: subscribe actions, RSS refresh actions, related and similar content, cross-promotion cards, offline/progress display, and detail-screen layout. It does not own catalog persistence, RSS parsing, download cache behavior, playback services, or app navigation registration.

## Public API

- `PodcastInfoScreen` and `PodcastInfoViewModel`.
- `EpisodeInfoScreen` and `EpisodeInfoViewModel`.
- `InfoViewModelAssembler` for podcast and episode ViewModel factories.
- `InfoListeningProgressItem` and supporting components/sections for detail UI.
- Logic helpers under `logic/` and component-level formatters used by tests.
- `EpisodeInfoSeekLogic` builds the progress-save payload when seeking an episode that is not the current player item.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/info/
  EpisodeInfoScreen.kt
  EpisodeInfoViewModel.kt
  InfoListeningProgressItem.kt
  InfoViewModelAssembler.kt
  PodcastInfoScreen.kt
  PodcastInfoViewModel.kt
  components/
  logic/
    EpisodeInfoSeekLogic.kt
    …
  sections/
```

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, and `:core:rss`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Coil, Palette, smooth corner rect, coroutines, Kotlin serialization, Turbine, and Compose Material.
- Reverse-edge rule: feature modules must not depend on other feature modules. ViewModels and assemblers must use ports rather than direct `BoxLoreDatabase` access.

## Threading / lifecycle

- ViewModels are scoped by app navigation.
- Catalog, local catalog, offline lookup, RSS, download, playback, and analytics dependencies are supplied by app wiring.
- UI runs on the main thread; refresh, subscribe, lookup, and related-content work use suspend APIs.

## Persistence & identity

- This module owns no storage files or stable keys.
- Podcast, episode, RSS, download, and listening-progress identities come from core modules.
- App navigation owns route patterns and deep links.

## Testing notes

- Unit tests live under `feature/info/src/test`.
- Existing coverage includes assembler behavior, catalog port behavior and errors, offline merge logic, listening-progress mapping, duration formatting, metadata chip logic, feed grouping, toolbar logic, HTML stripping, and podcast info ViewModel logic.
- Catalog HTTP paths are covered in `:core:catalog` tests.

```bash
./gradlew :feature:info:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Info JVM tests with the project suite.
- `scripts/ci/check-feature-no-boxlore-database.sh` guards direct database usage in feature ViewModels and assemblers.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../../core/catalog/README.md)
- [`:app` README](../../app/README.md)
