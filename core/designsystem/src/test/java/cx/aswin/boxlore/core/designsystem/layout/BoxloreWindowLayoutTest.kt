package cx.aswin.boxlore.core.designsystem.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
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
        // Nothing open: the page uses the whole window; with a context, browsing keeps below the hinge.
        assertEquals(BoxloreLayoutBounds(80f, 0f, 673f, 841f), boxlorePaneSpec(layout, false).primary)
        assertEquals(431f, boxlorePaneSpec(layout, true).primary.top)
        assertEquals(BoxloreLayoutBounds(0f, 0f, 673f, 410f), boxlorePaneSpec(layout, true).context)
        assertEquals(BoxloreLayoutBounds(0f, 431f, 673f, 841f), layout.foldSafeBrowsingRegion())
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
            assertFalse(layout.usesHingeSplit)
            // With the context closed the page uses the whole window, as with nothing open.
            assertEquals(boxlorePaneSpec(layout, false), panes)
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
        val layout = BoxloreWindowLayout(1_280f, 800f, listOf(horizontal, vertical))
        val panes = boxlorePaneSpec(layout, true)
        assertNull(panes.context)
        assertFalse(layout.usesHingeSplit)
        assertEquals(BoxloreLayoutBounds(80f, 0f, 1_280f, 800f), panes.primary)
    }

    @Test
    fun `half-open folds use the whole window when nothing is open`() {
        listOf(BoxloreTestWindows.FOLD_BOOK, BoxloreTestWindows.FOLD_TABLETOP, BoxloreTestWindows.UPRIGHT_BOOK).forEach { window ->
            val idle = boxlorePaneSpec(window, false)
            assertEquals(BoxloreLayoutBounds(80f, 0f, window.widthDp, window.heightDp), idle.primary)
            assertNull(idle.context)
        }
        assertEquals(80f, boxlorePaneSpec(BoxloreTestWindows.FOLD_BOOK, false).primary.left)
        assertEquals(932f, boxlorePaneSpec(BoxloreTestWindows.FOLD_BOOK, false).primary.right)
    }

    @Test
    fun `tabletop puts the context in the upper half and browsing in the lower half`() {
        val spec = boxlorePaneSpec(BoxloreTestWindows.FOLD_TABLETOP, true, playerOnly = true)
        assertEquals(BoxloreLayoutBounds(0f, 0f, 932f, 315f), spec.context)
        assertEquals(BoxloreLayoutBounds(80f, 336f, 932f, 650f), spec.primary)
        // A detail opens in the upper half too.
        assertEquals(spec, boxlorePaneSpec(BoxloreTestWindows.FOLD_TABLETOP, true))
        // An upper half under 240dp is too short to use: the context closes.
        val shortTop = BoxloreTestWindows.FOLD_TABLETOP.copy(
            folds = listOf(BoxloreTestWindows.FOLD_TABLETOP.folds.single().copy(bounds = BoxloreLayoutBounds(0f, 230f, 932f, 251f))),
        )
        assertNull(boxlorePaneSpec(shortTop, true).context)
        assertEquals(BoxloreLayoutBounds(80f, 0f, 932f, 650f), boxlorePaneSpec(shortTop, true).primary)
    }

    @Test
    fun `book posture opens the context in the second half whatever the aspect ratio`() {
        val book = boxlorePaneSpec(BoxloreTestWindows.FOLD_BOOK, true)
        assertTrue(BoxloreTestWindows.FOLD_BOOK.usesHingeSplit)
        assertEquals(BoxloreLayoutBounds(80f, 0f, 456f, 650f), book.primary)
        assertEquals(BoxloreLayoutBounds(477f, 0f, 932f, 650f), book.context)
        assertEquals(book, boxlorePaneSpec(BoxloreTestWindows.FOLD_BOOK, true, playerOnly = true))

        val upright = BoxloreTestWindows.UPRIGHT_BOOK
        assertFalse(upright.usesSplitLayout)
        assertTrue(upright.usesHingeSplit)
        val split = boxlorePaneSpec(upright, true)
        assertEquals(2, upright.foldSafeRegions().size)
        assertEquals(BoxloreLayoutBounds(375f, 0f, 750f, 832f), split.context)
        assertEquals(BoxloreLayoutBounds(80f, 0f, 375f, 832f), split.primary)
        assertTrue(split.primary.width >= 280f)
    }

    @Test
    fun `the player alone opens beside browsing only when browsing keeps 440dp`() {
        val pixelFold = BoxloreTestWindows.PIXEL_FOLD
        assertNull(boxlorePaneSpec(pixelFold, true, playerOnly = true).context)
        assertEquals(BoxloreLayoutBounds(80f, 0f, 841f, 701f), boxlorePaneSpec(pixelFold, true, playerOnly = true).primary)
        // A detail still splits.
        assertNotNull(boxlorePaneSpec(pixelFold, true).context)
        listOf(BoxloreWindowLayout(932f, 650f), BoxloreTestWindows.TABLET, BoxloreTestWindows.DESKTOP).forEach { window ->
            val player = boxlorePaneSpec(window, true, playerOnly = true)
            assertEquals(boxlorePaneSpec(window, true), player)
            assertTrue(requireNotNull(player.context).width in 360f..520f)
            assertTrue(player.primary.width >= 440f)
        }
    }

    @Test
    fun `flat foldables and tablets keep their layouts`() {
        listOf(BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.TABLET).forEach { window ->
            assertFalse(window.usesHingeSplit)
            assertEquals(BoxloreLayoutBounds(80f, 0f, window.widthDp, window.heightDp), boxlorePaneSpec(window, false).primary)
            val open = boxlorePaneSpec(window, true)
            val context = requireNotNull(open.context)
            assertEquals(window.widthDp, context.right)
            assertEquals(context.left - 16f, open.primary.right)
        }
    }
}
