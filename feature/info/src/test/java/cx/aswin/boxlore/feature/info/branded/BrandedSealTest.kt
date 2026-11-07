package cx.aswin.boxlore.feature.info.branded

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.hypot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class BrandedSealTest {
    private val foil = listOf(Color(0xFFFFB3D9), Color(0xFFFFE08A), Color(0xFFC6FF9E), Color(0xFF8FE9FF), Color(0xFFB9A6FF))

    @Test
    fun `the mark reads on every foil colour and the faded print stays visible`() {
        foil.forEach { colour ->
            assertTrue(contrast(SealInk, colour) >= 4.5f, "mark on $colour")
            // The rings and arc words are the emblem's print, faded on purpose; the seal's label carries its meaning.
            assertTrue(contrast(SealSoftInk.compositeOver(colour), colour) >= 2.9f, "faded print on $colour")
        }
    }

    @Test
    fun `the edge has 36 teeth around the centre`() {
        val edge = BrandedSealShape.edge()
        assertEquals(72, edge.size)
        edge.forEachIndexed { i, p ->
            val r = hypot(p.x, p.y)
            assertEquals(if (i % 2 == 0) 1f else BrandedSealShape.TOOTH_DEPTH, r, 1e-4f)
        }
    }

    @Test
    fun `both lines of words sit between the rings`() {
        val radius = BrandedSealShape.SIZE / 2f
        val inner = radius * BrandedSealShape.RING_INNER
        val outer = radius * BrandedSealShape.RING_OUTER
        // Capitals stand about 0.72 of the text size tall.
        val caps = BrandedSealShape.TEXT_SIZE * 0.72f
        // The top words stand on the inner ring and reach out; the bottom words hang from the outer ring and reach in.
        assertTrue(BrandedSealShape.topTextRadius > inner && BrandedSealShape.topTextRadius + caps < outer)
        assertTrue(BrandedSealShape.bottomTextRadius < outer && BrandedSealShape.bottomTextRadius - caps > inner)
        assertTrue(BrandedSealShape.dotRadius > inner && BrandedSealShape.dotRadius < outer)
    }

    @Test
    fun `the sheen starts and ends fully off the seal and settles within ten seconds`() {
        // The seal's farthest point from its centre is half its width.
        assertTrue(BrandedSealSheen.TRAVEL - BrandedSealSheen.HALF_WIDTH >= 0.5f)
        assertEquals(1f, hypot(BrandedSealSheen.Direction.x, BrandedSealSheen.Direction.y), 1e-3f)
        val total = BrandedSealSheen.SWEEPS * (BrandedSealSheen.SWEEP_MILLIS + BrandedSealSheen.REST_MILLIS)
        assertTrue(total <= 10_000, "sheen runs $total ms")
        assertTrue(abs(BrandedSealShape.MARK_TILT) in 5f..20f)
    }

    private fun contrast(a: Color, b: Color): Float {
        val light = maxOf(a.luminance(), b.luminance())
        val dark = minOf(a.luminance(), b.luminance())
        return (light + 0.05f) / (dark + 0.05f)
    }
}
