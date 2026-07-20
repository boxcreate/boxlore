package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListeningInsightsDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var sessions: ListeningSessionDao
    private lateinit var rollups: ListeningRollupDao
    private lateinit var maintenance: ListeningInsightsMaintenance

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        sessions = database.listeningSessionDao()
        rollups = database.listeningRollupDao()
        maintenance = database.listeningInsightsMaintenance()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun session(id: String) =
        ListeningSessionEntity(
            sessionId = id,
            episodeId = "ep-1",
            podcastId = "pod-1",
            startedAt = 1_000L,
            endedAt = 2_000L,
            consumedMs = 1_000L,
            completed = false,
            localDay = 10L,
            timeBucket = 0,
        )

    @Test
    fun upsertAndQuerySessions() =
        runTest {
            sessions.upsertSession(session("s1").copy(consumedMs = 500))
            assertEquals(500L, sessions.getAllSessions().single().consumedMs)
        }

    @Test
    fun rollupSkipsTodayAndMergesOlderSessions() =
        runTest {
            val today = 100L
            sessions.upsertSessions(
                listOf(
                    session("old-a").copy(localDay = 10, endedAt = 10_000, consumedMs = 100, timeBucket = 0),
                    session("old-b").copy(
                        localDay = 10,
                        endedAt = 11_000,
                        consumedMs = 200,
                        completed = true,
                        timeBucket = 1,
                    ),
                    session("today").copy(localDay = today, endedAt = 99_000, consumedMs = 999, timeBucket = 2),
                ),
            )

            val rolled =
                maintenance.rollUpEligibleSessions(
                    cutoffEndedAtExclusive = 50_000L,
                    todayLocalDay = today,
                )

            assertEquals(2, rolled)
            assertEquals(1, sessions.getAllSessions().size)
            assertEquals("today", sessions.getAllSessions().single().sessionId)

            val rollup = rollups.getRollup(10L, "ep-1")!!
            assertEquals(300L, rollup.consumedMs)
            assertEquals(2, rollup.sessionCount)
            assertEquals(1, rollup.completionCount)
            assertEquals(100L, rollup.morningMs)
            assertEquals(200L, rollup.afternoonMs)
        }

    @Test
    fun rollupIsIdempotentAndMergesIntoExisting() =
        runTest {
            val today = 200L
            sessions.upsertSession(session("old-1").copy(localDay = 5, endedAt = 5_000, consumedMs = 50))
            maintenance.rollUpEligibleSessions(cutoffEndedAtExclusive = 10_000L, todayLocalDay = today)
            assertEquals(50L, rollups.getRollup(5L, "ep-1")!!.consumedMs)

            sessions.upsertSession(session("old-2").copy(localDay = 5, endedAt = 6_000, consumedMs = 25))
            maintenance.rollUpEligibleSessions(cutoffEndedAtExclusive = 10_000L, todayLocalDay = today)

            val rollup = rollups.getRollup(5L, "ep-1")!!
            assertEquals(75L, rollup.consumedMs)
            assertEquals(2, rollup.sessionCount)
            assertTrue(sessions.getAllSessions().isEmpty())
        }

    @Test
    fun samePodcastCanExistInRollupAndTodayRaw() =
        runTest {
            val today = 50L
            rollups.upsertRollup(
                ListeningRollupEntity(
                    localDay = 1L,
                    episodeId = "ep-old",
                    podcastId = "pod-shared",
                    consumedMs = 1_000L,
                    sessionCount = 2,
                    completionCount = 1,
                    lastListenedAt = 1_000L,
                ),
            )
            sessions.upsertSession(
                session("today-s").copy(
                    episodeId = "ep-new",
                    podcastId = "pod-shared",
                    localDay = today,
                    endedAt = 90_000,
                    consumedMs = 400,
                ),
            )

            maintenance.rollUpEligibleSessions(cutoffEndedAtExclusive = 10_000L, todayLocalDay = today)

            assertEquals(1, rollups.getAllRollups().size)
            assertEquals(1, sessions.getAllSessions().size)
            assertEquals("pod-shared", sessions.getAllSessions().single().podcastId)
            assertEquals("pod-shared", rollups.getAllRollups().single().podcastId)
        }

    @Test
    fun deleteEpisodeAndClearAll() =
        runTest {
            sessions.upsertSession(session("s1").copy(episodeId = "ep-a"))
            sessions.upsertSession(session("s2").copy(episodeId = "ep-b"))
            rollups.upsertRollup(
                ListeningRollupEntity(
                    localDay = 1,
                    episodeId = "ep-a",
                    podcastId = "pod-1",
                    consumedMs = 10,
                    sessionCount = 1,
                    completionCount = 0,
                    lastListenedAt = 1,
                ),
            )

            maintenance.deleteEpisodeAnalytics("ep-a")
            assertNull(rollups.getRollup(1, "ep-a"))
            assertEquals(listOf("ep-b"), sessions.getAllSessions().map { it.episodeId })

            maintenance.clearAllAnalytics()
            assertTrue(sessions.getAllSessions().isEmpty())
            assertTrue(rollups.getAllRollups().isEmpty())
        }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ListeningInsightsMigration29To30Test {
    @Test
    fun migrationSqlCreatesSessionAndRollupTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(null)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(29) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                "CREATE TABLE listening_history (" +
                                    "episodeId TEXT NOT NULL PRIMARY KEY, " +
                                    "podcastId TEXT NOT NULL, " +
                                    "episodeTitle TEXT NOT NULL, " +
                                    "episodeImageUrl TEXT, " +
                                    "podcastImageUrl TEXT, " +
                                    "episodeAudioUrl TEXT, " +
                                    "podcastName TEXT NOT NULL, " +
                                    "progressMs INTEGER NOT NULL, " +
                                    "durationMs INTEGER NOT NULL, " +
                                    "isCompleted INTEGER NOT NULL, " +
                                    "isLiked INTEGER NOT NULL, " +
                                    "lastPlayedAt INTEGER NOT NULL, " +
                                    "isDirty INTEGER NOT NULL, " +
                                    "syncedAt INTEGER NOT NULL, " +
                                    "enclosureType TEXT, " +
                                    "isManualCompletion INTEGER NOT NULL, " +
                                    "isBulkCompletion INTEGER NOT NULL, " +
                                    "episodeDescription TEXT)",
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = openHelper.writableDatabase
        BoxLoreDatabaseMigrations.migrate29To30(db)

        val sessionTables =
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='listening_sessions'")
        assertTrue(sessionTables.moveToFirst())
        sessionTables.close()

        val rollupTables =
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='listening_rollups'")
        assertTrue(rollupTables.moveToFirst())
        rollupTables.close()
        db.close()
    }
}
