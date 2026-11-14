# `:core:catalog`

## Purpose

Owns catalog orchestration: API and Podcast Index access through `PodcastRepository`, subscriptions and their saved feed addresses, show-page episode sources, episode links and shares, cloud sync, library backup and import, notification registration, content slates, cross-promotion, chapters, transcripts and install referrals. It also holds the application-scoped bridges workers and services use.

It does not own playback, download workers, Compose UI, the Room schema ([`:core:database`](../database/README.md)), feed parsing and local feed copies ([`:core:rss`](../rss/README.md)), network DTOs ([`:core:network`](../network/README.md)) or ranking storage ([`:core:ranking`](../ranking/README.md)).

## Public API

### Repository and discovery

- `PodcastRepository`: the one application-scoped repository for search, trending, recommendations, vibes, Home bootstrap, similar episodes, briefings, content catalog v3, feedback and episode lists. It builds `api` and `apiV2` lazily on one cached OkHttp client.
- Episode-list sources: `rss:` shows read the RSS table; a followed show with a ready local catalog reads Room only; others read the server's list (`/v2/episodes`, mapped to local owner IDs). `getEpisodesPaginated`, `getEpisodeWindow` and `getEpisodes` never read a feed copy.
- Limits: pages are capped at 100 (`clampPageLimit`), `getEpisodes` is a 200-episode window, and `TimedLruCache` bounds the in-memory caches.
- `syncSubscriptions`: each show's latest episode, from Room for ready catalogs, cached extras for opted-in shows, else `/sync` in calls of at most 20 IDs, two at a time, because the API serves only 40 IDs per call.
- `loadPiEpisodesForSupplement`: the strict Podcast Index baseline that feed-only extras are compared with; when it fails the extras refresh stops.
- Discovery uses `/v2` routes while the discovery switch is on, else v1. Show search, typeahead, onboarding, content catalog, feedback, `/sync` and OPML lookups stay on v1.
- `DiscoveryEpisodeIds` (`RoomDiscoveryEpisodeIds` in production) maps recipe IDs in `/v2` answers to the phone's owner IDs before callers use them, and sends seeds and exclusions as recipe IDs.
- A failed `/v2` request never falls back to v1. It keeps the last good answer (`V2DiscoveryLastGood`; `RecommendationsLastGood` keeps picks per country across restarts), else marks Home results `failed` so screens keep what they show.
- `/v2` answers are cached under `v2|` keys, apart from v1 answers.
- `DiscoveryRouting` and `BriefingLinks` give code outside the repository the same switch and ID translation.
- `RolloutSwitches`: feed-first show pages (off by default, fixed per process), the upgrade repair, the daily address check and `/v2` discovery (on by default). Off pauses a job without marking it done.
- `ChapterRepository`, `TranscriptRepository`, `ChapterOfflineStorage` and `TranscriptOfflineStorage`: chapters and transcripts, remote, from descriptions, and saved for downloads.
- `shownotes.ShowNotesParser`: publisher HTML links, handles and description chapters, without fetching pages.

### Subscriptions and feed addresses

- `SubscriptionRepository`: follows, per-show settings and notification toggles; changes mark the row for the next sync push. Unfollowing records `unsubscribedAt` and starts the local catalog's 14-day countdown.
- `toggleSubscription(podcast, shownFollowed)` takes its direction from what the caller showed; it never adds a second follow, and Subscribe never unfollows.
- `FeedAddressUpdater` (`SubscriptionRepository.feedAddresses`) is the only writer of saved feed addresses, for every path: recovery, re-checks, moved feeds, reports, the daily check, auto-download, restores and cloud pulls.
- One address write is one transaction (`PodcastDao.replaceFeedAddress`): both addresses, `previousFeedUrl`, the source (`FeedUrlSources`), the check time, cleared validators and a dirty mark. It is a compare-and-set on the address read; `rss:` shows and non-HTTPS addresses are refused.
- A cloud pull never restores an address a confirmed correction replaced (`KEPT_CORRECTION`); merges use `PodcastDao.upsertKeepingFeedAddress`.
- Address writes fail closed: the show is recorded as pending registration (`PendingNotificationRegistrations`) first, and nothing is written (`NOT_SAVED`) if that fails.
- `SubscriptionForegroundSync`: the foreground pass. It sweeps expired catalogs, reads at most 10 due feeds (two at a time, visible show first), sends not-ready shows to `/sync` in chunks of 10 (six at a time) and recovers missing addresses at most daily.
- `requestRefresh` respects a cooldown, `refreshNow` (pull to refresh) skips it, and `requestCatalogIngest` reads a newly followed show's feed on the application scope.
- `DirectFeedRecovery`: after a failed read, reports it and tries a replacement address, saving it only once it reads. `FeedFixToggles` stay on without an answer.
- `FeedAddressDailyCheck`: asks `/v2/shows/feeds` daily per followed show and swaps only a confirmed move that reads; follows not yet pushed wait.
- `FeedReporter` / `FeedReportLimiter`: broken-feed reports, once per show a day and 20 a day, with `FOLLOWED_SHOWS_RESERVE` kept for followed shows. Use the app's one limiter.
- `RenumberedShowFollows` (`SubscriptionRepository.renumberedFollows`, rules in `RenumberedFollowMerge`) keeps one follow when Podcast Index renumbers a show.
- `SubscribedEpisodeCatalog`: routes the local catalog port to the Podcast Index or RSS adapter; with a `FeedMoveFollower` it saves permanent feed moves.
- `UpgradeAddressRepair`: the one-time post-upgrade address repair run by the app's `UpgradeRepairWorker`. Each step is saved as it finishes, so process death resumes at the next.
- `LegacyRssRepair`: a one-time pass turning old OPML `rss:` rows into Podcast Index shows on an exact feed-URL or GUID match.
- `FolderRepository` / `RoomFolderRepository`: folders and auto-organize; deleting a folder never unfollows its shows.

### Show pages and episode sources

- `showPageSource(feedId)`: `RSS`, `FOLLOWED_CATALOG`, `FEED` (feed-first on, a Podcast Index show not in fallback) or `SERVER_LIST`.
- `getShowPageEpisodes` is the only reader of the feed-first stored copy, so a copy no longer read never goes stale under other callers.
- `FeedFirstShowPages` reads the feed into the copy: offline shows the copy; a failed read is retried once and reported (never for a followed show), then the page uses the server's list (`FALLBACK`) until reopened.
- In-show search (`searchEpisodes`) runs on the phone or against the server's list, never v1 `episodes/search`.

### Episode links and shares

- `resolveEpisodeLink` (`EpisodeLinkResolver`): stored row, trusted alias, API, then one feed read for a `needsFeed` answer; results are `Found`, `NotFound` or `Unavailable`. `getEpisode` follows the same order without the feed read.
- `EpisodeAliasReads` / `RoomEpisodeAliasReads`: trusted alias reads for links, pushes and Android Auto.
- `EpisodeSharePreparer` never shares a phone-only ID; `EpisodeShareRegistrar.registerOnce` waits at most `DEFAULT_TIMEOUT_MS` and hands failures to the app's retry worker.
- `InstallReferrerManager`: parses Play referrers into a `ReferralIntent`, opens the shared page only on a fresh install, and reports attribution through `onInstallReferrerResolved`.

### Cloud sync

- `sync.UserSyncCoordinator`: bounded dirty-row pushes, pulls, account-switch purge and `deleteCloudSyncData`; results are `SyncResult` and `CloudSyncUiStatus`. For a linked watch, `linkedDeviceRows` gives the followed shows and newest history rows, and `applyLinkedDeviceChanges` merges the watch's changes by the pull rules and marks them for the next push.
- `SubscriptionSyncResolver`, `HistorySyncResolver` (shields the playing episode) and `QueueSyncResolver` (one entry per episode) merge pulls; `QueueItemHydration` builds pulled queue rows.
- `SyncEpisodeIdentity` carries identity beside each row's stored ID; a one-time catch-up runs after an upgrade or account switch, and `requestIdentityCatchUp()` asks again. Rules: [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md) §5.
- `sync.ListeningSync`: listening syncs under this installation's listening device ID, never the sync device ID; days count as sent only once the server accepts them. Wire fields: [`core/network/README.md`](../network/README.md).

### Backup and import

- `backup.LibraryBackupManager`: JSON export and import (subscriptions, folders, every stored history row, preferences, ranking, episode identity, listening), and OPML export and import.
- Identity and listening restores only add, never reverse local decisions, and change nothing when repeated.
- OPML import matches Podcast Index before creating an `rss:` row; a transient lookup failure defers the feed. OPML export writes saved publisher addresses only.

### Notifications

- `PodcastNotificationRegistrar` (`SubscriptionRepository.registerPodcastNotifications`): re-registers a show and waits for Firebase; outcomes `DONE`, `DROPPED` or `RETRY`.
- RSS notifications need consent per feed URL (`DeviceRssNotificationConsent`); `DeviceRssNotificationRegistrations` journals registrations and `RssNotificationReconciler` publishes and cleans them. Activation fails closed without the journal.
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
- Sync pushes run one at a time (`pushMutex`); address and renumbering writes read and write in one transaction.

## Persistence & identity

Never rename these:

- SharedPreferences: `boxlore_discovery_last_good` (keys `v2_recommendations|<personal or home>|<country>`, each with `|fallback`), `boxlore_feed_reports`, `boxlore_notification_reregistrations`, `boxlore_upgrade_repair_report`, `content_catalog_cache`, and `boxlore_player` / `boxlore_referrer_prefs`.
- `noBackupFilesDir`: `upgrade_repair_notifications`, and the RSS consent and registration journals (URL fingerprints only).
- `FeedUrlSources` values: `feed_move`, `podcast_lookup`, `cloud_sync`, `feed_report`, `backup_restore`, `registry`, or a `registry:` source.
- RTDB `tracked_podcasts/<show ID>` holds only `title`, `imageUrl` and an optional HTTPS `feedUrl`; RSS rows use per-device keys. FCM topics are `new_ep_<show ID>`, with `rss:` mapped to `rss_` plus a feed fingerprint.
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
