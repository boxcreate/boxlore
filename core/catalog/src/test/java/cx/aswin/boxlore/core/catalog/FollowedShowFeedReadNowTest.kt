package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
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
 * [FollowedShowFeedRead.readNow], the call Home's show chip makes, through a real
 * [PodcastRepository] and [SubscriptionRepository] on in-memory Room.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FollowedShowFeedReadNowTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var server: MockWebServer
    private val seen = CopyOnWriteArrayList<String>()
    private val stored = TestFixtures.episode(id = "yesterday", podcastId = SHOW, publishedDate = 1_000L)
    private val fresh = TestFixtures.episode(id = "today", podcastId = SHOW, publishedDate = 2_000L)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        PodcastRepository.episodesCache.clear()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    seen += request.path.orEmpty()
                    return MockResponse().setResponseCode(404)
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
    fun aChipReadReadsTheFeedInsideItsSixHourWaitAndSavesItsNewestEpisode() = runBlocking {
        // Before, the chip ran the automatic read: a feed read three hours ago was "unchanged", so
        // the show kept yesterday's episode. The chip's read is manual and reads the feed anyway.
        val catalog =
            FakeLocalEpisodeCatalogPort(episodes = mutableMapOf(stored.id to stored)).apply {
                publisherFeedUnchanged = true
                refreshOutcome = LocalEpisodeCatalogPort.RefreshOutcome.Success(fresh, itemCount = 2, ready = true)
            }
        val podcastDao = database.podcastDao()
        podcastDao.upsert(
            PodcastEntity(
                podcastId = SHOW,
                title = "The Daily",
                author = "Host",
                imageUrl = "https://img.example/daily.jpg",
                description = null,
                isSubscribed = true,
                subscribedAt = 1L,
                feedUrl = FEED,
                latestEpisode = stored,
            ),
        )

        FollowedShowFeedRead.readNow(podcastRepository(catalog), SubscriptionRepository(podcastDao), SHOW)

        assertEquals(1, catalog.refreshCalls)
        assertEquals(LocalEpisodeCatalogPort.RefreshReason.MANUAL, catalog.lastRefreshRequest?.reason)
        assertEquals(FEED, catalog.lastRefreshRequest?.feedUrl)
        assertEquals("today", podcastDao.getPodcast(SHOW)?.latestEpisode?.id)
        assertTrue("the feed answered, so nothing asked the server: $seen", seen.isEmpty())
    }

    private fun podcastRepository(catalog: LocalEpisodeCatalogPort): PodcastRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        return PodcastRepository(
            baseUrl = base,
            publicKey = "unused",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            localEpisodeCatalog = catalog,
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
        )
    }

    private companion object {
        const val SHOW = "7001"
        const val FEED = "https://feeds.example/daily.xml"
    }
}
