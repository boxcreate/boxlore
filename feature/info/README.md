# `:feature:info`

## Purpose

Owns the show page (Podcast Info) and the episode page (Episode Info): layout, subscribe and per-show settings, episode lists, search, multi-selection, pull to refresh, related content, cross-promotion cards, the artwork-tinted theme, and the Showcase (branded) show pages.

It does not own catalog persistence, RSS parsing, episode identity, downloads, playback, preferences storage, or routes and deep links; those belong to core modules and `:app`.

## Public API

Used by `:app` navigation:

- `PodcastInfoScreen` / `PodcastInfoViewModel`: the show page. The ViewModel's public functions are the screen's whole contract, and screen tests mock it.
- `PodcastInfoUiState`: the show page's state; navigation reads the loaded show's ID and title from `Success`.
- `EpisodeInfoScreen` / `EpisodeInfoViewModel`: the episode page.
- `EpisodeInfoUiState`: `Loading`, `Success`, `Error`, `ReadingFeed` and `NotFound`. `Success.requestedEpisodeId` is the ID the page was opened with, which deep-link autoplay matches on.
- `InfoViewModelAssembler`: `podcastInfoFactory` and `episodeInfoFactory` build both ViewModels from `InfoSharedDeps` and `PodcastInfoRouteArgs`.
- `EpisodeSort`, `ShowFeedStatus`, `DirectFeedChipState`, `InfoListeningProgressItem`: shared models.

## Show page

- **Loading.** When the show-details request fails, `logic/PodcastInfoLoadRecovery` keeps a stored row and its episodes; with no row the page shows `PodcastInfoLoadError` with a retry (`retryLoad`).
- **Repeat loads.** `PodcastInfoLoadGuard` skips a repeat `loadPodcast` for the route ID even when the page shows another ID (a linked `rss:` feed or catalog ID).
- **Episode source.** The list comes from `PodcastRepository.getShowPageEpisodes`. When `PodcastRepository.showPageSource` is `FEED`, `PodcastInfoFeedFirst` runs the page: the stored copy of the show's feed shows at once, boxlore's list stands in while the feed is read, and the list grows under the same IDs. Decisions live in `logic/ShowFeedLogic`; sources are described in [`core/catalog/README.md`](../../core/catalog/README.md).
- **Feed reads.** Search, oldest-first sort, range selection and scrolling past the stored copy read the whole archive first (`ShowFeedLogic.needsArchive`). After a failed read the page shows boxlore's list (`FALLBACK`) and reads no feed again until it is reopened (`readsFeed`). Feed-first pages never offer **Missing episodes?** (`isFeedFirstShowPage`).
- **Status line.** `ShowFeedStatusLine` shows `ShowFeedStatus` above the episodes. Reloads keep row IDs, scroll and selection as the list grows or switches source.
- **Missing episodes?** Offered only on unfollowed, non-feed-first Podcast Index shows with a valid feed URL (`EpisodeSupplementEligibility`) whose local catalog is not ready. Opting in reads the publisher feed for extras (`PodcastInfoSupplementSupport`). Once `LocalEpisodeCatalogPort.isReady` is true the list comes from the local catalog only.
- **After following.** `SubscriptionForegroundSync.requestCatalogIngest` refreshes the show's catalog on the application scope, so leaving the page never cancels it; `PodcastInfoPostSubscribeLogic` sets the status line meanwhile.
- **Pull to refresh.** `PodcastInfoPullRefreshLogic.Target` picks one path: RSS catalog, feed-first read, subscribed direct feed, opted-in direct feed, or a catalog reload. A pull while the post-follow refresh runs does nothing. Subscribed pulls use `MANUAL` to skip the automatic cooldown.
- **Hide completed.** While fewer than `HideCompletedFill.MIN_VISIBLE` unfinished episodes show and the show has more, the page loads further pages by itself; when it runs out it shows `ShowCaughtUpCard`. Search ignores this rule.
- **Selection.** Long-press starts multi-selection. Select all / older / newer read the whole show through `loadPodcastEpisodeSelectionWindow`, page by page with no cap; any failed page leaves the selection unchanged. While searching, ranges act on the results on screen (`PodcastEpisodeSelectionLogic.searchRangeEpisodes`).
- **Duplicates.** Lists never repeat an episode ID: offset pages overlap when episodes publish between requests, and rows are keyed by ID.
- **Settings.** Notifications and auto-download are independent toggles; `logic/PodcastNotificationActions` applies the repository's actual activation result. RSS-only shows confirm `RssNotificationDisclosureDialog` before turning notifications on. Home pin, tag/genre edits (`PodcastGenreEditSheet`, then `PodcastInfoFolderSyncLogic`) and per-show playback settings live in the overflow menu.
- **Renumbered shows.** A route recorded as renumbered opens as the new show, and a follow under the old number moves first. Follow state counts the shown, route and former shows (`RenumberedShowPageLogic`), so Subscribe never creates a second follow. See [renumbered shows](../../docs/EPISODE_IDS.md#renumbered-shows).
- **Helpers.** The ViewModel delegates to same-package helpers sharing one `PodcastInfoPageState`; a result for a show the page has left is dropped.

## Episode page

- **Opening by link.** A page opened with only an episode ID resolves it through `PodcastRepository.resolveEpisodeLink`. While the show's feed is read once it shows `ReadingFeed`; when nothing lists the episode it shows `NotFound`, with View show when the link named one (`EpisodeLinkStatus`). See [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).
- **Page episode.** `EpisodeLoadRequests` skips a repeat load of the same ID unless it adds the show, picks the page's episode for download state (`pageEpisode`), and fills in the show for shares (`shareEpisode`).
- **Notes.** `logic/EpisodeInfoNotesLoader` parses publisher HTML off the main thread, loads chapters and cross-promotion independently, and drops late results for an earlier request. Valid remote chapters win; otherwise chapters come from the description.
- **Shared lookup.** More from and Try these episodes share one show-details request through `logic/SharedLookup.kt` (`sharedLookup`): it runs once on first await, and failures resolve to `null` for every caller.
- **Try these episodes.** `logic/SimilarEpisodesLoader.kt` (`collectSimilarEpisodes`) loads it only while the listener's More like this switch is on (`EpisodeInfoViewModel.moreLikeThisOn`), using their content languages and region. Turning it off cancels the request; replies for another episode are dropped.
- **More from.** `logic/MoreFromEpisodeSelection.kt` picks the latest distinct episodes of the show, excluding the current one.
- **Links.** `EpisodeLinkRows`, `EpisodeLinkBrandIcon` and `EpisodeLinkPalette` place and tint resource links; brand recognition matches domains, so lookalike hosts get none.
- **Seeking.** `EpisodeInfoSeekLogic` saves progress when seeking an episode that is not the player's current item.

## Showcase pages

A few shows get a show page drawn in their own style. Each is a `BrandedShowPage` object listed in `BrandedShowPages.all`.

- **Matching.** `brandedShowPageFor` matches a show through `BrandedShowIds`: its Podcast Index ID (including a linked `rss:` copy, via `listenerShowIds`), its GUID, or its normalised feed URL.
- **What a page provides.** IDs, a `style` (`BrandedPageStyle`), a `Header` and a `Spotlight`; optionally a `subscribeMoment`, `darkOnly` or `headerKeepsTrailers`.
- **Shared behaviour.** Chips, action buttons and the episode list keep their regular order and callbacks; only colours and chip shapes change. Every page carries `BrandedSeal` in the top bar and the shared © button (`BrandedCopyrightButton`).
- **Spotlight.** `brandedSpotlightEpisode` shows the newest non-trailer episode only while sorting newest first with search closed. The list then leaves that episode out. With completed episodes hidden, a finished latest episode hides the card.
- **Subscribe moments.** A `BrandedSubscribeMoment` replaces the regular subscribe animation. It holds alerts and auto-download back until `revealDelayMillis` on its own clock, ends on the regular Subscribed state, and never plays with system animations off.
- **Art and motion.** Header art decodes off the main thread and is kept for the next visit (`rememberBrandedArt`, `BrandedArtCache`, `brandedArtNow`). Intros (`rememberBrandedIntro`) wait for `awaitPageSettled`, play once per page, and stand finished after recreation or with animations off.
- **Fonts.** Bundled fonts keep their licences under [`licenses/`](licenses/). Variable fonts go through `rememberVariableFont`, since some devices ignore Compose's font variation settings.

| Show | Package under `branded/` | Dark only |
| :--- | :--- | :--- |
| The Vergecast | `vergecast/` | no |
| Conspiracy Theories | `conspiracy/` | no |
| Waveform: The MKBHD Podcast | `waveform/` | yes |
| Crime Junkie | `crimejunkie/` | no |
| The Joe Rogan Experience | `jre/` | yes |
| Rotten Mango | `rottenmango/` | yes |
| The Daily | `daily/` | no |
| The Rest Is History | `history/` | no |
| Darknet Diaries | `darknet/` | yes |
| Serial | `serial/` | yes |
| Sherlock Holmes Short Stories | `sherlock/` | yes |
| The Desi Crime Podcast | `desicrime/` | yes |

## Theme

- `components/ArtworkInfoTheme` scopes a Material 3 scheme built from the episode or show artwork through `:core:designsystem`'s shared extractor. Monochrome, missing or failed art, or the Appearance artwork switch turned off, keeps the app scheme.
- The palette is keyed by navigation entry, so an outgoing page never recolours the active one.
- `logic/ArtworkBackdropLogic` (`artworkBackdropStrength`) limits the blurred-cover backdrop so header text keeps 4.5:1 contrast.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/info/
  PodcastInfoScreen.kt, PodcastInfoViewModel.kt   show page entry points
  PodcastInfoPageState.kt                         state the show-page helpers share
  PodcastInfoShowLoader.kt, PodcastInfoEpisodePaging.kt, PodcastInfoFeedFirst.kt, PodcastInfoPullRefresh.kt, ...
  PodcastEpisodeSelectionWindow.kt                whole-show selection window
  EpisodeInfoScreen.kt, EpisodeInfoViewModel.kt   episode page entry points
  EpisodeLoadRequests.kt, EpisodeLink*.kt
  InfoViewModelAssembler.kt
  components/   toolbars, list items, dialogs and sheets, theme, status and loading states
  logic/        pure decisions (feed, selection, refresh, notifications, renumbering, notes)
  sections/     hero, description and recommendation sections
  branded/      shared Showcase page contract and UI, one package per show
```

## Dependencies

- Project: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, `:core:rss`, `:core:prefs`.
- Libraries: Compose Material 3, Navigation Compose, lifecycle ViewModel, Coil, smooth corner rect, coroutines. Tests add `:core:testing`, Turbine, Robolectric and Compose UI test.
- No feature → feature edges. ViewModels and the assembler use ports and repositories, never `BoxLoreDatabase` directly. Analytics go through `:core:analytics`. See [`ARCHITECTURE.md`](../../ARCHITECTURE.md#dependency-direction).

## Threading / lifecycle

- UI runs on the main thread; data work uses core modules' suspend APIs.
- ViewModels are scoped to their navigation entries. Window changes keep the same ViewModel; search, dialogs and sheets, the tag/genre draft, scroll position and selection also survive recreation through saved state. See [adaptive UI and state](../../ARCHITECTURE.md#adaptive-ui-and-state).
- A restored selection waits for the first page (`PodcastEpisodeSelectionLogic.canRestoreSelection`) and keeps toolbar actions disabled while its episodes are looked up again.
- Late page-load, metadata and direct-feed results keep the current follow state (`PodcastInfoAsyncResultLogic`), so work started while following cannot undo an unfollow.
- `loadMoreEpisodes` and `toggleSort` rethrow `CancellationException`.
- Episode notes parse on `Dispatchers.Default`; superseded requests are cancelled.

## Persistence & identity

- This module owns no storage files or keys of its own.
- Keys it uses through `:core:prefs`: `home_pinned_podcast_ids` (Home pin), `content_languages` and region (Try these episodes), `has_requested_notification_permission` (`BoxcastPrefs`).
- Episode and show identity, aliases and `rss:` IDs come from core modules. Likes, progress, downloads and queue state are read through alias-aware lookups (`PlaybackRepository.likedEpisodeIds`, `historyForEpisodeLookup`, `withEpisodeAliases`), so an episode stored under several IDs shows one state. See [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).
- `:app` owns route patterns and deep links.
- Screen names: `podcast_detail`, `podcast_showcase`, `episode_detail`; show-page playback passes `podcast_detail` as its entry point. See [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Testing notes

Tests live in `src/test`: screen tests on Robolectric with a mocked `PodcastInfoViewModel`, logic tests on plain JVM. Phone goldens (`InfoPhoneGoldenTest`, `BrandedPagesPhoneGoldenTest`) are local-only; see [`docs/TESTING.md`](../../docs/TESTING.md#screenshots).

| Area | Test classes |
| :--- | :--- |
| Show page across window changes and recreation | `PodcastInfoAdaptiveTransitionTest` |
| Episode page across window changes, link states | `EpisodeInfoAdaptiveTransitionTest`, `EpisodeLoadRequestsTest` |
| Feed-first page | `ShowFeedLogicTest`, `PodcastInfoFeedFirstTest` |
| Loading, recovery, page state, analytics | `PodcastInfoLoadRecoveryTest`, `PodcastInfoLoadGuardTest`, `PodcastInfoPageStateTest`, `PodcastInfoSessionTrackerTest` |
| Pull to refresh, after following | `PodcastInfoPullRefreshTest`, `PodcastInfoPostSubscribeLogicTest`, `PodcastInfoSupplementSupportTest` |
| Search, selection, hide completed | `PodcastEpisodeSearchLogicTest`, `PodcastEpisodeSelectionLogicTest`, `PodcastEpisodeSelectionWindowTest`, `HideCompletedFillTest` |
| Notifications and auto-download | `PodcastNotificationActionsTest`, `PodcastAutoDownloadToggleTest`, `PodcastInfoNotificationLogicTest` |
| Renumbered shows | `RenumberedShowPageLogicTest` |
| Rows, progress, toolbar | `PodcastEpisodePlaybackStateLogicTest`, `SubscriptionToolbarMotionTest`, `SubscribePressShapeTest` |
| Episode notes, links, related | `EpisodeInfoNotesLoaderTest`, `EpisodeLink*Test`, `SimilarEpisodesLoaderTest`, `MoreFromEpisodeSelectionTest` |
| Theme and backdrop | `ArtworkInfoThemeHandoffTest`, `ArtworkInfoThemeLayoutTest`, `ArtworkBackdropLogicTest` |
| Showcase pages, shared | `BrandedShowPageTest`, `BrandedSpotlightTest`, `BrandedPagesAdaptiveTransitionTest`, `BrandedSubscribeMomentTest`, `BrandedAccentTest`, `EpisodeSelectionColorsTest` |
| Showcase pages, per show | `<Show>PageTest`, e.g. `VergecastPageTest`, `SerialPageTest` |

`BrandedPagesAdaptiveTransitionTest` and `BrandedAccentTest` iterate `BrandedShowPages.all`, so a newly listed page is covered automatically.

```bash
./gradlew :feature:info:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs this module's JVM tests with the project suite; phone goldens stay out of it.
- `scripts/ci/check-feature-no-boxlore-database.sh` guards direct database use in feature ViewModels and assemblers.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)
- [`:core:catalog` README](../../core/catalog/README.md)
- [`:core:designsystem` README](../../core/designsystem/README.md)
- [`:app` README](../../app/README.md)
