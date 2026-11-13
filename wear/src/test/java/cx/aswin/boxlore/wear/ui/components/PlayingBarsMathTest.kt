package cx.aswin.boxlore.wear.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlayingBarsMathTest {
    @Test
    fun `every bar stays between its lowest height and full height`() {
        for (step in 0..40) {
            val heights = PlayingBarsMath.heights(step / 40f)

            assertEquals(PlayingBarsMath.BARS, heights.size)
            assertTrue(heights.all { it in 0.3f..1f }, "at $step: $heights")
        }
    }

    @Test
    fun `the bars are out of step with each other`() {
        val heights = PlayingBarsMath.heights(0.1f)

        assertNotEquals(heights[0], heights[1])
        assertNotEquals(heights[1], heights[2])
    }

    @Test
    fun `the movement loops without a jump`() {
        val start = PlayingBarsMath.heights(0f)
        val end = PlayingBarsMath.heights(1f)

        start.zip(end).forEach { (first, last) -> assertEquals(first, last, 0.001f) }
    }

    @Test
    fun `standing still, the bars are uneven so they still read as sound`() {
        assertEquals(PlayingBarsMath.BARS, PlayingBarsMath.resting.size)
        assertEquals(3, PlayingBarsMath.resting.toSet().size)
    }
}
