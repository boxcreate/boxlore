# `:feature:player`

## Purpose

Owns player presentation: mini player, full player sheet, queue screen UI, control deck, seekbar, chapters, transcript surfaces, and pure UI logic. Playback engines, queue persistence, Media3 services, and media ID policy live in `:core:playback`.

## Public API

- `v2.PlayerSheetScaffold` is composed by `:app` as the mini/full player overlay.
- The collapsed v2 mini player follows Appearance → Navigation: Floating is a 64dp capsule with 32dp corners; Classic keeps the 72dp shape with 26dp top and 14dp bottom corners. Both clips use the selected navigation's corner metrics. The solid artwork-tinted surface holds 48dp cookie-shaped artwork, title/show metadata, and a 48dp circular play/pause control inside one bar. `PlayerSheetLayout.miniPlayerSeekButtonsEnabled` defaults off; opting in adds 40dp back/forward controls around play/pause using global seek durations. The title and show/Cast label each get their own line. A rounded progress line beneath them smooths position updates and gently waves while playing; its quiet remaining track has an open gap after the played segment. Pause/loading flatten the wave, and compactness, expansion, and stopped lifecycle suspend motion.
- Floating navigation can supply a shared `PlayerSheetLayout.compactProgress` and `compactTargetY` to morph that same mini-player surface and artwork into a 52dp scalloped cookie in the trailing navigation action slot. The nine shallow lobes grow during descent; `MiniPlayerShape` supplies matching surface, artwork, and progress contours. The app shell owns browsing scroll detection and eligibility; Classic ignores compactness. The compact player opens the full player on tap or swipe up and shows progress, loading, and Cast status without a pause badge or duplicate transport controls; its spoken state still reports Playing/Paused. The optional seek preference only affects the regular bar. Metadata, controls, and paused swipe-dismiss confirmation lose interaction/focus during compactness. Its measured hit bounds follow the visible surface, including RTL placement.
- Compact playback progress uses a 3.5dp scalloped ring outside the 40dp artwork, with an open gap, a solid neutral track, and a primary played arc with a rounded end. The compact backdrop and shadow fade away to expose that gap. The track stays visible while paused, loading, or awaiting duration; playback milliseconds take precedence over the episode's seconds fallback. A small central loading indicator leaves the progress ring visible. Spoken progress uses the same duration resolution.
- `MiniPlayerMorph` shares compactness, sheet expansion, cookie depth, and a drawing-only rotation state. While playback is active and the player is fully compact, `MiniPlayerMotion` moves the surface, artwork clip, and progress contour together at up to 30fps, completing one lobe cycle every 2.4 seconds. The image stays upright and the progress origin stays at twelve o'clock. Pause/buffering, sheet expansion, and stopped lifecycle freeze the phase; resume continues it. The motion does not change layout or draggable anchors.
- Mini/full expansion uses the existing interruptible, critically damped sheet spring. `PlayerSheetRevealLogic` derives complementary eased fades, a small full-content scale/rise, and the reverse collapse from the same sheet fraction. Full-player content is measured at its final width and height, aligned to the top behind the growing clip, preventing text/control reflow. Compact scalloping disappears before full content fades in; mini transport, dismissal, and child focus are disabled during expansion.
- `PlayerSheetActions.onSheetInteractionChanged` reports full-player visibility, active sheet drag/animation, and fullscreen video so the shell can freeze browsing-driven morphs; disposal reports false. The shared browse animation first eases the bar into a compact shape above navigation, then eases it into its action slot; both phases have zero slope at their endpoints, and Lore finishes joining the navbar before descent. Floating feature controls follow that same eased descent; reversing lifts the circle before widening the bar so intermediate bounds cannot cover navigation. Browse compactness changes only visual bounds, preserving the fixed mini/full draggable anchors, predictive Back, and existing full-player nested-scroll handoff. Playback and queue ownership are unchanged.
- `v2.FullPlayerV2`, `FullPlayerV2Content`, `FullPlayerV2Sheets`, `ControlDeck`, and `ControlDeckQuickActions` provide full-player presentation pieces. `SecondaryAvailabilityState` enables chapters and transcript quick controls seamlessly when offline by checking local downloaded files, active playback state, and timestamped show notes description chapters. Tapping download on an already-downloaded episode prompts with `RemoveDownloadConfirmationDialog` before removing local audio.
- The expanded-player transport group gives play/pause and both seek controls the same brief, coordinated width feedback when pressed or tapped.
- The expanded-player top bar keeps symmetric Cast/share circular actions at its edges, adapting icon tinting to dark/light theme surfaces (`colorScheme.onSurface`) and highlighting active Cast sessions (`colorScheme.primary`); the bare collapse chevron sits below the centered **Now Playing** label. Cast discovery uses a boxlore-styled Material 3 device sheet rather than MediaRouter's legacy dialog and actively scans only while that sheet is open. Selecting a receiver keeps the sheet visible with a named connection state and Material 3 circular wavy loader until the chosen route is selected and the Cast session is active; framework session state closes the sheet even when MediaRouter's connection flag lags behind the TV. While casting, an artwork/Cast toggle swaps the fixed-size hero between artwork and receiver controls without pushing the rest of the sheet; the Cast view avoids repeating the episode title, uses the same artwork-derived color scheme as the surrounding player, and keeps its discrete TV-volume strip plus device/next/stop icon rail within that fixed artwork slot without overlap. Remote video swaps to artwork, exits local fullscreen, and hides local video-mode controls instead of leaving a frozen surface. The mini player replaces its podcast subtitle with a compact Cast-device indicator.
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
    InlineTranscriptHero.kt
    MiniPlayerV2.kt
    MiniPlayerControls.kt
    MiniPlayerProgress.kt
    MiniPlayerCompactStatus.kt
    MiniPlayerShape.kt
    MiniPlayerMotion.kt
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
- Adaptive-player JVM coverage verifies normal/circle/full endpoints, coordinated intermediate bounds, RTL, constrained widths, invalid progress, Classic/fullscreen preservation, stable logical anchors, and interaction gating.
- Player descent and floating feature-control placement use designsystem's same docking phase. A regression test checks that controls maintain clearance above the player and navigation throughout the morph at multiple densities.
- Shape coverage checks nine-lobe bounds and matching artwork/progress contours. Seekbar regression coverage fits the thumb to temporarily narrow or zero-width tracks during player expansion, avoiding an empty clamp range reported in device fatal logs.
- Regular and compact artwork share the same nine-lobe cookie silhouette at 48dp and 40dp, maintaining its normalized contour throughout contraction; the outer Floating/Classic bar shapes still follow navigation metrics.
- Progress geometry tests verify fixed wave endpoints, bounded amplitude on short tracks, phase movement, flat paused geometry, and safe invalid input. The regular progress line smooths position updates, separates the remaining track with a small gap, and animates only during active, visible playback.
- Reveal coverage verifies continuous forward/reverse handoff, complementary opacity, eased boundaries, scale endpoints, safe invalid input, and regular sheet clipping before full content appears.
- Layout coverage preserves Floating's full capsule and Classic's distinct top/bottom corners through expansion. The shared `calculateAdaptivePlayerCornerRadius` helper keeps corner interpolation out of surface composition; regression tests cover regular/compact endpoints, Classic's intermediate asymmetry, expansion and square fullscreen-video corners.
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
