package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.model.BecauseYouLikeRequest
import cx.aswin.boxlore.core.network.model.BootstrapRequest
import cx.aswin.boxlore.core.network.model.RecommendationSeedV2
import cx.aswin.boxlore.core.network.model.RecommendationsV2Request
import cx.aswin.boxlore.core.network.model.SimilarEpisodesRequest
import cx.aswin.boxlore.core.network.model.V2BootstrapRequest
import cx.aswin.boxlore.core.network.model.V2EpisodeRegisterRequest
import cx.aswin.boxlore.core.network.model.V2EpisodeReply
import cx.aswin.boxlore.core.network.model.V2EpisodeSort
import cx.aswin.boxlore.core.network.model.V2FeedReadTarget
import cx.aswin.boxlore.core.network.model.V2FeedReportRequest
import cx.aswin.boxlore.core.network.model.V2FeedReportStatus
import cx.aswin.boxlore.core.network.model.V2FetchVia
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsRequest
import cx.aswin.boxlore.core.network.model.v2Identity
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * MockWebServer contract tests for [BoxLoreApiV2]: paths, raw request JSON (the client never
 * encodes defaults, so only explicitly set fields go out) and response decoding.
 */
class BoxLoreApiV2ContractTest {
    private lateinit var server: MockWebServer
    private lateinit var api: BoxLoreApiV2

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = NetworkModule.createBoxLoreApiV2(server.url("/").toString(), client())
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `paths are relative to a base URL with a path prefix`() {
        val prefixed = NetworkModule.createBoxLoreApiV2(server.url("/api/").toString(), client())
        enqueueJson("""{"status":"true","feed":{"id":920666,"title":"Show"}}""")

        prefixed.getPodcast(920666L).execute()

        assertEquals("/api/v2/podcast?id=920666", server.takeRequest().path)
    }

    @Test
    fun `getPodcast decodes the address fields and keeps the original address`() {
        enqueueJson(
            """
            {"status":"true","source":"pidump",
             "feed":{"id":920666,"title":"Show","url":"https://feeds.example.com/new","originalUrl":"https://old.example.com/rss","type":"episodic"},
             "fetchUrl":"https://feeds.example.com/new","fetchVia":"direct","transportUrl":null,"feedSource":"registry:apple",
             "epNs":920666,"episodeCount":2400,"appleLatestAt":1792600000,"appleEpisodeCount":2399,
             "flags":{"addressRepair":true,"episodeStale":false},"verifiedAt":1792700000,"unknownField":1}
            """.trimIndent(),
        )

        val body = requireNotNull(api.getPodcast(920666L).execute().body())

        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/v2/podcast?id=920666", request.path)
        assertNoAppKey(request)
        assertEquals("pidump", body.source)
        assertEquals("https://old.example.com/rss", body.feed?.originalUrl)
        assertEquals("https://feeds.example.com/new", body.feed?.url)
        assertEquals(V2FetchVia.DIRECT, body.fetchVia)
        assertEquals(V2FeedReadTarget.Direct("https://feeds.example.com/new"), body.readTarget(920666L))
        assertEquals("registry:apple", body.feedSource)
        assertEquals(920666L, body.validEpNs)
        assertEquals(2400L, body.episodeCount)
        assertEquals(1792600000L, body.appleLatestAt)
        assertEquals(2399L, body.appleEpisodeCount)
        assertTrue(body.flags!!.addressRepair)
        assertFalse(body.flags!!.episodeStale)
        assertEquals(1792700000L, body.verifiedAt)
    }

    @Test
    fun `getPodcast reports an out-of-range namespace as missing`() {
        enqueueJson("""{"status":"true","epNs":0}""")
        assertNull(api.getPodcast(1L).execute().body()!!.validEpNs)
        enqueueJson("""{"status":"true","epNs":9007199254740992}""")
        assertNull(api.getPodcast(1L).execute().body()!!.validEpNs)
    }

    @Test
    fun `getShowFeeds sends only the ids and decodes unknown and proxied shows`() {
        enqueueJson(
            """
            {"registryAvailable":true,"results":[
              {"id":920666,"fetchUrl":"https://feeds.example.com/a","fetchVia":"direct","transportUrl":null,"feedSource":"registry:apple","epNs":920666,"flags":{"addressRepair":false,"episodeStale":true},"verifiedAt":200},
              {"id":3391039,"fetchUrl":"http://plain.example.com/rss","fetchVia":"proxy","transportUrl":"https://api.example.com/v2/feed/3391039","feedSource":"catalog","epNs":3391039,"flags":{"addressRepair":false,"episodeStale":false},"verifiedAt":null},
              {"id":42,"fetchUrl":null,"fetchVia":null,"transportUrl":null,"feedSource":null,"epNs":42,"flags":{"addressRepair":false,"episodeStale":false},"verifiedAt":null}
            ]}
            """.trimIndent(),
        )

        val body = requireNotNull(api.getShowFeeds(V2ShowsFeedsRequest(listOf(920666L, 3391039L, 42L))).execute().body())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v2/shows/feeds", request.path)
        assertEquals("""{"ids":[920666,3391039,42]}""", request.body.readUtf8())
        assertNoAppKey(request)
        assertTrue(body.registryAvailable)
        assertEquals(listOf(920666L, 3391039L, 42L), body.results.map { it.id })
        val (direct, proxied, unknown) = body.results
        assertEquals(V2FeedReadTarget.Direct("https://feeds.example.com/a"), direct.readTarget(direct.id))
        assertTrue(direct.flags!!.episodeStale)
        assertEquals(200L, direct.verifiedAt)
        assertEquals("http://plain.example.com/rss", proxied.fetchUrl)
        assertEquals("https://api.example.com/v2/feed/3391039", proxied.transportUrl)
        assertEquals(V2FeedReadTarget.ViaApi(3391039L), proxied.readTarget(proxied.id))
        assertNull(unknown.fetchUrl)
        assertNull(unknown.readTarget(unknown.id))
        assertNull(unknown.verifiedAt)
        assertEquals(42L, unknown.validEpNs)
    }

    @Test
    fun `getShowFeeds tolerates a minimal answer`() {
        enqueueJson("""{"results":[{"id":7}]}""")

        val body = requireNotNull(api.getShowFeeds(V2ShowsFeedsRequest(listOf(7L))).execute().body())

        assertFalse(body.registryAvailable)
        assertNull(body.results.single().fetchUrl)
        assertNull(body.results.single().validEpNs)
    }

    @Test
    fun `getFeed streams the document and sends the validator`() {
        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/rss+xml")
                .setHeader("ETag", "\"abc\"")
                .setHeader("X-Boxlore-Feed-Source", "cache")
                .setBody("<rss version=\"2.0\"><channel/></rss>"),
        )
        server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "\"abc\""))

        val first = api.getFeed(3391039L).execute()
        val second = api.getFeed(3391039L, ifNoneMatch = "\"abc\"").execute()

        assertTrue(first.isSuccessful)
        assertEquals("<rss version=\"2.0\"><channel/></rss>", first.body()!!.string())
        assertEquals("\"abc\"", first.headers()["ETag"])
        assertEquals("cache", first.headers()["X-Boxlore-Feed-Source"])
        val plain = server.takeRequest()
        assertEquals("GET", plain.method)
        assertEquals("/v2/feed/3391039", plain.path)
        assertNull(plain.getHeader("If-None-Match"))
        assertEquals("no-store", plain.getHeader("Cache-Control"))
        assertNoAppKey(plain)
        assertEquals(304, second.code())
        assertFalse(second.isSuccessful)
        assertEquals("\"abc\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `getFeed never fills the shared HTTP cache`(@TempDir cacheDir: File) {
        val cache = Cache(cacheDir, 1024L * 1024L)
        val cached = NetworkModule.createBoxLoreApiV2(server.url("/").toString(), client().newBuilder().cache(cache).build())
        repeat(2) {
            server.enqueue(
                MockResponse()
                    .setHeader("Cache-Control", "private, max-age=600")
                    .setHeader("ETag", "\"abc\"")
                    .setBody("<rss/>"),
            )
        }

        repeat(2) { cached.getFeed(1L).execute().body()!!.close() }

        assertEquals(2, server.requestCount)
        assertEquals(0L, cache.size())
        cache.close()
    }

    @Test
    fun `a proxied show is read with getFeed on the base URL, never at its transportUrl`() {
        enqueueJson(
            """{"registryAvailable":true,"results":[{"id":3391039,"fetchUrl":"http://plain.example.com/rss","fetchVia":"proxy","transportUrl":"https://elsewhere.example.net/v2/feed/3391039"}]}""",
        )
        server.enqueue(MockResponse().setHeader("Content-Type", "application/rss+xml").setBody("<rss/>"))

        val address = api.getShowFeeds(V2ShowsFeedsRequest(listOf(3391039L))).execute().body()!!.results.single()
        server.takeRequest()
        val target = address.readTarget(address.id) as V2FeedReadTarget.ViaApi
        val document = api.getFeed(target.showId).execute()

        assertEquals("<rss/>", document.body()!!.string())
        val read = server.takeRequest()
        assertEquals("/v2/feed/3391039", read.path)
        assertEquals(server.hostName, read.requestUrl!!.host)
        assertNoAppKey(read)
    }

    @Test
    fun `getFeed passes on the not_proxied address in the error body`() {
        enqueueJson("""{"error":"not_proxied","fetchUrl":"https://feeds.example.com/a"}""", code = 404)

        val response = api.getFeed(1L).execute()

        assertEquals(404, response.code())
        assertTrue(response.errorBody()!!.string().contains("https://feeds.example.com/a"))
    }

    @Test
    fun `reportFeed sends the id and status only and decodes the current address`() {
        enqueueJson(
            """{"queued":true,"fetchUrl":"http://plain.example.com/rss","fetchVia":"proxy","transportUrl":"https://api.example.com/v2/feed/920666","feedSource":"registry:apple"}""",
        )

        val body = requireNotNull(api.reportFeed(V2FeedReportRequest(920666L, V2FeedReportStatus.http(404))).execute().body())

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v2/feed-report", request.path)
        assertEquals("""{"id":920666,"status":"http_404"}""", request.body.readUtf8())
        assertNoAppKey(request)
        assertTrue(body.queued)
        assertEquals(V2FeedReadTarget.ViaApi(920666L), body.readTarget(920666L))
        assertEquals("registry:apple", body.feedSource)
    }

    @Test
    fun `getEpisode decodes a known episode with its identity`() {
        enqueueJson(
            """
            {"status":"true","source":"book","episode":{
              "id":8825297254050678,"servedId":45678901234,"catalogKey":"https://api.spreaker.com/episode/75827097",
              "title":"Episode","guid":"https://api.spreaker.com/episode/75827097","enclosureUrl":"https://cdn.example.com/a.mp3",
              "datePublished":1792600000,"duration":1800,"feedId":3391039,"feedTitle":"Show"}}
            """.trimIndent(),
        )

        val reply = api.getEpisode(episodeId = "8825297254050678", showId = 3391039L).execute().body()

        val request = server.takeRequest()
        assertEquals("/v2/episode?id=8825297254050678&show=3391039", request.path)
        assertNoAppKey(request)
        val found = reply as V2EpisodeReply.Found
        assertEquals("book", found.source)
        assertEquals("https://cdn.example.com/a.mp3", found.episode.enclosureUrl)
        val identity = requireNotNull(found.episode.v2Identity())
        assertEquals(8825297254050678L, identity.recipeId)
        assertEquals(3391039L, identity.showId)
        assertEquals("https://api.spreaker.com/episode/75827097", identity.catalogKey)
        assertEquals("45678901234", identity.servedId)
    }

    @Test
    fun `getEpisode decodes needsFeed with and without an address`() {
        enqueueJson("""{"status":"true","needsFeed":true,"show":920666,"fetchUrl":null,"fetchVia":null,"transportUrl":null}""")
        enqueueJson(
            """{"status":"true","needsFeed":true,"show":920666,"fetchUrl":"http://plain.example.com/rss","fetchVia":"proxy","transportUrl":"https://api.example.com/v2/feed/920666"}""",
        )

        val bare = api.getEpisode("4503599627370496", 920666L).execute().body()
        val proxied = api.getEpisode("4503599627370496", 920666L).execute().body()

        val needsFeed = bare as V2EpisodeReply.NeedsFeed
        assertEquals(920666L, needsFeed.show)
        assertNull(needsFeed.fetchUrl)
        assertNull(needsFeed.readTarget(needsFeed.show))
        val withAddress = proxied as V2EpisodeReply.NeedsFeed
        assertEquals("http://plain.example.com/rss", withAddress.fetchUrl)
        assertEquals(V2FeedReadTarget.ViaApi(920666L), withAddress.readTarget(withAddress.show))
    }

    @Test
    fun `getEpisode without a show leaves the parameter out, and 404 is not a reply`() {
        enqueueJson("""{"status":"false","error":"not_found"}""", code = 404)

        val response = api.getEpisode("7777").execute()

        assertEquals("/v2/episode?id=7777", server.takeRequest().path)
        assertEquals(404, response.code())
        assertNull(response.body())
    }

    @Test
    fun `getEpisodes sends paging explicitly and decodes the fallback list`() {
        enqueueJson(
            """
            {"status":"true","fallback":true,"source":"feeds","feedId":920666,"items":[
              {"id":4503599627370497,"servedId":null,"catalogKey":"guid-1","title":"One","feedId":920666,"enclosureUrl":"https://cdn.example.com/1.mp3"},
              {"id":12345,"title":"Not a recipe ID","feedId":920666}
            ],"hasMore":true,"offset":0,"limit":100}
            """.trimIndent(),
        )

        val body = requireNotNull(api.getEpisodes(920666L).execute().body())

        assertEquals("/v2/episodes?id=920666&limit=100&offset=0&sort=newest", server.takeRequest().path)
        assertTrue(body.fallback)
        assertEquals("feeds", body.source)
        assertEquals(920666L, body.feedId)
        assertTrue(body.hasMore)
        assertEquals(100, body.limit)
        val identity = requireNotNull(body.items[0].v2Identity())
        assertEquals(4503599627370497L, identity.recipeId)
        assertEquals("guid-1", identity.catalogKey)
        assertNull(identity.servedId)
        assertNull(body.items[1].v2Identity())

        enqueueJson("""{"status":"true","fallback":true,"items":[]}""")
        api.getEpisodes(920666L, limit = 50, offset = 100, sort = V2EpisodeSort.OLDEST).execute()
        assertEquals("/v2/episodes?id=920666&limit=50&offset=100&sort=oldest", server.takeRequest().path)
    }

    @Test
    fun `registerEpisode leaves out missing keys and range-checks the answer`() {
        enqueueJson("""{"recipeId":8825297254050678,"servedId":null,"confirmed":false,"show":3391039,"catalogKey":"guid-1"}""")
        enqueueJson("""{"recipeId":42,"servedId":45678901234,"confirmed":true,"show":3391039,"catalogKey":"guid-2"}""")

        val first = api.registerEpisode(V2EpisodeRegisterRequest(show = 3391039L, guid = "guid-1")).execute().body()!!
        val second =
            api
                .registerEpisode(
                    V2EpisodeRegisterRequest(show = 3391039L, guid = "guid-2", enclosureUrl = "https://cdn.example.com/2.mp3"),
                ).execute()
                .body()!!

        val firstRequest = server.takeRequest()
        assertEquals("POST", firstRequest.method)
        assertEquals("/v2/episodes/register", firstRequest.path)
        assertEquals("""{"show":3391039,"guid":"guid-1"}""", firstRequest.body.readUtf8())
        assertNoAppKey(firstRequest)
        assertEquals(
            """{"show":3391039,"guid":"guid-2","enclosureUrl":"https://cdn.example.com/2.mp3"}""",
            server.takeRequest().body.readUtf8(),
        )
        assertEquals(8825297254050678L, first.validRecipeId)
        assertFalse(first.confirmed)
        assertNull(second.validRecipeId)
        assertEquals(45678901234L, second.servedId)
        assertTrue(second.confirmed)
    }

    @Test
    fun `recommendations send the contract version and explicit fields and leave defaults out`() {
        enqueueJson("""{"status":"true","items":[{"id":4503599627370497,"title":"Rec","feedId":920666,"servedId":7777}]}""")

        val body =
            api
                .getRecommendations(
                    deviceUuid = "device-1",
                    request =
                        RecommendationsV2Request(
                            contractVersion = RecommendationsV2Request.CONTRACT_VERSION,
                            country = "in",
                            languages = listOf("en", "hi"),
                            mode = "explore",
                            limit = 20,
                            seeds = listOf(RecommendationSeedV2(kind = "episode", id = 4503599627370497L, weight = 1.0)),
                            excludedEpisodeIds = listOf(4503599627370498L),
                        ),
                ).execute()
                .body()!!

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v2/recommendations", request.path)
        assertEquals("device-1", request.getHeader("X-Device-UUID"))
        assertNoAppKey(request)
        assertEquals(
            """{"contractVersion":2,"country":"in","languages":["en","hi"],"mode":"explore","limit":20,""" +
                """"seeds":[{"kind":"episode","id":4503599627370497,"weight":1.0}],"excludedEpisodeIds":[4503599627370498]}""",
            request.body.readUtf8(),
        )
        assertEquals("7777", body.items.single().v2Identity()!!.servedId)
    }

    @Test
    fun `home bootstrap posts the request and the device header`() {
        enqueueJson("""{"trending":[],"recommendations":[]}""")

        api
            .getHomeBootstrap(
                deviceUuid = "device-1",
                request = V2BootstrapRequest(country = "us", vibeIds = listOf("calm"), include = listOf(BootstrapRequest.INCLUDE_TRENDING)),
            ).execute()

        val request = server.takeRequest()
        assertEquals("/v2/home/bootstrap", request.path)
        assertEquals("device-1", request.getHeader("X-Device-UUID"))
        assertEquals("""{"country":"us","vibeIds":["calm"],"include":["trending"]}""", request.body.readUtf8())
    }

    @Test
    fun `home bootstrap sends the current recommendation body with its contract version`() {
        enqueueJson("""{"trending":[],"recommendations":[{"id":4503599627370497,"title":"Rec","feedId":920666}],"isRecommendationsFallback":false}""")

        val body =
            api
                .getHomeBootstrap(
                    deviceUuid = "device-1",
                    request =
                        V2BootstrapRequest(
                            country = "in",
                            vibeIds = emptyList(),
                            deviceUuid = "device-1",
                            recommendationsRequest =
                                RecommendationsV2Request(
                                    contractVersion = RecommendationsV2Request.CONTRACT_VERSION,
                                    country = "in",
                                    interests = listOf("History"),
                                    excludedEpisodeIds = listOf(4503599627370498L),
                                ),
                            languages = listOf("en", "hi"),
                            include = listOf(BootstrapRequest.INCLUDE_RECOMMENDATIONS),
                        ),
                ).execute()
                .body()!!

        assertEquals(
            """{"country":"in","vibeIds":[],"deviceUuid":"device-1","recommendationsRequest":{"contractVersion":2,"country":"in",""" +
                """"interests":["History"],"excludedEpisodeIds":[4503599627370498]},"languages":["en","hi"],"include":["recommendations"]}""",
            server.takeRequest().body.readUtf8(),
        )
        assertEquals(4503599627370497L, body.recommendations.single().v2Identity()!!.recipeId)
    }

    @Test
    fun `similar episodes send the recipe ID and leave defaults out`() {
        enqueueJson("""{"status":"true","items":[]}""")

        api
            .getSimilarEpisodes(
                SimilarEpisodesRequest(
                    id = "4503599627370497",
                    podcastId = "920666",
                    title = "T",
                    description = "D",
                    podcastTitle = "P",
                    country = "in",
                    languages = listOf("en", "hi"),
                ),
            ).execute()

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/v2/episodes/similar", request.path)
        assertNoAppKey(request)
        assertEquals(
            """{"id":"4503599627370497","podcastId":"920666","title":"T","description":"D","podcastTitle":"P",""" +
                """"country":"in","languages":["en","hi"]}""",
            request.body.readUtf8(),
        )
    }

    @Test
    fun `because-you-like sends the excluded show and country only when set`() {
        enqueueJson("""{"status":"true"}""")
        enqueueJson("""{"status":"true"}""")

        api.getBecauseYouLike(BecauseYouLikeRequest(podcastTitle = "Show", podcastDescription = "About", excludePodcastId = "920666", country = "in")).execute()
        api.getBecauseYouLike(BecauseYouLikeRequest(podcastTitle = "Show", podcastDescription = "About")).execute()

        val full = server.takeRequest()
        assertEquals("POST", full.method)
        assertEquals("/v2/recommendations/because-you-like", full.path)
        assertNoAppKey(full)
        assertEquals(
            """{"podcastTitle":"Show","podcastDescription":"About","excludePodcastId":"920666","country":"in"}""",
            full.body.readUtf8(),
        )
        assertEquals("""{"podcastTitle":"Show","podcastDescription":"About"}""", server.takeRequest().body.readUtf8())
    }

    @Test
    fun `discovery routes use the v2 paths without an app key`() {
        val calls: List<Pair<String, () -> Unit>> =
            listOf(
                "/v2/trending?country=us&limit=50&offset=0" to { api.getTrending().execute() },
                "/v2/trending?country=in&limit=10&cat=News&offset=5" to { api.getTrendingStream("in", 10, "News", 5).execute().body()?.close() },
                "/v2/home/bootstrap?country=in" to { api.getHomeBootstrapGet("in").execute() },
                "/v2/search?q=reply%20all&limit=30" to { api.search("reply all", 30).execute() },
                "/v2/search?q=news" to { api.search("news").execute() },
                "/v2/search/semantic?q=space&country=us" to { api.searchSemantic("space").execute() },
                "/v2/curated/vibe?id=calm&country=in&languages=en%2Chi" to { api.getCuratedVibe("calm", "in", "en,hi").execute() },
                "/v2/curated/curiosity-v3?page=1" to { api.getCuratedCuriosity().execute() },
                "/v2/briefings/metadata/us" to { api.getBriefingMetadata("us").execute() },
                "/v2/briefings/chapters/us?d=2026-10-24&v=3" to {
                    api.getBriefingResource(V2BriefingRoute.CHAPTERS.path, "us", "2026-10-24", "3").execute().body()?.close()
                },
                "/v2/recommendations/because-you-like" to {
                    api.getBecauseYouLike(BecauseYouLikeRequest(podcastTitle = "Show", podcastDescription = "About")).execute()
                },
                "/v2/episodes/similar" to {
                    api
                        .getSimilarEpisodes(
                            SimilarEpisodesRequest(id = "4503599627370497", podcastId = "920666", title = "T", description = "D", podcastTitle = "P"),
                        ).execute()
                },
            )

        for ((path, call) in calls) {
            // One body every response type here accepts (unknown keys are ignored).
            enqueueJson("""{"status":"true","date":"2026-10-24","region":"us","title":"T","script":"S","audioUrl":"a","coverUrl":"c"}""")
            call()
            val request = server.takeRequest()
            assertEquals(path, request.path)
            assertNoAppKey(request)
        }
    }

    @Test
    fun `briefing URLs resolve under the base URL with or without a trailing slash`() {
        assertEquals(
            "https://api.example.com/v2/briefings/chapters/us?d=2026-10-24&v=3",
            V2BriefingRoute.CHAPTERS.url("https://api.example.com", "us", date = "2026-10-24", version = "3"),
        )
        assertEquals(
            "https://api.example.com/v2/briefings/transcript/in?d=2026-10-24",
            V2BriefingRoute.TRANSCRIPT.url("https://api.example.com/", "in", date = "2026-10-24"),
        )
        assertEquals(
            "https://api.example.com/api/v2/briefings/audio/a%2Fb",
            V2BriefingRoute.AUDIO.url("https://api.example.com/api/", "a/b"),
        )
    }

    private fun client(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()

    private fun enqueueJson(body: String, code: Int = 200) {
        server.enqueue(
            MockResponse()
                .setResponseCode(code)
                .setHeader("Content-Type", "application/json")
                .setBody(body),
        )
    }

    private fun assertNoAppKey(request: RecordedRequest) {
        assertNull(request.getHeader("X-App-Key"), "v2 requests never carry the app key")
    }
}
