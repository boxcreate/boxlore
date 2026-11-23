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
    fun `inner screens held flat in landscape use two columns from 840dp`() {
        listOf(852f to 594f, 841f to 645f, 840f to 600f, 999f to 700f).forEach { (width, height) ->
            val regions = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(width, height), BoxloreLayoutBounds(0f, 24f, width, height - 24f), false))
            assertEquals("${width}x$height", TabletPlayerArrangement.Columns, regions.arrangement)
        }
        // Square and near-square portrait windows stay stacked, though their body under the header is wider than tall.
        listOf(839f to 600f, 800f to 1_280f, 840f to 1_000f, 840f to 880f, 880f to 880f).forEach { (width, height) ->
            val regions = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(width, height), BoxloreLayoutBounds(0f, 24f, width, height - 24f), false))
            assertEquals("${width}x$height", TabletPlayerArrangement.Stacked, regions.arrangement)
        }
        val context = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(1_280f, 800f), BoxloreLayoutBounds(0f, 24f, 900f, 776f), true))
        assertEquals(TabletPlayerArrangement.Stacked, context.arrangement)
    }

    @Test
    fun `big screens get the larger title and cover and inner screens keep today's`() {
        val tablet = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(1_280f, 800f), BoxloreLayoutBounds(0f, 24f, 1_280f, 776f), false))
        assertTrue(usesLargePlayerTitle(tablet))
        val desktop = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(1_600f, 1_000f), BoxloreLayoutBounds(0f, 24f, 1_600f, 976f), false))
        assertTrue(usesLargePlayerTitle(desktop) && desktop.artworkSizeDp <= 420f)
        val inner = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(932f, 650f), BoxloreLayoutBounds(0f, 24f, 932f, 626f), false))
        assertEquals(TabletPlayerArrangement.Columns, inner.arrangement)
        assertTrue(!usesLargePlayerTitle(inner))
        assertTrue(inner.artworkSizeDp <= 360f)
    }

    @Test
    fun `the supporting panel keeps a 16dp margin from the window edges beside the controls`() {
        val viewport = BoxloreLayoutBounds(0f, 24f, 1_280f, 776f)
        val columns = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(1_280f, 800f), viewport, false))
        assertEquals(viewport.right - 16f, columns.support.right, 0f)
        assertEquals(viewport.bottom - 16f, columns.support.bottom, 0f)
        assertEquals("Both columns end on one line", columns.support.bottom, columns.controls.bottom, 0f)
        val hinge = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Vertical, BoxloreLayoutBounds(456f, 0f, 476f, 650f), true, true)
        val book = requireNotNull(tabletPlayerRegions(BoxloreWindowLayout(932f, 650f, listOf(hinge)), BoxloreLayoutBounds(0f, 24f, 932f, 626f), false))
        assertEquals(TabletPlayerArrangement.VerticalFold, book.arrangement)
        assertTrue(book.support.right <= 932f - 16f)
        assertTrue(book.support.bottom <= 626f - 16f)
        assertEquals("Book posture stacks controls and panel on the right-hand screen", BoxloreLayoutBounds(476f, 24f, 932f, 626f), book.stack)
    }

    @Test
    fun `the hero gives way by its own shape and a cramped audio cover becomes the small heading`() {
        assertEquals(TabletArtworkPlan(217f), planTabletArtwork(TabletHeroKind.Audio, 260f, 217f, 600f, compactAvailable = true))
        assertEquals(TabletArtworkPlan(150f), planTabletArtwork(TabletHeroKind.Audio, 150f, 217f, 600f, compactAvailable = true))
        assertTrue(planTabletArtwork(TabletHeroKind.Audio, 108f, 217f, 600f, compactAvailable = true).compact)
        assertEquals(TabletArtworkPlan(96f), planTabletArtwork(TabletHeroKind.Audio, 60f, 217f, 600f, compactAvailable = false))
        // 16:9 video uses the height left as its height, not its width.
        assertEquals(192f, planTabletArtwork(TabletHeroKind.Video, 108f, 360f, 600f, compactAvailable = true).sizeDp, 0.01f)
        assertEquals(217f, planTabletArtwork(TabletHeroKind.Video, 200f, 217f, 600f, compactAvailable = true).sizeDp, 0.01f)
        assertEquals(300f, planTabletArtwork(TabletHeroKind.Video, 200f, 360f, 300f, compactAvailable = true).sizeDp, 0.01f)
        assertTrue(planTabletArtwork(TabletHeroKind.Video, 10f, 360f, 600f, compactAvailable = true).sizeDp >= 96f)
        // The Cast panel has a fixed height and keeps its width.
        assertEquals(TabletArtworkPlan(217f), planTabletArtwork(TabletHeroKind.Remote, 40f, 217f, 600f, compactAvailable = true))
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
