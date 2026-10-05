package cx.aswin.boxlore.feature.settings

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.ThemePresets
import cx.aswin.boxlore.core.designsystem.theme.computeEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.generateBrandColorScheme
import cx.aswin.boxlore.core.designsystem.theme.resolveBoxLoreChromeColors
import cx.aswin.boxlore.core.designsystem.theme.resolveBoxLoreColorScheme
import cx.aswin.boxlore.core.designsystem.theme.resolveFixedThemeColorScheme
import cx.aswin.boxlore.core.designsystem.theme.resolveThemeSeedColor
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemePresetResolutionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun completePresetsMatchAppAndPreviewAndFollowMode() {
        ThemePresets.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val actual = resolveBoxLoreColorScheme(context, dark, false, preset.key, preset.key)
                val preview = resolveFixedThemeColorScheme(preset.key, dark, preset.key)
                assertEquals(if (dark) preset.background.dark else preset.background.light, actual.background)
                assertEquals(if (dark) preset.primary.dark else preset.primary.light, actual.primary)
                assertEquals(preview.primary, actual.primary)
                assertEquals(preview.secondary, actual.secondary)
                assertEquals(preview.tertiary, actual.tertiary)
                assertEquals(preview.surfaceContainerHigh, actual.surfaceContainerHigh)
                val widget = resolveBoxLoreChromeColors(context, dark, false, preset.key, preset.key)
                assertEquals(actual.surface.toArgb(), widget.surface)
                assertEquals(actual.primary.toArgb(), widget.primary)
                assertEquals(actual.secondaryContainer.toArgb(), widget.secondaryContainer)
            }
        }
    }

    @Test
    fun accentCustomisationAndWallpaperColorsRetainPresetBackgrounds() {
        ThemePresets.forEach { preset ->
            listOf(false, true).forEach { dark ->
                val expected = if (dark) preset.background.dark else preset.background.light
                val custom = resolveBoxLoreColorScheme(context, dark, false, "exact:#006C4C", preset.key)
                assertEquals(expected, custom.background)
                assertEquals(Color(0xFF006C4C), custom.primary)
                val wallpaper = resolveBoxLoreColorScheme(context, dark, true, preset.key, preset.key)
                assertEquals(expected, wallpaper.background)
            }
        }
    }

    @Test
    fun existingBackgroundStylesAndLegacyLocksStillApplyWithPresetAccents() {
        ThemePresets.forEach { preset ->
            SurfaceStyles.entries.forEach { style ->
                listOf(false, true).forEach { dark ->
                    val effectiveDark = computeEffectiveDarkTheme(style.key, dark)
                    val original = generateBrandColorScheme(preset.primary.light, effectiveDark, style.key)
                    val actual = resolveBoxLoreColorScheme(context, dark, false, preset.key, style.key)
                    assertEquals(original.background, actual.background)
                    assertEquals(original.surfaceContainerHigh, actual.surfaceContainerHigh)
                    assertEquals(if (effectiveDark) preset.primary.dark else preset.primary.light, actual.primary)
                }
            }
        }
    }

    @Test
    fun ordinarySeedColorsKeepTheirExistingSchemeAndUnknownKeysFallBack() {
        listOf("violet", "emerald", "#985368", "preset:unknown").forEach { brand ->
            listOf(false, true).forEach { dark ->
                val original = generateBrandColorScheme(resolveThemeSeedColor(brand), dark, SurfaceStyles.STANDARD)
                val actual = resolveBoxLoreColorScheme(context, dark, false, brand, SurfaceStyles.STANDARD)
                assertEquals(original.primary, actual.primary)
                assertEquals(original.background, actual.background)
                assertEquals(original.tertiary, actual.tertiary)
            }
        }
    }
}
