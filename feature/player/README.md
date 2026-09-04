# `:feature:player`

## Purpose

Owns player presentation: mini player, full player sheet, queue screen UI, control deck, seekbar, chapters, transcript surfaces, and pure UI logic. Playback engines, queue persistence, Media3 services, and media ID policy live in `:core:playback`.

## Public API

- `v2.PlayerSheetScaffold` is composed by `:app` as the mini/full player overlay.
- The collapsed v2 mini player follows Appearance → Navigation: Floating is a 64dp pill with 32dp corners; Classic restores the 72dp legacy shape with 26dp top and 14dp bottom corners. Both retain circular artwork, a primary play/pause control, and smaller circular seek controls.
- `v2.FullPlayerV2`, `FullPlayerV2Content`, `FullPlayerV2Sheets`, `ControlDeck`, and `ControlDeckQuickActions` provide full-player presentation pieces.
- The expanded-player transport group gives play/pause and both seek controls the same brief, coordinated width feedback when pressed or tapped.
- The expanded-player top bar keeps symmetric Cast/share circular actions at its edges; the bare collapse chevron sits below the centered **Now Playing** label. Cast discovery uses a boxlore-styled Material 3 device sheet rather than MediaRouter's legacy dialog and actively scans only while that sheet is open. Selecting a receiver keeps the sheet visible with a named connection state and Material 3 circular wavy loader until the chosen route is selected and the Cast session is active; framework session state closes the sheet even when MediaRouter's connection flag lags behind the TV. While casting, an artwork/Cast toggle swaps the fixed-size hero between artwork and receiver controls without pushing the rest of the sheet; the Cast view avoids repeating the episode title, uses the same artwork-derived color scheme as the surrounding player, and keeps its discrete TV-volume strip plus device/next/stop icon rail within that fixed artwork slot without overlap. Remote video swaps to artwork, exits local fullscreen, and hides local video-mode controls instead of leaving a frozen surface. The mini player replaces its podcast subtitle with a compact Cast-device indicator.
- Nested scroll in the expanded sheet stops at the content top; collapsing to the mini player takes a new downward swipe from that rest position.
- `QueueScreen`, `PlayerControls`, `ChaptersSheet`, and `TranscriptView` support player sub-surfaces. The queue sheet empty state stays “Queue is empty”; if Settings → Playback **Smart queue** is off, it adds a one-line note and a **Turn on** text action. When recommendation playback skipped same-show continuation, a solid Material 3 card (`SameShowContinuationBanner`) appears directly above the Up Next list, featuring an accordion preview of the upcoming episodes and offering a one-tap action to add the next available forward episodes from that show, or dismiss the banner.
- `v2.logic.*` contains JVM-testable layout, control, queue-label, transcript-dialog, mini-player, and seekbar logic.
- Player UI uses centralized Google Sans Flex weight tokens from `:core:designsystem`.

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
    FullPlayerCastHero.kt
    FullPlayerV2.kt
    FullPlayerCastControls.kt
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

- Project dependencies: `:core:model`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:prefs`, `:core:network`, `:core:designsystem`, and `:core:analytics`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Media3 ExoPlayer/Session/UI/Cast, AndroidX MediaRouter, Google Cast framework, Coil, Palette, smooth corner rect, reorderable, coroutines, JUnit, and Turbine.
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
- Existing coverage includes time formatting and v2 logic for controls, layout, nested-scroll collapse handoff, seekbar, queue labels, queue podcast display, transcript dialogs, mini-player dismissal, and chapter art flow.
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
