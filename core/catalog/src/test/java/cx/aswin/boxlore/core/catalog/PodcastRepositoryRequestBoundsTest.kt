package cx.aswin.boxlore.core.catalog

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/**
 * Request sizes the API caps: `/sync` serves only the first 40 ids of a call, so whole-library
 * syncs (Smart Downloads, backup restore) must chunk; recommendation interests stop at 12.
 */
class PodcastRepositoryRequestBoundsTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: PodcastRepository
    private val syncBodies = CopyOnWriteArrayList<List<String>>()
    private val inFlight = AtomicInteger()
    private val maxInFlight = AtomicInteger()
    private val bodiesByPath = CopyOnWriteArrayList<Pair<String, String>>()

    /** The v1 cases pin the v1 discovery routes; the `/v2` cases switch it on. */
    @Volatile
    private var v2Discovery = false

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                val body = request.body.readUtf8()
                bodiesByPath += path to body
                return when (path) {
                    "/sync" -> syncResponse(body)
                    "/recommendations/v2" -> MockResponse().setResponseCode(500)
                    else -> MockResponse().setBody("""{"items":[]}""")
                }
            }
        }
        server.start()
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        repository = PodcastRepository(
            baseUrl = server.url("/").toString(),
            publicKey = "test-app-key",
            context = fakeContext(),
            rssRepository = RssPodcastRepository.createForTests(context = fakeContext(), database = fakeDatabase()),
            boxLoreApi = NetworkModule.createBoxLoreApi(server.url("/").toString(), client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(server.url("/").toString(), client),
            rolloutSwitches = RolloutSwitches(v2Discovery = { v2Discovery }),
        )
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
        RssPodcastRepository.clearInstanceForTests()
    }

    /** Behaves like the Worker: at most 40 ids are served per call, the rest are dropped. */
    private fun syncResponse(body: String): MockResponse {
        val running = inFlight.incrementAndGet()
        maxInFlight.accumulateAndGet(running) { a, b -> maxOf(a, b) }
        try {
            Thread.sleep(40)
            val ids = JsonParser.parseString(body).asJsonObject.getAsJsonArray("ids").map { it.asString }
            syncBodies += ids
            val items = ids.take(40).joinToString(",") { id ->
                """{"id":"$id","latestEpisode":{"id":${id.toLong() + 5_000},"title":"Latest of $id","description":"d","enclosureUrl":"https://cdn.example/$id.mp3","datePublished":100}}"""
            }
            return MockResponse().setBody("""{"items":[$items]}""")
        } finally {
            inFlight.decrementAndGet()
        }
    }

    @Test
    fun `a 60-show library syncs every show in chunks of 20`() = runTest {
        val ids = (1..60).map { (900_000 + it).toString() }

        val tips = repository.syncSubscriptions(ids)

        assertEquals(ids.toSet(), tips.keys, "every subscription gets its latest episode")
        assertEquals(3, syncBodies.size)
        assertTrue(syncBodies.all { it.size <= PI_SYNC_CHUNK_SIZE }, "chunks: ${syncBodies.map { it.size }}")
        assertEquals(ids.toSet(), syncBodies.flatten().toSet())
        assertTrue(maxInFlight.get() <= PI_SYNC_PARALLELISM, "at most $PI_SYNC_PARALLELISM calls at once, saw ${maxInFlight.get()}")
    }

    @Test
    fun `a failed chunk only drops its own shows`() = runTest {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                return if (body.contains("\"900001\"")) MockResponse().setResponseCode(503) else syncResponse(body)
            }
        }
        val ids = (1..25).map { (900_000 + it).toString() }

        val tips = repository.syncSubscriptions(ids)

        assertEquals(ids.drop(PI_SYNC_CHUNK_SIZE).toSet(), tips.keys)
    }

    @Test
    fun `legacy recommendations send at most 12 interests`() = runTest {
        val interests = (1..20).map { "Interest $it" } + listOf(" Interest 1 ", "", "  ")

        repository.getPersonalizedRecommendations(history = emptyList(), interests = interests, country = "us")

        val legacy = parse(bodiesByPath.first { it.first == "/recommendations" }.second)
        assertEquals((1..12).map { "Interest $it" }, legacy.interests())
        val v2 = parse(bodiesByPath.first { it.first == "/recommendations/v2" }.second)
        assertEquals(12, v2.interests().size)
        // The API refuses this body without its contract version, which defaults would leave out.
        assertEquals(2, v2.get("contractVersion").asInt)
    }

    @Test
    fun `interests are trimmed, cleaned and deduplicated before the 12-item limit`() = runTest {
        val interests = listOf(" Topic A ", "", "Topic A", "   ", " Topic B") + (1..20).map { "Interest $it" }

        repository.getPersonalizedRecommendations(history = emptyList(), interests = interests, country = "us")

        val expected = listOf("Topic A", "Topic B") + (1..10).map { "Interest $it" }
        assertEquals(expected, parse(bodiesByPath.first { it.first == "/recommendations" }.second).interests())
        assertEquals(expected, parse(bodiesByPath.first { it.first == "/recommendations/v2" }.second).interests())
    }

    @Test
    fun `blank-only interests with no history or subscriptions make no request`() = runTest {
        val results = repository.getPersonalizedRecommendations(history = emptyList(), interests = listOf("", "   "), country = "us")

        assertTrue(results.isEmpty())
        assertTrue(bodiesByPath.none { it.first.startsWith("/recommendations") })
    }

    @Test
    fun `Home recommendations send at most 12 interests in the bootstrap request`() = runTest {
        repository.getHomeRecommendations(country = "us", interests = (1..30).map { "Topic $it" })

        val bootstrap = parse(bodiesByPath.first { it.first == "/home/bootstrap" }.second)
        assertEquals((1..12).map { "Topic $it" }, bootstrap.getAsJsonObject("recommendationsRequest").interests())
    }

    @Test
    fun `v2 recommendations send the contract version and at most 12 interests, never the v1 routes`() = runTest {
        v2Discovery = true
        val interests = (1..20).map { "Interest $it" }

        repository.getPersonalizedRecommendations(history = emptyList(), interests = interests, country = "us")

        val v2 = parse(bodiesByPath.single { it.first == "/v2/recommendations" }.second)
        assertEquals(2, v2.get("contractVersion").asInt)
        assertEquals((1..12).map { "Interest $it" }, v2.interests())
        assertTrue(bodiesByPath.none { it.first == "/recommendations" || it.first == "/recommendations/v2" })
    }

    @Test
    fun `v2 Home recommendations send the current recommendation body in the bootstrap request`() = runTest {
        v2Discovery = true

        repository.getHomeRecommendations(country = "us", interests = (1..30).map { "Topic $it" })

        val bootstrap = parse(bodiesByPath.single { it.first == "/v2/home/bootstrap" }.second)
        val recommendations = bootstrap.getAsJsonObject("recommendationsRequest")
        assertEquals(2, recommendations.get("contractVersion").asInt)
        assertEquals((1..12).map { "Topic $it" }, recommendations.interests())
        assertEquals(listOf("recommendations"), bootstrap.getAsJsonArray("include").map { it.asString })
    }

    @Test
    fun `v2 Home recommendations without a seed or an interest make no request`() = runTest {
        v2Discovery = true

        val picks = repository.getHomeRecommendations(country = "us")

        assertTrue(picks.items.isEmpty())
        assertTrue(bodiesByPath.isEmpty())
    }

    private fun parse(body: String): JsonObject = JsonParser.parseString(body).asJsonObject

    private fun JsonObject.interests() = getAsJsonArray("interests").map { it.asString }

    private fun fakeContext(): Context {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenReturn(null)
        `when`(prefs.getAll()).thenReturn(emptyMap())
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        val appContext = mock(Context::class.java)
        `when`(appContext.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(appContext)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context
    }

    private fun fakeDatabase(): BoxLoreDatabase {
        val database = mock(BoxLoreDatabase::class.java)
        `when`(database.podcastDao()).thenReturn(mock(PodcastDao::class.java))
        `when`(database.rssEpisodeDao()).thenReturn(mock(RssEpisodeDao::class.java))
        return database
    }
}
