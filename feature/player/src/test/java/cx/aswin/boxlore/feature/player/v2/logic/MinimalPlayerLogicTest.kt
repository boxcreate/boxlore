package cx.aswin.boxlore.feature.player.v2.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MinimalPlayerLogicTest {
    @Test
    fun `standard and unknown preferences retain standard player and video remains viewable`() {
        assertFalse(useMinimalPlayer("standard", false))
        assertFalse(useMinimalPlayer("unknown", false))
        assertTrue(useMinimalPlayer(" BUNPOD ", false))
        assertFalse(useMinimalPlayer("bunpod", true))
    }

    @Test
    fun `remaining time rounds partial minutes and protects restored invalid positions`() {
        assertEquals(RemainingTimeParts(1, 14), minimalRemainingTime(4_440_000L, 0L))
        assertEquals(RemainingTimeParts(0, 1), minimalRemainingTime(60_000L, 59_999L))
        assertEquals(RemainingTimeParts(0, 0), minimalRemainingTime(60_000L, 80_000L))
        assertEquals(RemainingTimeParts(0, 0), minimalRemainingTime(-1L, -500L))
        assertEquals(RemainingTimeParts(0, 2), minimalRemainingTime(120_000L, -500L))
        assertTrue(minimalRemainingTime(Long.MAX_VALUE, 0L).hours > 0)
    }

    @Test
    fun `queue requires a deliberate upward gesture and triggers once per swipe`() {
        val gesture = MinimalQueueSwipe(56f)
        assertFalse(gesture.drag(-20f))
        assertFalse(gesture.drag(-20f))
        assertTrue(gesture.drag(-16f))
        assertFalse(gesture.drag(-80f))
        gesture.reset()
        assertTrue(gesture.drag(-60f))
    }

    @Test
    fun `downward collapse consumed list scrolling and invalid deltas clear queue intent`() {
        val gesture = MinimalQueueSwipe(56f)
        listOf(20f, 0f, Float.NaN).forEach { interruption ->
            gesture.reset()
            assertFalse(gesture.drag(-40f))
            assertFalse(gesture.drag(interruption))
            assertFalse(gesture.drag(-30f))
        }
    }
}
