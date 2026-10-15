package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import kotlin.math.abs
import kotlin.math.min
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkColorSchemeTest {
    private fun hct(hue: Double, chroma: Double, tone: Double) = Hct.from(hue, chroma, tone).toInt()

    // Seeds the picker can return: any hue, chroma at least 8, tone 12 to 92.
    private val palettes: List<ArtworkPalette> = buildList {
        for (hue in 0 until 360 step 30) {
            for ((chroma, tone) in listOf(12.0 to 20.0, 70.0 to 55.0, 40.0 to 88.0)) {
                add(ArtworkPalette(seed = hct(hue.toDouble(), chroma, tone), average = hct(hue.toDouble(), chroma, tone)))
            }
        }
    }

    private val styles = listOf(
        false to SurfaceStyles.STANDARD,
        true to SurfaceStyles.STANDARD,
        true to SurfaceStyles.AMOLED,
        false to SurfaceStyles.PURE_WHITE,
        true to SurfaceStyles.DYNAMIC_OLED_WHITE,
        false to SurfaceStyles.DYNAMIC_OLED_WHITE,
    )

    @Test
    fun `page text and buttons stay readable for any cover, mode and pure style`() {
        palettes.forEach { palette ->
            styles.forEach { (dark, style) ->
                val scheme = artworkColorScheme(palette, dark, style)
                val where = "palette=$palette dark=$dark style=$style"
                textPairs(scheme).forEach { (name, pair) -> assertReadable(pair.first, pair.second, 4.5f, "$name $where") }
            }
        }
    }

    @Test
    fun `page colours keep the cover's hue`() {
        val teal = ArtworkPalette(seed = hct(190.0, 40.0, 45.0), average = 0)
        listOf(false, true).forEach { dark ->
            val primary = Hct.fromInt(artworkColorScheme(teal, dark).primary.toArgb())
            assertTrue(hueGap(primary.hue, 190.0) < 20.0, "primary keeps the cover's hue: ${primary.hue}")
        }
    }

    @Test
    fun `pure styles keep black and white page grounds`() {
        val palette = ArtworkPalette(seed = hct(190.0, 40.0, 45.0), average = 0)
        assertEquals(Color.Black, artworkColorScheme(palette, true, SurfaceStyles.AMOLED).background)
        assertEquals(Color.White, artworkColorScheme(palette, false, SurfaceStyles.PURE_WHITE).background)
    }

    @Test
    fun `text stays readable throughout the colour handoff between pages`() {
        val red = ArtworkPalette(hct(25.0, 60.0, 45.0), 0)
        val blue = ArtworkPalette(hct(260.0, 50.0, 70.0), 0)
        listOf(false, true).forEach { dark ->
            val app = if (dark) darkColorScheme() else lightColorScheme()
            listOf(app to artworkColorScheme(red, dark), artworkColorScheme(red, dark) to artworkColorScheme(blue, dark)).forEach { (from, to) ->
                for (step in 0..20) {
                    val t = step / 20f
                    assertReadable(lerp(from.onSurface, to.onSurface, t), lerp(from.background, to.background, t), 4.5f, "dark=$dark t=$t")
                }
            }
        }
    }

    @Test
    fun `mini player bar is the player's primary container`() {
        val scheme = darkColorScheme(primary = Color(0xFFFFB59D), onPrimary = Color(0xFF5C1900), primaryContainer = Color(0xFF7E2B14), onPrimaryContainer = Color(0xFFFFDBD0))
        val mini = ArtworkMiniPlayerColors.fromScheme(scheme)
        assertEquals(scheme.primaryContainer, mini.bar)
        assertEquals(scheme.onPrimaryContainer, mini.onBar)
        assertEquals(scheme.primary, mini.play)
        assertEquals(scheme.onPrimary, mini.onPlay)
    }

    @Test
    fun `a yellow that has to be dark turns gold rather than olive`() {
        assertTrue(artworkHueForDarkTone(100.0) in 55.0..65.0)
        assertEquals(30.0, artworkHueForDarkTone(30.0))
        assertEquals(200.0, artworkHueForDarkTone(200.0))
    }

    private fun textPairs(scheme: ColorScheme) = listOf(
        "onSurface/background" to (scheme.onSurface to scheme.background),
        "onBackground/background" to (scheme.onBackground to scheme.background),
        "onSurface/surface" to (scheme.onSurface to scheme.surface),
        "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
        "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
        "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
    )

    private fun assertReadable(foreground: Color, background: Color, minimum: Float, where: String) {
        val contrast = artworkContrast(foreground, background)
        assertTrue(contrast >= minimum, "$where: contrast=$contrast")
    }

    private fun hueGap(a: Double, b: Double) = abs(a - b).let { min(it, 360.0 - it) }
}
