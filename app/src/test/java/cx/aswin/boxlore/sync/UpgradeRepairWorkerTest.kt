package cx.aswin.boxlore.sync

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.BackoffPolicy
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import cx.aswin.boxlore.core.catalog.AddressCheckAnswer
import cx.aswin.boxlore.core.catalog.MemoryUpgradeNotificationMarker
import cx.aswin.boxlore.core.catalog.UpgradeAddressRepair
import cx.aswin.boxlore.core.catalog.UpgradeRepairPorts
import cx.aswin.boxlore.core.catalog.UpgradeRepairRun
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.UpgradeRepairShowEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.network.model.V2ShowAddress
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsResponse
import kotlinx.coroutines.runBlocking
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
 * The shell of the one-time feed address repair (own-episode-IDs P4 PR 15, plan section 11): one
 * permanent unique work item, KEEP, network-gated with backoff, enqueued only while work is left,
 * and a run mapped to a WorkManager result with a per-launch cap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class UpgradeRepairWorkerTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: BoxLoreDatabase

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `the work has one permanent name, waits for a network and backs off exponentially`() {
        assertEquals("feed-address-upgrade-repair", UpgradeRepairWorker.WORK_NAME)
        val work = UpgradeRepairWorker.workRequest().workSpec
        assertEquals(NetworkType.CONNECTED, work.constraints.requiredNetworkType)
        assertEquals(BackoffPolicy.EXPONENTIAL, work.backoffPolicy)
        assertEquals(30_000L, work.backoffDelayDuration)
    }

    @Test
    fun `every launch with work left keeps the one waiting item`() = runBlocking {
        database.podcastDao().upsertRow(show("920666"))
        database.upgradeRepairDao().upsert(UpgradeRepairShowEntity("920666"))
        val repair = repair()

        assertTrue(UpgradeRepairWorker.scheduleIfNeeded(context, repair))
        val first = waiting().single().id
        assertTrue(UpgradeRepairWorker.scheduleIfNeeded(context, repair))

        assertEquals("KEEP leaves the waiting run in place", listOf(first), waiting().map { it.id })
    }

    @Test
    fun `a fresh install or rss-only follows enqueue nothing`() = runBlocking {
        assertFalse(UpgradeRepairWorker.scheduleIfNeeded(context, repair()))
        database.podcastDao().upsertRow(show("rss:abc").copy(sourceType = PodcastEntity.SOURCE_RSS))
        assertFalse(UpgradeRepairWorker.scheduleIfNeeded(context, repair()))

        assertTrue(waiting().isEmpty())
    }

    @Test
    fun `a run maps to a result, and retries stop at the per-launch cap`() = runBlocking {
        assertEquals(ListenableWorker.Result.success(), worker(UpgradeRepairRun.DONE).doWork())
        assertEquals("paused work is enqueued again by the next launch", ListenableWorker.Result.success(), worker(UpgradeRepairRun.PAUSED).doWork())
        assertEquals(ListenableWorker.Result.retry(), worker(UpgradeRepairRun.RETRY).doWork())
        assertEquals(ListenableWorker.Result.retry(), worker(UpgradeRepairRun.RETRY, attempt = UpgradeRepairWorker.MAX_RUNS - 2).doWork())
        assertEquals(ListenableWorker.Result.success(), worker(UpgradeRepairRun.RETRY, attempt = UpgradeRepairWorker.MAX_RUNS - 1).doWork())
    }

    @Test
    fun `an error counts as a retry`() = runBlocking {
        val failing = TestListenableWorkerBuilder<UpgradeRepairWorker>(context).build().apply { repair = { error("database closed") } }
        assertEquals(ListenableWorker.Result.retry(), failing.doWork())
    }

    @Test
    fun `the worker runs the real repair to the end`() = runBlocking {
        database.podcastDao().upsertRow(show("920666"))
        database.upgradeRepairDao().upsert(UpgradeRepairShowEntity("920666"))
        val repair = repair()
        val worker = TestListenableWorkerBuilder<UpgradeRepairWorker>(context).build().apply { this.repair = { repair.run() } }

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertEquals(0, database.upgradeRepairDao().pendingCount())
        assertFalse(repair.needsWork())
    }

    private fun worker(run: UpgradeRepairRun, attempt: Int = 0) =
        TestListenableWorkerBuilder<UpgradeRepairWorker>(context).setRunAttemptCount(attempt).build().apply { repair = { run } }

    private fun waiting() =
        WorkManager.getInstance(context).getWorkInfosForUniqueWork(UpgradeRepairWorker.WORK_NAME).get().filter { it.state == WorkInfo.State.ENQUEUED }

    private fun repair() = UpgradeAddressRepair(
        repairDao = database.upgradeRepairDao(),
        podcastDao = database.podcastDao(),
        ports =
        UpgradeRepairPorts(
            fetchAddresses = { ids -> AddressCheckAnswer.Answered(V2ShowsFeedsResponse(registryAvailable = true, results = ids.map { V2ShowAddress(id = it) })) },
            replaceAddress = { error("no swap expected") },
            reread = { _, _ -> LocalEpisodeCatalogPort.RefreshOutcome.Unchanged(null) },
            requestNotificationRegistration = {},
        ),
        marker = MemoryUpgradeNotificationMarker(),
        enabled = { true },
    )

    private fun show(id: String) = PodcastEntity(
        podcastId = id,
        title = "Show",
        author = "Author",
        imageUrl = "",
        description = null,
        isSubscribed = true,
        feedUrl = "https://example.com/feed.xml",
    )
}
