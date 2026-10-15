package cx.aswin.boxlore.feature.player.v2.logic

import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletPlayerLayoutLogicTest {
    @Test
    fun `phone sizes never select a tablet player`() {
        listOf(360f to 800f, 412f to 915f, 800f to 360f, 600f to 479f).forEach { (width, height) ->
            assertNull(tabletPlayerRegions(BoxloreWindowLayout(width, height), BoxloreLayoutBounds(0f, 0f, width, height), false))
        }
    }

    @Test
    fun `portrait and context panes reserve independent support space with bounded artwork`() {
        listOf(600f to 960f, 840f to 1_280f, 473f to 800f, 360f to 600f).forEach { (width, height) ->
            val viewport = BoxloreLayoutBounds(800f, 24f, 800f + width, height - 24f)
            val regions = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(1_600f, 1_280f), viewport, width < 600f))
            assertEquals(TabletPlayerArrangement.Stacked, regions.arrangement)
            assertTrue(regions.artworkSizeDp in 96f..360f)
            assertTrue(regions.controls.bottom <= regions.support.top)
            assertTrue(regions.support.height >= 150f)
            assertEquals(viewport.left, regions.controls.left, 0f)
            assertEquals(viewport.bottom, regions.support.bottom, 0f)
        }
    }

    @Test
    fun `immersive wide players put supporting content beside controls`() {
        val regions = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(1_600f, 1_000f), BoxloreLayoutBounds(0f, 24f, 1_600f, 976f), false))
        assertEquals(TabletPlayerArrangement.Columns, regions.arrangement)
        assertEquals(16f, regions.support.left - regions.controls.right, 0f)
        assertEquals(regions.controls.top, regions.support.top, 0f)
    }

    @Test
    fun `tabletop art stays above the hinge and all controls and tabs below`() {
        val hinge = BoxloreFold(
            BoxloreFoldState.HalfOpened,
            BoxloreFoldOrientation.Horizontal,
            BoxloreLayoutBounds(0f, 410f, 673f, 431f),
            true,
            true,
        )
        val regions = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(673f, 841f, listOf(hinge)), BoxloreLayoutBounds(0f, 24f, 673f, 817f), false))
        assertEquals(TabletPlayerArrangement.HorizontalFold, regions.arrangement)
        assertTrue(requireNotNull(regions.artwork).bottom <= 410f)
        assertTrue(regions.controls.top >= 431f)
        assertTrue(regions.support.top >= 431f)
        assertTrue(regions.header.bottom <= requireNotNull(regions.artwork).top)
    }

    @Test
    fun `a context wholly beside a hinge does not reapply window-relative hinge coordinates`() {
        val hinge = BoxloreFold(
            BoxloreFoldState.Flat,
            BoxloreFoldOrientation.Vertical,
            BoxloreLayoutBounds(630f, 0f, 650f, 800f),
            true,
            true,
        )
        val window = BoxloreWindowLayout(1_280f, 800f, listOf(hinge))
        val context = requireNotNull(tabletPlayerRegions(window, BoxloreLayoutBounds(700f, 24f, 1_220f, 776f), true))
        assertEquals(TabletPlayerArrangement.Stacked, context.arrangement)
        val immersive = requireNotNull(tabletPlayerRegions(window, BoxloreLayoutBounds(0f, 24f, 1_280f, 776f), false))
        assertEquals(TabletPlayerArrangement.VerticalFold, immersive.arrangement)
        assertTrue(requireNotNull(immersive.artwork).right <= 630f)
        assertTrue(immersive.controls.left >= 650f)
        assertTrue(immersive.support.left >= 650f)
    }
}
