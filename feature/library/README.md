# `:feature:library`

## Purpose

Owns Library presentation: hub, subscriptions, liked episodes, downloaded episodes, and show details. It does not own history presentation (decoupled into `:feature:history`), download settings (decoupled into `:feature:settings`), download workers, playback services, ranking storage, catalog persistence, or app route registration.

## Public API

- Subscriptions, folder pickers, downloads and liked episodes use shared artwork loading instead of raw Coil consumers. Episode art falls through to available show art on failure; show surfaces remain show-first, with the branded shared fallback.

- Cards and episode/subscription rows use steady Material feedback, retaining long-press selection and drag ownership. Custom Play All/Delete All/explore controls use restrained control feedback. The Smart Downloads sync button has one native Material click handler and indication.

- `LibraryScreen` and `LibraryViewModel`. When Appearance **Cleaner Home** is on, the hub top bar shows Settings and Feedback in the same shared `TopBarUtilityActions` capsule Home normally uses. Styling, 48dp touch targets and press feedback are consistent; Library retains its existing top-bar scrolling and click/long-press actions.
- `SubscriptionsScreen`, `LikedEpisodesScreen`, and `DownloadedEpisodesScreen`. `downloads/DownloadModels.kt` maps downloaded episode entities to domain episodes (including `chaptersUrl` and `transcriptUrl` for offline chapter/transcript display). Downloaded show episode 3-dots menu uses `RemoveDownloadConfirmationDialog` to protect against accidental file deletion.
- Folder pages drawn over Subscriptions (no new routes): the open folder (`SubscriptionFolderPage`), and three editors: New folder (`NewFolderFlow`), Edit folder (`EditFolderPage`) and Auto-organize (`AutoOrganizePage`). `FolderPagesState` holds which are open and every unsaved draft; `SubscriptionFolderPages` hosts them and reports changes through `FolderPagesActions`, which `SubscriptionsScreen` implements with `LibraryViewModel`. `AddToFoldersSheet` ticks the folders a show outside folders belongs in. `AutoOrganizeConfirmationDialogs` keeps only the turn-off question.
- Downloads multi-select: checklist in the top bar, or long-press a show (hub) / episode (show list) to enter selection with that row checked, then delete several at once.
- `PlayAllFab` and library UI helpers.
- Floating Subscriptions tabs and all library Play All controls follow designsystem's shared adaptive-player placement offset, including New Episodes with either Top or Floating tabs. Play All keeps its spacing above the floating selector. Existing system-bar and list clearances stay stable while the player morphs, and Classic retains its existing placement.
- Missing or failed show/episode artwork uses designsystem's shared B-and-microphone fallback in grids, folders and rows; titles alongside the artwork remain unchanged.
- Library UI uses centralized Google Sans Flex weight tokens from `:core:designsystem`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/library/
  DownloadedEpisodesScreen.kt
  FolderEditLogic.kt              — folder save rules shared by the editors (trim, icon fallback, genre link)
  LibraryScreen.kt
  LibraryViewModel.kt
  LikedEpisodesScreen.kt
  PlayAllFab.kt
  SubscriptionsScreen.kt
  subscriptions/
    AddToFoldersSheet.kt          — tick the folders a show outside folders belongs in; genre folders shown but not tickable
    AutoOrganizeConfirmationDialogs.kt — the turn-off question, with what turning off does and doesn't do
    AutoOrganizePage.kt           — Auto-organize page: off (preview + look + Turn on) or on (switch, genre folders, look, Apply to all)
    AutoOrganizeParts.kt          — preview list and look choice shared by the page and New folder › Auto
    AutoOrganizePreviewLogic.kt   — what auto-organize would make (mirrors the repository's grouping) and the look it gives each folder
    EditFolderPage.kt             — Edit folder page and its saved draft (`EditFolderDraft`)
    FolderFlowParts.kt            — top bar with steps, bottom action bar, name field and icon grid for the folder pages
    FolderLookPicker.kt           — the four looks with "Tap up to N directly", tile icon-or-covers choice, live previews
    FolderPagesState.kt           — open folder, open editor and drafts, saved across resize, fold and recreation
    FolderSummaryLogic.kt         — folder and auto-organize summaries, look names, Shows-grid section labels
    LatestEpisodeSwipeRow.kt      — New Episodes row swipe (queue / mark played) with TalkBack actions
    LatestPullToRefresh.kt        — New Episodes pull to refresh, including on its empty states
    LatestEpisodeActions.kt       — New Episodes pull to refresh (one at a time), swipe to queue and to played, and their Undo
    LatestSwipeLogic.kt           — swipe mapping, queue outcome messages, held-played row filtering
    FolderShowsSelectionSheet.kt  — Multi-select bottom sheet with search and pre-checked members for adding shows to folders
    MoveToFolderSheet.kt          — Folder picker bottom sheet with show counts and new folder action
    NewFolderDraft.kt             — New folder modes (Shows / Genre / Auto), steps and saved draft
    NewFolderFlow.kt              — New folder pages: what goes in it, name (Shows), look; Auto turns on auto-organize
    NewFolderPickSteps.kt         — show picker with search and the one-genre step with genre pills
    ShowsFolderGridItems.kt       — folder cards and the "Folders" / "Your shows" labels in the Shows grid
    SubscriptionContextMenuSheet.kt — Contextual press-and-hold sheet for folders, folder shows, and root shows
    SubscriptionFolderCards.kt    — Enlarged & compact folder card layouts with direct-clickable covers, overflow badge, and scroll-safe header clicks
    SubscriptionFolderPage.kt     — the open folder: header, where its shows come from, order chip, covers, reorder bar
    SubscriptionFolderPages.kt    — hosts the open folder (in the screen) and the editors (shared boxlore dialog)
    SubscriptionFolderLayoutLogic.kt — Folder partitioning, display-size calculations, and inter/intra folder sort resolution
    SubscriptionGenreCatalog.kt   — genre label/icon map mirrored from Explore and resolved against custom podcast genre overrides
    SubscriptionLatestLogic.kt    — Latest episodes sorting, smart scoring, and chronological header grouping
    SubscriptionListRowParts.kt   — list artwork, title column, pin badge
    SubscriptionReorderControls.kt — Scoped reorder mode state and floating bar with Save/Exit controls and sort notices
    SubscriptionRows.kt           — grid cards (shared boxlore mark on broken art), list/latest rows, date headers
    SubscriptionSortSheet.kt      — Sort and organize sheet: your shows, order of folders, shows inside folders, and the Auto-organize row
    SubscriptionTabContents.kt    — Shows grid/list + New Episodes catch-up list (Play All FAB); calvin reorderable on unfiltered Shows and folders
    SubscriptionTabs.kt           — Shows|New Episodes switcher
    SubscriptionsFilterRow.kt     — New folder button and genre chips; `SubscriptionChip` / `SubscriptionActionChip` (Explore Top tab chip look)
    SubscriptionsTabSelectorFab.kt — Floating segmented FAB pill indicator for Shows/New Episodes
  logic/
    SubscriptionManualOrderLogic.kt — Manual sort apply / drag move / drop; skips non-podcast drag keys
    SubscriptionSmartOrderLogic.kt — Smart sort: score desc, then title
```

## Subscriptions UX contracts

- Tapping a New Episodes row plays that row and the remaining visible rows in their current Smart/Chronological/filter order. Play All starts with the first unfinished visible row. Both actions snapshot the list at tap time, skip completed upcoming rows, preserve each show’s metadata, and use `onPlayEpisodes`; a selected completed row can still be replayed. The app shell starts a fresh context queue, with Smart Queue fallback only after it finishes.
- Play All remembers its snapshot by the displayed list, so unrelated player-state recompositions do not repeat episode copies and deduplication. Chronological mode follows publication-date display order; Smart mode retains the listener's chosen ranking.

- Route: `library/subscriptions?tab={0|1}` (`0` = Shows, `1` = New Episodes). Omitting `tab` (Library hub, Open app to Subscriptions) uses Appearance **Default tabs** (`shows` / `new_episodes`). Explicit `tab` still wins (Home Latest, widgets).
- Top and Floating tabs both use designsystem `DiscoveryTabSwitcher` with Shows/New Eps labels and the existing new-episode badge. Selection callbacks, saved style/default preferences, search/reorder visibility and player docking remain page-owned. The control has a 48dp minimum height that grows with text scaling; Play All clearance uses the same metric.
- Tab presentation style: Appearance setting allows switching Subscriptions tab style between **Top** (header tab switcher, default) and **Floating** (bottom pill FAB mirroring Explore). When Floating is active, top header tabs are omitted, and the Play All FAB on New Episodes elevates above the floating tab selector with spring animation.
- Shows: image-only 3-column grid (default) or richer list, saved as `subscriptions_grid_view` through `:core:prefs` so the choice survives the app being closed (it used to live only in saved instance state and reset after a swipe-away from recents); Explore-style `PillFilterChip` genres **with icons**; subscription folders partitioned into pinned full-width shelves (`Shelf 3×1`, `Panel 3×2`, `Showcase 3×3`, etc.) and compact 1×1 folders (both pinned at top of the library), and unfiled shows below; 3-tier granular sorting via `SubscriptionSortSheet` with independent controls for shows outside folders, folders arranged relative to each other at the top (`FolderInterSort`), and shows inside folders (`FolderIntraSort`), plus press-and-hold repositioning tips; folder Smart Sort uses Decayed Top-3 diminishing returns (`Top + 0.5 * 2nd + 0.25 * 3rd`) to eliminate both hoarder bias and dilution penalties; dual folder-level and cover-level NEW badges (1×1 compact folder shows elevated primary-framed badge when any show has new episodes; enlarged pinned folders badge visible show covers directly and display a header badge when new episodes hide within the +N overflow cluster; expanded dialog badges each show individually); in Manual sort mode, unfiled podcasts can be long-pressed and dragged to reorder; sort icon in top bar opens `SubscriptionSortSheet` on Shows tab and sort menu on New Episodes tab; Smart shares Home Your Shows' deterministic score (log-normalized listening signals plus a bounded three-day subscribe-recency floor, not a front-of-list slice); long-press-drag artwork on the unfiltered Shows list (genre All, empty search) to reorder — covers keep the usual shrink-bounce on tap and stay rounded while dragging; the first drop seeds `subscription_manual_order` from the visible list and switches `subscription_sort` to Manual; drag state stores ordered ids and re-reads current `Podcast` objects so artwork/episode badges stay fresh; new shows append A–Z at the end of Manual; unsubscribe drops that id from Manual order (and Home pins); a circular pin badge marks Home-pinned shows; NEW badge uses shared `isLatestEpisodeNew` (Room `rssHasNewEpisodes` for true-RSS and PI direct-feed tips, else 48h); broken/missing art uses the shared boxlore B-and-microphone mark.
- The Most listened sort counts history rows through `:core:database` `sortedByListening`, so an RSS show the listener switched to from a Podcast Index show counts that show's rows (`Podcast.listenerShowIds`; decision D8).
- Each show's latest-episode status (unplayed, in progress, completed) is looked up through `PlaybackRepository.historyForEpisodeLookup`, so an episode stored under several IDs shows its progress whichever ID the show lists; lists and ranking still count it once through `getAllHistory` ([`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md) §5).
- New Episodes: latest episode per show from Room `latestEpisode` (PI tip, or publisher-feed tip when opted into **Missing episodes?**); the screen calls `SubscriptionForegroundSync.requestRefresh` on appear so this is a live `/sync` (including **Open app to** Subscriptions), not a cache-only paint from a previous session. Same icon genre pills; Smart vs Chronological sort plus **Hide played episodes** checkbox in the Sort menu; denser play rows; quieter sticky date headers; Play All FAB.
- New Episodes pull to refresh (`PullToRefreshBox`, also on its empty states) calls `SubscriptionForegroundSync.refreshNow`, which skips the five-minute cooldown, joins a pass already running instead of starting another, and keeps the spinner until that pass ends.
- New Episodes row swipes (`LatestEpisodeSwipeRow`, same `SwipeToDismissBox` + `reset()` pattern as Downloads): start to end adds to the queue, end to start marks played; both are TalkBack custom actions too. A drag that starts on a row belongs to the row, so the Shows / New Episodes pager swipes from the header, chips and gaps (tabs still switch by tap). Queueing saves at once and its Undo removes it with `deferSkipSignal` so an undone add is not a Smart Queue rejection; the message says when the episode was already queued or the queue is at `PlaybackRepository.QUEUE_LIMIT`. Played is held in `LibraryViewModel.pendingPlayedEpisodeIds` while its Undo shows (the row reads as played and leaves at once with Hide played) and saved with `markAllEpisodesCompleted` only when the message closes without Undo, because saving clears the listening position; it is saved however the message closes, including leaving the screen. Pure rules live in `LatestSwipeLogic`.
- The auto-organize off dialog says what really happens: no new genre folders, and the genre folders already made keep collecting their genre (genre sync is unchanged).
- Folder rules are unchanged by the redesign: a show can be in many folders; filed shows leave the main grid; genre-linked folders add and drop shows of their genre on Library open; a folder named after a genre links to it; auto-organize groups by custom genre, else the first genre, and fills existing folders of that genre; Technology is saved as Tech; deleting a folder never unsubscribes.
- Look: every chip in Subscriptions (genre row on both tabs, One genre, Edit folder, Sort and organize) is `SubscriptionChip`, which mirrors Explore's Top tab chips: filled, no border, 48dp, primary container when picked. The New folder button is a 48dp filled circle beside them; order and Add shows are `SubscriptionActionChip`. Surfaces are solid and borderless (folder cards, look cards, choice cards); New folder's mode switch is the app's `DiscoveryTabSwitcher`; its labels stay one word (Shows, Genre, Auto) so they fit three equal tabs on narrow phones. A folder that fills itself from a genre shows the auto icon (`AutoFolderMark`), never "Fills from …" text. New folder's Genre and Auto options say once, as a plain line with that icon (`AutoAddNote`), that shows followed later are added automatically (`genreAutoAddNote`, `AUTO_ORGANIZE_NOTE`); Auto-organize uses the same line.
- Shows grid: folder names, the hanging name pill on tiles, NEW banners and tags, +N overflow and the New folder button keep their places. When pinned folders all come first, a "Folders" label sits above them and a "Your shows" label above the rest; neither shows while folders are being dragged. Pinned folder headers show the folder icon, the show count (with the auto icon on genre folders) and a "N new" pill.
- Open folder: a page over the Shows tab, part of the screen, so Back from a show returns to it and the mini player stays reachable. It shows the folder's icon, name and show count (auto icon on a genre folder), an order chip that opens Sort and organize, Add shows on a folder you fill (a genre folder's genre is changed from Edit), and the covers; press and hold a cover for its menu. Back ends reordering first, then closes the folder.
- New folder (three steps): what goes in it — Shows (pick them, then name, then look), Genre (one genre: pills with counts, name filled in from the genre, then look), or Auto, which turns on auto-organize with "Let boxlore choose" or one fixed look, or offers Manage when it is already on. Looks say "Tap up to N directly"; the rest go behind +N. A name that is a genre is marked as an auto folder. Started from Add to folder or Move to another folder, the show arrives picked; from Move, it leaves its old folder only if it stays picked.
- Edit folder: name, where shows come from (genre pills for a genre folder, or its shows with remove and Add shows), look, icon and Delete folder. Name, genre, look and icon save with Save; removing a show takes effect at once and keeps it in the library.
- Auto-organize page (from Sort and organize, or New folder › Manage): off shows what it would make and a look, then Turn on; on shows a switch, the genre folders it keeps filled (tap one to open it in place of this page), the look for new genre folders, and "Apply to all N genre folders", which applies the look shown. Turning off asks first.
- Add to folder on a show outside folders opens `AddToFoldersSheet`: tick any folders you fill (shows can be in several), genre folders are listed with the auto icon but can't be ticked; a show joins one by its genre. Move to another folder on a show in a folder keeps `MoveToFolderSheet`.
- Folder pages and adaptive layouts: the open folder is part of the screen and covers the tabs (screen readers skip what's under it, and the floating tab selector hides). The three editors use `BoxloreDialog`: full screen on phones, so their own bottom bar clears the navigation and mini player, and a 640dp framed panel on tablets; the same content moves between the two, so drafts, steps, focus and scroll survive. Which pages are open and all drafts live in `FolderPagesState` (saved state), so they survive rotation, split-screen, folding and recreation. Back in an editor goes one step back (New folder) or closes it; tapping outside a tablet panel closes only the open folder or Auto-organize, never an unsaved draft.
- Search stays in the top bar without removing the tab switcher. No glance/summary strip. Genre row is pills-only (sort/hide are not on that row).
- Context menus & Scoped Reorder Mode: Long-pressing anywhere on a folder card (including member show preview covers) triggers the folder context menu (`Edit folder`, `Add shows to folder`, `Reorder folders`, `Delete folder`). Long-pressing a podcast inside the open folder triggers the folder podcast context menu (`Remove from folder`, `Move to another folder`, `Reorder shows in folder`, `Unsubscribe`). Long-pressing an unfiled podcast in the root grid triggers the root podcast context menu (`Add to folder`, `Reorder shows`). Entering reorder mode displays a floating `SubscriptionReorderBar` with `Save` and `Exit` buttons and a notice that sort will switch to Manual upon saving. Strict boundary protection prevents dragging across sections (folders cannot drag into shows, and vice versa).
- Genre icon/label catalog in `subscriptions/SubscriptionGenreCatalog.kt` mirrors Explore (no feature→feature import).
- The Shows grid and list never receive the same podcast twice: saved manual order and subscription rows are de-duplicated before they reach the keyed lazy lists.

## Dependencies

- Project dependencies: `:core:model`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:designsystem`, `:core:analytics`, and `:core:ranking`.
- Libraries: Compose, Navigation, Activity Compose, lifecycle ViewModel/runtime, Coil, calvin reorderable, Turbine / JUnit for JVM tests. The unused adaptive navigation-suite dependency is removed; app-shell window information comes through `:core:designsystem`.
- Reverse-edge rule: feature modules must not depend on other feature modules or create local repository graphs.

## Threading / lifecycle

- ViewModels are scoped by app navigation.
- Download, playback, catalog, and ranking dependencies are application-scoped instances supplied by app wiring.
- UI runs on the main thread; history, download, and subscription operations use injected suspend APIs.

## Persistence & identity

- This module owns no storage files or stable keys; it reads/writes `subscription_sort`, `subscription_manual_order`, `subscriptions_grid_view`, `auto_organize_folders_enabled`, `auto_organize_folder_size`, `auto_organize_tile_covers`, and Home pin ids through `:core:prefs`.
- Download cache entries and worker identities are owned by `:core:downloads`.
- Playback media IDs are owned by `:core:playback`.
- Catalog and subscription identities are owned by catalog, RSS, and database modules.

Artwork-only subscription headers try usable fallback candidates even when the primary show URL is blank; liked episodes use their associated show artwork after episode artwork fails.

## Testing notes

- The tablet Library hub uses two bounded columns when space allows. Latest/download episode lists reserve a separate Play All action row on tablets, preventing overlap with individual playback controls; compact overlays retain their original geometry. Tablet list bottom padding accounts for that reserved row. Context menus, selection, sorting, playback queues and folder callbacks retain their existing owners.

- Tablet Shows grids pack by content-pane width. Poster/folder surfaces use shared maxima; hub cards, downloaded/liked episode lists and subscription episode lists stay bounded. Reordering, folder slots, playback queues and ordering remain unchanged.

- `LatestPlaybackQueueLogicTest` checks visible suffix order, publication-date chronology, Smart/filter order, completed-row handling, Play All, missing/duplicate rows, mixed-show metadata, and opaque RSS episode IDs.

- Unit tests live under `feature/library/src/test`.
- History date-picker conversion uses UTC midnight millis (`ZoneOffset.UTC`) so the selected calendar day is preserved.
- History back navigation exposes `history_back` for TalkBack.
- `HistoryFilterTest` covers history filtering behavior.
- `SubscriptionSortTest` covers subscription ordering, including Manual name round-trip.
- `SubscriptionManualOrderLogicTest` covers seed/move/append/drop, ignoring unknown ids, and skipping non-podcast drag keys (genre header).
- `SubscriptionSmartOrderLogicTest` covers Smart score-then-title order without a recency band.
- `SubscriptionFilterLogicTest` covers genre extract/filter (including hybrid custom tag frequency-first priority over catalog genres), custom icon resolution, sort labels, and chronological header buckets.
- `DownloadModelsTest` covers download entity mapping, size/date formatting, and long-press multi-select (`longPressDownloadSelection`).
- `FolderEditLogicTest` covers `FolderDisplaySize` placement properties (`isPinnedToTop`, `placementLabel`), the 3×1 SHELF size a new folder starts with, optional icon fallback, genre quick-fill token extraction, auto-sync linked genre fallback, and `FakeFolderRepository` lifecycle.
- `SubscriptionFolderLayoutLogicTest` covers folder slot allocation (visible covers + overflow count) across all display sizes, subscription partitioning into pinned, compact, and unfiled groups, genre-filtered folder visibility, in-folder sort matching and dialog show resolution (`resolveSortedFolderShows`), pinned and compact folder sort ordering, unified grid reorder item generation, and dual folder/overflow NEW episode badge logic (`hasFolderOverflowNew`, `hasAnyFolderShowNew`).

```bash
./gradlew :feature:library:testDebugUnitTest
```
- `SubscriptionReorderControlsTest` covers repeated ids in the saved order and in the subscription rows.
- `LatestSwipeLogicTest` covers swipe direction mapping, already-queued versus full-queue messages, and held-played rows with Hide played on and off.
- `LatestEpisodeActionsTest` covers a second pull not starting another refresh, the spinner clearing after success and failure, a swipe to played saving nothing until its Undo closes, Undo winning over a later commit, a committed swipe held until the list shows it (or the wait ends), and queue add, already queued, full queue and Undo without a turned-down signal.
- `FolderSummaryLogicTest` covers folder and auto-organize summaries, genre folders, Shows-grid section labels (only when pinned folders come first, never while dragging), look names and "Tap up to N directly".
- `AutoOrganizePreviewLogicTest` covers the preview's grouping (custom genre first, placeholder genre left out, existing folders by link or name, Technology as Tech), the size boxlore picks, Apply to all, and unknown saved looks.
- `FolderFlowLogicTest` covers New folder steps, picks, genre naming and icons, Back, save params (only One genre links a genre), Edit folder drafts, Add to folders changes (genre folders untouched), saved-state round trips for both drafts and `FolderPagesState`, and closing pages when a folder is deleted or opened from Auto-organize.
- `SubscriptionsScreensCaptureTest` (local only, runs with `BOXLORE_SUBS_CAPTURES=1`) renders the Shows tab, open folders, every New folder step, Edit folder, both Auto-organize states and Sort and organize at phone size in light and dark into `build/reports/subscriptions-captures/`; it records nothing and compares nothing.
- `FolderPagesAdaptiveTest` (Robolectric) keeps one live owner while the window crosses 599/600dp wide, 479/480dp tall, the 840dp split and back to phone: the New folder step, picks and name, an unsaved Edit folder name, the Auto-organize look and Add to folders ticks survive; the open folder stays open; recreation keeps the New folder draft; Back, Close, Create, Save, Turn on auto-organize and Done still act afterwards, and Back ends reordering before closing the folder. Device checks on a phone are separate; no tablet or foldable hardware run is part of this.

## CI relevance

- `unit-tests.yml` runs Library JVM tests with the project suite.
- Downloads settings visual baselines live under `screenshots/baselines/` (optional local Roborazzi; not CI-gated).

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:downloads` README](../../core/downloads/README.md)
- [`:app` README](../../app/README.md)

Tablet subscription/folder menus use the shared dialog adapter. Secondary click reuses the existing guarded long-press callbacks, including reorder/drag restrictions; nested targets retain one menu owner. Phone card/menu calls retain their native paths.

Local-only LibraryTabletAuditCaptureTest renders Liked, empty Liked, Downloads and a downloaded show in both tablet orientations and themes using mocked flows. It performs no downloads or playback and writes no phone baselines.

Liked empty-state actions and downloaded-show headers share the same bounded tablet column as their loaded lists. Offline capture fixtures provide the same Material content-color host as the application.
