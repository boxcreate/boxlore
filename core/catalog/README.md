# `:core:catalog`

## Purpose

Owns catalog orchestration: Podcast Index access through `PodcastRepository`, subscriptions, chapters, transcripts, content catalog (v3), cross-promotion, consent helpers, install-referrer handling, backup and restore, and shared dependency bridges used by workers and services. It does not own playback, download workers, Compose UI, Room schemas, RSS internals, or ranking model storage.

## Public API

- `PodcastRepository` coordinates Podcast Index calls, recommendation endpoints, curated vibes, home bootstrap, similar episodes (with content languages), content catalog/v3, and RSS delegation. For PI shows opted into **Missing episodes?**, episode list reads (`getEpisodes`, offset-0 `getEpisodesPaginated`, in-show `searchEpisodes`) union **cached** feed-only extras after the PI response. True `rss:` rows are unchanged. Pagination offsets use `EpisodePage.sourceCount` (PI size before extras). Matching a publisher feed uses `loadPiEpisodesForBaseline` (throws on HTTP failure) so extras are not treated as PI rows and wiped. UI lists still use `getEpisodesPaginated` (empty page on failure). `invalidateEpisodesCache` drops the 5-minute PI page cache for a feed (Podcast Info pull-to-refresh).
- `syncSubscriptions` (`POST /sync`) is PI-only for shows that are **not** opted in. Opted-in ids are omitted from the request; the map may include the newest cached supplement tip instead. Live feed freshen stays on `SubscriptionForegroundSync` and Podcast Info.
- Show search: legacy `searchPodcasts` / `searchPodcastsWithCorrection` keep calling `GET /search` (hybrid). New clients use `searchPodcastsTypeahead` (`GET /search/typeahead` / Meili) and `searchPodcastsGrouped` (parallel typeahead + hybrid → Matches / Also found). Concept search uses `searchSemanticGrouped` / `searchEpisodesSemantic` (`GET /search/semantic`: one CF embed → Qdrant podcasts + episodes; additive `feeds` + legacy `items`).
- `logic.mergeShowSearchResults` dedupes Meili vs hybrid by id / itunes / feed URL.
- `mapRegionForBriefing` maps content regions to briefing markets (`us` / `in` / `gb` / `global`; legacy `uk` → `gb`) via `ContentRegions.briefingMarket`.
- `SubscriptionRepository`, `ChapterRepository`, and `TranscriptRepository` expose catalog-adjacent data operations. `updateLatestEpisode` never replaces a newer Room tip with an older PI or feed item (`LatestEpisodeTipLogic`); same published date may replace when the episode id changes (PI catch-up). When the listener turns **show notifications** on, `SubscriptionRepository` writes RTDB `tracked_podcasts/{podcastIndexId}` (`title`, `imageUrl`, and HTTPS `feedUrl` only if the show is opted into Missing episodes?). Live RTDB rules allow those three keys only (`feedUrl` may be deleted); extra children are rejected. Subscribe does not write that map. Turning notifications **off** removes the `feedUrl` child (the tracked node stays). After a later opt-in while notifications are already on, `syncTrackedPodcastFeedUrl` patches `feedUrl` so the Check New Episodes Action can poll the publisher feed; if opt-in is no longer present it writes the payload without `feedUrl`.
- `SubscriptionForegroundSync` refreshes subscribed latest episodes (`syncSubscriptions` in chunks of 10). Cold start from Home still waits 2s for first paint; **Subscriptions appear** (including **Open app to** Subscriptions) and later app resumes call `requestRefresh()` with no extra delay so New Episodes is a live `/sync`, not Room cache from an earlier session. In-flight passes coalesce; a second fetch inside 5 minutes is skipped; a 15-minute loop keeps a warm process from going stale. PI `/sync` chunks and publisher-feed refreshes are both capped at concurrency 6. Shows opted into Podcast Info **Missing episodes?** skip PI `/sync` for the tip: first promote from the **local** supplement cache, then refresh publisher feeds (open Home chip first). Unchanged ETag/Last-Modified skips the feed GET and the 1000-episode PI baseline; a changed feed still rematches against `loadPiEpisodesForBaseline` (strict, not an empty UI fallback page) so serialized catalogs are not stored as extras. Direct-feed tip writes use `updateLatestEpisode(..., markAsNew = true)` so the shared `rssHasNewEpisodes` badge drives Your Shows NEW and the New episodes hero chip the same way true-RSS does; plain PI `/sync` tips do not set that flag. A successful persist emits `directFeedRefreshed` so Home can swap the open chip in place. The same pass also patches RTDB `feedUrl` for opted-in shows that already have notifications on. `:app` starts it after onboarding from `BoxLoreAppRoot`; Home's ViewModel may call `ensureStarted` again (idempotent); Library `onScreenResume` calls `requestRefresh`.
- `content.ContentOrchestrator`, `ServerGroupedSectionProvider`, `ContentContextEngine`, and related content contracts assemble discovery slates from catalog/v3 (no live `content/sections/v1` client).
- `content.CuratedMoods` is the shared catalog of curated-mood IDs/titles used by Home daypart rails and Explore For You chips (`getCuratedVibe`).
- `backup.LibraryBackupManager` imports and exports library data, OPML, listening history, ranking backup payloads, and global prefs including `contentLanguages`, `subscriptionSort` (may be `Manual`), optional `subscriptionManualOrder`, and optional `homePinnedPodcastIds`. JSON **v6** also exports PI shows opted into **Missing episodes?** (`directFeedOptIns`: podcast id + HTTPS publisher URL). Restore writes the opt-in stub, patches RTDB `feedUrl` when notifications are on (before the publisher GET), refreshes those publisher feeds against a 1000-episode PI baseline, promotes the newest tip (`markAsNew`), then PI `/sync` for everyone else and `refreshCatalogIfNeeded` for true `rss:` rows. Null/blank Gson `podcastId` / `feedUrl` rows are skipped. Older JSON without the field stays compatible (no opt-in restore). Older backups without the Manual-order / pin lists still parse; Manual with a missing id list seeds from the current Smart list on first use.
- `SharedAppDependencies` and `SharedAppDependenciesHolder` expose application-scoped instances to workers and services.
- `InstallReferrerManager` parses Play Install Referrer deep links and exposes optional `onInstallReferrerResolved` (channel + raw referrer). `:app` wires that callback into analytics; catalog must not depend on `:core:analytics`.
- `RoomLocalCatalog` implements `LocalCatalogPort`; `RoomEpisodeOfflineLookup` implements `EpisodeOfflineLookupPort`.
- `ports.ListeningHistoryBackupPort` and `ports.SmartDownloadSyncPort` keep backup and download seams out of UI modules.
- `:core:rss`, `:core:domain`, `:core:database`, and `:core:prefs` are re-exported where existing public signatures require those types.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/catalog/
  PodcastRepository.kt
  PodcastRepositoryContentCache.kt
  PodcastRepositoryContentMapping.kt
  PodcastRepositoryMappers.kt
  PodcastRepositoryNetworkLookups.kt
  PodcastRepositoryEpisodeSupplements.kt
  PodcastRepositoryRecommendations.kt
  PodcastRepositoryStreams.kt
  PodcastEpisodeSupplementMerge.kt
  LatestEpisodeTipLogic.kt
  TrackedPodcastRtdbLogic.kt
  EpisodeMapper.kt
  SubscriptionRepository.kt
  ChapterRepository.kt
  TranscriptRepository.kt
  SharedAppDependencies.kt
  SubscriptionForegroundSync.kt
  RoomLocalCatalog.kt
  RoomEpisodeOfflineLookup.kt
  EngagementPromptCoordinator.kt
  InstallReferrerManager.kt
  logic/
  backup/
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
- Existing coverage includes `PodcastRepositoryCatalogTest`, `PodcastRepositoryEpisodeSupplementTest` (offset-0 merge, later-page PI-only, `mergeSupplements = false` PI baseline, search union, opted-in `/sync` skip), `LatestEpisodeTipLogicTest`, `TrackedPodcastRtdbLogicTest` (HTTPS `feedUrl` on notification RTDB rows), `ShowSearchMergeTest`, `DirectFeedSyncOrderTest`, `LibraryBackupDirectFeedLogicTest` / `LibraryBackupDirectFeedRestoreTest` (JSON v6 opt-in export targeting, Gson-null skip, restore-before-`/sync` plan, RTDB patch before feed refresh), `LibraryBackupManualOrderPinsTest` (Manual sort + id lists round-trip; older JSON without lists), `SubscriptionForegroundSyncLogicTest` (cooldown / subscriptions-first fetch), `InstallReferrerManager` channel derivation / attribution callback seams, `SubscriptionForegroundSync` delay / `requestRefresh` / periodic / direct-feed concurrency and failure isolation, content orchestration tests, content signal enrichment, grouped sections, recent section intent storage, cross-promotion detection, transcript behavior, and dependency-holder behavior.
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
