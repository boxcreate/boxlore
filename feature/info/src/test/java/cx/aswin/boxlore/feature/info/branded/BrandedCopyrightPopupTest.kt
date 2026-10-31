package cx.aswin.boxlore.feature.info.branded

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BrandedCopyrightPopupTest {
    // The popup's box: 10px of caret room above the card, 20px of shadow room below it.
    private fun place(anchorTop: Int, windowHeight: Int, card: Int = 200) = copyrightPopupTop(
        anchorTop = anchorTop,
        anchorBottom = anchorTop + 30,
        windowHeight = windowHeight,
        popupHeight = card + 30,
        gap = 6,
        caretRoom = 10,
        bottomRoom = 20,
        margin = 8,
    )

    @Test
    fun `the popup hangs below the © when it fits there`() {
        val placement = place(anchorTop = 100, windowHeight = 1000)
        assertFalse(placement.above)
        // The card starts the gap below the ©, the caret in the room above it.
        assertEquals(100 + 30 + 6, placement.top + 10)
    }

    @Test
    fun `near the bottom of the window it opens above the ©`() {
        val placement = place(anchorTop = 800, windowHeight = 1000)
        assertTrue(placement.above)
        // The card ends the gap above the ©.
        assertEquals(800 - 6, placement.top + 10 + 200)
    }

    @Test
    fun `in a short window it takes the roomier side and stays inside the margins`() {
        // 150px above the ©, 100px below: neither fits a 200px card.
        val placement = place(anchorTop = 156, windowHeight = 300)
        assertTrue(placement.above)
        assertEquals(8, placement.top + 10, "Held at the top margin")
        val below = place(anchorTop = 60, windowHeight = 300)
        assertFalse(below.above)
        assertEquals(300 - 8, below.top + 10 + 200, "Held at the bottom margin")
    }

    @Test
    fun `a card taller than the window starts at the top margin and scrolls`() {
        val placement = place(anchorTop = 200, windowHeight = 300, card = 400)
        assertEquals(8, placement.top + 10)
    }
}
