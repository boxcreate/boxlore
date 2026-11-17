package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.io.IOException
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A server-list page that fails partway through a long show (a 429 or a 5xx on page 3): it is a
 * failure, never the end of the list; range selection's strict read throws instead of acting on part
 * of the show; and one list never mixes `/v2/episodes` recipe IDs with v1 served IDs.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerListPageFailureTest {
    private lateinit var server: MockWebServer
    private lateinit var database: BoxLoreDatabase
    private lateinit var context: Context
    private val seen = CopyOnWriteArrayList<String>()
    private val clock = AtomicLong(0L)

    /** `/v2/episodes` answers by offset; a missing offset answers 429. */
    private val v2 = mutableMapOf<Int, () -> MockResponse>()
    private var v2Down = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        PodcastRepository.episodesCache.clear()
        serverListSchemes.clear()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    seen += path
                    val offset = request.requestUrl?.queryParameter("offset")?.toInt() ?: 0
                    return when (path.substringBefore('?')) {
                        "/v2/episodes" -> if (v2Down) MockResponse().setResponseCode(503) else v2[offset]?.invoke() ?: tooMany()
                        "/episodes" -> json(v1Page(offset))
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }
        server.start()
        (0..1).forEach { page -> v2[page * PAGE] = { json(v2Page(page * PAGE, hasMore = true)) } }
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
        RssPodcastRepository.clearInstanceForTests()
        PodcastRepository.episodesCache.clear()
        serverListSchemes.clear()
    }

    @Test
    fun aRateLimitedPage3IsAFailureNotTheEndOfTheListAndNeverReadsV1() = runBlocking {
        val repository = repository()
        assertEquals(PAGE, repository.getEpisodesPaginated(SHOW, PAGE, 0).episodes.size)
        assertEquals(PAGE, repository.getEpisodesPaginated(SHOW, PAGE, PAGE).episodes.size)

        val third = repository.getEpisodesPaginated(SHOW, PAGE, 2 * PAGE)

        assertTrue("the page failed: the list does not end here", third.failed)
        assertTrue(third.episodes.isEmpty())
        assertTrue("no v1 page is mixed in", seen.none { it.startsWith("/episodes") })
    }

    @Test
    fun aServerErrorOnPage3IsAFailureToo() = runBlocking {
        val repository = repository()
        repository.getEpisodesPaginated(SHOW, PAGE, 0)
        v2[2 * PAGE] = { MockResponse().setResponseCode(502) }
        assertTrue(repository.getEpisodesPaginated(SHOW, PAGE, 2 * PAGE).failed)
        assertTrue(seen.none { it.startsWith("/episodes") })
    }

    @Test
    fun theStrictSelectionReadThrowsOnA429OnPage3InsteadOfEndingThere() = runBlocking {
        val repository = repository()
        repository.getEpisodesPaginated(SHOW, PAGE, 0)

        try {
            repository.getEpisodesPaginated(SHOW, PAGE, 2 * PAGE, strict = true)
            fail("a strict read must not come back short")
        } catch (expected: IOException) {
            // The selection keeps what it had and says it couldn't load every episode.
        }
        assertEquals("the read waited out Retry-After once, then gave up", 2, seen.count { it.contains("offset=${2 * PAGE}") })
        assertTrue("it waited for the route", clock.get() >= 60_000L)
        assertTrue(seen.none { it.startsWith("/episodes") })
    }

    @Test
    fun theStrictReadWaitsOutA429AndCarriesOn() = runBlocking {
        val repository = repository()
        repository.getEpisodesPaginated(SHOW, PAGE, 0)
        var asked = 0
        v2[2 * PAGE] = { if (asked++ == 0) tooMany() else json(v2Page(2 * PAGE, hasMore = false)) }

        val page = repository.getEpisodesPaginated(SHOW, PAGE, 2 * PAGE, strict = true)

        assertFalse(page.failed)
        assertEquals(PAGE, page.episodes.size)
        assertTrue(page.episodes.all { it.id.toLong() == recipe(it.title) })
    }

    @Test
    fun aListThatStartedOnV1KeepsReadingV1() = runBlocking {
        val repository = repository()
        v2Down = true
        val first = repository.getEpisodesPaginated(SHOW, PAGE, 0)
        assertEquals("v1 served the first page", "1", first.episodes.first().id)
        assertEquals(ServerListScheme.V1, first.scheme)

        v2Down = false
        val second = repository.getEpisodesPaginated(SHOW, PAGE, PAGE, scheme = first.scheme)

        assertEquals("v1 IDs again, not recipe IDs", (PAGE + 1).toString(), second.episodes.first().id)
        assertTrue("no /v2/episodes page after the first", seen.filter { it.startsWith("/v2/episodes") }.all { it.contains("offset=0") })
    }

    @Test
    fun aListFixedToV2FailsRatherThanReadV1() = runBlocking {
        val repository = repository()
        val first = repository.getEpisodesPaginated(SHOW, PAGE, 0)
        assertEquals(ServerListScheme.V2, first.scheme)
        v2Down = true

        assertTrue(repository.getEpisodesPaginated(SHOW, PAGE, PAGE, scheme = first.scheme).failed)
        assertTrue(seen.none { it.startsWith("/episodes") })
    }

    @Test
    fun anotherScreensFirstPageFallingBackToV1DoesNotSwitchTheShowPagesList() = runBlocking {
        val repository = repository()
        val shown = repository.getShowPageEpisodes(SHOW, PAGE, 0, "newest")
        assertEquals(ServerListScheme.V2, shown.scheme)

        // Episode details reads the show's newest 15 while /v2/episodes answers 429: it falls back to v1.
        v2[0] = { tooMany() }
        val elsewhere = repository.getEpisodesPaginated(SHOW, 15, 0, "newest")
        assertEquals(ServerListScheme.V1, elsewhere.scheme)

        val next = repository.getShowPageEpisodes(SHOW, PAGE, PAGE, "newest")
        assertEquals("the show page's next page stays on /v2", ServerListScheme.V2, next.scheme)
        assertTrue(next.episodes.all { it.id.toLong() == recipe(it.title) })
        v2[0] = { json(v2Page(0, hasMore = true)) }
        val selection = repository.getShowPageEpisodes(SHOW, PAGE, 0, "oldest", strict = true)
        assertEquals("Select all reads the list's own route, whatever its order", ServerListScheme.V2, selection.scheme)
        assertEquals("only the other screen's read used v1", 1, seen.count { it.startsWith("/episodes") })
    }

    @Test
    fun aSecondShowPageForTheSameShowFallingBackToV1DoesNotSwitchTheFirstPagesList() = runBlocking {
        val repository = repository()
        val first = ShowListSchemes()
        val second = ShowListSchemes()
        assertEquals(ServerListScheme.V2, repository.getShowPageEpisodes(SHOW, PAGE, 0, "newest", list = first).scheme)

        // The listener opens the same show again from an episode: that page's first page meets a 429 and falls back to v1.
        PodcastRepository.episodesCache.clear()
        v2[0] = { tooMany() }
        assertEquals(ServerListScheme.V1, repository.getShowPageEpisodes(SHOW, PAGE, 0, "newest", list = second).scheme)

        // Back on the first page, its next page still reads /v2: no v1 served IDs among its recipe IDs.
        val next = repository.getShowPageEpisodes(SHOW, PAGE, PAGE, "newest", list = first)
        assertEquals(ServerListScheme.V2, next.scheme)
        assertTrue(next.episodes.all { it.id.toLong() == recipe(it.title) })
        assertEquals("the second page's list keeps v1", ServerListScheme.V1, repository.getShowPageEpisodes(SHOW, PAGE, PAGE, "newest", list = second).scheme)
        assertEquals("only the second page's reads used v1", 2, seen.count { it.startsWith("/episodes") })
    }

    @Test
    fun anotherScreensV2FirstPageDoesNotSwitchAShowPageListThatStartedOnV1() = runBlocking {
        val repository = repository()
        v2Down = true
        val shown = repository.getShowPageEpisodes(SHOW, PAGE, 0, "newest")
        assertEquals(ServerListScheme.V1, shown.scheme)

        v2Down = false
        assertEquals(ServerListScheme.V2, repository.getEpisodesPaginated(SHOW, 15, 0, "newest").scheme)

        val next = repository.getShowPageEpisodes(SHOW, PAGE, PAGE, "newest")
        assertEquals("v1 IDs again", (PAGE + 1).toString(), next.episodes.first().id)
        assertEquals(ServerListScheme.V1, repository.getShowPageEpisodes(SHOW, PAGE, PAGE, "newest", strict = true).scheme)
    }

    @Test
    fun aWholeShowReadFollowsAShowPageListThatStartedOnV1() = runBlocking {
        val repository = repository()
        val list = ShowListSchemes()
        v2Down = true
        assertEquals(ServerListScheme.V1, repository.getShowPageEpisodes(SHOW, PAGE, 0, "newest", list = list).scheme)

        // /v2/episodes is back, but Select all and Mark all read the list on screen: v1 served IDs only.
        v2Down = false
        val v2Before = seen.count { it.startsWith("/v2/episodes") }
        val whole = repository.getWholeShowPage(SHOW, 0, "newest", list = list)
        assertTrue(whole.episodes.isNotEmpty())
        assertTrue("no recipe IDs mixed into a v1 list", whole.episodes.none { it.id.toLong() == recipe(it.title) })
        assertEquals("no /v2/episodes read for the whole show", v2Before, seen.count { it.startsWith("/v2/episodes") })
    }

    private fun repository(): PodcastRepository {
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        return PodcastRepository(
            baseUrl = base,
            publicKey = "test-app-key",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
            rolloutSwitches = RolloutSwitches(feedFirst = { false }),
            routeBudget = ApiRouteBudget(nowMs = { clock.get() }, sleep = { clock.addAndGet(it) }),
        )
    }

    private companion object {
        const val SHOW = "920666"
        const val PAGE = 100

        fun recipe(key: String): Long = EpisodeRecipeId.forNamespace(SHOW.toLong(), key)

        fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

        fun tooMany() = MockResponse().setResponseCode(429).setHeader("Retry-After", "60").setBody("""{"error":"rate_limited"}""")

        fun v2Page(offset: Int, hasMore: Boolean): String {
            val items =
                (offset until offset + PAGE).joinToString(",") { index ->
                    val key = "g$index"
                    """{"id":${recipe(key)},"title":"$key","guid":"$key","catalogKey":"$key","enclosureUrl":"https://cdn.example/$index.mp3",""" +
                        """"feedId":$SHOW,"datePublished":${1_790_000_000L - index * 3_600L}}"""
                }
            return """{"status":"true","feedId":$SHOW,"items":[$items],"hasMore":$hasMore,"offset":$offset,"limit":$PAGE}"""
        }

        fun v1Page(offset: Int): String {
            val items =
                (offset until offset + PAGE).joinToString(",") { index ->
                    """{"id":${index + 1},"title":"v1 $index","enclosureUrl":"https://cdn.example/v1-$index.mp3","feedId":$SHOW,"datePublished":${1_790_000_000L - index * 3_600L}}"""
                }
            return """{"items":[$items],"hasMore":true}"""
        }
    }
}
