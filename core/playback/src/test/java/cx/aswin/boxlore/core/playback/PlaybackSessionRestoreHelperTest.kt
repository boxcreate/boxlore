package cx.aswin.boxlore.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackSessionRestoreHelperTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastRepository: PodcastRepository
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database =
            Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        podcastRepository = mock(PodcastRepository::class.java)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun targetEpisodePresentWithBlankPodcastNameHealsFromCurrentItemAndEnrichesRoom() = testScope.runTest {
        val dao = database.listeningHistoryDao()
        dao.upsert(
            ListeningHistoryEntity(
                episodeId = "ep-1",
                podcastId = "pod-1",
                episodeTitle = "Episode 1",
                episodeImageUrl = "https://example.com/ep.png",
                podcastImageUrl = null,
                episodeAudioUrl = "https://example.com/ep.mp3",
                podcastName = "",
                progressMs = 5_000L,
                durationMs = 60_000L,
                isCompleted = false,
                isLiked = false,
                lastPlayedAt = 100L,
                isDirty = false,
            ),
        )

        val currentItem =
            MediaItem.Builder()
                .setMediaId("episode:ep-1")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle("Episode 1")
                        .setAlbumTitle("Live Show Name")
                        .build(),
                ).build()

        val restored =
            PlaybackSessionRestoreHelper.resolveRestoredSession(
                targetEpisodeId = "ep-1",
                currentItem = currentItem,
                listeningHistoryDao = dao,
                podcastRepository = podcastRepository,
                savedQueue = emptyList(),
            )

        assertNotNull(restored)
        assertEquals("ep-1", restored!!.episode.id)
        assertEquals("Live Show Name", restored.episode.podcastTitle)
        assertEquals("Live Show Name", restored.podcast.title)

        val stored = dao.getHistoryItem("ep-1")
        assertNotNull(stored)
        assertEquals("Live Show Name", stored!!.podcastName)
    }

    @Test
    fun targetEpisodeMissingInDbResolvesFromQueueWithoutRestoringUnrelatedLastSession() = testScope.runTest {
        val dao = database.listeningHistoryDao()
        // Insert an older unrelated session in SQLite
        dao.upsert(
            ListeningHistoryEntity(
                episodeId = "ep-unrelated",
                podcastId = "pod-old",
                episodeTitle = "Old Episode",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://example.com/old.mp3",
                podcastName = "Old Podcast",
                progressMs = 1_000L,
                durationMs = 30_000L,
                isCompleted = false,
                isLiked = false,
                lastPlayedAt = 50L,
                isDirty = false,
            ),
        )

        val queueEpisode =
            Episode(
                id = "ep-target",
                title = "Target Episode",
                description = "Desc",
                audioUrl = "https://example.com/target.mp3",
                imageUrl = "https://example.com/target.png",
                podcastImageUrl = "https://example.com/pod.png",
                podcastTitle = "Target Show",
                podcastId = "pod-target",
                podcastGenre = "Tech",
                podcastArtist = "Host",
                duration = 120,
                publishedDate = 0L,
            )

        val restored =
            PlaybackSessionRestoreHelper.resolveRestoredSession(
                targetEpisodeId = "ep-target",
                currentItem = null,
                listeningHistoryDao = dao,
                podcastRepository = podcastRepository,
                savedQueue = listOf(queueEpisode),
            )

        assertNotNull(restored)
        assertEquals("ep-target", restored!!.episode.id)
        assertEquals("Target Show", restored.episode.podcastTitle)
        assertEquals("Target Show", restored.podcast.title)

        // Verifies SQLite has synthesized the row for the target episode
        val stored = dao.getHistoryItem("ep-target")
        assertNotNull(stored)
        assertEquals("Target Show", stored!!.podcastName)
        assertEquals("https://example.com/target.mp3", stored.episodeAudioUrl)
    }

    @Test
    fun coldStartRestoreHealsBlankPodcastNameFromApiAndEnrichesRoom() = testScope.runTest {
        val dao = database.listeningHistoryDao()
        dao.upsert(
            ListeningHistoryEntity(
                episodeId = "ep-auto",
                podcastId = "pod-auto",
                episodeTitle = "Auto Episode",
                episodeImageUrl = "https://example.com/ep.png",
                podcastImageUrl = null,
                episodeAudioUrl = "https://example.com/auto.mp3",
                podcastName = "",
                progressMs = 2_000L,
                durationMs = 40_000L,
                isCompleted = false,
                isLiked = false,
                lastPlayedAt = 200L,
                isDirty = false,
            ),
        )

        `when`(podcastRepository.getEpisode("ep-auto")).thenReturn(
            Episode(
                id = "ep-auto",
                title = "Auto Episode",
                description = "",
                audioUrl = "https://example.com/auto.mp3",
                imageUrl = "https://example.com/ep.png",
                podcastImageUrl = "https://example.com/pod.png",
                podcastTitle = "Recovered API Show",
                podcastId = "pod-auto",
                podcastGenre = "News",
                podcastArtist = "Journalist",
                duration = 40,
                publishedDate = 0L,
            ),
        )

        val restored =
            PlaybackSessionRestoreHelper.resolveRestoredSession(
                targetEpisodeId = null,
                currentItem = null,
                listeningHistoryDao = dao,
                podcastRepository = podcastRepository,
                savedQueue = emptyList(),
            )

        assertNotNull(restored)
        assertEquals("ep-auto", restored!!.episode.id)
        assertEquals("Recovered API Show", restored.episode.podcastTitle)
        assertEquals("Recovered API Show", restored.podcast.title)

        val stored = dao.getHistoryItem("ep-auto")
        assertNotNull(stored)
        assertEquals("Recovered API Show", stored!!.podcastName)
        assertEquals("https://example.com/pod.png", stored.podcastImageUrl)
    }

    @Test
    fun coldStartWithNoHistoryReturnsNull() = testScope.runTest {
        val dao = database.listeningHistoryDao()
        val restored =
            PlaybackSessionRestoreHelper.resolveRestoredSession(
                targetEpisodeId = null,
                currentItem = null,
                listeningHistoryDao = dao,
                podcastRepository = podcastRepository,
                savedQueue = emptyList(),
            )

        assertNull(restored)
    }
}
