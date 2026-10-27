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
import cx.aswin.boxlore.core.model.FeedFetchSource
import cx.aswin.boxlore.core.model.FeedUrlCorrection
import cx.aswin.boxlore.core.network.NetworkModule
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Step 1 of the one-time address repair (own-episode-IDs P4 PR 15, plan section 11) through the
 * real `/v2` client against MockWebServer serving `/v2/shows/feeds` answers in the route's shape
 * (`fixtures/v2`, see its README): how each kind of answer is recorded, and which failures count
 * as a try.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpgradeAddressRepairApiTest {
    private lateinit var server: MockWebServer
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcasts: PodcastDao
    private lateinit var repairs: UpgradeRepairDao
    private val responses = ArrayDeque<MockResponse>()
    private val rereads = mutableListOf<Pair<String, Long?>>()
    private val reports = mutableListOf<FeedFetchReport>()

    private val telemetry =
        object : FeedTelemetryPort {
            override fun feedFetched(report: FeedFetchReport) {
                reports += report
            }

            override fun feedUrlCorrected(correction: FeedUrlCorrection) = Unit
        }

    @Before
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : okhttp3.mockwebserver.Dispatcher() {
                override fun dispatch(request: okhttp3.mockwebserver.RecordedRequest): MockResponse = responses.removeFirstOrNull() ?: MockResponse().setResponseCode(404)
            }
        server.start()
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        podcasts = database.podcastDao()
        repairs = database.upgradeRepairDao()
    }

    @After
    fun tearDown() {
        database.close()
        server.shutdown()
    }

    @Test
    fun `a recorded answer swaps the confirmed move only and records the rest`() = runTest {
        seed("920666", OLD)
        seed("920667", "http://plain.example.com/feed.xml")
        seed("920668", OTHER)
        seed("920669", OTHER)
        responses += fixture("fixtures/v2/shows-feeds-upgrade-cases.json")

        assertEquals(UpgradeRepairRun.DONE, repair().run())

        val sent = server.takeRequest()
        assertEquals("POST", sent.method)
        assertEquals("/v2/shows/feeds", sent.path)
        assertEquals("{\"ids\":[920666,920667,920668,920669]}", sent.body.readUtf8())

        assertEquals("a confirmed direct move is swapped", NEW, podcasts.getPodcast("920666")!!.feedUrl)
        assertEquals("an http-only show keeps its address", "http://plain.example.com/feed.xml", podcasts.getPodcast("920667")!!.feedUrl)
        assertEquals("https://api.example.test/v2/feed/920667", repairs.getShow("920667")!!.checkedTransportUrl)
        assertNull(repairs.getShow("920667")!!.checkedFetchUrl)
        assertEquals("a show the lookup doesn't know keeps its address", OTHER, podcasts.getPodcast("920668")!!.feedUrl)
        assertEquals("an unflagged address is no move", OTHER, podcasts.getPodcast("920669")!!.feedUrl)
        assertEquals(4242L, repairs.getShow("920669")!!.checkedEpNs)
        assertNull("the server's namespace is never stored for the show", podcasts.getPodcast("920669")!!.epNs)
        assertEquals(listOf("920666" to 920666L, "920667" to 920667L, "920668" to 920668L, "920669" to 4242L), rereads)
        assertEquals(listOf(FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, 4, 0, 0, namespaceMismatchCount = 1)), reports)
    }

    @Test
    fun `a 503 and a registry that is down keep the chunk waiting without counting a try`() = runTest {
        seed("920666", OLD)
        responses += json("""{"error":"lookup_unavailable"}""", code = 503)
        responses += fixture("fixtures/v2/shows-feeds-registry-down.json")
        val repair = repair()

        assertEquals(UpgradeRepairRun.RETRY, repair.run())
        assertEquals(UpgradeRepairRun.RETRY, repair.run())

        val row = repairs.getShow("920666")!!
        assertEquals(0, row.completedStep)
        assertEquals(0, row.attempts)
        assertEquals("address_unavailable", row.lastError)
        assertEquals(OLD, podcasts.getPodcast("920666")!!.feedUrl)
        assertTrue(rereads.isEmpty())
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a refused request and an unreadable answer each count a try`() = runTest {
        seed("920666", OLD)
        responses += json("""{"error":"invalid_ids","max":500}""", code = 400)
        responses += json("<html>")
        val repair = repair()

        assertEquals(UpgradeRepairRun.RETRY, repair.run())
        assertEquals(1, repairs.getShow("920666")!!.attempts)
        assertEquals(UpgradeRepairRun.RETRY, repair.run())
        assertEquals(2, repairs.getShow("920666")!!.attempts)
        assertEquals("address_refused", repairs.getShow("920666")!!.lastError)
    }

    private fun repair() = UpgradeAddressRepair(
        repairDao = repairs,
        podcastDao = podcasts,
        ports =
        UpgradeRepairPorts(
            fetchAddresses = AddressCheckAnswer.fetchWith(NetworkModule.createBoxLoreApiV2(server.url("/").toString(), OkHttpClient())),
            replaceAddress = FeedAddressUpdater(podcasts, requestNotificationRegistration = {})::update,
            reread = { row, epNs ->
                rereads += row.podcastId to epNs
                RefreshOutcome.Success(null, 1, true)
            },
            requestNotificationRegistration = {},
            telemetry = telemetry,
        ),
        marker = MemoryUpgradeNotificationMarker(),
        enabled = { true },
        nowMillis = { NOW },
    )

    private suspend fun seed(id: String, feedUrl: String) {
        podcasts.upsertRow(
            PodcastEntity(podcastId = id, title = "Show", author = "Author", imageUrl = "", description = null, isSubscribed = true, feedUrl = feedUrl),
        )
        repairs.upsert(UpgradeRepairShowEntity(id))
    }

    private fun fixture(path: String): MockResponse = json(requireNotNull(javaClass.classLoader?.getResource(path)) { path }.readText())

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private companion object {
        const val OLD = "https://old.example.com/feed.xml"
        const val NEW = "https://new.example.com/feed.xml"
        const val OTHER = "https://other.example.com/feed.xml"
        const val NOW = 1_800_000_000_000L
    }
}
