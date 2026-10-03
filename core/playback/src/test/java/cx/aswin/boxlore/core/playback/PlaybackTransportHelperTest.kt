package cx.aswin.boxlore.core.playback

import androidx.media3.session.MediaController
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
