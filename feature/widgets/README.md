# `:feature:widgets`

## Purpose

Owns boxlore's home-screen widgets, built with `AppWidgetProvider` and `RemoteViews` (no Glance): four playback widgets and two library list widgets. It renders snapshots supplied through narrow ports and does not construct `PlaybackRepository` or `SubscriptionRepository`, or talk to Media3 or Room directly; `:app` installs the ports.

## Widgets

| Picker label | Receiver | Contents |
| :--- | :--- | :--- |
| Now Playing · Full Controls | `NowPlayingWidgetReceiver` | Artwork, titles, previous, seek back, play-pause, seek forward, next |
| Now Playing · Compact | `NowPlayingBarWidgetReceiver` | One-row bar with artwork, titles and seek controls |
| Playback Controls · Seek | `PlaybackControlsWidgetReceiver` | Square grid: artwork, play-pause, seek back and forward |
| Playback Controls · Next | `PlaybackNextControlsWidgetReceiver` | Square grid: artwork, play-pause, next episode and seek forward |
| Your Shows | `SubscriptionsWidgetReceiver` | Scrollable list of subscribed shows, in Library's Shows order |
| New Episodes | `NewEpisodesWidgetReceiver` | Scrollable list of latest episodes, with Library's New Episodes filters |

- There is no shuffle or repeat on widgets. Library list widgets never play audio; rows open the app.
- When playback is dismissed, every playback widget becomes a branded launch surface ("Ready when you are") that opens boxlore, never disabled transport.
- Library lists are capped at `LibraryWidgetLogic.MAX_ROWS`. Row taps open the deep link `:app` supplies on each row; the footer opens `boxlore://library/subscriptions?tab=0` or `?tab=1`.
- Missing or unreadable artwork falls back to show or episode candidates, then to the shared boxlore mark.

## Public API

- The six receivers above: picker-visible providers.
- `WidgetControlReceiver`: one explicit, non-exported action endpoint shared by the playback widgets.
- `WidgetActionIntents`: builds broadcast, open-app and deep-link `PendingIntent`s with `SecurityException` guards. `cancelAll(context, appWidgetId)` and `LibraryWidgetRenderer.cancelAll` cancel a deleted widget's intents so the app never exhausts the system's `PendingIntent` quota.
- `NowPlayingWidgetDependencies`, `configureNowPlayingWidget(...)` and `WidgetPlaybackSource`: the playback port.
- `LibraryWidgetDependencies`, `configureLibraryWidgets(...)` and `WidgetLibrarySource`: the library port.
- `NowPlayingWidgetSnapshotStore` and `LibraryWidgetSnapshotStore`: persisted snapshots each widget renders from.

## Rendering and theming

- Coordinators collect the ports' flows, persist snapshots, render `RemoteViews` and load artwork without blocking metadata; cancellation keeps an outdated artwork result from replacing newer metadata.
- Both renderers push through `pushWidgetUpdate`. From Android 17, an update over the launcher's bitmap cap throws; the guard catches it, retries once with a lighter update where one exists, and otherwise waits for the next update, so the player service never crashes.
- Artwork is decoded to fit the widget, and text bitmaps (`WidgetTextBitmapRenderer`) to the label's own box, keeping updates under that cap.
- Widget chrome follows the in-app Appearance theme by default (`WidgetChrome` bakes colours from the theme cache). Appearance › Widgets can switch to System, which uses the system's dynamic colour resources.
- `WidgetThemeSync` re-pushes widgets on configuration changes; `:app` refreshes them when the theme preferences change.
- Picker previews (`widget_preview_*.xml`) mirror each live variant on themed surfaces.

## Design provenance

The playback widgets are an independent `RemoteViews` implementation of the widget hierarchy measured from PixelPlayerOSS. No GPL source, app assets, names or resources are copied into boxlore. Transport icons come independently from Google Material Symbols under Apache-2.0. boxlore keeps its own PolyForm Strict licensing and implementation.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/widgets/
  NowPlayingWidget*.kt          # receivers, dependencies, coordinator, renderer, models, snapshot store
  LibraryWidget*.kt             # receivers, dependencies, coordinator, renderer, snapshot store,
                                #   RemoteViewsService for the lists
  WidgetArtworkLoader.kt, WidgetChrome.kt, WidgetPalette.kt, WidgetRemoteViewsColors.kt,
  WidgetTextBitmapRenderer.kt, WidgetThemeSync.kt, WidgetHostPresence.kt, WidgetUpdates.kt
  actions/                      # WidgetActionIntents, WidgetActionHandler, WidgetControlReceiver
  logic/                        # NowPlayingWidgetLogic, LibraryWidgetLogic
src/main/res/
  layout/                       # live layouts and picker previews
  xml/*_widget_info.xml         # provider info
```

## Dependencies

- → `:core:model`, `:core:designsystem` (font and theme colours), `:core:prefs` (`FontRoundnessAxis`, `WidgetAppearance`).
- Libraries: AndroidX Core, Material, Coil (artwork disk cache), Kotlin coroutines, kotlinx.serialization JSON; tests add Robolectric and coroutines-test.
- Feature modules never depend on other features. This module never constructs repositories; `:app`'s `NowPlayingWidgetPlaybackAdapter` and `WidgetLibrarySourceAdapter` implement the ports.

## Threading / lifecycle

- `:app` calls both `configure*` helpers once from `BoxLoreApplication` after `AppContainer` is ready. On the application IO scope they check for installed widgets (`hasInstalledWidgets`) and start collection only for an installed family.
- Adding a widget later starts its coordinator through the enabled broadcast. The app's adapters are lazy, so an unused family never restores playback or ranks the library.
- Coordinator collection and artwork IO run on the application `CoroutineScope` from the dependencies.
- Control broadcasts use `goAsync()` with `Dispatchers.Main.immediate`: an optimistic snapshot renders first, and a failed action rolls back to the real playback state.
- `:app`'s playback adapter runs every transport action on the main thread before touching `MediaController`.
- `onUpdate` reads snapshots on a background scope, then renders once.
- Dependency holders expose `instance` with an `internal` setter; only `configure*` installs them.

## Persistence & identity

| Concern | Contract |
| :--- | :--- |
| SharedPreferences files | `boxlore_now_playing_widget`, `boxlore_library_widget` |
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
| Playback widgets | `NowPlayingWidgetRemoteViewsTest`, `NowPlayingWidgetCoordinatorTest`, `NowPlayingWidgetLogicTest`, `WidgetOptimisticActionTest` |
| Library widgets | `LibraryWidgetRemoteViewsTest`, `LibraryWidgetLogicTest` |
| Snapshots and dependency holders | `NowPlayingWidgetSnapshotStoreTest`, `NowPlayingWidgetDependenciesHolderTest` |
| Startup and updates | `WidgetStartupTest`, `WidgetUpdatesTest` |
| Theming and text | `WidgetChromeTest`, `WidgetRemoteViewsColorsTest`, `WidgetTextBitmapRendererTest` |
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
