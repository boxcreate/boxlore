# `:feature:player`

## Purpose

Owns player presentation: mini player, full player sheet, queue screen UI, control deck, seekbar, chapters, transcript surfaces, and pure UI logic. Playback engines, queue persistence, Media3 services, and media ID policy live in `:core:playback`.

## Public API

- `FullPlayerV2` also supports Appearance → **Player style** → **bunpod** for audio. Its original Compose layout is inspired by BunPod: show/title, remaining time, thin seekbar and five transport controls, with no displayed artwork. The title block is vertically centered above transport. Episode titles adapt from 28–40sp within four lines; show names adapt from 12–16sp within two. Remaining time fits one line at 32–64sp. This canvas uses crisp Google Sans Flex independently of Appearance lettering and caps its height to the visible window, keeping controls and the queue arrow above system navigation even though the shared sheet's animation canvas is taller. The existing artwork-derived palette, sheet anchors, predictive Back, global seek durations, analytics and miniplayer remain unchanged. Playback morphs its square into a circle, sleep into a four-sided cookie and custom speed into a six-sided cookie. Each seek direction has distinct transient press geometry. State/enablement changes reset press tracking so interrupted or repeated taps cannot leave Pause with the pressed square silhouette. Buffering uses the shared circular wavy loader. Long titles open in full on tap, and short windows scroll rather than covering controls.
- Episode titles use Android's full, dictionary-based hyphenation and high-quality line breaks through a scoped native text view within the existing four-line/adaptive-size bounds; show names and the full-title dialog keep their normal wrapping. The Compose node retains the original title and full-title accessibility action. Play/Pause keeps the artwork-derived primary accent; active sleep/speed controls use the artwork palette's own tertiary container and corresponding foreground, distinguishing them by tone rather than generating a new hue. Inactive small controls remain neutral. This hierarchy affects only the bunpod controls.
- The arrow labelled **Up next** for accessibility and an upward swipe at the end of the player open the unchanged `PlayerQueueSheet`, preserving reorder, remove/Undo, source labels, queue index mapping, show navigation, Same Show continuation and Smart Queue recovery. Speed/sleep controls use the existing modal sheets. Video retains the standard player.

- Player, mini-player, Up next and Queue retain episode/show artwork alternatives. Full-player chapter art falls through to episode then show artwork; next-episode art has its own chain. Chapter tiles use the shared loader. Playback, queue advancement and artwork geometry are unchanged.

- Queue rows use steady Material feedback; custom mini-player and Up Next controls opt into the shared 3% non-bouncy press response. The full-player connected transport animation, drag/seek interactions and playback callbacks are unchanged. Mini-player Play/Seek and Up Next backgrounds sit inside their press layer so the complete coloured control contracts with its content.

- `v2.PlayerSheetScaffold` is composed by `:app` as the mini/full player overlay.
- The collapsed v2 mini player follows Appearance → Navigation: Floating is a 64dp capsule with 32dp corners; Classic keeps the 72dp shape with 26dp top and 14dp bottom corners. Both clips use the selected navigation's corner metrics. The solid artwork-tinted surface holds 48dp cookie-shaped artwork, title/show metadata, and a 48dp circular play/pause control inside one bar. `PlayerSheetLayout.miniPlayerSeekButtonsEnabled` defaults off; opting in adds 40dp back/forward controls around play/pause using global seek durations. The title and show/Cast label each get their own line. A rounded progress line beneath them smooths position updates and gently waves while playing; its quiet remaining track has an open gap after the played segment. Pause/loading flatten the wave, and compactness, expansion, and stopped lifecycle suspend motion.
- The mini bar is a connected split pill (`MiniPlayerSplit.kt`): an info segment and a 72dp Play end joined by a see-through 3dp gap with 8dp inner corners; the outer ends keep the navigation-driven clip. The info segment shows the played share as a solid sweep one tone deeper (lighter in dark mode), and the 40dp cookie cover sits inside the same revolving scalloped progress ring as the compact cookie (its own clock while playing in the bar). The title is one line and the second line reads show · time left (`miniPlayerTimeLeft`). Optional seek buttons sit in the info segment before the Play end. As the bar starts shrinking to the compact cookie, the split merges into one surface (`miniSplitAmount`) and the bar ring fades as the compact ring fades in. The sheet backing behind the collapsed bar stays transparent so the gap is see-through, and fills in as the mini player fades into the full player. The previous wavy line (`MiniPlayerProgress`) is no longer shown. `MiniPlayerSplitTest` covers time left and the split merge.
- Floating navigation can supply a shared `PlayerSheetLayout.compactProgress` and `compactTargetY` to morph that same mini-player surface and artwork into a 52dp scalloped cookie in the trailing navigation action slot. The nine shallow lobes grow during descent; `MiniPlayerShape` supplies matching surface, artwork, and progress contours. The app shell owns browsing scroll detection and eligibility; Classic ignores compactness. The compact player opens the full player on tap or swipe up and shows progress, loading, and Cast status without a pause badge or duplicate transport controls; its spoken state still reports Playing/Paused. The optional seek preference only affects the regular bar. Metadata, controls, and paused swipe-dismiss confirmation lose interaction/focus during compactness. Its measured hit bounds follow the visible surface, including RTL placement.
- Compact playback progress uses a 3.5dp scalloped ring outside the 40dp artwork, with an open gap, a solid neutral track, and a primary played arc with a rounded end. The compact backdrop and shadow fade away to expose that gap. The track stays visible while paused, loading, or awaiting duration; playback milliseconds take precedence over the episode's seconds fallback. A small central loading indicator leaves the progress ring visible. Spoken progress uses the same duration resolution.
- `MiniPlayerMorph` shares compactness, sheet expansion, cookie depth, and a drawing-only rotation state. While playback is active and the player is fully compact, `MiniPlayerMotion` moves the surface, artwork clip, and progress contour together at up to 30fps, completing one lobe cycle every 2.4 seconds. The image stays upright and the progress origin stays at twelve o'clock. Pause/buffering, sheet expansion, and stopped lifecycle freeze the phase; resume continues it. The motion does not change layout or draggable anchors.
- Mini/full expansion uses the existing interruptible, critically damped sheet spring and one reversible fade-through choreography derived from the sheet fraction (`PlayerSheetRevealLogic`): the mini content fades out by 20%, then the full player fades in from 95% scale and a 40dp rise once the sheet can hold it (16–56% from the bar, 32–78% from the compact cookie, so it never appears cramped in a small box); the two never double-expose. Corners keep their collapsed rounding through the first half and square off over the second (`playerCornerHold`), and width leads height with an ease-out (`playerSheetWiden`) so the bar opens into a sheet while the height follows the finger exactly. The sheet backing is clear behind the resting split bar and fills within 2% of opening (`miniPlayerBackingFill`). Collapse runs the same path in reverse. `PlayerSheetRevealLogicTest`, `AdaptivePlayerBoundsLogicTest` and `MiniPlayerSplitTest` cover the windows, continuity, reversibility, corners, widening and backing fill.
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
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Media3 ExoPlayer/Session/UI/Cast, AndroidX Graphics Shapes, AndroidX MediaRouter, Google Cast framework, Coil, Palette, smooth corner rect, reorderable, coroutines, JUnit, and Turbine.
- Reverse-edge rule: feature modules must not depend on other feature modules or construct a second `PlaybackRepository`.

## Threading / lifecycle

- The player overlay lives with Activity composition rather than a navigation destination.
- Playback and queue state come from application-scoped core dependencies supplied by app wiring.
- Player colours (`rememberPlayerColors`) are one family from the cover: the full player is a Material 3 tonal scheme seeded from the cover's accent (`artworkPlayerColorScheme`) on a soft tinted surface ladder in the player ground's hue (the cover's main colour when clearly coloured, else the accent): light 92 down to 82 and dark 20 up to 32, so it is neither near-white nor near-black; control tiles use a tinted secondary container that steps clearly off its tray. The mini bar (`artworkMiniPlayerColors`) uses the same ground at light 84 / dark 28 with the same soft chroma, and its Play end the same accent tones as the full player. The palette is read by the shared `:core:designsystem` artwork extractor (`loadArtworkPalette`) that podcast and episode pages use. Pages wear the cover's two colours as a tinted ground; the denser player keeps Material's matched contrast pairs instead, so controls read the same on any cover. The full-player background (`playerCanvas`) is the scheme's flat surface with a faint primary glow behind the artwork; it no longer runs a gradient between two colours. A cover already read (by a page or earlier playback) colours the player from the first frame, and light/dark switches rebuild the scheme without reading the art again. Art loads through Coil with its disk cache from the resolved episode→podcast artwork URL, so process death does not leave the sheet on the default theme. Pure black/white styles keep their grounds (`artworkSurfaceStyle`).
- UI runs on the main thread; playback operations delegate to core repositories and Media3 session APIs.

## Persistence & identity

- This module owns no storage files or stable keys.
- Player session flags, queue rows, and media ID prefixes are owned by `:core:playback` and related core modules.
- UI state should treat media IDs as opaque strings from playback APIs.

## Testing notes

- `PlayerPhoneGoldenTest` captures the Standard full player at both portrait phone sizes and short landscape in light/dark. Fixed player/download/preference flows start no media service. Captures are local only, enabled with `-PphoneGoldens=verify --tests '*PhoneGoldenTest'`; see [capture instructions](../../docs/screenshots/README.md).

- `MinimalPlayerLogicTest` covers style fallback/video handling, bounded remaining time and one queue opening per upward gesture. `MinimalPlayerContentTest` exercises the real controls, accessibility seeking/queue access, title expansion, adaptive short/long text, title-only full hyphenation, centered metadata, scoped crisp typography, oversized sheet/window bounds, large-text RTL, buffering, short landscape windows and swipe-to-queue with local callbacks; it sends no media or network requests. `MinimalPlayerControlsTest` checks distinct active silhouettes, separate main/auxiliary control accents in both themes, rapid taps and a press interrupted by buffering. `MinimalControlAccentTest` checks related hues, tone separation and foreground contrast across artwork seeds in both modes. Optional local render exports use `BOXLORE_UI_TEST_ARTIFACTS`.

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
