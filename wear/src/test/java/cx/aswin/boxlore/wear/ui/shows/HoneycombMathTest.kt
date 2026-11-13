package cx.aswin.boxlore.wear.ui.shows

import kotlin.math.abs
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HoneycombMathTest {
    @Test
    fun `spiral starts at the centre and never repeats a cell`() {
        val cells = HoneycombMath.spiral(61)

        assertEquals(HexCell(0, 0), cells.first())
        assertEquals(61, cells.size)
        assertEquals(61, cells.toSet().size)
    }

    @Test
    fun `spiral fills whole rings in order`() {
        val cells = HoneycombMath.spiral(19)
        val distances = cells.map { ringOf(it) }

        assertEquals(listOf(0) + List(6) { 1 } + List(12) { 2 }, distances)
    }

    @Test
    fun `spiral of nothing is empty and of one is the centre`() {
        assertTrue(HoneycombMath.spiral(0).isEmpty())
        assertEquals(listOf(HexCell(0, 0)), HoneycombMath.spiral(1))
    }

    @Test
    fun `neighbouring cells sit one spacing apart`() {
        val origin = HoneycombMath.center(HexCell(0, 0))

        HoneycombMath.spiral(7).drop(1).forEach { cell ->
            val center = HoneycombMath.center(cell)
            assertEquals(HoneycombMath.SPACING, hypot(center.x - origin.x, center.y - origin.y), 0.0001f)
        }
    }

    @Test
    fun `the centred show is drawn full size in the middle`() {
        val bubbles = HoneycombMath.bubbles(HoneycombMath.spiral(19), 0f, 0f)
        val centre = bubbles.first { it.index == 0 }

        assertEquals(0f, centre.x, 0.0001f)
        assertEquals(0f, centre.y, 0.0001f)
        assertEquals(HoneycombMath.BASE_DIAMETER, centre.diameter, 0.0001f)
    }

    @Test
    fun `bubbles shrink the further they are from the centre`() {
        val bubbles = HoneycombMath.bubbles(HoneycombMath.spiral(19), 0f, 0f).sortedBy { hypot(it.x, it.y) }

        bubbles.zipWithNext().forEach { (nearer, further) ->
            assertTrue(further.diameter <= nearer.diameter + 0.0001f)
        }
    }

    @Test
    fun `bubbles never overlap or leave the round screen wherever the sheet is panned`() {
        val cells = HoneycombMath.spiral(61)
        var pan = -1.3f
        while (pan <= 1.3f) {
            var otherPan = -1.3f
            while (otherPan <= 1.3f) {
                val bubbles = HoneycombMath.bubbles(cells, pan, otherPan)
                bubbles.forEachIndexed { index, bubble ->
                    assertTrue(hypot(bubble.x, bubble.y) + bubble.diameter / 2f <= 1.0001f, "bubble leaves the screen at $pan, $otherPan")
                    bubbles.drop(index + 1).forEach { other ->
                        val gap = hypot(bubble.x - other.x, bubble.y - other.y) - (bubble.diameter + other.diameter) / 2f
                        assertTrue(gap >= 0f, "bubbles overlap at $pan, $otherPan")
                    }
                }
                otherPan += 0.05f
            }
            pan += 0.05f
        }
    }

    @Test
    fun `a full screen shows about nineteen shows`() {
        val visible = HoneycombMath.bubbles(HoneycombMath.spiral(61), 0f, 0f).size

        assertTrue(visible in 19..21, "visible was $visible")
    }

    @Test
    fun `panning onto another show puts it in the centre at full size`() {
        val cells = HoneycombMath.spiral(19)
        val target = HoneycombMath.center(cells[9])

        val bubble = HoneycombMath.bubbles(cells, target.x, target.y).first { it.index == 9 }

        assertEquals(0f, hypot(bubble.x, bubble.y), 0.0001f)
        assertEquals(HoneycombMath.BASE_DIAMETER, bubble.diameter, 0.0001f)
    }

    @Test
    fun `pan is held within half a step of the nearest show`() {
        val cells = HoneycombMath.spiral(7)

        val clamped = HoneycombMath.clampPan(cells, 5f, 0f)
        val nearest = cells.minOf { cell ->
            val center = HoneycombMath.center(cell)
            hypot(center.x - clamped.x, center.y - clamped.y)
        }

        assertEquals(HoneycombMath.SPACING / 2f, nearest, 0.0001f)
    }

    @Test
    fun `a pan already over a show is left alone`() {
        val cells = HoneycombMath.spiral(7)

        val clamped = HoneycombMath.clampPan(cells, 0.1f, -0.05f)

        assertEquals(HoneycombPoint(0.1f, -0.05f), clamped)
    }

    @Test
    fun `settling moves to the nearest show`() {
        val cells = HoneycombMath.spiral(7)
        val right = HoneycombMath.center(HexCell(1, 0))

        val snapped = HoneycombMath.snap(cells, right.x - 0.1f, 0.04f)

        assertEquals(right, snapped)
    }

    @Test
    fun `with no shows the sheet stays at the origin`() {
        assertEquals(HoneycombPoint(0f, 0f), HoneycombMath.clampPan(emptyList(), 2f, 2f))
        assertEquals(HoneycombPoint(0f, 0f), HoneycombMath.snap(emptyList(), 2f, 2f))
        assertTrue(HoneycombMath.bubbles(emptyList(), 0f, 0f).isEmpty())
    }

    private fun ringOf(cell: HexCell): Int = maxOf(abs(cell.q), abs(cell.r), abs(cell.q + cell.r))
}
