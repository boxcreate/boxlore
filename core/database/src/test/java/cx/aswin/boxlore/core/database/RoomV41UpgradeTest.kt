package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a v40 database to v41 through [BoxLoreDatabase.builder]: every daily summary and every day
 * the cloud holds keeps its values, and their new totals read 0 (not known) until sessions fold theirs in.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomV41UpgradeTest {
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
    fun summariesKeepTheirValuesAndTheirTotalsReadUnknown() = runBlocking<Unit> {
        RoomSchemaFixture.createFromSchema(context, NAME, 40) { db ->
            RoomSchemaFixture.insert(
                db,
                "listening_rollups",
                mapOf(
                    "localDay" to 20L, "episodeId" to "ep-1", "podcastId" to "show", "consumedMs" to 600_000L, "sessionCount" to 2,
                    "completionCount" to 1, "lastListenedAt" to 99L, "morningMs" to 400_000L, "afternoonMs" to 0L, "eveningMs" to 200_000L,
                    "nightMs" to 0L, "deviceId" to "phone-a", "wallMs" to 500_000L,
                ),
            )
            RoomSchemaFixture.insert(
                db,
                "listening_days_pushed",
                mapOf(
                    "deviceId" to "phone-a", "localDay" to 20L, "episodeId" to "ep-1", "podcastId" to "show", "consumedMs" to 600_000L,
                    "wallMs" to 500_000L, "sessionCount" to 2, "completionCount" to 1, "morningMs" to 400_000L, "afternoonMs" to 0L,
                    "eveningMs" to 200_000L, "nightMs" to 0L, "lastListenedAt" to 99L,
                ),
            )
        }
        val db = BoxLoreDatabase.builder(context, NAME).allowMainThreadQueries().build().also { database = it }

        assertEquals(42, db.openHelper.writableDatabase.version)
        val upgraded = db.listeningRollupDao().getRollup("phone-a", 20L, "ep-1")!!
        assertEquals(600_000L, upgraded.consumedMs)
        assertEquals(400_000L, upgraded.morningMs)
        assertEquals(500_000L, upgraded.wallMs)
        assertEquals(ListeningDayTotals(), upgraded.totals)
        // The day the cloud holds matches the summary, totals and all: nothing goes up again.
        assertEquals(PushedListeningDayEntity.of(upgraded), db.listeningPushDao().getPushedDays("phone-a").single())
    }

    private companion object {
        const val NAME = "room-v41-upgrade.db"
    }
}
