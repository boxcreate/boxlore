package cx.aswin.boxlore.wear.ui.components

import cx.aswin.boxlore.wear.ui.home.HomeDomeRules
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DomeGeometryTest {
    private val radius = 113f
    private val gap = 4f

    @Test
    fun `the dome has no width at the very top and widens on the way down`() {
        val widths = listOf(4f, 20f, 40f, 80f, 113f).map { DomeGeometry.halfWidth(radius, gap, it) }

        assertEquals(0f, widths.first(), 0.001f)
        assertEquals(widths.sorted(), widths)
        assertEquals(radius - gap, widths.last(), 0.001f)
    }

    @Test
    fun `it follows the screen's edge at the gap`() {
        // 60 below the top is 53 above the centre: the edge is sqrt(109^2 - 53^2) from the middle.
        assertEquals(95.25f, DomeGeometry.halfWidth(radius, gap, 60f), 0.01f)
    }

    @Test
    fun `below the middle of the screen it keeps its widest width`() {
        assertEquals(radius - gap, DomeGeometry.halfWidth(radius, gap, 140f), 0.001f)
        assertEquals(radius - gap, DomeGeometry.halfWidth(radius, gap, 400f), 0.001f)
    }

    @Test
    fun `a gap as large as the screen leaves nothing`() {
        assertEquals(0f, DomeGeometry.halfWidth(radius, radius, 50f), 0.001f)
    }

    @Test
    fun `a short show name shares a row with the time left, a long one gets its own`() {
        assertTrue(HomeDomeRules.sharesOneRow(showNameWidth = 80, timeWidth = 90, fixedWidth = 40, available = 210))
        assertTrue(HomeDomeRules.sharesOneRow(showNameWidth = 80, timeWidth = 90, fixedWidth = 40, available = 211))
        assertFalse(HomeDomeRules.sharesOneRow(showNameWidth = 81, timeWidth = 90, fixedWidth = 40, available = 210))
        assertFalse(HomeDomeRules.sharesOneRow(showNameWidth = 260, timeWidth = 90, fixedWidth = 40, available = 210))
    }

    @Test
    fun `words that do not fit give way before the time left is cut`() {
        assertTrue(HomeDomeRules.fits(needed = 140, available = 140))
        assertFalse(HomeDomeRules.fits(needed = 141, available = 140))
    }

    @Test
    fun `with no show name there is only ever one row`() {
        assertTrue(HomeDomeRules.sharesOneRow(showNameWidth = 0, timeWidth = 500, fixedWidth = 40, available = 100))
    }

    @Test
    fun `progress fills the arc's left part first, then the right`() {
        assertEquals(0f to 0f, HomeDomeRules.arcFill(0f))
        assertEquals(0.5f to 0f, HomeDomeRules.arcFill(0.25f))
        assertEquals(1f to 0f, HomeDomeRules.arcFill(0.5f))
        assertEquals(1f to 0.5f, HomeDomeRules.arcFill(0.75f))
        assertEquals(1f to 1f, HomeDomeRules.arcFill(1f))
        assertEquals(1f to 1f, HomeDomeRules.arcFill(3f))
        assertEquals(0f to 0f, HomeDomeRules.arcFill(-1f))
    }

    @Test
    fun `the name gets three lines, two on a small watch`() {
        assertEquals(3, HomeDomeRules.nameLines(compact = false))
        assertEquals(2, HomeDomeRules.nameLines(compact = true))
    }
}
