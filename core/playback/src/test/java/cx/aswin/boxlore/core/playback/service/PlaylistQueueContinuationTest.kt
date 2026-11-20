package cx.aswin.boxlore.core.playback.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlaybackQueueContext
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.SmartQueueRefillPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * When a playing playlist's last queued episode ends, the service tops the queue up from the
 * playlist: its next unheard episodes, leaving out what is already queued, saved as the playlist's
 * (`playlist:<id>`, `MANUAL`) so the next top-up works the same way. Other queues are left to Smart Queue.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistQueueContinuationTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var repository: QueueRepository
    private var asked: Triple<String, Int, Collection<String>>? = null

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        repository = QueueRepository(database, mock(PodcastRepository::class.java))
    }

    @After fun tearDown() {
        database.close()
    }

    private fun episode(id: Int) = Episode("$id", "Episode $id", "", "https://example.com/$id.mp3", podcastId = "show", podcastTitle = "Show", publishedDate = id.toLong())

    private class Queue(episodes: List<Episode>, initialIndex: Int) {
        val items = episodes.map { episode ->
            MediaItem.Builder().setMediaId(episode.id).setUri(episode.audioUrl)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(episode.title).setExtras(PlaybackQueueContext.extras(episode, null)).build()).build()
        }.toMutableList()
        var index = initialIndex
        val player: Player = mock(Player::class.java).also { player ->
            `when`(player.currentMediaItem).thenAnswer { items[index] }
            `when`(player.currentMediaItemIndex).thenAnswer { index }
            `when`(player.mediaItemCount).thenAnswer { items.size }
            `when`(player.deviceInfo).thenReturn(androidx.media3.common.DeviceInfo.UNKNOWN)
            `when`(player.getMediaItemAt(anyInt())).thenAnswer { items[it.getArgument<Int>(0)] }
            doAnswer {
                items.addAll(it.getArgument<List<MediaItem>>(0))
                null
            }.`when`(player).addMediaItems(anyList())
        }
    }

    private fun TestScope.continuation(queueMaxSize: Int = 50, next: (Int) -> List<Episode> = { limit -> (10 until 10 + limit).map(::episode) }): PlaylistQueueContinuation {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return PlaylistQueueContinuation(
            database = database,
            queueRepository = repository,
            nextEpisodes = { id, limit, exclude ->
                asked = Triple(id, limit, exclude)
                next(limit)
            },
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            queueMaxSize = queueMaxSize,
            mediaIdPrefixStripper = SmartQueueRefillPolicy::stripQueuePrefixes,
            batchSize = 3,
        )
    }

    @Test fun `a finished playlist gets its next batch as the playlist's own episodes`() = runTest {
        val episodes = PlaybackQueueContext.playlistEpisodes(listOf(episode(1), episode(2)), "list-a")
        repository.replaceQueue(episodes)
        val queue = Queue(episodes, 1)

        assertEquals(true, continuation().refill(queue.player) { true })

        assertEquals(Triple("list-a", 3, listOf("1", "2")), asked)
        assertEquals(listOf("1", "2", "10", "11", "12"), queue.items.map { it.mediaId })
        assertTrue("the top-up plays as the playlist, so it can top up again", queue.items.all(PlaybackQueueContext::isContextItem))
        val saved = database.queueDao().getAllQueueItemsSync()
        assertEquals(listOf("1", "2", "10", "11", "12"), saved.map { it.episodeId })
        assertEquals(setOf("playlist:list-a"), saved.map { it.contextSourceId }.toSet())
        assertEquals("MANUAL", saved.last().contextType)
    }

    @Test fun `a playlist with nothing left hands over to Smart Queue`() = runTest {
        val episodes = PlaybackQueueContext.playlistEpisodes(listOf(episode(1)), "list-a")
        repository.replaceQueue(episodes)
        val queue = Queue(episodes, 0)

        assertEquals(false, continuation(next = { emptyList() }).refill(queue.player) { true })
        assertEquals(listOf("1"), queue.items.map { it.mediaId })
    }

    @Test fun `New Episodes and Smart Queue items are not a playlist's`() = runTest {
        val newEpisodes = PlaybackQueueContext.episodes(listOf(episode(1)))
        repository.replaceQueue(newEpisodes)
        assertNull(continuation().refill(Queue(newEpisodes, 0).player) { true })

        val plain = listOf(episode(2))
        repository.replaceQueue(plain)
        assertNull(continuation().refill(Queue(plain, 0).player) { true })
        assertNull("never asked the playlist", asked)
    }

    @Test fun `the top-up never goes past the queue limit`() = runTest {
        val episodes = PlaybackQueueContext.playlistEpisodes((1..4).map(::episode), "list-a")
        repository.replaceQueue(episodes)
        val queue = Queue(episodes, 2)

        assertEquals(true, continuation(queueMaxSize = 3).refill(queue.player) { true })
        assertEquals(1, asked!!.second)
        assertEquals(listOf("1", "2", "3", "4", "10"), queue.items.map { it.mediaId })

        val full = Queue(episodes, 1)
        asked = null
        assertEquals(false, continuation(queueMaxSize = 3).refill(full.player) { true })
        assertNull(asked)
    }

    @Test fun `a replaced queue is left alone`() = runTest {
        val episodes = PlaybackQueueContext.playlistEpisodes(listOf(episode(1)), "list-a")
        repository.replaceQueue(episodes)
        val queue = Queue(episodes, 0)

        assertNull(continuation().refill(queue.player) { false })
        assertFalse(queue.items.size > 1)
    }

    @Test fun `a saved queue replaced while the playlist is read is left alone`() = runTest {
        val episodes = PlaybackQueueContext.playlistEpisodes(listOf(episode(1), episode(2)), "list-a")
        repository.replaceQueue(episodes)
        val queue = Queue(episodes, 1)
        val dispatcher = StandardTestDispatcher(testScheduler)
        val continuation = PlaylistQueueContinuation(
            database = database,
            queueRepository = repository,
            nextEpisodes = { _, limit, _ ->
                // A cloud queue apply lands in Room while the playlist is read; the player isn't touched.
                repository.replaceQueue(listOf(episode(5)))
                (10 until 10 + limit).map(::episode)
            },
            mainDispatcher = dispatcher,
            ioDispatcher = dispatcher,
            queueMaxSize = 50,
            mediaIdPrefixStripper = SmartQueueRefillPolicy::stripQueuePrefixes,
            batchSize = 3,
        )

        assertEquals(false, continuation.refill(queue.player) { true })
        assertEquals(listOf("5"), database.queueDao().getAllQueueItemsSync().map { it.episodeId })
        assertEquals(listOf("1", "2"), queue.items.map { it.mediaId })
    }

    @Test fun `the service tries the playlist first and hands over to Smart Queue`() = runTest {
        val asked = mutableListOf<String>()
        fun playlist(answer: Boolean?): suspend () -> Boolean? = {
            asked += "playlist"
            answer
        }
        val smart: suspend () -> Boolean = {
            asked += "smart"
            true
        }

        assertTrue(refillPlaylistFirst(contextExhausted = true, playlistTopUp = playlist(true), smartQueue = smart))
        assertEquals(listOf("playlist"), asked)

        asked.clear()
        refillPlaylistFirst(contextExhausted = true, playlistTopUp = playlist(false), smartQueue = smart)
        assertEquals("nothing left: Smart Queue", listOf("playlist", "smart"), asked)

        asked.clear()
        refillPlaylistFirst(contextExhausted = true, playlistTopUp = playlist(null), smartQueue = smart)
        assertEquals("not a playlist's: Smart Queue", listOf("playlist", "smart"), asked)

        asked.clear()
        refillPlaylistFirst(contextExhausted = false, playlistTopUp = playlist(true), smartQueue = smart)
        assertEquals("a running queue never asks the playlist", listOf("smart"), asked)

        asked.clear()
        refillPlaylistFirst(contextExhausted = true, playlistTopUp = null, smartQueue = smart)
        assertEquals(listOf("smart"), asked)
    }
}
