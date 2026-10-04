package cx.aswin.boxlore.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AdaptivePlayerChromeControllerTest {
    private val controller = AdaptivePlayerChromeController(48f).apply {
        configure(enabled = true, episodeId = "episode-a", route = "home")
    }

    private fun browse(y: Float) {
        assertEquals(Offset.Zero, controller.scrollConnection.onPreScroll(Offset(0f, y), NestedScrollSource.UserInput))
        assertEquals(
            Offset.Zero,
            controller.scrollConnection.onPostScroll(Offset(0f, y), Offset.Zero, NestedScrollSource.UserInput),
        )
    }

    @Test
    fun `metadata recomposition and tab changes preserve compactness`() {
        browse(-48f)
        assertTrue(controller.isCompact)
        controller.configure(true, "episode-a", "home")
        assertTrue(controller.isCompact)
        controller.configure(true, "episode-a", "explore")
        assertTrue(controller.isCompact)
    }

    @Test
    fun `route change discards partial travel between different lists`() {
        browse(-30f)
        controller.configure(true, "episode-a", "library")
        browse(-30f)
        assertFalse(controller.isCompact)
    }

    @Test
    fun `new episode and disabled navigation restore the bar`() {
        browse(-48f)
        controller.configure(true, "episode-b", "home")
        assertFalse(controller.isCompact)
        browse(-48f)
        controller.configure(false, "episode-b", "home")
        assertFalse(controller.isCompact)
        browse(-48f)
        assertFalse(controller.isCompact)
    }

    @Test
    fun `player interaction freezes direction until its callback clears busy`() {
        browse(-48f)
        controller.onSheetInteractionChanged(true)
        browse(100f)
        assertTrue(controller.isCompact)
        controller.onSheetInteractionChanged(false)
        browse(48f)
        assertFalse(controller.isCompact)
    }

    @Test
    fun `header pre-consumption is counted and boundary pull is ignored`() {
        val connection = controller.scrollConnection
        connection.onPreScroll(Offset(0f, -48f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset.Zero, Offset.Zero, NestedScrollSource.UserInput)
        assertTrue(controller.isCompact)
        connection.onPreScroll(Offset(0f, 100f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset.Zero, Offset(0f, 100f), NestedScrollSource.UserInput)
        assertTrue(controller.isCompact)
    }

    @Test
    fun `mismatched source never borrows stale pre-scroll travel`() {
        val connection = controller.scrollConnection
        connection.onPreScroll(Offset(0f, -100f), NestedScrollSource.SideEffect)
        connection.onPostScroll(Offset(0f, 48f), Offset.Zero, NestedScrollSource.UserInput)
        assertFalse(controller.isCompact)
        connection.onPreScroll(Offset(0f, -48f), NestedScrollSource.UserInput)
        connection.onPostScroll(Offset(0f, -48f), Offset.Zero, NestedScrollSource.SideEffect)
        assertFalse(controller.isCompact)
    }
}
