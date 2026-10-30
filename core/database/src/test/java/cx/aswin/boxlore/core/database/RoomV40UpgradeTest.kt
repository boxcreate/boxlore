package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a database left by every exported schema (v30 to v39, every build since listening
 * sessions existed) to v40 through [BoxLoreDatabase.builder], so each case runs the whole production
 * migration chain and Room's own schema check, then checks the listening data:
 * every stored value is unchanged, the new dates read "before recording", only unfinished rows lose
 * the finish default, daily summaries become this phone's, the played shows are filled from history,
 * later writes keep the old dates, and the upgraded file has exactly a fresh install's schema.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [34])
class RoomV40UpgradeTest(private val fromVersion: Int) {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: BoxLoreDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(NAME)
        context.deleteDatabase(FRESH)
    }

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(NAME)
        context.deleteDatabase(FRESH)
    }

    private fun openWithRoom(): BoxLoreDatabase =
        BoxLoreDatabase.builder(context, NAME).allowMainThreadQueries().build().also { database = it }

    private val BoxLoreDatabase.sql: SupportSQLiteDatabase get() = openHelper.writableDatabase

    private fun fill(db: SupportSQLiteDatabase) {
        // Phones that ran a build with Brief artwork compatibility carry its triggers while migrating.
        if (fromVersion >= TRIGGERS_SINCE) BriefingArtworkCompatibility(context.packageName).onOpen(db)
        val insert = { table: String, row: Map<String, Any?> -> RoomSchemaFixture.insert(db, table, row) }
        insert("podcasts", LegacyRows.podcast(SHOW, subscribed = true) + ("genre" to "True Crime"))
        insert("podcasts", LegacyRows.podcast(PLACEHOLDER_SHOW, subscribed = true) + ("genre" to "Podcast"))
        insert("podcasts", LegacyRows.podcast(MARKED_SHOW, subscribed = true))
        // SHOW: one finished by listening, one in progress played later (its name and art win).
        insert("listening_history", history("10", SHOW, playedAt = 1_000L, progress = 0L, completed = true, name = "Old name", art = "https://img/old.jpg"))
        insert("listening_history", history("11", SHOW, playedAt = 2_000L, progress = 5_000L, name = "New name", art = "https://img/new.jpg"))
        insert("listening_history", history("20", PLACEHOLDER_SHOW, playedAt = 1_500L, progress = 900L))
        // An unfollowed show: no podcasts row, no art but the episode's.
        insert("listening_history", history("30", UNFOLLOWED_SHOW, playedAt = 3_000L, progress = 0L, completed = true, art = null))
        // Only marked played (manually and in bulk) or only liked: not played.
        insert("listening_history", history("40", MARKED_SHOW, playedAt = 4_000L, progress = 0L, completed = true, manual = true))
        insert("listening_history", history("41", MARKED_SHOW, playedAt = 4_100L, progress = 0L, completed = true, bulk = true))
        insert("listening_history", history("50", LIKED_SHOW, playedAt = 5_000L, progress = 0L, likedAt = 5_000L))
        insert("listening_sessions", session("s-1", "11", SHOW, endedAt = 2_000L, day = 7L))
        insert("listening_sessions", session("s-2", "30", UNFOLLOWED_SHOW, endedAt = 3_000L, day = 8L))
        insert("listening_rollups", rollup(day = 1L, episodeId = "10", podcastId = SHOW, consumed = 60_000L))
        insert("listening_rollups", rollup(day = 2L, episodeId = "10", podcastId = SHOW, consumed = 30_000L))
    }

    @Test
    fun theUpgradeKeepsEveryListeningValueAndAddsTheNewOnes() = runBlocking<Unit> {
        lateinit var before: Map<String, RoomSchemaFixture.TableRows>
        RoomSchemaFixture.createFromSchema(context, NAME, fromVersion) { db ->
            fill(db)
            before = RoomSchemaFixture.snapshot(db)
        }

        val db = openWithRoom()

        assertEquals(40, db.sql.version)
        for (table in listOf("listening_history", "listening_sessions")) {
            val old = before.getValue(table)
            assertEquals("$table from v$fromVersion", old, RoomSchemaFixture.rows(db.sql, table, old.columns))
        }
        val oldRollups = before.getValue("listening_rollups")
        assertEquals(
            oldRollups.rows.map { it.toString() }.sorted(),
            RoomSchemaFixture.rows(db.sql, "listening_rollups", oldRollups.columns).rows.map { it.toString() }.sorted(),
        )

        val dates = RoomSchemaFixture.rows(db.sql, "listening_history", listOf("episodeId", "isCompleted", "firstPlayedAt", "finishedAt"))
        for (row in dates.rows) {
            val id = row[0]
            assertEquals("firstPlayedAt of $id", 0L, row[2])
            // Finished by listening before recording: 0. Only marked played (40, 41): no finish by listening yet.
            assertEquals("finishedAt of $id", if (row[1] == 1L && id !in setOf("40", "41")) 0L else null, row[3])
        }
        val sessionDetails = RoomSchemaFixture.rows(db.sql, "listening_sessions", listOf("deviceId", "wallMs", "source", "output", "episodePublishedAt"))
        assertTrue(sessionDetails.rows.isNotEmpty())
        assertTrue(sessionDetails.rows.all { row -> row.all { it == null } })
        assertEquals(
            listOf(listOf<Any?>("", 0L), listOf<Any?>("", 0L)),
            RoomSchemaFixture.rows(db.sql, "listening_rollups", listOf("deviceId", "wallMs")).rows,
        )

        assertEquals(
            listOf(
                PlayedShowEntity(UNFOLLOWED_SHOW, "Show", "https://img/e.jpg", genre = null, firstPlayedAt = 0L, lastPlayedAt = 3_000L, isDirty = true),
                PlayedShowEntity(SHOW, "New name", "https://img/new.jpg", genre = "True Crime", firstPlayedAt = 0L, lastPlayedAt = 2_000L, isDirty = true),
                PlayedShowEntity(PLACEHOLDER_SHOW, "Show", "https://img/p.jpg", genre = null, firstPlayedAt = 0L, lastPlayedAt = 1_500L, isDirty = true),
            ),
            db.playedShowDao().getAllPlayedShows(),
        )

        // The DAOs read the upgraded rows, including the summaries under their new key.
        assertEquals(7, db.listeningHistoryDao().getAllStoredHistory().size)
        assertEquals(60_000L, db.listeningRollupDao().getRollup(ListeningRollupEntity.THIS_DEVICE, 1L, "10")!!.consumedMs)
        assertEquals(2, db.listeningSessionDao().getAllSessions().size)
    }

    @Test
    fun laterWritesKeepTheDatesOfRowsFromBeforeTheUpgrade() = runBlocking<Unit> {
        RoomSchemaFixture.createFromSchema(context, NAME, fromVersion) { db -> fill(db) }
        val db = openWithRoom()
        val dao = db.listeningHistoryDao()

        // Replaying an old in-progress episode: it was first played before recording began.
        dao.updateProgress("11", progressMs = 9_000L, durationMs = 60_000L, lastPlayedAt = 10_000L)
        assertEquals(0L, dao.getHistoryItem("11")!!.firstPlayedAt)
        // A whole-row write from playback (dated) or from an older source (no dates) keeps them.
        dao.upsert(dao.getHistoryItem("20")!!.copy(progressMs = 1_000L, firstPlayedAt = 11_000L, finishedAt = null))
        assertEquals(0L, dao.getHistoryItem("20")!!.firstPlayedAt)
        assertNull(dao.getHistoryItem("20")!!.finishedAt)

        // Finishing an old unfinished episode by listening dates the finish; an old finish stays 0.
        dao.completeFromPlayback("11", durationMs = 60_000L, lastPlayedAt = 12_000L, isManualCompletion = false)
        assertEquals(12_000L, dao.getHistoryItem("11")!!.finishedAt)
        dao.completeFromPlayback("10", durationMs = 60_000L, lastPlayedAt = 13_000L, isManualCompletion = false)
        assertEquals(0L, dao.getHistoryItem("10")!!.finishedAt)
        // Marking played isn't finishing.
        dao.completeFromPlayback("50", durationMs = 60_000L, lastPlayedAt = 14_000L, isManualCompletion = true)
        assertNull(dao.getHistoryItem("50")!!.finishedAt)
        // An old episode only marked played, then finished by listening, dates that finish.
        dao.completeFromPlayback("40", durationMs = 60_000L, lastPlayedAt = 14_500L, isManualCompletion = false)
        assertEquals(14_500L, dao.getHistoryItem("40")!!.finishedAt)

        // A new episode starts without dates and gets them from its first play and finish.
        dao.upsert(dao.getHistoryItem("50")!!.copy(episodeId = "60", progressMs = 0L, isCompleted = false, isManualCompletion = false, firstPlayedAt = null, finishedAt = null))
        assertNull(dao.getHistoryItem("60")!!.firstPlayedAt)
        dao.updateProgress("60", progressMs = 1_000L, durationMs = 60_000L, lastPlayedAt = 15_000L)
        dao.updateProgress("60", progressMs = 2_000L, durationMs = 60_000L, lastPlayedAt = 16_000L)
        assertEquals(15_000L, dao.getHistoryItem("60")!!.firstPlayedAt)

        // A play of a known show keeps its first play from before recording.
        db.playedShowDao().recordPlay(SHOW, "Newest name", null, "Podcast", playedAt = 17_000L)
        val show = db.playedShowDao().getPlayedShow(SHOW)!!
        assertEquals(0L, show.firstPlayedAt)
        assertEquals(17_000L, show.lastPlayedAt)
        assertEquals("True Crime", show.genre)
        assertEquals("https://img/new.jpg", show.imageUrl)
    }

    /**
     * An upgraded phone and a fresh v40 install have the same tables, columns, keys and indices, and
     * every column v40 creates has the same default too. (Migrations up to 36→37 added a few older
     * columns with a SQL default 0 that a fresh install doesn't declare; Room accepts that, and v40
     * leaves them alone.)
     */
    @Test
    fun theUpgradedFileHasAFreshInstallsSchema() {
        RoomSchemaFixture.createFromSchema(context, NAME, fromVersion) { db -> fill(db) }
        val upgraded = describeSchema(openWithRoom().sql)
        val fresh = BoxLoreDatabase.builder(context, FRESH).allowMainThreadQueries().build()
        try {
            assertEquals(describeSchema(fresh.sql), upgraded)
        } finally {
            fresh.close()
        }
    }

    private fun describeSchema(db: SupportSQLiteDatabase): Map<String, List<String>> =
        RoomSchemaFixture.tables(db).associateWith { table ->
            val columns = db.query("PRAGMA table_info($table)").use { c ->
                buildList {
                    while (c.moveToNext()) {
                        val v40Column = table in V40_TABLES || "$table.${c.getString(1)}" in V40_COLUMNS
                        val default = if (v40Column) " default=${c.getString(4)}" else ""
                        add("${c.getString(1)} ${c.getString(2)} notnull=${c.getInt(3)}$default pk=${c.getInt(5)}")
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

    @Suppress("LongParameterList")
    private fun history(
        id: String,
        podcastId: String,
        playedAt: Long,
        progress: Long,
        completed: Boolean = false,
        manual: Boolean = false,
        bulk: Boolean = false,
        likedAt: Long = 0L,
        name: String = "Show",
        art: String? = "https://img/p.jpg",
    ): Map<String, Any?> =
        LegacyRows.history(id, podcastId, lastPlayedAt = playedAt, likedAt = likedAt) +
            mapOf(
                "progressMs" to progress,
                "isCompleted" to completed.int(),
                "isManualCompletion" to manual.int(),
                "isBulkCompletion" to bulk.int(),
                "isLiked" to (likedAt > 0L).int(),
                "podcastName" to name,
                "podcastImageUrl" to art,
            )

    private fun session(id: String, episodeId: String, podcastId: String, endedAt: Long, day: Long,): Map<String, Any?> =
        mapOf(
            "sessionId" to id, "episodeId" to episodeId, "podcastId" to podcastId, "startedAt" to endedAt - 60_000L,
            "endedAt" to endedAt, "consumedMs" to 60_000L, "completed" to 0, "localDay" to day, "timeBucket" to 2,
        )

    private fun rollup(day: Long, episodeId: String, podcastId: String, consumed: Long,): Map<String, Any?> =
        mapOf(
            "localDay" to day, "episodeId" to episodeId, "podcastId" to podcastId, "consumedMs" to consumed,
            "sessionCount" to 2, "completionCount" to 1, "lastListenedAt" to day * 1_000L, "morningMs" to consumed,
            "afternoonMs" to 0L, "eveningMs" to 0L, "nightMs" to 0L,
        )

    private fun Boolean.int(): Int = if (this) 1 else 0

    companion object {
        private const val NAME = "room-v40-upgrade.db"
        private const val FRESH = "room-v40-fresh.db"
        private const val SHOW = "1001"
        private const val PLACEHOLDER_SHOW = "1002"
        private const val UNFOLLOWED_SHOW = "1003"
        private const val MARKED_SHOW = "1004"
        private const val LIKED_SHOW = "1005"

        /** Tables migration 39→40 creates or rebuilds, and the columns it adds to others. */
        private val V40_TABLES = setOf("listening_rollups", "played_shows", "listening_days_pushed")
        private val V40_COLUMNS =
            setOf(
                "listening_history.firstPlayedAt",
                "listening_history.finishedAt",
                "listening_sessions.deviceId",
                "listening_sessions.wallMs",
                "listening_sessions.source",
                "listening_sessions.output",
                "listening_sessions.episodePublishedAt",
            )

        /** Brief artwork compatibility triggers are on phones from v36 on. */
        private const val TRIGGERS_SINCE = 36

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "from v{0}")
        fun versions(): List<Array<Any>> = (30..39).map { arrayOf<Any>(it) }
    }
}
