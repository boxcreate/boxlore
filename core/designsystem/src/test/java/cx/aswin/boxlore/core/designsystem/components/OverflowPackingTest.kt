package cx.aswin.boxlore.core.designsystem.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class OverflowPackingTest {
    @Test fun `all chips fit without a redundant more control`() {
        assertEquals(OverflowPacking(listOf(0, 1, 2), emptyList()), packOverflowItems(listOf(60, 80, 90), 246, 8, 70, null))
    }

    @Test fun `one pixel less reserves more and preserves remaining canonical order`() {
        assertEquals(OverflowPacking(listOf(0, 1), listOf(2)), packOverflowItems(listOf(60, 80, 90), 245, 8, 70, null))
    }

    @Test fun `width growth reveals more than the old fixed shortlist`() {
        val widths = List(20) { 80 }
        assertEquals(7, packOverflowItems(widths, 720, 8, 80, null).visible.size)
        assertEquals(12, packOverflowItems(widths, 1200, 8, 80, null).visible.size)
    }

    @Test fun `late selection remains inline while overflow excludes every visible item`() {
        val packing = packOverflowItems(List(8) { 80 }, 360, 8, 70, 7)
        assertEquals(listOf(0, 7, 1), packing.visible)
        assertEquals(listOf(2, 3, 4, 5, 6), packing.remaining)
    }

    @Test fun `required controls use scroll fallback instead of hiding the selection`() {
        assertEquals(OverflowPacking(listOf(0, 3), listOf(1, 2)), packOverflowItems(List(4) { 120 }, 200, 8, 90, 3))
    }
}
