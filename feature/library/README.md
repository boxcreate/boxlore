# `:feature:library`

## Purpose

Owns Library presentation: hub, history, subscriptions, liked episodes, downloaded episodes, show details, smart-download settings, and auto-download settings. It does not own download workers, playback services, ranking storage, catalog persistence, or app route registration.

## Public API

- `LibraryScreen` and `LibraryViewModel`.
- `HistoryScreen` and `HistoryViewModel`.
- `SubscriptionsScreen`, `LikedEpisodesScreen`, and `DownloadedEpisodesScreen`.
- `SmartDownloadsSettingsScreen` and `AutoDownloadSettingsScreen`.
- `PlayAllFab` and library UI helpers.
- History list bottom spacing uses designsystem’s shared navigation-style / mini-player padding contract.
- Library UI uses centralized Google Sans Flex weight tokens from `:core:designsystem`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/library/
  AutoDownloadSettingsScreen.kt
  DownloadedEpisodesScreen.kt
  HistoryScreen.kt
  HistoryViewModel.kt
  LibraryScreen.kt
  LibraryViewModel.kt
  LikedEpisodesScreen.kt
  PlayAllFab.kt
  SmartDownloadsSettingsScreen.kt
  SubscriptionsScreen.kt
  history/
    HistoryActivityGraphs.kt      — weekly activity + time-of-day charts, day filter chips
    HistoryDialogs.kt           — tracking notice, clear-all, date-picker dialogs
    HistoryEmptyState.kt          — zero-history empty state
    HistoryInsightCarousel.kt     — swipeable insight metric cards
    HistoryListItems.kt           — timeline rows, status filter (`ConnectedOptionSelector`), date headers
    HistoryScreenBody.kt          — loading / empty / success body switch
    HistoryScreenEffects.kt       — lifecycle, analytics, undo snackbar
    HistoryStatsCards.kt          — period selector (`ConnectedOptionSelector`), listening-time hero card
    HistorySuccessList.kt         — success-state LazyColumn (stats + timeline)
    HistoryTopBar.kt              — collapsible top app bar + overflow menu
  subscriptions/
    SubscriptionTabs.kt           — Shows|New Episodes switcher; Explore-style genre pills with icons
    SubscriptionGenreCatalog.kt   — genre label/icon map mirrored from Explore
    SubscriptionTabContents.kt    — Shows grid/list + New Episodes catch-up list (Play All FAB)
    SubscriptionRows.kt           — grid cards (title fallback on broken art), list/latest rows, date headers
```

## Subscriptions UX contracts

- Route: `library/subscriptions?tab={0|1}` (`0` = Shows, `1` = New Episodes).
- Shows: image-only 3-column grid (default) or richer list; Explore-style `PillFilterChip` genres **with icons**; sort menu in the top bar (Smart / Recently Updated / A–Z / Most Listened); NEW badge uses shared `isLatestEpisodeNew` (Room `rssHasNewEpisodes` for true-RSS and PI direct-feed tips, else 48h); broken/missing art shows podcast title on the cover.
- New Episodes: latest episode per show from Room `latestEpisode` (PI tip, or publisher-feed tip when opted into **Missing episodes?**); the screen calls `SubscriptionForegroundSync.requestRefresh` on appear so this is a live `/sync` (including **Open app to** Subscriptions), not a cache-only paint from a previous session. Same icon genre pills; Smart vs Chronological sort plus **Hide played episodes** checkbox in the Sort menu; denser play rows; quieter sticky date headers; Play All FAB.
- Search stays in the top bar without removing the tab switcher. No glance/summary strip. Genre row is pills-only (sort/hide are not on that row).
- Genre icon/label catalog in `subscriptions/SubscriptionGenreCatalog.kt` mirrors Explore (no feature→feature import).

## Dependencies

- Project dependencies: `:core:model`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:designsystem`, `:core:analytics`, and `:core:ranking`.
- Libraries: Compose, Navigation, Activity Compose, lifecycle ViewModel/runtime, Coil, Material adaptive, Turbine / JUnit for JVM tests.
- Reverse-edge rule: feature modules must not depend on other feature modules or create local repository graphs.

## Threading / lifecycle

- ViewModels are scoped by app navigation.
- Download, playback, catalog, and ranking dependencies are application-scoped instances supplied by app wiring.
- UI runs on the main thread; history, download, and subscription operations use injected suspend APIs.

## Persistence & identity

- This module owns no storage files or stable keys.
- Download cache entries and worker identities are owned by `:core:downloads`.
- Playback media IDs are owned by `:core:playback`.
- Catalog and subscription identities are owned by catalog, RSS, and database modules.

## Testing notes

- Unit tests live under `feature/library/src/test`.
- History date-picker conversion uses UTC midnight millis (`ZoneOffset.UTC`) so the selected calendar day is preserved.
- History back navigation exposes `history_back` for TalkBack.
- `HistoryFilterTest` covers history filtering behavior.
- `SubscriptionSortTest` covers subscription ordering.
- `SubscriptionFilterLogicTest` covers genre extract/filter, sort labels, and chronological header buckets.
- Broader ViewModel coverage should use fakes for catalog, download, playback, and ranking dependencies.

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
