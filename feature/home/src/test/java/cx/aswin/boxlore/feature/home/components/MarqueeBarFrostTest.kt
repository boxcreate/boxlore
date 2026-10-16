package cx.aswin.boxlore.feature.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The frost behind Marquee's top bar is full behind the bar and fades on an S-curve, so no line shows. */
class MarqueeBarFrostTest {
    @Test fun `the frost is full until its fade, then falls steadily to nothing at its foot`() {
        val stops = marqueeBarFrostStops(fadeFrom = 0.6f)
        assertEquals(0f to 1f, stops.first())
        assertEquals(0.6f to 1f, stops[1])
        assertEquals(1f to 0f, stops.last())
        stops.drop(1).zipWithNext().forEach { (a, b) ->
            assertTrue("positions move down", b.first > a.first)
            assertTrue("strength only falls", b.second < a.second)
        }
    }

    @Test fun `both ends of the fade are gentle, so neither shows as a line`() {
        val tail = marqueeBarFrostStops(fadeFrom = 0f).drop(2)
        // A tenth of the way in it has barely started; a tenth from the end it has nearly finished.
        assertTrue("gentle start (${tail.first().second})", tail.first().second > 0.95f)
        assertTrue("gentle finish (${tail[tail.size - 2].second})", tail[tail.size - 2].second < 0.05f)
        // No single step drops by more than a fifth, so the fade never jumps.
        (listOf(0f to 1f) + tail).zipWithNext().forEach { (a, b) -> assertTrue("step ${a.second} to ${b.second}", a.second - b.second < 0.2f) }
    }

    @Test fun `a fade start outside the strip is clamped`() {
        assertEquals(1f to 1f, marqueeBarFrostStops(2f)[1])
        assertEquals(0f to 1f, marqueeBarFrostStops(-1f)[1])
    }
}
