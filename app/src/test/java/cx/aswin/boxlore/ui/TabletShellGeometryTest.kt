package cx.aswin.boxlore.ui

import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.dockedPlayerCoversPane
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
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
    fun `tabletop puts the player in the upper half and browsing below the hinge`() {
        val fold = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)
        val window = BoxloreWindowLayout(673f, 841f, listOf(fold))
        val geometry = tabletShellGeometry(window, true, true, true, false, false)
        assertEquals(431f, geometry.primary.top)
        assertEquals(80f, geometry.primary.left)
        assertEquals(BoxloreLayoutBounds(0f, 0f, 673f, 410f), geometry.context)
        assertEquals(geometry.context, geometry.player)
        assertTrue(geometry.playerVisible)
        assertTrue(geometry.contextPlayerVisible)
        // The rail stays beside browsing in the lower half.
        assertEquals(BoxloreLayoutBounds(0f, 431f, 80f, 841f), geometry.rail)
        // Expanded, the player still uses the whole window.
        assertEquals(BoxloreLayoutBounds(0f, 0f, 673f, 841f), tabletShellGeometry(window, true, true, true, false, true).player)
        // Docked, nothing is beside browsing, so the page uses the whole window.
        val docked = tabletShellGeometry(window, true, true, true, false, false, playerDocked = true)
        assertNull(docked.context)
        assertEquals(BoxloreLayoutBounds(80f, 0f, 673f, 841f), docked.primary)
    }

    @Test
    fun `half-open folds follow the pane policy through the shell`() {
        val idleBook = tabletShellGeometry(BoxloreTestWindows.FOLD_BOOK, true, true, false, false, false)
        assertEquals(BoxloreLayoutBounds(80f, 0f, 932f, 650f), idleBook.primary)
        assertNull(idleBook.context)

        val tabletop = tabletShellGeometry(BoxloreTestWindows.FOLD_TABLETOP, true, true, true, false, false)
        assertEquals(BoxloreLayoutBounds(0f, 0f, 932f, 315f), tabletop.context)
        assertEquals(BoxloreLayoutBounds(80f, 336f, 932f, 650f), tabletop.primary)

        val upright = tabletShellGeometry(BoxloreTestWindows.UPRIGHT_BOOK, true, true, false, true, false)
        assertTrue(upright.detailVisible)
        assertEquals(BoxloreLayoutBounds(375f, 0f, 750f, 832f), upright.detail)
        assertTrue(upright.primary.width >= 280f)

        val pixelFold = tabletShellGeometry(BoxloreTestWindows.PIXEL_FOLD, true, true, true, false, false)
        assertNull(pixelFold.context)
        assertTrue(pixelFold.miniPlayerVisible(immersive = false))
        assertEquals(761f, pixelFold.primary.width)
        // A show page still opens beside browsing.
        assertTrue(tabletShellGeometry(BoxloreTestWindows.PIXEL_FOLD, true, true, true, true, false).context != null)

        listOf(BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.TABLET).forEach { window ->
            val playing = tabletShellGeometry(window, true, true, true, false, false)
            assertTrue(playing.contextPlayerVisible)
            assertTrue(playing.primary.width >= 440f)
        }
    }

    @Test
    fun `routes without navigation may opt in to the whole window of a half-open fold`() {
        val oneHalf = tabletShellGeometry(BoxloreTestWindows.FOLD_TABLETOP, false, false, false, false, false)
        assertEquals(BoxloreLayoutBounds(0f, 336f, 932f, 650f), oneHalf.primary)
        val whole = tabletShellGeometry(BoxloreTestWindows.FOLD_TABLETOP, false, false, false, false, false, hiddenNavFullWindow = true)
        assertEquals(BoxloreLayoutBounds(0f, 0f, 932f, 650f), whole.primary)
        assertFalse(whole.railVisible)
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

    @Test
    fun `only panes under the docked mini player reserve its height`() {
        val split = tabletShellGeometry(BoxloreWindowLayout(1_280f, 800f), true, true, true, true, false, playerDocked = true)
        assertTrue(split.context != null)
        assertTrue(dockedPlayerCoversPane(split.primary, split.primary))
        assertFalse(dockedPlayerCoversPane(split.detail, split.primary))
        val portrait = tabletShellGeometry(BoxloreWindowLayout(840f, 1_280f), true, true, true, true, false)
        assertTrue(dockedPlayerCoversPane(portrait.detail, portrait.primary))
        assertTrue(dockedPlayerCoversPane(null, portrait.primary))
        // With an episode and a show open on a wide tablet the detail sits beside the dock; at 839dp it covers it.
        val wide = tabletShellGeometry(BoxloreTestWindows.TABLET, true, true, true, true, false)
        assertFalse(dockedPlayerCoversPane(wide.detail, wide.primary))
        val narrow = tabletShellGeometry(BoxloreWindowLayout(839f, 800f), true, true, true, true, false)
        assertTrue(dockedPlayerCoversPane(narrow.detail, narrow.primary))
    }
}
