# `:feature:library`

## Purpose

Owns the Library tab: the hub, Subscriptions (Shows and New Episodes, with folders), Liked episodes and Downloads. It presents data from injected core dependencies and does not own Listening history (`:feature:history`), show or episode details (`:feature:info`), download settings (`:feature:settings`), download workers, playback services, ranking, folder storage or route registration.

## Public API

- `LibraryScreen`: the hub on the app's `library` route, with cards for Subscriptions, Liked Episodes, Downloads and Listening History.
- `SubscriptionsScreen`: Shows and New Episodes on `library/subscriptions?tab={tab}`. `LocalLastSeenEpisodes` supplies the last-seen episode per show for NEW badges.
- `LikedEpisodesScreen`, `DownloadedEpisodesScreen` and `DownloadedShowEpisodesScreen` (wrappers over `downloads/`).
- `DownloadedEpisodeEntity.toEpisode()` / `toPodcast()`: map a download to domain models, keeping its own show ID and name and its chapter and transcript links.
- `LibraryViewModel` and `LibraryUiState`: one ViewModel class for every Library destination, built by the app from core repositories, `AdaptiveCandidateScorer` and an optional `FolderRepository`.
- `SubscriptionSort`, `DownloadsSortOrder` and `ShowSortOrder`: sort choices shown by the screens.
- `PlayAllFab`: the shared Play All button.

## Subscriptions

- `tab=0` opens Shows and `tab=1` New Episodes. Without `tab` (the hub, **Open app to** Subscriptions) the app resolves Appearance **Default tabs** (`SubscriptionsDefaultTab`); an explicit `tab` wins (Home, widgets).
- The tab switcher is designsystem's `DiscoveryTabSwitcher`, in the top bar or as a floating selector (`SubscriptionsTabStyle`). Play All keeps clear of the floating selector and the player.
- Shows is a grid or a list; the choice persists. Genre chips (`SubscriptionChip`) filter both tabs; genre labels and icons come from `SubscriptionGenreCatalog`, which mirrors Explore without importing it.
- Smart sort uses the shared Your shows score (`RankingObjective.YOUR_SHOWS` on `RankingSurface.LIBRARY`), then title (`SubscriptionSmartOrderLogic`). Most listened counts history under every ID of a show (`sortedByListening` in `:core:database`).
- Each show's latest-episode status reads `PlaybackRepository.historyForEpisodeLookup`, so an episode stored under several IDs shows its progress whichever ID the show lists; see [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).
- Manual order: the first drop seeds the saved order from the visible list and switches the sort to Manual; new shows append A–Z; unsubscribing drops the ID from the manual order and Home pins (`SubscriptionManualOrderLogic`).
- The Shows grid and list never receive the same show twice: the saved order and subscription rows are de-duplicated before they reach the keyed lists.
- NEW badges use the shared `isLatestEpisodeNew` rule from `:core:model`.

### Folders

- Folder pages draw over Subscriptions without routes of their own: the open folder (`SubscriptionFolderPage`) and three editors, New folder (`NewFolderFlow`), Edit folder (`EditFolderPage`) and Auto-organize (`AutoOrganizePage`).
- `FolderPagesState` (saved state) holds which pages are open and every unsaved draft, so they survive rotation, resizing, folding and recreation. `SubscriptionFolderPages` hosts them and reports changes through `FolderPagesActions`, which `SubscriptionsScreen` implements with `LibraryViewModel`.
- The editors use `BoxloreDialog`: full screen on phones, a framed panel on tablets. Tapping outside a panel never discards an unsaved draft.
- Folder rules (enforced by `FolderRepository` in `:core:catalog`): a show can be in many folders; filed shows leave the main grid; a genre-linked folder gains and loses shows of its genre; deleting a folder never unsubscribes.
- Auto-organize groups by custom genre, else the first genre, and fills existing folders of that genre. `AutoOrganizePreviewLogic` mirrors the repository's grouping for the preview.
- Sorting has three levels in `SubscriptionSortSheet`: shows outside folders, folders (`FolderInterSort`) and shows inside folders (`FolderIntraSort`). Reorder mode (`SubscriptionReorderBar`) never drags across sections.

### New Episodes

- Rows show each show's latest episode. Opening the screen calls `SubscriptionForegroundSync.requestRefresh` (`:core:catalog`, reached through `SharedAppDependenciesHolder`); pull to refresh calls `refreshNow`, which joins a pass already running.
- Tapping a row plays it and the visible rows after it; Play All starts at the first unfinished row. Both snapshot the visible order at tap time and skip finished rows (`latestPlaybackEpisodes`).
- Swiping a row queues it or marks it played (both also TalkBack actions). Undoing a queue add removes it with `deferSkipSignal`, so it is not a Smart Queue rejection.
- Marked played is held in `LibraryViewModel.pendingPlayedEpisodeIds` while its Undo shows, then saved with `markAllEpisodesCompleted` however the message closes, because saving clears the listening position. Pure rules live in `LatestSwipeLogic`.

## Downloads and Liked

- Downloads groups by show, sorts by `DownloadsSortOrder`, and supports multi-select delete (checklist in the top bar, or long-press a row).
- Removing a single downloaded episode asks first through designsystem's `RemoveDownloadConfirmationDialog`.
- The Smart Downloads dashboard card (`SmartDownloadsDashboardCard`) shows status and a sync action; its settings live in `:feature:settings`.

## Adaptive layouts

Window gates and state retention follow [`ARCHITECTURE.md#adaptive-ui-and-state`](../../ARCHITECTURE.md#adaptive-ui-and-state). Library-specific behaviour: `TabletLibraryHub` lays the hub out in bounded columns, `LibraryPlaybackLayout` gives Play All its own row on tablets so it never covers episode controls, and folder pages keep one owner across window changes (`FolderPagesAdaptiveTest`).

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/library/
  LibraryScreen.kt, TabletLibraryHub.kt     # hub
  LibraryViewModel.kt                       # state, sorting, folders, New Episodes actions
  SubscriptionsScreen.kt
  LikedEpisodesScreen.kt
  DownloadedEpisodesScreen.kt               # public wrappers over downloads/
  LibraryPlaybackLayout.kt, PlayAllFab.kt
  FolderEditLogic.kt                        # folder save rules shared by the editors
  downloads/                                # Downloads list, a show's downloads, Smart Downloads card, models
  logic/                                    # SubscriptionManualOrderLogic, SubscriptionSmartOrderLogic
  subscriptions/                            # tabs, rows, chips, sort sheet, reorder, context menus,
                                            #   folder pages and editors, New Episodes swipe, refresh
                                            #   and playback queue logic
```

## Dependencies

- → `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:designsystem`, `:core:prefs`, `:core:analytics`, `:core:ranking`.
- Libraries: Compose, Material 3, Navigation, lifecycle, Activity Compose, Coil, calvin reorderable; tests add JUnit 5, Turbine and Robolectric through `:core:testing`.
- Feature modules never depend on other features, and never call PostHog directly; analytics go through `:core:analytics`.

## Threading / lifecycle

- `LibraryViewModel` is scoped to each Library navigation entry; repositories, playback, downloads, prefs and the scorer are application-scoped instances from app wiring.
- The Library state pipeline (status enrichment, scoring, sorting) runs on `Dispatchers.Default`; database, download and subscription work goes through injected suspend APIs.
- On start the ViewModel runs auto-organize when it is on, else syncs genre-linked folders.
- UI state is exposed as flows and collected by Compose on the main thread.

## Persistence & identity

- This module owns no storage files. Keys it reads and writes live in `:core:prefs`: `subscription_sort`, `subscription_manual_order`, `subscription_folder_sort`, `subscription_intra_folder_sort`, `subscriptions_grid_view`, `hide_completed_in_subs`, `subscriptions_tab_style`, `home_shortcuts_in_library`, `auto_organize_folders_enabled`, `auto_organize_folder_size`, `auto_organize_tile_covers` and `home_pinned_podcast_ids`.
- Folders and their members are stored by `:core:catalog` and `:core:database`; download entries and workers by `:core:downloads`; media IDs by `:core:playback`.
- Routes `library`, `library/subscriptions?tab={tab}`, `library/liked`, `library/downloads` and the `boxlore://library/subscriptions` deep link are registered in `:app` and must stay stable.
- Analytics: hub and destination views send `library_destination_viewed`; folder pages and sheets send `screen_viewed`. Events are listed in [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md).

## Testing notes

Hermetic JVM tests live in `feature/library/src/test`, using fakes from [`:core:testing`](../../core/testing/README.md) and Robolectric for Compose.

| Area | Tests |
| :--- | :--- |
| Sorting and order | `SubscriptionSortTest`, `SubscriptionManualOrderLogicTest`, `SubscriptionSmartOrderLogicTest`, `SubscriptionReorderControlsTest` |
| Genres, filters and tabs | `SubscriptionFilterLogicTest`, `SubscriptionsTabStyleTest` |
| Folders | `FolderEditLogicTest`, `SubscriptionFolderLayoutLogicTest`, `FolderSummaryLogicTest`, `AutoOrganizePreviewLogicTest`, `FolderFlowLogicTest` |
| New Episodes | `LatestEpisodesDisplayLogicTest`, `LatestPlaybackQueueLogicTest`, `LatestSwipeLogicTest`, `LatestEpisodeActionsTest` |
| Downloads | `DownloadModelsTest` |
| Adaptive layouts | `FolderPagesAdaptiveTest`, `LibraryPlaybackLayoutTest` |

`SubscriptionsScreensCaptureTest` and `LibraryTabletAuditCaptureTest` are local review captures that skip unless asked for; see [`docs/TESTING.md#screenshots`](../../docs/TESTING.md#screenshots).

```bash
./gradlew :feature:library:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Library JVM tests in the features shard and includes the module in merged coverage verification.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`docs/ANALYTICS_EVENT_GLOSSARY.md`](../../docs/ANALYTICS_EVENT_GLOSSARY.md)
- [`:app` README](../../app/README.md)
- [`:feature:history` README](../history/README.md)
- [`:core:catalog` README](../../core/catalog/README.md)
- [`:core:downloads` README](../../core/downloads/README.md)
- [`:core:prefs` README](../../core/prefs/README.md)
