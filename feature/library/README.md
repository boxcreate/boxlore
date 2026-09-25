# `:feature:library`

## Purpose

Owns Library presentation: hub, history, subscriptions, liked episodes, downloaded episodes, and show details. It does not own download settings (decoupled into `:feature:settings`), download workers, playback services, ranking storage, catalog persistence, or app route registration.

## Public API

- `LibraryScreen` and `LibraryViewModel`. When Appearance **Cleaner Home** is on, the hub top bar shows Settings and Feedback (the same shortcuts Home normally owns).
- `HistoryScreen` and `HistoryViewModel`. History uses a compact period-first hierarchy: a full-width listening-time hero, content-sized highlights, listening-pattern cards, then the filtered episode timeline. Highlights use a compact top-show row and explicit two-column metric grid instead of fixed-height carousel pages; paired cards match the tallest cell in their row, while an odd final metric spans the row as a featured card with a large tonal icon. The hero flows vertically so narrow phones do not sacrifice labels or leave split-column dead space; timeline rows allow longer episode/show copy. The one-time tracking-reset notice no longer appears.
- `SubscriptionsScreen`, `LikedEpisodesScreen`, and `DownloadedEpisodesScreen`. `downloads/DownloadModels.kt` maps downloaded episode entities to domain episodes (including `chaptersUrl` and `transcriptUrl` for offline chapter/transcript display). Downloaded show episode 3-dots menu uses `RemoveDownloadConfirmationDialog` to protect against accidental file deletion.
- `AutoOrganizeConfirmationDialogs`: Confirmation dialogs for enabling/disabling auto-organize into folders. The enable dialog allows users to choose their preferred folder display size (default 3×1 Shelf), conditionally pick 1×1 cover style (Folder Icon vs Podcast Grid), and reminds users how to edit show genres using the genre pill. The disable dialog explains that existing folders and contents remain safe and intact.
- `FolderEditSheet` and `FolderEditComponents`: Subscription folder creation and edit sheet featuring a streamlined hierarchy, slim actionable auto-organize library nudge (`AutoOrganizeSlimNudge`) for instant creation-time grouping, prominent name input with real-time keyword suggestions, preset icons (prioritizing subscribed library genres), automatic icon switching on typing exact genre/topic matches, visual size selector cards for display sizes (`1×1 Compact`, `3×1 Shelf`, `3×2 Panel`, `3×3 Showcase`), contextual 1×1 cover display selector (`[ Folder Icon ]` vs `[ Podcast Grid ]`), compact optional icon picker row, LazyRow-powered horizontal scrolling with crisp edge stops, auto-sync with genre tags, and quiet beta feedback footnote.
- Downloads multi-select: checklist in the top bar, or long-press a show (hub) / episode (show list) to enter selection with that row checked, then delete several at once.
- `PlayAllFab` and library UI helpers.
- History list bottom spacing uses designsystem’s shared navigation-style / mini-player padding contract.
- Library UI uses centralized Google Sans Flex weight tokens from `:core:designsystem`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/library/
  DownloadedEpisodesScreen.kt
  FolderEditComponents.kt
  FolderEditSheet.kt
  HistoryScreen.kt
  HistoryViewModel.kt
  LibraryScreen.kt
  LibraryViewModel.kt
  LikedEpisodesScreen.kt
  PlayAllFab.kt
  SubscriptionsScreen.kt
  history/
    HistoryActivityGraphs.kt      — weekly activity + time-of-day charts, day filter chips
    HistoryDialogs.kt           — clear-all and date-picker dialogs
    HistoryEmptyState.kt          — zero-history empty state
    HistoryInsightCarousel.kt     — compact top-show highlight + two-column metric grid
    HistoryListItems.kt           — timeline rows, status filter (`ConnectedOptionSelector`), date headers
    HistoryScreenBody.kt          — loading / empty / success body switch
    HistoryScreenEffects.kt       — lifecycle, analytics, undo snackbar
    HistoryStatsCards.kt          — period selector (`ConnectedOptionSelector`), vertically flowing listening-time hero
    HistorySuccessList.kt         — success-state LazyColumn (stats + timeline)
    HistoryTopBar.kt              — collapsible top app bar + overflow menu
  subscriptions/
    AutoOrganizeConfirmationDialogs.kt — Confirmation dialogs for enabling/disabling auto-organize with size selection and tips
    FolderShowsSelectionSheet.kt  — Multi-select bottom sheet with search and pre-checked members for adding shows to folders
    MoveToFolderSheet.kt          — Folder picker bottom sheet with show counts and new folder action
    SubscriptionContextMenuSheet.kt — Contextual press-and-hold sheet for folders, folder shows, and root shows
    SubscriptionFolderCards.kt    — Enlarged & compact folder card layouts with direct-clickable covers, overflow badge, and scroll-safe header clicks
    SubscriptionFolderDialog.kt   — Centered floating dialog displaying folder shows in a 3-column grid with maximized viewing area and embedded reorder bar
    SubscriptionFolderLayoutLogic.kt — Folder partitioning, display-size calculations, and inter/intra folder sort resolution
    SubscriptionGenreCatalog.kt   — genre label/icon map mirrored from Explore and resolved against custom podcast genre overrides
    SubscriptionLatestLogic.kt    — Latest episodes sorting, smart scoring, and chronological header grouping
    SubscriptionListRowParts.kt   — list artwork, title column, pin badge
    SubscriptionReorderControls.kt — Scoped reorder mode state and floating bar with Save/Exit controls and sort notices
    SubscriptionRows.kt           — grid cards (title fallback on broken art), list/latest rows, date headers
    SubscriptionSortSheet.kt      — Multi-tier subscription sorting modal bottom sheet (shows outside folders, folders at top, shows inside folders, drag tips, and auto-organize)
    SubscriptionTabContents.kt    — Shows grid/list + New Episodes catch-up list (Play All FAB); calvin reorderable on unfiltered Shows and folders
    SubscriptionTabs.kt           — Shows|New Episodes switcher; Explore-style genre pills with icons
    SubscriptionsFilterRow.kt     — horizontal filter chips row with dynamic custom genre & icon resolution
    SubscriptionsTabSelectorFab.kt — Floating segmented FAB pill indicator for Shows/New Episodes
  logic/
    SubscriptionManualOrderLogic.kt — Manual sort apply / drag move / drop; skips non-podcast drag keys
    SubscriptionSmartOrderLogic.kt — Smart sort: score desc, then title
```

## Subscriptions UX contracts

- Route: `library/subscriptions?tab={0|1}` (`0` = Shows, `1` = New Episodes). Omitting `tab` (Library hub, Open app to Subscriptions) uses Appearance **Default tabs** (`shows` / `new_episodes`). Explicit `tab` still wins (Home Latest, widgets).
- Tab presentation style: Appearance setting allows switching Subscriptions tab style between **Top** (header tab switcher, default) and **Floating** (bottom pill FAB mirroring Explore). When Floating is active, top header tabs are omitted, and the Play All FAB on New Episodes elevates above the floating tab selector with spring animation.
- Shows: image-only 3-column grid (default) or richer list; Explore-style `PillFilterChip` genres **with icons**; subscription folders partitioned into pinned full-width shelves (`Shelf 3×1`, `Panel 3×2`, `Showcase 3×3`, etc.) and compact 1×1 folders (both pinned at top of the library), and unfiled shows below; 3-tier granular sorting via `SubscriptionSortSheet` with independent controls for shows outside folders, folders arranged relative to each other at the top (`FolderInterSort`), and shows inside folders (`FolderIntraSort`), plus press-and-hold repositioning tips; folder Smart Sort uses Decayed Top-3 diminishing returns (`Top + 0.5 * 2nd + 0.25 * 3rd`) to eliminate both hoarder bias and dilution penalties; dual folder-level and cover-level NEW badges (1×1 compact folder shows elevated primary-framed badge when any show has new episodes; enlarged pinned folders badge visible show covers directly and display a header badge when new episodes hide within the +N overflow cluster; expanded dialog badges each show individually); in Manual sort mode, unfiled podcasts can be long-pressed and dragged to reorder; sort icon in top bar opens `SubscriptionSortSheet` on Shows tab and sort menu on New Episodes tab; Smart shares Home Your Shows' deterministic score (log-normalized listening signals plus a bounded three-day subscribe-recency floor, not a front-of-list slice); long-press-drag artwork on the unfiltered Shows list (genre All, empty search) to reorder — covers keep the usual shrink-bounce on tap and stay rounded while dragging; the first drop seeds `subscription_manual_order` from the visible list and switches `subscription_sort` to Manual; drag state stores ordered ids and re-reads current `Podcast` objects so artwork/episode badges stay fresh; new shows append A–Z at the end of Manual; unsubscribe drops that id from Manual order (and Home pins); a circular pin badge marks Home-pinned shows; NEW badge uses shared `isLatestEpisodeNew` (Room `rssHasNewEpisodes` for true-RSS and PI direct-feed tips, else 48h); broken/missing art shows podcast title on the cover.
- New Episodes: latest episode per show from Room `latestEpisode` (PI tip, or publisher-feed tip when opted into **Missing episodes?**); the screen calls `SubscriptionForegroundSync.requestRefresh` on appear so this is a live `/sync` (including **Open app to** Subscriptions), not a cache-only paint from a previous session. Same icon genre pills; Smart vs Chronological sort plus **Hide played episodes** checkbox in the Sort menu; denser play rows; quieter sticky date headers; Play All FAB.
- Search stays in the top bar without removing the tab switcher. No glance/summary strip. Genre row is pills-only (sort/hide are not on that row).
- Context menus & Scoped Reorder Mode: Long-pressing anywhere on a folder card (including member show preview covers) triggers the folder context menu (`Edit folder`, `Add shows to folder`, `Reorder folders`, `Delete folder`). Long-pressing a podcast inside `SubscriptionFolderDialog` triggers the folder podcast context menu (`Remove from folder`, `Move to another folder`, `Reorder shows in folder`, `Unsubscribe`). Long-pressing an unfiled podcast in the root grid triggers the root podcast context menu (`Add to folder`, `Reorder shows`). Entering reorder mode displays a floating `SubscriptionReorderBar` with `Save` and `Exit` buttons and a notice that sort will switch to Manual upon saving. Strict boundary protection prevents dragging across sections (folders cannot drag into shows, and vice versa).
- Genre icon/label catalog in `subscriptions/SubscriptionGenreCatalog.kt` mirrors Explore (no feature→feature import).

## Dependencies

- Project dependencies: `:core:model`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:designsystem`, `:core:analytics`, and `:core:ranking`.
- Libraries: Compose, Navigation, Activity Compose, lifecycle ViewModel/runtime, Coil, Material adaptive, calvin reorderable, Turbine / JUnit for JVM tests.
- Reverse-edge rule: feature modules must not depend on other feature modules or create local repository graphs.

## Threading / lifecycle

- ViewModels are scoped by app navigation.
- Download, playback, catalog, and ranking dependencies are application-scoped instances supplied by app wiring.
- UI runs on the main thread; history, download, and subscription operations use injected suspend APIs.

## Persistence & identity

- This module owns no storage files or stable keys; it reads/writes `subscription_sort`, `subscription_manual_order`, and Home pin ids through `:core:prefs`.
- Download cache entries and worker identities are owned by `:core:downloads`.
- Playback media IDs are owned by `:core:playback`.
- Catalog and subscription identities are owned by catalog, RSS, and database modules.

## Testing notes

- Unit tests live under `feature/library/src/test`.
- History date-picker conversion uses UTC midnight millis (`ZoneOffset.UTC`) so the selected calendar day is preserved.
- History back navigation exposes `history_back` for TalkBack.
- `HistoryFilterTest` covers history filtering behavior.
- `SubscriptionSortTest` covers subscription ordering, including Manual name round-trip.
- `SubscriptionManualOrderLogicTest` covers seed/move/append/drop, ignoring unknown ids, and skipping non-podcast drag keys (genre header).
- `SubscriptionSmartOrderLogicTest` covers Smart score-then-title order without a recency band.
- `SubscriptionFilterLogicTest` covers genre extract/filter (including hybrid custom tag frequency-first priority over catalog genres), custom icon resolution, sort labels, and chronological header buckets.
- `DownloadModelsTest` covers download entity mapping, size/date formatting, and long-press multi-select (`longPressDownloadSelection`).
- `FolderEditLogicTest` covers `FolderDisplaySize` placement properties (`isPinnedToTop`, `placementLabel`), default 3×1 SHELF creation size, optional icon fallback, genre quick-fill token extraction, auto-sync linked genre fallback, and `FakeFolderRepository` lifecycle.
- `SubscriptionFolderLayoutLogicTest` covers folder slot allocation (visible covers + overflow count) across all display sizes, subscription partitioning into pinned, compact, and unfiled groups, genre-filtered folder visibility, in-folder sort matching and dialog show resolution (`resolveSortedFolderShows`), pinned and compact folder sort ordering, unified grid reorder item generation, and dual folder/overflow NEW episode badge logic (`hasFolderOverflowNew`, `hasAnyFolderShowNew`).

```bash
./gradlew :feature:library:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Library JVM tests with the project suite.
- Downloads settings visual baselines live under `screenshots/baselines/` (optional local Roborazzi; not CI-gated).

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:downloads` README](../../core/downloads/README.md)
- [`:app` README](../../app/README.md)
