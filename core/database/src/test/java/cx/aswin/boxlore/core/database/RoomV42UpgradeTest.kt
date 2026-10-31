package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a v41 database to v42 through [BoxLoreDatabase.builder]: every daily summary and every day
 * the cloud holds keeps its totals, and the time with a known release date reads 0 (not known), so a
 * summary's new releases no longer count against it; a day still held in sessions knows it and goes up.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomV42UpgradeTest {
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

    @Test
    fun summariesKeepTheirTotalsAndTheirReleaseDateTimeReadsUnknown() = runBlocking<Unit> {
        RoomSchemaFixture.createFromSchema(context, NAME, 41) { db ->
            val day = mapOf(
                "localDay" to 20L, "episodeId" to "ep-1", "podcastId" to "show", "consumedMs" to 600_000L, "sessionCount" to 2,
                "completionCount" to 1, "lastListenedAt" to 99L, "morningMs" to 400_000L, "afternoonMs" to 0L, "eveningMs" to 200_000L,
                "nightMs" to 0L, "deviceId" to "phone-a", "wallMs" to 500_000L, "longestSessionMs" to 300_000L, "carMs" to 600_000L,
                "loreMs" to 100_000L, "newReleaseMs" to 250_000L,
            )
            RoomSchemaFixture.insert(db, "listening_rollups", day)
            RoomSchemaFixture.insert(db, "listening_days_pushed", day)
            // A session recorded under v41 with its episode's release date: the upgrade keeps it.
            RoomSchemaFixture.insert(
                db,
                "listening_sessions",
                mapOf(
                    "sessionId" to "s-1", "episodeId" to "ep-2", "podcastId" to "show", "startedAt" to 1_000L, "endedAt" to 61_000L,
                    "consumedMs" to 60_000L, "completed" to 0, "localDay" to 21L, "timeBucket" to 0, "deviceId" to "phone-a",
                    "episodePublishedAt" to 500L,
                ),
            )
        }
        val db = BoxLoreDatabase.builder(context, NAME).allowMainThreadQueries().build().also { database = it }

        assertEquals(42, db.openHelper.writableDatabase.version)
        val upgraded = db.listeningRollupDao().getRollup("phone-a", 20L, "ep-1")!!
        assertEquals(600_000L, upgraded.consumedMs)
        assertEquals(ListeningDayTotals(longestSessionMs = 300_000L, carMs = 600_000L, loreMs = 100_000L, newReleaseMs = 250_000L), upgraded.totals)
        // The summary still matches the day the cloud holds; the day held in sessions now knows its date and goes up.
        assertEquals(PushedListeningDayEntity.of(upgraded), db.listeningPushDao().getPushedDays("phone-a").single())
        val toPush = db.listeningInsightsMaintenance().listeningDaysToPush(listOf("phone-a"), limit = 10)
        assertEquals(listOf(21L to 60_000L), toPush.map { it.localDay to it.totals.releaseKnownMs })
        assertTrue(db.listeningSessionDao().getAllSessions().single().episodePublishedAt == 500L)
    }

    private companion object {
        const val NAME = "room-v42-upgrade.db"
    }
}
