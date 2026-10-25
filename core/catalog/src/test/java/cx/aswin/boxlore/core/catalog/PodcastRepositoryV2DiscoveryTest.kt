package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
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
 * Discovery on the `/v2` routes against MockWebServer serving answers recorded from the live routes
 * (`fixtures/v2`, see its README): episode IDs go out as recipe IDs and come back as the phone's own
 * stored IDs, a failure keeps the last good answer and never falls back to v1, the v1 and v2 answers
 * never share a cache entry, and the `v2Discovery` switch sends everything back to v1 as shipped.
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

    /**
     * The phone's aliases. "7777" (an older build's Podcast Index ID) is the first recorded pick; the
     * second pick's episode sits in the queue under "9001"; the other entries place one episode of
     * each recorded route.
     */
    private val ids =
        FakeDiscoveryIds(
            recipeIds = mapOf("7777" to REC_A, "-55" to RECIPE_B, "-77" to RECIPE_C),
            owners =
            mapOf(
                (REC_SHOW to REC_A) to DiscoveryEpisodeOwner("7777", REC_SHOW),
                (REC_C_SHOW to REC_C) to DiscoveryEpisodeOwner("9001", REC_C_SHOW),
                (TRENDING_SHOW to TRENDING_LATEST) to DiscoveryEpisodeOwner("6100", TRENDING_SHOW),
                (BRIEF_SHOW to BRIEF_RELATED) to DiscoveryEpisodeOwner("6200", BRIEF_SHOW),
                (CURIOSITY_SHOW to CURIOSITY_EPISODE) to DiscoveryEpisodeOwner("6300", CURIOSITY_SHOW),
                (VIBE_SHOW to VIBE_LATEST) to DiscoveryEpisodeOwner("6400", VIBE_SHOW),
            ),
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
                subscribedPodcastIds = listOf(REC_SHOW, "rss:abc"),
                languages = listOf("en", "id", "hi", "fr"),
            )

        // The episode played under its Podcast Index ID is not offered again; the one in the queue
        // comes back under its stored ID, with the recipe ID the answer named.
        assertEquals(listOf(REC_B.toString(), "9001"), picks.map { it.id })
        assertEquals(listOf(REC_B, REC_C), picks.map { it.recipeId })
        val request = seen.single()
        assertEquals("/v2/recommendations", request.path)
        assertNull("v2 requests never carry the app key", request.appKey)
        val body = parse(request.body)
        assertEquals(2, body.get("contractVersion").asInt)
        val episodeSeeds = body.getAsJsonArray("seeds").map { it.asJsonObject }.filter { it.get("kind").asString == "episode" }
        // Recipe IDs for every ID the phone can place, including phone-minted and RSS-linked ones;
        // a Podcast Index ID it can't place still seeds through its text; "-66" has nothing to send.
        assertEquals(setOf(REC_A, RECIPE_B, RECIPE_C, 5555L), episodeSeeds.map { it.get("id").asLong }.toSet())
        // The Podcast Index ID the phone can't place is still excluded as itself.
        assertEquals(listOf(REC_A, RECIPE_B, RECIPE_C, 5555L), body.getAsJsonArray("excludedEpisodeIds").map { it.asLong })
        assertEquals(listOf(REC_SHOW.toLong()), body.getAsJsonArray("subscribedPodcastIds").map { it.asLong })
        // The API refuses more than four languages; `id` is expanded on the server.
        assertEquals(listOf("en", "id", "hi", "fr"), body.getAsJsonArray("languages").map { it.asString })
    }

    @Test
    fun aPlayedPodcastIndexEpisodeWithNoAliasIsMatchedByItsAudioAndNotOfferedAgain() = runTest {
        val database = inMemoryDatabase()
        try {
            val recorded = fixtureItems("fixtures/v2/recommendations.json")
            // Played on an older build from discovery: its show was never followed, so it has no alias.
            database.listeningHistoryDao().upsertAll(listOf(historyRow("5555", REC_SHOW, recorded[0].asJsonObject.get("enclosureUrl").asString, completed = true)))
            database.queueDao().insertQueueItem(queueRow("4444", REC_C_SHOW, recorded[2].asJsonObject.get("enclosureUrl").asString))
            answers["/v2/recommendations"] = { fixture("fixtures/v2/recommendations.json") }
            val history = listOf(HistoryItem("AP News", "Played", podcastId = REC_SHOW, episodeId = "5555", isCompleted = true))

            val picks = repository(RoomDiscoveryEpisodeIds { database }).getPersonalizedRecommendations(history = history, country = "us")

            assertEquals(listOf(REC_B.toString(), "4444"), picks.map { it.id })
            assertEquals(listOf(5555L), parse(seen.single().body).getAsJsonArray("excludedEpisodeIds").map { it.asLong })
        } finally {
            database.close()
        }
    }

    @Test
    fun aFailedRecommendationRequestKeepsTheLastGoodPicksAcrossRestartsAndNeverAsksV1() = runTest {
        answers["/v2/recommendations"] = { fixture("fixtures/v2/recommendations.json") }
        repository().getPersonalizedRecommendations(history = listeningHistory(), country = "us")

        // A new process: nothing in memory, the server is down.
        PodcastRepository.recommendationsCache.clear()
        answers["/v2/recommendations"] = { MockResponse().setResponseCode(503) }
        val afterRestart = repository().getPersonalizedRecommendations(history = listeningHistory(), interests = listOf("News"), country = "us")
        assertEquals(listOf(REC_B.toString(), "9001"), afterRestart.map { it.id })

        // An answer without picks (the server found no usable seed) is not a result either.
        answers["/v2/recommendations"] = { json("""{"status":"false","error":"No usable semantic seeds"}""", code = 422) }
        val noPicks = repository().getPersonalizedRecommendations(history = listeningHistory(), interests = listOf("Science"), country = "us")
        assertEquals(listOf(REC_B.toString(), "9001"), noPicks.map { it.id })

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
        answers["/v2/home/bootstrap"] = { fixture("fixtures/v2/bootstrap-post.json") }
        val first = repository().getHomeRecommendations(country = "us", history = listeningHistory(), languages = listOf("en", "id", "hi", "fr"))
        assertEquals(fixtureIds("fixtures/v2/bootstrap-post.json", "recommendations"), first.items.map { it.id })
        // Picks for the listener's own seeds are personal, whatever the server's flag says (as on v1).
        assertFalse(first.isFallback)
        assertFalse(first.failed)
        val sent = parse(seen.single().body)
        assertEquals("POST", seen.single().method)
        val recs = sent.getAsJsonObject("recommendationsRequest")
        assertEquals(2, recs.get("contractVersion").asInt)
        assertEquals(REC_A, recs.getAsJsonArray("excludedEpisodeIds").first().asLong)
        assertEquals(listOf("en", "id", "hi", "fr"), recs.getAsJsonArray("languages").map { it.asString })

        // The Worker answers 200 with no picks when its recommendation part fails.
        answers["/v2/home/bootstrap"] = { json("""{"trending":[],"recommendations":[],"isRecommendationsFallback":true}""") }
        val failed = repository().getHomeRecommendations(country = "us", history = listeningHistory())
        assertEquals(first.items.map { it.id }, failed.items.map { it.id })
        assertFalse(failed.isFallback)
        assertTrue(seen.none { it.path.startsWith("/home/bootstrap") })
    }

    @Test
    fun aListenerWithNoSeedOrInterestGetsTheCountryDefaultPicks() = runTest {
        answers["/v2/home/bootstrap"] = { fixture("fixtures/v2/bootstrap-post.json") }
        // Only an RSS follow and an RSS episode that has no recipe ID: nothing `/v2` can seed from.
        val history = listOf(HistoryItem("Feed show", "Unplaced", podcastId = "rss:abc", episodeId = "-66"))

        val picks = repository().getHomeRecommendations(country = "us", history = history, subscribedPodcastIds = listOf("rss:abc"))

        assertEquals(fixtureIds("fixtures/v2/bootstrap-post.json", "recommendations"), picks.items.map { it.id })
        assertTrue(picks.isFallback)
        val recs = parse(seen.single().body).getAsJsonObject("recommendationsRequest")
        assertEquals(listOf("News", "Comedy", "Technology", "Science"), recs.getAsJsonArray("interests").map { it.asString })
        assertNull(recs.get("seeds"))
        assertEquals(listOf("Cricket", "Indian news", "Technology", "Comedy"), countryDefaultInterests("IN"))
        assertEquals(countryDefaultInterests("us"), countryDefaultInterests("zz"))
    }

    @Test
    fun interestOnlyPicksArePersonalAsOnV1() = runTest {
        answers["/v2/home/bootstrap"] = { fixture("fixtures/v2/bootstrap-post.json") }

        val picks = repository().getHomeRecommendations(country = "us", interests = listOf("History"))

        assertFalse(picks.isFallback)
        assertEquals(listOf("History"), parse(seen.single().body).getAsJsonObject("recommendationsRequest").getAsJsonArray("interests").map { it.asString })
    }

    @Test
    fun aFailureWithNothingToKeepIsMarkedFailedSoHomeKeepsWhatItShows() = runTest {
        answers["/v2/home/bootstrap"] = { MockResponse().setResponseCode(503) }
        val repository = repository()

        val picks = repository.getHomeRecommendations(country = "us", history = listeningHistory())
        assertTrue(picks.failed)
        assertTrue(picks.items.isEmpty())
        val bootstrap = repository.getHomeBootstrapDataFast("us")
        assertTrue(bootstrap.failed)

        answers["/v2/home/bootstrap"] = { fixture("fixtures/v2/bootstrap-get.json") }
        assertFalse(repository.getHomeBootstrapDataFast("us").failed)
        answers["/v2/home/bootstrap"] = { MockResponse().setResponseCode(503) }
        val kept = repository.getHomeBootstrapDataFast("us")
        assertFalse(kept.failed)
        assertEquals(fixtureIds("fixtures/v2/bootstrap-get.json", "trending"), kept.trending.map { it.id })
    }

    @Test
    fun discoveryRoutesUseV2AndShowEpisodesUnderTheirLocalIds() = runTest {
        val items = fixtureItems("fixtures/v2/recommendations.json").toString()
        answers["/v2/trending"] = { fixture("fixtures/v2/trending.json") }
        answers["/v2/curated/vibe"] = { fixture("fixtures/v2/vibe.json") }
        answers["/v2/home/bootstrap"] = { fixture("fixtures/v2/bootstrap-get.json") }
        answers["/v2/curated/curiosity-v3"] = { fixture("fixtures/v2/curiosity.json") }
        answers["/v2/search/semantic"] = { json("""{"status":"true","feeds":[],"items":$items}""") }
        answers["/v2/recommendations/because-you-like"] = { json("""{"status":"true","podcasts":[{"id":75075,"title":"Reply All"}],"episodes":$items}""") }
        answers["/v2/episodes/similar"] = { json("""{"status":"true","items":$items}""") }
        answers["/v2/briefings/metadata/us"] = { fixture("fixtures/v2/briefing-metadata.json") }
        val repository = repository()

        val trending = repository.getTrendingPodcasts(country = "us", limit = 10)
        assertEquals(fixtureIds("fixtures/v2/trending.json", "feeds"), trending.map { it.id })
        assertEquals("6100", trending.first().latestEpisode?.id)
        assertEquals(TRENDING_LATEST, trending.first().latestEpisode?.recipeId)
        // A latest episode the phone doesn't hold keeps its recipe ID, recorded as such.
        assertEquals(trending[1].latestEpisode?.id?.toLong(), trending[1].latestEpisode?.recipeId)
        assertEquals("6400", repository.getCuratedPodcasts("morning_news", "us", listOf("en")).first().latestEpisode?.id)
        assertEquals(mapOf("morning_news" to "6400"), repository.getCuratedVibes(listOf("morning_news"), "us").mapValues { it.value.first().latestEpisode?.id })
        val translated = listOf("7777", REC_B.toString(), "9001")
        assertEquals(translated, repository.searchSemanticGrouped("space", "us").episodes.map { it.id })
        assertEquals(translated, repository.getBecauseYouLikeRecommendations("The Daily", "News", REC_SHOW, "us").episodes.map { it.id })
        val similar = repository.getSimilarEpisodes(SimilarEpisodesQuery("7777", REC_SHOW, "Seed", "D", "P", country = "us"))
        // The seed episode itself is not similar to itself.
        assertEquals(listOf(REC_B.toString(), "9001"), similar.map { it.id })
        assertTrue(repository.getBriefingMetadata("us")?.chaptersUrl.orEmpty().startsWith("https://api.aswin.cx/v2/briefings/chapters/us?"))

        val curiosity = repository.getCuratedCuriosity(page = 1)!!
        assertNull(curiosity.questionOfTheDay)
        assertEquals(6300L, curiosity.questionsStack.first().episode.id)
        assertEquals(fixtureCuriosityIds().drop(1), curiosity.questionsStack.drop(1).map { it.episode.id })

        val bootstrap = repository.getHomeBootstrapDataFast("us")
        assertTrue(bootstrap.briefing?.title.orEmpty().isNotBlank())
        assertEquals("6200", bootstrap.briefingChapters.first().relatedEpisodes!!.first().id)
        assertEquals(BRIEF_RELATED, bootstrap.briefingChapters.first().relatedEpisodes!!.first().recipeId)
        assertEquals("6100", bootstrap.trending.first().latestEpisode?.id)

        assertTrue(seen.all { it.path.startsWith("/v2/") })
        assertTrue(seen.all { it.appKey == null })
        val similarBody = parse(seen.single { it.path == "/v2/episodes/similar" }.body)
        assertEquals(REC_A.toString(), similarBody.get("id").asString)
        assertTrue(seen.any { it.path == "/v2/home/bootstrap?country=us" && it.method == "GET" })
    }

    @Test
    fun similarEpisodesSendZeroForAnEpisodeThePhoneCannotPlaceAndLeaveItOut() = runTest {
        val first = fixtureItems("fixtures/v2/recommendations.json")[0].asJsonObject
        answers["/v2/episodes/similar"] = { json("""{"status":"true","items":[$first]}""") }

        val similar = repository().getSimilarEpisodes(SimilarEpisodesQuery("-66", REC_SHOW, first.get("title").asString, "D", "P"))

        val body = parse(seen.single().body)
        assertEquals("0", body.get("id").asString)
        assertEquals(REC_SHOW, body.get("podcastId").asString)
        // The server can't leave out a seed it can't place, so its own episode is dropped here.
        assertTrue(similar.isEmpty())
    }

    @Test
    fun showSearchStaysOnV1BesideTypeahead() = runTest {
        answers["/search/typeahead"] = { json("""{"status":"true","feeds":[{"id":743229,"title":"The Daily"}]}""") }
        answers["/search"] = { json("""{"status":"true","feeds":[{"id":75075,"title":"Reply All"},{"id":0,"itunesId":1234,"title":"Only on Apple"}]}""") }
        val repository = repository()

        val grouped = repository.searchPodcastsGrouped("daily")
        val plain = repository.searchPodcastsWithCorrection("daily")

        assertEquals(listOf("743229"), grouped.catalog.map { it.id })
        assertEquals(listOf("75075", "itunes:1234"), grouped.alsoFound.map { it.id })
        assertEquals(listOf("75075", "itunes:1234"), plain.podcasts.map { it.id })
        assertEquals(setOf("/search/typeahead", "/search"), seen.map { it.path.substringBefore('?') }.toSet())
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
        assertEquals(fixtureIds("fixtures/v2/trending.json", "feeds"), streamed.map { it.id })
        assertEquals("6100", streamed.first().latestEpisode?.id)

        answers["/v2/trending"] = { MockResponse().setResponseCode(503) }
        assertEquals(listOf(streamed), repository.getTrendingPodcastsStream("us", 50, null, 0).toList())
        assertTrue(seen.all { it.path.startsWith("/v2/trending") })
    }

    @Test
    fun theV2DiscoverySwitchSendsDiscoveryBackToV1AsShipped() = runTest {
        v2Discovery = false
        val repository = repository()

        repository.getTrendingPodcasts(country = "us")
        repository.getTrendingPodcastsStream("us", 50, null, 0).toList()
        repository.getCuratedPodcasts("calm")
        repository.searchPodcastsWithCorrection("daily")
        repository.searchSemanticGrouped("space", "us")
        repository.getBecauseYouLikeRecommendations("The Daily", "News")
        repository.getSimilarEpisodes(SimilarEpisodesQuery("7777", REC_SHOW, "T", "D", "P"))
        repository.getBriefingMetadata("us")
        repository.getHomeBootstrapDataFast("us")
        repository.getCuratedCuriosity(page = 1)
        repository.getPersonalizedRecommendations(history = listeningHistory(), country = "us")
        repository.getHomeRecommendations(country = "us", history = listeningHistory())

        assertTrue(seen.isNotEmpty())
        assertTrue(seen.map { it.path }.toString(), seen.none { it.path.startsWith("/v2/") })
        // Personal picks use contract 1, as shipped builds do; they never reached `/recommendations/v2`.
        val recommendations = seen.single { it.path.startsWith("/recommendations") && !it.path.contains("because-you-like") }
        assertEquals("/recommendations", recommendations.path)
        assertNull(parse(recommendations.body).get("contractVersion"))
        val bootstrap = parse(seen.single { it.path.startsWith("/home/bootstrap") && it.method == "POST" }.body)
        assertNull(bootstrap.getAsJsonObject("recommendationsRequest").get("contractVersion"))
    }

    @Test
    fun v1AndV2AnswersNeverShareACacheEntry() = runTest {
        answers["/recommendations"] = { fixture("fixtures/v2/recommendations.json") }
        answers["/v2/recommendations"] = { fixture("fixtures/v2/recommendations.json") }
        val items = fixtureItems("fixtures/v2/recommendations.json").toString()
        answers["/recommendations/because-you-like"] = { json("""{"status":"true","episodes":$items}""") }
        answers["/v2/recommendations/because-you-like"] = { json("""{"status":"true","episodes":$items}""") }
        val repository = repository()
        val history = listOf(HistoryItem("AP News", "Played", podcastId = REC_SHOW, episodeId = "1234", isLiked = true))

        v2Discovery = false
        val v1 = repository.getPersonalizedRecommendations(history = history, country = "us")
        val v1Byl = repository.getBecauseYouLikeRecommendations("The Daily", "News", REC_SHOW, "us")
        v2Discovery = true
        val v2 = repository.getPersonalizedRecommendations(history = history, country = "us")
        val v2Byl = repository.getBecauseYouLikeRecommendations("The Daily", "News", REC_SHOW, "us")
        v2Discovery = false
        repository.getPersonalizedRecommendations(history = history, country = "us")
        repository.getBecauseYouLikeRecommendations("The Daily", "News", REC_SHOW, "us")

        // v1 keeps the answer as the server named it; v2 shows it under the phone's own ID.
        assertEquals(REC_A.toString(), v1.first().id)
        assertEquals("7777", v2.first().id)
        assertEquals(REC_A.toString(), v1Byl.episodes.first().id)
        assertEquals("7777", v2Byl.episodes.first().id)
        assertEquals(
            listOf("/recommendations", "/recommendations/because-you-like", "/v2/recommendations", "/v2/recommendations/because-you-like"),
            seen.map { it.path },
        )
    }

    @Test
    fun withoutASeedOrAnInterestNoPersonalRecommendationRequestIsMadeAsOnV1() = runTest {
        val history = listOf(HistoryItem("Show", "Unplaced", podcastId = REC_SHOW, episodeId = "-66"))

        assertTrue(repository().getPersonalizedRecommendations(history = history, country = "us").isEmpty())
        assertFalse(seen.any())
    }

    private fun repository(discoveryIds: DiscoveryEpisodeIds = ids): PodcastRepository {
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
            discoveryEpisodeIds = discoveryIds,
        )
    }

    private fun inMemoryDatabase(): BoxLoreDatabase = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()

    private fun listeningHistory(): List<HistoryItem> = listOf(
        HistoryItem("AP News", "Played on an older build", podcastId = REC_SHOW, episodeId = "7777", isLiked = true),
        HistoryItem("AP News", "Feed-only episode", podcastId = REC_SHOW, episodeId = "-55", isCompleted = true),
        HistoryItem("AP News", "Not placed yet", podcastId = REC_SHOW, episodeId = "-66"),
        HistoryItem("AP News (feed)", "Linked RSS episode", podcastId = "rss:abc", episodeId = "-77"),
        HistoryItem("Reply All", "Other show", podcastId = "75075", episodeId = "5555"),
    )

    private fun historyRow(id: String, show: String, audioUrl: String, completed: Boolean) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = show,
        episodeTitle = "Episode $id",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = audioUrl,
        podcastName = "Show",
        progressMs = 1_000L,
        durationMs = 1_000L,
        isCompleted = completed,
        lastPlayedAt = 500L,
    )

    private fun queueRow(id: String, show: String, audioUrl: String) = cx.aswin.boxlore.core.database.entities.QueueItem(
        episodeId = id,
        title = "Queued",
        podcastId = show,
        podcastTitle = "Show",
        imageUrl = null,
        audioUrl = audioUrl,
        duration = 60,
        pubDate = 0L,
        description = null,
        position = 0,
    )

    private fun fixtureText(path: String): String = requireNotNull(javaClass.classLoader?.getResource(path)) { path }.readText()

    private fun fixture(path: String): MockResponse = json(fixtureText(path))

    private fun fixtureItems(path: String): JsonArray = parse(fixtureText(path)).getAsJsonArray("items")

    /** The `id` of each entry of [list] in the recorded fixture at [path]. */
    private fun fixtureIds(path: String, list: String): List<String> = parse(fixtureText(path)).getAsJsonArray(list).map { it.asJsonObject.get("id").asString }

    private fun fixtureCuriosityIds(): List<Long> =
        parse(fixtureText("fixtures/v2/curiosity.json")).getAsJsonArray("questionsStack").map { it.asJsonObject.getAsJsonObject("episode").get("id").asLong }

    private fun json(body: String, code: Int = 200): MockResponse = MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun parse(body: String): JsonObject = JsonParser.parseString(body).asJsonObject

    private data class Seen(val method: String, val path: String, val body: String, val appKey: String?,)

    private class FakeDiscoveryIds(
        private val recipeIds: Map<String, Long>,
        private val owners: Map<Pair<String, Long>, DiscoveryEpisodeOwner>,
    ) : DiscoveryEpisodeIds {
        override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> =
            DiscoveryEpisodeIds.ownRecipeIds(episodeIds) + episodeIds.mapNotNull { id -> recipeIds[id]?.let { id to it } }

        override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, DiscoveryEpisodeOwner> =
            refs.mapNotNull { ref -> owners[ref.podcastId to ref.recipeId]?.let { ref to it } }.toMap()
    }

    private companion object {
        const val APP_KEY = "test-app-key"

        // Recorded `/v2/recommendations` picks (fixtures/v2/recommendations.json).
        const val REC_SHOW = "190857"
        const val REC_A = 8_611_187_632_061_423L
        const val REC_B = 5_622_440_355_708_660L
        const val REC_C_SHOW = "55810"
        const val REC_C = 5_989_386_213_024_727L

        // Recorded trending, briefing, curiosity and vibe episodes.
        const val TRENDING_SHOW = "743229"
        const val TRENDING_LATEST = 6_477_972_780_914_663L
        const val BRIEF_SHOW = "7488327"
        const val BRIEF_RELATED = 7_504_489_678_679_256L
        const val CURIOSITY_SHOW = "197123"
        const val CURIOSITY_EPISODE = 6_548_421_932_954_342L
        const val VIBE_SHOW = "55810"
        const val VIBE_LATEST = 8_729_163_079_552_997L

        // Recipe IDs of phone-minted and RSS-linked history entries.
        const val RECIPE_B = 4_503_599_627_370_498L
        const val RECIPE_C = 4_503_599_627_370_499L
    }
}
