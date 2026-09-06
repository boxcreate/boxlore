package cx.aswin.boxlore.core.playback.service.auto

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.QueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Suppress("UNCHECKED_CAST")
private fun <T> mockAny(): T {
    Mockito.any<T>()
    val dummy: Any? = null
    return dummy as T
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoPlaybackResumptionHandlerTest {

    private lateinit var context: Context
    private lateinit var prefs: SharedPreferences
    private lateinit var host: AutoBrowseLibraryHost
    private lateinit var mediaResolver: AutoMediaResolver
    private lateinit var listeningHistoryDao: ListeningHistoryDao
    private lateinit var queueRepository: QueueRepository
    private lateinit var mediaSession: MediaSession
    private lateinit var player: Player
    private lateinit var handler: AutoPlaybackResumptionHandler

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        prefs = context.getSharedPreferences("test_player_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        host = mock(AutoBrowseLibraryHost::class.java)
        `when`(host.asContext()).thenReturn(context)
        `when`(host.serviceScope).thenReturn(CoroutineScope(Dispatchers.Unconfined))

        mediaResolver = mock(AutoMediaResolver::class.java)
        listeningHistoryDao = mock(ListeningHistoryDao::class.java)
        queueRepository = mock(QueueRepository::class.java)

        mediaSession = mock(MediaSession::class.java)
        player = mock(Player::class.java)
        `when`(mediaSession.player).thenReturn(player)

        handler = AutoPlaybackResumptionHandler(
            host = host,
            mediaResolver = mediaResolver,
            customPrefs = prefs,
            customListeningHistoryDao = listeningHistoryDao,
            customQueueRepository = queueRepository,
        )
    }

    @Test
    fun `resolveResumption returns live player items and clears player dismissed flag when player has items`() = runBlocking {
        prefs.edit().putBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, true).commit()

        val liveItem = MediaItem.Builder()
            .setMediaId("episode:live-1")
            .setUri(Uri.parse("https://example.com/live1.mp3"))
            .build()
        `when`(player.mediaItemCount).thenReturn(1)
        `when`(player.currentMediaItemIndex).thenReturn(0)
        `when`(player.currentPosition).thenReturn(45_000L)
        `when`(player.getMediaItemAt(0)).thenReturn(liveItem)
        `when`(player.currentMediaItem).thenReturn(liveItem)

        val result = handler.resolveResumption(mediaSession)

        assertEquals(1, result.mediaItems.size)
        assertEquals("episode:live-1", result.mediaItems[0].mediaId)
        assertEquals(0, result.startIndex)
        assertEquals(45_000L, result.startPositionMs)
        assertFalse(prefs.getBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, false))
    }

    @Test
    fun `resolveResumption restores active miniplayer from listening history with incomplete progress`() = runBlocking {
        `when`(player.mediaItemCount).thenReturn(0)
        prefs.edit().putBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, false).commit()

        val historyEntity = ListeningHistoryEntity(
            episodeId = "ep-history-1",
            podcastId = "pod-1",
            episodeTitle = "Episode 1",
            episodeImageUrl = "https://example.com/img.jpg",
            podcastImageUrl = null,
            episodeAudioUrl = "https://example.com/ep1.mp3",
            podcastName = "Podcast 1",
            progressMs = 30_000L,
            durationMs = 120_000L,
            isCompleted = false,
            lastPlayedAt = 1000L,
        )
        `when`(listeningHistoryDao.getLastPlayedSessionAny()).thenReturn(historyEntity)
        `when`(queueRepository.getQueueSnapshot()).thenReturn(emptyList())

        val domainEpisode = Episode(
            id = "ep-history-1",
            title = "Episode 1",
            description = "Desc",
            audioUrl = "https://example.com/ep1.mp3",
            imageUrl = "https://example.com/img.jpg",
            podcastImageUrl = null,
            podcastTitle = "Podcast 1",
            podcastId = "pod-1",
            duration = 120,
        )
        `when`(mediaResolver.resolveDomainEpisode("ep-history-1")).thenReturn(domainEpisode)

        val resolvedPlayableItem = MediaItem.Builder()
            .setMediaId("queue:ep-history-1")
            .setUri(Uri.parse("https://example.com/ep1.mp3"))
            .build()
        `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(resolvedPlayableItem)

        val result = handler.resolveResumption(mediaSession)

        assertEquals(1, result.mediaItems.size)
        assertEquals(0, result.startIndex)
        assertEquals(30_000L, result.startPositionMs)
        assertFalse(prefs.getBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, true))
        verify(queueRepository).replaceQueue(listOf(domainEpisode))
    }

    @Test
    fun `resolveResumption starts from zero when active miniplayer candidate is completed`() = runBlocking {
        `when`(player.mediaItemCount).thenReturn(0)
        prefs.edit().putBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, false).commit()

        val historyEntity = ListeningHistoryEntity(
            episodeId = "ep-done",
            podcastId = "pod-1",
            episodeTitle = "Done Ep",
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = "https://example.com/ep.mp3",
            podcastName = "Podcast",
            progressMs = 100_000L,
            durationMs = 100_000L,
            isCompleted = true,
            lastPlayedAt = 1000L,
        )
        `when`(listeningHistoryDao.getLastPlayedSessionAny()).thenReturn(historyEntity)
        `when`(queueRepository.getQueueSnapshot()).thenReturn(emptyList())

        val domainEpisode = Episode(
            id = "ep-done",
            title = "Done Ep",
            description = "",
            audioUrl = "https://example.com/ep.mp3",
            imageUrl = null,
            podcastImageUrl = null,
            podcastTitle = "Podcast",
            podcastId = "pod-1",
            duration = 100,
        )
        `when`(mediaResolver.resolveDomainEpisode("ep-done")).thenReturn(domainEpisode)

        val resolvedPlayableItem = MediaItem.Builder()
            .setMediaId("queue:ep-done")
            .setUri(Uri.parse("https://example.com/ep.mp3"))
            .build()
        `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(resolvedPlayableItem)

        val result = handler.resolveResumption(mediaSession)

        assertEquals(1, result.mediaItems.size)
        assertEquals(0L, result.startPositionMs)
    }

    @Test
    fun `resolveResumption restores inactive incomplete episode from listening history`() = runBlocking {
        `when`(player.mediaItemCount).thenReturn(0)
        prefs.edit().putBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, true).commit()

        val historyEntity = ListeningHistoryEntity(
            episodeId = "ep-incomplete",
            podcastId = "pod-1",
            episodeTitle = "Incomplete",
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = "https://example.com/incomplete.mp3",
            podcastName = "Podcast",
            progressMs = 50_000L,
            durationMs = 200_000L,
            isCompleted = false,
            lastPlayedAt = 1000L,
        )
        `when`(listeningHistoryDao.getLastPlayedSession()).thenReturn(historyEntity)
        `when`(queueRepository.getQueueSnapshot()).thenReturn(emptyList())

        val domainEpisode = Episode(
            id = "ep-incomplete",
            title = "Incomplete",
            description = "",
            audioUrl = "https://example.com/incomplete.mp3",
            imageUrl = null,
            podcastImageUrl = null,
            podcastTitle = "Podcast",
            podcastId = "pod-1",
            duration = 200,
        )
        `when`(mediaResolver.resolveDomainEpisode("ep-incomplete")).thenReturn(domainEpisode)

        val resolvedItem = MediaItem.Builder()
            .setMediaId("queue:ep-incomplete")
            .setUri(Uri.parse("https://example.com/incomplete.mp3"))
            .build()
        `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(resolvedItem)

        val result = handler.resolveResumption(mediaSession)

        assertEquals(1, result.mediaItems.size)
        assertEquals(50_000L, result.startPositionMs)
        assertFalse(prefs.getBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, true))
    }

    @Test
    fun `resolveResumption throws UnsupportedOperationException when player dismissed and candidate is completed`() = runBlocking {
        `when`(player.mediaItemCount).thenReturn(0)
        prefs.edit().putBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, true).commit()

        val completedHistory = ListeningHistoryEntity(
            episodeId = "ep-done-dismissed",
            podcastId = "pod-1",
            episodeTitle = "Done",
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = "https://example.com/done.mp3",
            podcastName = "Podcast",
            progressMs = 95_000L,
            durationMs = 100_000L,
            isCompleted = false,
            lastPlayedAt = 1000L,
        )
        `when`(listeningHistoryDao.getLastPlayedSession()).thenReturn(completedHistory)

        try {
            handler.resolveResumption(mediaSession)
            fail("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("No playback resumption items available") == true)
        }
    }

    @Test
    fun `resolveResumption falls back to first queue item when listening history is empty`() = runBlocking {
        `when`(player.mediaItemCount).thenReturn(0)
        prefs.edit().putBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, false).commit()

        `when`(listeningHistoryDao.getLastPlayedSessionAny()).thenReturn(null)

        val queueEpisode = Episode(
            id = "queue-ep-1",
            title = "Queue Ep",
            description = "",
            audioUrl = "https://example.com/q1.mp3",
            imageUrl = null,
            podcastImageUrl = null,
            podcastTitle = "Podcast",
            podcastId = "pod-1",
            duration = 300,
        )
        `when`(queueRepository.getQueueSnapshot()).thenReturn(listOf(queueEpisode))
        `when`(mediaResolver.resolveDomainEpisode("queue-ep-1")).thenReturn(queueEpisode)

        val resolvedItem = MediaItem.Builder()
            .setMediaId("queue:queue-ep-1")
            .setUri(Uri.parse("https://example.com/q1.mp3"))
            .build()
        `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(resolvedItem)

        val result = handler.resolveResumption(mediaSession)

        assertEquals(1, result.mediaItems.size)
        assertEquals(0, result.startIndex)
        assertEquals(0L, result.startPositionMs)
    }

    @Test
    fun `resolveResumption throws UnsupportedOperationException when both history and queue are empty`() = runBlocking {
        `when`(player.mediaItemCount).thenReturn(0)
        prefs.edit().putBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, false).commit()

        `when`(listeningHistoryDao.getLastPlayedSessionAny()).thenReturn(null)
        `when`(queueRepository.getQueueSnapshot()).thenReturn(emptyList())

        try {
            handler.resolveResumption(mediaSession)
            fail("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("No playback resumption items available") == true)
        }
    }

    @Test
    fun `resolveResumption strips prefixes on target episode id and aligns existing queue`() = runBlocking {
        `when`(player.mediaItemCount).thenReturn(0)
        prefs.edit().putBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, false).commit()

        val historyEntity = ListeningHistoryEntity(
            episodeId = "queue:target-ep",
            podcastId = "pod-1",
            episodeTitle = "Target Ep",
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = "https://example.com/target.mp3",
            podcastName = "Podcast",
            progressMs = 15_000L,
            durationMs = 120_000L,
            isCompleted = false,
            lastPlayedAt = 1000L,
        )
        `when`(listeningHistoryDao.getLastPlayedSessionAny()).thenReturn(historyEntity)

        val ep1 = Episode(id = "other-ep", title = "Ep 1", description = "", audioUrl = "https://example.com/1.mp3", imageUrl = null, podcastImageUrl = null, podcastTitle = null, podcastId = null, duration = 60)
        val ep2 = Episode(id = "target-ep", title = "Target Ep", description = "", audioUrl = "https://example.com/target.mp3", imageUrl = null, podcastImageUrl = null, podcastTitle = null, podcastId = null, duration = 120)
        `when`(queueRepository.getQueueSnapshot()).thenReturn(listOf(ep1, ep2))
        `when`(mediaResolver.resolveDomainEpisode("target-ep")).thenReturn(ep2)

        val resolved1 = MediaItem.Builder().setMediaId("queue:other-ep").setUri(Uri.parse("https://example.com/1.mp3")).build()
        val resolved2 = MediaItem.Builder().setMediaId("queue:target-ep").setUri(Uri.parse("https://example.com/target.mp3")).build()
        `when`(mediaResolver.resolveMediaItem(mockAny())).thenAnswer { invocation ->
            val item = invocation.arguments[0] as MediaItem
            if (item.mediaId.contains("other-ep")) resolved1 else resolved2
        }

        val result = handler.resolveResumption(mediaSession)

        assertEquals(2, result.mediaItems.size)
        assertEquals(1, result.startIndex)
        assertEquals(15_000L, result.startPositionMs)
    }

    @Test
    fun `resolveResumption throws UnsupportedOperationException when resolved target has no playable URI`() = runBlocking {
        `when`(player.mediaItemCount).thenReturn(0)
        prefs.edit().putBoolean(AutoPlaybackResumptionHandler.KEY_PLAYER_DISMISSED, false).commit()

        val historyEntity = ListeningHistoryEntity(
            episodeId = "unresolvable-ep",
            podcastId = "pod-1",
            episodeTitle = "No URI",
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = null,
            podcastName = "Podcast",
            progressMs = 10_000L,
            durationMs = 60_000L,
            isCompleted = false,
            lastPlayedAt = 1000L,
        )
        `when`(listeningHistoryDao.getLastPlayedSessionAny()).thenReturn(historyEntity)
        `when`(queueRepository.getQueueSnapshot()).thenReturn(emptyList())
        `when`(mediaResolver.resolveDomainEpisode("unresolvable-ep")).thenReturn(null)

        val ungroundedItem = MediaItem.Builder().setMediaId("unresolvable-ep").build()
        `when`(mediaResolver.resolveMediaItem(mockAny())).thenReturn(ungroundedItem)

        try {
            handler.resolveResumption(mediaSession)
            fail("Expected UnsupportedOperationException")
        } catch (e: UnsupportedOperationException) {
            assertTrue(e.message?.contains("No playable items resolved for resumption") == true)
        }
    }
}
