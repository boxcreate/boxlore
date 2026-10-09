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
import cx.aswin.boxlore.feature.settings.pages.AppearanceUiState
import cx.aswin.boxlore.feature.settings.pages.themeLookOptions
import cx.aswin.boxlore.feature.settings.pages.themePreviewColors
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

    @Test
    fun selectedSplitPreviewsShowCustomColorsInBothModes() {
        val look = themeLookOptions().first { it.key == "custom" }
        val state = AppearanceUiState("dark", false, "exact:#006C4C", "preset:aurora")
        val preview = themePreviewColors(context, state, look, selected = true)
        assertEquals(Color(0xFF006C4C), preview.light.primary)
        assertEquals(Color(0xFF006C4C), preview.dark.primary)
        assertEquals(resolveBoxLoreColorScheme(context, false, false, state.currentThemeBrand, state.currentSurfaceStyle).background, preview.light.background)
        assertEquals(resolveBoxLoreColorScheme(context, true, false, state.currentThemeBrand, state.currentSurfaceStyle).background, preview.dark.background)
    }

    @Test
    fun unselectedMaterial3ShowsWallpaperColorsAndClassicShowsItsOriginalColors() {
        val state = AppearanceUiState("dark", false, "preset:paper", "preset:paper")
        listOf(SurfaceStyles.STANDARD, SurfaceStyles.CLASSIC_DYNAMIC).forEach { key ->
            val look = themeLookOptions().first { it.key == key }
            val preview = themePreviewColors(context, state, look, selected = false)
            val brand = if (key == SurfaceStyles.CLASSIC_DYNAMIC) "violet" else state.currentThemeBrand
            val wallpaper = key == SurfaceStyles.STANDARD
            assertEquals(resolveBoxLoreColorScheme(context, false, wallpaper, brand, key).primary, preview.light.primary)
            assertEquals(resolveBoxLoreColorScheme(context, true, wallpaper, brand, key).primary, preview.dark.primary)
        }
    }

    @Test
    fun customCreatorUsesTheSameSchemeInAppPreviewAndWidgets() {
        val seeds = cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds(Color(0xFF4422EE), Color(0xFF007766), Color(0xFFAA5522))
        val state = AppearanceUiState("system", false, seeds.encode(), SurfaceStyles.DYNAMIC_OLED_WHITE)
        val look = themeLookOptions().first { it.key == "custom" }
        val preview = themePreviewColors(context, state, look, selected = true)
        listOf(false, true).forEach { dark ->
            val app = resolveBoxLoreColorScheme(context, dark, false, seeds.encode(), state.currentSurfaceStyle)
            val shown = if (dark) preview.dark else preview.light
            val widget = cx.aswin.boxlore.core.designsystem.theme.resolveBoxLoreChromeColors(context, dark, false, seeds.encode(), state.currentSurfaceStyle)
            assertEquals(app.primary, shown.primary)
            assertEquals(app.secondary, shown.secondary)
            assertEquals(app.tertiary, shown.tertiary)
            assertEquals(app.primary.toArgb(), widget.primary)
            assertEquals(app.surface.toArgb(), widget.surface)
        }
    }
}
