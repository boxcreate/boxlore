package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkShadeTest {
    private fun hct(hue: Double, chroma: Double, tone: Double) = Hct.from(hue, chroma, tone).toInt()

    // Seeds the picker can return: any hue, chroma at least 8, tone 12 to 92.
    private val palettes: List<ArtworkPalette> = buildList {
        for (hue in 0 until 360 step 30) {
            for ((chroma, tone) in listOf(8.0 to 14.0, 20.0 to 30.0, 70.0 to 55.0, 40.0 to 90.0)) {
                add(ArtworkPalette(seed = hct(hue.toDouble(), chroma, tone), average = hct(hue.toDouble(), chroma, tone)))
            }
        }
    }

    // Theme primaries from light and dark schemes, plus vivid and greyed extremes.
    private val themeColors = listOf(Color(0xFF5145D8), Color(0xFF9C8CFF), Color(0xFF006874), Color(0xFFFFB4A3), Color(0xFFCCCC55), Color(0xFF777777), Color(0xFFFF0000))

    private fun shades() = palettes.map(ArtworkShade::fromPalette) + themeColors.map(ArtworkShade::fromColor) + ArtworkShade.Neutral

    @Test
    fun `words and buttons stay readable on every shade`() {
        shades().forEach { shade ->
            assertTrue(artworkContrast(shade.content, shade.deep) >= 7f, "content on deep: $shade")
            assertTrue(artworkContrast(shade.softContent, shade.deep) >= 4.5f, "soft content on deep: $shade")
            assertTrue(artworkContrast(shade.onAccent, shade.accent) >= 4.5f, "button label: $shade")
            assertTrue(artworkContrast(shade.content, shade.container) >= 4.5f, "content on container: $shade")
            assertTrue(artworkContrast(shade.accent, shade.deep) >= 3f, "button against the shade: $shade")
        }
    }

    @Test
    fun `the shade keeps the source hue and stays dark`() {
        val violet = ArtworkShade.fromColor(Color(0xFF5145D8))
        val source = Hct.fromInt(Color(0xFF5145D8).toArgb())
        val deep = Hct.fromInt(violet.deep.toArgb())
        assertTrue(kotlin.math.abs(deep.hue - source.hue) < 8.0, "hue ${deep.hue} vs ${source.hue}")
        assertTrue(deep.chroma > 12.0, "a theme shade reads as its colour, not black")
        assertEquals(16.0, deep.tone, 1.0)
    }

    @Test
    fun `a cover's shade follows its seed`() {
        val seed = Color(0xFF2855BB)
        assertEquals(ArtworkShade.fromColor(seed), ArtworkShade.fromPalette(ArtworkPalette(seed = seed.toArgb(), average = 0)))
    }

    @Test
    fun `greyish colours and the dark option give a near-neutral shade`() {
        val grey = ArtworkShade.fromColor(Color(0xFF777777))
        val deep = Hct.fromInt(grey.deep.toArgb())
        val accent = Hct.fromInt(grey.accent.toArgb())
        assertTrue(deep.chroma <= 7.0, "grey shade: $deep from ${Hct.fromInt(Color(0xFF777777).toArgb())}")
        // HCT reads exact greys at a chroma of about 2, not 0.
        assertTrue(accent.chroma < 4.0, "grey button: $accent")
        val neutral = Hct.fromInt(ArtworkShade.Neutral.deep.toArgb())
        assertTrue(neutral.chroma < 2.0 && neutral.tone < 8.0, "Dark is a plain near-black shade: $neutral")
    }

    @Test
    fun `a yellow cover turns gold instead of olive when it goes dark`() {
        val yellow = ArtworkShade.fromColor(Color(0xFFE8D33A))
        val hue = Hct.fromInt(yellow.deep.toArgb()).hue
        assertTrue(hue in 50.0..70.0, "deep yellow hue $hue")
    }
}
