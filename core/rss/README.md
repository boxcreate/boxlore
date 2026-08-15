# `:core:rss`

## Purpose

Owns RSS feed fetching, parsing, deterministic ID generation, episode catalog management, freshness checks, and the RSS subscription port implementation. It does not own Podcast Index HTTP APIs, download management, playback services, ranking persistence, or feature UI.

## Public API

- `RssFeedClient` fetches feeds, handles conditional freshness checks, and parses RSS/Atom data. HEAD 405/501 falls through to a conditional GET (`RssUnchangedLogic`) instead of treating the feed as changed.
- `RssPodcastRepository` implements `RssSubscriptionPort` and manages RSS podcast and episode catalog operations. Refresh and subscribe remap parsed rows through `StickyRssEpisodeRemap.prepare` before writing `latestEpisode` and `rss_episodes`, so stored `episodeId`s never remint.
- `LocalEpisodeCatalogRepository` implements `LocalEpisodeCatalogPort`: full publisher-feed catalog under a Podcast Index id, sticky upsert by guid, per-show ready gate (count + listener ids + tip; a non-null unsubscribe `ttlExpiresAt` is not ready so unsubscribed reads stay on Podcast Index), HEAD/quiet skip, mega GET capped at 2. Never creates `rss:` rows. Incremental upsert never shrinks existing rows. HEAD validators are not reused after the publisher `feedUrl` changes. `sweepExpired` rechecks TTL before delete so a refresh that cleared `ttlExpiresAt` is kept. `:core:catalog` `SubscriptionForegroundSync` runs that sweep on each launch / Subscriptions refresh pass.
- `StickyEpisodeIdentity` / `LocalEpisodeCatalogPersist` / `FeedOrderLogic` / `LocalCatalogReadyLogic` / `LocalCatalogOrphanRematch` are the identity and freshness helpers. Orphan rematch (guid → enclosure → title+date) runs only when the stored id no longer resolves.
- `RssIdGenerator` creates deterministic `rss:` podcast IDs and negative episode IDs (mint only, not refresh).
- `RssSourceMatcher` provides migration and matching heuristics between Podcast Index and RSS sources.
- `EpisodeSupplementRepository` implements `EpisodeSupplementPort`: fetches a PI show’s public feed and caches **feed-only** episodes under the Podcast Index id. This is **not** a subscription — it never creates `rss:` library rows, never migrates/retires a PI subscription, and never writes FCM topics or autodownload flags. Prefer `RssPodcastRepository` / `RssSubscriptionPort` for true RSS library ownership. Catalog subscription code may read `hasDirectFeedOptIn` when attaching `feedUrl` to notification RTDB rows. `listDirectFeedOptIns` exports HTTPS publisher URLs for library JSON; `restoreDirectFeedOptIn` writes a stub row (no ETag) so import can keep Missing episodes? on even before the feed GET, and does not replace existing extras.
- After a successful feed refresh, `EpisodeSupplementOutcome.Success.newestFeedEpisode` is the tip callers should write to Room `podcasts.latestEpisode` (Home filter chips). `refreshFromFeed` / `optInFromFeedIfDisconnected` fall back to the stored supplement HTTPS `feedUrl` when the request URL is missing or not HTTPS. `refreshFromFeed(RefreshFromFeedRequest)` can load the PI baseline in parallel with the publisher GET; a throwing `loadBaseline` is a feed-load failure and does **not** persist (empty PI must not mark every RSS item as feed-only). `isPublisherFeedUnchanged` HEADs stored ETag / Last-Modified so launch sync can skip GET+PI when the feed did not change. `SubscriptionForegroundSync` and FCM hydration (`NewEpisodePushHydration`) both use `refreshFromFeed` so Home chips and Library get the full extra list; `resolveNewestTipFromFeed` remains the payload guid/enclosure match fallback. Feed load failures return a fixed user-facing message (never raw HTTP/parser text).
- `optInFromFeedIfDisconnected` fetches the feed and persists a supplement **only** when there are feed-only episodes or a newer tip vs the PI baseline; otherwise returns `NoDisconnect` (no opt-in row).
- `EpisodeSupplementListMerge` dedupes PI pages with feed-only supplement episodes using the same identity rules as `EpisodeSupplementMatcher` (audio URL, then non-empty title with a date window). **Page/list merge** (Home, Podcast Info episode list, Smart Queue) runs only in `PodcastRepository`. **In-show search** is a separate union: catalog prefers the network/PI hits; Podcast Info prefers supplement hits so feed-only extras keep their enriched titles.
- `EpisodeSupplementDisconnectLogic` decides auto opt-in on subscribe.
- `EpisodeSupplementArtworkLogic` fills blank item artwork from the feed channel or the PI show image when persisting extras.
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
  LocalEpisodeCatalogRepository.kt
  LocalEpisodeCatalogReads.kt
  LocalEpisodeCatalogRefresh.kt
  StickyEpisodeIdentity.kt
  LocalEpisodeCatalogPersist.kt
  StickyRssEpisodeRemap.kt
  FeedOrderLogic.kt
  LocalCatalogReadyLogic.kt
  LocalCatalogOrphanRematch.kt
  RssUnchangedLogic.kt
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
- Existing coverage includes deterministic ID contracts, source matching heuristics, feed-client helpers, episode-supplement matching (including empty titles and distant same-title dates), list merge, tip-resolution (PI id vs negative feed id, blank metadata), supplement artwork fallback (item / channel / show), repository require/failure mapping, disconnect / auto-opt-in predicates, `restoreDirectFeedOptIn` / `listDirectFeedOptIns` (HTTPS stub, ignore http/`rss:`, keep existing extras), local-catalog refresh outcomes (unchanged validators, empty parse, blank/`rss:` ids), bounded `getWindow`, TTL recheck on expiry sweep, `LocalCatalogReadyLogic` unsubscribe-TTL not-ready, and sticky RSS tip reuse when a stored id differs from the minted id.
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
