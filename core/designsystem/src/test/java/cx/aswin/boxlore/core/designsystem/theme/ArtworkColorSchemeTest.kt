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

    private val palettes: List<ArtworkPalette> = buildList {
        for (hue in 0 until 360 step 30) {
            for ((chroma, tone) in listOf(20.0 to 30.0, 70.0 to 55.0, 40.0 to 85.0)) {
                add(ArtworkPalette(ground = hct(hue.toDouble(), chroma, tone), accent = hct((hue + 150.0) % 360, 80.0, 60.0), average = hct(hue.toDouble(), chroma, tone)))
            }
        }
        // Neutral grounds: white, black and cream covers with one colour.
        add(ArtworkPalette(Color.White.toArgb(), hct(25.0, 80.0, 50.0), Color.White.toArgb()))
        add(ArtworkPalette(Color.Black.toArgb(), hct(100.0, 70.0, 85.0), Color.Black.toArgb()))
        add(ArtworkPalette(Color(0xFFF3EEDC).toArgb(), hct(180.0, 40.0, 50.0), Color(0xFFF3EEDC).toArgb()))
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
    fun `every text and control pair stays readable for any cover, mode and pure style`() {
        palettes.forEach { palette ->
            styles.forEach { (dark, style) ->
                val scheme = artworkColorScheme(palette, dark, style)
                val where = "palette=$palette dark=$dark style=$style"
                textPairs(scheme).forEach { (name, pair) -> assertReadable(pair.first, pair.second, 4.5f, "$name $where") }
                assertReadable(scheme.primary, scheme.surfaceContainerLow, 3f, "primary on cards $where")
            }
        }
    }

    @Test
    fun `the ground carries the cover's hue at a fixed tone, and neutral covers stay neutral`() {
        val teal = ArtworkPalette(ground = hct(190.0, 40.0, 45.0), accent = hct(30.0, 80.0, 60.0), average = 0)
        listOf(false to 92.0, true to 11.0).forEach { (dark, tone) ->
            val ground = Hct.fromInt(artworkColorScheme(teal, dark).background.toArgb())
            assertEquals(tone, ground.tone, 1.0)
            assertTrue(ground.chroma >= 5.0, "A coloured cover tints the page: ${ground.chroma}")
            assertTrue(hueGap(ground.hue, 190.0) < 20.0, "Ground keeps the cover's hue: ${ground.hue}")
        }
        val white = ArtworkPalette(ground = Color.White.toArgb(), accent = hct(30.0, 80.0, 60.0), average = 0)
        listOf(false, true).forEach { dark ->
            assertTrue(Hct.fromInt(artworkColorScheme(white, dark).background.toArgb()).chroma <= 4.5)
        }
    }

    @Test
    fun `pure styles keep black and white grounds while the accent still comes from the cover`() {
        val palette = ArtworkPalette(ground = hct(190.0, 40.0, 45.0), accent = hct(30.0, 80.0, 60.0), average = 0)
        assertEquals(Color.Black, artworkColorScheme(palette, true, SurfaceStyles.AMOLED).background)
        assertEquals(Color.White, artworkColorScheme(palette, false, SurfaceStyles.PURE_WHITE).background)
        assertTrue(hueGap(Hct.fromInt(artworkColorScheme(palette, true, SurfaceStyles.AMOLED).primary.toArgb()).hue, 30.0) < 20.0)
    }

    @Test
    fun `a yellow accent that has to be dark turns gold rather than olive`() {
        val yellow = ArtworkPalette(ground = Color.White.toArgb(), accent = hct(100.0, 70.0, 85.0), average = 0)
        assertTrue(Hct.fromInt(artworkColorScheme(yellow, isDark = false).primary.toArgb()).hue < 80.0)
        assertTrue(artworkHueForDarkTone(100.0) in 55.0..65.0)
        assertEquals(30.0, artworkHueForDarkTone(30.0))
        assertEquals(200.0, artworkHueForDarkTone(200.0))
    }

    @Test
    fun `text stays readable throughout the colour handoff between pages`() {
        val red = ArtworkPalette(hct(25.0, 60.0, 45.0), hct(25.0, 80.0, 55.0), 0)
        val blue = ArtworkPalette(hct(260.0, 50.0, 70.0), hct(200.0, 60.0, 60.0), 0)
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

    private fun textPairs(scheme: ColorScheme) = listOf(
        "onSurface/background" to (scheme.onSurface to scheme.background),
        "onBackground/background" to (scheme.onBackground to scheme.background),
        "onSurface/surface" to (scheme.onSurface to scheme.surface),
        "onSurfaceVariant/surface" to (scheme.onSurfaceVariant to scheme.surface),
        "onSurfaceVariant/low" to (scheme.onSurfaceVariant to scheme.surfaceContainerLow),
        "onSurfaceVariant/container" to (scheme.onSurfaceVariant to scheme.surfaceContainer),
        "onSurfaceVariant/high" to (scheme.onSurfaceVariant to scheme.surfaceContainerHigh),
        "onSurfaceVariant/highest" to (scheme.onSurfaceVariant to scheme.surfaceContainerHighest),
        "primary/background" to (scheme.primary to scheme.background),
        "onPrimary/primary" to (scheme.onPrimary to scheme.primary),
        "onPrimaryContainer/primaryContainer" to (scheme.onPrimaryContainer to scheme.primaryContainer),
        "onSecondaryContainer/secondaryContainer" to (scheme.onSecondaryContainer to scheme.secondaryContainer),
    )

    private fun assertReadable(foreground: Color, background: Color, minimum: Float, where: String) {
        val contrast = artworkContrast(foreground, background)
        assertTrue(contrast >= minimum, "$where: contrast=$contrast")
    }

    private fun hueGap(a: Double, b: Double) = abs(a - b).let { min(it, 360.0 - it) }

    @Test
    fun `player scheme keeps readable controls on a soft tinted surface in both modes`() {
        for (palette in palettes) {
            for ((dark, style) in styles) {
                val scheme = artworkPlayerColorScheme(palette, dark, style)
                fun readable(fg: Color, bg: Color, min: Float, label: String) =
                    assertTrue(artworkContrast(fg, bg) >= min, "$label ${artworkContrast(fg, bg)} for dark=$dark style=$style")
                readable(scheme.onSurface, scheme.surface, 4.5f, "text on background")
                readable(scheme.onSurface, scheme.surfaceContainerHighest, 4.5f, "text on highest container")
                readable(scheme.onSurfaceVariant, scheme.surfaceContainerHigh, 4.5f, "secondary text on cards")
                readable(scheme.onSecondaryContainer, scheme.secondaryContainer, 4.5f, "control tiles")
                readable(scheme.onPrimary, scheme.primary, 4.5f, "play button")
                readable(scheme.primary, scheme.surface, 3f, "accent icons on background")
            }
            val dark = artworkPlayerColorScheme(palette, true)
            assertEquals(20.0, Hct.fromInt(dark.surface.toArgb()).tone, 1.0)
            assertEquals(92.0, Hct.fromInt(artworkPlayerColorScheme(palette, false).surface.toArgb()).tone, 1.0)
            assertTrue(Hct.fromInt(dark.surfaceContainerHighest.toArgb()).tone > Hct.fromInt(dark.surface.toArgb()).tone + 10)
        }
    }

    @Test
    fun `mini player bar follows the cover's main colour and keeps every pair readable`() {
        for (palette in palettes) {
            for (dark in listOf(false, true)) {
                val mini = artworkMiniPlayerColors(palette, dark)
                fun readable(fg: Color, bg: Color, min: Float, label: String) =
                    assertTrue(artworkContrast(fg, bg) >= min, "$label ${artworkContrast(fg, bg)} dark=$dark")
                readable(mini.onBar, mini.bar, 4.5f, "title on bar")
                readable(mini.onBarVariant, mini.bar, 4.5f, "show on bar")
                readable(mini.onBar, mini.progress, 4.5f, "title over the progress sweep")
                readable(mini.onPlay, mini.play, 4.5f, "Play icon")
                readable(mini.play, mini.bar, 3f, "Play end against the bar")
            }
        }
        // A strongly coloured ground leads the bar's hue; a neutral ground hands it to the accent.
        val red = ArtworkPalette(ground = hct(25.0, 60.0, 45.0), accent = hct(250.0, 70.0, 50.0), average = hct(25.0, 60.0, 45.0))
        assertEquals(25.0, Hct.fromInt(artworkMiniPlayerColors(red, isDark = false).bar.toArgb()).hue, 12.0)
        val grey = ArtworkPalette(ground = hct(0.0, 2.0, 50.0), accent = hct(250.0, 70.0, 50.0), average = hct(0.0, 2.0, 50.0))
        assertEquals(250.0, Hct.fromInt(artworkMiniPlayerColors(grey, isDark = false).bar.toArgb()).hue, 12.0)
    }

    @Test
    fun `mini and full player share one colour family`() {
        for (palette in palettes) {
            assertSharedFamily(palette, dark = false)
            assertSharedFamily(palette, dark = true)
        }
    }

    private fun assertSharedFamily(palette: ArtworkPalette, dark: Boolean) {
        val full = artworkPlayerColorScheme(palette, dark)
        val mini = artworkMiniPlayerColors(palette, dark)
        val fullHct = Hct.fromInt(full.surface.toArgb())
        val barHct = Hct.fromInt(mini.bar.toArgb())
        if (fullHct.chroma > 6.0 && barHct.chroma > 6.0) {
            val hueGap = abs(fullHct.hue - barHct.hue).let { min(it, 360.0 - it) }
            assertTrue(hueGap <= 15.0, "mini bar hue ${barHct.hue} vs player ${fullHct.hue} dark=$dark")
        }
        assertEquals(Hct.fromInt(full.primary.toArgb()).tone, Hct.fromInt(mini.play.toArgb()).tone, 3.0)
        // Light: the floating bar sits a step deeper than the player's background; dark: a step lighter.
        assertTrue(if (dark) barHct.tone > fullHct.tone else barHct.tone < fullHct.tone)
    }
}
