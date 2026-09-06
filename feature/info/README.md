# `:feature:info`

## Purpose

Owns podcast and episode detail presentation: subscribe actions, RSS refresh actions, related and similar content, cross-promotion cards, offline/progress display, and detail-screen layout. It does not own catalog persistence, RSS parsing, download cache behavior, playback services, or app navigation registration.

## Public API

- `PodcastInfoScreen` and `PodcastInfoViewModel`. Long-pressing an episode enters multi-selection; the floating toolbar can download, mark completed (or mark unplayed when every selected episode is already complete), play, or append selected episodes to the queue. Its overflow can select only cards currently visible on screen, or fetch up to 100 show episodes for Select all / Select older / Select newer; fetched episode metadata is retained only while selection is active. Episode sorting remains available. Subscribed shows expose **Pin to Home screen** / **Unpin from Home screen** and **Change tag / genre** in the overflow menu (or by tapping the genre chip directly) to open `PodcastGenreEditSheet` with custom text input, standard suggestions, and a 30-icon Material Rounded palette. Unsubscribing clears custom genre tags and icons back to default. Episode play taps and multi-selection play pass `podcast_detail` as the playback entry point context (allowing same-show continuation in Smart Queue) while preserving any spotlight route for video telemetry.
- `EpisodeInfoScreen` and `EpisodeInfoViewModel` (similar episodes use prefs `content_languages` + region).
- `InfoViewModelAssembler` for podcast and episode ViewModel factories.
- `InfoListeningProgressItem` and supporting components/sections for detail UI. Recommendation rails (`EpisodeRecommendationSection`) and episode search overlay (`PodcastInfoSearchOverlay`) protect item lists against duplicate keys using `LazyListKeyPolicy`.
- Logic helpers under `logic/` and component-level formatters used by tests. `EpisodeArtworkLogic` is the Podcast Info row/sheet artwork URL (episode art, else `podcastImageUrl`, else the show image) so Missing episodes? extras without item itunes:image still show cover art.
- Detail UI uses centralized Google Sans Flex weight tokens from `:core:designsystem`.
- `EpisodeInfoSeekLogic` builds the progress-save payload when seeking an episode that is not the current player item.
- For PI-owned shows whose local catalog is **not ready**, unsubscribed Podcast Info may still show **Missing episodes?** (opt-in extras path). The confirm dialog explains that publisher-feed access is allowed once and refreshes on future visits, while subscribing automatically keeps the latest episodes up to date. Subscribe kicks `SubscriptionForegroundSync.requestCatalogIngest` on the application scope so leaving Podcast Info does not cancel the publisher-feed persist (no PI-vs-feed extras compare). The screen only waits on `catalogIngestFinished` to remount if it is still open. Unsubscribe stamps a 14-day Room TTL; that catalog is **not** ready, so the page uses Podcast Index again (a new PI episode still appears). Resubscribe clears the TTL. Once `LocalEpisodeCatalogPort.isReady` is true, the pill is hidden and the episode list comes from Room only. Successful publisher-feed refreshes treat that feed's newest row as authoritative, including same-date/different-id and older-than-PI cross-promo cases. Pull-to-refresh: true RSS uses `refreshCatalog`; subscribed non-pure RSS shows route to a direct-feed catalog refresh via `LocalEpisodeCatalogPort.refresh` (preserving notification, auto-download, skip overrides, artwork fallback, and sort preferences); not-ready opted-in unsubscribed PI still runs the extras refresh; other unsubscribed PI shows reload from `PodcastRepository` (Room or PI). NEW badges still follow newer `publishedDate` only.

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

- ViewModels are scoped by app navigation. Post-subscribe local-catalog ingest is owned by application-scoped `SubscriptionForegroundSync`, not the Podcast Info ViewModel. Late page-load, metadata, and direct-feed responses preserve the current subscription state, so work started while subscribed cannot undo a completed unsubscribe.
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
- Existing coverage includes assembler behavior, catalog port behavior and errors, offline merge logic, listening-progress mapping, duration formatting, metadata chip logic, feed grouping, selection range/order logic, toolbar logic, HTML stripping, podcast info ViewModel logic, pull-to-refresh target (RSS vs subscribed direct feed vs opted-in direct feed), subscription property/toggle preservation across pull-to-refresh and late API enrichment, episode-supplement merge/eligibility, episode list artwork fallback, and `PodcastInfoSupplementSupport` refresh / PI-only baseline / auto-opt-in / search union. Home pin persistence, capacity, and unsubscribe cleanup are covered in `:core:prefs` (`HomePinnedShowsTest`, `UserPreferencesRepositoryTest`) rather than constructing `PodcastInfoViewModel`.
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
