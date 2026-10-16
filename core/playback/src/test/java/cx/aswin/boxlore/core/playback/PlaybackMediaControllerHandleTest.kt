package cx.aswin.boxlore.core.playback

import androidx.media3.session.MediaController
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Regression (0.0.27): "Session rejected the connection request" was thrown from the controller
 * future on the main thread and crashed the app at startup.
 */
class PlaybackMediaControllerHandleTest {
    @Test
    fun `rejected connection returns no controller and asks for one reconnect`() = runTest {
        val handle = PlaybackMediaControllerHandle()
        var reconnects = 0
        handle.reconnect = { reconnects++ }
        val failed = Futures.immediateFailedFuture<MediaController>(SecurityException("Session rejected the connection request."))
        handle.future = failed

        assertNull(handle.awaitController())
        assertNull(handle.future)
        assertEquals(1, reconnects)

        // The main-thread listener reporting the same failed attempt must not reconnect twice.
        handle.onConnectionFailed(failed, SecurityException())
        assertEquals(1, reconnects)
    }

    @Test
    fun `no pending connection returns null without reconnecting`() = runTest {
        val handle = PlaybackMediaControllerHandle()
        var reconnects = 0
        handle.reconnect = { reconnects++ }

        assertNull(handle.awaitController())
        assertEquals(0, reconnects)
    }

    @Test
    fun `reconnect attempts are bounded and back off`() {
        assertEquals(3, MediaControllerReconnectPolicy.MAX_ATTEMPTS)
        assertEquals(1_000L, MediaControllerReconnectPolicy.delayMs(1))
        assertEquals(3_000L, MediaControllerReconnectPolicy.delayMs(3))
    }
}
