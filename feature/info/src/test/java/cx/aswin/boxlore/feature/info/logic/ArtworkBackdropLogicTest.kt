package cx.aswin.boxlore.feature.info.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkBackdropLogicTest {
    @Test fun `blur is completely transparent before the layer boundary`() {
        assertEquals(1f, artworkBackdropAlpha(0f))
        assertEquals(1f, artworkBackdropAlpha(0.08f))
        listOf(0.92f, 0.95f, 1f, 2f).forEach { assertEquals(0f, artworkBackdropAlpha(it)) }
        listOf(Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { assertEquals(0f, artworkBackdropAlpha(it)) }
    }

    @Test fun `fade remains gradual and monotonic rather than developing a shelf or cliff`() {
        val samples = (0..1000).map { artworkBackdropAlpha(it / 1000f) }
        samples.zipWithNext().forEach { (before, after) ->
            assertTrue(after in 0f..1f)
            assertTrue(after <= before + 0.000002f)
            assertTrue(before - after < 0.003f)
        }
        assertTrue(artworkBackdropAlpha(0.081f) > 0.999f)
        assertTrue(artworkBackdropAlpha(0.919f) < 0.001f)
    }
}
