# `:feature:widgets`

## Purpose

Owns home-screen widgets built with `AppWidgetProvider` + `RemoteViews` (no Glance):

- **Now playing** — 4×2 compact card (`wrap_content` height, centered in the host cell) with a 48dp artwork tile, two-line bounded episode metadata, and previous / seek-back / play-pause / seek-forward / next. Its 100dp content height matches the provider minimum, so long titles cannot displace or clip transport controls.
- **Now playing bar** — compact 4×1 card (`wrap_content` height, centered in the host cell). Fixed 40dp art / 36dp square seek controls; titles fill the middle. Playing and empty states both fit the provider's 48dp minimum instead of stretching or clipping.
- **Playback controls** — centered square 2×2 artwork / play-pause / seek-back / seek-forward tile grid.
- **Subscriptions** — 4×3 scrollable `ListView` of subscribed shows (cap 50); same sort prefs as Library → Subscriptions → Shows. Its 180dp resize floor preserves the header, one complete row, and footer. Row tap opens `boxlore://podcast/{id}`.
- **New episodes** — 4×3 scrollable `ListView` of latest episodes from subscriptions (cap 50); same filters as Library → Subscriptions → Latest (`hideCompletedInSubs`, smart/recency sort). It uses the same 180dp safe resize floor. Row tap opens the episode deep link.

Compact playback surfaces expose seek only; the wider 4×2 adds previous/next around seek. There is no shuffle or repeat on widgets. Library list widgets do not play audio from the home screen.

Does **not** construct `PlaybackRepository` or talk to Media3 / Room directly — `:app` installs narrow ports via `configureNowPlayingWidget` and `configureLibraryWidgets`.

## Public API

- `NowPlayingWidgetReceiver`, `NowPlayingBarWidgetReceiver`, `PlaybackControlsWidgetReceiver` — picker-visible playback providers.
- `SubscriptionsWidgetReceiver`, `NewEpisodesWidgetReceiver` — picker-visible library list providers.
- `WidgetControlReceiver` — one explicit, non-exported action endpoint shared by playback providers.
- `NowPlayingWidgetDependencies` + `configureNowPlayingWidget(...)` / `WidgetPlaybackSource`.
- `LibraryWidgetDependencies` + `configureLibraryWidgets(...)` / `WidgetLibrarySource`.
- `NowPlayingWidgetSnapshotStore` — SharedPreferences file `boxlore_now_playing_widget`.
- `LibraryWidgetSnapshotStore` — SharedPreferences file `boxlore_library_widget`.
- Coordinators collect Flows, persist snapshots, render RemoteViews, and load artwork without blocking metadata.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/widgets/
  NowPlayingWidget*.kt / WidgetArtworkLoader / WidgetPalette / WidgetChrome / WidgetTextBitmapRenderer
  LibraryWidget*.kt / LibraryWidgetRemoteViewsService.kt
  logic/NowPlayingWidgetLogic.kt
  logic/LibraryWidgetLogic.kt
  actions/WidgetActionIntents.kt
  actions/WidgetActionHandler.kt
  actions/WidgetControlReceiver.kt
src/main/res/
  layout/now_playing_*.xml / playback_controls_widget.xml / library_widget_list*.xml
  layout/widget_preview_*.xml — picker previews use themed surfaces (not white tint bases),
    rounded clipped mock covers, and mock episode/show titles matching live chrome.
  xml/*_widget_info.xml
```

## Dependencies

- → `:core:model`, `:core:designsystem` (Google Sans Flex font + Appearance chrome ARGB), `:core:prefs` (`FontRoundnessAxis` / ROND, `WidgetAppearance`)
- Libraries: AndroidX Core, Material, Coil (disk cache for widget artwork), Kotlin coroutines, kotlinx.serialization JSON.
- Forbidden: feature → feature; constructing `PlaybackRepository` / `SubscriptionRepository` in this module.

## Threading / lifecycle

- Both `configure*` helpers are called once from `BoxLoreApplication` after `AppContainer` is ready.
- Coordinator collection and artwork IO run on the application `CoroutineScope` from dependencies.
- Widget control broadcasts use `goAsync()` + `Dispatchers.Main.immediate`; optimistic snapshot render happens before transport restore/actions. Failed restore/transport rolls the snapshot back from authoritative playback state.
- `onUpdate` reads snapshot prefs on a background scope then renders once (no double `requestRefresh` pass on the main thread).
- `:app`’s `NowPlayingWidgetPlaybackAdapter` hops every transport action onto `Dispatchers.Main` before touching `MediaController`.
- Dependency holders expose `instance` with an `internal` setter — only `configure*` installs them.
- `:app`’s `WidgetLibrarySourceAdapter` enriches/sorts subscribed podcasts with history + `AdaptiveCandidateScorer` to match Library → Subscriptions.
- Lettering: playback episode/podcast titles use RemoteViews `TextView`s; empty-state labels may use `WidgetTextBitmapRenderer`.
- Widget chrome defaults to the in-app Appearance theme (Theme, Background, Colors, including wallpaper colors and a chosen accent) via `WidgetChrome` baking ARGB from `boxlore_theme_fast_cache`. Settings → Appearance → Widgets can switch to **System**, which keeps `RemoteViews.setColor(resource)` Material You (`system_neutral*` / `system_accent*`). `WidgetThemeSync` re-pushes widgets on configuration changes; `:app` also refreshes when those prefs change.

## Persistence & identity

| Concern | Contract |
| :--- | :--- |
| SharedPreferences files | `boxlore_now_playing_widget`, `boxlore_library_widget` |
| Snapshot keys | `snapshot` (JSON) |
| Artwork cache dir | `{cacheDir}/widget_artwork/` |
| Receiver FQCNs | Now Playing / bar / controls / Subscriptions / New Episodes under `cx.aswin.boxlore.feature.widgets` |
| RemoteViewsService | `LibraryWidgetRemoteViewsService` (not exported; `BIND_REMOTEVIEWS`) |
| Widget info | All providers use `updatePeriodMillis=0` |

## Design provenance

The playback widget families are an independent RemoteViews implementation of the measured PixelPlayerOSS widget hierarchy. No GPL source, app assets, names, or resources are copied into boxlore. Transport icons come independently from Google Material Symbols under Apache-2.0. boxlore retains its own PolyForm Strict licensing and implementation.

## Testing notes

Hermetic JVM tests under `src/test` cover provider variants, RemoteViews inflation, library row cap (scrollable ListView), snapshot storage, mapper/update policy for playback widgets, and widget chrome (App theme vs System).

```bash
./gradlew :feature:widgets:testDebugUnitTest
```

## CI relevance

Included in merged Kover gate via root `kover(projects.feature.widgets)` once wired in `build.gradle.kts`.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`app/README.md`](../../app/README.md) — widget dependency install from `BoxLoreApplication`
