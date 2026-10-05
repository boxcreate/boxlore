package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance as relativeLuminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ThemePresetsTest {
    @Test
    fun `sixteen distinct presets resolve through the existing brand key contract`() {
        assertEquals(16, ThemePresets.size)
        assertEquals(16, ThemePresets.map { it.key }.distinct().size)
        assertEquals(16, ThemePresets.map { it.primary.light }.distinct().size)
        assertEquals(16, ThemePresets.map { it.background.light }.distinct().size)
        assertEquals(16, ThemePresets.map { it.background.dark }.distinct().size)
        ThemePresets.forEach { preset ->
            assertEquals(preset, findThemePreset(preset.key))
            assertEquals(preset.primary.light, resolveThemeSeedColor(preset.key))
            assertFalse(isCustomThemeBrand(preset.key))
            assertFalse(BrandSeeds.containsKey(preset.key))
        }
        assertNull(findThemePreset("preset:unknown"))
        assertEquals(BrandSeeds["violet"]!!.second, resolveThemeSeedColor("preset:unknown"))
    }

    @Test
    fun `collections include quiet and bold complete themes without changing existing keys`() {
        assertEquals(2, ThemePresets.count { it.collection == ThemeCollection.MINIMAL })
        assertEquals(4, ThemePresets.count { it.collection == ThemeCollection.BOLD })
        assertEquals(10, ThemePresets.count { it.collection == ThemeCollection.COLORFUL })
        assertTrue(
            listOf("aurora", "tide", "moss", "dune", "ember", "rosewood", "iris", "glacier", "lagoon", "ink").all {
            findThemePreset("preset:$it") != null
        }
        )
    }

    @Test
    fun `preset backgrounds have readable surface text across the full elevation hierarchy`() {
        ThemePresets.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val base = if (dark) darkColorScheme() else lightColorScheme()
                val scheme = base.withPresetBackground(preset, dark).withThemePreset(preset, dark)
                assertEquals(if (dark) preset.background.dark else preset.background.light, scheme.background)
                assertEquals(scheme.background, scheme.surface)
                assertReadableSurfaces(scheme, "${preset.name} dark=$dark")
            }
        }
    }

    @Test
    fun `every preset has readable accent and container text in both modes`() {
        ThemePresets.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val base = if (dark) darkColorScheme() else lightColorScheme()
                val scheme = base.withThemePreset(preset, dark)
                val pairs = listOf(
                    scheme.onPrimary to scheme.primary,
                    scheme.onSecondary to scheme.secondary,
                    scheme.onTertiary to scheme.tertiary,
                    scheme.onPrimaryContainer to scheme.primaryContainer,
                    scheme.onSecondaryContainer to scheme.secondaryContainer,
                    scheme.onTertiaryContainer to scheme.tertiaryContainer,
                )
                pairs.forEach { (foreground, background) ->
                    assertTrue(
                        contrast(foreground, background) >= 4.5f,
                        "${preset.name} dark=$dark: $foreground on $background",
                    )
                }
                val opposite = base.withThemePreset(preset, !dark)
                assertEquals(opposite.primary, scheme.inversePrimary)
                assertEquals(scheme.primary, scheme.surfaceTint)
            }
        }
    }

    @Test
    fun `preset accents preserve surfaces and semantic error colors`() {
        val base = lightColorScheme(
            background = Color(0xFFF0EBE0),
            surface = Color(0xFFFBF8F1),
            surfaceContainerHigh = Color.White,
            error = Color.Red,
        )
        ThemePresets.forEach { preset ->
            val scheme = base.withThemePreset(preset, false)
            assertEquals(base.background, scheme.background)
            assertEquals(base.surface, scheme.surface)
            assertEquals(base.surfaceContainerLowest, scheme.surfaceContainerLowest)
            assertEquals(base.surfaceContainerHigh, scheme.surfaceContainerHigh)
            assertEquals(base.onSurface, scheme.onSurface)
            assertEquals(base.error, scheme.error)
        }
    }

    private fun contrast(foreground: Color, background: Color): Float {
        val first = foreground.relativeLuminance()
        val second = background.relativeLuminance()
        return (maxOf(first, second) + 0.05f) / (minOf(first, second) + 0.05f)
    }

    private fun assertReadableSurfaces(scheme: ColorScheme, label: String) {
        val surfaces = listOf(
            scheme.background,
            scheme.surfaceDim,
            scheme.surfaceBright,
            scheme.surfaceContainerLowest,
            scheme.surfaceContainerLow,
            scheme.surfaceContainer,
            scheme.surfaceContainerHigh,
            scheme.surfaceContainerHighest,
        )
        surfaces.forEach { surface ->
            listOf(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary).forEach { foreground ->
                assertTrue(contrast(foreground, surface) >= 4.5f, "$label: $foreground on surface $surface")
            }
        }
    }
}
