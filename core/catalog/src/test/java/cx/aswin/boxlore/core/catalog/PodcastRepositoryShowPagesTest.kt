package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import cx.aswin.boxlore.core.rss.RssFeedClient
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
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
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The show page's episode source through [PodcastRepository] on a real Room database: the
 * feed-first stored copy (`feed_first` on), the server's list in recipe IDs mapped to the phone's
 * owners (`feed_first` off, and after a fallback), v1 `/episodes` only when `/v2/episodes` fails,
 * search on the phone that never calls v1 `episodes/search`, paging that stays stable while the copy
 * grows, and a rollback that changes no stored ID or alias.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastRepositoryShowPagesTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private val seen = CopyOnWriteArrayList<String>()
    private val ifNoneMatch = CopyOnWriteArrayList<String>()
    private val answers = mutableMapOf<String, () -> MockResponse>()
    private var feedFirst = true
    private var feedItems = 150

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        PodcastRepository.episodesCache.clear()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seen += request.path.orEmpty()
                    request.getHeader("If-None-Match")?.let { ifNoneMatch += it }
                    return answers[request.path.orEmpty().substringBefore('?')]?.invoke() ?: MockResponse().setResponseCode(404)
                }
            }
        server.start()
        answers["/v2/podcast"] = { json("""{"status":"true","fetchUrl":"$FEED_URL","fetchVia":"direct","epNs":$SHOW}""") }
        answers["/v2/episodes"] = { json(v2Episodes(listOf("g0", "g1", "Moon g2"))) }
    }

    @After
    fun tearDown() {
        server.shutdown()
        database.close()
        RssPodcastRepository.clearInstanceForTests()
        PodcastRepository.episodesCache.clear()
    }

    @Test
    fun withTheFlagOffThePageReadsTheServersListUnderLocalOwnersAndV1OnlyWhenItFails() = runBlocking {
        feedFirst = false
        val repository = repository(FakeIds(mapOf(recipe("g1") to "5001")))

        assertEquals(ShowPageSource.SERVER_LIST, repository.showPageSource(SHOW))
        val page = repository.getEpisodesPaginated(SHOW, limit = 20, offset = 0, sort = "newest")

        assertEquals(listOf(recipe("g0").toString(), "5001", recipe("Moon g2").toString()), page.episodes.map { it.id })
        assertEquals("the recipe ID stays with its owner", recipe("g1"), page.episodes[1].recipeId)
        assertEquals("/v2/episodes?id=$SHOW&limit=20&offset=0&sort=newest", seen.single())

        answers["/v2/episodes"] = { MockResponse().setResponseCode(503) }
        answers["/episodes"] = { json("""{"items":[{"id":777,"title":"v1","enclosureUrl":"https://cdn.example/v1.mp3","feedId":$SHOW}],"hasMore":false}""") }
        repository.invalidateEpisodesCache(SHOW)
        assertEquals(listOf("777"), repository.getEpisodesPaginated(SHOW, 20, 0, "newest").episodes.map { it.id })
        assertTrue(seen.last().startsWith("/episodes?"))
    }

    @Test
    fun withTheFlagOnThePageShowsTheStoredCopyAndPagesStayStableWhileItGrows() = runBlocking {
        val repository = repository()
        assertEquals(ShowPageSource.FEED, repository.showPageSource(SHOW))
        assertFalse(requireNotNull(repository.openFeedShowPage(SHOW, META)).cached)

        val newest = repository.readShowPageFeed(SHOW, META, Scope.NEWEST, savedFeedUrl = null)
        assertEquals(ShowPageFeedRead(ShowPageFeedResult.UPDATED, archiveComplete = false, hasCopy = true), newest)

        val shown = mutableListOf<String>()
        var offset = 0
        repeat(5) {
            val page = repository.getShowPageEpisodes(SHOW, 20, offset, "newest")
            assertTrue(page.hasMore)
            shown += page.episodes.map { it.title }
            offset += page.sourceCount
        }
        val pastCopy = repository.getShowPageEpisodes(SHOW, 20, offset, "newest")
        assertTrue("the copy ran out, but the archive isn't read yet", pastCopy.episodes.isEmpty() && pastCopy.hasMore)

        assertEquals(ShowPageFeedResult.UPDATED, repository.readShowPageFeed(SHOW, META, Scope.ARCHIVE, null).result)
        while (true) {
            val page = repository.getShowPageEpisodes(SHOW, 20, offset, "newest")
            shown += page.episodes.map { it.title }
            offset += page.sourceCount
            if (!page.hasMore) break
        }
        assertEquals("every episode once, in order, across the growth", (0 until 150).map { "Episode $it" }, shown)
        assertTrue("no network page was read", seen.none { it.startsWith("/v2/episodes") || it.startsWith("/episodes") })
    }

    @Test
    fun searchRunsOnThePhoneAndNeverCallsV1EpisodeSearch() = runBlocking {
        feedFirst = false
        val offRepository = repository()
        assertEquals(listOf("Moon g2"), offRepository.searchEpisodes(SHOW, "moon").map { it.title })
        assertTrue(seen.single().startsWith("/v2/episodes?id=$SHOW&limit=1000"))

        feedFirst = true
        feedItems = 30
        val onRepository = repository()
        onRepository.readShowPageFeed(SHOW, META, Scope.ARCHIVE, null)
        seen.clear()
        assertEquals(listOf("Episode 12"), onRepository.searchEpisodes(SHOW, "Episode 12").map { it.title })
        assertTrue(seen.isEmpty())
        assertTrue(seen.none { it.startsWith("/episodes/search") })
    }

    @Test
    fun aFeedThatCantBeReadSwitchesThePageToTheServersList() = runBlocking {
        val repository = repository()
        answers["/v2/podcast"] = { json("""{"status":"true","fetchUrl":"https://feeds.example/gone.xml","fetchVia":"direct"}""") }

        val read = repository.readShowPageFeed(SHOW, META, Scope.NEWEST, null)

        assertEquals(ShowPageFeedResult.FALLBACK, read.result)
        assertEquals(ShowPageSource.SERVER_LIST, repository.showPageSource(SHOW))
        assertTrue("still a feed-first page: no Missing episodes? chip", repository.isFeedFirstShowPage(SHOW))
        assertEquals(3, repository.getShowPageEpisodes(SHOW, 20, 0, "newest").episodes.size)
        assertTrue(seen.any { it.startsWith("/v2/feed-report") })

        // Opening the page again tries the feed again.
        requireNotNull(repository.openFeedShowPage(SHOW, META))
        assertEquals(ShowPageSource.FEED, repository.showPageSource(SHOW))
        feedFirst = false
        assertEquals("with the flag off the page never reads the feed", null, repository.openFeedShowPage(SHOW, META))
    }

    @Test
    fun rollingTheFlagBackChangesNoStoredIdAndTheServersListMapsToTheSameOwners() = runBlocking {
        // An episode an older build stored under its Podcast Index ID while the show was followed.
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(row("5001", "g1")))
        // The show's saved row, which notification registration and the tracked-shows sync read.
        database.podcastDao().upsert(PodcastEntity(podcastId = SHOW, title = "Show", author = "", imageUrl = "", description = null, isSubscribed = false, feedUrl = SAVED_URL))
        val savedRow = database.podcastDao().getPodcast(SHOW)
        val repository = repository(RoomDiscoveryEpisodeIds { database })
        repository.readShowPageFeed(SHOW, META, Scope.ARCHIVE, null)
        val storedIds = database.localEpisodeCatalogDao().listIdentities(SHOW).associate { it.guid to it.episodeId }
        val aliases = database.episodeIdentityDao().aliasesForShow(SHOW).sortedBy { it.aliasId }
        assertEquals("5001", storedIds["g1"])
        assertEquals(recipe("g0").toString(), storedIds["g0"])

        feedFirst = false
        repository.invalidateEpisodesCache(SHOW)
        val serverList = repository.getEpisodesPaginated(SHOW, 20, 0, "newest").episodes.map { it.id }

        assertEquals(listOf(storedIds["g0"], "5001", storedIds["Moon g2"] ?: recipe("Moon g2").toString()), serverList)
        assertEquals(storedIds, database.localEpisodeCatalogDao().listIdentities(SHOW).associate { it.guid to it.episodeId })
        assertEquals(aliases, database.episodeIdentityDao().aliasesForShow(SHOW).sortedBy { it.aliasId })
        val after = requireNotNull(database.podcastDao().getPodcast(SHOW))
        // The namespace the read kept (decision D12) is the one column it may fill.
        assertEquals("no address, transport or sync state of the show changed", savedRow?.copy(epNs = after.epNs), after)
        assertTrue("no registration or address route was called", seen.none { it.startsWith("/v2/episodes/register") || it.startsWith("/v2/shows/feeds") })
    }

    @Test
    fun onlyTheShowPageReadsTheCopyAndAFollowedHttpOnlyShowKeepsReadingTheServersList() = runBlocking {
        answers["/v2/podcast"] = { fixture("fixtures/v2/podcast-proxy.json") }
        answers["/v2/feed/$PROXIED"] = { MockResponse().setHeader("ETag", "\"t1\"").setBody(feedXml(30)) }
        val repository = repository()
        // Followed, but its catalog can never be ready: the address is http-only.
        database.podcastDao().upsert(PodcastEntity(podcastId = PROXIED, title = "Proxied", author = "", imageUrl = "", description = null, isSubscribed = true))

        assertEquals(ShowPageFeedResult.UPDATED, repository.readShowPageFeed(PROXIED, META, Scope.NEWEST, null).result)
        assertEquals(ShowPageSource.FEED, repository.showPageSource(PROXIED))
        seen.clear()

        assertEquals(20, repository.getShowPageEpisodes(PROXIED, 20, 0, "newest").episodes.size)
        assertTrue("the show page reads the copy", seen.isEmpty())
        repository.getEpisodesPaginated(PROXIED, 20, 0, "newest")
        assertEquals("Smart Queue, Auto, Home and downloads read the server's list", "/v2/episodes?id=$PROXIED&limit=20&offset=0&sort=newest", seen.single())

        // The next read sends the copy's ETag; the 304 keeps it.
        answers["/v2/feed/$PROXIED"] = { MockResponse().setResponseCode(304) }
        assertEquals(ShowPageFeedResult.UNCHANGED, repository.readShowPageFeed(PROXIED, META, Scope.ARCHIVE, null).result)
        assertEquals(listOf("\"t1\""), ifNoneMatch.toList())
    }

    @Test
    fun anOldestFirstPageOfAPartialCopyComesFromTheServersList() = runBlocking {
        val repository = repository()
        repository.readShowPageFeed(SHOW, META, Scope.NEWEST, null)
        seen.clear()

        repository.getShowPageEpisodes(SHOW, 50, 0, "oldest")

        assertEquals("/v2/episodes?id=$SHOW&limit=50&offset=0&sort=oldest", seen.single())
    }

    @Test
    fun playbackWindowsListTheEpisodesUnderTheIdsTheShowPageShowed() = runBlocking {
        val repository = repository()
        repository.readShowPageFeed(SHOW, META, Scope.ARCHIVE, null)
        val playing = recipe("g1").toString()
        val next = recipe("g0").toString()

        val fromCopy = repository.getEpisodeWindow(SHOW, aroundEpisodeId = playing).map { it.id }
        assertTrue(playing in fromCopy && next in fromCopy)
        assertTrue("no v1 window", seen.none { it.startsWith("/episodes") })

        feedFirst = false
        val fromServerList = repository.getEpisodeWindow(SHOW, aroundEpisodeId = playing).map { it.id }
        assertTrue(playing in fromServerList && next in fromServerList)
        assertEquals(listOf(next, playing, recipe("Moon g2").toString()), repository.getEpisodes(SHOW).map { it.id })
        assertTrue("v1 /episodes only when /v2/episodes fails", seen.none { it.startsWith("/episodes") })
    }

    @Test
    fun recordedAnswersOfTheV2RoutesReadAsTheRoutesSendThem() = runBlocking {
        feedFirst = false
        answers["/v2/episodes"] = { fixture("fixtures/v2/episodes.json") }
        val page = repository().getEpisodesPaginated(SHOW, 20, 0, "newest")

        assertEquals(listOf("5626175219222083", "7579913942623881"), page.episodes.map { it.id })
        assertEquals(5626175219222083L, page.episodes.first().recipeId)

        feedFirst = true
        answers["/v2/podcast"] = { fixture("fixtures/v2/podcast-direct.json") }
        assertEquals(ShowPageFeedResult.UPDATED, repository().readShowPageFeed(SHOW, META, Scope.NEWEST, null).result)

        assertEquals(
            ShowPageFeedPort.Transported.ReadDirect(RECORDED_FEED_URL),
            transportFailure(404, fixtureText("fixtures/v2/feed-not-proxied.json")),
        )
        assertEquals(ShowPageFeedPort.Transported.Failed("http_503"), transportFailure(503, "{\"error\":\"feed_unavailable\"}"))
    }

    @Test
    fun droppingAResolvedShowsPagesAlsoDropsThePagesStoredUnderItsShowId() = runBlocking {
        feedFirst = false
        answers["/podcast"] = { json("""{"status":"true","feed":{"id":$SHOW,"title":"Show","url":"$FEED_URL"}}""") }
        val repository = repository()
        val byUrl = "url:" + java.net.URLEncoder.encode(FEED_URL, "UTF-8")

        repository.getEpisodesPaginated(byUrl, 20, 0, "newest")
        repository.getEpisodesPaginated(byUrl, 20, 0, "newest")
        assertEquals("the second page came from the cache", 1, seen.count { it.startsWith("/v2/episodes") })
        repository.invalidateEpisodesCache(byUrl)
        repository.getEpisodesPaginated(byUrl, 20, 0, "newest")
        assertEquals(2, seen.count { it.startsWith("/v2/episodes") })
    }

    private fun repository(ids: DiscoveryEpisodeIds = DiscoveryEpisodeIds.Unmapped): PodcastRepository {
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        val catalog = LocalEpisodeCatalogRepository.create(database, feedClient())
        return PodcastRepository(
            baseUrl = base,
            publicKey = "test-app-key",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            localEpisodeCatalog = catalog,
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
            rolloutSwitches = RolloutSwitches(feedFirst = { feedFirst }),
            discoveryEpisodeIds = ids,
            feedFirstWiring = FeedFirstWiring(copies = catalog.showPages),
        )
    }

    /** Publisher feeds over an interceptor: [FEED_URL] lists [feedItems] episodes, newest first; any other address is gone. */
    private fun feedClient() = RssFeedClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            val url = chain.request().url.toString()
            val code = if (url == FEED_URL || url == RECORDED_FEED_URL) 200 else 404
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .body(feedXml(feedItems).toResponseBody("application/rss+xml".toMediaType())).build()
        }.build(),
    )

    private fun row(id: String, key: String) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = SHOW,
        guid = key,
        title = "Episode 1",
        description = "",
        audioUrl = "https://cdn.example/$key.mp3",
        imageUrl = null,
        duration = 60,
        publishedDate = 1_790_000_000_000L - 86_400_000L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = "audio/mpeg",
    )

    private class FakeIds(private val owners: Map<Long, String>) : DiscoveryEpisodeIds {
        override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> = DiscoveryEpisodeIds.ownRecipeIds(episodeIds)

        override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, DiscoveryEpisodeOwner> =
            refs.mapNotNull { ref -> owners[ref.recipeId]?.let { ref to DiscoveryEpisodeOwner(it, ref.podcastId) } }.toMap()
    }

    private companion object {
        const val SHOW = "920666"
        const val FEED_URL = "https://feeds.example/920666.xml"
        const val SAVED_URL = "https://saved.example/920666.xml"

        /** The address `fixtures/v2/podcast-direct.json` names. */
        const val RECORDED_FEED_URL = "https://feeds.podcastindex.org/pc20.xml"

        /** The http-only show of `fixtures/v2/podcast-proxy.json`. */
        const val PROXIED = "1031614"

        fun fixtureText(path: String): String = requireNotNull(PodcastRepositoryShowPagesTest::class.java.classLoader?.getResource(path)) { path }.readText()

        fun fixture(path: String) = json(fixtureText(path))
        val META = PodcastMeta(title = "Show")

        fun recipe(key: String): Long = EpisodeRecipeId.forNamespace(SHOW.toLong(), key)

        fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)

        /** A `/v2/episodes` answer naming [keys] under their recipe IDs, as the route re-keys Podcast Index's list. */
        fun v2Episodes(keys: List<String>): String {
            val items =
                keys.mapIndexed { index, key ->
                    """{"id":${recipe(key)},"title":"$key","guid":"$key","catalogKey":"$key","enclosureUrl":"https://cdn.example/$index.mp3",""" +
                        """"feedId":$SHOW,"datePublished":${1_790_000_000L - index * 86_400L}}"""
                }
            return """{"status":"true","fallback":true,"source":"pi","feedId":$SHOW,"items":[${items.joinToString(",")}],"hasMore":false,"offset":0,"limit":20}"""
        }

        fun feedXml(count: Int): String {
            val items =
                (0 until count).joinToString("") { index ->
                    val key = if (index == 2) "Moon g2" else "g$index"
                    val date = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(1_790_000_000L - index * 86_400L).atZone(ZoneOffset.UTC))
                    "<item><title>Episode $index</title><guid>$key</guid><pubDate>$date</pubDate>" +
                        "<enclosure url=\"https://cdn.example/$key.mp3\" type=\"audio/mpeg\" length=\"1\"/></item>"
                }
            return "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Show</title>$items</channel></rss>"
        }
    }
}
