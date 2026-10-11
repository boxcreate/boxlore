package cx.aswin.boxlore.feature.home.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeroPeekMotionTest {
    @Test fun `mask progress clamps and handles unavailable geometry`() {
        assertEquals(0f, HeroPeekMotion.expansion(20f, 40f, 320f), 0f)
        assertEquals(0.5f, HeroPeekMotion.expansion(180f, 40f, 320f), 0f)
        assertEquals(1f, HeroPeekMotion.expansion(400f, 40f, 320f), 0f)
        assertEquals(1f, HeroPeekMotion.expansion(0f, 0f, 0f), 0f)
        assertEquals(1f, HeroPeekMotion.expansion(Float.NaN, 40f, 320f), 0f)
        assertEquals(1f, HeroPeekMotion.expansion(40f, Float.POSITIVE_INFINITY, 320f), 0f)
    }

    @Test fun `content stays hidden in a peek and completes before the card settles`() {
        assertEquals(0f, HeroPeekMotion.contentAlpha(0f), 0f)
        assertEquals(0f, HeroPeekMotion.contentAlpha(0.12f), 0f)
        assertTrue(HeroPeekMotion.contentAlpha(0.3f) in 0.49f..0.51f)
        assertEquals(1f, HeroPeekMotion.contentAlpha(0.48f), 0.001f)
        assertEquals(1f, HeroPeekMotion.peekAlpha(0f), 0f)
        assertTrue(HeroPeekMotion.peekAlpha(0.06f) in 0.49f..0.51f)
        assertEquals(0f, HeroPeekMotion.peekAlpha(0.12f), 0f)
        assertEquals(0f, HeroPeekMotion.peekAlpha(1f), 0f)
        val values = (0..100).map { HeroPeekMotion.contentAlpha(it / 100f) }
        assertTrue(values.zipWithNext().all { (a, b) -> a <= b })
        val peeks = (0..100).map { HeroPeekMotion.peekAlpha(it / 100f) }
        assertTrue(peeks.zipWithNext().all { (a, b) -> a >= b })
        assertTrue((0..100).all { HeroPeekMotion.peekAlpha(it / 100f) * HeroPeekMotion.contentAlpha(it / 100f) == 0f })
    }
}
