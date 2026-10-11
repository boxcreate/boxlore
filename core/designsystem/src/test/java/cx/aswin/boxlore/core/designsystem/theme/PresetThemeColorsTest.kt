package cx.aswin.boxlore.core.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance as relativeLuminance
import androidx.compose.ui.graphics.toArgb
import com.materialkolor.hct.Hct
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PresetThemeColorsTest {
    @Test
    fun `pure shares classic accents in both modes with strictly white and black backgrounds`() {
        listOf(false, true).forEach { dark ->
            val classic = resolveFixedThemeColorScheme("violet", dark, SurfaceStyles.CLASSIC_DYNAMIC)
            val pure = resolveFixedThemeColorScheme("violet", dark, SurfaceStyles.DYNAMIC_OLED_WHITE)
            assertEquals(classic.primary, pure.primary)
            assertEquals(classic.secondary, pure.secondary)
            assertEquals(classic.tertiary, pure.tertiary)
            assertEquals(classic.primaryFixed, pure.primaryFixed)
            assertEquals(if (dark) Color.Black else Color.White, pure.background)
            assertEquals(pure.background, pure.surface)
            assertReadable(pure)
            val levels = listOf(pure.surfaceContainerLowest, pure.surfaceContainerLow, pure.surfaceContainer, pure.surfaceContainerHigh, pure.surfaceContainerHighest)
            levels.zipWithNext().forEach { (a, b) ->
                assertTrue(if (dark) a.relativeLuminance() < b.relativeLuminance() else a.relativeLuminance() > b.relativeLuminance())
            }
        }
    }

    @Test
    fun `pure legacy mode locks and custom accent inputs are preserved`() {
        assertEquals(Color.Black, resolveFixedThemeColorScheme("violet", false, SurfaceStyles.AMOLED).surface)
        assertEquals(Color.White, resolveFixedThemeColorScheme("violet", true, SurfaceStyles.PURE_WHITE).surface)
        listOf(false, true).forEach { dark ->
            val seeds = CustomThemeSeeds(Color(0xFFD34027), Color(0xFF456600), Color(0xFF006A70))
            val original = generatePersonalColorScheme(seeds, dark)
            val pure = resolveFixedThemeColorScheme(seeds.encode(), dark, SurfaceStyles.DYNAMIC_OLED_WHITE)
            assertEquals(original.primary, pure.primary)
            assertEquals(original.secondary, pure.secondary)
            assertEquals(original.tertiary, pure.tertiary)
            assertEquals(original.error, pure.error)
            assertReadable(pure)
        }
    }

    @Test
    fun `wallpaper refinement preserves hue families supporting roles and neutral surfaces`() {
        listOf(Color(0xFF416D32), Color(0xFFAD4B35), Color(0xFF2768AB), Color(0xFF6750A4), Color.Gray).forEach { seed ->
            listOf(false, true).forEach { dark ->
                val base = generatePersonalColorScheme(CustomThemeSeeds(seed), dark)
                val refined = refineWallpaperColors(base, dark)
                assertEquals(
                    base.toString(),
                    refined.copy(
                    primary = base.primary, onPrimary = base.onPrimary,
                    primaryContainer = base.primaryContainer, onPrimaryContainer = base.onPrimaryContainer,
                    inversePrimary = base.inversePrimary, surfaceTint = base.surfaceTint,
                    primaryFixed = base.primaryFixed, primaryFixedDim = base.primaryFixedDim,
                    onPrimaryFixed = base.onPrimaryFixed, onPrimaryFixedVariant = base.onPrimaryFixedVariant,
                ).toString(),
                )
                val before = Hct.fromInt(base.primary.toArgb())
                val after = Hct.fromInt(refined.primary.toArgb())
                val hueDifference = kotlin.math.abs(before.hue - after.hue)
                if (before.chroma > 5) assertTrue(minOf(hueDifference, 360 - hueDifference) < 4)
                assertEquals(if (dark) 76.0 else 40.0, after.tone, 0.5)
                assertReadable(refined)
            }
        }
    }

    @Test
    fun `fixed roles stay identical across classic modes and wallpaper modes`() {
        assertFixedRolesEqual(ClassicBrandColors.light, ClassicBrandColors.dark)
        val seeds = CustomThemeSeeds(Color(0xFF558129))
        assertFixedRolesEqual(refineWallpaperColors(generatePersonalColorScheme(seeds, false), false), refineWallpaperColors(generatePersonalColorScheme(seeds, true), true))
    }

    private fun assertFixedRolesEqual(light: ColorScheme, dark: ColorScheme) {
        assertEquals(light.primaryFixed, dark.primaryFixed)
        assertEquals(light.primaryFixedDim, dark.primaryFixedDim)
        assertEquals(light.onPrimaryFixed, dark.onPrimaryFixed)
        assertEquals(light.onPrimaryFixedVariant, dark.onPrimaryFixedVariant)
        assertEquals(light.secondaryFixed, dark.secondaryFixed)
        assertEquals(light.tertiaryFixed, dark.tertiaryFixed)
    }

    private fun assertReadable(scheme: ColorScheme) {
        listOf(
            scheme.primary to scheme.onPrimary, scheme.primaryContainer to scheme.onPrimaryContainer,
            scheme.secondary to scheme.onSecondary, scheme.secondaryContainer to scheme.onSecondaryContainer,
            scheme.tertiary to scheme.onTertiary, scheme.tertiaryContainer to scheme.onTertiaryContainer,
            scheme.primaryFixedDim to scheme.onPrimaryFixedVariant,
            scheme.surface to scheme.onSurface, scheme.surfaceContainerHighest to scheme.onSurfaceVariant,
            scheme.inverseSurface to scheme.inverseOnSurface,
        ).forEach { (background, foreground) ->
            val a = background.relativeLuminance()
            val b = foreground.relativeLuminance()
            assertTrue((maxOf(a, b) + 0.05f) / (minOf(a, b) + 0.05f) >= 4.49f, "$background / $foreground")
        }
    }
}
