package cx.aswin.boxlore.core.designsystem.component

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import cx.aswin.boxlore.core.designsystem.theme.ThemePresets
import cx.aswin.boxlore.core.designsystem.theme.withPinnedPrimary
import cx.aswin.boxlore.core.designsystem.theme.withThemePreset
import org.junit.jupiter.api.Assertions.assertEquals
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
}
