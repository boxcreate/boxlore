package cx.aswin.boxlore.core.designsystem.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoxloreCardSpecTest {
    @Test
    fun `compact families preserve caller constraints except posters in short windows`() {
        listOf(360f to 800f, 412f to 915f, 443f to 649f, 475f to 695f).forEach { (width, height) ->
            val layout = BoxloreWindowLayout(width, height)
            BoxloreCardFamily.entries.forEach { family ->
                assertEquals(BoxloreCardSpec(0f, Float.POSITIVE_INFINITY, null, true), boxloreCardSpec(layout, family))
            }
        }
        listOf(BoxloreWindowLayout(915f, 412f), BoxloreWindowLayout(412f, 450f), BoxloreWindowLayout(1_200f, 479f)).forEach { short ->
            val poster = boxloreCardSpec(short, BoxloreCardFamily.Poster)
            assertFalse(poster.preserveCallerGeometry)
            assertEquals(208f, poster.maximumWidthDp)
            (BoxloreCardFamily.entries - BoxloreCardFamily.Poster).forEach { family ->
                assertTrue(boxloreCardSpec(short, family).preserveCallerGeometry)
            }
        }
        // Previews without a window keep the caller's geometry.
        assertTrue(boxloreCardSpec(BoxloreWindowLayout.Unspecified, BoxloreCardFamily.Poster).preserveCallerGeometry)
    }

    @Test
    fun `portrait phones and the fold cover keep their columns while wider compact windows pack by width`() {
        listOf(360f to 800f, 412f to 915f, 430f to 932f, 443f to 649f, 475f to 695f).forEach { (width, height) ->
            val layout = BoxloreWindowLayout(width, height)
            assertEquals(2, boxlorePosterColumns(layout, width))
            assertEquals(3, boxlorePosterColumns(layout, width, compactColumns = 3, gapDp = 12f))
        }
        assertEquals(3, boxlorePosterColumns(BoxloreWindowLayout(540f, 860f), 540f))
        assertEquals(5, boxlorePosterColumns(BoxloreWindowLayout(915f, 412f), 915f))
        // Never fewer than the caller's compact count.
        assertEquals(4, boxlorePosterColumns(BoxloreWindowLayout(540f, 860f), 540f, compactColumns = 4))
    }

    @Test
    fun `columns never drop when a window crosses the 600dp tablet gate`() {
        val compact = boxlorePosterColumns(BoxloreWindowLayout(599f, 900f), 599f)
        val tablet = boxlorePosterColumns(BoxloreWindowLayout(600f, 900f), 520f)
        assertEquals(3, compact)
        assertEquals(3, tablet)
    }

    @Test
    fun `grid family is wide on tablets and does nothing on phones`() {
        val grid = boxloreCardSpec(BoxloreWindowLayout(1_280f, 800f), BoxloreCardFamily.Grid)
        assertEquals(1_040f, grid.maximumWidthDp)
        assertFalse(grid.preserveCallerGeometry)
        assertTrue(boxloreCardSpec(BoxloreWindowLayout(412f, 915f), BoxloreCardFamily.Grid).preserveCallerGeometry)
    }

    @Test
    fun `tablet families have finite bounds and retain their artwork rules`() {
        val layout = BoxloreWindowLayout(840f, 1_280f)
        val maxima = mapOf(
            BoxloreCardFamily.Hero to 480f,
            BoxloreCardFamily.Poster to 208f,
            BoxloreCardFamily.Episode to 720f,
            BoxloreCardFamily.RailItem to 310f,
            BoxloreCardFamily.Reading to 720f,
            BoxloreCardFamily.Utility to 720f,
            BoxloreCardFamily.Grid to 1_040f,
        )
        maxima.forEach { (family, maximum) ->
            val spec = boxloreCardSpec(layout, family)
            assertFalse(spec.preserveCallerGeometry)
            assertEquals(maximum, spec.maximumWidthDp)
            assertTrue(spec.minimumWidthDp in 1f..maximum)
        }
        assertEquals(1f, boxloreCardSpec(layout, BoxloreCardFamily.Poster).artworkAspectRatio)
    }

    @Test
    fun `packing follows the current pane rather than full window width`() {
        val medium = BoxloreWindowLayout(600f, 960f)
        val expanded = BoxloreWindowLayout(840f, 1_280f)
        val large = BoxloreWindowLayout(1_600f, 1_000f)
        assertEquals(3, boxlorePosterColumns(medium, 520f))
        assertEquals(4, boxlorePosterColumns(expanded, 760f))
        assertTrue(boxlorePosterColumns(large, 1_520f) >= 5)
        assertEquals(2, boxlorePosterColumns(large, 384f))
        assertEquals(1, boxlorePosterColumns(large, 120f))
    }
}
