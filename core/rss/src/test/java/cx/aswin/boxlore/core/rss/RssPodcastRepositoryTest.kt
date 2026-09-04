package cx.aswin.boxlore.core.rss

import android.content.Context
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeDao
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class RssPodcastRepositoryTest {
    private lateinit var context: Context
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var episodeDao: RssEpisodeDao
    private lateinit var repository: RssPodcastRepository

    @BeforeEach
    fun setUp() {
        context = mock(Context::class.java)
        `when`(context.applicationContext).thenReturn(context)
        database = mock(BoxLoreDatabase::class.java)
        podcastDao = mock(PodcastDao::class.java)
        episodeDao = mock(RssEpisodeDao::class.java)
        `when`(database.podcastDao()).thenReturn(podcastDao)
        `when`(database.rssEpisodeDao()).thenReturn(episodeDao)

        repository = RssPodcastRepository.createForTests(context, database)
    }

    @AfterEach
    fun tearDown() {
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun getEpisodesAroundReturnsAnchorAndSubsequentEpisodes() = runTest {
        val podcastId = "-1001"
        val podcast =
            PodcastEntity(
                podcastId = podcastId,
                title = "Test RSS Show",
                author = "Test Author",
                imageUrl = "https://example.com/image.jpg",
                description = "Description",
                sourceType = PodcastEntity.SOURCE_RSS,
            )
        `when`(podcastDao.getPodcast(podcastId)).thenReturn(podcast)

        val anchor =
            rssEpisode(
                episodeId = "-1",
                podcastId = podcastId,
                publishedDate = 1000L,
            )
        val subsequent1 =
            rssEpisode(
                episodeId = "-2",
                podcastId = podcastId,
                publishedDate = 2000L,
            )
        val subsequent2 =
            rssEpisode(
                episodeId = "-3",
                podcastId = podcastId,
                publishedDate = 3000L,
            )

        `when`(episodeDao.getEpisode("-1")).thenReturn(anchor)
        `when`(
            episodeDao.getEpisodesAfter(
                podcastId = podcastId,
                publishedDate = 1000L,
                episodeId = "-1",
                limit = 9,
            ),
        ).thenReturn(listOf(subsequent1, subsequent2))

        val window =
            repository.getEpisodesAround(
                podcastId = podcastId,
                bound = 10,
                aroundEpisodeId = "-1",
            )

        assertEquals(listOf("-1", "-2", "-3"), window.map { it.id })
    }

    private fun rssEpisode(episodeId: String, podcastId: String, publishedDate: Long,) = RssEpisodeEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        guid = "guid-$episodeId",
        title = "Episode $episodeId",
        description = "Description $episodeId",
        audioUrl = "https://example.com/$episodeId.mp3",
        imageUrl = null,
        duration = 120,
        publishedDate = publishedDate,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = "full",
        enclosureType = "audio/mpeg",
    )
}
