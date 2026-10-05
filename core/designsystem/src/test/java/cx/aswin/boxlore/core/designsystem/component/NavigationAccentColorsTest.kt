package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import cx.aswin.boxlore.core.designsystem.theme.ThemePresets
import cx.aswin.boxlore.core.designsystem.theme.withPinnedPrimary
import cx.aswin.boxlore.core.designsystem.theme.withThemePreset
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NavigationAccentColorsTest {
    @Test
    fun `regular navigation uses every preset's authored accent in both modes`() {
        ThemePresets.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val scheme = (if (dark) darkColorScheme() else lightColorScheme()).withThemePreset(preset, dark)
                val colors = navigationAccentColors(scheme)

                assertEquals(if (dark) preset.primary.dark else preset.primary.light, colors.indicator, "${preset.name} dark=$dark")
                assertEquals(scheme.onPrimary, colors.content)
            }
        }
    }

    @Test
    fun `custom exact accent replaces the indicator even when containers retain other colors`() {
        val exact = Color(0xFFFF5722)
        val scheme = darkColorScheme(primaryContainer = Color.Blue, secondaryContainer = Color.Green).withPinnedPrimary(exact)
        val colors = navigationAccentColors(scheme)

        assertEquals(exact, colors.indicator)
        assertEquals(scheme.onPrimary, colors.content)
    }

    @Test
    fun `wallpaper primary and foreground win over unrelated secondary and container roles`() {
        val scheme = lightColorScheme(
            primary = Color(0xFF006C4C),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFD6FFEA),
            secondaryContainer = Color(0xFFFFE0B2),
        )
        val colors = navigationAccentColors(scheme)

        assertEquals(Color(0xFF006C4C), colors.indicator)
        assertEquals(Color.White, colors.content)
    }

    @Test
    fun `Lore uses the primary foreground before its aurora container arrives`() {
        val scheme = darkColorScheme(primary = Color(0xFFCEC0FF), onPrimary = Color.Black, onPrimaryContainer = Color.White)
        listOf(0f, 1f, 2f, Float.NaN, Float.POSITIVE_INFINITY).forEach { index ->
            assertEquals(scheme.onPrimary, loreNavigationContentColor(scheme, index))
        }
    }

    @Test
    fun `resting Lore retains the foreground paired with its original container base`() {
        val scheme = darkColorScheme(primaryContainer = Color(0xFF28203A), onPrimary = Color.Black, onPrimaryContainer = Color.White)
        assertEquals(scheme.onPrimaryContainer, loreNavigationContentColor(scheme, 3f))
        assertEquals(scheme.onPrimaryContainer, loreNavigationContentColor(scheme, 4f))
    }

    @Test
    fun `Lore transition keeps icon contrast against the blended solid base for every preset`() {
        ThemePresets.forEach { preset ->
            assertLoreTransitionContrast(lightColorScheme().withThemePreset(preset, false), preset.name)
            assertLoreTransitionContrast(darkColorScheme().withThemePreset(preset, true), preset.name)
        }
    }

    private fun assertLoreTransitionContrast(scheme: androidx.compose.material3.ColorScheme, name: String) {
        for (step in 0..100) {
            val index = 2f + step / 100f
            val blend = floatingNavigationLoreAuroraBlend(index)
            val background = scheme.primaryContainer.copy(alpha = blend).compositeOver(scheme.primary)
            val foreground = loreNavigationContentColor(scheme, index)
            val luminance = listOf(background.luminance(), foreground.luminance()).sorted()
            val contrast = (luminance.last() + 0.05f) / (luminance.first() + 0.05f)
            assertTrue(contrast >= 3f, "$name at index $index: contrast=$contrast")
        }
    }
}
