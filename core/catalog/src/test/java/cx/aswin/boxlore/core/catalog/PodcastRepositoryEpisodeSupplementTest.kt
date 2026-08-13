package cx.aswin.boxlore.core.catalog

import android.content.Context
import android.content.SharedPreferences
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.TimeUnit

/**
 * Hermetic [PodcastRepository] PI + cached-feed merge and opted-in `/sync` skip.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PodcastRepositoryEpisodeSupplementTest {
    private lateinit var server: MockWebServer
    private lateinit var fakePort: FakeEpisodeSupplementPort
    private lateinit var repository: PodcastRepository
    private val testDispatcher = UnconfinedTestDispatcher()

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start()
        fakePort = FakeEpisodeSupplementPort()

        val context = fakeContext()
        val database = fakeDatabase()
        val client =
            OkHttpClient
                .Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        val api = NetworkModule.createBoxLoreApi(server.url("/").toString(), client)
        val rss =
            RssPodcastRepository.createForTests(
                context = context,
                database = database,
            )

        repository =
            PodcastRepository(
                baseUrl = server.url("/").toString(),
                publicKey = APP_KEY,
                context = context,
                rssRepository = rss,
                episodeSupplementRepository = fakePort,
                ioDispatcher = testDispatcher,
                boxLoreApi = api,
            )
    }

    @AfterEach
    fun tearDown() {
        if (::server.isInitialized) {
            server.shutdown()
        }
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun `getEpisodesPaginated offset zero unions cached extras and keeps PI sourceCount`() =
        runTest(testDispatcher) {
            val podcastId = "900001"
            fakePort.episodesByPodcast[podcastId] =
                listOf(
                    TestFixtures.episode(
                        id = "-203",
                        title = "Feed only",
                        audioUrl = "https://cdn.example/feed-only.mp3",
                        publishedDate = 200L,
                    ),
                )
            enqueueEpisodesPage(
                id = 10,
                title = "PI latest",
                enclosureUrl = "https://cdn.example/pi.mp3",
                datePublished = 100,
                hasMore = true,
            )

            val page =
                repository.getEpisodesPaginated(
                    feedId = podcastId,
                    limit = 20,
                    offset = 0,
                    sort = "newest",
                )

            assertEquals(listOf("-203", "10"), page.episodes.map { it.id })
            assertEquals(1, page.sourceCount)
            assertTrue(page.hasMore)
        }

    @Test
    fun `getEpisodesPaginated later pages stay PI-only`() =
        runTest(testDispatcher) {
            val podcastId = "900002"
            fakePort.episodesByPodcast[podcastId] =
                listOf(
                    TestFixtures.episode(
                        id = "-203",
                        title = "Feed only",
                        audioUrl = "https://cdn.example/feed-only.mp3",
                        publishedDate = 200L,
                    ),
                )
            enqueueEpisodesPage(
                id = 11,
                title = "Older PI",
                enclosureUrl = "https://cdn.example/older.mp3",
                datePublished = 50,
                hasMore = false,
            )

            val page =
                repository.getEpisodesPaginated(
                    feedId = podcastId,
                    limit = 20,
                    offset = 20,
                    sort = "newest",
                )

            assertEquals(listOf("11"), page.episodes.map { it.id })
            assertEquals(1, page.sourceCount)
        }

    @Test
    fun `getEpisodesPaginated mergeSupplements false stays PI-only on offset zero`() =
        runTest(testDispatcher) {
            val podcastId = "900030"
            fakePort.episodesByPodcast[podcastId] =
                listOf(
                    TestFixtures.episode(
                        id = "-203",
                        title = "Feed only",
                        audioUrl = "https://cdn.example/feed-only.mp3",
                        publishedDate = 200L,
                    ),
                )
            enqueueEpisodesPage(
                id = 12,
                title = "PI latest",
                enclosureUrl = "https://cdn.example/pi.mp3",
                datePublished = 100,
                hasMore = false,
            )

            val page =
                repository.getEpisodesPaginated(
                    feedId = podcastId,
                    limit = 20,
                    offset = 0,
                    sort = "newest",
                    mergeSupplements = false,
                )

            assertEquals(listOf("12"), page.episodes.map { it.id })
        }

    @Test
    fun `searchEpisodes unions PI hits with supplement matches`() =
        runTest(testDispatcher) {
            val podcastId = "900003"
            fakePort.searchByPodcast[podcastId] =
                listOf(
                    TestFixtures.episode(
                        id = "-9",
                        title = "Feed match",
                        publishedDate = 200L,
                    ),
                )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "status": "true",
                          "items": [
                            {
                              "id": 44,
                              "title": "PI match",
                              "description": "d",
                              "enclosureUrl": "https://cdn.example/pi-match.mp3",
                              "datePublished": 100
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            )

            val results = repository.searchEpisodes(podcastId, "match")

            assertEquals(listOf("-9", "44"), results.map { it.id })
        }

    @Test
    fun `syncSubscriptions omits opted-in ids from POST and returns cached feed tip`() =
        runTest(testDispatcher) {
            fakePort.optedIn = setOf("900010")
            fakePort.episodesByPodcast["900010"] =
                listOf(
                    TestFixtures.episode(
                        id = "-1",
                        title = "Feed tip",
                        publishedDate = 300L,
                    ),
                )
            server.enqueue(
                MockResponse()
                    .setResponseCode(200)
                    .setHeader("Content-Type", "application/json")
                    .setBody(
                        """
                        {
                          "items": [
                            {
                              "id": "900011",
                              "latestEpisode": {
                                "id": 77,
                                "title": "PI tip",
                                "description": "d",
                                "enclosureUrl": "https://cdn.example/pi-tip.mp3",
                                "datePublished": 50
                              }
                            }
                          ]
                        }
                        """.trimIndent(),
                    ),
            )

            val tips = repository.syncSubscriptions(listOf("900010", "900011"))

            assertEquals(setOf("900010", "900011"), tips.keys)
            assertEquals("-1", tips.getValue("900010").id)
            assertEquals("77", tips.getValue("900011").id)

            val recorded = server.takeRequest()
            assertEquals("POST", recorded.method)
            assertTrue(recorded.path!!.startsWith("/sync"))
            val body = recorded.body.readUtf8()
            assertFalse(body.contains("900010"))
            assertTrue(body.contains("900011"))
        }

    @Test
    fun `syncSubscriptions skips network when every id is opted in`() =
        runTest(testDispatcher) {
            fakePort.optedIn = setOf("900020")
            fakePort.episodesByPodcast["900020"] =
                listOf(
                    TestFixtures.episode(id = "-5", title = "Cached", publishedDate = 9L),
                )

            val tips = repository.syncSubscriptions(listOf("900020"))

            assertEquals("-5", tips.getValue("900020").id)
            assertEquals(0, server.requestCount)
        }

    private fun enqueueEpisodesPage(
        id: Long,
        title: String,
        enclosureUrl: String,
        datePublished: Long,
        hasMore: Boolean,
    ) {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(
                    """
                    {
                      "items": [
                        {
                          "id": $id,
                          "title": "$title",
                          "description": "d",
                          "enclosureUrl": "$enclosureUrl",
                          "datePublished": $datePublished
                        }
                      ],
                      "hasMore": $hasMore,
                      "offset": 0,
                      "limit": 20
                    }
                    """.trimIndent(),
                ),
        )
    }

    private fun fakeContext(): Context {
        val prefs = mock(SharedPreferences::class.java)
        val editor = mock(SharedPreferences.Editor::class.java)
        `when`(prefs.getString(anyString(), nullable(String::class.java))).thenReturn(null)
        `when`(prefs.getAll()).thenReturn(emptyMap())
        `when`(prefs.contains(anyString())).thenReturn(false)
        `when`(prefs.edit()).thenReturn(editor)
        `when`(editor.putString(anyString(), anyString())).thenReturn(editor)
        `when`(editor.commit()).thenReturn(true)
        `when`(editor.apply()).then { }

        val appContext = mock(Context::class.java)
        `when`(appContext.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)

        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(appContext)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context
    }

    private fun fakeDatabase(): BoxLoreDatabase {
        val database = mock(BoxLoreDatabase::class.java)
        `when`(database.podcastDao()).thenReturn(mock(PodcastDao::class.java))
        `when`(database.rssEpisodeDao()).thenReturn(mock(RssEpisodeDao::class.java))
        return database
    }

    private class FakeEpisodeSupplementPort : EpisodeSupplementPort {
        var optedIn: Set<String> = emptySet()
        val episodesByPodcast = mutableMapOf<String, List<Episode>>()
        val searchByPodcast = mutableMapOf<String, List<Episode>>()

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

        override suspend fun hasDirectFeedOptIn(podcastIndexId: String): Boolean = podcastIndexId in optedIn

        override suspend fun listOptedInPodcastIds(): Set<String> = optedIn

        override suspend fun resolveNewestTipFromFeed(
            request: EpisodeSupplementPort.NewestTipRequest,
        ): Episode? = episodesByPodcast[request.podcastIndexId]?.maxByOrNull { it.publishedDate }

        override suspend fun getEpisodesForPodcast(
            podcastIndexId: String,
            podcastTitle: String?,
            podcastImageUrl: String?,
            podcastGenre: String?,
            podcastArtist: String?,
        ): List<Episode> = episodesByPodcast[podcastIndexId].orEmpty()

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
        ): List<Episode> = searchByPodcast[podcastIndexId].orEmpty()
    }

    companion object {
        private const val APP_KEY = "test-app-key"
    }
}
