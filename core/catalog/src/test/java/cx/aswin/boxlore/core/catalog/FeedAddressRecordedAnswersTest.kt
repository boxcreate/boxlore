package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.NetworkModule
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The daily address check and broken-feed reports on the API's own answers (`fixtures/v2`, see its
 * README), decoded by the app's client: a registry move, a dump address, a show the API doesn't
 * know, an http-only show, an unavailable registry, and a report's answer (own-episode-IDs P4 PR 14).
 */
class FeedAddressRecordedAnswersTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BoxLoreApiV2
    private val rows = mutableMapOf<String, PodcastEntity>()
    private val changes = mutableListOf<FeedAddressChange>()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = NetworkModule.createBoxLoreApiV2(server.url("/").toString(), OkHttpClient())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `only the recorded registry move is swapped, and every show the registry answered for is checked`() = runTest {
        listOf("3391039", "920666", "9999999999", "7").forEach { id -> rows[id] = show(id) }
        server.enqueue(json(fixture("shows-feeds.json")))

        val outcome = check().run()

        assertEquals("""{"ids":[3391039,920666,9999999999,7]}""", server.takeRequest().body.readUtf8())
        val change = changes.single()
        assertEquals("3391039", change.podcastId)
        assertEquals("https://www.spreaker.com/show/4688572/episodes/feed", change.feedUrl)
        assertEquals("registry:apple", change.source)
        assertEquals(1, outcome.swapped)
        assertEquals(4, outcome.checked)
        assertTrue(rows.values.all { it.feedUrlCheckedAt == NOW }, "a dump address, an unknown show and an http-only show keep their address")
    }

    @Test
    fun `an unavailable registry, or an answer without the field, checks nothing`() = runTest {
        rows["3391039"] = show("3391039")
        server.enqueue(json(fixture("shows-feeds-registry-unavailable.json")))
        server.enqueue(json("""{"results":[]}"""))

        assertEquals(1, check().run().unchecked)
        assertEquals(1, check().run().unchecked)

        assertTrue(changes.isEmpty())
        assertEquals(0L, rows.getValue("3391039").feedUrlCheckedAt, "the /podcast fallback may still run")
    }

    @Test
    fun `a report's recorded answer offers the registry address once, and a refused report counts nothing`() = runTest {
        val stamps = MemoryFeedReportStamps()
        val reporter =
            FeedReporter(
                send = { request -> FeedReporter.sendWith(api, request) },
                limiter = FeedReportLimiter(stamps),
                isOnline = { true },
                enabled = { true },
                nowMillis = { NOW },
            )
        server.enqueue(MockResponse().setResponseCode(429).setBody("""{"error":"report_limit"}"""))
        server.enqueue(json(fixture("feed-report.json")))

        assertNull(reporter.report("3391039", "http_404", OLD))
        assertTrue(stamps.all().isEmpty())
        assertEquals("https://www.spreaker.com/show/4688572/episodes/feed", reporter.report("3391039", "http_404", OLD))

        server.takeRequest()
        assertEquals("""{"id":3391039,"status":"http_404"}""", server.takeRequest().body.readUtf8())
        assertEquals(setOf("3391039"), stamps.all().keys)
        assertFalse(FeedReportLimiter(stamps).allows("3391039", NOW))
    }

    private fun check() = FeedAddressDailyCheck(
        followedShows = { rows.values.toList() },
        fetchAddresses = { ids -> FeedAddressDailyCheck.fetchWith(api, ids) },
        readsAt = { _, _ -> true },
        replaceAddress = { change ->
            changes += change
            rows[change.podcastId] = rows.getValue(change.podcastId).copy(feedUrl = change.feedUrl, feedUrlCheckedAt = change.checkedAt)
            FeedAddressUpdate.UPDATED
        },
        markChecked = { id, at -> rows[id] = rows.getValue(id).copy(feedUrlCheckedAt = at) },
        enabled = { true },
        nowMillis = { NOW },
    )

    private fun fixture(name: String): String = requireNotNull(javaClass.classLoader?.getResource("fixtures/v2/$name")) { name }.readText()

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

    private fun show(id: String) = PodcastEntity(
        podcastId = id,
        title = "Show $id",
        author = "Author",
        imageUrl = "",
        description = null,
        isSubscribed = true,
        feedUrl = OLD,
    )

    private companion object {
        const val NOW = 50 * FeedAddressDailyCheck.CHECK_INTERVAL_MS
        const val OLD = "https://old.example/feed.xml"
    }
}
