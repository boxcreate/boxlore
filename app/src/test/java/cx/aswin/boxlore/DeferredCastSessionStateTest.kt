package cx.aswin.boxlore

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Regression for startup ANRs: Cast state used to construct playback inside Application.onCreate.
 * It now waits until playback exists, without losing the latest value.
 */
class DeferredCastSessionStateTest {
    @Test
    fun latestStateBeforePlaybackIsAppliedOnceWhenAttached() {
        val state = DeferredCastSessionState()
        state.submit(null)
        state.submit(true)
        val applied = mutableListOf<Boolean?>()

        state.attach { applied += it }

        assertEquals(listOf<Boolean?>(true), applied)
    }

    @Test
    fun nothingIsAppliedWhenNoStateArrivedBeforePlayback() {
        val state = DeferredCastSessionState()
        val applied = mutableListOf<Boolean?>()

        state.attach { applied += it }

        assertEquals(emptyList<Boolean?>(), applied)
    }

    @Test
    fun updatesAfterAttachPassStraightThrough() {
        val state = DeferredCastSessionState()
        val applied = mutableListOf<Boolean?>()
        state.attach { applied += it }

        state.submit(true)
        state.submit(false)

        assertEquals(listOf<Boolean?>(true, false), applied)
    }
}
