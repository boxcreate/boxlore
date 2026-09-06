package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import android.content.SharedPreferences
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.domain.RssSubscriptionResult
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.nullable
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class LibraryBackupManagerProgressTest {
    private lateinit var podcastDao: PodcastDao
    private lateinit var subscriptionRepository: SubscriptionRepository
    private lateinit var podcastRepository: PodcastRepository
    private lateinit var adaptiveRankingRepository: AdaptiveRankingRepository
    private lateinit var rssPodcastRepository: RssPodcastRepository
    private lateinit var manager: LibraryBackupManager
    private val fakeListeningHistory = object : ListeningHistoryBackupPort {
        override fun getAllHistory(): Flow<List<ListeningHistoryEntity>> = flowOf(emptyList())
        override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) {}
        override suspend fun markAllEpisodesCompleted(
            episodes: List<Episode>,
            podcastId: String,
            podcastTitle: String,
            podcastImageUrl: String?,
        ) {}
    }

    @BeforeEach
    fun setUp() {
        val context = fakeContext()
        podcastDao = mock(PodcastDao::class.java)
        subscriptionRepository = SubscriptionRepository(podcastDao)
        podcastRepository = mock(PodcastRepository::class.java)
        adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java)
        rssPodcastRepository = mock(RssPodcastRepository::class.java)

        manager = LibraryBackupManager(
            subscriptionRepository = subscriptionRepository,
            listeningHistory = fakeListeningHistory,
            podcastRepository = podcastRepository,
            context = context,
            adaptiveRankingRepository = adaptiveRankingRepository,
            rssPodcastRepository = rssPodcastRepository,
        )
    }

    @Test
    fun `importLibraryFromJson emits preparing and zero-target completion for empty library`() = runTest {
        val progressEvents = mutableListOf<JsonBackupProgress>()
        val json = """{"version": 4, "subscriptions": [], "history": []}"""

        val result = manager.importLibraryFromJson(json) { progressEvents.add(it) }

        assertEquals(Pair(0, false), result)
        assertEquals(2, progressEvents.size)
        assertEquals(JsonBackupPhase.PREPARING, progressEvents.first().phase)
        assertEquals(JsonBackupPhase.COMPLETED, progressEvents.last().phase)
        assertEquals(0, progressEvents.last().current)
        assertEquals(0, progressEvents.last().total)
    }

    @Test
    fun `importLibraryFromJson emits feed progress callbacks for restored shows`() = runTest {
        val progressEvents = mutableListOf<JsonBackupProgress>()
        val json = """
            {
              "version": 4,
              "subscriptions": [
                {
                  "podcastId": "1001",
                  "title": "Tech Talk",
                  "author": "Host",
                  "imageUrl": "https://example.com/art.jpg",
                  "subscribedAt": 1000,
                  "notificationsEnabled": true,
                  "autoDownloadEnabled": false
                }
              ],
              "history": []
            }
        """.trimIndent()

        `when`(podcastRepository.syncSubscriptions(listOf("1001"))).thenReturn(emptyMap())

        val result = manager.importLibraryFromJson(json) { progressEvents.add(it) }

        assertEquals(Pair(1, true), result)
        assertTrue(progressEvents.any { it.phase == JsonBackupPhase.PREPARING })
        assertTrue(
            progressEvents.any {
                it.phase == JsonBackupPhase.REFRESHING_FEEDS &&
                    it.current == 0 &&
                    it.total == 1 &&
                    it.currentTitle == "Tech Talk"
            },
        )
        assertTrue(
            progressEvents.any {
                it.phase == JsonBackupPhase.REFRESHING_FEEDS &&
                    it.current == 1 &&
                    it.total == 1
            },
        )
        val completed = progressEvents.last()
        assertEquals(JsonBackupPhase.COMPLETED, completed.phase)
        assertEquals(1, completed.current)
        assertEquals(1, completed.total)
    }

    @Test
    fun `importLibraryFromJson rethrows coroutine CancellationException during RSS refresh`() = runTest {
        val rssShow = TestFixtures.podcast(id = "rss:feed-1", title = "RSS Show", sourceType = "rss")
        `when`(rssPodcastRepository.addSubscription("https://example.com/rss.xml"))
            .thenReturn(RssSubscriptionResult(podcast = rssShow, episodeCount = 10, automaticUpdateChecksSupported = true))
        `when`(rssPodcastRepository.refreshCatalogIfNeeded("rss:feed-1"))
            .thenAnswer { throw CancellationException("coroutine cancelled") }

        val json = """
            {
              "version": 4,
              "subscriptions": [
                {
                  "podcastId": "rss:feed-1",
                  "title": "RSS Show",
                  "feedUrl": "https://example.com/rss.xml",
                  "sourceType": "rss",
                  "subscribedAt": 1000,
                  "notificationsEnabled": false,
                  "autoDownloadEnabled": false
                }
              ],
              "history": []
            }
        """.trimIndent()

        var caught: Throwable? = null
        try {
            manager.importLibraryFromJson(json)
        } catch (e: Throwable) {
            caught = e
        }
        assertTrue(caught is CancellationException)
    }

    @Test
    fun `importLibraryFromJson handles ordinary RSS refresh failure and completes`() = runTest {
        val progressEvents = mutableListOf<JsonBackupProgress>()
        val rssShow = TestFixtures.podcast(id = "rss:feed-2", title = "RSS Show 2", sourceType = "rss")
        `when`(rssPodcastRepository.addSubscription("https://example.com/rss2.xml"))
            .thenReturn(RssSubscriptionResult(podcast = rssShow, episodeCount = 10, automaticUpdateChecksSupported = true))
        `when`(rssPodcastRepository.refreshCatalogIfNeeded("rss:feed-2"))
            .thenAnswer { throw RuntimeException("network error") }

        val json = """
            {
              "version": 4,
              "subscriptions": [
                {
                  "podcastId": "rss:feed-2",
                  "title": "RSS Show 2",
                  "feedUrl": "https://example.com/rss2.xml",
                  "sourceType": "rss",
                  "subscribedAt": 1000,
                  "notificationsEnabled": false,
                  "autoDownloadEnabled": false
                }
              ],
              "history": []
            }
        """.trimIndent()

        val result = manager.importLibraryFromJson(json) { progressEvents.add(it) }

        assertEquals(Pair(1, false), result)
        assertEquals(JsonBackupPhase.COMPLETED, progressEvents.last().phase)
        assertEquals(1, progressEvents.last().current)
    }

    @Test
    fun `importLibraryFromJson returns minus one on malformed json`() = runTest {
        val progressEvents = mutableListOf<JsonBackupProgress>()
        val result = manager.importLibraryFromJson("{ broken json") { progressEvents.add(it) }

        assertEquals(Pair(-1, false), result)
        assertEquals(1, progressEvents.size)
        assertEquals(JsonBackupPhase.PREPARING, progressEvents.single().phase)
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

        val appContext = mock(Context::class.java)
        `when`(appContext.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)

        val context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(appContext)
        `when`(context.getSharedPreferences(anyString(), anyInt())).thenReturn(prefs)
        return context
    }
}
