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
        assertEquals(Color(0xFF5145D8), light.primary)
        assertEquals(Color(0xFF55647C), light.secondary)
        assertEquals(Color(0xFF397B6D), light.tertiary)
        assertEquals(Color(0xFF9C8CFF), dark.primary)
        assertEquals(Color(0xFFA3B1CA), dark.secondary)
        assertEquals(Color(0xFF89C6B5), dark.tertiary)
    }

    @Test
    fun `accent refinement retains classic backgrounds and surface levels`() {
        assertEquals(Color(0xFFF8F8FB), ClassicBrandColors.light.background)
        assertEquals(Color(0xFFF8F8FB), ClassicBrandColors.light.surface)
        assertEquals(Color(0xFFEDEDF4), ClassicBrandColors.light.surfaceContainer)
        assertEquals(Color(0xFF121318), ClassicBrandColors.dark.background)
        assertEquals(Color(0xFF121318), ClassicBrandColors.dark.surface)
        assertEquals(Color(0xFF20212A), ClassicBrandColors.dark.surfaceContainer)
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
                scheme.surface to scheme.onSurface,
                scheme.surfaceContainerHighest to scheme.onSurfaceVariant,
            ).forEach { (background, foreground) ->
                val a = background.luminance()
                val b = foreground.luminance()
                assertTrue((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.5f)
            }
        }
    }
}
