package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ClassicBrandColorsTest {
    @Test
    fun `classic resolves authored light and dark accents`() {
        val light = resolveFixedThemeColorScheme("violet", false, SurfaceStyles.CLASSIC_DYNAMIC)
        val dark = resolveFixedThemeColorScheme("violet", true, SurfaceStyles.CLASSIC_DYNAMIC)
        assertEquals(Color(0xFF563DE8), light.primary)
        assertEquals(Color(0xFF4D607E), light.secondary)
        assertEquals(Color(0xFF226C62), light.tertiary)
        assertEquals(Color(0xFFA594FF), dark.primary)
        assertEquals(Color(0xFFAABDDD), dark.secondary)
        assertEquals(Color(0xFF82CFC0), dark.tertiary)
    }

    @Test
    fun `classic uses cool neutral surfaces with distinct container levels`() {
        assertEquals(Color(0xFFF8F8FB), ClassicBrandColors.light.background)
        assertEquals(Color(0xFFF8F8FB), ClassicBrandColors.light.surface)
        assertEquals(Color(0xFFEFEDF6), ClassicBrandColors.light.surfaceContainer)
        assertEquals(Color(0xFF121318), ClassicBrandColors.dark.background)
        assertEquals(Color(0xFF121318), ClassicBrandColors.dark.surface)
        assertEquals(Color(0xFF211F29), ClassicBrandColors.dark.surfaceContainer)
    }

    @Test
    fun `legacy classic mode locks still select the matching palette`() {
        assertEquals(ClassicBrandColors.light, resolveFixedThemeColorScheme("violet", true, SurfaceStyles.CLASSIC_LIGHT))
        assertEquals(ClassicBrandColors.dark, resolveFixedThemeColorScheme("violet", false, SurfaceStyles.CLASSIC_DARK))
    }

    @Test
    fun `classic text remains readable on accents containers and surfaces`() {
        listOf(ClassicBrandColors.light, ClassicBrandColors.dark).forEach { scheme ->
            listOf(
                scheme.primary to scheme.onPrimary,
                scheme.secondary to scheme.onSecondary,
                scheme.tertiary to scheme.onTertiary,
                scheme.primaryContainer to scheme.onPrimaryContainer,
                scheme.secondaryContainer to scheme.onSecondaryContainer,
                scheme.tertiaryContainer to scheme.onTertiaryContainer,
                scheme.primaryFixedDim to scheme.onPrimaryFixedVariant,
                scheme.secondaryFixedDim to scheme.onSecondaryFixedVariant,
                scheme.tertiaryFixedDim to scheme.onTertiaryFixedVariant,
                scheme.primaryFixed to scheme.onPrimaryFixed,
                scheme.secondaryFixed to scheme.onSecondaryFixed,
                scheme.tertiaryFixed to scheme.onTertiaryFixed,
                scheme.surface to scheme.onSurface,
                scheme.surfaceContainerHighest to scheme.onSurfaceVariant,
            ).forEach { (background, foreground) ->
                val a = background.luminance()
                val b = foreground.luminance()
                assertTrue((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f)
            }
        }
    }

    @Test
    fun `small accent text stays readable across the entire neutral surface hierarchy`() {
        listOf(ClassicBrandColors.light, ClassicBrandColors.dark).forEach { scheme ->
            val surfaces = listOf(scheme.background, scheme.surfaceDim, scheme.surfaceBright, scheme.surfaceContainerLowest, scheme.surfaceContainerLow, scheme.surfaceContainer, scheme.surfaceContainerHigh, scheme.surfaceContainerHighest)
            surfaces.forEach { surface ->
                listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.onSurface, scheme.onSurfaceVariant).forEach { foreground ->
                    val a = surface.luminance()
                    val b = foreground.luminance()
                    assertTrue((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f, "$foreground on $surface")
                }
            }
        }
    }
}
