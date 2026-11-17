package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.ApiRouteBudget
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssFeedClient
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.net.URLDecoder
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * OPML import against an API that enforces a per-minute limit with 429 and Retry-After: the import
 * paces its requests through the shared [ApiRouteBudget], waits out a 429, and never drops an outline
 * the API refused; outlines it can't finish stay pending and a later run finishes them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpmlImportRateLimitTest {
    private lateinit var server: MockWebServer
    private lateinit var database: BoxLoreDatabase
    private lateinit var context: Context

    /** Virtual time shared by the budget (which advances it instead of sleeping) and the fake API. */
    private val clock = AtomicLong(0L)
    private val podcastCalls = CopyOnWriteArrayList<Long>()
    private val rateLimited = CopyOnWriteArrayList<String>()

    /** Requests the fake API serves per minute; past that it answers 429 with Retry-After: 60. */
    @Volatile private var serverPerMinute = 30

    /** Feed addresses the API can't answer for at all (503), until cleared. */
    private val broken = CopyOnWriteArrayList<String>()

    private val searches = CopyOnWriteArrayList<String>()

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty().substringBefore('?')
                    if (path == "/search") {
                        searches += path
                        return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"status":"true","feeds":[]}""")
                    }
                    if (path != "/podcast") return MockResponse().setResponseCode(404)
                    val url = URLDecoder.decode(request.requestUrl?.queryParameter("url").orEmpty(), "UTF-8")
                    val now = clock.get()
                    val recent = podcastCalls.count { now - it < ApiRouteBudget.WINDOW_MS }
                    podcastCalls += now
                    if (recent >= serverPerMinute) {
                        rateLimited += url
                        return MockResponse().setResponseCode(429).setHeader("Retry-After", "60").setBody("""{"error":"rate_limited"}""")
                    }
                    if (url in broken) return MockResponse().setResponseCode(503)
                    if ("/dead/" in url) return MockResponse().setResponseCode(404)
                    val id = url.substringAfterLast('/').toLong()
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"status":"true","feed":{"id":$id,"title":"Show $id","url":"$url"}}""")
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun aLargeImportFollowsEveryShowAlthoughTheApiRateLimits() = runBlocking {
        // Other requests share the minute: the API refuses past 20, below the budget's 25.
        serverPerMinute = 20
        val pending = MemoryPendingOpmlImports()
        val feeds = feeds(200)

        val report = import(feeds, pending)

        assertTrue("the API did answer 429 during the import", rateLimited.isNotEmpty())
        assertEquals("every outline is followed", feeds.map { it.xmlUrl.substringAfterLast('/') }.toSet(), report.imported.map { it.id }.toSet())
        assertTrue("nothing is left pending", report.pending.isEmpty() && pending.all().isEmpty())
    }

    @Test
    fun withinTheLimitTheImportNeverMeetsA429() = runBlocking {
        serverPerMinute = 30
        val report = import(feeds(200), MemoryPendingOpmlImports())
        assertEquals(200, report.imported.size)
        assertTrue("25 a minute stays under 30 a minute", rateLimited.isEmpty())
    }

    @Test
    fun aRateLimitedOutlineCostsOneRequestNotEverySpelling() = runBlocking {
        serverPerMinute = 0
        val budget = budget()
        val resolver = OpmlCatalogResolver(repository(budget), RssFeedClient()) { route -> budget.acquire(route).let { true } }

        assertEquals(OpmlCatalogDecision.Limited, resolver.resolve(OpmlFeed("Show 1", "https://feeds.example/1")))
        assertEquals("one GET /podcast, then the outline waits", 1, podcastCalls.size)
    }

    @Test
    fun aServerErrorDefersTheOutlineWithoutASecondTryInTheSameRun() = runBlocking {
        serverPerMinute = 30
        val feed = OpmlFeed("Show 1", "https://feeds.example/1")
        broken += feed.xmlUrl
        val pending = MemoryPendingOpmlImports()

        val report = run(listOf(feed), pending, forget = false)

        assertEquals(listOf(feed), report.pending)
        assertEquals("one GET /podcast; a 503 is not a rate limit, so no second round now", 1, podcastCalls.size)
    }

    @Test
    fun anOutlineWithoutATitleSendsNoSearch() = runBlocking {
        val budget = budget()
        val resolver = OpmlCatalogResolver(repository(budget), RssFeedClient()) { route -> budget.acquire(route).let { true } }

        val decision = resolver.resolve(OpmlFeed("", server.url("/dead/1").toString()))

        assertTrue("no /search for an empty title", searches.isEmpty())
        assertEquals("the catalog answered and the feed can't be read: settled, not kept pending", OpmlCatalogDecision.Unresolvable, decision)
    }

    @Test
    fun outlinesTheApiCantAnswerStayPendingAndALaterRunFinishesThem() = runBlocking {
        serverPerMinute = 30
        val feeds = feeds(40)
        broken += feeds.takeLast(5).map { it.xmlUrl }
        val pending = MemoryPendingOpmlImports()

        val first = import(feeds, pending)
        assertEquals(35, first.imported.size)
        assertEquals("the unanswered outlines are kept, not dropped", feeds.takeLast(5).toSet(), first.pending.toSet())
        assertEquals(feeds.takeLast(5).toSet(), pending.all().toSet())

        broken.clear()
        val later = run(pending.all(), pending, forget = false)
        assertEquals(feeds.takeLast(5).map { it.xmlUrl.substringAfterLast('/') }.toSet(), later.imported.map { it.id }.toSet())
        assertTrue(pending.all().isEmpty())
    }

    @Test
    fun anImportThatAddsNothingKeepsNothingPending() = runBlocking {
        val feeds = feeds(3)
        broken += feeds.map { it.xmlUrl }
        val pending = MemoryPendingOpmlImports()

        val report = import(feeds, pending)

        assertTrue(report.imported.isEmpty() && report.pending.isEmpty())
        assertTrue("the screen offers Try again instead", pending.all().isEmpty())
    }

    @Test
    fun anImportThatStopsPartwayLeavesTheRestPending() = runBlocking {
        val feeds = feeds(10)
        val pending = MemoryPendingOpmlImports()
        val budget = budget()
        val resolver = OpmlCatalogResolver(repository(budget), RssFeedClient()) { route -> budget.acquire(route).let { true } }
        var seen = 0
        runCatching {
            OpmlImportRun.run(feeds, pending, forgetWhenNothingImported = true, importOne = { feed ->
                if (++seen > 4) throw kotlinx.coroutines.CancellationException("the app closed")
                importOf(resolver, feed)
            })
        }
        assertEquals("every outline not yet settled is still saved", feeds.drop(4).toSet(), pending.all().toSet())
    }

    @Test
    fun aDeadFeedIsSkippedNotKeptPendingAndALaterRunSendsNothingForIt() = runBlocking {
        // The catalog has no show under any spelling, the title search finds none, and the feed can't be read.
        val gone = (1..2).map { OpmlFeed("Gone $it", server.url("/dead/$it").toString()) }
        val feeds = feeds(3) + gone
        val pending = MemoryPendingOpmlImports()

        val report = import(feeds, pending)

        assertEquals(3, report.imported.size)
        assertTrue("dead feeds are settled, not promised as still being added", report.pending.isEmpty())
        assertTrue(pending.all().isEmpty())
        val asked = podcastCalls.size
        run(pending.all(), pending, forget = false)
        assertEquals("nothing is asked again for them", asked, podcastCalls.size)
    }

    @Test
    fun aRateLimitedOutlineStillStaysPending() = runBlocking {
        serverPerMinute = 0
        val pending = MemoryPendingOpmlImports()
        val budget = budget()
        val resolver = OpmlCatalogResolver(repository(budget), RssFeedClient()) { route -> budget.tryAcquire(route) }
        val feed = OpmlFeed("Show 1", "https://feeds.example/1")

        val report = OpmlImportRun.run(listOf(feed), pending, forgetWhenNothingImported = false, importOne = { importOf(resolver, it) })

        assertEquals(listOf(feed), report.pending)
        assertEquals(listOf(feed), pending.all())
    }

    @Test
    fun theImportScreenStopsSendingAfterItsTimeAndLeavesTheRestPending() = runBlocking {
        serverPerMinute = 30
        val feeds = feeds(200)
        val pending = MemoryPendingOpmlImports()
        val budget = budget()
        val pace = OpmlScreenPace(budget, nowMs = { clock.get() })
        val resolver = OpmlCatalogResolver(repository(budget), RssFeedClient(), pace::pace)

        val report =
            OpmlImportRun.run(feeds, pending, forgetWhenNothingImported = true, importOne = { feed ->
                if (pace.expired()) OpmlFeedImport.Pending else importOf(resolver, feed)
            })

        assertTrue("the screen ends within its time", clock.get() <= OpmlScreenPace.SCREEN_BUDGET_MS)
        assertTrue(report.imported.isNotEmpty() && report.pending.isNotEmpty())
        assertEquals("every outline is followed or still pending", 200, report.imported.size + report.pending.size)
        assertEquals(report.pending.toSet(), pending.all().toSet())

        // The catch-up finishes them.
        val later = run(pending.all(), pending, forget = false)
        assertEquals(report.pending.size, later.imported.size)
        assertTrue(pending.all().isEmpty())
    }

    @Test
    fun savedMissesAreNotReadAsOutlines() {
        val prefs = context.getSharedPreferences("pending_opml_test", Context.MODE_PRIVATE)
        val pending = SharedPreferencesPendingOpmlImports(prefs)
        val feed = OpmlFeed("Show 1", "https://feeds.example/1")
        pending.add(listOf(feed))

        assertEquals(PendingOpmlMisses(1, 10L), pending.missed(feed, 10L))
        assertEquals(PendingOpmlMisses(2, 10L, 20L), pending.missed(feed, 20L))
        assertEquals("a later read finds the same misses", PendingOpmlMisses(2, 10L, 20L), pending.misses(feed))
        assertEquals(listOf(feed), pending.all())

        pending.remove(feed)
        assertTrue(pending.all().isEmpty())
        assertTrue("its misses go with it", prefs.all.isEmpty())
    }

    private suspend fun import(feeds: List<OpmlFeed>, pending: PendingOpmlImports) = run(feeds, pending, forget = true)

    private suspend fun run(feeds: List<OpmlFeed>, pending: PendingOpmlImports, forget: Boolean): OpmlImportReport {
        val budget = budget()
        val resolver = OpmlCatalogResolver(repository(budget), RssFeedClient()) { route -> budget.acquire(route).let { true } }
        return OpmlImportRun.run(feeds, pending, forgetWhenNothingImported = forget, importOne = { importOf(resolver, it) })
    }

    /** What [LibraryBackupManager] does with each decision, without the follow itself. */
    private suspend fun importOf(resolver: OpmlCatalogResolver, feed: OpmlFeed): OpmlFeedImport = when (val decision = resolver.resolve(feed)) {
        is OpmlCatalogDecision.Found -> OpmlFeedImport.Imported(decision.podcast)
        OpmlCatalogDecision.Deferred -> OpmlFeedImport.Pending
        OpmlCatalogDecision.Limited -> OpmlFeedImport.Limited
        OpmlCatalogDecision.ConfirmedAbsent, OpmlCatalogDecision.Unresolvable -> OpmlFeedImport.Skipped
    }

    private fun budget() = ApiRouteBudget(nowMs = { clock.get() }, sleep = { clock.addAndGet(it) })

    private fun feeds(count: Int) = (1..count).map { OpmlFeed("Show $it", "https://feeds.example/$it") }

    private fun repository(budget: ApiRouteBudget): PodcastRepository {
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        return PodcastRepository(
            baseUrl = base,
            publicKey = "test-app-key",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
            routeBudget = budget,
        )
    }
}
