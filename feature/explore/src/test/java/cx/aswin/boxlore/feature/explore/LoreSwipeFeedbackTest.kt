package cx.aswin.boxlore.feature.explore

import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoreSwipeFeedbackTest {
    private val threshold = LoreSwipeThresholdDp.toFloat()

    @Test
    fun `only the matching action appears and slides in from the physical swipe side`() {
        val skip = loreSwipeFeedbackMotion(-threshold / 2, threshold, SwipeDirection.Left)
        val queue = loreSwipeFeedbackMotion(threshold / 2, threshold, SwipeDirection.Right)
        assertTrue(skip.alpha > 0f && skip.alpha < 1f)
        assertEquals(skip.alpha, queue.alpha)
        assertEquals(skip.scale, queue.scale)
        assertTrue(skip.translationDp < 0f)
        assertEquals(-skip.translationDp, queue.translationDp)
        assertEquals(0f, loreSwipeFeedbackMotion(-threshold / 2, threshold, SwipeDirection.Right).alpha)
        assertEquals(0f, loreSwipeFeedbackMotion(threshold / 2, threshold, SwipeDirection.Left).alpha)
    }

    @Test
    fun `minor movement is hidden and cancelled or reversed travel unwinds the indicator`() {
        for (offset in listOf(-8f, 0f, 8f)) {
            for (direction in SwipeDirection.entries) {
                assertEquals(0f, loreSwipeFeedbackMotion(offset, threshold, direction).alpha)
            }
        }
        val frames = listOf(-threshold, -threshold * 0.75f, -threshold / 2, -8f, 0f)
            .map { loreSwipeFeedbackMotion(it, threshold, SwipeDirection.Left) }
        assertTrue(frames.zipWithNext().all { (previous, next) -> next.alpha <= previous.alpha && next.scale <= previous.scale })
        assertEquals(0f, frames.last().alpha)
        assertEquals(0f, loreSwipeFeedbackMotion(20f, threshold, SwipeDirection.Left).alpha)
        assertTrue(loreSwipeFeedbackMotion(20f, threshold, SwipeDirection.Right).alpha > 0f)
    }

    @Test
    fun `growth is bounded during exit and invalid gesture values stay hidden`() {
        for (direction in SwipeDirection.entries) {
            val sign = if (direction == SwipeDirection.Left) -1f else 1f
            val frames = (0..200).map { loreSwipeFeedbackMotion(sign * it, threshold, direction) }
            assertTrue(frames.all { it.alpha in 0f..1f && it.scale in 0.62f..1f })
            assertTrue(frames.zipWithNext().all { (previous, next) -> next.alpha >= previous.alpha && next.scale >= previous.scale })
            val completed = loreSwipeFeedbackMotion(sign * 1500f, threshold, direction)
            assertEquals(1f, completed.alpha)
            assertEquals(1f, completed.scale)
            assertEquals(0f, abs(completed.translationDp))
            for (offset in listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)) {
                assertEquals(0f, loreSwipeFeedbackMotion(offset, threshold, direction).alpha)
            }
            for (invalidThreshold in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
                assertEquals(0f, loreSwipeFeedbackMotion(sign * 44f, invalidThreshold, direction).alpha)
            }
        }
    }
}
