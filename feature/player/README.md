# `:feature:player`

## Purpose

Owns player presentation: mini player, full player sheet, queue screen UI, control deck, seekbar, chapters, transcript surfaces, and pure UI logic. Playback engines, queue persistence, Media3 services, and media ID policy live in `:core:playback`.

## Public API

- `v2.PlayerSheetScaffold` is composed by `:app` as the mini/full player overlay.
- `v2.FullPlayerV2`, `FullPlayerV2Content`, `FullPlayerV2Sheets`, `ControlDeck`, and `ControlDeckQuickActions` provide full-player presentation pieces.
- `QueueScreen`, `PlayerControls`, `ChaptersSheet`, and `TranscriptView` support player sub-surfaces.
- `v2.logic.*` contains JVM-testable layout, control, queue-label, transcript-dialog, mini-player, and seekbar logic.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/player/
  ChaptersSheet.kt
  PlayerControls.kt
  PlayerUtils.kt
  QueueScreen.kt
  SeekDurationIcon.kt
  TranscriptView.kt
  v2/
    ControlDeck.kt
    ControlDeckQuickActions.kt
    FullPlayerV2.kt
    FullPlayerV2Content.kt
    FullPlayerV2Sheets.kt
    MiniPlayerV2.kt
    PlayerHero.kt
    PlayerSeekbar.kt
    PlayerSheetScaffold.kt
    PlayerTheme.kt
    logic/
```

Main Kotlin files should remain below 1000 lines; extracted full-player content, sheets, and quick-action files keep the player sheet maintainable.

## Dependencies

- Project dependencies: `:core:model`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, and `:core:analytics`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Media3 ExoPlayer/Session/UI, Coil, Palette, smooth corner rect, reorderable, coroutines, JUnit, and Turbine.
- Reverse-edge rule: feature modules must not depend on other feature modules or construct a second `PlaybackRepository`.

## Threading / lifecycle

- The player overlay lives with Activity composition rather than a navigation destination.
- Playback and queue state come from application-scoped core dependencies supplied by app wiring.
- Artwork-tinted player colors are reseeding via Coil `ImageLoader.execute` (disk+memory cache) from the resolved episode→podcast artwork URL so process death does not leave the sheet on the default theme seed.
- UI runs on the main thread; playback operations delegate to core repositories and Media3 session APIs.

## Persistence & identity

- This module owns no storage files or stable keys.
- Player session flags, queue rows, and media ID prefixes are owned by `:core:playback` and related core modules.
- UI state should treat media IDs as opaque strings from playback APIs.

## Testing notes

- Unit tests live under `feature/player/src/test`.
- Existing coverage includes time formatting and v2 logic for controls, layout, seekbar, queue labels, queue podcast display, transcript dialogs, mini-player dismissal, and chapter art flow.
- Compose UI test tags for player controls should remain stable when added or expanded.

```bash
./gradlew :feature:player:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs player JVM tests with the project suite.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:playback` README](../../core/playback/README.md)
- [`:app` README](../../app/README.md)
