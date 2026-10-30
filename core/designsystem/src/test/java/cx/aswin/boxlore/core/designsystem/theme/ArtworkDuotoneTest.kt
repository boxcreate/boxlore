package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkDuotoneTest {
    private val seeds = buildList {
        for (hue in 0 until 360 step 30) for ((chroma, tone) in listOf(8.0 to 14.0, 30.0 to 40.0, 70.0 to 60.0, 40.0 to 90.0)) add(Color(Hct.from(hue.toDouble(), chroma, tone).toInt()))
        add(Color(0xFF5145D8))
        add(Color(0xFF777777))
    }

    @Test
    fun `light words stay readable on the brightest part of any duotone cover`() {
        seeds.forEach { seed ->
            val duotone = ArtworkDuotone.fromColor(seed)
            val shade = ArtworkShade.fromColor(seed)
            assertTrue(artworkContrast(shade.content, duotone.highlight) >= 6f, "content on highlight for $seed")
            assertTrue(artworkContrast(shade.softContent, duotone.highlight) >= 4.5f, "soft content on highlight for $seed")
            assertTrue(Hct.fromInt(duotone.highlight.toArgb()).tone <= 39.0)
        }
        val neutralShade = ArtworkShade.Neutral
        assertTrue(artworkContrast(neutralShade.softContent, ArtworkDuotone.Neutral.highlight) >= 4.5f)
    }

    @Test
    fun `the filter maps black to the shadow and white to the highlight`() {
        val duotone = ArtworkDuotone.fromColor(Color(0xFF5145D8))
        fun apply(rgb: Float): FloatArray {
            // Mirrors colorFilter(): the same matrix rows applied to a grey pixel.
            val lift = 0.12f * 127.5f
            return floatArrayOf(duotone.shadow.red, duotone.shadow.green, duotone.shadow.blue).zip(floatArrayOf(duotone.highlight.red, duotone.highlight.green, duotone.highlight.blue))
                .map { (from, to) -> ((to - from) * 1.12f * rgb + from * 255f - (to - from) * lift).coerceIn(0f, 255f) / 255f }
                .toFloatArray()
        }
        val black = apply(0f)
        val white = apply(255f)
        // The contrast lift pushes both ends slightly past the tones, then clamps.
        assertEquals(duotone.shadow.red, black[0], 0.05f)
        assertEquals(duotone.highlight.blue, white[2], 0.05f)
    }

    @Test
    fun `vivid pairs keep a cover's light parts light and its own hue`() {
        seeds.forEach { seed ->
            val vivid = ArtworkDuotone.fromColor(seed, vivid = true)
            val shadow = Hct.fromInt(vivid.shadow.toArgb())
            val highlight = Hct.fromInt(vivid.highlight.toArgb())
            // The dark pair spans tones 8 to 38, which left Marquee's covers murky; the vivid pair spans most of the range.
            assertTrue(shadow.tone <= 11.0, "vivid shadow for $seed")
            assertTrue(highlight.tone >= 78.0, "vivid highlight for $seed")
            assertTrue(highlight.tone - Hct.fromInt(ArtworkDuotone.fromColor(seed).highlight.toArgb()).tone >= 38.0, "vivid lift for $seed")
        }
        // A yellow seed stays yellow in the light tone instead of turning gold.
        val yellow = Hct.fromInt(ArtworkDuotone.fromColor(Color(0xFFFFCC00), vivid = true).highlight.toArgb())
        assertEquals(Hct.fromInt(Color(0xFFFFCC00).toArgb()).hue, yellow.hue, 6.0)
        val neutral = Hct.fromInt(ArtworkDuotone.NeutralVivid.highlight.toArgb())
        assertTrue(neutral.chroma < 4.0 && neutral.tone >= 78.0, "neutral vivid highlight $neutral")
        assertEquals(ArtworkDuotone.fromColor(Color(0xFF2855BB), vivid = true), ArtworkDuotone.fromPalette(ArtworkPalette(Color(0xFF2855BB).toArgb(), 0), vivid = true))
    }

    @Test
    fun `greys and the dark option give a black and white duotone`() {
        val grey = Hct.fromInt(ArtworkDuotone.fromColor(Color(0xFF777777)).highlight.toArgb())
        assertTrue(grey.chroma < 4.0, "grey cover highlight $grey")
        assertTrue(Hct.fromInt(ArtworkDuotone.Neutral.shadow.toArgb()).tone < 10.0)
        assertEquals(ArtworkDuotone.fromColor(Color(0xFF2855BB)), ArtworkDuotone.fromPalette(ArtworkPalette(Color(0xFF2855BB).toArgb(), 0)))
    }
}
