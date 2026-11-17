# `:core:catalog`

## Purpose

Owns `PodcastRepository`, subscriptions, feed addresses, show-page sources, episode links and shares, cloud sync, backup and import, notification registration, content slates, cross-promotion, chapters, transcripts, install referrals and application-scoped worker and service bridges.

It does not own playback, downloads, Compose UI, Room schema ([`:core:database`](../database/README.md)), feed parsing and copies ([`:core:rss`](../rss/README.md)), network DTOs ([`:core:network`](../network/README.md)) or ranking storage ([`:core:ranking`](../ranking/README.md)).

## Public API

### Repository and discovery

- `PodcastRepository`: the one application-scoped repository for discovery, Home bootstrap, briefings, content catalog v3, feedback and episode lists; lazy `api`/`apiV2` share one cached OkHttp client.
- Episode-list sources: RSS table for `rss:` shows, Room only for ready followed catalogs, else the server's list (`/v2/episodes`, local owner IDs).
- Limits: 100 per page (`clampPageLimit`), a 200-episode `getEpisodes` window, bounded `TimedLruCache` caches. `getEpisodeWindow` around an episode gives it and up to 199 newer; on the server's list it searches in the asked sort from near the last found row, empty if not listed.
- `syncSubscriptions`: each show's latest episode from Room (ready catalogs), cached extras (opted-in shows), else `/sync` in 20-ID calls, two at a time.
- `loadPiEpisodesForSupplement`: the strict Podcast Index baseline for feed-only extras; failure stops the extras refresh.
- Discovery uses `/v2` when its switch is on, else v1; show search, typeahead, onboarding, content catalog, feedback, `/sync` and OPML lookups stay v1.
- `DiscoveryEpisodeIds` (`RoomDiscoveryEpisodeIds` in production) maps recipe IDs in `/v2` answers to the phone's owner IDs before callers use them, and sends seeds and exclusions as recipe IDs.
- Failed `/v2` requests never fall back to v1: they keep the last good answer (`V2DiscoveryLastGood`; `RecommendationsLastGood` per country, Home picks marked `fromLastGood`), else mark Home results `failed` (keep what's shown), like failed v1 picks.
- `/v2` answers are cached under `v2|` keys, apart from v1 answers.
- `DiscoveryRouting` and `BriefingLinks` give code outside the repository the same switch and ID translation.
- `RolloutSwitches`: feed-first show pages (default off, fixed per process); upgrade repair, daily address check, `/v2` discovery (default on). Off pauses without marking done.
- `ChapterRepository`, `TranscriptRepository`, `ChapterOfflineStorage` and `TranscriptOfflineStorage`: chapters and transcripts, remote, from descriptions, and saved for downloads.
- `shownotes.ShowNotesParser`: publisher HTML links, handles and description chapters, without fetching pages.

### Subscriptions and feed addresses

- `SubscriptionRepository`: follows, per-show settings, notification toggles; changes mark the row dirty; unfollowing sets `unsubscribedAt`, starting the catalog's 14-day countdown.
- `toggleSubscription(podcast, shownFollowed)` takes its direction from `shownFollowed`: never a second follow; Subscribe never unfollows.
- `FeedAddressUpdater` (`SubscriptionRepository.feedAddresses`): the only writer of saved addresses, on every path including restores and cloud pulls.
- One address write is one compare-and-set transaction (`PodcastDao.replaceFeedAddress`): both addresses, `previousFeedUrl`, source (`FeedUrlSources`), check time, cleared validators, dirty mark; refuses `rss:` shows and non-HTTPS addresses.
- A cloud pull never restores an address a confirmed correction replaced (`KEPT_CORRECTION`); merges use `PodcastDao.upsertKeepingFeedAddress`.
- Address writes fail closed: without a `PendingNotificationRegistrations` record first, nothing is written (`NOT_SAVED`).
- `SubscriptionForegroundSync`: the foreground pass. It sweeps expired catalogs, reads at most 10 due feeds (two at a time, visible show first), sends not-ready shows to `/sync` in chunks of 10 (six at a time) and recovers missing addresses at most daily.
- `requestRefresh` respects a cooldown, `refreshNow` (pull to refresh) skips it, and `requestCatalogIngest` reads a newly followed show's feed on the application scope.
- `DirectFeedRecovery`: reports a failed read, tries a replacement address, saved only if it reads; `FeedFixToggles` stay on without an answer.
- `FeedAddressDailyCheck`: asks `/v2/shows/feeds` daily per followed show and swaps only a confirmed move that reads; follows not yet pushed wait.
- `FeedReporter` / `FeedReportLimiter`: broken-feed reports, once per show a day and 20 a day, with `FOLLOWED_SHOWS_RESERVE` kept for followed shows. Use the app's one limiter.
- `RenumberedShowFollows` (`SubscriptionRepository.renumberedFollows`, rules in `RenumberedFollowMerge`) keeps one follow when Podcast Index renumbers a show.
- `SubscribedEpisodeCatalog`: routes the local catalog port to the Podcast Index or RSS adapter; a `FeedMoveFollower` saves permanent feed moves.
- `UpgradeAddressRepair`: one-time post-upgrade address repair run by the app's `UpgradeRepairWorker`; finished steps survive process death.
- `LegacyRssRepair`: one-time pass turning old OPML `rss:` rows into Podcast Index shows on exact feed-URL or GUID match.
- `FolderRepository` / `RoomFolderRepository`: folders and auto-organize; deleting a folder never unfollows its shows.

### Show pages and episode sources

- `showPageSource(feedId)`: `RSS`, `FOLLOWED_CATALOG`, `FEED` (feed-first on, a Podcast Index show not in fallback) or `SERVER_LIST`.
- Only show-page reads use the feed-first stored copy: `getShowPageEpisodes`, `getWholeShowPage`, in-show search and `getEpisodeWindow` around an episode.
- `FeedFirstShowPages` reads the feed into the copy, shown offline; a failed read is retried once and reported (not for followed shows), then the page uses the server's list (`FALLBACK`) until reopened.
- In-show search (`searchShowEpisodes`; `searchEpisodes` for matches only) reads stored episodes or the server's list (`/v2/episodes`), never v1 `episodes/search`; an unreadable list throws; `searchedNewest` counts rows searched when more exist. `showSearchCache` (not the page cache) keeps one show's progress: newest 1,000 rows whole, older slim; `showSearchMatches` keeps older matches whole.
- `getWholeShowPage` (Select all, Mark all) reads the show page's sources, throwing on a failed page instead of ending early, and `ShowListTooLongException` past `/v2/episodes`' last offset. Server's-list pages come only from `/v2/episodes`; shows `/v2` doesn't list read v1 `/episodes` throughout.

### Episode links and shares

- `resolveEpisodeLink` (`EpisodeLinkResolver`): stored row, trusted alias, API, then one feed read on `needsFeed`, giving `Found`, `NotFound` or `Unavailable`; `getEpisode` skips the feed read.
- `EpisodeAliasReads` / `RoomEpisodeAliasReads`: trusted alias reads for links, pushes and Android Auto.
- `EpisodeSharePreparer` never shares a phone-only ID; `EpisodeShareRegistrar.registerOnce` waits at most `DEFAULT_TIMEOUT_MS`; failures go to the app's retry worker.
- `InstallReferrerManager`: parses Play referrers to `ReferralIntent`, opens the shared page on fresh installs only, reports attribution via `onInstallReferrerResolved`.

### Cloud sync

- `sync.UserSyncCoordinator`: bounded dirty-row pushes, pulls, account-switch purge, `deleteCloudSyncData`; results: `SyncResult`, `CloudSyncUiStatus`. Watch link: `linkedDeviceRows` (follows, newest history), `applyLinkedDeviceChanges` (merged by pull rules, marked dirty).
- `SubscriptionSyncResolver`, `HistorySyncResolver` (shields the playing episode) and `QueueSyncResolver` (one entry per episode) merge pulls; `QueueItemHydration` builds pulled queue rows.
- `SyncEpisodeIdentity` carries identity beside each row's stored ID; catch-up runs once after upgrade or account switch, again on `requestIdentityCatchUp()`. Rules: [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md) §5.
- `sync.ListeningSync`: uses the listening device ID, not the sync one; days count as sent once accepted. Wire fields: [`core/network/README.md`](../network/README.md).

### Backup and import

- `backup.LibraryBackupManager`: JSON (subscriptions, folders, all stored history, preferences, ranking, episode identity, listening) and OPML, export and import.
- Identity and listening restores only add, never reverse local decisions, and change nothing when repeated.
- OPML import matches Podcast Index before creating an `rss:` row; a transient lookup failure defers the feed. OPML export writes saved publisher addresses only.

### Notifications

- `PodcastNotificationRegistrar` (`SubscriptionRepository.registerPodcastNotifications`): re-registers a show and waits for Firebase; outcomes `DONE`, `DROPPED` or `RETRY`.
- RSS notifications need per-feed-URL consent (`DeviceRssNotificationConsent`); `DeviceRssNotificationRegistrations` journals them, `RssNotificationReconciler` publishes and cleans up; no journal, no activation.
- `canPresentEpisodeNotification` checks saved URL, follow, toggle and consent before showing a notification.

### Content and cross-promotion

- `content.ContentOrchestrator`, `ContentContextEngine` and `ServerGroupedSectionProvider` assemble slates from content catalog v3; `content.CuratedMoods` lists curated-mood IDs.
- `crosspromo.CrossPromotionDetector` finds episodes promoting another show, and `CrossPromotionResolver` resolves that show.
- `EngagementPromptCoordinator`: at most one proactive prompt per session, only at a finish moment.
- `privacy.ConsentManager`: crash-reporting and analytics consent.

### Shared wiring and ports

- `SharedAppDependencies` / `SharedAppDependenciesHolder`: application-scoped instances for workers and services.
- `RoomLocalCatalog` and `RoomEpisodeOfflineLookup`: production implementations of the domain ports.
- `ports.*` keep catalog free of playback. Backups read `ListeningHistoryBackupPort.exportStoredHistory`, never the screens' capped list.
- Android Auto `auto_*` strings live here; `auto_app_name` stays `boxlore`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/catalog/
  PodcastRepository*.kt        # repository, split by concern (DiscoveryV2, ShowPages, EpisodeLinks…)
  Discovery*.kt, BriefingLinks.kt, EpisodeLinkResolver.kt, EpisodeShareRegistration.kt
  Subscription*.kt, SubscribedEpisodeCatalog.kt, *FolderRepository.kt
  FeedAddress*.kt, DirectFeedRecovery.kt, FeedReports.kt, FeedFirstShowPages.kt
  Renumbered*.kt, Upgrade*.kt, LegacyRssRepair.kt, RolloutSwitches.kt
  PodcastNotificationRegistrar.kt, PendingNotificationRegistrations.kt, RssNotification*.kt
  SharedAppDependencies.kt, InstallReferrerManager.kt, ChapterRepository.kt, TranscriptRepository.kt
  backup/  content/  crosspromo/  logic/  ports/  privacy/  shownotes/  sync/
src/main/res/                  # auto_* strings and Android Auto drawables
```

## Dependencies

- `api`: `:core:domain`, `:core:database`, `:core:prefs`, `:core:rss`.
- `implementation`: `:core:model`, `:core:network`, `:core:ranking`.
- Libraries: Retrofit, OkHttp, Gson, kotlinx-serialization-json, coroutines, DataStore, Jsoup, Firebase Realtime Database and Messaging, Play Install Referrer.
- Forbidden: playback, downloads, designsystem, analytics or feature modules; `:core:ranking` and `:core:analytics` never go through `api`.

## Threading / lifecycle

- Instances are application-scoped, built by `AppContainer`; workers and services use `SharedAppDependenciesHolder`.
- Repository APIs are `suspend` and do network and database work on IO.
- `PodcastRepository` is built on the main thread, so its preferences and clients open lazily.
- `SubscriptionForegroundSync` pauses its timer outside the foreground; manual and ingest passes are not tied to a screen.
- Sync pushes run one at a time (`pushMutex`); address and renumbering read-modify-writes are single transactions.

## Persistence & identity

Never rename these:

- SharedPreferences: `boxlore_discovery_last_good` (keys `v2_recommendations|<personal or home>|<country>`, each with `|fallback`), `boxlore_feed_reports`, `boxlore_notification_reregistrations`, `boxlore_upgrade_repair_report`, `content_catalog_cache`, and `boxlore_player` / `boxlore_referrer_prefs`.
- `noBackupFilesDir`: `upgrade_repair_notifications`, and the RSS consent and registration journals (URL fingerprints only).
- `FeedUrlSources` values: `feed_move`, `podcast_lookup`, `cloud_sync`, `feed_report`, `backup_restore`, `registry`, or a `registry:` source.
- RTDB `tracked_podcasts/<show ID>` holds only `title`, `imageUrl` and optional HTTPS `feedUrl`; RSS rows use per-device keys. FCM topics: `new_ep_<show ID>`; `rss:` becomes `rss_` plus feed fingerprint.
- Sync metadata version 3 (`IDENTITY_SYNC_METADATA_VERSION`) marks the identity catch-up done.
- Work names, `boxlore_renumbered_shows` and other identities: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#identity-and-storage). Episode IDs: [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).
- JSON backup fields stay optional and restore-compatible; the current version is 10 (`LibraryBackupDirectFeedLogic.VERSION`):

| Version | Adds |
| :--- | :--- |
| 6 | `directFeedOptIns` (read; exported empty) |
| 7 | `episodeIdentity`: aliases, group states, renumbered shows |
| 8 | `listening`; history `firstPlayedAt` and `finishedAt` |
| 9 | Richer daily summary totals |
| 10 | Each summary's `releaseKnownMs` |

- `LibraryBackupFieldPolicy` keeps the feed identity and address columns out of exports.

## Testing notes

Tests live in `core/catalog/src/test` (JUnit 5, Robolectric Room, MockWebServer, no MockK); recorded `/v2` answers are in `src/test/resources/fixtures/v2`.

| Area | Test classes |
| :--- | :--- |
| Episode lists | `PodcastRepositoryCatalogTest`, `PodcastRepositoryEpisodeSupplementTest`, `PodcastRepositoryRequestBoundsTest` |
| Discovery | `PodcastRepositoryV2DiscoveryTest`, `RoomDiscoveryEpisodeIdsTest`, `BriefingLinksTest` |
| Show pages | `FeedFirstShowPagesTest`, `PodcastRepositoryShowPagesTest` |
| Feed addresses | `FeedAddressUpdaterTest`, `FeedAddressDailyCheckTest`, `FeedReportsTest`, `DirectFeedRecoveryTest` |
| Renumbering | `RenumberedShowFollowsTest`, `RenumberedFollowMergeTest` |
| Upgrade | `UpgradeAddressRepairTest`, `UpgradeAddressRepairApiTest`, `UpgradeGateTest` |
| Foreground sync | `SubscriptionForegroundSyncTest`, `SubscriptionForegroundSyncIngestTest` |
| Subscriptions, folders | `SubscriptionRepositoryTest`, `RoomFolderRepositoryTest` |
| Links, shares | `EpisodeLinkResolverTest`, `EpisodeLinkRoomTest`, `EpisodeSharePreparerTest` |
| Cloud sync | `CloudSyncIdentityTest`, `SyncEpisodeIdentityTest`, `ListeningCloudSyncTest`, `sync/*ResolverTest` |
| Backup | `LibraryBackupExportTest`, `LibraryBackupIdentityRoomTest`, `LibraryBackupListeningRoomTest` |
| Notifications | `PodcastNotificationRegistrarTest`, `RssNotificationReconcilerTest` |
| Content, cross-promotion | `content/*Test`, `crosspromo/*Test`, `ShowNotesParserTest` |

```bash
./gradlew :core:catalog:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests, merges their Kover coverage and checks `:core:catalog:dependencyGuard`.
- `:core:testing` architecture guards check this README and the dependency rules.
- Release keeps only the reflectively used backup and cache models; `release-contracts.yml` verifies them.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:rss` README](../rss/README.md)
- [`:core:database` README](../database/README.md)
- [`:core:network` README](../network/README.md)
- [`:core:prefs` README](../prefs/README.md)
- [`:core:playback` README](../playback/README.md)
