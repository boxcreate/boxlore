# `:core:database`

## Purpose

Owns the main Room database, entities, DAOs, type converters, and migrations for podcasts, queue items, listening history, downloads, RSS episodes, PI episode supplements (cutover dual-read), and the first-class local episode catalog (`local_episode_feeds` / `local_episodes`). It does not own repositories, ranking's separate Room database, playback services, download workers, or feature UI.

## Public API

- `BoxLoreDatabase` and its `getDatabase` factory.
- Entities: `PodcastEntity`, `ListeningHistoryEntity`, `ListeningSessionEntity`, `ListeningRollupEntity`, `DownloadedEpisodeEntity`, `RssEpisodeEntity`, `EpisodeSupplementEntity`, `EpisodeSupplementItemEntity`, `LocalEpisodeFeedEntity`, `LocalEpisodeEntity`, and `entities.QueueItem`.
- DAOs: `PodcastDao`, `ListeningHistoryDao`, `ListeningSessionDao`, `ListeningRollupDao`, `DownloadedEpisodeDao`, `RssEpisodeDao`, `EpisodeSupplementDao`, `LocalEpisodeCatalogDao`, and `dao.QueueDao`. `PodcastDao.setFeedUrl` writes a publisher HTTPS URL after library restore. Listening session/rollup DAOs can reassign a podcast id without changing episode/session identity for the exact legacy RSS repair. `EpisodeSupplementDao.listSupplements` is the export source for Missing episodes? opt-ins during cutover. `LocalEpisodeCatalogDao` pages the first-class PI local catalog (sticky `episodeId`, unique `(podcastId, guid)`). Unsubscribe sets `ttlExpiresAt`; a non-null TTL is not catalog-ready for live lists. `deleteCatalogIfExpired` rechecks `ttlExpiresAt` in the same transaction so a refresh that cleared TTL after the expiry scan is not deleted. Migration 31→32 copies extras **keeping episodeIds** and does not drop supplement tables.
- `EpisodeSupplementDao.replaceAll` / `upsertSupplementAndOptionalItems` keep validator rows and tip items in one Room transaction.
- `ListeningInsightsMaintenance` for rolling session/rollup maintenance (via `BoxLoreDatabase.listeningInsightsMaintenance()`).
- `ListeningRollupMerge` for pure session→rollup merge logic.
- `Converters` for Room type conversion.

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
  dao/
    QueueDao.kt
  entities/
    QueueItem.kt
```

## Dependencies

- Project dependencies: `:core:model`, `:core:network`.
- Libraries: Room runtime, Room KTX, Room compiler through KSP, and Gson.
- Reverse-edge rule: database must not depend on catalog, playback, downloads, designsystem, or feature modules.

## Threading / lifecycle

- `BoxLoreDatabase.getDatabase` is a process singleton created from application wiring.
- DAO operations expose suspend functions and flows; callers should keep large database work off the main thread.
- Feature modules should depend on ports or repositories rather than injecting `BoxLoreDatabase` directly.

## Persistence & identity

- Room filename `boxlore_database` stores user data and must remain stable.
- Legacy database rename behavior preserves installs that used `boxcast_database`.
- Entity/table schemas and migrations are release-critical persisted contracts.
- Package root is `cx.aswin.boxlore.core.database`.
- Ranking persistence is owned by `:core:ranking` in `adaptive_ranking_database`.

## Testing notes

- Unit tests live under `core/database/src/test`.
- `PodcastDaoInMemoryTest` verifies the in-memory Room DAO path when Android resources are available to JVM tests.
- `ListeningRollupMergeTest` covers session→rollup merge, including the empty-sessions guard.
- `EpisodeToDomainArtworkTest` covers blank item `imageUrl` falling back to the show image on RSS and supplement `toEpisode`.
- Prefer repository or port fakes for feature tests instead of depending on Room directly.

```bash
./gradlew :core:database:testDebugUnitTest
```

## CI relevance

- `unit-tests.yml` runs database JVM tests with the project suite.
- Compile and KSP failures in this module block app and repository tests that depend on entities or DAOs.

## See also

- [`ARCHITECTURE.md`](../../ARCHITECTURE.md)
- [`docs/TESTING.md`](../../docs/TESTING.md)
- [`:core:catalog` README](../catalog/README.md)
- [`:core:ranking` README](../ranking/README.md)
