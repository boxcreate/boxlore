package cx.aswin.boxlore.core.playback

import androidx.media3.common.MediaItem
import androidx.media3.session.MediaController
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackTransportHelperTest {
    @Before fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        PlaybackActivationRequest.clear()
    }

    @After fun tearDown() {
        PlaybackActivationRequest.clear()
        Dispatchers.resetMain()
    }

    private fun TestScope.helper(savedPositionMs: Long = 50_000L) = PlaybackTransportHelper(
        scope = this,
        playerStateFlow = MutableStateFlow(PlayerState()),
        mediaHandle = PlaybackMediaControllerHandle(),
        storePendingEntryPoint = {},
        resolveInitialSeekMs = { _, _ -> savedPositionMs },
        resolvePersistedResumePositionMs = { _, _ -> null },
        playQueue = { _, _, _, _, _, _ -> error("A seek must not replace the queue") },
    )

    private fun episode(id: String) = Episode(id = id, title = "Episode $id", description = "", audioUrl = "https://example.com/$id.mp3")

    private fun TestScope.resumed(state: PlayerState): Pair<List<MediaItem>, Int> {
        val controller = mock(MediaController::class.java)
        `when`(controller.mediaItemCount).thenReturn(0)
        var loaded: Pair<List<MediaItem>, Int>? = null
        var startMs = -1L
        doAnswer { call ->
            @Suppress("UNCHECKED_CAST")
            loaded = (call.arguments[0] as List<MediaItem>) to (call.arguments[1] as Int)
            startMs = call.arguments[2] as Long
            null
        }.`when`(controller).setMediaItems(anyList(), anyInt(), anyLong())
        val handle = PlaybackMediaControllerHandle().apply { this.controller = controller }
        val helper =
            PlaybackTransportHelper(
                scope = this,
                playerStateFlow = MutableStateFlow(state),
                mediaHandle = handle,
                storePendingEntryPoint = {},
                resolveInitialSeekMs = { _, _ -> 0L },
                resolvePersistedResumePositionMs = { _, _ -> null },
                playQueue = { _, _, _, _, _, _ -> error("Resume must not go through playQueue") },
            )

        helper.resume()
        advanceUntilIdle()

        verify(controller).play()
        assertEquals("Resume keeps the position the mini player showed", 340_000L, startMs)
        return checkNotNull(loaded) { "Resume loaded nothing into the player" }
    }

    @Test fun `resume starts the episode the mini player shows even when the saved queue does not hold it`() = runTest {
        // Another device's episode was handed over while idle; the saved queue is still this phone's old one.
        val state =
            PlayerState(
                currentEpisode = episode("handed-over"),
                currentPodcast = Podcast(id = "1", title = "Show", artist = "", imageUrl = ""),
                position = 340_000L,
                queue = listOf(episode("old-first"), episode("old-second")),
            )

        val (items, index) = resumed(state)

        assertEquals("handed-over", items[index].mediaId)
        assertEquals(listOf("handed-over", "old-first", "old-second"), items.map { it.mediaId })
    }

    @Test fun `resume does not queue an episode twice when the queue holds it under another ID`() = runTest {
        val shown = episode("recipe-id")
        val state =
            PlayerState(
                currentEpisode = shown,
                currentPodcast = Podcast(id = "1", title = "Show", artist = "", imageUrl = ""),
                position = 340_000L,
                queue = listOf(episode("first"), episode("feed-id").copy(audioUrl = shown.audioUrl)),
            )

        val (items, index) = resumed(state)

        assertEquals(1, index)
        assertEquals(listOf("first", "feed-id"), items.map { it.mediaId })
    }

    @Test fun `resume keeps a saved queue that holds the shown episode and starts at it`() = runTest {
        val state =
            PlayerState(
                currentEpisode = episode("second"),
                currentPodcast = Podcast(id = "1", title = "Show", artist = "", imageUrl = ""),
                position = 340_000L,
                queue = listOf(episode("first"), episode("second"), episode("third")),
            )

        val (items, index) = resumed(state)

        assertEquals(1, index)
        assertEquals(listOf("first", "second", "third"), items.map { it.mediaId })
    }

    @Test fun `seeking the current item clears stale activation without recording a new request`() = runTest {
        val controller = mock(MediaController::class.java)
        `when`(controller.currentMediaItemIndex).thenReturn(0)
        PlaybackActivationRequest.set("123", 10_000L)

        helper().restorePositionAndSeek(controller, "123", 0, null)
        advanceUntilIdle()

        verify(controller).seekTo(0, 50_000L)
        verify(controller).play()
        assertNull(PlaybackActivationRequest.consume("123"))
    }

    @Test fun `changing items carries its deliberate position only to the matching activation`() = runTest {
        val controller = mock(MediaController::class.java)
        `when`(controller.currentMediaItemIndex).thenReturn(0)

        helper().restorePositionAndSeek(controller, "123", 1, null)
        advanceUntilIdle()

        verify(controller).seekTo(1, 50_000L)
        assertEquals(PlaybackActivationRequest.Position(50_000L, false), PlaybackActivationRequest.consume("123"))
        assertNull(PlaybackActivationRequest.consume("123"))
    }

    @Test fun `policy selected zero on a different item carries intro trim intent`() = runTest {
        val controller = mock(MediaController::class.java)
        `when`(controller.currentMediaItemIndex).thenReturn(0)

        helper(0L).restorePositionAndSeek(controller, "123", 1, null)
        advanceUntilIdle()

        verify(controller).seekTo(1, 0L)
        assertEquals(PlaybackActivationRequest.Position(0L, true), PlaybackActivationRequest.consume("123"))
    }
}
