# `:feature:player`

## Purpose

Owns player presentation: the mini player, the full player sheet in its Standard and bunpod styles, the tablet player layouts, the queue, chapters and transcript surfaces, the Cast picker and the video overlay, plus the pure layout and control logic behind them.

It does not own playback engines, queue persistence, Media3 services, Smart Queue refill or media ID policy; those live in [`:core:playback`](../../core/playback/README.md). Routes, browsing chrome and the window shell belong to `:app`.

## Public API

Used by `:app`:

- `v2.PlayerSheetScaffold`: the mini/full player overlay the app shell composes. It takes the app's one UI-scoped `PlaybackRepository`, `DownloadRepository` and `UserPreferencesRepository`; it never creates its own `PlaybackRepository`.
- `PlayerSheetLayout`: placement from the shell: collapsed position, navigation style, the floating-navigation compact morph (`compactProgress`, `compactTargetY`), the optional mini-player seek buttons and fold-safe side insets.
- `PlayerSheetActions`: episode and show navigation, `onSheetInteractionChanged` (full player visible, sheet dragging or fullscreen video, so the shell can pause browsing-driven morphs) and `onOpenRequest` (lets the host open the player elsewhere instead of expanding the sheet).
- `v2.FullPlayerV2` with `FullPlayerDependencies`, `FullPlayerDisplay` and `FullPlayerActions`: the full player, also hosted directly by the tablet shell as a pane or docked player.
- `v2.MiniPlayerV2` with `MiniPlayerContent`, `MiniPlayerColors` and `MiniPlayerActions`: the collapsed bar.
- `RetainedPlayerPresentation(tabletLayout)` and `playerPresentationController()`: an app-owned scope that keeps supporting tabs, transient controls and expanded state while the same episode moves between pane, docked and sheet presentations.
- `PlayerFullscreenOverlay` and `LocalPlayerFullscreenRequest`: let the tablet shell own full-window video and transcript overlays.
- `rememberPlayerColors` / `PlayerColors`: the artwork-derived player scheme and mini-bar colours.
- `QueueSheetContent`, `QueueSheetActions`, `ChaptersSheetContent`, `TranscriptView`, `FullscreenTranscriptScreen`, `PlayerControls`, `SeekDurationIcon` and `formatTime`: shared sub-surfaces and helpers.

## Player styles

- Appearance → **Player style** stores `FullPlayerStyle` (`standard` or `bunpod`) in `:core:prefs`. `useMinimalPlayer` picks the bunpod layout for audio only; video always uses Standard.
- **Standard** (`FullPlayerV2`, `ControlDeck`): artwork or Cast hero, seekbar, transport and a quick-action tray (speed, sleep, queue, chapters, transcript), with Up next and show notes below.
- **bunpod** (`Minimal*.kt`): show, title, remaining time, a thin seekbar and five controls, without artwork. The **Up next** arrow or an upward swipe at the end opens the queue sheet once per gesture (`MinimalQueueSwipe`).
- Chapter and transcript quick actions stay available offline when a downloaded file, live playback state or timestamped show notes provide them (`SecondaryAvailabilityState`).
- Tapping download on an already downloaded episode asks before removing it (`RemoveDownloadConfirmationDialog` from `:core:designsystem`).

## Mini player and sheet

- The bar is a split pill (`MiniPlayerSplit.kt`): an info segment with cover, title and show · time left, and a Play end. Its outer shape follows Appearance → Navigation (Floating or Classic).
- With Floating navigation the shell can shrink the bar into a cookie-shaped compact player in the navigation action slot. `MiniPlayerShape`, `MiniPlayerMorph` and `MiniPlayerMotion` keep surface, artwork and progress ring in step; the ring turns only while playing and visible. Classic ignores compactness.
- Compactness changes visual bounds only; draggable anchors and predictive Back stay fixed, and controls take no input while compact.
- Expansion follows the sheet fraction both ways (`PlayerSheetRevealLogic`): the mini content fades out before the full player fades in, and the two never show together.
- Scrolling the full player's content stops at its top; collapsing takes a new downward swipe (`PlayerSheetNestedScrollLogic`).
- A paused bar can be swiped sideways to dismiss after a confirmation (`MiniPlayerDismissLogic`).
- The sheet runs past the screen's foot; `FullPlayerDisplay.visibleHeight` and `Modifier.fitVisibleHeight` keep full-screen transcript controls on screen.

## Queue and Cast

- `PlayerQueueSheet` wraps `QueueSheetContent`: reorder, remove with Undo, source labels and show navigation. With an empty queue and Smart queue off, it offers **Turn on**.
- `SameShowContinuationBanner` appears above Up next when recommendation playback skipped same-show continuation, and adds the show's next episodes in one tap.
- The Cast button opens a boxlore device sheet that scans only while open. On Android 17 it first asks for local network access; if that is declined, it opens Android's own output picker instead (`castButtonAction`, `castActionAfterLocalNetworkRequest`).
- One `CastPickerState` (`LocalCastPicker`) sits above the phone and tablet layouts, so an open sheet or a pending permission answer survives a fold, unfold or resize.
- While casting, the hero toggles between artwork and receiver controls (`FullPlayerCastHero`, `FullPlayerCastControls`), and the mini bar shows the Cast device.

## Tablets and foldables

- `TabletPlayerBody`, `TabletPlayerSupport` and `TabletPlayerLayoutLogic` arrange the player as stacked, columns or around a horizontal or vertical fold, keeping regions off the hinge.
- Up next, Chapters, Transcript and Notes become supporting tabs (`PlayerSupportingTab`) that survive recreation and window changes through `FullPlayerUiStateSaver` and `RetainedPlayerPresentation`.
- A phone keeps its own player state until the first tablet frame, which continues it. Window gates and transition rules are in [adaptive UI and state](../../ARCHITECTURE.md#adaptive-ui-and-state).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/player/
  ChaptersSheet.kt, PlayerControls.kt, PlayerUtils.kt, QueueScreen.kt,
  SeekDurationIcon.kt, TranscriptView.kt     shared sub-surfaces
  v2/
    PlayerSheetScaffold.kt                   mini/full sheet
    FullPlayer*.kt, ControlDeck*.kt          Standard player, top bar, Cast hero, video
    PlayerHero.kt, PlayerSeekbar.kt, InlineTranscriptHero.kt,
    PlayerTranscriptAction.kt, UpNextCard.kt, NotesPreviewCard.kt
    Minimal*.kt                              bunpod style
    MiniPlayer*.kt                           mini and compact player
    Cast*.kt                                 Cast picker state and logic
    Tablet*.kt, RetainedPlayerPresentation.kt, PlayerFullscreenRequest.kt
    PlayerTheme.kt                           artwork-derived colours
    logic/                                   pure layout, control, queue, seek, reveal logic
src/main/res/                                strings, plurals, video texture layout
```

## Dependencies

- Project: `:core:model`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:prefs`, `:core:network`, `:core:designsystem`, `:core:analytics`.
- Libraries: Compose Material 3, AndroidX Graphics Shapes, Navigation, lifecycle, Media3 (ExoPlayer, Session, UI, Cast), MediaRouter, Google Cast framework, Coil, smooth corner rect, reorderable, coroutines. Tests add `:core:testing`, JUnit 5, Turbine, Robolectric and Compose UI test.
- No feature → feature edges, no direct PostHog calls, and no second `PlaybackRepository`. See [`ARCHITECTURE.md`](../../ARCHITECTURE.md#dependency-direction).

## Threading / lifecycle

- The player overlay lives in Activity composition, not in a navigation destination.
- Playback, queue, downloads and prefs are application-scoped instances from app wiring; the player only observes their flows and calls their APIs. Playback contracts are in [`core/playback/README.md`](../../core/playback/README.md).
- UI runs on the main thread. The sheet separates position updates from other player state so ticks don't recompose the whole sheet.
- Colours come from the cover through `:core:designsystem`'s shared artwork palette loader; a cover already read colours the player from the first frame.
- Compact motion runs only while playing and visible, and stops when the lifecycle stops.
- The video overlay hides only the status and navigation bars (`videoOverlayBarTypes`).

## Persistence & identity

- This module owns no storage files. Keys it reads live in `:core:prefs`: `full_player_style` and `mini_player_seek_buttons_enabled`.
- Queue rows, session flags and media ID prefixes belong to `:core:playback`; the player treats media IDs as opaque strings.
- Stable test tags used by `:app` tests: `tablet_player_artwork_region`, `tablet_player_controls_region`, `tablet_player_support_region`, `tablet_player_support`, `tablet_player_scroll`. `minimalEpisodeTitle` is used by this module's tests.
- Page views (`TrackScreenView`): the expanded sheet is `player_full`; its sheets report `player_queue`, `player_chapters`, `player_speed`, `player_sleep_timer`, `player_cast`, `player_share` and `player_transcript_fullscreen`. See [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Testing notes

Hermetic JVM tests live in `feature/player/src/test`, logic on plain JVM and UI on Robolectric. `PlayerPhoneGoldenTest` is a local-only phone golden; see [`docs/TESTING.md`](../../docs/TESTING.md#screenshots).

| Area | Test classes |
| :--- | :--- |
| Sheet reveal, bounds and scroll handoff | `PlayerSheetRevealLogicTest`, `AdaptivePlayerBoundsLogicTest`, `PlayerSheetNestedScrollLogicTest`, `PlayerLayoutLogicTest` |
| Mini and compact player | `MiniPlayerSplitTest`, `MiniPlayerShapeTest`, `MiniPlayerMotionTest`, `MiniPlayerCompactStatusTest`, `MiniPlayerProgressLogicTest`, `MiniPlayerDismissLogicTest` |
| Controls and seekbar | `PlayerControlLogicTest`, `SeekbarLogicTest`, `PlayerMetadataStylingLogicTest`, `FormatTimeTest` |
| bunpod style | `MinimalPlayerLogicTest`, `MinimalPlayerContentTest`, `MinimalPlayerControlsTest`, `MinimalControlAccentTest` |
| Queue and continuation | `QueueLabelLogicTest`, `QueuePodcastLogicTest`, `SameShowContinuationBannerTest`, `TabletQueueIndexTest`, `TabletQueueViewportTest` |
| Chapters and transcript | `ChapterArtFlowTest`, `PlayerTranscriptActionTest`, `FullscreenTranscriptLayoutTest` |
| Cast | `CastRoutePickerLogicTest`, `CastPickerReflowTest` |
| Tablets, folds and saved state | `TabletPlayerLayoutLogicTest`, `TabletPlayerContentTest`, `TabletPlayerInsetsTest`, `FullPlayerUiStateSaverTest` |
| Video overlay | `VideoOverlayBarTypesTest` |

```bash
./gradlew :feature:player:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs this module's JVM tests in the features-a shard and includes it in merged coverage; phone goldens stay out of it.
- `scripts/ci/check-feature-no-posthog.sh` guards direct PostHog use.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`:core:playback` README](../../core/playback/README.md)
- [`:core:designsystem` README](../../core/designsystem/README.md)
- [`:app` README](../../app/README.md)
