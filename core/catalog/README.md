# `:core:catalog`

## Purpose

Owns catalog orchestration: Podcast Index access through `PodcastRepository`, subscriptions, chapters, transcripts, content catalog (v3), cross-promotion, consent helpers, install-referrer handling, backup and restore, and shared dependency bridges used by workers and services. It does not own playback, download workers, Compose UI, Room schemas, RSS internals, or ranking model storage.

## Public API

- `PodcastRepository` coordinates Podcast Index calls, recommendation endpoints, curated vibes, home bootstrap, similar episodes (with content languages), content catalog/v3, and RSS delegation. Canonical `PodcastEntity.toPodcast()` mapper standardizes entity conversion across data and feature modules, with module-boundary isolation between `:core:catalog` and `:core:rss`. Subscribed PI shows whose local catalog is **ready** (`LocalEpisodeCatalogPort.isReady`) serve list/search/`getEpisode` from Room only. Unsubscribe stamps a 14-day TTL; that catalog stays on disk but is **not** ready, so unsubscribed reads keep today’s PI pages plus cached extras merge. True `rss:` rows are unchanged. `getEpisodes` uses a bounded window (200), not `getAllNewest`. `loadPiEpisodesForBaseline` remains for **first** catalog persist rematch only. `invalidateEpisodesCache` drops the 5-minute PI page cache for a feed (Podcast Info pull-to-refresh). In-memory caching and pagination safety are managed by `PodcastRepositoryMemoryPolicy` (`clampPageLimit` caps requests to 100, and `TimedLruCache` bounds cache entries with LRU and TTL eviction to prevent OutOfMemoryError).
- `syncSubscriptions` (`POST /sync`) is PI-only for shows that are **not** catalog-ready and **not** still on extras opt-in. Ready ids contribute the Room newest tip; opted-in not-ready ids contribute the cached extras tip. Live feed freshen stays on `SubscriptionForegroundSync`. `getEpisodeWindow` is the bounded Smart Queue / Auto entry (never `getAllNewest`).
- Show search: legacy `searchPodcasts` / `searchPodcastsWithCorrection` keep calling `GET /search` (hybrid). New clients use `searchPodcastsTypeahead` (`GET /search/typeahead` / Meili) and `searchPodcastsGrouped` (parallel typeahead + hybrid → Matches / Also found). Concept search uses `searchSemanticGrouped` / `searchEpisodesSemantic` (`GET /search/semantic`: one CF embed → Qdrant podcasts + episodes; additive `feeds` + legacy `items`).
- `logic.mergeShowSearchResults` dedupes Meili vs hybrid by id / itunes / feed URL.
- `mapRegionForBriefing` maps content regions to briefing markets (`us` / `in` / `gb` / `global`; legacy `uk` → `gb`) via `ContentRegions.briefingMarket`.
- `SubscriptionRepository`, `ChapterRepository`, and `TranscriptRepository` expose catalog-adjacent data operations. Normal `updateLatestEpisode` calls never replace a newer Room tip with an older item (`LatestEpisodeTipLogic`); same published date does **not** replace or badge. A successful full publisher-feed ingest passes `publisherFeedAuthoritative`, allowing the real feed tip to replace a newer PI cross-promo or same-date different identity. NEW is still set only when `publishedDate` is newer. Subscribe recovers a missing HTTPS `feedUrl` via `GET /podcast` (daily retry also runs in launch sync). When the listener turns **show notifications** on, `SubscriptionRepository` writes RTDB `tracked_podcasts/{podcastIndexId}` (`title`, `imageUrl`, and HTTPS `feedUrl` only when a Room tip exists so the Action can seed `lastRssKey`). Live RTDB rules allow those three keys only. The Check New Episodes Action seeds `lastRssKey` from existing `lastEpisodeId` on first RSS see (notifies only when the PI id differs). Turning notifications **off** removes the `feedUrl` child. Unsubscribe sets a 14-day TTL on the local catalog (no CASCADE wipe); resubscribe clears it.
- `SubscriptionForegroundSync` refreshes subscribed latest episodes (`syncSubscriptions` in chunks of 10). Cold start from Home still waits 2s for first paint; **Subscriptions appear** call `requestRefresh()`. Each pass starts with `sweepExpired` so unsubscribed catalogs past the 14-day TTL are deleted. PI `/sync` runs for shows that are not catalog-ready. Every subscribed PI show with an HTTPS `feedUrl` refreshes the local catalog (HEAD/quiet skip for ready rows; not-ready rows force the repair GET; one-time PI rematch only while `needsFullBackfill`). Missing HTTPS URLs are recovered via `GET /podcast` at most once per day, with bounded concurrency; one failed recovery does not abort the rest of the pass. Concurrency 6. A successful persist emits `directFeedRefreshed`. Subscribe calls `requestCatalogIngest` on the same application scope so the first publisher-feed persist is not cancelled when Podcast Info is popped; it ignores `requestRefresh` cooldown / in-flight gates. `catalogIngestFinished` emits when that one-shot ends (success, skip, or failure). The same pass patches RTDB `feedUrl` for notifying HTTPS shows. `:app` starts it after onboarding; Library `onScreenResume` calls `requestRefresh`.
- `LegacyRssRepair` runs once after onboarding for pre-fix OPML rows when enabled by the app-shell build/install gate. It fetches each subscribed `sourceType=rss` feed and accepts only an exact Podcast Index feed-URL variant or podcast GUID; title/author/artwork are never match inputs. Rows with `linkedPodcastIndexId` are skipped because they represent an intentional RSS-source choice. URL/GUID conflicts, lookup failures, active target subscriptions, or incomplete local catalogs leave the RSS row untouched. Successful rows adopt the PI id while retaining RSS episode ids, journaling DataStore id references for crash-safe completion, restoring notification routing for shows that had alerts enabled, and clearing any recommendation cache seeded by the old id. A pass starts only when an eligible row and connectivity are present, publishes `inProgress` for persistent app-shell status UI, and is admitted by a process-local one-shot gate. Three consecutive transient lookup/inspect failures stop the remaining sequential work. Fresh installs can persist the completed marker without starting a pass or showing progress. Once a pass settles without a pending ID transaction, its version marker is written even if individual feeds had transient failures, preventing foreground/banner loops; only a process killed mid-transaction can resume from the pending journal on a later launch.
- `content.ContentOrchestrator`, `ServerGroupedSectionProvider`, `ContentContextEngine`, and related content contracts assemble discovery slates from catalog/v3 (no live `content/sections/v1` client).
- `content.CuratedMoods` is the shared catalog of curated-mood IDs/titles used by Home daypart rails and Explore For You chips (`getCuratedVibe`).
- `backup.LibraryBackupManager` imports and exports library data, OPML, listening history, ranking backup payloads, and global prefs including `contentLanguages`, `subscriptionSort` (may be `Manual`), optional `subscriptionManualOrder`, optional `homePinnedPodcastIds`, optional `sameShowQueueOnly`, and optional `homeShortcutsInLibrary`, optional `widgetAppearance`, optional `exploreDefaultTab`, optional `subscriptionsDefaultTab`, optional `subscriptionsTabStyle`, optional `navigationStyle`, and optional `fontRoundness`. JSON restore preserves each valid historical `subscribedAt` and suppresses new-subscribe ranking feedback; missing/future timestamps fall back to import time. History restore preserves manual/bulk completion provenance while sanitizing legacy Gson-null strings. RSS/OPML restore rethrows coroutine cancellation and treats other failures as skippable. OPML import matches Podcast Index first (feed URL variants, same-title / same-feed search, then a publisher-feed peek for redirected URL + podcast GUID) and creates a true `rss:` row only after those identity lookups settle as not found. Transient PI URL/GUID/search failures defer that feed rather than silently creating an RSS subscription. Every imported PI subscription with an HTTPS publisher `feedUrl` now receives a bounded direct-feed ingest immediately, including old JSON backups whose optional `directFeedOptIns` is absent/null and OPML rows resolved to PI. Incoming **v6** `directFeedOptIns` remains a compatible explicit URL override; PI `/sync` handles only imported PI rows without a usable publisher URL, and true `rss:` rows use `refreshCatalogIfNeeded`. Settings → Add RSS is unchanged (always `rss:`); null/blank Gson `podcastId` / `feedUrl` rows are skipped. Older backups without the Manual-order / pin lists still parse; Manual with a missing id list seeds from the current Smart list on first use.
- `SharedAppDependencies` and `SharedAppDependenciesHolder` expose application-scoped instances to workers and services.
- `InstallReferrerManager` parses Play Install Referrer deep links and exposes optional `onInstallReferrerResolved` (channel + raw referrer). `:app` wires that callback into analytics; catalog must not depend on `:core:analytics`.
- `RoomLocalCatalog` implements `LocalCatalogPort`; its metadata enrichment transaction updates only an existing subscribed row, so a late Podcast Info response cannot create or restore a subscription after unsubscribe. `RoomEpisodeOfflineLookup` implements `EpisodeOfflineLookupPort`.
- `ports.ListeningHistoryBackupPort` and `ports.SmartDownloadSyncPort` keep backup and download seams out of UI modules.
- `:core:rss`, `:core:domain`, `:core:database`, and `:core:prefs` are re-exported where existing public signatures require those types.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/catalog/
  PodcastRepository.kt
  PodcastRepositoryContentCache.kt
  PodcastRepositoryContentMapping.kt
  PodcastRepositoryMappers.kt
  PodcastRepositoryMemoryPolicy.kt
  PodcastRepositoryNetworkLookups.kt
  PodcastRepositoryEpisodeSupplements.kt
  PodcastRepositoryRecommendations.kt
  PodcastRepositoryStreams.kt
  PodcastEpisodeSupplementMerge.kt
  LegacyRssRepair.kt
  LatestEpisodeTipLogic.kt
  TrackedPodcastRtdbLogic.kt
  EpisodeMapper.kt
  SubscriptionRepository.kt
  ChapterRepository.kt
  TranscriptRepository.kt
  SharedAppDependencies.kt
  SubscriptionForegroundSync.kt
  SubscriptionForegroundSyncIngest.kt
  RoomLocalCatalog.kt
  RoomEpisodeOfflineLookup.kt
  EngagementPromptCoordinator.kt
  InstallReferrerManager.kt
  logic/
  backup/
    LibraryBackupManager.kt
    LibraryBackupDirectFeed.kt
    LibraryBackupDirectFeedRestore.kt
    LibraryBackupImportLogic.kt
    OpmlImportLogic.kt
  content/
  crosspromo/
  ports/
  privacy/
```

Main Kotlin files should remain below 1000 lines; extracted helpers keep repository mapping, network lookups, content cache, recommendations, and stream handling reviewable.

## Dependencies

- Project dependencies: `:core:model`, `:core:network`, `:core:domain`, `:core:database`, `:core:prefs`, `:core:rss`, and `:core:ranking`.
- Libraries: Retrofit, OkHttp, Gson, coroutines, DataStore, Firebase Database, Firebase Messaging, and Install Referrer.
- Reverse-edge rule: catalog must not depend on playback, downloads, designsystem, analytics, or feature modules.

## Threading / lifecycle

- Production repositories are application-scoped through `AppContainer` and `SharedAppDependenciesHolder`.
- Network and database work uses suspend APIs and background dispatchers at repository boundaries.
- Workers and services must use the installed holder instead of constructing independent catalog, RSS, or ranking graphs.

## Persistence & identity

- Main Room database identity is owned by `:core:database` and exposed through catalog APIs.
- User preference and cache file names are owned by `:core:prefs` and catalog cache code.
- RSS podcast IDs and negative episode IDs are owned by `:core:rss`.
- Backup JSON field names and backup version fields must remain restore-compatible. Version **6** adds optional `directFeedOptIns`; do not rename those keys. Optional `subscriptionManualOrder` and `homePinnedPodcastIds` must stay nullable for older backups.
- Package root is `cx.aswin.boxlore.core.catalog`.

## Testing notes

- Unit tests live under `core/catalog/src/test`.
- Existing coverage includes `PodcastRepositoryCatalogTest`, `PodcastRepositoryEpisodeSupplementTest` (offset-0 merge, later-page PI-only, `mergeSupplements = false` PI baseline, search union, opted-in `/sync` skip), `LegacyRssRepairLogicTest` (URL/GUID-only acceptance, conflicts, transient failures), `LatestEpisodeTipLogicTest`, `TrackedPodcastRtdbLogicTest` (HTTPS `feedUrl` on notification RTDB rows), `ShowSearchMergeTest`, `DirectFeedSyncOrderTest`, `LibraryBackupDirectFeedLogicTest` / `LibraryBackupDirectFeedRestoreTest` (JSON v6 opt-in export targeting, Gson-null skip, restore-before-`/sync` plan, RTDB patch before feed refresh), `LibraryBackupManualOrderPinsTest` (Manual sort + id lists round-trip; older JSON without lists), `LibraryBackupImportLogicTest` (restore/OPML rethrow cancellation), `OpmlImportLogicTest` (catalog URL/title/feed match before `rss:` fallback, URL scheme/slash/`www` candidates, HTTPS rewrite for feed peek), `SubscriptionForegroundSyncLogicTest` (cooldown / subscriptions-first fetch / catalog ingest id gates), `InstallReferrerManager` channel derivation / attribution callback seams, `SubscriptionForegroundSync` delay / `requestRefresh` / periodic / `SubscriptionForegroundSyncIngestTest` (`requestCatalogIngest` cooldown bypass, RSS skip, finished emit, unsubscribe TTL, expiry sweep isolation) / direct-feed concurrency and failure isolation (including missing-feed URL recovery), content orchestration tests, content signal enrichment, grouped sections, recent section intent storage, cross-promotion detection, transcript behavior, and dependency-holder behavior.
- RSS ID and matcher tests live in `:core:rss`; smart-queue tests live in `:core:playback`.

```bash
./gradlew :core:catalog:testDebugUnitTest
./gradlew :core:catalog:testDebugUnitTest --tests 'cx.aswin.boxlore.core.catalog.PodcastRepositoryCatalogTest'
```

## CI relevance

- `unit-tests.yml` runs catalog JVM tests.
- The root Kover merged verification includes this module.
- Architecture guards verify README presence and selected dependency boundaries.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:rss` README](../rss/README.md)
- [`:core:ranking` README](../ranking/README.md)
- [`:core:prefs` README](../prefs/README.md)
- [`:core:playback` README](../playback/README.md)
