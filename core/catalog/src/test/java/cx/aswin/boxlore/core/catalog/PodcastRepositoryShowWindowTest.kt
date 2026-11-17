package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A show nobody follows, on the server's list (`/v2/episodes`, `feed_first` off), longer than the
 * newest few hundred: same-show continuation reads from the listener's place in the asked sort, and
 * in-show search reads the whole list, says so when it can't, and keeps long lists out of the
 * shared page cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastRepositoryShowWindowTest {
    private lateinit var server: MockWebServer
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private val requests = CopyOnWriteArrayList<RecordedRequest>()

    /** Episodes 1..[showSize], episode 1 the oldest. */
    private var showSize = 1_000

    /** `/v2/episodes` offsets that answer 503. */
    private val failingOffsets = mutableSetOf<Int>()

    /** True when v1 `/episodes` answers (with served IDs), as it may after `/v2` fails. */
    private var v1Answers = false

    /** The show's cached feed extras. */
    private var supplements: List<Episode> = emptyList()

    /** A `/v2/episodes` offset whose answer waits for [release]. */
    @Volatile private var heldOffset: Int? = null

    /** The catalog caches' clock ([CatalogCacheClock]). */
    @Volatile private var now = 1_700_000_000_000L
    private val held = CountDownLatch(1)
    private val release = CountDownLatch(1)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        clearCaches()
        CatalogCacheClock.timeProvider = { now }
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    requests += request
                    val url = requireNotNull(request.requestUrl)
                    if (url.encodedPath == "/episodes" && v1Answers) return json(v1Page())
                    if (url.encodedPath != "/v2/episodes") return MockResponse().setResponseCode(404)
                    val offset = url.queryParameter("offset")?.toInt() ?: 0
                    if (offset in failingOffsets) return MockResponse().setResponseCode(503)
                    if (offset == heldOffset) {
                        held.countDown()
                        release.await(5, TimeUnit.SECONDS)
                    }
                    return json(page(url.queryParameter("limit")?.toInt() ?: 100, offset, url.queryParameter("sort") ?: "newest"))
                }
            }
        server.start()
    }

    @After
    fun tearDown() {
        release.countDown()
        server.shutdown()
        database.close()
        RssPodcastRepository.clearInstanceForTests()
        clearCaches()
        CatalogCacheClock.timeProvider = System::currentTimeMillis
    }

    @Test
    fun continuingFromEpisode500Of1000OldestFirstOffers501Next() = runBlocking {
        // Reported: continuation read only the newest 200, so from an older episode it jumped hundreds ahead.
        val window = repository().getEpisodeWindow(SHOW, aroundEpisodeId = id(500), sort = "oldest")

        assertEquals(id(500), window.first().id)
        assertEquals(id(501), window[1].id)
        assertEquals((500..699).map(::id), window.map { it.id })
        assertTrue("every page in the asked sort", episodeRequests().all { it.requestUrl?.queryParameter("sort") == "oldest" })
    }

    @Test
    fun continuingNewestFirstRunsForwardFromThePlaceToo() = runBlocking {
        val window = repository().getEpisodeWindow(SHOW, aroundEpisodeId = id(300), sort = "newest")

        assertEquals((300..499).map(::id), window.map { it.id })
        assertTrue(episodeRequests().all { it.requestUrl?.queryParameter("sort") == "newest" })
    }

    @Test
    fun anEpisodeTheListDoesntHoldGivesAnEmptyWindowInsteadOfTheNewest() = runBlocking {
        val repository = repository()

        assertEquals(emptyList<String>(), repository.getEpisodeWindow(SHOW, aroundEpisodeId = "404", sort = "oldest").map { it.id })
        val read = episodeRequests().size
        assertEquals(emptyList<String>(), repository.getEpisodeWindow(SHOW, aroundEpisodeId = "404", sort = "oldest").map { it.id })
        assertEquals("the finished search is kept for the next playback start", read, episodeRequests().size)
    }

    @Test
    fun aPageThatFailsGivesNoWindowAndIsTriedAgainNextTime() = runBlocking {
        showSize = 3_000
        failingOffsets += 1_200
        val repository = repository()

        assertEquals(emptyList<String>(), repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_500), sort = "oldest").map { it.id })
        failingOffsets.clear()
        assertEquals(id(2_500), repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_500), sort = "oldest").first().id)
    }

    @Test
    fun searchFindsAnEpisodeOlderThanTheNewest1000AndKeepsTheListOutOfThePageCache() = runBlocking {
        // Reported: search read one page of the newest 1,000, so older episodes were "no matches".
        showSize = 2_500
        val results = repository().searchEpisodes(SHOW, "moon")

        assertEquals(listOf(id(MOON)), results.map { it.id })
        assertEquals("the older match comes back whole", MOON_NOTES, results.single().description)
        // The list once: the older match is kept whole as its page is read, never read again.
        assertEquals(listOf("0", "1000", "2000"), episodeRequests().map { it.requestUrl?.queryParameter("offset") })
        assertTrue(episodeRequests().all { it.requestUrl?.queryParameter("limit") == "1000" })
        assertEquals("no long list in the shared page cache", 0, PodcastRepository.episodesCache.size())
        assertEquals(1, PodcastRepository.showSearchCache.size())
    }

    @Test
    fun searchKeepsOnlyTheNewest1000RowsWholeAndTheRestSlim() = runBlocking {
        // Reviewed: a whole list of full episodes, notes and all, stayed in memory for every searched show.
        showSize = 2_500
        val repository = repository()
        repository.searchEpisodes(SHOW, "moon")

        val scan = checkNotNull(PodcastRepository.showSearchCache[SHOW])
        assertEquals(SEARCH_FULL_ROWS, scan.full.size)
        assertEquals(1_500, scan.slim.size)
        assertTrue("slim rows hold no full notes", scan.slim.all { it.key.length <= "Episode 2500".length + 1 + SEARCH_SLIM_NOTES_CHARS })

        // Another search of the same show reads nothing again for a newer match, nor for an older one already found.
        val read = episodeRequests().size
        assertEquals(listOf(id(2_400)), repository.searchEpisodes(SHOW, "Episode 2400").map { it.id })
        assertEquals(read, episodeRequests().size)
        assertEquals(listOf(id(MOON)), repository.searchEpisodes(SHOW, "landing").map { it.id })
        assertEquals(read, episodeRequests().size)
    }

    @Test
    fun typingOnInASearchWhoseMatchesAreAllOlderReadsNothingAgain() = runBlocking {
        // Reviewed: every keystroke read again each older page holding a match, past the route's burst limit.
        showSize = 2_500
        val repository = repository()
        assertEquals(listOf(id(MOON)), repository.searchEpisodes(SHOW, "moon").map { it.id })
        requests.clear()

        val narrowed = repository.searchEpisodes(SHOW, "moon l")
        assertEquals(listOf(id(MOON)), repository.searchEpisodes(SHOW, "moon la").map { it.id })
        assertEquals(listOf(id(MOON)), narrowed.map { it.id })
        assertEquals(MOON_NOTES, narrowed.single().description)
        assertEquals("no request while typing on", 0, episodeRequests().size)

        // A new older match on a page not read again yet costs that page only.
        assertEquals(listOf(id(1_234)), repository.searchEpisodes(SHOW, "Episode 1234").map { it.id })
        assertEquals(listOf("1000"), episodeRequests().map { it.requestUrl?.queryParameter("offset") })
    }

    @Test
    fun searchingAnotherShowDropsTheFirstShowsList() = runBlocking {
        val repository = repository()
        repository.searchEpisodes(SHOW, "moon")
        repository.searchEpisodes(OTHER_SHOW, "moon")

        assertEquals(1, PodcastRepository.showSearchCache.size())
        assertEquals(null, PodcastRepository.showSearchCache[SHOW])
    }

    @Test
    fun aSearchOfAListPastTheRoutesLastOffsetSaysHowFarItSearched() = runBlocking {
        // Reviewed: a list cut at the route's last offset was answered as if it were the whole show.
        showSize = 12_000
        val search = repository().searchShowEpisodes(SHOW, "moon")

        assertEquals(emptyList<String>(), search.episodes.map { it.id })
        assertEquals(11_000, search.searchedNewest)
        assertEquals((0..10_000 step 1_000).map(Int::toString), episodeRequests().map { it.requestUrl?.queryParameter("offset") })
    }

    @Test
    fun aSearchOfTheWholeListSaysNothingAboutHowFar() = runBlocking {
        showSize = 2_500
        assertEquals(null, repository().searchShowEpisodes(SHOW, "moon").searchedNewest)
    }

    @Test
    fun aSearchCancelledByTheNextKeystrokeCarriesOnWhereItStopped() = runBlocking {
        // Reviewed: every debounced keystroke cancelled the read and the next one started again from offset 0.
        showSize = 2_500
        heldOffset = 2_000
        val repository = repository()
        val first = launch(Dispatchers.IO) { repository.searchEpisodes(SHOW, "moo") }
        assertTrue(held.await(5, TimeUnit.SECONDS))
        first.cancelAndJoin()
        heldOffset = null
        release.countDown()
        requests.clear()

        assertEquals(listOf(id(MOON)), repository.searchEpisodes(SHOW, "moon").map { it.id })
        assertEquals(listOf("2000"), episodeRequests().map { it.requestUrl?.queryParameter("offset") })
    }

    @Test
    fun aSearchWhoseNewestPageHoldsEnoughMatchesReadsNoOlderPage() = runBlocking {
        // Reviewed: the first search read every page of a long show before matching, though the newest 1,000 already answered it.
        showSize = 10_000
        val repository = repository()

        assertEquals(MAX_SHOW_SEARCH_RESULTS, repository.searchEpisodes(SHOW, "Episode").size)
        assertEquals(listOf("0"), episodeRequests().map { it.requestUrl?.queryParameter("offset") })

        // A narrower query that needs older rows carries on from there and reads each page once.
        requests.clear()
        assertEquals(listOf(id(1_234)), repository.searchEpisodes(SHOW, "Episode 1234").map { it.id })
        assertEquals((1_000 until 10_000 step 1_000).map(Int::toString), episodeRequests().map { it.requestUrl?.queryParameter("offset") })
    }

    @Test
    fun aSearchThatCantReadTheListFailsInsteadOfFindingNothingAndRetries() = runBlocking {
        showSize = 2_500
        failingOffsets += 1_000
        val repository = repository()

        try {
            repository.searchEpisodes(SHOW, "moon")
            fail("a failed read must not answer no matches")
        } catch (_: IOException) {
            // The page shows Try again.
        }
        failingOffsets.clear()
        requests.clear()
        assertEquals(listOf(id(MOON)), repository.searchEpisodes(SHOW, "moon").map { it.id })
        assertEquals("Try again carries on from the failed page", "1000", episodeRequests().first().requestUrl?.queryParameter("offset"))
    }

    @Test
    fun continuationOnAListPastTheRoutesLastOffsetEndsEmptyAndIsKept() = runBlocking {
        // Reviewed: reaching the route's last offset counted as a failed page, so every track change read 11 pages again.
        showSize = 12_000
        val repository = repository()

        assertEquals(emptyList<String>(), repository.getEpisodeWindow(SHOW, aroundEpisodeId = "404", sort = "oldest").map { it.id })
        assertEquals("the route's last page is read too", "10000", episodeRequests().last().requestUrl?.queryParameter("offset"))
        val read = episodeRequests().size
        assertEquals(emptyList<String>(), repository.getEpisodeWindow(SHOW, aroundEpisodeId = "404", sort = "oldest").map { it.id })
        assertEquals("the empty window is kept", read, episodeRequests().size)
    }

    @Test
    fun continuationFindsAnEpisodeOnTheRoutesLastPage() = runBlocking {
        // Reviewed: pages after the first 200 rows stepped from 9,200 to 10,200, past the last offset, so rows 10,200-10,999 were never read.
        showSize = 12_000
        // Oldest first, row 10,500 is episode 10,501.
        val window = repository().getEpisodeWindow(SHOW, aroundEpisodeId = id(10_501), sort = "oldest")

        assertEquals((10_501..10_700).map(::id), window.map { it.id })
        assertEquals("10000", episodeRequests().last().requestUrl?.queryParameter("offset"))
    }

    @Test
    fun oldestFirstCaughtUpToTheListsNewestStillOffersTheFeedsNewerEpisodes() = runBlocking {
        // Reviewed: oldest first, the feed's extras joined the page of the oldest rows, so the end of a long show offered nothing after it.
        showSize = 3_000
        supplements = listOf(extra("-77", 3_001), extra("-78", 5))
        val repository = repository()

        assertEquals(listOf(id(2_999), id(3_000), "-77"), repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_999), sort = "oldest").map { it.id })
        assertEquals("no extra among the oldest rows", (100..299).map(::id), repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(100), sort = "oldest").map { it.id })
    }

    @Test
    fun aFailedPageAfterTheEpisodeGivesNoWindowAndIsntKept() = runBlocking {
        // Reviewed: the failed next page read as the show's end, so a one-episode window was kept and hinted.
        showSize = 3_000
        failingOffsets += 1_200
        val repository = repository()
        // Oldest first, episode 1,200 is row 1,199: the last row of the page read at 200.
        assertEquals(emptyList<String>(), repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(1_200), sort = "oldest").map { it.id })
        assertEquals(null, PodcastRepository.episodesCache["$SHOW|v2around|oldest|${id(1_200)}"])

        failingOffsets.clear()
        assertEquals((1_200..1_399).map(::id), repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(1_200), sort = "oldest").map { it.id })
    }

    @Test
    fun theNextEpisodeAfterAnHourLongOneStillStartsNearItsPlace() = runBlocking {
        // Reviewed: the remembered row lasted 5 minutes, so after a longer episode the next one read from offset 0 again.
        showSize = 3_000
        val repository = repository()
        repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_500), sort = "oldest")
        now += 40 * 60 * 1_000L
        requests.clear()

        val next = repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_501), sort = "oldest")
        assertEquals((2_501..2_700).map(::id), next.map { it.id })
        assertEquals("one request near its place", listOf("2099"), episodeRequests().map { it.requestUrl?.queryParameter("offset") })
    }

    @Test
    fun anEpisodeFarFromTheLastFoundRowReadsTwoPagesThereThenStartsAgain() = runBlocking {
        // Reviewed: from a far remembered row the search read to the end of the list before starting again.
        showSize = 9_000
        val repository = repository()
        // Newest first, row 6,000 is episode 3,000 and row 1,000 episode 8,000; oldest first, rows are episode - 1.
        for ((sort, last, picked) in listOf(Triple("newest", 3_000, 8_000), Triple("oldest", 6_001, 1_001))) {
            repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(last), sort = sort)
            requests.clear()

            assertEquals(id(picked), repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(picked), sort = sort).first().id)
            assertEquals(sort, listOf("5600", "6600", "200"), episodeRequests().map { it.requestUrl?.queryParameter("offset") })
        }
    }

    @Test
    fun theNextEpisodesWindowStartsNearTheLastOneInBothSorts() = runBlocking {
        // Reviewed: every track change read the list from offset 0 again, a thousand rows a page.
        showSize = 3_000
        val repository = repository()

        repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_500), sort = "oldest")
        requests.clear()
        val next = repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_501), sort = "oldest")
        assertEquals((2_501..2_700).map(::id), next.map { it.id })
        assertEquals("one request for the next episode", 1, episodeRequests().size)

        repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(1_500), sort = "newest")
        requests.clear()
        val newer = repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(1_501), sort = "newest")
        assertEquals((1_501..1_700).map(::id), newer.map { it.id })
        assertEquals(1, episodeRequests().size)
    }

    @Test
    fun anEpisodeBeforeTheLastFoundRowIsStillFound() = runBlocking {
        showSize = 3_000
        val repository = repository()
        repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_500), sort = "oldest")

        assertEquals(id(10), repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(10), sort = "oldest").first().id)
    }

    @Test
    fun twoCallersAskingForOneWindowAtOnceShareOneRead() = runBlocking {
        // Reviewed: the continuation banner and Smart Queue each read the same pages at the same time.
        showSize = 3_000
        val repository = repository()
        val windows =
            listOf(
                async(Dispatchers.IO) { repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_500), sort = "oldest") },
                async(Dispatchers.IO) { repository.getEpisodeWindow(SHOW, aroundEpisodeId = id(2_500), sort = "oldest") },
            ).awaitAll()

        assertEquals(windows[0], windows[1])
        assertEquals("one read: 0, 200, 1200, 2200", 4, episodeRequests().size)
    }

    @Test
    fun theWholeShowReadThrowsOnAFailedPageAndNeverFallsBackToV1() = runBlocking {
        // Reviewed: a failed /v2 page fell back to v1, whose failure ended the list early, so Mark all marked part of the show.
        showSize = 3_500
        failingOffsets += 2_000
        v1Answers = true
        val repository = repository()

        assertEquals(1_000, repository.getWholeShowPage(SHOW, 0, "newest").sourceCount)
        try {
            repository.getWholeShowPage(SHOW, 2_000, "newest")
            fail("a failed page must not end the whole-show read")
        } catch (_: IOException) {
            // Mark all writes nothing and says so.
        }
        assertTrue("never v1 pages, whose IDs the page doesn't use", requests.none { it.requestUrl?.encodedPath == "/episodes" })
        assertEquals("no long pages in the shared page cache", 0, PodcastRepository.episodesCache.size())
    }

    @Test
    fun theWholeShowReadReadsEveryEpisodeAThousandAtATime() = runBlocking {
        showSize = 3_500
        val repository = repository()
        val ids = mutableSetOf<String>()
        var offset = 0
        do {
            val page = repository.getWholeShowPage(SHOW, offset, "newest")
            page.episodes.forEach { ids += it.id }
            offset += page.sourceCount
        } while (page.hasMore)

        assertEquals((1..3_500).map(::id).toSet(), ids)
        assertEquals(4, episodeRequests().size)
    }

    @Test
    fun theWholeShowReadOfAListPastTheRoutesLastOffsetThrows() = runBlocking {
        showSize = 12_000
        try {
            repository().getWholeShowPage(SHOW, 11_000, "newest")
            fail("the read can't reach the show's end, so it isn't the whole show")
        } catch (_: ShowListTooLongException) {
            // Mark all writes nothing and says the show is too long, not to check the connection.
        }
    }

    @Test
    fun droppingTheShowsPagesAlsoDropsItsSearchList() = runBlocking {
        val repository = repository()
        repository.searchEpisodes(SHOW, "moon")
        repository.invalidateEpisodesCache(SHOW)
        repository.searchEpisodes(SHOW, "moon")

        assertEquals(2, episodeRequests().size)
    }

    private fun episodeRequests() = requests.filter { it.requestUrl?.encodedPath == "/v2/episodes" }

    private fun clearCaches() {
        PodcastRepository.episodesCache.clear()
        PodcastRepository.showSearchCache.clear()
        PodcastRepository.showSearchMatches.clear()
        PodcastRepository.windowScanHints.clear()
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
            episodeSupplementRepository = CachedExtras(),
        )
    }

    /** Cached feed extras only: [supplements] for the show, nothing else. */
    private inner class CachedExtras : EpisodeSupplementPort {
        override suspend fun refreshFromFeed(
            podcastIndexId: String,
            feedUrl: String,
            baselineEpisodes: List<Episode>,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): EpisodeSupplementOutcome = EpisodeSupplementOutcome.NoDisconnect

        override suspend fun optInFromFeedIfDisconnected(
            podcastIndexId: String,
            feedUrl: String,
            baselineEpisodes: List<Episode>,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): EpisodeSupplementOutcome = EpisodeSupplementOutcome.NoDisconnect

        override suspend fun hasDirectFeedOptIn(podcastIndexId: String): Boolean = false

        override suspend fun listOptedInPodcastIds(): Set<String> = emptySet()

        override suspend fun resolveNewestTipFromFeed(request: EpisodeSupplementPort.NewestTipRequest): Episode? = null

        override suspend fun getEpisodesForPodcast(
            podcastIndexId: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): List<Episode> = if (podcastIndexId == SHOW) supplements else emptyList()

        override suspend fun getEpisode(
            episodeId: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): Episode? = null

        override suspend fun search(
            podcastIndexId: String,
            query: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): List<Episode> = emptyList()
    }

    /** The show's `/v2/episodes` page in [sort], as the route answers it. */
    private fun page(limit: Int, offset: Int, sort: String): String {
        val all = (1..showSize).toList().let { if (sort == "oldest") it else it.reversed() }
        val slice = all.drop(offset).take(limit)
        val items =
            slice.joinToString(",") { n ->
                val title = if (n == MOON) "Moon landing" else "Episode $n"
                val notes = if (n == MOON) MOON_NOTES else ""
                """{"id":${id(n)},"title":"$title","description":"$notes","guid":"k$n","catalogKey":"k$n","enclosureUrl":"https://cdn.example/$n.mp3",""" +
                    """"feedId":$SHOW,"datePublished":${1_700_000_000L + n * 86_400L}}"""
            }
        val hasMore = offset + slice.size < all.size
        return """{"status":"true","feedId":$SHOW,"items":[$items],"hasMore":$hasMore,"offset":$offset,"limit":$limit}"""
    }

    /** v1 `/episodes`: one page under served IDs, which the show page and history don't use. */
    private fun v1Page(): String =
        """{"status":"true","items":[{"id":777001,"title":"Served","enclosureUrl":"https://cdn.example/s.mp3","feedId":$SHOW,"datePublished":1700000000}],"hasMore":false}"""

    private companion object {
        const val SHOW = "920666"
        const val OTHER_SHOW = "920667"

        /** The moon episode's notes: long enough that a slim row keeps only their start. */
        val MOON_NOTES = "Notes ".repeat(200)

        /** The one episode titled "Moon landing": older than the newest 1,000 of a 2,500-episode show. */
        const val MOON = 30

        fun id(n: Int): String = EpisodeRecipeId.forNamespace(SHOW.toLong(), "k$n").toString()

        /** A feed-only extra published as episode [n] of the list would be. */
        fun extra(extraId: String, n: Int): Episode = TestFixtures.episode(
            id = extraId,
            title = "Feed only $n",
            podcastId = SHOW,
            audioUrl = "https://cdn.example/feed-only-$n.mp3",
            publishedDate = 1_700_000_000L + n * 86_400L,
        )

        fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
    }
}
