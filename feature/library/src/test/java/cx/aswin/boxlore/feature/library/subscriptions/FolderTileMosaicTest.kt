package cx.aswin.boxlore.feature.library.subscriptions

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A folder's "+N" tile fills its preview by how many covers it has, never leaving a flat grey half. */
class FolderTileMosaicTest {
    @Test
    fun `one cover fills the tile, two sit side by side, three are a half and two quarters, more are 2x2`() {
        assertEquals(listOf(MosaicCell(0f, 0f, 1f, 1f)), overflowMosaicCells(1))
        assertEquals(listOf(MosaicCell(0f, 0f, 0.5f, 1f), MosaicCell(0.5f, 0f, 1f, 1f)), overflowMosaicCells(2))
        assertEquals(listOf(MosaicCell(0f, 0f, 0.5f, 1f), MosaicCell(0.5f, 0f, 1f, 0.5f), MosaicCell(0.5f, 0.5f, 1f, 1f)), overflowMosaicCells(3))
        for (count in 4..6) {
            val cells = overflowMosaicCells(count)
            assertEquals(4, cells.size)
            // The four quarters cover the tile exactly.
            assertEquals(1f, cells.sumOf { (it.width * it.height).toDouble() }.toFloat(), 0.0001f)
        }
    }

    @Test
    fun `every count's cells cover the whole tile`() {
        for (count in 1..3) assertEquals(1f, overflowMosaicCells(count).sumOf { (it.width * it.height).toDouble() }.toFloat(), 0.0001f)
    }
}
