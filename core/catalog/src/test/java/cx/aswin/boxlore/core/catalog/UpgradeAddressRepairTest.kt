package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.UpgradeRepairDao
import cx.aswin.boxlore.core.database.UpgradeRepairShowEntity
import cx.aswin.boxlore.core.database.UpgradeRepairState
import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedFetchSource
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import cx.aswin.boxlore.core.network.model.V2ShowAddress
import cx.aswin.boxlore.core.network.model.V2ShowFlags
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsResponse
import cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The one-time repair of saved feed addresses after the update (own-episode-IDs P4 PR 15, plan
 * section 11): steps 1–3 per show in Room, step 4 in a marker a restore never brings, resumable
 * after process death, offline-safe, capped per show, and silent on fresh installs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpgradeAddressRepairTest {
    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var database: BoxLoreDatabase
    private lateinit var podcasts: PodcastDao
    private lateinit var repairs: UpgradeRepairDao
    private lateinit var updater: FeedAddressUpdater
    private val calls = mutableListOf<List<Long>>()
    private val answers = mutableMapOf<Long, V2ShowAddress>()
    private val rereads = mutableListOf<Pair<String, Long?>>()
    private val registrations = mutableListOf<String>()
    private val corrections = mutableListOf<FeedUrlCorrection>()
    private val reports = mutableListOf<FeedFetchReport>()
    private var marker: UpgradeNotificationMarker = MemoryUpgradeNotificationMarker()
    private var online = true
    private var enabled = true
    private var now = NOW
    private var catchUps = 0
    private var fetch: suspend (List<Long>) -> AddressCheckAnswer = { ids -> answer(ids) }
    private var register: suspend (String) -> Unit = { registrations += it }
    private var reread: suspend (PodcastEntity, Long?) -> RefreshOutcome = { _, _ -> RefreshOutcome.Success(null, 1, true) }

    private val telemetry =
        object : FeedTelemetryPort {
            override fun feedFetched(report: FeedFetchReport) {
                reports += report
            }

            override fun feedUrlCorrected(correction: FeedUrlCorrection) {
                corrections += correction
            }
        }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        podcasts = database.podcastDao()
        repairs = database.upgradeRepairDao()
        updater = FeedAddressUpdater(podcasts, requestNotificationRegistration = { registrations += it }, telemetry = telemetry)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a fresh install and rss-only follows settle without network`() = runTest {
        val repair = repair()
        assertFalse(repair.needsWork())
        assertTrue(marker.isComplete)

        marker = MemoryUpgradeNotificationMarker()
        podcasts.upsertRow(show("rss:abc", "https://example.com/rss.xml", notifications = true).copy(sourceType = PodcastEntity.SOURCE_RSS))
        val rssOnly = repair()
        assertFalse(rssOnly.needsWork())
        assertEquals(UpgradeRepairRun.DONE, rssOnly.run())

        assertTrue(calls.isEmpty())
        assertTrue(rereads.isEmpty())
        assertTrue(registrations.isEmpty())
        assertEquals("nothing was repaired, so the sync is left alone", 0, catchUps)
    }

    @Test
    fun `each show is checked, a confirmed move swapped, read again and registered again`() = runTest {
        seed(show("920666", OLD, notifications = true))
        seed(show("920667", OTHER))
        answers[920666] = moved(920666)

        val repair = repair()
        assertTrue(repair.needsWork())
        assertEquals(UpgradeRepairRun.DONE, repair.run())

        val swapped = podcasts.getPodcast("920666")!!
        assertEquals(NEW, swapped.feedUrl)
        assertEquals(OLD, swapped.previousFeedUrl)
        assertEquals("registry:apple", swapped.feedUrlSource)
        assertEquals(NOW, swapped.feedUrlCheckedAt)
        assertEquals("a show the server doesn't know keeps its address", OTHER, podcasts.getPodcast("920667")!!.feedUrl)
        assertEquals(NOW, podcasts.getPodcast("920667")!!.feedUrlCheckedAt)
        assertEquals(listOf(FeedUrlCorrection("920666", FeedUrlCorrectionReason.REGISTRY, FeedUrlCorrectionStep.UPGRADE_REPAIR)), corrections)
        assertEquals(listOf("920666" to 920666L, "920667" to null), rereads)
        for (id in listOf("920666", "920667")) {
            val row = repairs.getShow(id)!!
            assertEquals(3, row.completedStep)
            assertEquals(UpgradeRepairState.DONE, row.state)
        }
        assertEquals("the writer and step 4 both ask; shows with notifications off never", listOf("920666", "920666"), registrations)
        assertTrue(marker.isComplete)
        assertEquals(1, catchUps)
        assertEquals(listOf(FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, okCount = 2, notModifiedCount = 0, failedCount = 0)), reports)
        assertFalse(repair.needsWork())
    }

    @Test
    fun `more than 500 followed shows are asked in chunks of at most 500`() = runTest {
        repeat(501) { seed(show((1_000 + it).toString(), OLD)) }

        assertEquals(UpgradeRepairRun.DONE, repair().run())

        assertEquals(listOf(500, 1), calls.map { it.size })
        assertEquals(0, repairs.pendingCount())
    }

    @Test
    fun `a partial batch keeps the failed chunk at step 0 without counting a try`() = runTest {
        repeat(501) { seed(show((1_000 + it).toString(), OLD)) }
        fetch = { ids -> if (calls.isEmpty()) answer(ids) else AddressCheckAnswer.Unavailable.also { calls += ids } }

        assertEquals(UpgradeRepairRun.RETRY, repair().run())

        assertEquals(1, repairs.pendingCount())
        val waiting = repairs.getShow("1500")!!
        assertEquals(0, waiting.completedStep)
        assertEquals(0, waiting.attempts)
        assertEquals(3, repairs.getShow("1499")!!.completedStep)
        assertFalse("step 4 waits for every show", marker.isComplete)

        calls.clear()
        fetch = { ids -> answer(ids) }
        assertEquals(UpgradeRepairRun.DONE, repair().run())
        assertEquals(listOf(listOf(1500L)), calls)
    }

    @Test
    fun `offline the address check advances nothing and counts no try`() = runTest {
        seed(show("920666", OLD, notifications = true))
        online = false
        fetch = { ids ->
            calls += ids
            throw IOException("offline")
        }

        assertEquals(UpgradeRepairRun.RETRY, repair().run())

        val row = repairs.getShow("920666")!!
        assertEquals(0, row.completedStep)
        assertEquals(0, row.attempts)
        assertTrue(rereads.isEmpty())
        assertTrue(registrations.isEmpty())
        assertFalse(marker.isComplete)
    }

    @Test
    fun `a read that fails while offline counts no try and stops the run`() = runTest {
        seed(show("920666", OLD), completedStep = 2)
        seed(show("920667", OTHER), completedStep = 2)
        online = false
        reread = { _, _ -> RefreshOutcome.Failure("offline", reportStatus = "network") }

        assertEquals(UpgradeRepairRun.RETRY, repair().run())

        assertEquals(listOf("920666"), rereads.map { it.first })
        for (id in listOf("920666", "920667")) {
            assertEquals(0, repairs.getShow(id)!!.attempts)
            assertEquals(UpgradeRepairState.PENDING, repairs.getShow(id)!!.state)
        }
    }

    @Test
    fun `process death mid-step resumes at the next step without asking or swapping again`() = runTest {
        seed(show("920666", OLD, notifications = true))
        answers[920666] = moved(920666)
        reread = { _, _ -> throw CancellationException("process killed") }

        try {
            repair().run()
            fail("the run was stopped")
        } catch (_: CancellationException) {
            // Expected: the process died during step 3.
        }
        assertEquals(2, repairs.getShow("920666")!!.completedStep)
        assertEquals(NEW, podcasts.getPodcast("920666")!!.feedUrl)

        calls.clear()
        rereads.clear()
        reread = { _, _ -> RefreshOutcome.Unchanged(null) }
        assertEquals(UpgradeRepairRun.DONE, repair().run())

        assertTrue("step 1 is not asked again", calls.isEmpty())
        assertEquals("the address is swapped once", 1, corrections.size)
        assertEquals(listOf("920666" to 920666L), rereads)
        assertEquals(UpgradeRepairState.DONE, repairs.getShow("920666")!!.state)
    }

    @Test
    fun `a restore with Room done but no step 4 record registers every followed show again`() = runTest {
        podcasts.upsertRow(show("920666", OLD, notifications = true))
        podcasts.upsertRow(show("920667", OTHER, notifications = true))
        podcasts.upsertRow(show("920668", OTHER))
        podcasts.upsertRow(show("rss:abc", OTHER, notifications = true).copy(sourceType = PodcastEntity.SOURCE_RSS))
        repairs.upsert(UpgradeRepairShowEntity("920666", completedStep = 3, state = UpgradeRepairState.DONE))
        repairs.upsert(UpgradeRepairShowEntity("920667", completedStep = 1, state = UpgradeRepairState.GAVE_UP))

        val repair = repair()
        assertTrue(repair.needsWork())
        assertEquals(UpgradeRepairRun.DONE, repair.run())

        assertEquals(listOf("920666", "920667"), registrations.sorted())
        assertEquals("the restored phone's next sync catches up again", 1, catchUps)
        assertTrue(calls.isEmpty())
        assertTrue(rereads.isEmpty())
        assertTrue(marker.isComplete)
        assertFalse(repair.needsWork())
    }

    @Test
    fun `a show unfollowed between steps ends done without a swap or a read`() = runTest {
        seed(show("920666", OLD, notifications = true))
        answers[920666] = moved(920666)
        fetch = { ids ->
            answer(ids).also { podcasts.upsertRow(podcasts.getPodcast("920666")!!.copy(isSubscribed = false)) }
        }

        assertEquals(UpgradeRepairRun.DONE, repair().run())

        val row = repairs.getShow("920666")!!
        assertEquals(UpgradeRepairState.DONE, row.state)
        assertEquals(1, row.completedStep)
        assertEquals(OLD, podcasts.getPodcast("920666")!!.feedUrl)
        assertTrue(rereads.isEmpty())
        assertTrue(registrations.isEmpty())
    }

    @Test
    fun `a follow added mid-run is left to the daily check and doesn't hold the repair`() = runTest {
        seed(show("920666", OLD))
        answers[920666] = moved(920666)
        answers[920700] = moved(920700)
        fetch = { ids -> answer(ids).also { podcasts.upsertRow(show("920700", OLD, notifications = true)) } }

        assertEquals(UpgradeRepairRun.DONE, repair().run())

        assertEquals(listOf(listOf(920666L)), calls)
        assertNull(repairs.getShow("920700"))
        assertEquals(OLD, podcasts.getPodcast("920700")!!.feedUrl)
        assertEquals(listOf("920666"), rereads.map { it.first })
        assertTrue(marker.isComplete)
    }

    @Test
    fun `a dead show gives up at the cap and the repair then finishes`() = runTest {
        seed(show("920666", OLD, notifications = true))
        reread = { _, _ -> RefreshOutcome.Failure("gone", addressGone = true, reportStatus = "http_404") }
        val repair = repair()

        repeat(UpgradeAddressRepair.MAX_ATTEMPTS - 1) { assertEquals(UpgradeRepairRun.RETRY, repair.run()) }
        assertEquals(UpgradeRepairState.PENDING, repairs.getShow("920666")!!.state)
        assertTrue("step 4 waits for the show", registrations.isEmpty())

        assertEquals(UpgradeRepairRun.DONE, repair.run())
        val row = repairs.getShow("920666")!!
        assertEquals(UpgradeRepairState.GAVE_UP, row.state)
        assertEquals(UpgradeAddressRepair.MAX_ATTEMPTS, row.attempts)
        assertEquals("http_404", row.lastError)
        assertEquals(UpgradeAddressRepair.MAX_ATTEMPTS, rereads.size)
        assertEquals("notifications are registered again even for a show that gave up", listOf("920666"), registrations)
        assertEquals(UpgradeRepairRun.DONE, repair.run())
        assertEquals(UpgradeAddressRepair.MAX_ATTEMPTS, rereads.size)
    }

    @Test
    fun `a different server namespace is recorded, reported and handed to the read only to compare`() = runTest {
        seed(show("920666", OLD).copy(epNs = 920666L))
        seed(show("920667", OTHER))
        answers[920666] = V2ShowAddress(id = 920666, fetchUrl = OLD, fetchVia = "direct", feedSource = "registry:apple", epNs = 4242)
        answers[920667] = V2ShowAddress(id = 920667, epNs = 920667)

        assertEquals(UpgradeRepairRun.DONE, repair().run())

        assertEquals(4242L, repairs.getShow("920666")!!.checkedEpNs)
        assertEquals("the phone's own namespace is kept", 920666L, podcasts.getPodcast("920666")!!.epNs)
        assertEquals("the read gets it only to compare", listOf("920666" to 4242L, "920667" to 920667L), rereads)
        assertTrue(corrections.isEmpty())
        assertEquals(listOf(FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, 2, 0, 0, namespaceMismatchCount = 1)), reports)
    }

    @Test
    fun `the production read keeps the saved address, passes the namespace and starts no auto-download`() = runTest {
        val requests = mutableListOf<LocalEpisodeCatalogPort.RefreshRequest>()
        val catalog =
            object : LocalEpisodeCatalogPort by FakeLocalEpisodeCatalogPort() {
                override suspend fun refresh(request: LocalEpisodeCatalogPort.RefreshRequest): RefreshOutcome {
                    requests += request
                    return RefreshOutcome.Unchanged(null)
                }
            }

        val outcome = UpgradeAddressRepair.rereadWith(catalog)(show("920666", OLD).copy(epNs = 920666L), 4242L)

        assertEquals(RefreshOutcome.Unchanged(null), outcome)
        val request = requests.single()
        assertEquals("920666", request.podcastIndexId)
        assertEquals(OLD, request.feedUrl)
        assertEquals(4242L, request.serverEpNs)
        assertEquals(LocalEpisodeCatalogPort.RefreshReason.NORMAL, request.reason)
        assertFalse("the background repair never starts the foreground auto-download pass", request.runPostPersistCallback)
    }

    @Test
    fun `a follow changed on the phone keeps its address`() = runTest {
        seed(show("920666", OLD).copy(isDirty = true, syncedAt = 5L))
        answers[920666] = moved(920666)

        assertEquals(UpgradeRepairRun.DONE, repair().run())

        val row = podcasts.getPodcast("920666")!!
        assertEquals(OLD, row.feedUrl)
        assertEquals("the daily check asks again once it is pushed", 0L, row.feedUrlCheckedAt)
        assertTrue(corrections.isEmpty())
        assertEquals(UpgradeRepairState.DONE, repairs.getShow("920666")!!.state)
    }

    @Test
    fun `with the switch off nothing runs and nothing is marked done`() = runTest {
        seed(show("920666", OLD, notifications = true))
        enabled = false

        val repair = repair()
        assertEquals(UpgradeRepairRun.PAUSED, repair.run())

        assertTrue(calls.isEmpty())
        assertEquals(1, repairs.pendingCount())
        assertFalse(marker.isComplete)
        assertTrue(repair.needsWork())

        enabled = true
        assertEquals(UpgradeRepairRun.DONE, repair.run())
    }

    @Test
    fun `the step 4 record survives a new process in its file and starts empty without one`() {
        val file = File(folder.root, FileUpgradeNotificationMarker.FILE_NAME)
        val first = FileUpgradeNotificationMarker(file)
        assertFalse(first.isComplete)
        assertTrue(first.registered().isEmpty())

        assertTrue(first.addRegistered(listOf("920666", "920667")))
        assertTrue(first.markComplete())
        assertFalse(first.syncCatchUpRequested)
        assertTrue(first.markSyncCatchUpRequested())

        val next = FileUpgradeNotificationMarker(file)
        assertEquals("its own records are no shows", setOf("920666", "920667"), next.registered())
        assertTrue(next.isComplete)
        assertTrue(next.syncCatchUpRequested)

        val restored = FileUpgradeNotificationMarker(File(folder.root, "restored"))
        assertFalse(restored.isComplete)
    }

    @Test
    fun `the swap target is checked again against the row as it is now`() {
        val entry = UpgradeRepairShowEntity("920666", completedStep = 1, checkedFetchUrl = NEW, checkedFeedSource = "registry:apple", lastAttemptAt = NOW)
        assertEquals(NEW, UpgradeAddressRepair.swapTarget(show("920666", OLD), entry, NOW))
        assertNull("already there", UpgradeAddressRepair.swapTarget(show("920666", NEW), entry, NOW))
        assertNull(
            "a confirmed feed move left that address",
            UpgradeAddressRepair.swapTarget(show("920666", OLD).copy(feedUrlSource = FeedUrlSources.FEED_MOVE, previousFeedUrl = NEW), entry, NOW),
        )
        assertNull("no confirmed move", UpgradeAddressRepair.swapTarget(show("920666", OLD), entry.copy(checkedFetchUrl = null), NOW))
        assertNull("a day old", UpgradeAddressRepair.swapTarget(show("920666", OLD), entry, NOW + FeedAddressDailyCheck.CHECK_INTERVAL_MS))
        assertNull("the daily check has a newer answer", UpgradeAddressRepair.swapTarget(show("920666", OLD).copy(feedUrlCheckedAt = NOW + 1), entry, NOW + 1))
    }

    @Test
    fun `an answer left waiting by a pause is asked again later and never applied`() = runTest {
        seed(show("920666", OLD, notifications = true))
        answers[920666] = moved(920666)
        fetch = { ids -> answer(ids).also { enabled = false } }

        assertEquals("switched off after step 1", UpgradeRepairRun.PAUSED, repair().run())
        assertEquals(1, repairs.getShow("920666")!!.completedStep)
        assertEquals(NEW, repairs.getShow("920666")!!.checkedFetchUrl)

        now = NOW + 2 * FeedAddressDailyCheck.CHECK_INTERVAL_MS
        enabled = true
        answers.remove(920666)
        calls.clear()
        fetch = { ids -> answer(ids) }
        assertEquals(UpgradeRepairRun.DONE, repair().run())

        assertEquals("asked again", listOf(listOf(920666L)), calls)
        assertEquals("the old answer is never applied", OLD, podcasts.getPodcast("920666")!!.feedUrl)
        assertTrue(corrections.isEmpty())
        assertEquals(UpgradeRepairState.DONE, repairs.getShow("920666")!!.state)
    }

    @Test
    fun `a failed swap keeps the time of its answer, so the answer still expires`() = runTest {
        seed(show("920666", OLD))
        answers[920666] = moved(920666)
        var swaps = 0
        val failingSwap: suspend (FeedAddressChange) -> FeedAddressUpdate = {
            swaps++
            error("database busy")
        }

        assertEquals(UpgradeRepairRun.RETRY, repair(replace = failingSwap).run())
        val waiting = repairs.getShow("920666")!!
        assertEquals(1, waiting.completedStep)
        assertEquals(1, waiting.attempts)
        assertEquals(NOW, waiting.lastAttemptAt)

        now = NOW + FeedAddressDailyCheck.CHECK_INTERVAL_MS
        answers.remove(920666)
        assertEquals(UpgradeRepairRun.DONE, repair().run())
        assertEquals(OLD, podcasts.getPodcast("920666")!!.feedUrl)
        assertEquals(1, swaps)
    }

    @Test
    fun `a refused address check counts a try and the show goes on without an answer at the cap`() = runTest {
        seed(show("920666", OLD, notifications = true))
        fetch = { ids -> AddressCheckAnswer.Rejected.also { calls += ids } }
        val repair = repair()

        repeat(UpgradeAddressRepair.MAX_ATTEMPTS - 1) { assertEquals(UpgradeRepairRun.RETRY, repair.run()) }
        val waiting = repairs.getShow("920666")!!
        assertEquals(0, waiting.completedStep)
        assertEquals(UpgradeAddressRepair.MAX_ATTEMPTS - 1, waiting.attempts)
        assertTrue(rereads.isEmpty())

        assertEquals(UpgradeRepairRun.DONE, repair.run())
        val row = repairs.getShow("920666")!!
        assertEquals(UpgradeRepairState.DONE, row.state)
        assertNull("no answer, so no swap", row.checkedFetchUrl)
        assertEquals(OLD, podcasts.getPodcast("920666")!!.feedUrl)
        assertEquals("the read and step 4 still run", listOf("920666" to null), rereads)
        assertEquals(listOf("920666"), registrations)
        assertEquals(UpgradeAddressRepair.MAX_ATTEMPTS, calls.size)
    }

    @Test
    fun `an address check the server can't answer counts no try and goes on without an answer after a week`() = runTest {
        seed(show("920666", OLD))
        seed(show("920667", OTHER))
        var registryDown = false
        fetch = { ids ->
            calls += ids
            if (registryDown) {
                AddressCheckAnswer.Answered(V2ShowsFeedsResponse(registryAvailable = false, results = ids.map { V2ShowAddress(id = it, fetchUrl = OLD) }))
            } else {
                AddressCheckAnswer.Unavailable
            }
        }
        val repair = repair()

        assertEquals(UpgradeRepairRun.RETRY, repair.run())
        now = NOW + 6 * FeedAddressDailyCheck.CHECK_INTERVAL_MS
        registryDown = true
        assertEquals(UpgradeRepairRun.RETRY, repair.run())
        for (id in listOf("920666", "920667")) {
            val row = repairs.getShow(id)!!
            assertEquals(0, row.completedStep)
            assertEquals("never counted as a try", 0, row.attempts)
            assertEquals("the first failed check", NOW, row.lastAttemptAt)
        }
        assertTrue(rereads.isEmpty())

        now = NOW + UpgradeAddressRepair.MAX_UNANSWERED_MS
        assertEquals(UpgradeRepairRun.DONE, repair.run())
        assertEquals(listOf("920666" to null, "920667" to null), rereads)
        assertEquals(OTHER, podcasts.getPodcast("920667")!!.feedUrl)
        assertTrue(corrections.isEmpty())
        assertEquals(0, repairs.pendingCount())
    }

    @Test
    fun `the sync catch-up is asked for once, after the last show, never while shows wait or the run is paused`() = runTest {
        repeat(501) { seed(show((1_000 + it).toString(), OLD)) }
        fetch = { ids -> if (calls.isEmpty()) answer(ids) else AddressCheckAnswer.Unavailable.also { calls += ids } }
        val repair = repair()

        assertEquals(UpgradeRepairRun.RETRY, repair.run())
        assertEquals("one show still waits", 0, catchUps)

        enabled = false
        assertEquals(UpgradeRepairRun.PAUSED, repair.run())
        assertEquals(0, catchUps)

        enabled = true
        fetch = { ids -> answer(ids) }
        assertEquals(UpgradeRepairRun.DONE, repair.run())
        assertEquals(1, catchUps)
        assertTrue(marker.syncCatchUpRequested)

        assertEquals(UpgradeRepairRun.DONE, repair.run())
        assertFalse(repair.needsWork())
        assertEquals("never again on this install", 1, catchUps)
    }

    @Test
    fun `a sync catch-up that couldn't be asked for keeps the repair going until it is`() = runTest {
        seed(show("920666", OLD))
        var failing = true
        val repair =
            UpgradeAddressRepair(
                repairDao = repairs,
                podcastDao = podcasts,
                ports =
                UpgradeRepairPorts(
                    fetchAddresses = { ids -> answer(ids) },
                    replaceAddress = updater::update,
                    reread = { _, _ -> RefreshOutcome.Unchanged(null) },
                    requestNotificationRegistration = { registrations += it },
                    onAddressStepsFinished = {
                        check(!failing) { "prefs unavailable" }
                        catchUps++
                    },
                ),
                marker = marker,
                enabled = { true },
                nowMillis = { now },
            )

        assertEquals(UpgradeRepairRun.RETRY, repair.run())
        assertFalse(marker.syncCatchUpRequested)
        assertTrue(repair.needsWork())

        failing = false
        assertEquals(UpgradeRepairRun.DONE, repair.run())
        assertEquals(1, catchUps)
        assertFalse(repair.needsWork())
    }

    @Test
    fun `a registration request that couldn't be saved is not recorded and is asked again`() = runTest {
        seed(show("920666", OLD, notifications = true))
        var saved = false
        register = { id ->
            check(saved) { "the request wasn't saved" }
            registrations += id
        }

        assertEquals(UpgradeRepairRun.RETRY, repair().run())
        assertFalse("920666" in marker.registered())
        assertFalse(marker.isComplete)

        saved = true
        assertEquals(UpgradeRepairRun.DONE, repair().run())
        assertEquals(listOf("920666"), registrations)
        assertTrue("920666" in marker.registered())
        assertTrue(marker.isComplete)
    }

    private fun repair(replace: suspend (FeedAddressChange) -> FeedAddressUpdate = updater::update) = UpgradeAddressRepair(
        repairDao = repairs,
        podcastDao = podcasts,
        ports =
        UpgradeRepairPorts(
            fetchAddresses = { ids -> fetch(ids) },
            replaceAddress = replace,
            reread = { row, epNs ->
                rereads += row.podcastId to epNs
                reread(row, epNs)
            },
            requestNotificationRegistration = { register(it) },
            onAddressStepsFinished = { catchUps++ },
            isOnline = { online },
            telemetry = telemetry,
        ),
        marker = marker,
        enabled = { enabled },
        nowMillis = { now },
    )

    private fun answer(ids: List<Long>): AddressCheckAnswer {
        calls += ids
        return AddressCheckAnswer.Answered(V2ShowsFeedsResponse(registryAvailable = true, results = ids.map { answers[it] ?: V2ShowAddress(id = it) }))
    }

    private suspend fun seed(row: PodcastEntity, completedStep: Int = 0) {
        podcasts.upsertRow(row)
        repairs.upsert(UpgradeRepairShowEntity(row.podcastId, completedStep = completedStep))
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
    }
}
