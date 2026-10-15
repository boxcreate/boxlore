package cx.aswin.boxlore.core.designsystem.theme

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
