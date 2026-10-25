# `:core:database`

## Purpose

Owns the main Room database, entities, DAOs, type converters, and migrations for podcasts, queue items, listening history, downloads, RSS episodes, PI episode supplements (cutover dual-read), the first-class local episode catalog (`local_episode_feeds` / `local_episodes`), and episode identity storage: aliases for every stored episode ID, shared like and completion state, and per-show progress of the one-time feed address repair (see [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md)). It does not own repositories, ranking's separate Room database, playback services, download workers, or feature UI.

## Public API

- `AutoDownloadDao.seedExisting` seeds both `local_episodes` and `rss_episodes` in the activation transaction. `RssEpisodeDao.getByAudioUrl` supports exact push hydration when a GUID is unavailable. Existing tables/keys/schema version are unchanged; `RSS_REFRESH_AUTOMATIC` is a new value in the existing freshness capability field.
- `PodcastDao.getRssNotificationRows` includes every canonical `rss:` row, including disabled/unsubscribed rows and stale source metadata, so notification registration migration can clean up device-owned remote state. This adds no schema migration.

- `PodcastScoring` provides interpretable listening, like, freshness, preference, and optional subscription-recency signals. Canonical Your Shows ranking disables its legacy subscription-recency term so `:core:ranking` can apply one bounded recency policy after normalization; other callers retain the existing `600 / (1 + hours/24)` default.
- `BoxLoreDatabase` and its `getDatabase` factory.
- Entities: `PodcastEntity`, `ListeningHistoryEntity`, `ListeningSessionEntity`, `ListeningRollupEntity`, `DownloadedEpisodeEntity`, `RssEpisodeEntity`, `EpisodeSupplementEntity`, `EpisodeSupplementItemEntity`, `LocalEpisodeFeedEntity`, `LocalEpisodeEntity`, `FolderEntity`, `PodcastFolderCrossRef`, `AutoDownloadShowEntity`, `AutoDownloadReleaseEntity`, `EpisodeAliasEntity`, `EpisodeGroupStateEntity`, `UpgradeRepairShowEntity`, `entities.QueueItem`, and `entities.QueueMetadataEntity`. String values for alias states, alias provenance and repair states live in `EpisodeAliasState`, `EpisodeAliasProvenance` and `UpgradeRepairState`; stored values are never renamed.
- DAOs: `EpisodeIdentityDao` (alias and group-state reads and writes; owner choice and resolution rules belong to callers), `UpgradeRepairDao` (pending shows and per-show progress), `PodcastDao`, `ListeningHistoryDao`, `ListeningSessionDao`, `ListeningRollupDao`, `DownloadedEpisodeDao`, `RssEpisodeDao`, `EpisodeSupplementDao`, `LocalEpisodeCatalogDao`, `FolderDao`, and `dao.QueueDao`. `PodcastDao.upsert` and `LocalEpisodeCatalogDao.upsertFeed` keep a stored row's Room v39 columns (see Version 39 below). `ListeningHistoryDao.insertIfAbsent` lets service-only playback establish a row without replacing concurrent UI/history state, `enrichMetadataIfMissing` fills incomplete service seeds without touching progress or likes, `completeFromPlayback` atomically updates only playback-owned completion columns, `reopenProgress` resets completion and advances progress for intentional replays, and `updateLastPlayedAt` refreshes session recency. `PodcastDao.markPodcastSyncedIfUnchanged`, `ListeningHistoryDao.markHistorySyncedIfUnchanged`, and `QueueDao.markQueueSyncedIfSequenceMatches` provide multi-column optimistic concurrency flag clearing during cloud sync. `PodcastDao.getDirtyCountFlow` and `ListeningHistoryDao.getDirtyCountFlow` observe pending unsynced changes to drive reactive cloud sync ignition. `PodcastDao.clearAllSubscriptionsForAccountSwitch` cleanly purges subscriptions for account switches without wiping cached episodes. Routine progress updates ignore already-completed rows so a delayed snapshot cannot undo terminal state. `PodcastDao.setFeedUrl` writes a publisher HTTPS URL found on the device (address recovery, auto-download, library restore) and marks the row dirty so the next sync push sends it; an unchanged address writes nothing. Listening sessions can reassign an episode id while preserving stable session UUIDs; catalog restore repair merges rollup primary-key collisions before deleting the legacy episode id. Session/rollup DAOs can also reassign a podcast id for the exact legacy RSS repair. `EpisodeSupplementDao.listSupplements` is the export source for Missing episodes? opt-ins during cutover. `LocalEpisodeCatalogDao` pages the first-class PI local catalog (sticky `episodeId`, unique `(podcastId, guid)`); `findOwners` reports which (show, key) holds each episode ID so writers check before `upsertEpisodes` replaces a row. `LocalEpisodeCatalogDao.getEpisodesAfter` and `RssEpisodeDao.getEpisodesAfter` query chronological forward-continuation episodes for same-show queue playback. Unsubscribe sets `ttlExpiresAt`; a non-null TTL is not catalog-ready for live lists. `deleteCatalogIfExpired` rechecks `ttlExpiresAt` in the same transaction so a refresh that cleared TTL after the expiry scan is not deleted. Migration 31→32 copies extras **keeping episodeIds** and does not drop supplement tables. Migration 32→33 adds `customGenre` and `customGenreIcon` columns to `podcasts` for subscribed podcast tag and icon overrides. Migration 33→34 creates `folders` and `podcast_folder_cross_ref` tables with `CASCADE` on `folderId` so deleting folders never deletes shows. Migration 34→35 adds `showPodcastGrid` column to `folders`. Migration 33→35 provides direct upgrade from version 33. Migration 35→36 adds `chaptersUrl` and `transcriptUrl` columns to `downloaded_episodes` for offline chapter and transcript persistence. Migration 36→37 adds `unsubscribedAt`, `isDirty`, and `syncedAt` columns to `podcasts` with index on `isDirty` for subscription tombstoning; adds `likedAt` column to `listening_history` with indices on `isDirty` and `lastPlayedAt` for decoupled likes and delta tracking; and creates `queue_metadata` table for monotonic queue sequence versioning, device origin tracking, dirty sync state, and recent removed episode tombstones.
- `EpisodeSupplementDao.replaceAll` / `upsertSupplementAndOptionalItems` keep validator rows and tip items in one Room transaction.
- `ListeningInsightsMaintenance` for rolling session/rollup maintenance (via `BoxLoreDatabase.listeningInsightsMaintenance()`).
- `ListeningRollupMerge` for pure session→rollup merge logic.
- `Converters` for Room type conversion.

- Version 38 adds `auto_download_shows` activation boundaries, a durable `auto_download_releases` pending/handled/removed ledger, and `downloaded_episodes.downloadOrigin`. Existing non-smart downloads migrate to protected `unknown` provenance; existing smart downloads retain `smart`. Migration seeds currently cached episodes as handled, preserving all episode IDs and existing audio. `AutoDownloadDao` atomically changes show settings and activation state and retains removal tombstones after download rows are deleted.

- Version 39 adds episode identity storage and the columns the feed-first show page and address repair need. Nothing reads them yet. `MIGRATION_38_39` (`EpisodeIdentityMigration`) only adds: no existing row's ID or column value changes, and `rss_episodes`, the supplement tables and their `CHECK(id < 0)` constraints are left untouched.
  - New tables: `episode_aliases` (`aliasId` primary key, `podcastId`, `catalogKey`, `recipeId`, `state`, `owner`, `provenance`, `createdAt`; indexed on show + key and show + recipe ID; no foreign key, so `LocalEpisodeCatalogDao.deleteCatalog` leaves aliases in place), `episode_group_state` (shared like and completion settings per show + key; empty after the upgrade) and `upgrade_repair_shows` (steps 1–3, attempts, state and the server's answer per followed show).
  - New columns, each with a default: `podcasts.epNs`, `feedUrlSource`, `feedUrlCheckedAt`, `transportUrl`, `previousFeedUrl`; `local_episode_feeds.followed`, `lastOpenedAt`, `archiveComplete`, `epNs`, `showMetadata`, `showMetadataAt`; `local_episodes.legacy`; `queue_metadata.recentRemovedEpisodes` (rich queue removals beside the unchanged `recentRemovedEpisodeIds`).
  - Seeding:
    - `local_episodes.legacy` has the schema default 1, so every existing catalog row reads as legacy without being rewritten (catalog rows carry long descriptions; rewriting them all would copy the whole table through the journal). Room binds the field on every write, so rows the app writes store 0, and a row replaced from a feed read loses `legacy`, which is intended: its key now comes from the feed. Raw SQL that inserts into `local_episodes` must set it.
    - Each Podcast Index show's `epNs` is set to its show ID, and `local_episode_feeds.followed` copies the follow state.
    - Each stored episode ID of a Podcast Index show gets one alias (`EpisodeAliasSeeding`). Catalog rows are stored under their key re-trimmed with the recipe set, with the recipe ID from `EpisodeRecipeId` in `:core:model`. A row that already holds a recipe-range ID keeps it, as the episode's own alias when it equals the computed recipe ID and as `ambiguous` otherwise. A catalog row whose ID history, the queue or downloads hold under another show is `ambiguous` too: builds before the collision guard let a show that reads the same feed take another show's rows. Synthetic `id:<own ID>` keys, phone-minted (negative) IDs keyed by their own audio address (the GUID may be hidden, and its saved item is deleted once Podcast Index lists the episode), and IDs found only in history, the queue or downloads are `unresolved` until the show's feed is read. `briefing_*` IDs and `rss:` shows get no alias.
    - One trusted alias per show + key is marked owner: the latest listening (a history row with no play or like time counts as none), else a completed download, else the ID that equals its recipe ID (a served ID), else the oldest catalog row.
    - Each followed Podcast Index show gets a pending repair row; a fresh install starts with none.
  - Whole-row writers keep the v39 columns of a stored row, so the one-time seed survives catalog refreshes, subscribe, restore and sync. `LocalEpisodeCatalogDao.upsertFeed` keeps `followed`, `lastOpenedAt`, `archiveComplete`, `epNs`, `showMetadata` and `showMetadataAt`. `PodcastDao.upsert` always keeps `epNs`, and keeps `feedUrlSource`, `feedUrlCheckedAt`, `transportUrl` and `previousFeedUrl` while the write leaves `feedUrl` unchanged (they describe that address). A new row takes the values it is given. These columns change only through their own updates; `replaceFeedRow` and `upsertRow` write every column as given and exist for those two methods. Follow and unfollow don't update `followed` yet, so whatever reads it first must also write it.
  - The JSON library backup leaves the new `podcasts` columns out of its export (`LibraryBackupFieldPolicy` in `:core:catalog`), so the backup format is unchanged.

## Internal structure

```text
src/main/java/cx/aswin/boxlore/core/database/
  BoxLoreDatabase.kt
  Converters.kt
  DownloadedEpisodeDao.kt
  DownloadedEpisodeEntity.kt
  ListeningHistoryDao.kt
  ListeningHistoryEntity.kt
  ListeningInsightsMaintenance.kt
  ListeningRollupDao.kt
  ListeningRollupMerge.kt
  ListeningSessionDao.kt
  PodcastDao.kt
  PodcastEntity.kt
  RssEpisodeDao.kt
  RssEpisodeEntity.kt
  EpisodeSupplementDao.kt
  EpisodeSupplementEntity.kt
  EpisodeSupplementItemEntity.kt
  LocalEpisodeFeedEntity.kt
  LocalEpisodeEntity.kt
  LocalEpisodeCatalogDao.kt
  EpisodeAliasEntity.kt         # alias entity plus state and provenance values
  EpisodeAliasSeeding.kt        # which alias the v39 upgrade seeds for each stored ID (pure)
  EpisodeGroupStateEntity.kt
  EpisodeIdentityDao.kt
  EpisodeIdentityMigration.kt   # migration 38→39
  UpgradeRepairShowEntity.kt
  UpgradeRepairDao.kt
  dao/
    QueueDao.kt
  entities/
    QueueItem.kt
    QueueMetadataEntity.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:network`.
- Libraries: Room runtime, Room KTX, Room compiler through KSP, and Gson.
- Reverse-edge rule: database must not depend on catalog, playback, downloads, designsystem, or feature modules.

## Threading / lifecycle

- `BoxLoreDatabase.getDatabase` is a process singleton created from application wiring.
- DAO operations expose suspend functions and flows; callers should keep large database work off the main thread.
- Feature modules should depend on ports or repositories rather than injecting `BoxLoreDatabase` directly.
- Completed episode ids are read in pages of 1,000 inside one transaction (`ListeningHistoryDao.getCompletedEpisodeIds`). The Flow re-reads them on any `listening_history` change. A single cursor over tens of thousands of ids failed with "Couldn't read row … from CursorWindow".
- `FolderDao.addPodcastToFolderIfExists` and `setPodcastsForFolder` do nothing when the folder no longer exists, so a link racing a folder deletion cannot violate the cross-ref foreign key.
- `BoxLoreDatabase.getDatabase` re-checks the instance inside its lock so two threads never open separate Room instances on the same file.
- Migrations run on the first open after an update, from whichever caller opens the database first. That can be the playback service (`PlaybackHistoryStore`) with no `AppContainer`, so a migration may only use the database and `:core:model` (the 38→39 seeding uses `EpisodeRecipeId` from there). The 38→39 upgrade rewrites no catalog row; on the JVM it took about 0.4 s for 20,000 catalog rows with 2–20 KB descriptions (a 240 MB database), where rewriting every row to mark it legacy had copied about 240 MB through the journal.

## Persistence & identity

- Room filename `boxlore_database` stores user data and must remain stable.
- Legacy database rename behavior preserves installs that used `boxcast_database`.
- `BriefingArtworkCompatibility` repairs legacy numeric Brief artwork references on open and on later history, queue and download writes (including cloud/backup restores). Region aliases resolve to the shared named-resource contract in `:core:model`. Only artwork columns change; progress, likes, audio, queue order and sync flags are preserved. Conditional SQLite triggers are idempotent and installed after schema migrations; schema version and table identities stay unchanged.
- Entity/table schemas and migrations are release-critical persisted contracts. Never edit a migration or an exported schema once a phone may have run it: Room refuses to open a database whose schema hash differs at the same version. Later schema changes go to a new version (v40 and up).
- `BoxLoreDatabase.builder` is the one database configuration, used by `getDatabase` and the upgrade tests. Upgrades from version 12 on run `BoxLoreDatabase.ALL_MIGRATIONS`; a version with no path fails to open with an error instead of silently erasing history, queue, downloads and likes. Versions 1–11, which predate every migration, and opening a newer file with an older build (a downgrade) still recreate the database empty.
- Episode aliases, group state, the `legacy` mark and alias provenance are permanent: old local IDs (Podcast Index, phone-minted negative and recipe IDs) keep resolving through them after their catalog rows are evicted. Alias `catalogKey` values are the recipe-trimmed keys of [`docs/EPISODE_IDS.md`](../../docs/EPISODE_IDS.md).
- Package root is `cx.aswin.boxlore.core.database`.
- Ranking persistence is owned by `:core:ranking` in `adaptive_ranking_database`.

## Testing notes

- Unit tests live under `core/database/src/test`.
- `BriefingArtworkCompatibilityTest` covers upgrade/reopen, restored rows, all region aliases, recursive-trigger safety, unchanged non-Brief artwork and preservation of playback/download/queue state. Opening recreates trigger definitions transactionally, including after a database comes from another application variant, so future restores use the current authority and aliases.
- `PodcastDaoExtendedTest.setFeedUrlMarksACorrectedAddressForTheNextPush` covers a corrected address marking the row dirty and an unchanged one staying clean.
- `PodcastDaoInMemoryTest` verifies the in-memory Room DAO path and RSS notification cleanup selection, including disabled/unsubscribed rows with stale source metadata.
- `ListeningRollupMergeTest` covers session→rollup merge, including the empty-sessions guard. `ListeningHistoryDaoInMemoryTest` covers insert-if-absent conflict safety and playback completion updates preserving listener-owned fields such as likes. `ListeningHistoryDao.repairPlaybackArtworkLinks` and `QueueDao.repairPlaybackArtworkLinks` swap player-only `.collage` artwork links saved by older builds for the episode's cover (local, then RSS catalogue), else the show's, else the row's own show cover, else null. A history row is marked dirty only when every dead link found a real cover, so an emptied field never overwrites a cover another device uploaded; `QueueDao.repairPlaybackArtworkLinksForSync` marks a changed queue dirty in the same transaction without changing its sequence, so the cloud copy drops the dead links, and its order is untouched. `ListeningHistoryDaoInMemoryTest` and `QueueDaoInMemoryTest` cover each source, the row's own show cover, emptied rows staying clean, the queue marked dirty only when a row changed, untouched rows and a second run changing nothing.
- `RoomV39UpgradeTest` builds databases from the exported 36, 37 and 38 schemas, and a phone that came up through the older migrations (from 30, with the `CHECK` constraints, `queue_metadata` SQL defaults and supplement-copied rows), fills them with every kind of stored ID (Podcast Index, negative, served recipe-range IDs in the catalog, history, queue and downloads, SQLite-trimmed, synthetic and address keys, Brief and `rss:` IDs, reserved IDs from the collision guard), then opens them through `BoxLoreDatabase.builder`, so Room runs the production migrations and its own schema check. It asserts that every pre-existing value is unchanged, the untouched tables keep their exact SQL, the Brief artwork triggers don't interfere, aliases, owners, namespaces and repair rows are seeded as described above (including IDs held under another show, address keys whose saved item is gone, and each owner rule), eviction keeps aliases, later catalog refreshes and podcast writes keep the seeded columns, a fresh install and an upgraded phone have identical tables, columns, defaults and indices, a failure inside the seeding rolls the file back to v38 and the next open succeeds, a second seeding pass changes nothing, a missing upgrade path fails without erasing data, and downgrades and pre-12 versions still open empty. `EpisodeAliasSeedingTest` covers every seeding rule without SQLite, including the recipe trim cases; `EpisodeIdentityDaoTest` covers the new DAOs. `RoomSchemaFixture` and `LegacyRows` build these fixtures; reuse them for later upgrades.
- `Migration36To37Test` verifies Room schema upgrade from version 36 to 37, adding `unsubscribedAt`, `isDirty`, `syncedAt` to `podcasts`, `likedAt` and indices to `listening_history`, creating `queue_metadata`, and preserving existing row data.
- `PodcastScoringTest` covers subscribe-recency priors (peak, 72h decay, missing `subscribedAt`) and the recency-free listening prior used by canonical Your Shows scoring.
- `ConvertersEpisodeIdentityTest` covers embedded `Episode` JSON (queue, history and other rows) through `Converters`' Gson path: rows written before `recipeId` and `catalogKey` existed read back unchanged with both null, an episode without them is written without their keys (the bytes an older build reads), and both round trip at full `Long` precision.
- `EpisodeToDomainArtworkTest` covers blank item `imageUrl` falling back to the show image on RSS and supplement `toEpisode`.
- Prefer repository or port fakes for feature tests instead of depending on Room directly.

```bash
./gradlew :core:database:testDebugUnitTest
```
- `ListeningHistoryDaoInMemoryTest` reads completed ids across page boundaries.
- `AutoDownloadMigrationTest` opens a v37 database through `BoxLoreDatabase.builder`, so it runs 37→38 and 38→39 as a phone does.

## CI relevance

- `unit-tests.yml` runs database JVM tests with the project suite.
- Compile and KSP failures in this module block app and repository tests that depend on entities or DAOs.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:ranking` README](../ranking/README.md)

### Release optimization

Release checks both generated Room implementations and Gson-backed converter/backup model field names, default constructors and TypeToken signatures. DAOs and unrelated entities remain eligible for R8 optimization; database/storage identities stay unchanged.
