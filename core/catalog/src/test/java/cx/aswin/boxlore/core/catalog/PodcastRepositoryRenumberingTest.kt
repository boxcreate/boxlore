package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Show details asked for one numeric show ID that answer as another report a renumbering before
 * the answer is returned, so every existing details call (the show page, a cloud pull's
 * enrichment, address recovery) can move a follow; answers for the same ID, an address or a
 * failing hook report nothing or still return the answer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastRepositoryRenumberingTest {
    private lateinit var server: MockWebServer
    private val reported = mutableListOf<Pair<String, String>>()

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
    fun anAnswerNamingAnotherShowIdIsReportedBeforeItIsReturned() = runBlocking {
        server.enqueue(feed(id = 7976624))

        val answer = repository().getPodcastDetails("1258562")

        assertEquals("7976624", answer?.id)
        assertEquals(listOf("1258562" to "7976624"), reported)
    }

    @Test
    fun answersForTheSameIdOrAnAddressReportNothing() = runBlocking {
        server.enqueue(feed(id = 7976624))
        server.enqueue(feed(id = 7976624))

        repository().getPodcastDetails("7976624")
        repository().getPodcastDetails("url:https%3A%2F%2Ffeeds.example%2Fshow.xml")

        assertEquals(emptyList<Pair<String, String>>(), reported)
    }

    @Test
    fun aFailingHookStillReturnsTheAnswer() = runBlocking {
        server.enqueue(feed(id = 7976624))

        val answer = repository(onRenumbered = { _, _ -> error("not saved") }).getPodcastDetails("1258562")

        assertEquals("7976624", answer?.id)
    }

    private fun repository(onRenumbered: suspend (String, Podcast) -> Unit = { old, answer -> reported += old to answer.id }): PodcastRepository {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val client = OkHttpClient.Builder().readTimeout(5, TimeUnit.SECONDS).build()
        val base = server.url("/").toString()
        val database = mock(BoxLoreDatabase::class.java)
        `when`(database.podcastDao()).thenReturn(mock(PodcastDao::class.java))
        `when`(database.rssEpisodeDao()).thenReturn(mock(RssEpisodeDao::class.java))
        return PodcastRepository(
            baseUrl = base,
            publicKey = "unused",
            context = context,
            rssRepository = RssPodcastRepository.createForTests(context, database),
            boxLoreApi = NetworkModule.createBoxLoreApi(base, client),
            boxLoreApiV2 = NetworkModule.createBoxLoreApiV2(base, client),
            onShowRenumbered = onRenumbered,
        )
    }

    private fun feed(id: Long) =
        MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""{"status":"true","feed":{"id":$id,"title":"The Desi Crime Podcast","url":"https://feeds.acast.example/desi"}}""")
}
