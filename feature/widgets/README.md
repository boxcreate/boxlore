# `:feature:widgets`

## Purpose

Owns boxlore's home-screen widgets, built with `AppWidgetProvider` and `RemoteViews` (no Glance): four playback widgets and two library widgets, drawn as Material 3 Expressive shapes (pills, circles and a scalloped "cookie") on a transparent root. It renders snapshots supplied through narrow ports and does not construct `PlaybackRepository` or `SubscriptionRepository`, or talk to Media3 or Room directly; `:app` installs the ports.

## Widgets

| Picker label | Receiver | Contents |
| :--- | :--- | :--- |
| Now Playing · Full Controls | `NowPlayingWidgetReceiver` | Info pill (round artwork in a progress ring, title, time left and show) over a row of previous and seek back, play-pause, seek forward and next |
| Now Playing · Compact | `NowPlayingBarWidgetReceiver` | One pill with artwork in its progress ring, the episode title and time left (the artwork names the show), seek back, play-pause and seek forward |
| Now Playing · Round | `PlaybackControlsWidgetReceiver` | Round widget: artwork inside a progress ring, with seek back, play-pause and seek forward along the bottom |
| Now Playing · Round with Next | `PlaybackNextControlsWidgetReceiver` | Round widget: artwork inside a progress ring, with seek forward, play-pause and next episode |
| Library · Your Shows | `SubscriptionsWidgetReceiver` | Scrollable grid of subscribed shows' artwork, in Library's Shows order, with a dot on shows that have a new episode |
| Library · New Episodes | `NewEpisodesWidgetReceiver` | Scrollable rows of latest episodes, with Library's New Episodes filters |

- There is no shuffle or repeat on widgets. Library widgets never play audio; rows and tiles open the app.
- The play button is a circle at rest and the scalloped cookie while playing. Seek icons show the listener's own seek lengths.
- When playback is dismissed, every playback widget becomes a launch surface ("Ready when you are") that opens boxlore, never disabled transport.
- Library lists are capped at `LibraryWidgetLogic.MAX_ROWS`; the header badge counts the whole library through `WidgetLibraryCounts`. Row and tile taps open the deep link `:app` supplies; the header and its arrow open `boxlore://library/subscriptions?tab=0` or `?tab=1`.
- Your Shows draws whole covers as rounded squares, or cuts them into mixed shapes when the listener chooses that in Appearance › Widgets (`WidgetShowsArtwork`).
- Missing or unreadable artwork falls back to show or episode candidates, then to a tinted placeholder with the boxlore mark, cut to the same shape.

## Public API

- The six receivers above: picker-visible providers.
- `HomeScreenWidgets`: the six widgets under their picker names, each one's live preview as `RemoteViews` with the size it is drawn for (`preview`), whether the current home screen app can add a widget on request (`canAdd`), and the request itself (`requestAdd`). Settings shows and offers them through `:app`.
- `WidgetControlReceiver`: one explicit, non-exported action endpoint shared by the playback widgets.
- `WidgetActionIntents`: builds broadcast, open-app and deep-link `PendingIntent`s with `SecurityException` guards. `cancelAll(context, appWidgetId)` and `LibraryWidgetRenderer.cancelAll` cancel a deleted widget's intents so the app never exhausts the system's `PendingIntent` quota.
- `NowPlayingWidgetDependencies`, `configureNowPlayingWidget(...)` and `WidgetPlaybackSource`: the playback port. `refreshPosition()` asks the player for its position, which the app only tracks while it is on screen.
- `LibraryWidgetDependencies`, `configureLibraryWidgets(...)` and `WidgetLibrarySource`: the library port, with capped lists and uncapped `counts`.
- `NowPlayingWidgetSnapshotStore` and `LibraryWidgetSnapshotStore`: persisted snapshots each widget renders from.

## Rendering and theming

- Coordinators collect the ports' flows, persist snapshots, render `RemoteViews` and load artwork without blocking metadata; cancellation keeps an outdated artwork result from replacing newer metadata.
- Playback layouts scale themselves: rows share the height by weight, squares take their width from their height, icons are fractions of their button and text shrinks to fit, inside a frame that keeps the widget's proportions and centres it in the cell. Nothing is sized from what the launcher reports, so a widget fits phones, tablets, foldables and any resize. The round widgets stay a circle as wide as the cell's shorter side, and Your Shows picks its own column count.
- What a playback widget shows (title lines, the time left, the show name, Full Controls' taller shape) changes at the size steps in `WidgetPresentation`. Each update carries one layout per step and the launcher draws the largest that fits the space it really has, also while resizing. Reported sizes (`WidgetSizes`) only decide how sharp the pictures are.
- While an episode plays and a playback widget is placed, `WidgetProgressPoller` asks the port for the position every `PROGRESS_POLL_MS`. `WidgetUpdatePolicy` repaints only when the ring or the time-left label would change, about once a minute.
- Both renderers push through `pushWidgetUpdate`. From Android 17, an update over the launcher's bitmap cap throws; the guard catches it, retries once with a lighter update where one exists, and otherwise waits for the next update, so the player service never crashes.
- Artwork is decoded to fit the widget and cut to its shape (`WidgetShapes`), keeping updates under that cap. `WidgetBitmapCache` keeps cut artwork, rings, masks and seek icons in memory, so a tap redraws without decoding anything again.
- Library covers load a few at a time and each batch is shown as it lands (`LibraryArtworkBatches`); a newer library waits for the older load to stop before it saves. The list service also fetches a missing cover itself, so covers appear while the app is closed; a tile shows its placeholder until then, and a cover that ran out of time is left alone for a while (`ArtworkTimeoutCooldown`).
- Every colour is a tint, never baked into a bitmap. Widget chrome follows the in-app Appearance theme by default: `WidgetChrome` sends one ARGB value for a fixed Light or Dark display mode, and a light and dark pair when the display mode follows the system, so the launcher switches with the phone while the app is not running. Appearance › Widgets can switch to System, which uses the system's dynamic colour resources.
- `WidgetThemeSync` re-pushes widgets on configuration changes; `:app` refreshes them when the theme or widget preferences change.
- Picker previews: from Android 15, `WidgetPickerPreviews` publishes previews drawn by the live renderers with sample content, in the listener's own widget colours, again whenever that look changes. Older versions show the static `widget_preview_*.xml` layouts in resource colours.

## Design provenance

The widgets are boxlore's own design in Material 3 Expressive shapes, drawn in code and vector drawables written for this module. Transport icons come from Google Material Symbols under Apache-2.0. boxlore keeps its own PolyForm Strict licensing and implementation.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/widgets/
  NowPlayingWidget*.kt          # receivers, dependencies, coordinator, renderer, models, snapshot store
  LibraryWidget*.kt             # receivers, dependencies, coordinator, renderer, snapshot store,
                                #   RemoteViewsService for the lists
  WidgetArtworkLoader.kt, WidgetBitmapCache.kt, WidgetChrome.kt, WidgetPalette.kt,
  WidgetRemoteViewsColors.kt, WidgetShapes.kt, WidgetSeekIcon.kt, WidgetPickerPreviews.kt,
  WidgetThemeSync.kt, WidgetHostPresence.kt, WidgetUpdates.kt, HomeScreenWidgets.kt
  actions/                      # WidgetActionIntents, WidgetActionHandler, WidgetControlReceiver
  logic/                        # NowPlayingWidgetLogic, LibraryWidgetLogic, WidgetPresentation, WidgetSizes,
                                #   WidgetProgress, WidgetProgressPoller, LibraryArtworkBatches,
                                #   ArtworkTimeoutCooldown
src/main/res/
  layout/                       # live layouts and picker previews
  xml/*_widget_info.xml         # provider info
```

## Dependencies

- → `:core:model`, `:core:designsystem` (font and theme colours), `:core:prefs` (`FontRoundnessAxis`, `WidgetAppearance`, `WidgetShowsArtwork`).
- Libraries: AndroidX Core, Material, Coil (artwork disk cache), Kotlin coroutines, kotlinx.serialization JSON; tests add Robolectric and coroutines-test.
- Feature modules never depend on other features. This module never constructs repositories; `:app`'s `NowPlayingWidgetPlaybackAdapter` and `WidgetLibrarySourceAdapter` implement the ports.

## Threading / lifecycle

- `:app` calls both `configure*` helpers once from `BoxLoreApplication` after `AppContainer` is ready. On the application IO scope they check for installed widgets (`hasInstalledWidgets`) and start collection only for an installed family.
- Adding a widget later starts its coordinator through the enabled broadcast. The app's adapters are lazy, so an unused family never restores playback or ranks the library.
- Coordinator collection and artwork IO run on the application `CoroutineScope` from the dependencies.
- Control broadcasts use `goAsync()` with `Dispatchers.Main.immediate`: an optimistic snapshot renders first from the player's last published position, the action then asks for the real position, and a failed action rolls back to the real playback state.
- Progress polling runs on the application scope only while an episode plays and a playback widget is placed; it stops when playback pauses.
- `:app`'s playback adapter runs every transport action on the main thread before touching `MediaController`.
- `onUpdate` reads snapshots on a background scope, then renders once.
- Dependency holders expose `instance` with an `internal` setter; only `configure*` installs them.

## Persistence & identity

| Concern | Contract |
| :--- | :--- |
| SharedPreferences files | `boxlore_now_playing_widget`, `boxlore_library_widget`, `boxlore_widget_picker_previews` (the look each published preview was drawn for) |
| Snapshot key | `snapshot` (JSON) |
| Artwork cache | `{cacheDir}/widget_artwork/` |
| Receiver FQCNs | the six receivers above, under `cx.aswin.boxlore.feature.widgets` |
| Action receiver | `cx.aswin.boxlore.feature.widgets.actions.WidgetControlReceiver` (not exported) |
| RemoteViewsService | `LibraryWidgetRemoteViewsService` (not exported; `BIND_REMOTEVIEWS`) |
| Provider info | every provider uses `updatePeriodMillis=0` |

## Testing notes

Hermetic JVM tests live in `feature/widgets/src/test`, using Robolectric and fakes from [`:core:testing`](../../core/testing/README.md).

| Area | Tests |
| :--- | :--- |
| Playback widgets | `NowPlayingWidgetRemoteViewsTest`, `NowPlayingWidgetCoordinatorTest`, `NowPlayingWidgetLogicTest`, `WidgetOptimisticActionTest`, `WidgetActionHandlerTest` |
| Sizing, resizing and progress | `WidgetPresentationTest`, `WidgetSizesTest`, `WidgetProgressTest`, `WidgetProgressPollerTest`, `WidgetShapesTest` |
| Every widget, state and size applied and drawn | `WidgetRenderGalleryTest` (set `BOXLORE_WIDGET_RENDERS=1` to save the drawings under `build/reports/widget-renders/`) |
| Library widgets | `LibraryWidgetRemoteViewsTest`, `LibraryWidgetLogicTest`, `LibraryArtworkBatchesTest`, `ArtworkTimeoutCooldownTest` |
| Snapshots and dependency holders | `NowPlayingWidgetSnapshotStoreTest`, `NowPlayingWidgetDependenciesHolderTest` |
| Startup and updates | `WidgetStartupTest`, `WidgetUpdatesTest` |
| Theming, picker previews and adding from the app | `WidgetChromeTest`, `WidgetRemoteViewsColorsTest`, `WidgetPickerPreviewsTest`, `HomeScreenWidgetsTest` |
| Intents | `WidgetActionIntentsTest` |

```bash
./gradlew :feature:widgets:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs widget JVM tests in the features shard; the root `build.gradle.kts` includes the module in merged Kover coverage.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:app` README](../../app/README.md) (widget port installation from `BoxLoreApplication`)
- [`:feature:library` README](../library/README.md)
