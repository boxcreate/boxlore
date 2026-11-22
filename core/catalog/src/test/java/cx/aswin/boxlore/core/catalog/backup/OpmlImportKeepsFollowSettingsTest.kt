package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Importing an OPML file that lists a show already followed keeps that show's alerts and auto-download. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OpmlImportKeepsFollowSettingsTest {
    private lateinit var server: MockWebServer
    private lateinit var database: BoxLoreDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path.orEmpty().substringBefore('?') != "/podcast") return MockResponse().setResponseCode(404)
                    val url = URLDecoder.decode(request.requestUrl?.queryParameter("url").orEmpty(), "UTF-8")
                    val id = url.substringAfterLast('/').toLongOrNull() ?: return MockResponse().setResponseCode(404)
                    // No feed address in the answer, so the import's refresh reads nothing from the web.
                    return MockResponse()
                        .setHeader("Content-Type", "application/json")
                        .setBody("""{"status":"true","feed":{"id":$id,"title":"Show $id"}}""")
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
    fun anImportedFileWithAFollowedShowKeepsItsAlertsOn() = runBlocking {
        val autoDownloads = database.autoDownloadDao()
        val subscriptions = SubscriptionRepository(database.podcastDao(), autoDownloadDao = autoDownloads)
        database.podcastDao().upsert(followed(FOLLOWED))
        autoDownloads.changeSetting(FOLLOWED, enabled = true, nowSeconds = 100L)

        val report =
            manager(subscriptions).importOpmlFeeds(
                listOf(OpmlFeed("Show $FOLLOWED", "https://feeds.example/$FOLLOWED"), OpmlFeed("Show $NEW", "https://feeds.example/$NEW")),
            )

        assertEquals(listOf(FOLLOWED, NEW), report.imported.map { it.id })
        val kept = database.podcastDao().getPodcast(FOLLOWED)!!
        assertTrue(kept.isSubscribed)
        assertTrue("the followed show's alerts stay on", kept.notificationsEnabled)
        assertTrue("and its auto-download", kept.autoDownloadEnabled)
        assertEquals(1_000L, kept.subscribedAt)
        assertNotNull(autoDownloads.getShow(FOLLOWED))
        assertTrue(subscriptions.canPresentEpisodeNotification(FOLLOWED, null))

        val added = database.podcastDao().getPodcast(NEW)!!
        assertTrue(added.isSubscribed)
        assertFalse("a show the file adds starts with alerts off", added.notificationsEnabled)
        assertFalse(added.autoDownloadEnabled)
    }

    private fun followed(id: String) = PodcastEntity(
        podcastId = id,
        title = "Show $id",
        author = "Host",
        imageUrl = "https://example.com/$id.jpg",
        description = null,
        isSubscribed = true,
        subscribedAt = 1_000L,
        notificationsEnabled = true,
        autoDownloadEnabled = true,
    )

    private fun manager(subscriptions: SubscriptionRepository) = LibraryBackupManager(
        subscriptionRepository = subscriptions,
        listeningHistory = NoHistory,
        podcastRepository = podcastRepository(),
        context = context,
        adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java),
        rssPodcastRepository = mock(RssPodcastRepository::class.java),
        database = database,
        pendingOpmlImports = MemoryPendingOpmlImports(),
        playlistRepository = null,
    )

    private fun podcastRepository(): PodcastRepository {
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        return PodcastRepository(
            baseUrl = base,
            publicKey = "test-app-key",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
        )
    }

    private object NoHistory : ListeningHistoryBackupPort {
        override fun getRecentHistory(): Flow<List<ListeningHistoryEntity>> = emptyFlow()

        override suspend fun exportStoredHistory(): List<ListeningHistoryEntity> = emptyList()

        override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) = Unit

        override suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?) = Unit
    }

    private companion object {
        const val FOLLOWED = "42"
        const val NEW = "43"
    }
}
