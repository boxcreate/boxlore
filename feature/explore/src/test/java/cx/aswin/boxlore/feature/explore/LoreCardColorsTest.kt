package cx.aswin.boxlore.feature.explore

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LoreCardColorsTest {
    private fun contrast(a: Color, b: Color): Float {
        val hi = maxOf(a.luminance(), b.luminance())
        val lo = minOf(a.luminance(), b.luminance())
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test
    fun `play icon stays readable on any cover accent`() {
        val accents = (0 until 360 step 15).flatMap { hue ->
            listOf(0.25f, 0.5f, 0.75f, 0.95f).map { value -> Color.hsv(hue.toFloat(), 0.7f, value) }
        } + listOf(Color.White, Color.Black, Color(0xFF7C9CFF), Color(0xFFFFD54F))
        for (accent in accents) {
            assertTrue(contrast(loreOnAccent(accent), accent) >= 4.5f, "accent $accent")
        }
        assertEquals(Color.Black, loreOnAccent(Color(0xFF7C9CFF)))
    }

    @Test
    fun `card ground stays dark enough for white text on any accent`() {
        for (hue in 0 until 360 step 15) {
            val ground = loreCardColor(Color.hsv(hue.toFloat(), 1f, 1f))
            assertTrue(contrast(Color.White.copy(alpha = 1f), ground) >= 12f)
        }
        assertTrue(contrast(Color.White, loreCardColor(Color.White)) >= 12f)
    }

    @Test
    fun `card ground keeps the cover colour instead of going black`() {
        for (hue in 0 until 360 step 30) {
            val ground = loreCardColor(Color.hsv(hue.toFloat(), 0.8f, 0.9f))
            val max = maxOf(ground.red, ground.green, ground.blue)
            val min = minOf(ground.red, ground.green, ground.blue)
            assertTrue((max - min) / max >= 0.35f, "hue $hue ground $ground is too grey")
            assertTrue(contrast(Color.White, loreSegmentColor(Color.hsv(hue.toFloat(), 0.8f, 0.9f))) >= 7f)
        }
    }
}
