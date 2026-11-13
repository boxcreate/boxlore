package cx.aswin.boxlore.wear.ui.components

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenFitTest {
    @Test
    fun `the design size and larger watches are never scaled`() {
        assertEquals(1f, ScreenFit.scale(225))
        assertEquals(1f, ScreenFit.scale(227))
        assertEquals(1f, ScreenFit.scale(240))
        assertFalse(ScreenFit.isCompact(ScreenFit.scale(227)))
    }

    @Test
    fun `a small round watch is scaled down and treated as compact`() {
        val scale = ScreenFit.scale(192)

        assertEquals(192f / 225f, scale, 0.0001f)
        assertTrue(ScreenFit.isCompact(scale))
    }

    @Test
    fun `a slightly smaller watch scales without dropping anything`() {
        val scale = ScreenFit.scale(213)

        assertTrue(scale < 1f)
        assertFalse(ScreenFit.isCompact(scale))
    }

    @Test
    fun `tiny screens stop shrinking at the smallest scale`() {
        assertEquals(ScreenFit.SMALLEST, ScreenFit.scale(120))
    }
}
