package cx.aswin.boxlore.tv.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class KeepInViewTest {
    private val list = 1000f
    private val margin = 40f

    @Test
    fun `an item already in view with its margin does not move the list`() {
        assertEquals(0f, KeepInView.scrollFor(offset = 100f, size = 200f, containerSize = list))
    }

    @Test
    fun `an item below or above the view moves it only as far as needed`() {
        assertEquals(900f + 200f + margin - list, KeepInView.scrollFor(offset = 900f, size = 200f, containerSize = list))
        assertEquals(-50f - margin, KeepInView.scrollFor(offset = -50f, size = 200f, containerSize = list))
    }

    @Test
    fun `an item taller than the view lines up its start`() {
        assertEquals(300f - margin, KeepInView.scrollFor(offset = 300f, size = 1200f, containerSize = list))
    }
}
