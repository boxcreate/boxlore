package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.UpgradeRepairDao
import cx.aswin.boxlore.core.database.UpgradeRepairShowEntity
import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.UpgradeRepairOutcome
import cx.aswin.boxlore.core.model.UpgradeRepairReport
import cx.aswin.boxlore.core.network.model.V2ShowAddress
import cx.aswin.boxlore.core.network.model.V2ShowFlags
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsResponse
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * `own_ids_upgrade_completed` (P4 PR 18): sent once when the repair of an upgraded install
 * finishes, with its counts; never on a fresh install, never twice, never before the sent record
 * is saved, and the tally survives in SharedPreferences.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpgradeRepairReportTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcasts: PodcastDao
    private lateinit var repairs: UpgradeRepairDao
    private val reports = mutableListOf<UpgradeRepairReport>()
    private val answers = mutableMapOf<Long, V2ShowAddress>()
    private var online = true
    private var now = NOW
    private var tally: UpgradeRepairTally = MemoryUpgradeRepairTally()

    private val telemetry =
        object : FeedTelemetryPort {
            override fun feedFetched(report: FeedFetchReport) = Unit

            override fun feedUrlCorrected(correction: FeedUrlCorrection) = Unit

            override fun upgradeRepairCompleted(report: UpgradeRepairReport) {
                reports += report
            }
        }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        podcasts = database.podcastDao()
        repairs = database.upgradeRepairDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aFinishedRepairReportsOnceWithItsCounts() = runTest {
        seed(show("920666", OLD, notifications = true))
        seed(show("920667", OTHER))
        answers[920666] = moved(920666)

        online = false
        val repair = repair()
        assertEquals(UpgradeRepairRun.RETRY, repair.run())
        assertTrue("nothing is sent before the repair finishes", reports.isEmpty())

        online = true
        now = NOW + 2 * HOUR
        assertEquals(UpgradeRepairRun.DONE, repair.run())
        val report = reports.single()
        assertEquals(2, report.showsCount)
        assertEquals(0, report.showsGaveUp)
        assertEquals(1, report.addressesSwapped)
        assertEquals(UpgradeRepairOutcome.DONE, report.outcome)
        assertEquals(2, report.runs)
        assertEquals(2 * HOUR, report.elapsedMillis)

        assertEquals(UpgradeRepairRun.DONE, repair().run())
        assertEquals("never twice", 1, reports.size)
    }

    @Test
    fun aFreshInstallNeverReports() = runTest {
        assertEquals(UpgradeRepairRun.DONE, repair().run())
        assertTrue(reports.isEmpty())
    }

    @Test
    fun nothingIsSentWhenTheSentRecordCantBeSaved() = runTest {
        seed(show("920666", OLD))
        tally =
            object : UpgradeRepairTally by MemoryUpgradeRepairTally() {
                override fun markReported(): Boolean = false
            }
        assertEquals(UpgradeRepairRun.DONE, repair().run())
        assertTrue(reports.isEmpty())
    }

    @Test
    fun theTallySurvivesInSharedPreferences() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(SharedPreferencesUpgradeRepairTally.FILE_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val first = SharedPreferencesUpgradeRepairTally(prefs)
        assertTrue(first.recordRun(startedAt = 10L, endedAt = 20L, addressesSwapped = 2, notificationsReregistered = 1))
        assertTrue(first.recordRun(startedAt = 30L, endedAt = 50L, addressesSwapped = 1, notificationsReregistered = 0))
        assertFalse(first.reported)
        assertTrue(first.markReported())

        val reopened = SharedPreferencesUpgradeRepairTally(prefs)
        assertEquals(UpgradeRepairCounts(runs = 2, firstRunAt = 10L, lastRunAt = 50L, addressesSwapped = 3, notificationsReregistered = 1), reopened.read())
        assertTrue(reopened.reported)
    }

    private fun repair() = UpgradeAddressRepair(
        repairDao = repairs,
        podcastDao = podcasts,
        ports =
        UpgradeRepairPorts(
            fetchAddresses = { ids ->
                if (!online) {
                    AddressCheckAnswer.Unavailable
                } else {
                    AddressCheckAnswer.Answered(V2ShowsFeedsResponse(registryAvailable = true, results = ids.map { answers[it] ?: V2ShowAddress(id = it) }))
                }
            },
            replaceAddress = FeedAddressUpdater(podcasts, requestNotificationRegistration = {})::update,
            reread = { _, _ -> RefreshOutcome.Success(null, 1, true) },
            requestNotificationRegistration = {},
            onAddressStepsFinished = {},
            isOnline = { online },
            telemetry = telemetry,
        ),
        marker = marker,
        enabled = { true },
        tally = tally,
        nowMillis = { now },
    )

    private val marker = MemoryUpgradeNotificationMarker()

    private suspend fun seed(row: PodcastEntity) {
        podcasts.upsertRow(row)
        repairs.upsert(UpgradeRepairShowEntity(row.podcastId))
    }

    private fun moved(id: Long) = V2ShowAddress(
        id = id,
        fetchUrl = NEW,
        fetchVia = "direct",
        feedSource = "registry:apple",
        epNs = id,
        flags = V2ShowFlags(addressRepair = true),
    )

    private fun show(id: String, feedUrl: String?, notifications: Boolean = false) = PodcastEntity(
        podcastId = id,
        title = "Show",
        author = "Author",
        imageUrl = "",
        description = null,
        isSubscribed = true,
        notificationsEnabled = notifications,
        feedUrl = feedUrl,
    )

    private companion object {
        const val OLD = "https://old.example.com/feed.xml"
        const val NEW = "https://new.example.com/feed.xml"
        const val OTHER = "https://other.example.com/feed.xml"
        const val NOW = 1_800_000_000_000L
        const val HOUR = 3_600_000L
    }
}
