package cx.aswin.boxlore.core.designsystem.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoxloreWindowLayoutTest {
    @Test
    fun `width classes use the exact requested boundaries`() {
        listOf(
            599f to BoxloreWindowWidth.Compact,
            600f to BoxloreWindowWidth.Medium,
            839f to BoxloreWindowWidth.Medium,
            840f to BoxloreWindowWidth.Expanded,
            1_199f to BoxloreWindowWidth.Expanded,
            1_200f to BoxloreWindowWidth.Large,
        ).forEach { (width, expected) -> assertEquals(expected, BoxloreWindowLayout(width, 960f).widthClass) }
    }

    @Test
    fun `a short landscape phone always keeps its complete phone geometry`() {
        listOf(360f to 800f, 412f to 915f, 800f to 360f, 1_200f to 479f).forEach { (width, height) ->
            val layout = BoxloreWindowLayout(width, height)
            assertFalse(layout.usesTabletLayout)
            assertFalse(layout.usesSplitLayout)
            listOf(false, true).forEach { contextOpen ->
                assertEquals(
                    BoxlorePaneSpec(BoxloreNavigationLayout.BottomBar, 0f, BoxloreLayoutBounds(0f, 0f, width, height), null, 0f),
                    boxlorePaneSpec(layout, contextOpen),
                )
            }
        }
    }

    @Test
    fun `height classes and tablet eligibility have no boundary overlap`() {
        assertFalse(BoxloreWindowLayout(600f, 479f).usesTabletLayout)
        assertTrue(BoxloreWindowLayout(600f, 480f).usesTabletLayout)
        assertEquals(BoxloreWindowHeight.Compact, BoxloreWindowLayout(600f, 479f).heightClass)
        assertEquals(BoxloreWindowHeight.Medium, BoxloreWindowLayout(600f, 480f).heightClass)
        assertEquals(BoxloreWindowHeight.Medium, BoxloreWindowLayout(600f, 899f).heightClass)
        assertEquals(BoxloreWindowHeight.Expanded, BoxloreWindowLayout(600f, 900f).heightClass)
    }

    @Test
    fun `inner foldable and split-screen halves select layouts from available window bounds`() {
        listOf(673f to 841f, 600f to 960f, 800f to 1_280f).forEach { (width, height) ->
            val layout = BoxloreWindowLayout(width, height)
            assertTrue(layout.usesTabletLayout)
            assertFalse(layout.usesSplitLayout)
            assertEquals(BoxloreNavigationLayout.Rail, boxlorePaneSpec(layout, true).navigation)
            assertNull(boxlorePaneSpec(layout, true).context)
        }
        assertFalse(BoxloreWindowLayout(599f, 960f).usesTabletLayout)
        assertFalse(BoxloreWindowLayout(839f, 600f).usesSplitLayout)
        assertTrue(BoxloreWindowLayout(840f, 600f).usesSplitLayout)
        assertFalse(BoxloreWindowLayout(840f, 840f).usesSplitLayout)
    }

    @Test
    fun `context pane closes fully and stays bounded when available`() {
        listOf(840f to 600f, 1_280f to 800f, 1_600f to 1_000f).forEach { (width, height) ->
            val layout = BoxloreWindowLayout(width, height)
            val closed = boxlorePaneSpec(layout, false)
            assertNull(closed.context)
            assertEquals(width - 80f, closed.primary.width)
            val open = boxlorePaneSpec(layout, true)
            val context = requireNotNull(open.context)
            assertTrue(context.width in 360f..520f)
            assertEquals(width, 80f + open.primary.width + open.gapDp + context.width, 0.001f)
            assertTrue(open.primary.width >= 320f)
        }
    }

    @Test
    fun `tabletop and separating hinges keep contiguous content away from the hinge`() {
        val hinge = BoxloreFold(
            BoxloreFoldState.HalfOpened,
            BoxloreFoldOrientation.Horizontal,
            BoxloreLayoutBounds(0f, 410f, 673f, 431f),
            true,
            true,
        )
        val layout = BoxloreWindowLayout(673f, 841f, listOf(hinge))
        assertNotNull(layout.tabletopFold)
        assertEquals(
            listOf(BoxloreLayoutBounds(0f, 0f, 673f, 410f), BoxloreLayoutBounds(0f, 431f, 673f, 841f)),
            layout.foldSafeRegions(),
        )
        assertEquals(431f, boxlorePaneSpec(layout, false).primary.top)
        val flat = layout.copy(folds = listOf(hinge.copy(state = BoxloreFoldState.Flat, isSeparating = false, isOccluding = false)))
        assertNull(flat.tabletopFold)
        assertEquals(listOf(BoxloreLayoutBounds(0f, 0f, 673f, 841f)), flat.foldSafeRegions())
    }

    @Test
    fun `vertical hinge places each pane on a separate physical region`() {
        val hinge = BoxloreFold(
            BoxloreFoldState.Flat,
            BoxloreFoldOrientation.Vertical,
            BoxloreLayoutBounds(630f, 0f, 650f, 800f),
            true,
            true,
        )
        val layout = BoxloreWindowLayout(1_280f, 800f, listOf(hinge))
        val panes = boxlorePaneSpec(layout, true)
        assertEquals(630f, panes.primary.right)
        assertTrue(requireNotNull(panes.context).left >= 650f)
        assertTrue(requireNotNull(panes.context).width <= 520f)
    }

    @Test
    fun `off-center hinges close undersized panes without crossing the fold`() {
        listOf(300f, 950f).forEach { hingeLeft ->
            val fold = BoxloreFold(
                BoxloreFoldState.HalfOpened,
                BoxloreFoldOrientation.Vertical,
                BoxloreLayoutBounds(hingeLeft, 0f, hingeLeft + 20f, 800f),
                false,
                false,
            )
            val layout = BoxloreWindowLayout(1_280f, 800f, listOf(fold))
            val panes = boxlorePaneSpec(layout, true)
            assertNull(panes.context)
            assertTrue(panes.primary.right <= hingeLeft || panes.primary.left >= hingeLeft + 20f)
            assertTrue(panes.primary.width >= 320f)
        }
    }

    @Test
    fun `mixed fold orientations never overlap primary and context content`() {
        val horizontal = BoxloreFold(
            BoxloreFoldState.HalfOpened,
            BoxloreFoldOrientation.Horizontal,
            BoxloreLayoutBounds(0f, 390f, 1_280f, 410f),
            true,
            true,
        )
        val vertical = horizontal.copy(
            orientation = BoxloreFoldOrientation.Vertical,
            bounds = BoxloreLayoutBounds(630f, 0f, 650f, 800f),
        )
        val panes = boxlorePaneSpec(BoxloreWindowLayout(1_280f, 800f, listOf(horizontal, vertical)), true)
        assertNull(panes.context)
        assertTrue(panes.primary.top >= 410f)
        assertTrue(panes.primary.left >= 650f)
    }
}
