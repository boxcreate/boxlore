package cx.aswin.boxlore.core.catalog

import android.content.Context
import android.content.SharedPreferences
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Hermetic [PodcastRepository] catalog paths against MockWebServer (B2).
 *
 * Uses Mockito doubles for [Context] / Room (no Robolectric) so OkHttp MockWebServer
 * stays on the plain JVM classpath. Unit-test resolution pins OkHttp 4.12 to match
 * MockWebServer 4.x (rssparser otherwise upgrades production deps to OkHttp 5).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastRepositoryCatalogTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: PodcastRepository
    private lateinit var podcastDao: PodcastDao
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()

        val context = fakeContext()
        val database = fakeDatabase()
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        val api = NetworkModule.createBoxLoreApi(server.url("/").toString(), client)
        val rss =
            RssPodcastRepository.createForTests(
                context = context,
                database = database,
            )

        repository =
            PodcastRepository(
                baseUrl = server.url("/").toString(),
                publicKey = APP_KEY,
                context = context,
                rssRepository = rss,
                ioDispatcher = testDispatcher,
                boxLoreApi = api,
            )
    }

    @AfterEach
    fun tearDown() {
        if (::server.isInitialized) {
            server.shutdown()
        }
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun `RSS lookup failure preserves a sibling RSS tip and the PI sync result`() = runTest(testDispatcher) {
        val tip = Episode("-99", "RSS", "", "https://audio.example/rss.mp3", duration = 10, podcastId = "rss:good")
        `when`(podcastDao.getPodcast("rss:broken")).thenThrow(IllegalStateException("broken lookup"))
        `when`(podcastDao.getPodcast("rss:good")).thenReturn(PodcastEntity("rss:good", "RSS", "", "", null, sourceType = PodcastEntity.SOURCE_RSS, latestEpisode = tip))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"123","latestEpisode":{"id":321,"title":"PI","enclosureUrl":"https://audio.example/pi.mp3"}}]}"""))
        val tips = repository.syncSubscriptions(listOf("rss:broken", "rss:good", "123"))
        assertEquals(setOf("rss:good", "123"), tips.keys)
        assertEquals("-99", tips["rss:good"]!!.id)
        assertEquals("321", tips["123"]!!.id)
        assertTrue(server.takeRequest().path!!.startsWith("/sync"))
    }

    @Test fun `slow RSS lookup does not delay the PI request`() = runTest(testDispatcher) {
        val piStarted = CountDownLatch(1)
        val overlapped = AtomicBoolean(false)
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                piStarted.countDown()
                return MockResponse().setBody("""{"items":[]}""")
            }
        }
        `when`(podcastDao.getPodcast("rss:slow")).thenAnswer {
            overlapped.set(piStarted.await(3, TimeUnit.SECONDS))
            null
        }
        repository.syncSubscriptions(listOf("rss:slow", "123"))
        assertTrue(overlapped.get())
    }

    @Test fun `RSS lookup cancellation propagates from subscription sync`() = runTest(testDispatcher) {
        `when`(podcastDao.getPodcast("rss:cancelled")).thenThrow(CancellationException("cancelled"))
        try {
            repository.syncSubscriptions(listOf("rss:cancelled"))
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(0, server.requestCount)
        }
    }

    @Test
    fun `getTrendingPodcasts maps feeds from MockWebServer`() = runTest(testDispatcher) {
        enqueueFixture("fixtures/trending.json")

        val podcasts = repository.getTrendingPodcasts(country = "us", limit = 10, category = "News")

        assertEquals(1, podcasts.size)
        assertEquals("920666", podcasts.single().id)
        assertEquals("The Daily", podcasts.single().title)
        assertEquals("The New York Times", podcasts.single().artist)

        val recorded = server.takeRequest()
        assertTrue(recorded.path!!.startsWith("/trending"))
        assertEquals("us", recorded.requestUrl?.queryParameter("country"))
        assertEquals(APP_KEY, recorded.getHeader("X-App-Key"))
    }

    @Test
    fun `getTrendingPodcasts returns empty list on HTTP error`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse()
                .setResponseCode(500)
                .setBody("""{"status":"false","error":"boom"}"""),
        )

        val podcasts = repository.getTrendingPodcasts(country = "us")

        assertTrue(podcasts.isEmpty())
    }

    @Test
    fun `getHomeBootstrapDataFast maps trending and briefing`() = runTest(testDispatcher) {
        enqueueFixture("fixtures/bootstrap.json")

        val data = repository.getHomeBootstrapDataFast(country = "us")

        assertEquals("Morning Brief", data.briefing?.title)
        assertEquals(1, data.trending.size)
        assertEquals("Trending Show", data.trending.single().title)
        assertEquals(false, data.isRecommendationsFallback)

        val recorded = server.takeRequest()
        assertEquals("GET", recorded.method)
        assertTrue(recorded.path!!.startsWith("/home/bootstrap"))
        assertEquals("us", recorded.requestUrl?.queryParameter("country"))
    }

    @Test
    fun `getHomeBootstrapDataFast returns empty shell on transport failure`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("""{"error":"unavailable"}"""),
        )

        val data = repository.getHomeBootstrapDataFast(country = "us")

        assertEquals(null, data.briefing)
        assertTrue(data.trending.isEmpty())
        assertTrue(data.recommendations.isEmpty())
    }

    @Test
    fun `searchPodcastsGrouped merges typeahead catalog and hybrid also-found`() = runTest(testDispatcher) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/search/typeahead") ->
                            fixtureBody("fixtures/search-typeahead.json")
                        path.startsWith("/search") ->
                            fixtureBody("fixtures/search.json")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }

        val grouped = repository.searchPodcastsGrouped("serial")

        assertEquals(listOf("745392"), grouped.catalog.map { it.id })
        assertEquals(listOf("75075"), grouped.alsoFound.map { it.id })
        assertEquals("Serial", grouped.catalog.single().title)
        assertEquals("Reply All", grouped.alsoFound.single().title)
    }

    @Test
    fun `searchPodcastsGrouped recovers a real genre when typeahead returns only Podcast`() = runTest(testDispatcher) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val feeds = if (request.path.orEmpty().startsWith("/search/typeahead")) {
                    """[{"id":1330097,"title":"Catalog title","categories":{"0":"Podcast"}}]"""
                } else {
                    """[{"id":1330097,"title":"Hybrid title","categories":{"20":"Education","29":"Health"}},{"id":200,"title":"Other show","categories":{"27":"Technology"}}]"""
                }
                return MockResponse().setHeader("Content-Type", "application/json").setBody("""{"status":"true","feeds":$feeds}""")
            }
        }
        val grouped = repository.searchPodcastsGrouped("show")
        assertEquals(listOf("1330097"), grouped.catalog.map { it.id })
        assertEquals("Catalog title", grouped.catalog.single().title)
        assertEquals("Health", grouped.catalog.single().genre)
        assertEquals(listOf("200"), grouped.alsoFound.map { it.id })
        assertEquals("Technology", grouped.alsoFound.single().genre)
    }

    @Test
    fun `searchPodcastsGrouped returns empty groups for blank query`() = runTest(testDispatcher) {
        val grouped = repository.searchPodcastsGrouped("   ")
        assertTrue(grouped.catalog.isEmpty())
        assertTrue(grouped.alsoFound.isEmpty())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `searchPodcastsGrouped keeps hybrid when typeahead fails`() = runTest(testDispatcher) {
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty()
                    return when {
                        path.startsWith("/search/typeahead") ->
                            MockResponse()
                                .setResponseCode(500)
                                .setBody("""{"status":"false"}""")
                        path.startsWith("/search") ->
                            fixtureBody("fixtures/search.json")
                        else -> MockResponse().setResponseCode(404)
                    }
                }
            }

        val grouped = repository.searchPodcastsGrouped("reply")

        assertTrue(grouped.catalog.isEmpty())
        assertEquals(listOf("75075"), grouped.alsoFound.map { it.id })
    }

    @Test
    fun `getEpisodesPaginated clamps runaway limit to MAX_SAFE_PAGE_LIMIT`() = runTest(testDispatcher) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""{"status":"true","items":[],"count":0,"total":0,"hasMore":false}"""),
        )

        repository.getEpisodesPaginated("90001", limit = 1000)

        val recorded = server.takeRequest()
        assertTrue(
            recorded.path?.contains("limit=100") == true,
            "Expected request path to have limit=100, but got: ${recorded.path}",
        )
    }

    private fun fixtureBody(resourcePath: String): MockResponse {
        val json =
            requireNotNull(javaClass.classLoader?.getResource(resourcePath)) {
                "Missing test fixture: $resourcePath"
            }.readText()
        return MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "application/json")
            .setBody(json)
    }

    private fun enqueueFixture(resourcePath: String) {
        val json =
            requireNotNull(javaClass.classLoader?.getResource(resourcePath)) {
                "Missing test fixture: $resourcePath"
            }.readText()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(json),
        )
    }

    private fun fakeContext(): Context {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenReturn(null)
        `when`(prefs.getAll()).thenReturn(emptyMap())
        `when`(prefs.contains(anyString())).thenReturn(false)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)
        `when`(editor.apply()).then { }

        val appContext = mock(Context::class.java)
        `when`(appContext.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)

        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(appContext)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context
    }

    private fun fakeDatabase(): BoxLoreDatabase {
        val database = mock(BoxLoreDatabase::class.java)
        podcastDao = mock(PodcastDao::class.java)
        `when`(database.podcastDao()).thenReturn(podcastDao)
        `when`(database.rssEpisodeDao()).thenReturn(mock(RssEpisodeDao::class.java))
        return database
    }

    companion object {
        private const val APP_KEY = "test-app-key"
    }
}
