package cx.aswin.boxlore.feature.explore

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoreDeckMotionTest {
    private val threshold = 88f

    @Test
    fun `a full swipe leaves every card exactly where the card in front rested, so promotion never snaps`() {
        for (depth in 1..LoreDeckDepth + 1) {
            assertEquals(loreDeckPose(depth - 1, 0f), loreDeckPose(depth, 1f), "depth $depth at a full swipe")
        }
        assertEquals(loreDeckPose(0, 0f), loreDeckPose(0, 1f))
    }

    @Test
    fun `the waiting card stays hidden at rest and fades in with the swipe`() {
        val waiting = LoreDeckDepth + 1
        assertEquals(0f, loreDeckPose(waiting, 0f).alpha)
        val frames = (0..20).map { loreDeckPose(waiting, it / 20f).alpha }
        assertTrue(frames.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(1f, loreDeckPose(waiting, 1f).alpha)
        assertEquals(1f, loreDeckPose(1, 0.5f).alpha)
    }

    @Test
    fun `invalid progress keeps cards at rest`() {
        for (progress in listOf(Float.NaN, Float.POSITIVE_INFINITY, -1f)) {
            assertEquals(loreDeckPose(1, 0f), loreDeckPose(1, progress))
        }
    }

    @Test
    fun `a released cue holds its size and fades instead of riding the card out`() {
        for (direction in SwipeDirection.entries) {
            val sign = if (direction == SwipeDirection.Left) -1f else 1f
            val atRelease = loreSwipeFeedbackMotion(sign * 120f, threshold, direction)
            val flying = loreSwipeCueMotion(sign * 1400f, sign * 120f, exiting = true, exitFade = 0.5f, threshold = threshold, direction = direction)
            assertEquals(atRelease.scale, flying.scale)
            assertEquals(atRelease.alpha * 0.5f, flying.alpha)
            assertEquals(0f, loreSwipeCueMotion(sign * 1400f, sign * 120f, exiting = true, exitFade = 0f, threshold = threshold, direction = direction).alpha)
            // A button action releases at rest, so no cue flashes while the card leaves.
            assertEquals(0f, loreSwipeCueMotion(sign * 1400f, 0f, exiting = true, exitFade = 1f, threshold = threshold, direction = direction).alpha)
            assertEquals(loreSwipeFeedbackMotion(sign * 40f, threshold, direction), loreSwipeCueMotion(sign * 40f, 0f, exiting = false, exitFade = 1f, threshold = threshold, direction = direction))
        }
    }
}
