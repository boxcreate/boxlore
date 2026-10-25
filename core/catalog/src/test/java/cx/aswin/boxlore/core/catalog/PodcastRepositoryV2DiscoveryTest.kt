package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.model.HistoryItem
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Discovery on the `/v2` routes against MockWebServer with answers in the Worker's v1 shapes
 * (fixtures under `fixtures/v2`): episode IDs go out as recipe IDs and come back as the phone's own
 * stored IDs, a failure keeps the last good answer and never falls back to v1, the v1 and v2 answers
 * never share a cache entry, and the `v2Discovery` switch sends everything back to v1.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastRepositoryV2DiscoveryTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private val seen = CopyOnWriteArrayList<Seen>()
    private val answers = mutableMapOf<String, () -> MockResponse>()

    @Volatile
    private var v2Discovery = true

    /** The phone's aliases: "7777" (an older build's Podcast Index ID) is the episode of recipe ID [RECIPE_A]. */
    private val ids =
        FakeDiscoveryIds(
            recipeIds = mapOf("7777" to RECIPE_A, "-55" to RECIPE_B, "-77" to RECIPE_C),
            owners = mapOf(DiscoveryEpisodeRef(SHOW, RECIPE_A) to "7777"),
        )

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
                    val path = request.path.orEmpty().substringBefore('?')
                    seen += Seen(request.method.orEmpty(), request.path.orEmpty(), request.body.readUtf8(), request.getHeader("X-App-Key"))
                    return answers[path]?.invoke() ?: MockResponse().setResponseCode(404)
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
    fun recommendationsSeedAndExcludeByRecipeIdsAndComeBackUnderLocalOwners() = runTest {
        answers["/v2/recommendations"] = { fixture("fixtures/v2/recommendations.json") }

        val picks =
            repository().getPersonalizedRecommendations(
                history = listeningHistory(),
                interests = listOf("History"),
                country = "us",
                subscribedPodcastIds = listOf(SHOW, "rss:abc"),
            )

        // The episode played under its Podcast Index ID comes back under that ID, not a second copy.
        assertEquals(listOf("7777", RECIPE_E.toString()), picks.map { it.id })
        val request = seen.single()
        assertEquals("/v2/recommendations", request.path)
        assertNull("v2 requests never carry the app key", request.appKey)
        val body = parse(request.body)
        assertEquals(2, body.get("contractVersion").asInt)
        val episodeSeeds = body.getAsJsonArray("seeds").map { it.asJsonObject }.filter { it.get("kind").asString == "episode" }
        // Recipe IDs for every ID the phone can place, including phone-minted and RSS-linked ones;
        // a Podcast Index ID it can't place still seeds through its text; "-66" has nothing to send.
        assertEquals(setOf(RECIPE_A, RECIPE_B, RECIPE_C, 5555L), episodeSeeds.map { it.get("id").asLong }.toSet())
        assertEquals(listOf(RECIPE_A, RECIPE_B, RECIPE_C), body.getAsJsonArray("excludedEpisodeIds").map { it.asLong })
        assertEquals(listOf(920666L), body.getAsJsonArray("subscribedPodcastIds").map { it.asLong })
    }

    @Test
    fun aFailedRecommendationRequestKeepsTheLastGoodPicksAcrossRestartsAndNeverAsksV1() = runTest {
        answers["/v2/recommendations"] = { fixture("fixtures/v2/recommendations.json") }
        repository().getPersonalizedRecommendations(history = listeningHistory(), country = "us")

        // A new process: nothing in memory, the server is down.
        PodcastRepository.recommendationsCache.clear()
        answers["/v2/recommendations"] = { MockResponse().setResponseCode(503) }
        val afterRestart = repository().getPersonalizedRecommendations(history = listeningHistory(), interests = listOf("News"), country = "us")
        assertEquals(listOf("7777", RECIPE_E.toString()), afterRestart.map { it.id })

        // An answer without picks (the server found no usable seed) is not a result either.
        answers["/v2/recommendations"] = { json("""{"status":"false","error":"No usable semantic seeds"}""", code = 422) }
        val noPicks = repository().getPersonalizedRecommendations(history = listeningHistory(), interests = listOf("Science"), country = "us")
        assertEquals(listOf("7777", RECIPE_E.toString()), noPicks.map { it.id })

        assertTrue(seen.none { it.path.startsWith("/recommendations") })
        // Another country has no last good picks: nothing rather than another market's picks.
        assertTrue(repository().getPersonalizedRecommendations(history = listeningHistory(), country = "in").isEmpty())
    }

    @Test
    fun unreadableLastGoodPicksAreIgnored() = runTest {
        context
            .getSharedPreferences(RecommendationsLastGood.FILE, Context.MODE_PRIVATE)
            .edit()
            .putString(RecommendationsLastGood.key(RecommendationsLastGood.Surface.PERSONAL, "us"), "not json")
            .commit()
        answers["/v2/recommendations"] = { MockResponse().setResponseCode(503) }

        assertTrue(repository().getPersonalizedRecommendations(history = listeningHistory(), country = "us").isEmpty())
    }

    @Test
    fun homePicksKeepTheLastGoodPicksAndTheirFallbackFlagWhenTheBootstrapFails() = runTest {
        answers["/v2/home/bootstrap"] = {
            json(
                """{"trending":[],"recommendations":[{"id":$RECIPE_A,"title":"Played","enclosureUrl":"https://cdn.example/a.mp3","feedId":920666}],""" +
                    """"isRecommendationsFallback":true}""",
            )
        }
        val first = repository().getHomeRecommendations(country = "us", history = listeningHistory())
        assertEquals(listOf("7777"), first.items.map { it.id })
        assertTrue(first.isFallback)
        val sent = parse(seen.single().body)
        assertEquals("POST", seen.single().method)
        assertEquals(2, sent.getAsJsonObject("recommendationsRequest").get("contractVersion").asInt)
        assertEquals(RECIPE_A, sent.getAsJsonObject("recommendationsRequest").getAsJsonArray("excludedEpisodeIds").first().asLong)

        // The Worker answers 200 with no picks when its recommendation part fails.
        answers["/v2/home/bootstrap"] = { json("""{"trending":[],"recommendations":[],"isRecommendationsFallback":false}""") }
        val failed = repository().getHomeRecommendations(country = "us", history = listeningHistory())
        assertEquals(listOf("7777"), failed.items.map { it.id })
        assertTrue(failed.isFallback)
        assertTrue(seen.none { it.path.startsWith("/home/bootstrap") })
    }

    @Test
    fun discoveryRoutesUseV2AndShowEpisodesUnderTheirLocalIds() = runTest {
        answers["/v2/trending"] = { fixture("fixtures/v2/trending.json") }
        answers["/v2/curated/vibe"] = { fixture("fixtures/v2/trending.json") }
        answers["/v2/home/bootstrap"] = { fixture("fixtures/v2/bootstrap-get.json") }
        answers["/v2/curated/curiosity-v3"] = { fixture("fixtures/v2/curiosity.json") }
        answers["/v2/search"] = { json("""{"status":"true","query":"daily","feeds":[{"id":920666,"title":"The Daily","url":"https://feeds.example/daily"}]}""") }
        answers["/v2/search/semantic"] = { json("""{"status":"true","feeds":[],"items":[$EPISODE_A_JSON,$EPISODE_E_JSON]}""") }
        answers["/v2/recommendations/because-you-like"] = { json("""{"status":"true","podcasts":[{"id":75075,"title":"Reply All"}],"episodes":[$EPISODE_A_JSON]}""") }
        answers["/v2/episodes/similar"] = { json("""{"status":"true","items":[$EPISODE_E_JSON,$EPISODE_A_JSON]}""") }
        answers["/v2/briefings/metadata/us"] = {
            json("""{"date":"2026-10-24","region":"us","title":"Brief","script":"S","audioUrl":"a","coverUrl":"c","chaptersUrl":"https://api.example/v2/briefings/chapters/us?d=2026-10-24"}""")
        }
        val repository = repository()

        val trending = repository.getTrendingPodcasts(country = "us", limit = 10)
        assertEquals("7777", trending.first().latestEpisode?.id)
        assertNull(trending.last().latestEpisode)
        assertEquals(listOf("7777"), repository.getCuratedPodcasts("calm", "in", listOf("en")).mapNotNull { it.latestEpisode?.id })
        assertEquals(mapOf("calm" to "7777"), repository.getCuratedVibes(listOf("calm"), "us").mapValues { it.value.first().latestEpisode?.id })
        assertEquals(listOf("920666"), repository.searchPodcastsWithCorrection("daily").podcasts.map { it.id })
        assertEquals(listOf("7777", RECIPE_E.toString()), repository.searchSemanticGrouped("space", "us").episodes.map { it.id })
        assertEquals(listOf("7777"), repository.getBecauseYouLikeRecommendations("The Daily", "News", SHOW, "us").episodes.map { it.id })
        val similar = repository.getSimilarEpisodes(SimilarEpisodesQuery("7777", SHOW, "T", "D", "P", country = "us"))
        assertEquals(listOf(RECIPE_E.toString(), "7777"), similar.map { it.id })
        assertEquals("https://api.example/v2/briefings/chapters/us?d=2026-10-24", repository.getBriefingMetadata("us")?.chaptersUrl)

        val curiosity = repository.getCuratedCuriosity(page = 1)!!
        assertEquals(7777L, curiosity.questionOfTheDay?.episode?.id)
        assertEquals(listOf(7777L, RECIPE_E), curiosity.questionsStack.map { it.episode.id })

        val bootstrap = repository.getHomeBootstrapDataFast("us")
        assertEquals("Morning Brief", bootstrap.briefing?.title)
        assertEquals(listOf("7777", RECIPE_E.toString()), bootstrap.briefingChapters.single().relatedEpisodes!!.map { it.id })
        assertEquals("7777", bootstrap.trending.single().latestEpisode?.id)

        assertTrue(seen.all { it.path.startsWith("/v2/") })
        assertTrue(seen.all { it.appKey == null })
        val similarBody = parse(seen.single { it.path == "/v2/episodes/similar" }.body)
        assertEquals(RECIPE_A.toString(), similarBody.get("id").asString)
        assertTrue(seen.any { it.path == "/v2/home/bootstrap?country=us" && it.method == "GET" })
    }

    @Test
    fun similarEpisodesSendZeroForAnEpisodeThePhoneCannotPlace() = runTest {
        answers["/v2/episodes/similar"] = { json("""{"status":"true","items":[]}""") }

        repository().getSimilarEpisodes(SimilarEpisodesQuery("-66", "rss:abc", "T", "D", "P"))

        val body = parse(seen.single().body)
        assertEquals("0", body.get("id").asString)
        assertEquals("0", body.get("podcastId").asString)
    }

    @Test
    fun typeaheadStaysOnV1WhileShowSearchMovesToV2() = runTest {
        answers["/search/typeahead"] = { json("""{"status":"true","feeds":[{"id":920666,"title":"The Daily"}]}""") }
        answers["/v2/search"] = { json("""{"status":"true","query":"daily","feeds":[{"id":75075,"title":"Reply All"}]}""") }

        val grouped = repository().searchPodcastsGrouped("daily")

        assertEquals(listOf("920666"), grouped.catalog.map { it.id })
        assertEquals(listOf("75075"), grouped.alsoFound.map { it.id })
        assertEquals(setOf("/search/typeahead", "/v2/search"), seen.map { it.path.substringBefore('?') }.toSet())
    }

    @Test
    fun aFailureKeepsTheLastGoodListAndAnEmptyPageStaysEmpty() = runTest {
        val repository = repository()
        answers["/v2/trending"] = { fixture("fixtures/v2/trending.json") }
        val good = repository.getTrendingPodcasts(country = "us", limit = 10)

        answers["/v2/trending"] = { MockResponse().setResponseCode(503) }
        assertEquals(good, repository.getTrendingPodcasts(country = "us", limit = 10))
        // A request that never succeeded has nothing to keep.
        assertTrue(repository.getTrendingPodcasts(country = "in", limit = 10).isEmpty())

        answers["/v2/trending"] = { json("""{"status":"true","feeds":[]}""") }
        assertTrue(repository.getTrendingPodcasts(country = "us", limit = 10, offset = 50).isEmpty())
        assertTrue(seen.none { it.path.startsWith("/trending") })
    }

    @Test
    fun theTrendingStreamReadsV2TranslatesAndKeepsTheLastGoodListOnFailure() = runTest {
        val repository = repository()
        answers["/v2/trending"] = { fixture("fixtures/v2/trending.json") }
        val streamed = repository.getTrendingPodcastsStream("us", 50, null, 0).toList().last()
        assertEquals(listOf("920666", "75075"), streamed.map { it.id })
        assertEquals("7777", streamed.first().latestEpisode?.id)

        answers["/v2/trending"] = { MockResponse().setResponseCode(503) }
        assertEquals(listOf(streamed), repository.getTrendingPodcastsStream("us", 50, null, 0).toList())
        assertTrue(seen.all { it.path.startsWith("/v2/trending") })
    }

    @Test
    fun theV2DiscoverySwitchSendsDiscoveryBackToV1() = runTest {
        v2Discovery = false
        val repository = repository()

        repository.getTrendingPodcasts(country = "us")
        repository.getTrendingPodcastsStream("us", 50, null, 0).toList()
        repository.getCuratedPodcasts("calm")
        repository.searchPodcastsWithCorrection("daily")
        repository.searchSemanticGrouped("space", "us")
        repository.getBecauseYouLikeRecommendations("The Daily", "News")
        repository.getSimilarEpisodes(SimilarEpisodesQuery("7777", SHOW, "T", "D", "P"))
        repository.getBriefingMetadata("us")
        repository.getHomeBootstrapDataFast("us")
        repository.getCuratedCuriosity(page = 1)
        repository.getPersonalizedRecommendations(history = listeningHistory(), country = "us")
        repository.getHomeRecommendations(country = "us", history = listeningHistory())

        assertTrue(seen.isNotEmpty())
        assertTrue(seen.map { it.path }.toString(), seen.none { it.path.startsWith("/v2/") })
        assertTrue(seen.any { it.path.startsWith("/recommendations") })
        assertTrue(seen.any { it.path.startsWith("/home/bootstrap") && it.method == "POST" })
    }

    @Test
    fun v1AndV2AnswersNeverShareACacheEntry() = runTest {
        answers["/recommendations/v2"] = { fixture("fixtures/v2/recommendations.json") }
        answers["/v2/recommendations"] = { fixture("fixtures/v2/recommendations.json") }
        answers["/recommendations/because-you-like"] = { json("""{"status":"true","episodes":[$EPISODE_A_JSON]}""") }
        answers["/v2/recommendations/because-you-like"] = { json("""{"status":"true","episodes":[$EPISODE_A_JSON]}""") }
        val repository = repository()
        val history = listOf(HistoryItem("The Daily", "Played", podcastId = SHOW, episodeId = "7777", isLiked = true))

        v2Discovery = false
        val v1 = repository.getPersonalizedRecommendations(history = history, country = "us")
        repository.getBecauseYouLikeRecommendations("The Daily", "News", SHOW, "us")
        v2Discovery = true
        val v2 = repository.getPersonalizedRecommendations(history = history, country = "us")
        repository.getBecauseYouLikeRecommendations("The Daily", "News", SHOW, "us")
        v2Discovery = false
        repository.getPersonalizedRecommendations(history = history, country = "us")
        repository.getBecauseYouLikeRecommendations("The Daily", "News", SHOW, "us")

        // v1 keeps the answer as the server named it; v2 shows it under the phone's own ID.
        assertEquals(RECIPE_A.toString(), v1.first().id)
        assertEquals("7777", v2.first().id)
        assertEquals(
            listOf("/recommendations/v2", "/recommendations/because-you-like", "/v2/recommendations", "/v2/recommendations/because-you-like"),
            seen.map { it.path },
        )
    }

    @Test
    fun withoutASeedOrAnInterestNoRecommendationRequestIsMade() = runTest {
        val history = listOf(HistoryItem("Show", "Unplaced", podcastId = SHOW, episodeId = "-66"))

        assertTrue(repository().getPersonalizedRecommendations(history = history, country = "us").isEmpty())
        assertFalse(seen.any())
    }

    private fun repository(): PodcastRepository {
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        val database = mock(BoxLoreDatabase::class.java)
        `when`(database.podcastDao()).thenReturn(mock(PodcastDao::class.java))
        `when`(database.rssEpisodeDao()).thenReturn(mock(RssEpisodeDao::class.java))
        return PodcastRepository(
            baseUrl = base,
            publicKey = APP_KEY,
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
            rolloutSwitches = RolloutSwitches(v2Discovery = { v2Discovery }),
            discoveryEpisodeIds = ids,
        )
    }

    private fun listeningHistory(): List<HistoryItem> = listOf(
        HistoryItem("The Daily", "Played on an older build", podcastId = SHOW, episodeId = "7777", isLiked = true),
        HistoryItem("The Daily", "Feed-only episode", podcastId = SHOW, episodeId = "-55", isCompleted = true),
        HistoryItem("The Daily", "Not placed yet", podcastId = SHOW, episodeId = "-66"),
        HistoryItem("The Daily (feed)", "Linked RSS episode", podcastId = "rss:abc", episodeId = "-77"),
        HistoryItem("Reply All", "Other show", podcastId = "75075", episodeId = "5555"),
    )

    private fun fixture(path: String): MockResponse = json(requireNotNull(javaClass.classLoader?.getResource(path)) { path }.readText())

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun parse(body: String): JsonObject = JsonParser.parseString(body).asJsonObject

    private data class Seen(val method: String, val path: String, val body: String, val appKey: String?,)

    private class FakeDiscoveryIds(
        private val recipeIds: Map<String, Long>,
        private val owners: Map<DiscoveryEpisodeRef, String>,
    ) : DiscoveryEpisodeIds {
        override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> =
            DiscoveryEpisodeIds.ownRecipeIds(episodeIds) + episodeIds.mapNotNull { id -> recipeIds[id]?.let { id to it } }

        override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, String> =
            refs.mapNotNull { ref -> owners[ref]?.let { ref to it } }.toMap()
    }

    private companion object {
        const val APP_KEY = "test-app-key"
        const val SHOW = "920666"
        const val RECIPE_A = 4_503_599_627_370_497L
        const val RECIPE_B = 4_503_599_627_370_498L
        const val RECIPE_C = 4_503_599_627_370_499L
        const val RECIPE_E = 4_503_599_627_370_501L
        const val EPISODE_A_JSON =
            """{"id":4503599627370497,"title":"The episode you already played","enclosureUrl":"https://cdn.example/a.mp3","feedId":920666,"feedTitle":"The Daily"}"""
        const val EPISODE_E_JSON =
            """{"id":4503599627370501,"title":"A new episode","enclosureUrl":"https://cdn.example/e.mp3","feedId":75075,"feedTitle":"Reply All"}"""
    }
}
