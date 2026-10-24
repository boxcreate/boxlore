# `:core:rss`

## Purpose

Owns RSS feed fetching, parsing, deterministic ID generation, episode catalog management, freshness checks, and the RSS subscription port implementation. It does not own Podcast Index HTTP APIs, download management, playback services, ranking persistence, or feature UI.

## Public API

Failed automatic fetches record a six-hour retry boundary while keeping catalogs stale and retaining last-good rows; manual refresh and release pushes bypass it.

- `RssEpisodeCatalog` adapts true RSS subscriptions to the same refresh/read contract as catalog shows, using `rss_episodes` and the saved `rss:` identity. Conditional GET supports feeds that reject HEAD; feeds without validators share the six-hour cooldown. Manual/push refresh bypasses it. Anchored playback windows continue chronologically regardless of list sort, matching the PI adapter; blank search terms return no results. Existing imports with legacy freshness capability or missing rows receive an in-place repair, without catalog matching. Refresh re-reads settings after network I/O and checks cancellation/authorization before persistence. Failed/empty feeds retain last-good rows; sticky IDs and removed-from-feed archive rows remain intact. The full-feed semaphore is shared with the PI adapter. Foreground/push persists call the download listener; background discovery suppresses that callback.
- `restoreSubscription` keeps the saved podcast ID across feed URL redirects. Re-adding an existing feed preserves its auto-download and notification settings.

- `RssFeedClient` fetches feeds, handles conditional freshness checks, and parses RSS/Atom data. HEAD 405/501 falls through to a conditional GET (`RssUnchangedLogic`) instead of treating the feed as changed.
- `RssPodcastRepository` implements `RssSubscriptionPort` and manages RSS podcast and episode catalog operations. Refresh and subscribe remap parsed rows through `StickyRssEpisodeRemap.prepare` before writing `latestEpisode` and `rss_episodes`, so stored `episodeId`s never remint. `getEpisodesAround` queries forward-continuation episodes via `RssEpisodeDao.getEpisodesAfter` for same-show queue playback.
- `RssPodcastRepository.legacySubscriptionRepair` exposes `LegacyRssSubscriptionRepair`. `inspect` returns a `LegacyRssFeedSnapshot` without mutating Room; `upgrade` returns a `LegacyRssUpgradeOutcome` and atomically copies a verified RSS catalog under a Podcast Index id without re-keying episodes. Both share the per-podcast refresh lock with `refreshCatalog`.
- `LocalEpisodeCatalogRepository` implements `LocalEpisodeCatalogPort`: full publisher-feed catalog under a Podcast Index id, sticky upsert by guid, and a structural per-show ready gate (complete persisted count; a non-null unsubscribe `ttlExpiresAt` is not ready so unsubscribed reads stay on Podcast Index). `getWindow` with `aroundEpisodeId` uses `continuationRows` (`getEpisodesAfter`) to fetch the anchor and subsequent forward chronological episodes. A not-ready catalog always bypasses HEAD validators and performs the repair GET. Listener-id or cached PI-tip mismatches never permanently hide a successfully persisted publisher feed. HEAD/quiet skip and mega GET (capped at 2) remain for ready catalogs. It never creates `rss:` rows; incremental upsert never shrinks existing rows. HEAD validators are not reused after the publisher `feedUrl` changes. `sweepExpired` rechecks TTL before delete so a refresh that cleared `ttlExpiresAt` is kept. `:core:catalog` `SubscriptionForegroundSync` runs that sweep on each launch / Subscriptions refresh pass.
- `StickyEpisodeIdentity` / `LocalEpisodeCatalogPersist` / `FeedOrderLogic` / `LocalCatalogReadyLogic` / `LocalCatalogOrphanRematch` are the identity and freshness helpers. `LocalCatalogListenerStateReconciler` safely re-keys orphaned negative restore ids after a full persist (unique enclosure, then unique title within 48 hours measured in epoch seconds), merging history, sessions/rollups, and queue state into the chosen catalog id. Completed downloads move only when `DownloadCacheRelinker` confirms cached bytes reached the new key; otherwise the legacy row/cache is retained. Ambiguous or metadata-poor rows are retained rather than guessed and no longer block source cutover.
- `RssIdGenerator` creates deterministic `rss:` podcast IDs and negative episode IDs (mint only, not refresh).
- The episode recipe (`EpisodeRecipeId`) lives in [`:core:model`](../model/README.md), so `:core:database` can use it too. `StickyEpisodeIdentity.catalogKey` delegates to its `catalogKey`, so stored keys don't change. The shared fixture and its test stay here: `EpisodeRecipeIdTest` checks the model's recipe against `src/test/resources/fixtures/episode-recipe-vectors.json`, which the scripts copy also reads. The full identity rules are in [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).
- `RssSourceMatcher` provides migration and matching heuristics between Podcast Index and RSS sources.
- `EpisodeSupplementRepository` implements `EpisodeSupplementPort`: fetches a PI show’s public feed and caches **feed-only** episodes under the Podcast Index id. This is **not** a subscription — it never creates `rss:` library rows, never migrates/retires a PI subscription, and never writes FCM topics or autodownload flags. Prefer `RssPodcastRepository` / `RssSubscriptionPort` for true RSS library ownership. Catalog subscription code may read `hasDirectFeedOptIn` when attaching `feedUrl` to notification RTDB rows. `listDirectFeedOptIns` exports HTTPS publisher URLs for library JSON; `restoreDirectFeedOptIn` writes a stub row (no ETag) so import can keep Missing episodes? on even before the feed GET, and does not replace existing extras.
- After a successful feed refresh, `EpisodeSupplementOutcome.Success.newestFeedEpisode` is the tip callers should write to Room `podcasts.latestEpisode` (Home filter chips). A tip that matches a baseline episode keeps its ID and takes every empty field (description, artwork, length, Podcast 2.0 links) from the feed episode (`Episode.withBlanksFrom`), because a match baseline carries only the ID, title, audio and date; a full Podcast Index row is unchanged. `refreshFromFeed` / `optInFromFeedIfDisconnected` fall back to the stored supplement HTTPS `feedUrl` when the request URL is missing or not HTTPS. `refreshFromFeed(RefreshFromFeedRequest)` can load the PI baseline in parallel with the publisher GET; a throwing `loadBaseline` is a feed-load failure and does **not** persist (empty PI must not mark every RSS item as feed-only). `isPublisherFeedUnchanged` HEADs stored ETag / Last-Modified so launch sync can skip GET+PI when the feed did not change. `SubscriptionForegroundSync` and FCM hydration (`NewEpisodePushHydration`) both use `refreshFromFeed` so Home chips and Library get the full extra list; `resolveNewestTipFromFeed` remains the payload guid/enclosure match fallback. Feed load failures return a fixed user-facing message (never raw HTTP/parser text).
- `optInFromFeedIfDisconnected` fetches the feed and persists a supplement **only** when there are feed-only episodes or a newer tip vs the PI baseline; otherwise returns `NoDisconnect` (no opt-in row).
- `EpisodeSupplementListMerge` dedupes PI pages with feed-only supplement episodes using the same identity rules as `EpisodeSupplementMatcher` (audio URL, then non-empty title with a date window). **Page/list merge** (Home, Podcast Info episode list, Smart Queue) runs only in `PodcastRepository`. **In-show search** is a separate union: catalog prefers the network/PI hits; Podcast Info prefers supplement hits so feed-only extras keep their enriched titles.
- `EpisodeSupplementDisconnectLogic` decides auto opt-in on subscribe.
- `EpisodeSupplementArtworkLogic` fills blank item artwork from the feed channel or the PI show image when persisting extras.
- `ports.DownloadCacheRelinker` is injected by app wiring so RSS can request download cache relinking without a downloads dependency.

- Ready catalogs share one persisted six-hour cooldown for `NORMAL` and `AUTO_DOWNLOAD`, including feeds with ETag/Last-Modified. Repair or changed feed URLs bypass stale readiness/freshness assumptions. `MANUAL` and `NEW_RELEASE` force a publisher GET. The shared full-feed gate caps overlapping fetches at two; `RefreshRequest.canProceed` is rechecked after waiting and after fetching so revocation prevents persistence. `isRefreshDue` lets foreground batching exclude recent feeds. The post-persist callback runs outside the transaction/lock and excludes `AUTO_DOWNLOAD` or requests with `runPostPersistCallback = false`; background discovery must admit releases through its own consent and restriction gate.

- `RssHttpExecution` keeps OkHttp cancellation attached through response-body reading. Revoking background consent or leaving a foreground refresh cancels the actual HTTP call; late responses are closed and conditional HEAD cancellation cannot start a fallback GET.

- RSS response consumption keeps coroutine cancellation attached until the body is consumed and closed. The shared response reader validates HTTPS before consuming a response, including redirect responses.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/rss/
  RssFeedClient.kt
  RssPodcastRepository.kt
  LegacyRssSubscriptionUpgrade.kt
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
- Freshness checks share a bounded conditional GET budget and use per-podcast locks to avoid duplicate refreshes.
- Feeds larger than 4 MB skip the RSS library parser, which copies the whole feed into one String and ran out of memory, and use only the streaming pull parser (`RssParsePolicy`).

## Persistence & identity

- Podcast IDs use `rss:<sha256-hex>` from normalized feed URLs and are Room primary keys.
- RSS episode IDs are strings encoding deterministic negative `Long` values to avoid collision with positive Podcast Index IDs.
- RSS episode and podcast rows are persisted in the main database owned by `:core:database`.
- Exact legacy upgrades retain the old RSS row and episodes as an unsubscribed compatibility alias; the active PI row owns an equivalent ready local catalog using the same negative episode ids.
- Feed read failures are typed: `FeedHttpException` (status code; message unchanged) and `NotAFeedException` (wrong content type or nothing parseable), both `IllegalArgumentException`s. `FeedReadFailure.isAddressGone` reads them, plus no such host and failing certificates, as a dead address (`RefreshOutcome.Failure.addressGone`); timeouts, 5xx, 429 and dropped connections are passing trouble. An address that answers with no episodes counts as dead too.
- Moved feeds: `RssFetchResult.movedTo` is the final address when every redirect was permanent (301, 308; `permanentMoveTarget`); `ParsedRssFeed.newFeedUrl` is the channel's `itunes:new-feed-url` (both parsers). With `RefreshRequest.detectMove`, `confirmedMove` reports a new HTTPS address only when it differs from the saved one and serves the same show by title (case, spacing and punctuation ignored); a declared address is read once to confirm. Temporary redirects never move a feed.
- Local catalog writes never replace another episode (`LocalEpisodeCollisionGuard`, [docs/EPISODE_IDS.md](../../docs/EPISODE_IDS.md) §4). The write is a REPLACE, which deletes any stored row sharing the episode ID or the unique (show, catalog key). Inside the refresh transaction, for each row:
  - **Its own stored row first.** When the show already stores the key, the row keeps that stored ID, so it is updated and never deleted through the unique index; an exact stored key wins over one that only matches after trimming. Stored keys are compared after the recipe trim, so a key that Room migration 31→32 left with a trailing newline, tab or no-break space keeps its ID and gets the trimmed key instead of a second row.
  - **Legacy heal.** A same-show row that holds the item's own minted ID (`StickyEpisodeIdentity.ownMintedId`) and whose key is the synthetic `id:<its ID>`, or its audio address while the item has a GUID, is the same episode: it keeps its ID and takes the feed's key. The own mint is looked up whatever ID the row arrives with, so a first read that matched a Podcast Index episode heals the legacy row instead of writing a second row under the PI ID.
  - **A free ID.** Otherwise the row takes its incoming ID when no other (show, key) holds it, in the database or earlier in the batch. A key repeated in one batch is written once (first wins).
  - **Reserved ID.** Any other clash (two shows reading one feed, which mint the same IDs from one feed namespace, or a same-show row under a different GUID) gets a reserved negative ID (`StickyEpisodeIdentity.reservedEpisodeId`, stable across refreshes).
  - The guard returns the rows to write, the stored keys it corrected with where each old key came from (`KeyRepair`: re-trimmed, synthetic, audio address), and any row with no free ID after 8 reserved tries. The refresh logs both; the old key's provenance is not stored anywhere else yet. A row with no free ID makes the refresh write nothing and fail, so a partial catalog is never marked complete.
  - The legacy RSS upgrade keeps its IDs for listener state, so it stays on RSS instead when another show holds one.
- Package root is `cx.aswin.boxlore.core.rss`.

## Testing notes

- Unit tests live under `core/rss/src/test`.
- Existing coverage includes deterministic ID contracts, source matching heuristics, exact legacy URL/GUID upgrade gates, feed-client helpers, episode-supplement matching (including empty titles and distant same-title dates), list merge, tip-resolution (PI id vs negative feed id, blank metadata, match-only baseline rows filled from the feed), supplement artwork fallback (item / channel / show), repository require/failure mapping, disconnect / auto-opt-in predicates, `restoreDirectFeedOptIn` / `listDirectFeedOptIns` (HTTPS stub, ignore http/`rss:`, keep existing extras), local-catalog refresh outcomes (unchanged validators, not-ready validator bypass, empty parse, blank/`rss:` ids), bounded `getWindow`, TTL recheck on expiry sweep, structural ready cutover, restored-listener remap/merge behavior, sticky RSS tip reuse when a stored id differs from the minted id, entity toPodcast notification/download/custom-genre flag preservation, dead-address classification (`FeedReadFailureTest`, `LocalEpisodeCatalogRepositoryTest.refreshFailureSaysWhetherTheSavedAddressLooksGone`), moved feeds (`FeedMoveTest`: redirect chains, `itunes:new-feed-url`, same-show check; `refreshReportsAPermanentMoveOnlyWhenAskedAndTheShowMatches`), and episode ID clashes (`LocalEpisodeCollisionGuardTest`: cross-show, same-show other key, within one batch, stable reserved IDs, exhaustion, legacy keys ending in a newline, tab, CR LF, space plus newline, no-break space, U+2028 or U+180E (written as escapes and trimmed with the recipe's set, so the JVM agrees with Android), the unique (show, key) index modelled as Room's REPLACE, synthetic and audio-address legacy heals whether the row arrives with its own mint or a Podcast Index ID, and the cases kept apart; `LocalEpisodeCatalogRepositoryTest`: two shows refreshing one feed keep their own rows, a full refresh over legacy keys keeps one row per episode on its stored ID, and a first read with a Podcast Index match heals the legacy row and reconciles listener state onto its stored ID).
- MockWebServer is available for feed-fetch tests.

```bash
./gradlew :core:rss:testDebugUnitTest
```
- `RssParsePolicyTest` checks the size limit and that a large valid feed never reaches the library parser.
- `EpisodeRecipeIdTest` runs every case in the shared recipe fixture against `:core:model`'s `EpisodeRecipeId` and asserts the fixture's SHA-256, so a changed copy fails here and in the scripts test.

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
