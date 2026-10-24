package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** [PodcastRepository] owns the `/v2` client: same base URL and one shared HTTP cache with v1. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastRepositoryApiV2Test {
    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun productionClientsShareOneCacheAndBaseUrl() {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val repository = repository(context)
        repeat(2) { server.enqueue(json("""{"status":"true","feed":{"id":920666,"title":"Show"}}""")) }

        repository.api.getPodcast("unused", "920666").execute()
        repository.apiV2.getPodcast(920666L).execute()

        assertEquals("/podcast?id=920666", server.takeRequest().path)
        assertEquals("/v2/podcast?id=920666", server.takeRequest().path)
        // One okhttp3.Cache for both versions: two caches must never share a directory.
        verify(context, times(1)).cacheDir
    }

    @Test
    fun testOverridesBuildNoCache() {
        val context = spy(ApplicationProvider.getApplicationContext<Context>())
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        val repository =
            repository(
                context,
                v1 = NetworkModule.createBoxLoreApi(base, client),
                v2 = NetworkModule.createBoxLoreApiV2(base, client),
            )
        server.enqueue(json("""{"registryAvailable":true,"results":[]}"""))

        repository.apiV2
            .getShowFeeds(
                cx.aswin.boxlore.core.network.model
                    .V2ShowsFeedsRequest(listOf(1L)),
            ).execute()

        assertEquals("/v2/shows/feeds", server.takeRequest().path)
        verify(context, never()).cacheDir
    }

    private fun repository(
        context: Context,
        v1: cx.aswin.boxlore.core.network.BoxLoreApi? = null,
        v2: cx.aswin.boxlore.core.network.BoxLoreApiV2? = null,
    ): PodcastRepository {
        val database = mock(BoxLoreDatabase::class.java)
        `when`(database.podcastDao()).thenReturn(mock(PodcastDao::class.java))
        `when`(database.rssEpisodeDao()).thenReturn(mock(RssEpisodeDao::class.java))
        return PodcastRepository(
            baseUrl = server.url("/").toString(),
            publicKey = "unused",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(ApplicationProvider.getApplicationContext(), database),
            boxLoreApi = v1,
            boxLoreApiV2 = v2,
        )
    }

    private fun json(body: String) = MockResponse().setHeader("Content-Type", "application/json").setBody(body)
}
