package cx.aswin.boxlore.core.designsystem.layout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoxloreCardSpecTest {
    @Test
    fun `every compact family preserves caller constraints at every phone size`() {
        listOf(360f to 800f, 412f to 915f, 800f to 360f, 1_200f to 479f).forEach { (width, height) ->
            val layout = BoxloreWindowLayout(width, height)
            BoxloreCardFamily.entries.forEach { family ->
                assertEquals(BoxloreCardSpec(0f, Float.POSITIVE_INFINITY, null, true), boxloreCardSpec(layout, family))
            }
            assertEquals(2, boxlorePosterColumns(layout, width))
            assertEquals(3, boxlorePosterColumns(layout, width, compactColumns = 3))
        }
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
