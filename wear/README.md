# `:wear`

## Purpose

The boxlore app for Wear OS watches: a second application module that installs under the phone app's
application ID and signature. It owns the watch screens, the watch's own player, its downloads and
storage rules, its saved library, and its own connection to boxlore's API.

It plays on its own, with no phone nearby. With the phone app linked it is one more device of
the listener's library; it does not sync with the cloud yet.

## Public API

Nothing outside this module depends on it. Inside it, screens depend only on these ports:

- `data.WatchLibrary`: the listener's shows, Jump back in, New episodes, Up next and downloads, one
  show or episode by ID, search, download problems, and the subscribe, download, play-next and
  mark-played actions.
- `data.WatchPlayback`: what is loaded and where it is playing (`NowPlaying`), and the transport,
  speed, sleep timer, Daily Mix and phone/watch switch. `hasDailyMix` and `canSwitchPlace` say which
  of the last two a build can do.
- `data.WatchSettings`: choices kept on the watch (Your shows layout, storage limit).
- `WatchContainer`: the watch's whole object graph, built once by `WatchApplication` through
  `WatchContainerFactory`. Nothing else creates one.

`LiveWatchContainer` builds the real graph. A debug build made with `-Pwear.sample=true` swaps in
the sample shows under `preview.*`; a release build never shows made-up content.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/wear/
  WatchApplication.kt, MainActivity.kt, LiveWatchContainer.kt
  model/        # WatchShow, WatchEpisode, NowPlaying, speeds, sleep timer choices
  net/          # WatchServer: the watch's own API session
  data/         # ports, the saved library, catalog, feed reader, downloads, storage rules
  playback/     # WatchPlaybackService (Media3), LiveWatchPlayback, PlaybackRules
  link/         # the phone link: WatchLinkRules, LiveWatchLink, LinkedWatchPlayback, PlaceRules, PhoneOrders
  glance/       # the tile and the watch-face shortcut: GlanceRules, TileRules, TileArtwork
  ui/
    WatchApp.kt             # routes and navigation
    theme/                  # boxlore's dark roles on black, Google Sans Flex
    components/             # artwork, pill rows, empty messages, DomeShape, ScreenFit
    home/  lists/  show/  search/  settings/
    shows/                  # Your shows: honeycomb and list
    player/                 # player, more controls, sleep timer, speed, artwork colours
src/debug/java/.../preview/  # sample shows; debug WatchContainerFactory
src/release/java/...         # release WatchContainerFactory
```

- Episodes come from each show's own feed, read as the phone reads it: `WatchCatalog.show` gives
  the address and episode namespace, `RssWatchFeedReader` reads only the newest items through
  `RssFeedClient.fetchNewest`, and `WatchFeedItems` gives each item its recipe ID
  ([`docs/EPISODE_IDS.md`](../docs/EPISODE_IDS.md)). The API's episode list is the fallback when a
  feed cannot be read. Only `/v2` routes are used.
- `WatchLibraryState` is the whole saved library as one value; every change is a pure function.
  `WatchLibraryStore` keeps it in a file.
- `StoragePolicy` decides which stored episodes go: ones left alone for a month, then the least
  recently used once the listener's limit is reached. The episode in the player stays; finishing
  an episode removes its download at that moment. `FileWatchDownloads` applies it and refuses a
  download that cannot fit.
- The phone link ([`:core:wearlink`](../core/wearlink/README.md)) makes the watch one more device
  of the listener's library. The library keeps a `revision` and what the phone has not confirmed
  (`pendingShows`, `pendingEpisodes`); `WatchLinkRules.changes` sends those, and
  `WatchLinkRules.withPhone` takes the phone's shows and newer listening while keeping
  unconfirmed watch changes on top. A phone in reach also answers a show's episodes from what it
  has saved, before the watch reads a feed itself. New episodes and Daily Mix are the phone's own
  lists (`WatchLinkRules.withPhone`); Home hides them until a phone has linked (`HomeTiles`).
  `PhoneOrders` downloads or removes an episode when the phone's Send to watch asks.
- `LinkedWatchPlayback` shows whichever player is in use, the watch's or the phone's
  (`PlaceRules`), sends the buttons to that one, and moves playback between them with its position
  and queue. It tells the phone when the watch plays, and takes the phone's orders (pause, play
  this here) through `WatchLinkListenerService`. A phone that went out of reach while playing is
  shown paused where it was last heard (`PlaceRules.heard`), so Play carries on here.
- `MainActivity` opens a named screen from the launch extra `WatchRoutes.EXTRA_ROUTE`; only the
  routes in `WatchRoutes.launchRoute` are honoured. `WatchRoutes.EXTRA_RESUME` names an episode
  to carry on with (`GlanceRules.onResume`).
- The tile and the watch-face shortcut show the episode to jump back into (`WatchGlance`), read
  from the saved library so they work with the app closed, and are redrawn when it or its time
  left changes. The tile's artwork travels as pixels, named per episode (`TileRules`).
- `ui.shows.HoneycombMath` is the honeycomb's geometry in screen-radius units; `HoneycombShows`
  only draws it.
- Home's top block is a dome (`ui.components.DomeShape`, `ui.home.HomeDome`): it follows the top of
  a round screen, is as tall as what it holds, and gives the show's name its own row when it does
  not fit beside the time left (`HomeDomeRules`). On other screens it is a rounded card.
- The episode in a player is marked wherever it appears: bars move over its artwork on Home's dome
  and in the lists (`ui.components.PlayingBars`, `LocalPlayingEpisode`), and stand still with motion
  reduced. The player shows the artwork under a dark scrim.
- Screens are drawn for a 225dp watch; `ui.components.ScreenFit` scales them down for smaller
  watches and drops what no longer fits. On every screen the most important text gets the room.
- `ui.player.PlayerPalette` builds the player's colours from one artwork colour and fixes each
  role's lightness, so text stays readable on any artwork.
- Speeds and sleep timer choices in `model` repeat the phone player's lists.

## Dependencies

- `:core:network` (API clients, sessions, sync rows), `:core:rss` (feed reading and parsing),
  `:core:model` (the episode ID recipe) and `:core:wearlink` (the phone link). No module depends
  on `:wear`.
- Wear Compose Material 3, Foundation and Navigation, which bring a newer Compose than the phone
  modules use: keep `:wear` away from any module that carries Compose UI.
- Media3 ExoPlayer and Session, OkHttp, kotlinx-serialization, Wear Input, Coil, Palette, Wear
  Tiles with ProtoLayout Material 3, and the watch-face complication data source.
- The Google Sans Flex font file is a copy of the one in `:core:designsystem`.

## Threading / lifecycle

- `WatchContainer` is application-scoped. Screens collect its flows with the lifecycle and hold no
  data of their own.
- `LiveWatchPlayback` and the media controller run on the main thread. API calls, feed reads,
  downloads and file writes run on IO.
- `WatchPlaybackService` is a foreground media service: playback continues with the app closed and
  stops with the task only when nothing is playing.
- Downloads run while the app's process is alive. One cut short is forgotten at the next start and
  starts again when asked.
- The watch holds a limited API session: it has no device attestation.

## Persistence & identity

- Application ID `cx.aswin.boxlore`, the same as the phone app; the release build signs with the
  phone app's key. Both are required for the watch and phone to talk to each other.
- Launcher activity `cx.aswin.boxlore.wear.MainActivity`; services
  `cx.aswin.boxlore.wear.playback.WatchPlaybackService` and
  `cx.aswin.boxlore.wear.link.WatchLinkListenerService`.
- `cx.aswin.boxlore.wear.glance.BoxloreTileService` and `BoxloreComplicationService`: a tile or
  shortcut the listener placed is tied to these names.
- `files/boxlore_watch_library.json` (`WatchLibraryStore.FILE_NAME`): subscriptions, queue,
  progress, played episodes and download state.
- `files/watch_downloads/` (`FileWatchDownloads.DIRECTORY_NAME`): stored audio, one
  `<episode ID>.audio` per episode.
- SharedPreferences `boxlore_watch_settings`: `shows_layout`, `storage_limit_mb`, `changed_at`.
- The capability `boxlore_watch` (`res/values/wear.xml`) is how the phone app finds this app.
- The API session uses `:core:network`'s own file and Keystore alias, created fresh on the watch.
- Install channel `wear` (`WatchServer.CHANNEL`).

## Testing notes

Tests live in `wear/src/test` (JUnit 5, plain JVM, fakes for the ports).

| Area | Test class |
| :--- | :--- |
| Saved library rules: subscribe, progress, played, queue, downloads | `data/WatchLibraryStateTest` |
| API mapping, the store across restarts, the library over fakes | `data/WatchDataTest` |
| Feed items to episodes and their IDs | `data/WatchFeedItemsTest` |
| Storage limit, cleanup and room for a download | `data/StoragePolicyTest` |
| Now-playing state, sleep timer and queue rules | `playback/PlaybackRulesTest` |
| Honeycomb geometry | `ui/shows/HoneycombMathTest` |
| Home dome shape, its rows and progress arc | `ui/components/DomeGeometryTest` |
| Scaling for small watches | `ui/components/ScreenFitTest` |
| The moving now-playing bars | `ui/components/PlayingBarsMathTest` |
| Phone link: what is sent, merged and confirmed | `link/WatchLinkRulesTest` |
| Which player is shown, the watch's or the phone's | `link/PlaceRulesTest` |
| Tile and watch-face shortcut: what they show, when they redraw, what Resume does | `glance/GlanceRulesTest` |
| Home tiles with and without a linked phone | `ui/home/HomeTilesTest` |
| Plain-http audio and the link capability | `WatchManifestTest` |
| Player colours and contrast | `ui/player/PlayerPaletteTest` |
| Speeds, sleep timer choices, text helpers, launch routes | `WatchModelsTest` |

```bash
./gradlew :wear:testDebugUnitTest
```

Playback, downloads and the screens are checked on a Wear OS emulator or watch; there is no
Compose instrumentation.

## See also

- [`ARCHITECTURE.md`](../ARCHITECTURE.md)
- [`docs/EPISODE_IDS.md`](../docs/EPISODE_IDS.md)
- [`docs/TESTING.md`](../docs/TESTING.md)
