package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.ColorScheme
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.ThemeCollection
import cx.aswin.boxlore.core.designsystem.theme.findThemePreset
import cx.aswin.boxlore.core.designsystem.theme.isCustomThemeBrand
import cx.aswin.boxlore.core.designsystem.theme.resolveBoxLoreColorScheme
import cx.aswin.boxlore.core.prefs.ThemeSelection
import cx.aswin.boxlore.feature.settings.R

internal const val CUSTOM_THEME_KEY = "custom"
internal data class ThemeLookOption(val key: String, @StringRes val name: Int, @StringRes val description: Int, val collection: ThemeCollection = ThemeCollection.MINIMAL, val isPreset: Boolean = false)
internal data class ThemeLookSection(@StringRes val title: Int, val looks: List<ThemeLookOption>)
internal data class ThemePreviewColors(val light: ColorScheme, val dark: ColorScheme)

internal fun themeLookOptions(collection: ThemeCollection? = null): List<ThemeLookOption> = listOf(
    ThemeLookOption(SurfaceStyles.CLASSIC_DYNAMIC, R.string.settings_theme_look_classic, R.string.settings_theme_look_classic_description),
    ThemeLookOption(SurfaceStyles.DYNAMIC_OLED_WHITE, R.string.settings_theme_look_pure, R.string.settings_theme_look_pure_description),
    ThemeLookOption(SurfaceStyles.STANDARD, R.string.settings_theme_look_material, R.string.settings_theme_look_material_description),
    ThemeLookOption(CUSTOM_THEME_KEY, R.string.settings_theme_look_custom, R.string.settings_theme_look_custom_description),
).filter { collection == null || it.collection == collection }

/** Legacy choices retain their exact inputs and render as Custom; nothing is reset during upgrade. */
internal fun selectedThemeLookKey(surfaceStyle: String, themeBrand: String = "violet", wallpaperColors: Boolean = surfaceStyle == SurfaceStyles.STANDARD): String {
    if (surfaceStyle == SurfaceStyles.STANDARD && wallpaperColors) return SurfaceStyles.STANDARD
    val preset = findThemePreset(surfaceStyle) != null || findThemePreset(themeBrand) != null
    val customColor = isCustomThemeBrand(themeBrand) || themeBrand != "violet"
    if (preset || customColor || wallpaperColors) return CUSTOM_THEME_KEY
    return when (surfaceStyle) {
        SurfaceStyles.CLASSIC_DYNAMIC, SurfaceStyles.CLASSIC_DARK, SurfaceStyles.CLASSIC_LIGHT -> SurfaceStyles.CLASSIC_DYNAMIC
        SurfaceStyles.DYNAMIC_OLED_WHITE, SurfaceStyles.AMOLED, SurfaceStyles.PURE_WHITE -> SurfaceStyles.DYNAMIC_OLED_WHITE
        else -> CUSTOM_THEME_KEY
    }
}

internal fun AppearanceUiState.selectedLookKey(): String = selectedThemeLookKey(currentSurfaceStyle, currentThemeBrand, isDynamicColorEnabled)
internal fun AppearanceUiState.themeSelection(): ThemeSelection = ThemeSelection(currentThemeBrand, currentSurfaceStyle, isDynamicColorEnabled)
internal fun themeLookSections(): List<ThemeLookSection> = listOf(ThemeLookSection(R.string.settings_theme_section_title, themeLookOptions()))

internal fun selectThemeLook(look: ThemeLookOption, state: AppearanceUiState, actions: AppearanceActions) {
    if (look.key == state.selectedLookKey() || look.key == CUSTOM_THEME_KEY && state.savedCustomTheme == null) return
    val target = when (look.key) {
        CUSTOM_THEME_KEY -> state.savedCustomTheme ?: return
        SurfaceStyles.STANDARD -> ThemeSelection("violet", SurfaceStyles.STANDARD, true)
        else -> ThemeSelection("violet", look.key)
    }
    val remember = if (state.selectedLookKey() == CUSTOM_THEME_KEY) state.themeSelection() else null
    actions.onApplyTheme(target, remember)
}

internal fun themePreviewColors(context: Context, state: AppearanceUiState, look: ThemeLookOption, selected: Boolean): ThemePreviewColors {
    val selection = when {
        selected -> state.themeSelection()
        look.key == CUSTOM_THEME_KEY -> state.savedCustomTheme ?: state.themeSelection()
        else -> ThemeSelection("violet", look.key, look.key == SurfaceStyles.STANDARD)
    }
    fun scheme(dark: Boolean) = resolveBoxLoreColorScheme(context, dark, selection.wallpaperColors, selection.brand, selection.surfaceStyle)
    return ThemePreviewColors(scheme(false), scheme(true))
}
