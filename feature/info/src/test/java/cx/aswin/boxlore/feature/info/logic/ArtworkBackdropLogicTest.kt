package cx.aswin.boxlore.feature.info.logic

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import cx.aswin.boxlore.core.designsystem.theme.artworkContrast
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

    @Test fun `the backdrop is as strong as header text allows, within its bounds`() {
        val darkGround = Color(0xFF1B1418)
        val lightText = Color(0xFFF1ECEE)
        // A dark cover behind light text can show at full strength.
        assertEquals(0.75f, artworkBackdropStrength(Color(0xFF3A1020), darkGround, lightText), 0.001f)
        // A white cover behind light text backs off until the text reads again.
        val pale = artworkBackdropStrength(Color(0xFFF5F5F5), darkGround, lightText)
        assertTrue(pale < 0.75f)
        assertTrue(pale >= 0.3f)
        for (grey in 0..255 step 15) {
            val average = Color(grey, grey, grey)
            listOf(darkGround to lightText, Color(0xFFF2ECEE) to Color(0xFF1D1A1B)).forEach { (ground, text) ->
                val strength = artworkBackdropStrength(average, ground, text)
                assertTrue(strength in 0.3f..0.75f)
                if (strength > 0.3f) assertTrue(artworkContrast(text, lerp(ground, average, strength)) >= 4.5f, "grey=$grey strength=$strength")
            }
        }
    }
}
