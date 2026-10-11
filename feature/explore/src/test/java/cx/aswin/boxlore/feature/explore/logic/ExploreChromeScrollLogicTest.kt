package cx.aswin.boxlore.feature.explore.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ExploreChromeScrollLogicTest {
    @Test
    fun `consumed downward scroll progressively collapses and upward scroll reveals`() {
        val down = ExploreChromeScrollLogic.offsetAfterScroll(0f, -24f, 48f, true)
        assertEquals(0.5f, ExploreChromeScrollLogic.fraction(down, 48f))
        assertEquals(12f, ExploreChromeScrollLogic.offsetAfterScroll(down, 12f, 48f, true))
        assertEquals(down, ExploreChromeScrollLogic.offsetAfterScroll(down, 0f, 48f, true))
    }

    @Test
    fun `flings and direction reversals remain bounded`() {
        assertEquals(48f, ExploreChromeScrollLogic.offsetAfterScroll(0f, -800f, 48f, true))
        assertEquals(0f, ExploreChromeScrollLogic.offsetAfterScroll(48f, 800f, 48f, true))
        assertEquals(1f, ExploreChromeScrollLogic.fraction(100f, 48f))
    }

    @Test
    fun `search and zero height controls stay expanded`() {
        assertEquals(0f, ExploreChromeScrollLogic.offsetAfterScroll(48f, -10f, 48f, false))
        assertEquals(0f, ExploreChromeScrollLogic.offsetAfterScroll(48f, -10f, 0f, true))
        assertEquals(0f, ExploreChromeScrollLogic.fraction(48f, 0f))
    }

    @Test
    fun `invalid deltas cannot corrupt chrome layout`() {
        assertEquals(12f, ExploreChromeScrollLogic.offsetAfterScroll(12f, Float.NaN, 48f, true))
        assertEquals(0f, ExploreChromeScrollLogic.offsetAfterScroll(12f, -10f, Float.POSITIVE_INFINITY, true))
        assertEquals(10f, ExploreChromeScrollLogic.offsetAfterScroll(Float.NaN, -10f, 48f, true))
        assertEquals(0f, ExploreChromeScrollLogic.fraction(Float.NaN, 48f))
    }
}
