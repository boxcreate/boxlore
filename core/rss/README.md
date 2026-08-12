# `:core:rss`

## Purpose

Owns RSS feed fetching, parsing, deterministic ID generation, episode catalog management, freshness checks, and the RSS subscription port implementation. It does not own Podcast Index HTTP APIs, download management, playback services, ranking persistence, or feature UI.

## Public API

- `RssFeedClient` fetches feeds, handles conditional freshness checks, and parses RSS/Atom data.
- `RssPodcastRepository` implements `RssSubscriptionPort` and manages RSS podcast and episode catalog operations.
- `RssIdGenerator` creates deterministic `rss:` podcast IDs and negative episode IDs.
- `RssSourceMatcher` provides migration and matching heuristics between Podcast Index and RSS sources.
- `EpisodeSupplementRepository` implements `EpisodeSupplementPort`: fetches a PI show’s public feed and caches **feed-only** episodes under the Podcast Index id. This is **not** a subscription — it never creates `rss:` library rows, never migrates/retires a PI subscription, and never writes FCM topics or autodownload flags. Prefer `RssPodcastRepository` / `RssSubscriptionPort` for true RSS library ownership. Catalog subscription code may read `hasDirectFeedOptIn` when attaching `feedUrl` to notification RTDB rows.
- After a successful feed refresh, `EpisodeSupplementOutcome.Success.newestFeedEpisode` is the tip callers should write to Room `podcasts.latestEpisode` (Home filter chips). `resolveNewestTipFromFeed` is the lighter path used by `SubscriptionForegroundSync` for opted-in subscribed shows (falls back to the stored supplement `feedUrl` when the podcast row has none).
- `optInFromFeedIfDisconnected` fetches the feed and persists a supplement **only** when there are feed-only episodes or a newer tip vs the PI baseline; otherwise returns `NoDisconnect` (no opt-in row).
- `EpisodeSupplementListMerge` dedupes PI pages with feed-only supplement episodes. **`PodcastRepository` is the only PI+feed list merge** (Home filter, Podcast Info, Smart Queue, and other catalog reads). Info may still union in-show search so enriched supplement hits win.
- `EpisodeSupplementDisconnectLogic` decides auto opt-in on subscribe.
- `ports.DownloadCacheRelinker` is injected by app wiring so RSS can request download cache relinking without a downloads dependency.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/rss/
  RssFeedClient.kt
  RssPodcastRepository.kt
  EpisodeSupplementRepository.kt
  EpisodeSupplementMatcher.kt
  EpisodeSupplementTipLogic.kt
  EpisodeSupplementDisconnectLogic.kt
  EpisodeSupplementListMerge.kt
  ports/
    DownloadCacheRelinker.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:database`, and `:core:domain`.
- Libraries: OkHttp, rssparser, Firebase Messaging, and coroutines.
- Reverse-edge rule: RSS must not depend on downloads, playback, designsystem, analytics, or feature modules.

## Threading / lifecycle

- Production `RssPodcastRepository` is application-scoped through `AppContainer` and shared holders.
- Feed fetch, parse, refresh, and database operations run through suspend APIs on IO-oriented dispatchers.
- Freshness checks cap concurrent HEAD requests and use per-podcast locks to avoid duplicate refreshes.

## Persistence & identity

- Podcast IDs use `rss:<sha256-hex>` from normalized feed URLs and are Room primary keys.
- RSS episode IDs are strings encoding deterministic negative `Long` values to avoid collision with positive Podcast Index IDs.
- RSS episode and podcast rows are persisted in the main database owned by `:core:database`.
- Package root is `cx.aswin.boxlore.core.rss`.

## Testing notes

- Unit tests live under `core/rss/src/test`.
- Existing coverage includes deterministic ID contracts, source matching heuristics, feed-client helpers, episode-supplement matching, tip-resolution (PI id vs negative feed id), and disconnect / auto-opt-in predicates.
- MockWebServer is available for feed-fetch tests.

```bash
./gradlew :core:rss:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs RSS JVM tests.
- The root Kover merged verification includes this module.
- Catalog tests rely on RSS test helpers and deterministic ID behavior.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:downloads` README](../downloads/README.md)
- [`:core:domain` README](../domain/README.md)
