# `:core:database`

## Purpose

Owns the main Room database (`BoxLoreDatabase`): its entities, DAOs, type converters, migrations and exported schemas. It stores shows, listening history and timed listening, the queue, downloads and auto-download state, RSS episodes, feed-only extras, local copies of Podcast Index feeds, folders, and episode identity (aliases, shared like and completion state, and the feed address repair's per-show progress).

It does not own repositories, sync, feed parsing or ranking. Catalog and sync logic live in [`:core:catalog`](../catalog/README.md), local feed copies are read and refreshed in [`:core:rss`](../rss/README.md), and ranking keeps its own database in [`:core:ranking`](../ranking/README.md).

## Public API

- `BoxLoreDatabase.getDatabase(context)`: the process-wide database, one accessor per DAO.
- Tables, entities and DAOs:

| Table | Entity | DAO | Stores / used by |
| :--- | :--- | :--- | :--- |
| `podcasts` | `PodcastEntity` | `PodcastDao` | Shows, follow state, per-show settings, saved feed addresses, sync flags; catalog, `:core:rss`, playback, downloads |
| `listening_history` | `ListeningHistoryEntity` | `ListeningHistoryDao` | Progress, likes, completion and listening dates per stored episode ID; playback, sync, backup |
| `listening_sessions` | `ListeningSessionEntity` | `ListeningSessionDao` | Timed plays with origin, source, output and release date; playback, insights, backup |
| `listening_rollups` | `ListeningRollupEntity` | `ListeningRollupDao` | Daily summaries per origin, day and episode, with `ListeningDayTotals`; insights, sync, backup |
| `listening_days_pushed` | `PushedListeningDayEntity` | `ListeningPushDao` | Each day as this phone knows the cloud holds it; `ListeningInsightsMaintenance` |
| `played_shows` | `PlayedShowEntity` | `PlayedShowDao` | Every show played, followed or not; playback, sync, backup |
| `queue_items`, `queue_metadata` | `entities.QueueItem`, `entities.QueueMetadataEntity` | `dao.QueueDao` | Queue order, sequence, sync state and recent removals; `QueueRepository`, Smart Queue, sync |
| `downloaded_episodes` | `DownloadedEpisodeEntity` | `DownloadedEpisodeDao` | Download status, file, origin, offline chapters and transcripts; `:core:downloads`, playback |
| `auto_download_shows`, `auto_download_releases` | `AutoDownloadShowEntity`, `AutoDownloadReleaseEntity` | `AutoDownloadDao` | Per-show activation time and a pending/handled/removed release ledger; `:core:downloads` |
| `rss_episodes` | `RssEpisodeEntity` | `RssEpisodeDao` | Episodes of `rss:` shows; `:core:rss`, playback |
| `episode_supplements`, `episode_supplement_items` | `EpisodeSupplementEntity`, `EpisodeSupplementItemEntity` | `EpisodeSupplementDao` | Feed-only extras under a Podcast Index show, with feed validators; `:core:rss` |
| `local_episode_feeds`, `local_episodes` | `LocalEpisodeFeedEntity`, `LocalEpisodeEntity` | `LocalEpisodeCatalogDao` | Local copies of Podcast Index feeds; `:core:rss`, catalog |
| `folders`, `podcast_folder_cross_ref` | `FolderEntity`, `PodcastFolderCrossRef` | `FolderDao` | Custom folders; catalog's folder repository |
| `episode_aliases`, `episode_group_state` | `EpisodeAliasEntity`, `EpisodeGroupStateEntity` | `EpisodeIdentityDao` | Every stored ID of an episode and its shared like and completion; `EpisodeListenerStore`, `:core:rss` linkers, sync, backup |
| `upgrade_repair_shows` | `UpgradeRepairShowEntity` | `UpgradeRepairDao` | Per-show progress of the feed address repair; catalog's upgrade repair |

- `EpisodeListenerStore` (`BoxLoreDatabase.episodeListenerStore()`): reads and writes one listener state per episode across its stored IDs, built on `EpisodeGroup` and `EpisodeListenerProjection`. Rules: [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md#5-aliases-on-the-phone).
- `ListeningInsightsMaintenance` (`BoxLoreDatabase.listeningInsightsMaintenance()`): rolls sessions into daily summaries, claims listening stored without an origin (`ListeningOrigins`), lists days to push and applies cloud days. Listening sync itself: [`:core:catalog`](../catalog/README.md).
- Pure rules, testable without SQLite: `ListeningDates`, `ListeningDayTotals`, `ListeningRollupMerge`, `EpisodeListenerProjection`.
- `PodcastScoring`, `ScorablePodcast` and `sortedByListening`: listening, like and freshness signals for `:core:ranking`, Smart Queue, Smart Downloads and Most listened.
- `PodcastDao.replaceFeedAddress(FeedAddressWrite)`: the one transactional feed address write, a compare-and-set on the address the caller read. Only `:core:catalog`'s `FeedAddressUpdater` calls it.
- `EpisodeIdentityDao.knownRecipeIds`: an ID's own recipe ID or a trusted alias's, never a guess.
- `Converters`: Room type conversion, with Gson for embedded JSON such as `Episode` and `QueueRemovalIdentity`.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/database/
  BoxLoreDatabase.kt                 # database, builder, ALL_MIGRATIONS (12→29 inline)
  BoxLoreDatabaseMigrations.kt       # migration SQL for 29→37
  AutoDownloadMigration.kt           # 37→38
  EpisodeIdentityMigration.kt        # 38→39
  ListeningDataMigration.kt          # 39→40
  ListeningTotalsMigration.kt        # 40→41
  ListeningReleaseKnownMigration.kt  # 41→42
  BriefingArtworkCompatibility.kt    # open callback and triggers for Brief artwork
  Converters.kt
  PodcastEntity.kt, PodcastDao.kt, FeedAddressWrite.kt, PodcastScoring.kt
  ListeningHistoryEntity.kt, ListeningHistoryDao.kt, ListeningDates.kt
  ListeningSessionEntity.kt, ListeningSessionDao.kt, ListeningSessionValues.kt
  ListeningRollupEntity.kt, ListeningRollupDao.kt, ListeningRollupMerge.kt, ListeningDayTotals.kt
  PushedListeningDayEntity.kt, ListeningPushDao.kt, ListeningInsightsMaintenance.kt
  PlayedShowEntity.kt, PlayedShowDao.kt
  DownloadedEpisodeEntity.kt, DownloadedEpisodeDao.kt
  AutoDownloadState.kt, AutoDownloadDao.kt
  RssEpisodeEntity.kt, RssEpisodeDao.kt
  EpisodeSupplementEntity.kt, EpisodeSupplementItemEntity.kt, EpisodeSupplementDao.kt
  LocalEpisodeFeedEntity.kt, LocalEpisodeEntity.kt, LocalEpisodeCatalogDao.kt
  FolderEntity.kt, PodcastFolderCrossRef.kt, FolderDao.kt
  EpisodeAliasEntity.kt              # alias entity, state and provenance values
  EpisodeAliasSeeding.kt             # v39 alias seeding rules (pure)
  EpisodeGroupStateEntity.kt, EpisodeIdentityDao.kt, EpisodeRecipeIdReads.kt
  EpisodeGroup.kt, EpisodeListenerProjection.kt, EpisodeListenerStore.kt
  UpgradeRepairShowEntity.kt, UpgradeRepairDao.kt
  dao/QueueDao.kt
  entities/QueueItem.kt, QueueMetadataEntity.kt, QueueRemovalIdentity.kt
src/upgradeFixtures/java/            # RoomSchemaFixture, LegacyRows (shared with :core:catalog tests)
schemas/                             # exported Room schemas, versions 30–42
```

## Dependencies

- `api`: `:core:model`, `:core:network`, Room runtime and Room KTX.
- `ksp`: Room compiler, with `room.schemaLocation` set to `schemas/`.
- `implementation`: Gson.
- Forbidden: catalog, rss, playback, downloads, designsystem, analytics or feature modules.

## Threading / lifecycle

- `getDatabase` creates one instance per process and re-checks it inside its lock, so two threads never open the same file twice.
- DAOs expose `suspend` functions and Flows; Room runs them off the main thread.
- Queries that take ID lists are called with at most 500 IDs (900 for listening dates), under SQLite's 999 bound-parameter limit.
- Completed IDs and the full stored history are read in pages inside one transaction, so a long history never overflows a single cursor window.
- Migrations run on the first open after an update, which may be the playback service without `AppContainer`, so migration code may use only the database and `:core:model`.
- Features reach data through ports and repositories, not `BoxLoreDatabase`; `scripts/ci/check-feature-no-boxlore-database.sh` guards Home and Info.

## Persistence & identity

- The database file is `boxlore_database`. A legacy `boxcast_database` file (with its journal files) is renamed on first open. Storage identities: [`ARCHITECTURE.md`](../../ARCHITECTURE.md#identity-and-storage).
- Table and column names are stable; other modules, backups and sync rely on them.
- Episode IDs, aliases, the owner rule, orphan rematch, RSS switch links and renumbered shows follow [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md). Aliases and group state survive catalog eviction.
- Whole-row writers keep columns they don't own: `PodcastDao.upsert` keeps `epNs` (and the address columns while `feedUrl` is unchanged), `LocalEpisodeCatalogDao.upsertFeed` keeps the follow, cleanup, namespace and show-metadata columns, and `ListeningHistoryDao.upsert` keeps listening dates.
- `listening_history.firstPlayedAt` and `finishedAt`: null means not yet, 0 means before boxlore recorded dates, otherwise epoch ms.
- `local_episodes.legacy` defaults to 1 for rows that existed before v39; raw SQL inserting into `local_episodes` must set it.
- `local_episode_feeds.followed` follows the subscription and the unsubscribe TTL (`setTtlAndFollowed`); a feed copy with a TTL is not ready for live lists.
- `ListeningRollupEntity.THIS_DEVICE` (`''`) marks summaries from before v40 until this installation claims them.
- `BriefingArtworkCompatibility` repairs legacy Brief artwork references on open and through triggers on history, queue and download writes; only artwork columns change.

Stored values, never renamed:

- `EpisodeAliasState`: `trusted`, `ambiguous`, `unresolved`.
- `EpisodeAliasProvenance`: `upgrade_*`, `feed_*`, `heal_*`, `orphan_rematch`, `catalog_key_match`, `rss_source_link`, `rss_source_audio_match`, `rss_follow_upgrade`, `renumbered_show_match`, `cloud_sync`.
- `UpgradeRepairState`: `pending`, `done`, `gave_up`.
- `AutoDownloadReleaseEntity` states: `pending`, `handled`, `removed`.
- `DownloadedEpisodeEntity.downloadOrigin`: `unknown`, `manual`, `auto`, `smart`.
- `ListeningSessionValues`: sources `lore`, `smart_queue`; outputs `speaker`, `headphones`, `bluetooth`, `car`, `cast`.
- `LocalEpisodeFeedEntity.feedOrder` (`LocalFeedOrder`): `newest_first`, `oldest_first`, `mixed`.

Schema versions (the current version is 42):

| Version | Migration adds |
| :--- | :--- |
| 30 | `listening_sessions` and `listening_rollups` |
| 31 | `episode_supplements` and `episode_supplement_items` |
| 32 | `local_episode_feeds` and `local_episodes`, copying supplement extras with their episode IDs |
| 33 | `podcasts.customGenre` and `customGenreIcon` |
| 34 | `folders` and `podcast_folder_cross_ref` (deleting a folder never deletes shows) |
| 35 | `folders.showPodcastGrid`; 33→35 is also a direct path |
| 36 | `downloaded_episodes.chaptersUrl` and `transcriptUrl` |
| 37 | `podcasts` sync columns, `listening_history.likedAt` and indices, `queue_metadata` |
| 38 | `auto_download_shows`, `auto_download_releases` and `downloaded_episodes.downloadOrigin` |
| 39 | `episode_aliases`, `episode_group_state`, `upgrade_repair_shows`, feed and namespace columns, `local_episodes.legacy`, `queue_metadata.recentRemovedEpisodes`; seeds aliases and owners ([rules](../../docs/EPISODE_IDS.md#5-aliases-on-the-phone)) |
| 40 | Listening dates, session origin and details, rollups keyed by origin, `listening_days_pushed`, `played_shows` |
| 41 | Nine richer daily totals on `listening_rollups` and `listening_days_pushed` (0 means not known) |
| 42 | `releaseKnownMs` on the same two tables (0 means not known) |

Migration rules:

- Never edit a shipped migration or exported schema: Room refuses to open a file whose schema differs at the same version. Any schema change takes a new version (43 next) and a new exported schema.
- Migrations only add: no stored ID or value changes, and new columns carry defaults so existing rows need no rewrite.
- Add each new migration to `BoxLoreDatabase.ALL_MIGRATIONS`. `BoxLoreDatabase.builder` is the one configuration, used by `getDatabase` and the upgrade tests.
- A version from 12 on with no path fails to open instead of erasing data. Only versions 1–11 and downgrades still recreate the database empty.
- Test each new version with an upgrade test that builds the previous version from its exported schema (`RoomSchemaFixture`), opens it through `builder` and checks every stored value survives (`RoomV39UpgradeTest` to `RoomV42UpgradeTest`). For a large change, also compare the upgraded file with a fresh install, as the v39 and v40 tests do.

## Testing notes

Tests live in `core/database/src/test` (JUnit 5, Robolectric Room); shared upgrade fixtures are in `src/upgradeFixtures/java`. Feature tests should use port or repository fakes instead of Room.

| Area | Test classes |
| :--- | :--- |
| Upgrades and migrations | `RoomV39UpgradeTest`, `RoomV40UpgradeTest`, `RoomV41UpgradeTest`, `RoomV42UpgradeTest`, `AutoDownloadMigrationTest`, `Migration36To37Test`, `DownloadedEpisodeMigration35To36Test`, `CustomFolderMigration33To34Test`, `CustomGenreMigration32To33Test`, `LocalCatalogMigration31To32Test` |
| Episode identity | `EpisodeAliasSeedingTest`, `EpisodeIdentityDaoTest`, `EpisodeListenerProjectionTest`, `EpisodeListenerStoreTest`, `EpisodeRecipeIdReadsTest`, `SwitchedShowListeningTest` |
| Shows and feed addresses | `PodcastDaoInMemoryTest`, `PodcastDaoExtendedTest`, `PodcastFeedAddressDaoTest`, `PodcastScoringTest` |
| History and listening | `ListeningHistoryDaoInMemoryTest`, `ListeningDatesTest`, `ListeningRollupMergeTest`, `ListeningInsightsDaoInMemoryTest`, `PlayedShowDaoTest` |
| Queue | `QueueDaoInMemoryTest`, `QueueRichRemovalsTest` |
| Catalog, RSS, downloads | `LocalEpisodeCatalogDaoOwnersTest`, `RssEpisodeDaoInMemoryTest`, `DownloadedEpisodeDaoInMemoryTest`, `AutoDownloadDaoTest` |
| Converters and artwork | `ConvertersEpisodeIdentityTest`, `EpisodeToDomainArtworkTest`, `BriefingArtworkCompatibilityTest` |

- `:core:catalog`'s `UpgradeGateTest` and `:core:rss`'s `ShowPageFeedCatalogTest` exercise these DAOs end to end.

```bash
./gradlew :core:database:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs these tests with the project suite, includes the module in the coverage gate, and runs the feature database boundary check.
- Compile or KSP failures here block every module that uses the entities or DAOs.
- Release keeps Room's generated code and the Gson-read fields; `release-contracts.yml` verifies the optimized build.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:rss` README](../rss/README.md)
- [`:core:ranking` README](../ranking/README.md)
