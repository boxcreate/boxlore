package cx.aswin.boxlore.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades databases left by shipped builds (Room v36: 0.0.25, v37: 0.0.26–0.0.27, v38:
 * 0.0.28–0.0.29) to v39 through [BoxLoreDatabase.builder], so every case runs the production
 * migration list and Room's own schema check, then reads the result through the DAOs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomV39UpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: BoxLoreDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(NAME)
    }

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(NAME)
    }

    private fun openWithRoom(): BoxLoreDatabase =
        BoxLoreDatabase.builder(context, NAME).allowMainThreadQueries().build().also { database = it }

    private val BoxLoreDatabase.sql: SupportSQLiteDatabase get() = openHelper.writableDatabase

    /** Every kind of stored ID a v38 phone can hold, with Brief artwork triggers installed as on a phone. */
    private fun fillVersion38(db: SupportSQLiteDatabase) {
        BriefingArtworkCompatibility(context.packageName).onOpen(db)
        val insert = { table: String, row: Map<String, Any?> -> RoomSchemaFixture.insert(db, table, row) }
        insert("podcasts", LegacyRows.podcast(SHOW, subscribed = true))
        insert("podcasts", LegacyRows.podcast(SPREAKER_SHOW, subscribed = true))
        insert("podcasts", LegacyRows.podcast(UNFOLLOWED, subscribed = false))
        insert("podcasts", LegacyRows.podcast(RSS_SHOW, subscribed = true, sourceType = PodcastEntity.SOURCE_RSS))
        insert("local_episode_feeds", LegacyRows.feed(SHOW))
        insert("local_episode_feeds", LegacyRows.feed(SPREAKER_SHOW))
        insert("local_episode_feeds", LegacyRows.feed(UNFOLLOWED, ttlExpiresAt = 99L))
        insert("local_episodes", LegacyRows.localEpisode("5001", SHOW, "guid-a"))
        insert("local_episodes", LegacyRows.localEpisode("-11", SHOW, "guid-b\n"))
        insert("local_episodes", LegacyRows.localEpisode("-12", SHOW, "id:-12"))
        insert("local_episodes", LegacyRows.localEpisode(SERVED_OTHER, SHOW, "guid-c"))
        insert("local_episodes", LegacyRows.localEpisode(SERVED, SPREAKER_SHOW, SPREAKER_KEY))
        // P4a development builds: a reserved ID beside a legacy row whose key only differs by a tab.
        insert("local_episodes", LegacyRows.localEpisode("-13", SHOW, "guid-d"))
        insert("local_episodes", LegacyRows.localEpisode("-14", SHOW, "guid-d\t"))
        insert("local_episodes", LegacyRows.localEpisode("-21", UNFOLLOWED, "guid-e"))
        insert("listening_history", LegacyRows.history("5001", SHOW, lastPlayedAt = 500L))
        insert("listening_history", LegacyRows.history("-14", SHOW, lastPlayedAt = 900L, likedAt = 950L))
        insert("listening_history", LegacyRows.history("-99", SHOW))
        insert("listening_history", LegacyRows.history(HISTORY_RECIPE, SHOW))
        insert("listening_history", LegacyRows.history(BRIEFING, "briefing_us"))
        insert("listening_history", LegacyRows.history("-777", RSS_SHOW))
        insert("queue_items", LegacyRows.queueItem("-98", SHOW, position = 0))
        insert("queue_items", LegacyRows.queueItem("5001", SHOW, position = 1))
        insert("queue_items", LegacyRows.queueItem(QUEUE_RECIPE, SHOW, position = 2))
        insert("queue_items", LegacyRows.queueItem(BRIEFING, "briefing_us", position = 3))
        insert("downloaded_episodes", LegacyRows.download("-97", SHOW))
        insert("downloaded_episodes", LegacyRows.download("-13", SHOW))
        insert("downloaded_episodes", LegacyRows.download(DOWNLOAD_RECIPE, SHOW))
        insert("downloaded_episodes", LegacyRows.download(SERVED, SPREAKER_SHOW))
        insert("rss_episodes", LegacyRows.rssEpisode("-555", RSS_SHOW))
        insert("queue_metadata", LegacyRows.queueMetadata("-1,-2"))
    }

    @Test
    fun version38UpgradeSeedsAliasesAndChangesNoStoredValue() = runBlocking<Unit> {
        lateinit var before: Map<String, RoomSchemaFixture.TableRows>
        lateinit var schemaBefore: Map<String, String?>
        RoomSchemaFixture.createFromSchema(context, NAME, 38) { db ->
            fillVersion38(db)
            before = RoomSchemaFixture.snapshot(db)
            schemaBefore = RoomSchemaFixture.schemaSql(db)
        }

        // The Brief artwork triggers are on the file while the migration runs, as on a phone.
        assertTrue(schemaBefore.keys.any { it.startsWith("brief_artwork_listening_history") })
        assertTrue(schemaBefore.keys.any { it.startsWith("brief_artwork_downloaded_episodes") })

        val db = openWithRoom()
        val dao = db.episodeIdentityDao()
        assertEquals("5001", db.localEpisodeCatalogDao().getEpisode("5001")?.episodeId)

        // No existing row's ID or column value changed, in any table.
        for ((table, rows) in before) {
            assertEquals(table, rows, RoomSchemaFixture.rows(db.sql, table, rows.columns))
        }
        // Tables the upgrade only reads keep their exact definitions, indices and triggers too.
        val schemaAfter = RoomSchemaFixture.schemaSql(db.sql)
        for ((name, sql) in schemaBefore - ALTERED_TABLES) assertEquals(name, sql, schemaAfter[name])

        // Every existing catalog row is legacy.
        assertEquals(0L, RoomSchemaFixture.long(db.sql, "SELECT COUNT(*) FROM local_episodes WHERE legacy = 0"))
        assertTrue(db.localEpisodeCatalogDao().getEpisode("-11")!!.legacy)

        suspend fun alias(id: String) = dao.getAlias(id)
        val recipeA = EpisodeRecipeId.forNamespace(100, "guid-a")
        assertEquals(
            EpisodeAliasEntity("5001", SHOW, "guid-a", recipeA, EpisodeAliasState.TRUSTED, true, EpisodeAliasProvenance.UPGRADE_CATALOG, NOW_CHECK),
            alias("5001")?.copy(createdAt = NOW_CHECK),
        )
        assertEquals("guid-b", alias("-11")?.catalogKey)
        assertEquals(EpisodeRecipeId.forNamespace(100, "guid-b"), alias("-11")?.recipeId)
        assertEquals(EpisodeAliasState.UNRESOLVED, alias("-12")?.state)
        assertEquals(EpisodeAliasProvenance.UPGRADE_SYNTHETIC_KEY, alias("-12")?.provenance)
        assertEquals(EpisodeAliasState.AMBIGUOUS, alias(SERVED_OTHER)?.state)
        assertFalse(alias(SERVED_OTHER)!!.owner)
        assertEquals(SPREAKER_RECIPE, alias(SERVED)?.recipeId)
        assertEquals(EpisodeAliasState.TRUSTED, alias(SERVED)?.state)
        assertEquals(listOf(SERVED), dao.aliasesForRecipeId(SPREAKER_SHOW, SPREAKER_RECIPE).map { it.aliasId })

        // Two stored IDs of one episode: the one with the latest listening owns it.
        assertEquals(listOf("-14", "-13"), dao.aliasesForKey(SHOW, "guid-d").map { it.aliasId })
        assertTrue(alias("-14")!!.owner)
        assertFalse(alias("-13")!!.owner)

        // IDs only in history, the queue or downloads wait for their feed; Brief and rss: IDs get none.
        for (id in listOf("-99", HISTORY_RECIPE, "-98", QUEUE_RECIPE, "-97", DOWNLOAD_RECIPE)) {
            assertEquals(id, EpisodeAliasState.UNRESOLVED, alias(id)?.state)
            assertEquals(id, EpisodeAliasProvenance.UPGRADE_LISTENER_ROW, alias(id)?.provenance)
            assertFalse(id, alias(id)!!.owner)
        }
        assertNull(alias(BRIEFING))
        assertNull(alias("-777"))
        assertNull(alias("-555"))
        assertEquals(
            listOf("-12", "-97", "-98", "-99", HISTORY_RECIPE, QUEUE_RECIPE, DOWNLOAD_RECIPE),
            dao.unresolvedAliases(SHOW).map { it.aliasId },
        )
        assertEquals(
            listOf(
                "5001", "-11", "-12", SERVED_OTHER, SERVED, "-13", "-14", "-21",
                "-99", HISTORY_RECIPE, "-98", QUEUE_RECIPE, "-97", DOWNLOAD_RECIPE,
            ).sorted(),
            db.sql.query("SELECT aliasId FROM episode_aliases").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }.sorted(),
        )

        // Namespaces, follow state and the repair list.
        assertEquals(100L, db.podcastDao().getPodcast(SHOW)?.epNs)
        assertEquals(3391039L, db.podcastDao().getPodcast(SPREAKER_SHOW)?.epNs)
        assertNull(db.podcastDao().getPodcast(RSS_SHOW)?.epNs)
        assertEquals(0L, db.podcastDao().getPodcast(SHOW)?.feedUrlCheckedAt)
        assertEquals(100L, db.localEpisodeCatalogDao().getFeed(SHOW)?.epNs)
        assertTrue(db.localEpisodeCatalogDao().getFeed(SHOW)!!.followed)
        assertFalse(db.localEpisodeCatalogDao().getFeed(UNFOLLOWED)!!.followed)
        assertFalse(db.localEpisodeCatalogDao().getFeed(SHOW)!!.archiveComplete)
        assertEquals(
            listOf(SHOW, SPREAKER_SHOW),
            db.upgradeRepairDao().pendingShows().map { it.podcastId },
        )
        assertEquals(UpgradeRepairShowEntity(SHOW), db.upgradeRepairDao().getShow(SHOW))
        assertNull(dao.getGroupState(SHOW, "guid-a"))

        // The queue keeps its legacy removal list; the rich one starts empty.
        val metadata = db.queueDao().getQueueMetadata()
        assertEquals("-1,-2", metadata?.recentRemovedEpisodeIds)
        assertNull(metadata?.recentRemovedEpisodes)
    }

    @Test
    fun evictingACatalogKeepsItsAliases() = runBlocking<Unit> {
        RoomSchemaFixture.createFromSchema(context, NAME, 38) { fillVersion38(it) }
        val db = openWithRoom()
        val aliases = db.sql.query("SELECT COUNT(*) FROM episode_aliases").use {
            it.moveToFirst()
            it.getLong(0)
        }

        db.localEpisodeCatalogDao().deleteCatalog(SHOW)

        assertNull(db.localEpisodeCatalogDao().getEpisode("5001"))
        assertEquals(aliases, RoomSchemaFixture.long(db.sql, "SELECT COUNT(*) FROM episode_aliases"))
        assertEquals("guid-a", db.episodeIdentityDao().getAlias("5001")?.catalogKey)
        assertEquals(EpisodeAliasProvenance.UPGRADE_CATALOG, db.episodeIdentityDao().getAlias("5001")?.provenance)
    }

    @Test
    fun version37UpgradeRunsTheWholeChain() = runBlocking<Unit> {
        RoomSchemaFixture.createFromSchema(context, NAME, 37) { db ->
            RoomSchemaFixture.insert(db, "podcasts", LegacyRows.podcast(SHOW, subscribed = true) + ("autoDownloadEnabled" to 1))
            RoomSchemaFixture.insert(db, "local_episodes", LegacyRows.localEpisode("-123", SHOW, "old-guid "))
            RoomSchemaFixture.insert(db, "listening_history", LegacyRows.history("-123", SHOW))
            RoomSchemaFixture.insert(db, "listening_history", LegacyRows.history(HISTORY_RECIPE, SHOW))
            RoomSchemaFixture.insert(db, "downloaded_episodes", LegacyRows.download("-124", SHOW) + ("isSmartDownloaded" to 1))
            RoomSchemaFixture.insert(db, "queue_metadata", LegacyRows.queueMetadata("7"))
        }

        val db = openWithRoom()

        // 37→38 still did its part.
        assertEquals(AutoDownloadReleaseEntity.HANDLED, db.autoDownloadDao().getRelease("-123")?.state)
        assertEquals(DownloadedEpisodeEntity.ORIGIN_SMART, db.downloadedEpisodeDao().getDownload("-124")?.downloadOrigin)
        // 38→39 on top of it.
        assertEquals("old-guid ", db.localEpisodeCatalogDao().getEpisode("-123")?.guid)
        assertEquals("old-guid", db.episodeIdentityDao().getAlias("-123")?.catalogKey)
        assertTrue(db.episodeIdentityDao().getAlias("-123")!!.owner)
        assertEquals(EpisodeAliasState.UNRESOLVED, db.episodeIdentityDao().getAlias(HISTORY_RECIPE)?.state)
        assertEquals(EpisodeAliasState.UNRESOLVED, db.episodeIdentityDao().getAlias("-124")?.state)
        assertEquals("7", db.queueDao().getQueueMetadata()?.recentRemovedEpisodeIds)
        assertEquals(1, db.upgradeRepairDao().pendingCount())
    }

    @Test
    fun version36UpgradeRunsTheWholeChainIncludingTheQueueDefaults() = runBlocking<Unit> {
        RoomSchemaFixture.createFromSchema(context, NAME, 36) { db ->
            RoomSchemaFixture.insert(db, "podcasts", LegacyRows.podcast(SHOW, subscribed = true))
            RoomSchemaFixture.insert(db, "local_episodes", LegacyRows.localEpisode("7001", SHOW, "guid-36"))
            RoomSchemaFixture.insert(db, "listening_history", LegacyRows.history("7001", SHOW))
            RoomSchemaFixture.insert(db, "queue_items", LegacyRows.queueItem(HISTORY_RECIPE, SHOW, position = 0))
        }

        val db = openWithRoom()

        assertEquals(EpisodeRecipeId.forNamespace(100, "guid-36"), db.episodeIdentityDao().getAlias("7001")?.recipeId)
        assertEquals(EpisodeAliasState.UNRESOLVED, db.episodeIdentityDao().getAlias(HISTORY_RECIPE)?.state)
        assertEquals(HISTORY_RECIPE, db.queueDao().getAllQueueItemsSync().single().episodeId)
        // queue_metadata was created by 36→37 with SQL defaults that the exported schema lacks.
        assertEquals("0", RoomSchemaFixture.string(db.sql, "SELECT dflt_value FROM pragma_table_info('queue_metadata') WHERE name = 'isDirty'"))
        assertNull(db.queueDao().getQueueMetadata()?.recentRemovedEpisodes)
        assertTrue(db.localEpisodeCatalogDao().getEpisode("7001")!!.legacy)
    }

    /**
     * A phone that came up through the older migrations: `rss_episodes` and the supplement tables
     * carry `CHECK(id < 0)`, and `local_episodes` holds rows copied from Missing-episodes extras by
     * migration 31→32 with SQLite-trimmed, address and synthetic keys.
     */
    @Test
    fun migratedPhoneShapeKeepsChecksAndSupplementTablesAndSeedsCopiedRows() = runBlocking<Unit> {
        lateinit var before: Map<String, RoomSchemaFixture.TableRows>
        lateinit var schemaBefore: Map<String, String?>
        RoomSchemaFixture.createFromSchema(context, NAME, 30) { db ->
            db.execSQL("DROP TABLE rss_episodes")
            db.execSQL(MIGRATION_26_27_RSS_EPISODES)
            db.execSQL("CREATE INDEX IF NOT EXISTS index_rss_episodes_podcastId ON rss_episodes(podcastId)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_rss_episodes_podcastId_publishedDate ON rss_episodes(podcastId, publishedDate)",
            )
            RoomSchemaFixture.insert(db, "podcasts", LegacyRows.podcast(SHOW, subscribed = true))
            RoomSchemaFixture.insert(db, "podcasts", LegacyRows.podcast(RSS_SHOW, subscribed = true, sourceType = PodcastEntity.SOURCE_RSS))
            RoomSchemaFixture.insert(db, "rss_episodes", LegacyRows.rssEpisode("-555", RSS_SHOW))
            RoomSchemaFixture.runMigrations(db, from = 30, to = 31)
            RoomSchemaFixture.insert(db, "episode_supplements", LegacyRows.supplement(SHOW))
            val item = { id: String, guid: String?, audio: String ->
                RoomSchemaFixture.insert(db, "episode_supplement_items", LegacyRows.supplementItem(id, SHOW, guid, audio))
            }
            item("-31", "unique-guid\n", "https://cdn.example/31.mp3")
            item("-32", "twice", "https://cdn.example/32.mp3")
            item("-33", "twice", "https://cdn.example/33.mp3")
            item("-34", "thrice", "https://cdn.example/same.mp3")
            item("-35", "thrice", "https://cdn.example/same.mp3")
            item("-36", "thrice", "https://cdn.example/36.mp3")
            RoomSchemaFixture.runMigrations(db, from = 31, to = 38)
            RoomSchemaFixture.insert(db, "listening_history", LegacyRows.history("-32", SHOW))
            before = RoomSchemaFixture.snapshot(db)
            schemaBefore = RoomSchemaFixture.schemaSql(db)
        }

        val db = openWithRoom()
        val dao = db.episodeIdentityDao()

        assertEquals("unique-guid\n", db.localEpisodeCatalogDao().getEpisode("-31")?.guid)
        for ((table, rows) in before) {
            assertEquals(table, rows, RoomSchemaFixture.rows(db.sql, table, rows.columns))
        }
        val schemaAfter = RoomSchemaFixture.schemaSql(db.sql)
        for (name in listOf("rss_episodes", "episode_supplements", "episode_supplement_items")) {
            assertEquals(name, schemaBefore[name], schemaAfter[name])
        }
        assertTrue(schemaAfter["rss_episodes"]!!.contains("CHECK(CAST(episodeId AS INTEGER) < 0)"))

        assertEquals("unique-guid", dao.getAlias("-31")?.catalogKey)
        assertEquals(EpisodeAliasState.TRUSTED, dao.getAlias("-31")?.state)
        // Repeated GUID, so 31→32 keyed by address: the real key is unknown until the feed is read.
        for (id in listOf("-32", "-33", "-36")) {
            assertEquals(id, EpisodeAliasState.UNRESOLVED, dao.getAlias(id)?.state)
            assertEquals(id, EpisodeAliasProvenance.UPGRADE_AUDIO_URL_KEY, dao.getAlias(id)?.provenance)
        }
        for (id in listOf("-34", "-35")) {
            assertEquals(id, EpisodeAliasProvenance.UPGRADE_SYNTHETIC_KEY, dao.getAlias(id)?.provenance)
        }
        assertNull(dao.getAlias("-555"))

        // Positive IDs still can't enter the RSS or supplement tables.
        assertThrows(SQLiteConstraintException::class.java) {
            RoomSchemaFixture.insert(db.sql, "rss_episodes", LegacyRows.rssEpisode("42", RSS_SHOW))
        }
        assertThrows(SQLiteConstraintException::class.java) {
            RoomSchemaFixture.insert(
                db.sql,
                "episode_supplement_items",
                LegacyRows.supplementItem("43", SHOW, "g", "https://cdn.example/43.mp3"),
            )
        }
    }

    /** A fresh v39 install and an upgraded v38 phone end up with the same tables, columns, defaults and indices. */
    @Test
    fun freshInstallAndUpgradedPhoneHaveTheSameSchema() {
        RoomSchemaFixture.createFromSchema(context, NAME, 38) { }
        val upgraded = describeSchema(openWithRoom().sql)
        context.deleteDatabase(FRESH)
        val fresh = BoxLoreDatabase.builder(context, FRESH).allowMainThreadQueries().build()
        try {
            assertEquals(describeSchema(fresh.sql), upgraded)
        } finally {
            fresh.close()
            context.deleteDatabase(FRESH)
        }
    }

    private fun describeSchema(db: SupportSQLiteDatabase): Map<String, List<String>> =
        RoomSchemaFixture.tables(db).associateWith { table ->
            val columns = db.query("PRAGMA table_info($table)").use { c ->
                buildList {
                    while (c.moveToNext()) {
                        add("${c.getString(1)} ${c.getString(2)} notnull=${c.getInt(3)} default=${c.getString(4)} pk=${c.getInt(5)}")
                    }
                }
            }.sorted()
            val indices = db.query("PRAGMA index_list($table)").use { c ->
                buildList { while (c.moveToNext()) add("${c.getString(1)} unique=${c.getInt(2)}") }
            }.map { index ->
                index + db.query("PRAGMA index_info(${index.substringBefore(' ')})").use { c ->
                    buildList { while (c.moveToNext()) add(c.getString(2)) }
                }
            }.sorted()
            columns + indices
        }

    @Test
    fun aMissingUpgradePathFailsInsteadOfErasingData() {
        RoomSchemaFixture.createFromSchema(context, NAME, 38) { db ->
            RoomSchemaFixture.insert(db, "listening_history", LegacyRows.history("5001", SHOW))
        }
        val withoutLatest = BoxLoreDatabase.ALL_MIGRATIONS.filterNot { it.startVersion == 38 }.toTypedArray()
        val incomplete = BoxLoreDatabase.builder(context, NAME, withoutLatest).allowMainThreadQueries().build()

        val failure = assertThrows(IllegalStateException::class.java) { incomplete.openHelper.writableDatabase }
        incomplete.close()

        assertTrue(failure.message.orEmpty(), failure.message.orEmpty().contains("38"))
        RoomSchemaFixture.open(context, NAME, 38).use { helper ->
            assertEquals(38, helper.readableDatabase.version)
            assertEquals("5001", RoomSchemaFixture.string(helper.readableDatabase, "SELECT episodeId FROM listening_history"))
        }
    }

    @Test
    fun aNewerFileOpenedByThisBuildIsRecreatedEmpty() = runBlocking<Unit> {
        RoomSchemaFixture.open(context, NAME, 40) { db -> db.execSQL("CREATE TABLE future_table (id INTEGER PRIMARY KEY)") }
            .use { it.writableDatabase.execSQL("INSERT INTO future_table VALUES (1)") }

        val db = openWithRoom()

        assertEquals(0, db.upgradeRepairDao().pendingCount())
        assertEquals(39, db.sql.version)
        assertFalse(RoomSchemaFixture.tables(db.sql).contains("future_table"))
    }

    @Test
    fun versionsOlderThanEveryMigrationAreStillRecreatedEmpty() = runBlocking<Unit> {
        RoomSchemaFixture.open(context, NAME, 11) { db -> db.execSQL("CREATE TABLE podcasts (podcastId TEXT PRIMARY KEY)") }
            .use { it.writableDatabase.execSQL("INSERT INTO podcasts VALUES ('100')") }

        val db = openWithRoom()

        assertNull(db.podcastDao().getPodcast(SHOW))
        assertEquals(39, db.sql.version)
    }

    private companion object {
        const val NAME = "room-v39-upgrade-test"
        const val FRESH = "room-v39-fresh-test"
        const val SHOW = "100"
        const val UNFOLLOWED = "200"
        const val RSS_SHOW = "rss:abc"
        const val SPREAKER_SHOW = "3391039"
        const val SPREAKER_KEY = "https://api.spreaker.com/episode/75827097"
        const val SPREAKER_RECIPE = 8825297254050678L
        const val SERVED = "8825297254050678"
        const val SERVED_OTHER = "4503599627370497"
        const val HISTORY_RECIPE = "4503599627370999"
        const val QUEUE_RECIPE = "4503599627371000"
        const val DOWNLOAD_RECIPE = "4503599627371001"
        const val BRIEFING = "briefing_us_2026-10-25"
        const val NOW_CHECK = 1L

        /** The tables 38→39 adds columns to; their `CREATE TABLE` text in `sqlite_master` gains those columns. */
        val ALTERED_TABLES = setOf("podcasts", "local_episodes", "local_episode_feeds", "queue_metadata")

        /** `rss_episodes` as migration 26→27 created it on phones. */
        const val MIGRATION_26_27_RSS_EPISODES = """
            CREATE TABLE IF NOT EXISTS rss_episodes (
                episodeId TEXT NOT NULL PRIMARY KEY CHECK(CAST(episodeId AS INTEGER) < 0),
                podcastId TEXT NOT NULL,
                guid TEXT,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                audioUrl TEXT NOT NULL,
                imageUrl TEXT,
                duration INTEGER NOT NULL,
                publishedDate INTEGER NOT NULL,
                chaptersUrl TEXT,
                transcriptUrl TEXT,
                transcripts TEXT,
                persons TEXT,
                seasonNumber INTEGER,
                episodeNumber INTEGER,
                episodeType TEXT,
                enclosureType TEXT,
                FOREIGN KEY(podcastId) REFERENCES podcasts(podcastId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
        """
    }
}
