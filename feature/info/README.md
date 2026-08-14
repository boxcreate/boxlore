# `:feature:info`

## Purpose

Owns podcast and episode detail presentation: subscribe actions, RSS refresh actions, related and similar content, cross-promotion cards, offline/progress display, and detail-screen layout. It does not own catalog persistence, RSS parsing, download cache behavior, playback services, or app navigation registration.

## Public API

- `PodcastInfoScreen` and `PodcastInfoViewModel`. Subscribed shows expose **Pin** / **Unpin** in the overflow menu (same max-5 list as Your Shows; at capacity a snackbar explains the limit).
- `EpisodeInfoScreen` and `EpisodeInfoViewModel` (similar episodes use prefs `content_languages` + region).
- `InfoViewModelAssembler` for podcast and episode ViewModel factories.
- `InfoListeningProgressItem` and supporting components/sections for detail UI.
- Logic helpers under `logic/` and component-level formatters used by tests.
- Detail UI uses centralized Google Sans Flex weight tokens from `:core:designsystem`.
- `EpisodeInfoSeekLogic` builds the progress-save payload when seeking an episode that is not the current player item.
- For PI-owned shows with a valid HTTPS `feedUrl`, Podcast Info’s top bar shows a compact **Missing episodes?** pill (quiet, right-aligned; hides as the header collapses). Confirm opts the show into **direct-feed refresh on every open**. The confirm copy notes that **subscribing already auto-checks** the publisher feed and adds missing episodes; the pill is for shows already in the library when the list still looks behind. The same pill becomes **Fetching…** then persistent **Updated**. Never uses `RssSubscriptionPort`. **Subscribe auto-check:** on a new subscribe, the loaded repository page list (`piEpisodes`, already merged on offset-0) is matched against the publisher feed; if there is a disconnect, the show is opted in automatically (tip promoted to Room, chip → **Updated**) without a manual tap. Manual pill remains for shows already in the library. Episode lists come from `PodcastRepository` (PI page + cached feed extras); Info remounts the chip and re-fetches after a feed refresh rather than merging a second time. Pagination uses `EpisodePage.sourceCount` so extras do not skip PI items; a supplement refresh matches against a **PI-only** baseline (`mergeSupplements = false`) then reloads page one. Pull-to-refresh is on every show Info page (not only `rss:` catalogs): true RSS uses `refreshCatalog`; opted-in PI runs the missing-episodes feed refresh; other PI shows bypass the 5-minute episode cache and reload page one. On successful opted-in refresh, the newest feed tip is written to Room `podcasts.latestEpisode` with `markAsNew = true` (shared `rssHasNewEpisodes`) **when the show is subscribed** so Home Your Shows NEW badges and the New episodes hero chip stay in sync with the same PI+feed tip; opening the show clears the flag. `SubscriptionForegroundSync` also refreshes those opted-in tips from the feed on cold start (mark-as-new on feed promote only). If show notifications are already on, a successful opt-in (manual or subscribe auto-check) also patches RTDB `tracked_podcasts/{id}.feedUrl` so new-episode pushes can poll the publisher feed.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/feature/info/
  EpisodeInfoScreen.kt
  EpisodeInfoViewModel.kt
  InfoListeningProgressItem.kt
  InfoViewModelAssembler.kt
  PodcastInfoScreen.kt
  PodcastInfoViewModel.kt
  PodcastInfoSupplementSupport.kt
  components/
  logic/
    EpisodeInfoSeekLogic.kt
    …
  sections/
```

## Dependencies

- Project dependencies: `:core:model`, `:core:domain`, `:core:catalog`, `:core:downloads`, `:core:playback`, `:core:network`, `:core:designsystem`, `:core:analytics`, and `:core:rss`.
- Libraries: Compose, Navigation, lifecycle ViewModel/runtime, Coil, Palette, smooth corner rect, coroutines, Kotlin serialization, Turbine, and Compose Material.
- Reverse-edge rule: feature modules must not depend on other feature modules. ViewModels and assemblers must use ports rather than direct `BoxLoreDatabase` access.

## Threading / lifecycle

- ViewModels are scoped by app navigation.
- Catalog, local catalog, offline lookup, RSS, download, playback, and analytics dependencies are supplied by app wiring.
- Podcast/episode info emits glossary analytics via `:core:analytics` façades (no PostHog direct).
- UI runs on the main thread; refresh, subscribe, lookup, and related-content work use suspend APIs.

## Persistence & identity

- This module owns no storage files or stable keys.
- Pin reads and writes `home_pinned_podcast_ids` through `:core:prefs` (`toggleHomePinnedPodcastId`). At-capacity copy comes from `HomePinnedShows.capacityUserMessage()`.
- Podcast, episode, RSS, download, and listening-progress identities come from core modules.
- App navigation owns route patterns and deep links.

## Testing notes

- Unit tests live under `feature/info/src/test`.
- Existing coverage includes assembler behavior, catalog port behavior and errors, offline merge logic, listening-progress mapping, duration formatting, metadata chip logic, feed grouping, toolbar logic, HTML stripping, podcast info ViewModel logic, pull-to-refresh target (RSS vs opted-in direct feed), episode-supplement merge/eligibility, and `PodcastInfoSupplementSupport` refresh / PI-only baseline / auto-opt-in / search union. Home pin persistence, capacity, and unsubscribe cleanup are covered in `:core:prefs` (`HomePinnedShowsTest`, `UserPreferencesRepositoryTest`) rather than constructing `PodcastInfoViewModel`.
- Catalog HTTP paths are covered in `:core:catalog` tests.

```bash
./gradlew :feature:info:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs Info JVM tests with the project suite.
- `scripts/ci/check-feature-no-boxlore-database.sh` guards direct database usage in feature ViewModels and assemblers.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../../core/catalog/README.md)
- [`:app` README](../../app/README.md)
