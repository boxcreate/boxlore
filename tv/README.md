# `:tv`

## Purpose

The boxlore app for Android TV and Google TV: a second application module that installs under the
phone app's application ID and signature. It owns the TV screens, remote (D-pad) navigation and
focus, the TV launcher banner, and its own release build.

The TV keeps its own small library and plays audio itself, like the watch: it reads boxlore's API
and each show's own feed, and never runs the phone's catalog or playback engines, which are too
heavy for older TVs. Signing in is optional: the TV shows a code, the listener enters it in boxlore
on a phone signed in to their account, and from then on the TV syncs with the account like the
watch does.

Debug and `benchmark` builds run on sample shows; `-Ptv.sample=false` runs the real library in
either. `benchmark` is a release-speed build signed with the debug key, for judging smoothness on a
real TV, where debug builds run several times slower.

## Public API

Nothing outside this module depends on it. Inside it, screens depend only on these ports:

- `data.TvLibrary`: Your shows (pinned first, then ranked), Jump back in, Daily Mix, Up next, liked
  episodes, listening history and trending shows; one show, its episodes and one episode; Your
  shows in the phone's sort orders; search; and the subscribe, like, add-to-Up-next and
  mark-played actions.
- `data.TvPlayback`: what is loaded (`NowPlaying`) and the transport, speed, sleep timer and Daily
  Mix. The remote's media keys reach it through the same calls.
- `cloud.TvAccount`: where signing in stands (`TvSignIn`: signed out, showing a code, signed in),
  starting and cancelling sign-in, and signing out.
- `TvContainer`: the whole object graph, built once by `TvApplication` through
  `TvContainerFactory`. Nothing else creates one. `LiveTvContainer` builds the real one
  (`LiveTvLibrary`, `LiveTvPlayback`).

The debug `TvContainerFactory` swaps in the sample library under `preview.*` unless the build is
made with `-Ptv.sample=false`; the release factory never shows made-up content.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/tv/
  TvApplication.kt, MainActivity.kt
  model/        # TvShow, TvEpisode, NowPlaying, sleep timer and speed choices, sort orders
  LiveTvContainer.kt
  data/         # the TvLibrary and TvPlayback ports; the saved library, API, feeds and their rules
  net/          # TvServer: the TV's own API session
  cloud/        # sign-in with a code (LiveTvAccount), cloud sync (TvCloudSync) and its rules
  playback/     # TvPlaybackService (Media3) and LiveTvPlayback
  ui/
    TvApp.kt        # routes and TvNavigator
    RailScaffold.kt # the navigation rail
    TvText.kt       # time, date and sleep-timer labels
    theme/          # boxlore's dark roles, Google Sans Flex, the safe area (TvSpace)
    components/     # artwork, pill and round buttons, content surfaces, section titles
    home/  show/  player/  search/  library/  settings/
src/debug/java/.../preview/  # sample shows; debug TvContainerFactory
src/release/java/...         # release TvContainerFactory
```

- Home is the Resume card, Jump back in and New episodes (one sliding row each, at most four;
  See all opens Library's New episodes), Your shows, Daily Mix, then charts: top shows and one row
  per genre (`ChartGenre`), each loaded as it nears the screen. Home opens with the remote on Resume;
  coming into a row lands on its first item.
- `ui.home.YourShowsRules` lays out Your shows: up to three rows of eight covers filling the width,
  so at most 24 on Home. All shows opens the rest in Library.
- Focus follows one rule. Content the remote lands on (artwork, rows, covers) takes a light outline
  and lifts (`ContentSurface`); actions take the iris fill (`PillButton`, `RoundButton`). Nothing in
  focus covers another item: a show cover widens into a pill with its name and the other covers in
  its row shrink to make room, so the whole row stays on screen. The player is the
  exception, because the cover's colour already fills Play: there the control the remote is on turns
  near-white and Play takes a light ring.
- The rail (`RailScaffold`) shows icons until the remote reaches it, then labels over a scrim. Tabs
  replace each other above Home, so Back from a tab returns to Home and Back from Home leaves the
  app, as Play's TV rules ask.
- The player is a full-screen route without the rail, coloured from the cover as on the phone:
  `ui.player.PlayerColorRules` takes the cover's main hue and sets each colour role at a fixed
  lightness, so text keeps its contrast on any cover. Left and Right on the seek bar skip back 10 and
  ahead 30 seconds. The bottom row holds speed, sleep timer, Like, Mark played and the next episode;
  side panels hold Up Next, the sleep timer and speed. `handleMediaKey` maps the remote's
  play/pause, rewind and fast-forward.
- Links in episode notes can't open a browser on a TV, so OK on one shows it for the phone.
- Text and controls stay inside the 5% safe area (`TvSpace`); rails that slide sideways may run past
  the right edge to show there is more. Lists scroll only as far as it takes to show the whole item
  the remote moves to (`ui.KeepInView`).
- `data.TvLibraryState` is the whole saved library as one value: followed shows, Up next, places,
  played, likes, history and each followed show's newest episode. Every change is a pure function;
  `TvLibraryStore` keeps it in a file.
- A show's episodes come from its own feed, as on the phone (address from `/v2/podcast`, the shared
  feed reader and episode IDs); the API's list stands in only when the feed cannot be read. Only
  `/v2` routes are used. A show page keeps a list once shown (`TvLibraryRules`).
- Daily Mix is worked out on the TV: one unfinished episode per followed show, its newest, newest
  first. Followed shows' feeds are read again, a few at a time, at most every six hours.
- Your shows on Home use the Smart order: shows listened to most recently first, then as followed.
- `LiveTvPlayback` talks to `TvPlaybackService` through a media controller, saves the place every
  few seconds and on pause, and moves on to Up next when an episode ends. The service's media
  session takes the remote's media keys while the app is closed.
- Signing in: `LiveTvAccount` asks the API for a code (`/auth/tv/start`), shows it, and asks every
  few seconds whether a phone approved it (`/auth/tv/poll`). A code that runs out is replaced by a
  fresh one until the listener leaves the page. The TV then holds its own account token, used as the
  watch's is, and gives it up on Sign out.
- `cloud.TvCloudSync` pushes what changed on the TV, then pulls what changed elsewhere: at start, a
  little after each change, and every 15 minutes. `cloud.TvCloudRules` merges by the rows the phone
  and the watch exchange: each part of a listening row (play, like) wins where it is newer, and the
  episode playing on the TV is never moved. Signing in sends everything listened to on the TV; the
  account's follows come down first, and only shows the account never heard of go up, so a TV never
  changes a show's settings on the phone. Up next stays on the TV.
- Many TVs are old and short of memory: artwork keeps a small memory cache and a bounded disk cache,
  decodes at the size it is drawn, and skips crossfades.

## Dependencies

- `:core:network` (API clients and session), `:core:rss` (feed reading and parsing) and
  `:core:model` (the episode ID recipe). No module depends on `:tv`. The TV's minimum Android
  version matches theirs.
- Compose for TV (`tv-material`) for focus-aware surfaces, buttons and the navigation drawer,
  standard Compose foundation for rows and grids, Navigation Compose, Media3 ExoPlayer and Session,
  Retrofit, OkHttp, kotlinx-serialization, Coil, Material icons, and the AndroidX activity and
  lifecycle libraries.
- The Google Sans Flex font file is a copy of the one in `:core:designsystem`.

## Threading / lifecycle

- `TvContainer` is application-scoped. Screens collect its flows with the lifecycle and hold no data
  of their own beyond what is on screen (selected tab, open panel, search text).
- `LiveTvLibrary` reads the API and feeds and writes its file on IO. `LiveTvPlayback` and the media
  controller run on the main thread.
- `TvPlaybackService` is a foreground media service: audio keeps playing with the app in the
  background, and the service stops when the app is closed with nothing playing.
- The TV holds a limited API session: it has no device attestation.

## Persistence & identity

- Application ID `cx.aswin.boxlore`, the same as the phone app; the release build signs with the
  phone app's key, so Google Play treats them as one app.
- The version name is the phone app's and the version code is the phone's plus 2,000,000 (the
  watch uses plus 1,000,000), both read from `app/build.gradle.kts`; a release never edits this
  module.
- Launcher activity `cx.aswin.boxlore.tv.MainActivity` in the `LEANBACK_LAUNCHER` category, with
  the banner `@drawable/tv_banner` made from the brand kit's wordmark.
- The manifest requires `android.software.leanback`, so Play offers this build only to TVs, and
  marks the touchscreen as not required.
- `files/boxlore_tv_library.json` (`TvLibraryStore.FILE_NAME`): followed shows, Up next, places,
  played, likes, history and each show's newest episode.
- Service `cx.aswin.boxlore.tv.playback.TvPlaybackService`.
- SharedPreferences `boxlore_tv_account` (`TvAccountStore.FILE_NAME`): the TV's account token.
- Install channel `tv` (`TvServer.CHANNEL`); the API session uses `:core:network`'s own file and
  Keystore alias, created fresh on the TV.

## Testing notes

Tests live in `tv/src/test` (JUnit 5, plain JVM).

| Area | Test class |
| :--- | :--- |
| Version follows the phone; launcher entry, banner, features and permissions | `TvBuildTest` |
| Your shows rows, the Home limit and the player clock | `ui/home/YourShowsRulesTest` |
| Player colours from the cover and their contrast | `ui/player/PlayerColorRulesTest` |
| Saved library rules: places, played, Daily Mix, Up next, likes; the file across restarts | `data/TvLibraryStateTest` |
| Show page lists and states, Your shows orders, when feeds are read again | `data/TvLibraryRulesTest` |
| Show notes, links and API episodes | `data/TvMappingTest` |
| Player time, sleep timer and saved places | `playback/PlaybackRulesTest` |
| Charts: loaded once per genre, followed shows marked | `data/TvChartsTest` |
| How far a list scrolls to the remote's item | `ui/KeepInViewTest` |
| What is sent to and taken from the account, and when | `cloud/TvCloudRulesTest` |
| Sign-in with a code, a fresh code, sign out, a refused token | `cloud/TvAccountTest` |

```bash
./gradlew :tv:testDebugUnitTest
```

Screens, focus, the remote and audio are checked on a TV; there is no Compose instrumentation.

## See also

- [`ARCHITECTURE.md`](../ARCHITECTURE.md)
- [`wear/README.md`](../wear/README.md)
- [`docs/TESTING.md`](../docs/TESTING.md)
