package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.ranking.AdaptiveRankingBackup
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

/** Pins what the Settings "Full library backup" summary may promise about the export. */
class LibraryBackupExportTest {
    private val podcastDao = mock(PodcastDao::class.java)
    private val folderRepository = mock(FolderRepository::class.java)
    private val adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java)
    private val listeningHistory =
        object : ListeningHistoryBackupPort {
            override fun getAllHistory(): Flow<List<ListeningHistoryEntity>> = flowOf(
                listOf(
                    ListeningHistoryEntity(
                        episodeId = "ep-1",
                        podcastId = "1001",
                        episodeTitle = "Pilot",
                        episodeImageUrl = null,
                        podcastImageUrl = null,
                        episodeAudioUrl = "https://example.com/ep-1.mp3",
                        podcastName = "Tech Talk",
                        progressMs = 1_000L,
                        durationMs = 60_000L,
                        isCompleted = false,
                        isLiked = true,
                        likedAt = 2_000L,
                        lastPlayedAt = 3_000L,
                    ),
                ),
            )

            override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) {}

            override suspend fun markAllEpisodesCompleted(
                episodes: List<Episode>,
                podcastId: String,
                podcastTitle: String,
                podcastImageUrl: String?,
            ) {}
        }

    private val manager =
        LibraryBackupManager(
            subscriptionRepository = SubscriptionRepository(podcastDao),
            listeningHistory = listeningHistory,
            podcastRepository = mock(PodcastRepository::class.java),
            context = mock(Context::class.java).also { `when`(it.applicationContext).thenReturn(it) },
            adaptiveRankingRepository = adaptiveRankingRepository,
            rssPodcastRepository = mock(RssPodcastRepository::class.java),
            folderRepository = folderRepository,
        )

    init {
        `when`(podcastDao.getSubscribedPodcasts()).thenReturn(
            flowOf(
                listOf(
                    PodcastEntity(
                        podcastId = "1001",
                        title = "Tech Talk",
                        author = "Host",
                        imageUrl = "https://example.com/art.jpg",
                        description = null,
                        isSubscribed = true,
                        feedUrl = "https://example.com/feed.xml",
                    ),
                ),
            ),
        )
    }

    @Test
    fun `json export writes subscriptions folders history and likes but no Missing episodes opt-ins`() = runTest {
        `when`(folderRepository.getFolders()).thenReturn(listOf(SubscriptionFolder(id = "f1", name = "Focus", podcastIds = listOf("1001"))))
        `when`(adaptiveRankingRepository.exportBackup()).thenReturn(AdaptiveRankingBackup())

        val json = JsonParser.parseString(manager.exportLibraryAsJson()).asJsonObject

        assertEquals(LibraryBackupDirectFeedLogic.VERSION, json["version"].asInt)
        assertEquals("1001", json["subscriptions"].asJsonArray.single().asJsonObject["podcastId"].asString)
        assertEquals("Focus", json["folders"].asJsonArray.single().asJsonObject["name"].asString)
        assertTrue(json["history"].asJsonArray.single().asJsonObject["isLiked"].asBoolean)
        assertTrue(json.has("adaptiveRanking"))
        // Restore derives publisher-feed refreshes from each subscription's feedUrl instead.
        assertFalse(json.has("directFeedOptIns"))
    }

    @Test
    fun `opml export title spells boxlore in lowercase`() = runTest {
        val opml = manager.exportLibraryAsOpml()

        assertTrue(opml.contains("<title>boxlore subscriptions</title>"), opml)
    }
}
