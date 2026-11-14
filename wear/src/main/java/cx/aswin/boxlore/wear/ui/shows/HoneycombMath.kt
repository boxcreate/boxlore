package cx.aswin.boxlore.wear.ui.shows

import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tanh

/** A cell of the honeycomb in axial coordinates; rows run left to right. */
internal data class HexCell(val q: Int, val r: Int)

/** A point in screen-radius units: 1 is the distance from the screen centre to its edge. */
internal data class HoneycombPoint(val x: Float, val y: Float)

/** Where one show's bubble is drawn, relative to the screen centre, in screen-radius units. */
internal data class HoneycombBubble(val index: Int, val x: Float, val y: Float, val diameter: Float)

/**
 * Geometry of the Your shows honeycomb. Shows sit on a hexagonal sheet larger than the screen,
 * ordered outward from the centre, and the sheet is panned under a fisheye: bubbles are full size
 * in the middle and shrink and bunch up toward the edge, so they never overlap or leave the round
 * screen. Everything is in screen-radius units, so it holds on any watch size.
 */
internal object HoneycombMath {
    /** Distance between neighbouring cells on the flat sheet. */
    const val SPACING = 0.52f

    /** Diameter of the bubble at the centre of the screen. */
    const val BASE_DIAMETER = 0.49f

    /** Bubbles smaller than this are not drawn. */
    const val MIN_VISIBLE_DIAMETER = 0.1f

    /** The fisheye pulls far cells in toward this distance from the centre. */
    private const val REACH = 1.2f

    /** Sheet distance at which a bubble has shrunk to nothing. */
    private const val FADE_DISTANCE = 1.3f
    private const val SHRINK_POWER = 2.2f
    private val ROW_HEIGHT = SPACING * sqrt(3f) / 2f

    private val DIRECTIONS =
        listOf(HexCell(1, 0), HexCell(1, -1), HexCell(0, -1), HexCell(-1, 0), HexCell(-1, 1), HexCell(0, 1))

    /** The first [count] cells walking outward ring by ring, so index 0 is the centre. */
    fun spiral(count: Int): List<HexCell> {
        if (count <= 0) return emptyList()
        val cells = ArrayList<HexCell>(count)
        cells += HexCell(0, 0)
        var ring = 1
        while (cells.size < count) {
            var q = -ring
            var r = ring
            for (direction in DIRECTIONS) {
                repeat(ring) {
                    if (cells.size < count) cells += HexCell(q, r)
                    q += direction.q
                    r += direction.r
                }
            }
            ring++
        }
        return cells
    }

    /** Where [cell] sits on the flat sheet. */
    fun center(cell: HexCell): HoneycombPoint = HoneycombPoint(SPACING * (cell.q + cell.r / 2f), ROW_HEIGHT * cell.r)

    /** The bubbles to draw for [cells] when the sheet point ([panX], [panY]) is under the screen centre. */
    fun bubbles(cells: List<HexCell>, panX: Float, panY: Float): List<HoneycombBubble> {
        val result = ArrayList<HoneycombBubble>()
        cells.forEachIndexed { index, cell ->
            val center = center(cell)
            val dx = center.x - panX
            val dy = center.y - panY
            val distance = hypot(dx, dy)
            val diameter = diameterAt(distance)
            if (diameter >= MIN_VISIBLE_DIAMETER) {
                val pulled = if (distance == 0f) 0f else REACH * tanh(distance / REACH) / distance
                result += HoneycombBubble(index, dx * pulled, dy * pulled, diameter)
            }
        }
        return result
    }

    /** Keeps the pan inside the sheet, so some show is always at the centre of the screen. */
    fun clampPan(cells: List<HexCell>, panX: Float, panY: Float): HoneycombPoint {
        val nearest = nearest(cells, panX, panY) ?: return HoneycombPoint(0f, 0f)
        val center = center(nearest)
        val dx = panX - center.x
        val dy = panY - center.y
        val distance = hypot(dx, dy)
        val limit = SPACING / 2f
        if (distance <= limit) return HoneycombPoint(panX, panY)
        val scale = limit / distance
        return HoneycombPoint(center.x + dx * scale, center.y + dy * scale)
    }

    /** The pan that puts the cell nearest to ([panX], [panY]) exactly at the centre. */
    fun snap(cells: List<HexCell>, panX: Float, panY: Float): HoneycombPoint =
        nearest(cells, panX, panY)?.let(::center) ?: HoneycombPoint(0f, 0f)

    private fun nearest(cells: List<HexCell>, panX: Float, panY: Float): HexCell? =
        cells.minByOrNull { cell ->
            val center = center(cell)
            hypot(center.x - panX, center.y - panY)
        }

    private fun diameterAt(distance: Float): Float =
        if (distance >= FADE_DISTANCE) 0f else BASE_DIAMETER * (1f - (distance / FADE_DISTANCE).pow(SHRINK_POWER))
}
