package cx.aswin.boxlore.core.database

import android.database.Cursor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * Room migration 38→39: episode aliases, shared group state, the upgrade repair table and the
 * new feed columns (docs/EPISODE_IDS.md §5).
 *
 * Runs once on every upgrading phone, inside Room's migration transaction, and may also run from
 * the playback service's first database open, so it needs nothing but the database and the
 * recipe in `:core:model`. It only adds: no existing row's ID or column value changes.
 *
 * 1. Adds the new columns, each with a default, and creates the new tables with exactly the SQL
 *    Room exports for them (`schemas/…/39.json`); Room checks both against the entities after
 *    the migration and refuses to open on any difference.
 * 2. Every existing `local_episodes` row reads as `legacy` through the column's schema default
 *    (1), so no catalog row is rewritten: rows carry long descriptions, and rewriting them all
 *    would copy the whole table through the journal. Room binds `legacy` on every write, so rows
 *    the app writes from now on store 0.
 * 3. Seeds each Podcast Index show's episode namespace as its show ID (`podcasts.epNs`,
 *    `local_episode_feeds.epNs`) and `local_episode_feeds.followed` from the follow state.
 *    `PodcastDao.upsert` and `LocalEpisodeCatalogDao.upsertFeed` keep these on later writes.
 * 4. Seeds one alias per stored episode ID ([EpisodeAliasSeeding]): catalog rows first, then IDs
 *    found only in history, the queue or downloads. `rss_episodes` and the supplement tables are
 *    not touched.
 * 5. Marks one owner per episode among its trusted aliases: the ID with the most recent listener
 *    activity, else one with a completed download, else the one that equals its recipe ID (a
 *    served ID), else the oldest catalog row.
 * 6. Adds a pending upgrade repair row for each followed Podcast Index show.
 *
 * Never edit this migration once a phone may have run it; later schema changes go to v40.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39; section 4, synthetic keys;
 * section 11, upgrade repair).
 * COMPAT(own-ids): the upgrade path from every shipped build. Permanent: never remove (MIGRATION.md §5).
 */
internal object EpisodeIdentityMigration {
    private val NEW_COLUMNS = listOf(
        "podcasts" to "epNs INTEGER",
        "podcasts" to "feedUrlSource TEXT",
        "podcasts" to "feedUrlCheckedAt INTEGER NOT NULL DEFAULT 0",
        "podcasts" to "transportUrl TEXT",
        "podcasts" to "previousFeedUrl TEXT",
        "queue_metadata" to "recentRemovedEpisodes TEXT",
        "local_episode_feeds" to "followed INTEGER NOT NULL DEFAULT 0",
        "local_episode_feeds" to "lastOpenedAt INTEGER NOT NULL DEFAULT 0",
        "local_episode_feeds" to "archiveComplete INTEGER NOT NULL DEFAULT 0",
        "local_episode_feeds" to "epNs INTEGER",
        "local_episode_feeds" to "showMetadata TEXT",
        "local_episode_feeds" to "showMetadataAt INTEGER NOT NULL DEFAULT 0",
        "local_episodes" to "legacy INTEGER NOT NULL DEFAULT 1",
    )

    /** Verbatim from the exported v39 schema, so fresh and upgraded databases match. */
    private val NEW_TABLES = listOf(
        "CREATE TABLE IF NOT EXISTS `episode_aliases` (`aliasId` TEXT NOT NULL, `podcastId` TEXT NOT NULL, " +
            "`catalogKey` TEXT, `recipeId` INTEGER, `state` TEXT NOT NULL, `owner` INTEGER NOT NULL DEFAULT 0, " +
            "`provenance` TEXT NOT NULL, `createdAt` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`aliasId`))",
        "CREATE INDEX IF NOT EXISTS `index_episode_aliases_podcastId_catalogKey` " +
            "ON `episode_aliases` (`podcastId`, `catalogKey`)",
        "CREATE INDEX IF NOT EXISTS `index_episode_aliases_podcastId_recipeId` " +
            "ON `episode_aliases` (`podcastId`, `recipeId`)",
        "CREATE TABLE IF NOT EXISTS `episode_group_state` (`podcastId` TEXT NOT NULL, `catalogKey` TEXT NOT NULL, " +
            "`likedAt` INTEGER NOT NULL DEFAULT 0, `likeClearedAt` INTEGER NOT NULL DEFAULT 0, " +
            "`completedAt` INTEGER NOT NULL DEFAULT 0, `completeClearedAt` INTEGER NOT NULL DEFAULT 0, " +
            "`version` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`podcastId`, `catalogKey`))",
        "CREATE TABLE IF NOT EXISTS `upgrade_repair_shows` (`podcastId` TEXT NOT NULL, " +
            "`completedStep` INTEGER NOT NULL DEFAULT 0, `attempts` INTEGER NOT NULL DEFAULT 0, " +
            "`state` TEXT NOT NULL DEFAULT 'pending', `checkedFetchUrl` TEXT, `checkedTransportUrl` TEXT, " +
            "`checkedFeedSource` TEXT, `checkedEpNs` INTEGER, `lastAttemptAt` INTEGER NOT NULL DEFAULT 0, " +
            "`lastError` TEXT, PRIMARY KEY(`podcastId`))",
    )

    private const val CATALOG_ROWS = """
        SELECT episodeId, podcastId, guid, audioUrl FROM local_episodes ORDER BY rowid
    """

    private const val LISTENER_ROWS = """
        SELECT episodeId, podcastId, 0 AS source, rowid AS position FROM listening_history
        UNION ALL SELECT episodeId, podcastId, 1, rowid FROM queue_items
        UNION ALL SELECT episodeId, podcastId, 2, rowid FROM downloaded_episodes
        ORDER BY source, position
    """

    private const val INSERT_ALIAS = """
        INSERT OR IGNORE INTO episode_aliases
            (aliasId, podcastId, catalogKey, recipeId, state, owner, provenance, createdAt)
        VALUES (?, ?, ?, ?, ?, 0, ?, ?)
    """

    private const val MARK_SINGLE_OWNERS = """
        UPDATE episode_aliases SET owner = 1
        WHERE state = 'trusted' AND NOT EXISTS (
            SELECT 1 FROM episode_aliases o
            WHERE o.podcastId = episode_aliases.podcastId
              AND o.catalogKey = episode_aliases.catalogKey
              AND o.state = 'trusted'
              AND o.aliasId != episode_aliases.aliasId
        )
    """

    private const val SHARED_KEYS = """
        SELECT podcastId, catalogKey FROM episode_aliases
        WHERE state = 'trusted'
        GROUP BY podcastId, catalogKey
        HAVING COUNT(*) > 1
    """

    /**
     * The owner among one episode's trusted aliases (docs/EPISODE_IDS.md §5). A history row with
     * no play or like time (0) counts as no listening, so it never beats a completed download.
     */
    private const val CHOOSE_OWNER = """
        SELECT a.aliasId FROM episode_aliases a
        LEFT JOIN local_episodes le ON le.episodeId = a.aliasId
        WHERE a.podcastId = ? AND a.catalogKey = ? AND a.state = 'trusted'
        ORDER BY
            (
                SELECT NULLIF(MAX(h.lastPlayedAt, h.likedAt), 0) FROM listening_history h
                WHERE h.episodeId = a.aliasId
            ) DESC,
            EXISTS (
                SELECT 1 FROM downloaded_episodes d
                WHERE d.episodeId = a.aliasId AND d.status = ${DownloadedEpisodeEntity.STATUS_COMPLETED}
            ) DESC,
            a.aliasId = CAST(a.recipeId AS TEXT) DESC,
            le.rowid IS NULL, le.rowid ASC, a.aliasId ASC
        LIMIT 1
    """

    /** Runs the whole upgrade on [db]; [nowMillis] stamps the seeded aliases. */
    fun migrate(db: SupportSQLiteDatabase, nowMillis: Long,) {
        addColumns(db)
        NEW_TABLES.forEach(db::execSQL)
        seedNamespaces(db)
        val listenerRows = listenerRows(db)
        db.compileStatement(INSERT_ALIAS).use { insert ->
            seedCatalogAliases(db, insert, listenerShows(listenerRows), nowMillis)
            listenerRows.forEach { (episodeId, podcastId) ->
                EpisodeAliasSeeding.forListenerRow(episodeId, podcastId)?.let { insert.write(it, nowMillis) }
            }
        }
        markOwners(db)
        seedUpgradeRepair(db)
    }

    /** Adds each new column unless the table already has it, so a repeated run cannot fail on a duplicate. */
    private fun addColumns(db: SupportSQLiteDatabase) {
        val existing = mutableMapOf<String, Set<String>>()
        for ((table, definition) in NEW_COLUMNS) {
            val columns = existing.getOrPut(table) { tableColumns(db, table) }
            if (definition.substringBefore(' ') !in columns) {
                db.execSQL("ALTER TABLE $table ADD COLUMN $definition")
            }
        }
    }

    private fun tableColumns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            val name = cursor.getColumnIndexOrThrow("name")
            buildSet { while (cursor.moveToNext()) add(cursor.getString(name)) }
        }

    private fun seedNamespaces(db: SupportSQLiteDatabase) {
        val shows = showIds(db, "SELECT podcastId FROM podcasts WHERE sourceType != 'rss'")
        db.compileStatement("UPDATE podcasts SET epNs = ? WHERE podcastId = ?").use { update ->
            shows.forEach { (podcastId, showId) -> update.run(showId, podcastId) }
        }
        val catalogs = showIds(db, "SELECT podcastId FROM local_episode_feeds")
        db.compileStatement("UPDATE local_episode_feeds SET epNs = ? WHERE podcastId = ?").use { update ->
            catalogs.forEach { (podcastId, showId) -> update.run(showId, podcastId) }
        }
        db.execSQL(
            "UPDATE local_episode_feeds SET followed = 1 " +
                "WHERE podcastId IN (SELECT podcastId FROM podcasts WHERE isSubscribed = 1)",
        )
    }

    /** (podcastId, show ID) for each row of [sql] whose podcast ID is a canonical Podcast Index show ID. */
    private fun showIds(db: SupportSQLiteDatabase, sql: String): List<Pair<String, Long>> =
        db.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val podcastId = cursor.getString(0) ?: continue
                    EpisodeRecipeId.showIdOrNull(podcastId)?.let { add(podcastId to it) }
                }
            }
        }

    private fun SupportSQLiteStatement.run(showId: Long, podcastId: String,) {
        clearBindings()
        bindLong(1, showId)
        bindString(2, podcastId)
        executeUpdateDelete()
    }

    /** (episode ID, show) of every history, queue and download row, history first, each in table order. */
    private fun listenerRows(db: SupportSQLiteDatabase): List<Pair<String, String>> =
        db.query(LISTENER_ROWS).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    if (cursor.isNull(0) || cursor.isNull(1)) continue
                    add(cursor.getString(0) to cursor.getString(1))
                }
            }
        }

    /** The non-blank shows each episode ID is held under in history, the queue or downloads. */
    private fun listenerShows(rows: List<Pair<String, String>>): Map<String, Set<String>> =
        rows.filter { (_, podcastId) -> podcastId.isNotBlank() }
            .groupBy(keySelector = { it.first }, valueTransform = { it.second })
            .mapValues { (_, shows) -> shows.toSet() }

    private fun seedCatalogAliases(
        db: SupportSQLiteDatabase,
        insert: SupportSQLiteStatement,
        listenerShows: Map<String, Set<String>>,
        nowMillis: Long,
    ) {
        db.query(CATALOG_ROWS).use { cursor ->
            while (cursor.moveToNext()) catalogSeed(cursor, listenerShows)?.let { insert.write(it, nowMillis) }
        }
    }

    private fun SupportSQLiteStatement.write(seed: AliasSeed, nowMillis: Long,) {
        clearBindings()
        bindString(1, seed.aliasId)
        bindString(2, seed.podcastId)
        seed.catalogKey?.let { bindString(3, it) } ?: bindNull(3)
        seed.recipeId?.let { bindLong(4, it) } ?: bindNull(4)
        bindString(5, seed.state)
        bindString(6, seed.provenance)
        bindLong(7, nowMillis)
        executeInsert()
    }

    private fun markOwners(db: SupportSQLiteDatabase) {
        db.execSQL(MARK_SINGLE_OWNERS)
        val sharedKeys = db.query(SHARED_KEYS).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1)) }
        }
        db.compileStatement("UPDATE episode_aliases SET owner = 1 WHERE aliasId = ?").use { update ->
            for ((podcastId, catalogKey) in sharedKeys) {
                val owner = chooseOwner(db, podcastId, catalogKey) ?: continue
                update.clearBindings()
                update.bindString(1, owner)
                update.executeUpdateDelete()
            }
        }
    }

    private fun chooseOwner(db: SupportSQLiteDatabase, podcastId: String, catalogKey: String,): String? =
        db.query(CHOOSE_OWNER, arrayOf(podcastId, catalogKey)).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    private fun seedUpgradeRepair(db: SupportSQLiteDatabase) {
        val follows = showIds(db, "SELECT podcastId FROM podcasts WHERE isSubscribed = 1 AND sourceType != 'rss'")
        db.compileStatement(
            "INSERT OR IGNORE INTO upgrade_repair_shows (podcastId, completedStep, attempts, state, lastAttemptAt) " +
                "VALUES (?, 0, 0, '${UpgradeRepairState.PENDING}', 0)",
        ).use { insert ->
            for ((podcastId, _) in follows) {
                insert.clearBindings()
                insert.bindString(1, podcastId)
                insert.executeInsert()
            }
        }
    }
}

/** One `local_episodes` row's alias. The schema makes the first three columns NOT NULL; a null is skipped, never a crash. */
private fun catalogSeed(cursor: Cursor, listenerShows: Map<String, Set<String>>,): AliasSeed? {
    if ((0..2).any(cursor::isNull)) return null
    val episodeId = cursor.getString(0)
    val podcastId = cursor.getString(1)
    return EpisodeAliasSeeding.forCatalogRow(
        episodeId = episodeId,
        podcastId = podcastId,
        storedKey = cursor.getString(2),
        audioUrl = cursor.nullableString(3),
        heldUnderAnotherShow = listenerShows[episodeId].orEmpty().any { it != podcastId },
    )
}

private fun Cursor.nullableString(index: Int): String? = if (isNull(index)) null else getString(index)
