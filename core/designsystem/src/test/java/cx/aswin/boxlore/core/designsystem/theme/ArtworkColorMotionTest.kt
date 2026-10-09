package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ArtworkColorMotionTest {
    @Test fun `retargeting a late or replaced palette starts from the visible colors`() {
        val original = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), true)
        val first = generatePersonalColorScheme(CustomThemeSeeds(Color.Red), true)
        val next = generatePersonalColorScheme(CustomThemeSeeds(Color.Green), true)
        val transition = ColorSchemeTransition(original, first)
        val visible = transition.valueAt(0.4f)
        val interrupted = transition.retarget(next, 0.4f)
        val resumed = interrupted.valueAt(0f)
        assertEquals(visible.primary, resumed.primary)
        assertEquals(visible.background, resumed.background)
        assertEquals(visible.surfaceContainerHigh, resumed.surfaceContainerHigh)
        assertSame(next, interrupted.valueAt(1f))
        assertNotEquals(original.primary, resumed.primary)
        assertNotEquals(first.primary, resumed.primary)
    }

    @Test fun `transition endpoints and invalid progress cannot extrapolate colors`() {
        val from = generatePersonalColorScheme(CustomThemeSeeds(Color.Blue), false)
        val to = generatePersonalColorScheme(CustomThemeSeeds(Color.Red), false)
        listOf(-1f, 0f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY).forEach {
            assertSame(from, interpolateArtworkColors(from, to, it))
        }
        listOf(1f, 2f).forEach { assertSame(to, interpolateArtworkColors(from, to, it)) }
    }

    @Test fun `classic to artwork transition keeps text readable and navbar surfaces coordinated`() {
        listOf(false, true).forEach { dark ->
            val from = resolveFixedThemeColorScheme("violet", dark, SurfaceStyles.CLASSIC_DYNAMIC)
            listOf(Color.Red, Color.Green, Color.Blue, Color.Yellow, Color.Cyan).forEach { seed ->
                val to = generatePersonalColorScheme(CustomThemeSeeds(seed), dark)
                (0..20).forEach { step ->
                    val colors = interpolateArtworkColors(from, to, step / 20f)
                    assertReadable(colors, "$dark $seed $step")
                    val nav = navigationSchemeWithArtwork(from, colors)
                    assertEquals(colors.surfaceContainerHigh, nav.surfaceContainerHigh)
                    assertEquals(colors.onSurface, nav.onSurface)
                    assertEquals(colors.primary, nav.primary)
                }
            }
        }
    }

    @Test fun `pure retains neutral black or white surfaces through the entire palette handoff`() {
        listOf(false, true).forEach { dark ->
            val from = applySurfaceStyle(if (dark) ClassicBrandColors.dark else ClassicBrandColors.light, dark, SurfaceStyles.DYNAMIC_OLED_WHITE)
            val to = generatePersonalColorScheme(CustomThemeSeeds(Color.Red), dark, SurfaceStyles.DYNAMIC_OLED_WHITE)
            (0..20).forEach { step ->
                val colors = interpolateArtworkColors(from, to, step / 20f)
                assertEquals(if (dark) Color.Black else Color.White, colors.background)
                assertEquals(from.surfaceContainerHigh, navigationSchemeWithArtwork(from, colors).surfaceContainerHigh)
            }
        }
    }

    private fun assertReadable(scheme: ColorScheme, context: String) {
        listOf(
            scheme.primary to scheme.onPrimary,
            scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondary to scheme.onSecondary,
            scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiary to scheme.onTertiary,
            scheme.tertiaryContainer to scheme.onTertiaryContainer,
            scheme.background to scheme.onBackground,
            scheme.surfaceContainerHigh to scheme.onSurface,
        ).forEach { (background, foreground) ->
            val low = minOf(background.luminance(), foreground.luminance())
            val high = maxOf(background.luminance(), foreground.luminance())
            assertTrue((high + 0.05f) / (low + 0.05f) >= 4.49f, context)
        }
    }
}
