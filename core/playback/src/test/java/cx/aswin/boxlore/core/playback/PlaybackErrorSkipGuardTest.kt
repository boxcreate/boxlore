package cx.aswin.boxlore.core.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression: when every stream failed (offline, CDN outage), the error handler skipped forever
 * because Smart Queue kept refilling, and it called play() after each error so pause never stuck.
 */
class PlaybackErrorSkipGuardTest {
    @Test
    fun `stops skipping after three failures in a row`() {
        val guard = PlaybackErrorSkipGuard()
        assertEquals(PlaybackErrorSkipGuard.Action.SKIP_AND_PLAY, guard.onError(hasNext = true, playWhenReady = true))
        assertEquals(PlaybackErrorSkipGuard.Action.SKIP_AND_PLAY, guard.onError(hasNext = true, playWhenReady = true))
        assertEquals(PlaybackErrorSkipGuard.Action.STOP, guard.onError(hasNext = true, playWhenReady = true))
        // An endless refill keeps offering a next item; the guard never resumes skipping on its own.
        repeat(20) {
            assertEquals(PlaybackErrorSkipGuard.Action.STOP, guard.onError(hasNext = true, playWhenReady = true))
        }
    }

    @Test
    fun `never resumes playback the listener paused`() {
        val guard = PlaybackErrorSkipGuard()
        assertEquals(PlaybackErrorSkipGuard.Action.SKIP_PAUSED, guard.onError(hasNext = true, playWhenReady = false))
        assertEquals(PlaybackErrorSkipGuard.Action.SKIP_PAUSED, guard.onError(hasNext = true, playWhenReady = false))
        assertEquals(PlaybackErrorSkipGuard.Action.STOP, guard.onError(hasNext = true, playWhenReady = false))
    }

    @Test
    fun `a successful episode resets the allowance`() {
        val guard = PlaybackErrorSkipGuard()
        guard.onError(hasNext = true, playWhenReady = true)
        guard.onError(hasNext = true, playWhenReady = true)
        guard.onPlaybackSucceeded()
        assertEquals(0, guard.consecutiveFailures)
        assertEquals(PlaybackErrorSkipGuard.Action.SKIP_AND_PLAY, guard.onError(hasNext = true, playWhenReady = true))
        assertEquals(PlaybackErrorSkipGuard.Action.SKIP_AND_PLAY, guard.onError(hasNext = true, playWhenReady = true))
    }

    @Test
    fun `a new listener-started queue resets the allowance`() {
        val guard = PlaybackErrorSkipGuard()
        repeat(3) { guard.onError(hasNext = true, playWhenReady = true) }
        guard.reset()
        assertEquals(PlaybackErrorSkipGuard.Action.SKIP_AND_PLAY, guard.onError(hasNext = true, playWhenReady = true))
    }

    @Test
    fun `last item failing clears the queue`() {
        val guard = PlaybackErrorSkipGuard()
        assertEquals(PlaybackErrorSkipGuard.Action.CLEAR_QUEUE, guard.onError(hasNext = false, playWhenReady = true))
    }

    @Test
    fun `stop message tells the listener playback is paused instead of skipping`() {
        val local = PlaybackErrorMessages.forAction(PlaybackErrorSkipGuard.Action.STOP, isRemote = false)
        val cast = PlaybackErrorMessages.forAction(PlaybackErrorSkipGuard.Action.STOP, isRemote = true)
        assertTrue(!local.contains("skipping"))
        assertTrue(cast.contains("Cast"))
        assertEquals("Stream unavailable, skipping…", PlaybackErrorMessages.forAction(PlaybackErrorSkipGuard.Action.SKIP_AND_PLAY, isRemote = false))
    }
}
