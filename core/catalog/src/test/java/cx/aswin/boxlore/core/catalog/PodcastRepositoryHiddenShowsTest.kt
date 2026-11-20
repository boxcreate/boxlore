package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Hidden shows against the recorded `/v2` answers (`fixtures/v2`): every discovery read leaves them
 * out after its caches, recommendation requests name the hidden Podcast Index shows, Explore's paged
 * chart keeps whole pages, and a hidden-shows read that fails never empties discovery.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastRepositoryHiddenShowsTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private val seen = CopyOnWriteArrayList<Pair<String, String>>()
    private val answers = mutableMapOf<String, () -> MockResponse>()

    @Volatile
    private var hidden: Set<String> = emptySet()

    @Volatile
    private var hiddenFails = false

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(RecommendationsLastGood.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        PodcastRepository.recommendationsCache.clear()
        PodcastRepository.becauseYouLikeCache.clear()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seen += request.path.orEmpty() to request.body.readUtf8()
                    return answers[request.path.orEmpty().substringBefore('?')]?.invoke() ?: MockResponse().setResponseCode(404)
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        RssPodcastRepository.clearInstanceForTests()
        PodcastRepository.recommendationsCache.clear()
        PodcastRepository.becauseYouLikeCache.clear()
    }

    @Test
    fun everyDiscoveryReadLeavesHiddenShowsOutButShowSearchAndThePagedChartKeepThem() = runTest {
        val items = fixtureItems("fixtures/v2/recommendations.json")
        answers["/v2/trending"] = { fixture("fixtures/v2/trending.json") }
        answers["/v2/curated/vibe"] = { fixture("fixtures/v2/vibe.json") }
        answers["/v2/home/bootstrap"] = { fixture("fixtures/v2/bootstrap-get.json") }
        answers["/v2/search/semantic"] = { json("""{"status":"true","feeds":[{"id":75075,"title":"Reply All"},{"id":1,"title":"Kept"}],"items":$items}""") }
        answers["/v2/recommendations/because-you-like"] = { json("""{"status":"true","podcasts":[{"id":75075,"title":"Reply All"}],"episodes":$items}""") }
        answers["/v2/episodes/similar"] = { json("""{"status":"true","items":$items}""") }
        answers["/v2/recommendations"] = { fixture("fixtures/v2/recommendations.json") }
        hidden = setOf(TRENDING_SHOW, VIBE_SHOW, REC_SHOW, "75075")
        val repository = repository()

        assertEquals(listOf("743411", "550168"), repository.getTrendingPodcasts(country = "us", limit = 10).map { it.id })
        // Explore pages its chart by size and offset, so its pages stay whole and are filtered on screen.
        assertEquals(listOf(TRENDING_SHOW, "743411", "550168"), repository.getTrendingPodcastsOrNull(country = "us", limit = 10)!!.map { it.id })
        assertEquals(listOf("3709813", "728899"), repository.getCuratedPodcasts("morning_news", "us", listOf("en")).map { it.id })
        assertEquals(listOf("3709813", "728899"), repository.getCuratedVibes(listOf("morning_news"), "us").getValue("morning_news").map { it.id })
        assertEquals(listOf("743411", "550168"), repository.getHomeBootstrapDataFast("us").trending.map { it.id })

        val semantic = repository.searchSemanticGrouped("space", "us")
        assertEquals(listOf("1"), semantic.podcasts.map { it.id })
        assertTrue(semantic.episodes.isEmpty())
        val becauseYouLike = repository.getBecauseYouLikeRecommendations("The Daily", "News", "1", "us")
        assertTrue(becauseYouLike.podcasts.isEmpty())
        assertTrue(becauseYouLike.episodes.isEmpty())
        assertTrue(repository.getSimilarEpisodes(SimilarEpisodesQuery("1", "1", "Seed", "D", "P", country = "us")).isEmpty())
        assertTrue(repository.getPersonalizedRecommendations(history = emptyList(), interests = listOf("News"), country = "us").isEmpty())

        // Remembered answers are filtered too: unhiding brings them back without a request.
        val requests = seen.size
        hidden = emptySet()
        assertEquals(listOf(REC_SHOW, REC_SHOW, VIBE_SHOW), repository.getBecauseYouLikeRecommendations("The Daily", "News", "1", "us").episodes.map { it.podcastId })
        assertEquals(requests, seen.size)
    }

    @Test
    fun recommendationRequestsNameHiddenPodcastIndexShowsAndAHideAsksAgain() = runTest {
        answers["/v2/recommendations"] = { fixture("fixtures/v2/recommendations.json") }
        val repository = repository()

        val before = repository.getPersonalizedRecommendations(history = emptyList(), interests = listOf("News"), country = "us")
        assertEquals(listOf(REC_SHOW, REC_SHOW, VIBE_SHOW), before.map { it.podcastId })
        assertFalse("no hidden shows, no field", parse(seen.last().second).has("excludedPodcastIds"))

        hidden = setOf(REC_SHOW, "rss:https://feed.example/a", "0", "-5")
        val after = repository.getPersonalizedRecommendations(history = emptyList(), interests = listOf("News"), country = "us")

        assertEquals("a hidden show changes the request, so it is asked again", 2, seen.size)
        assertEquals(listOf(REC_SHOW.toLong()), parse(seen.last().second).getAsJsonArray("excludedPodcastIds").map { it.asLong })
        assertEquals(listOf(VIBE_SHOW), after.map { it.podcastId })
    }

    @Test
    fun homePicksForANewListenerNameHiddenShowsAndLeaveThemOut() = runTest {
        answers["/v2/home/bootstrap"] = { fixture("fixtures/v2/bootstrap-post.json") }
        hidden = setOf("6490758")

        val picks = repository().getHomeRecommendations(country = "us")

        assertEquals(listOf("7238952", "320115"), picks.items.map { it.podcastId })
        val recs = parse(seen.single().second).getAsJsonObject("recommendationsRequest")
        assertEquals(listOf(6490758L), recs.getAsJsonArray("excludedPodcastIds").map { it.asLong })
    }

    @Test
    fun hiddenShowIdsAreSentAtMostToTheRequestLimit() {
        val ids = (1..(MAX_RECOMMENDATION_IDS + 10)).map(Int::toString).toSet() + "rss:a"
        assertEquals(MAX_RECOMMENDATION_IDS, hiddenPodcastIndexIds(ids).size)
        assertEquals(emptyList<Long>(), hiddenPodcastIndexIds(setOf("rss:a", "itunes:5", "0")))
    }

    @Test
    fun aFailedHiddenShowsReadLeavesDiscoveryWhole() = runTest {
        answers["/v2/trending"] = { fixture("fixtures/v2/trending.json") }
        hiddenFails = true

        assertEquals(3, repository().getTrendingPodcasts(country = "us", limit = 10).size)
    }

    private fun repository(): PodcastRepository {
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        val database = mock(BoxLoreDatabase::class.java)
        `when`(database.podcastDao()).thenReturn(mock(PodcastDao::class.java))
        `when`(database.rssEpisodeDao()).thenReturn(mock(RssEpisodeDao::class.java))
        return PodcastRepository(
            baseUrl = base,
            publicKey = "test-app-key",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
            rolloutSwitches = RolloutSwitches(v2Discovery = { true }),
            hiddenShows = {
                if (hiddenFails) error("DataStore unreadable")
                hidden
            },
        )
    }

    private fun fixtureText(path: String): String = requireNotNull(javaClass.classLoader?.getResource(path)) { path }.readText()

    private fun fixture(path: String): MockResponse = json(fixtureText(path))

    private fun fixtureItems(path: String): String = parse(fixtureText(path)).getAsJsonArray("items").toString()

    private fun json(body: String): MockResponse = MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body)

    private fun parse(body: String): JsonObject = JsonParser.parseString(body).asJsonObject

    private companion object {
        const val TRENDING_SHOW = "743229"
        const val VIBE_SHOW = "55810"
        const val REC_SHOW = "190857"
    }
}
