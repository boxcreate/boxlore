package cx.aswin.boxlore.ui

import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TabletShellGeometryTest {
    @Test
    fun `compact windows retain the full phone viewport and expanded empty contexts close`() {
        listOf(360f to 800f, 800f to 360f).forEach { (width, height) ->
            val geometry = tabletShellGeometry(BoxloreWindowLayout(width, height), true, true, false, false, false)
            assertEquals(BoxloreLayoutBounds(0f, 0f, width, height), geometry.primary)
            assertFalse(geometry.railVisible)
            assertNull(geometry.context)
        }
        val empty = tabletShellGeometry(BoxloreWindowLayout(1_280f, 800f), true, true, false, false, false)
        assertEquals(1_200f, empty.primary.width)
        assertNull(empty.context)
        assertFalse(empty.playerAvailable)
    }

    @Test
    fun `tabletop docking stays below the hinge while its expanding player uses the whole window`() {
        val fold = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)
        val geometry = tabletShellGeometry(BoxloreWindowLayout(673f, 841f, listOf(fold)), true, true, true, false, false)
        assertEquals(431f, geometry.primary.top)
        assertEquals(80f, geometry.primary.left)
        assertEquals(BoxloreLayoutBounds(0f, 0f, 673f, 841f), geometry.player)
        assertTrue(geometry.playerVisible)
        assertNull(geometry.context)
    }

    @Test
    fun `details retain docked playback and immersive presentation covers both retained panes`() {
        val window = BoxloreWindowLayout(1_280f, 800f)
        val detail = tabletShellGeometry(window, true, true, true, true, false)
        assertTrue(requireNotNull(detail.context).width in 360f..520f)
        assertTrue(detail.primary.right < detail.context!!.left)
        assertTrue(detail.playerAvailable)
        assertTrue(detail.playerVisible)
        assertEquals(detail.primary, detail.player)
        val immersive = tabletShellGeometry(window, true, true, true, false, true)
        assertEquals(BoxloreLayoutBounds(0f, 0f, 1_280f, 800f), immersive.player)
        assertTrue(immersive.playerVisible)
    }

    @Test
    fun `docking the context player releases its column without losing playback`() {
        val docked = tabletShellGeometry(BoxloreWindowLayout(1_280f, 800f), true, true, true, false, false, playerDocked = true)
        assertNull(docked.context)
        assertEquals(1_200f, docked.primary.width)
        assertEquals(docked.primary, docked.player)
        assertTrue(docked.playerVisible)
        val detail = tabletShellGeometry(BoxloreWindowLayout(1_280f, 800f), true, true, true, true, false, playerDocked = true)
        assertTrue(detail.detailVisible)
        assertEquals(detail.primary, detail.player)
    }

    @Test
    fun `hidden navigation retains a complete fold safe region without a rail gutter`() {
        val tabletop = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)
        val vertical = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Vertical, BoxloreLayoutBounds(500f, 0f, 520f, 800f), true, true)
        val cases = listOf(
            BoxloreWindowLayout(673f, 841f, listOf(tabletop)) to BoxloreLayoutBounds(0f, 431f, 673f, 841f),
            BoxloreWindowLayout(1_280f, 800f, listOf(vertical)) to BoxloreLayoutBounds(520f, 0f, 1_280f, 800f),
            BoxloreWindowLayout(1_280f, 800f, listOf(vertical.copy(state = BoxloreFoldState.Flat, isSeparating = false, isOccluding = false))) to BoxloreLayoutBounds(0f, 0f, 1_280f, 800f),
            BoxloreWindowLayout(800f, 360f, listOf(vertical)) to BoxloreLayoutBounds(0f, 0f, 800f, 360f),
            BoxloreWindowLayout(600f, 960f) to BoxloreLayoutBounds(0f, 0f, 600f, 960f),
        )
        cases.forEach { (window, expected) ->
            listOf(false, true).forEach { playerAllowed ->
                val geometry = tabletShellGeometry(window, false, playerAllowed, true, true, false)
                assertEquals(expected, geometry.primary)
                assertFalse(geometry.railVisible)
                assertFalse(geometry.detailVisible)
                assertFalse(geometry.playerAvailable)
                assertNull(geometry.context)
            }
        }
    }
}
