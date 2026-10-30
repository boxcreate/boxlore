package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The full player's scheme ([artworkPlayerColorScheme]) is the brand scheme built from the cover's
 * seed; it runs on Android's colour utilities, hence Robolectric.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ArtworkPlayerColorSchemeTest {
    @Test
    fun `player text and buttons stay readable for any cover, mode and pure style`() {
        val failures = ArtworkSchemeFixtures.palettes.flatMap { palette ->
            ArtworkSchemeFixtures.styles.flatMap { (dark, style) ->
                ArtworkSchemeFixtures.unreadable(artworkPlayerColorScheme(palette, dark, style), 4.5f, "player palette=$palette dark=$dark style=$style")
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `light mode keeps a light Play icon on a deeper primary instead of turning it black`() {
        val yellow = ArtworkPalette(seed = ArtworkSchemeFixtures.hct(95.0, 70.0, 80.0), average = 0)
        val scheme = artworkPlayerColorScheme(yellow, isDark = false)
        assertTrue("Play icon stays light: ${scheme.onPrimary}", scheme.onPrimary.luminance() > 0.5f)
        assertTrue("Play icon reads", artworkContrast(scheme.onPrimary, scheme.primary) >= 4.5f)
        val hue = Hct.fromInt(scheme.primary.toArgb()).hue
        assertTrue("primary keeps the cover's hue: $hue", ArtworkSchemeFixtures.hueGap(hue, 95.0) < 25.0)
    }

    @Test
    fun `light mode text is a deep shade of the cover's hue, not black`() {
        val teal = ArtworkPalette(seed = ArtworkSchemeFixtures.hct(190.0, 40.0, 45.0), average = 0)
        val scheme = artworkPlayerColorScheme(teal, isDark = false)
        val text = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(scheme.onSurface.toArgb(), text)
        assertTrue("text is not near-black: lightness=${text[2]}", text[2] >= 0.15f)
        assertTrue("text still reads clearly", artworkContrast(scheme.onSurface, scheme.surface) >= 7f)
        assertTrue("dark mode is unchanged", artworkPlayerColorScheme(teal, isDark = true).onSurface == generateBrandColorScheme(androidx.compose.ui.graphics.Color(teal.seed), true).onSurface)
    }

    @Test
    fun `player colours keep the cover's hue`() {
        val teal = ArtworkPalette(seed = ArtworkSchemeFixtures.hct(190.0, 40.0, 45.0), average = 0)
        listOf(false, true).forEach { dark ->
            val scheme = artworkPlayerColorScheme(teal, dark)
            listOf("primary" to scheme.primary, "primaryContainer" to scheme.primaryContainer).forEach { (name, colour) ->
                val hue = Hct.fromInt(colour.toArgb()).hue
                assertTrue("$name keeps the cover's hue in the player (dark=$dark): $hue", ArtworkSchemeFixtures.hueGap(hue, 190.0) < 20.0)
            }
        }
    }
}
