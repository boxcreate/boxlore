package cx.aswin.boxlore.feature.explore

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LoreTabletPaneSizeTest {
    private val pane = BoxloreLayoutBounds(80f, 20f, 560f, 780f)

    @Test
    fun `tablet uses actual pane dimensions independently of its offset`() {
        assertEquals(DpSize(480.dp, 760.dp), loreTabletPaneSize(BoxloreWindowLayout(1_280f, 800f), pane))
    }

    @Test
    fun `missing pane retains the configuration fallback`() {
        assertNull(loreTabletPaneSize(BoxloreWindowLayout(1_280f, 800f), null))
    }

    @Test
    fun `width and height boundary transitions select the pane only for tablet states`() {
        val tabletPane = DpSize(480.dp, 760.dp)
        val windows = listOf(
            BoxloreWindowLayout(599f, 800f) to null,
            BoxloreWindowLayout(600f, 800f) to tabletPane,
            BoxloreWindowLayout(599f, 800f) to null,
            BoxloreWindowLayout(1_280f, 479f) to null,
            BoxloreWindowLayout(1_280f, 480f) to tabletPane,
            BoxloreWindowLayout(1_280f, 479f) to null,
        )
        windows.forEach { (window, expected) -> assertEquals(expected, loreTabletPaneSize(window, pane)) }
    }

    @Test
    fun `tablet pane resize uses the current bounds`() {
        val window = BoxloreWindowLayout(1_280f, 800f)
        assertEquals(DpSize(480.dp, 760.dp), loreTabletPaneSize(window, pane))
        assertEquals(DpSize(720.dp, 480.dp), loreTabletPaneSize(window, BoxloreLayoutBounds(80f, 0f, 800f, 480f)))
        assertEquals(DpSize(480.dp, 760.dp), loreTabletPaneSize(window, pane))
    }
}
