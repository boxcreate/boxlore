package cx.aswin.boxlore.feature.settings.pages

import android.content.Context
import androidx.compose.material3.ColorScheme
import cx.aswin.boxlore.core.designsystem.theme.BrandSeeds
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.ThemeCollection
import cx.aswin.boxlore.core.designsystem.theme.ThemePresets
import cx.aswin.boxlore.core.designsystem.theme.findThemePreset
import cx.aswin.boxlore.core.designsystem.theme.isCustomThemeBrand
import cx.aswin.boxlore.core.designsystem.theme.resolveBoxLoreColorScheme
import cx.aswin.boxlore.core.designsystem.theme.resolveFixedThemeColorScheme

internal data class ThemeLookOption(
    val key: String,
    val name: String,
    val description: String,
    val collection: ThemeCollection,
    val isPreset: Boolean = false,
)

internal fun themeLookOptions(collection: ThemeCollection? = null): List<ThemeLookOption> {
    val essentials = listOf(
        ThemeLookOption(SurfaceStyles.CLASSIC_DYNAMIC, "boxlore classic", "Cream or charcoal backgrounds with boxlore colors.", ThemeCollection.MINIMAL),
        ThemeLookOption(SurfaceStyles.DYNAMIC_OLED_WHITE, "Pure", "White in light mode. True black in dark mode.", ThemeCollection.MINIMAL),
        ThemeLookOption(SurfaceStyles.STANDARD, "Material 3", "Choosing this automatically uses colors from your wallpaper.", ThemeCollection.COLORFUL),
    )
    val presets = ThemePresets.map { ThemeLookOption(it.key, it.name, it.description, it.collection, isPreset = true) }
    return (essentials + presets).filter { collection == null || it.collection == collection }
}

internal fun selectedThemeLookKey(surfaceStyle: String): String = when (surfaceStyle) {
    SurfaceStyles.CLASSIC_LIGHT, SurfaceStyles.CLASSIC_DARK -> SurfaceStyles.CLASSIC_DYNAMIC
    SurfaceStyles.AMOLED, SurfaceStyles.PURE_WHITE -> SurfaceStyles.DYNAMIC_OLED_WHITE
    else -> if (themeLookOptions().any { it.key == surfaceStyle }) surfaceStyle else SurfaceStyles.STANDARD
}

internal fun themeSelectionSummary(state: AppearanceUiState): String {
    val look = themeLookOptions().first { it.key == selectedThemeLookKey(state.currentSurfaceStyle) }
    val mode = when (state.currentThemeConfig) {
        "light" -> "Light"
        "dark" -> "Dark"
        else -> "System"
    }
    val colors = themeAccentSummary(state)
    return "${look.name} · $mode · $colors"
}

internal fun themeAccentSummary(state: AppearanceUiState): String {
    val preset = findThemePreset(state.currentThemeBrand)
    return when {
        state.isDynamicColorEnabled -> "Wallpaper colors"
        preset != null -> "${preset.name} colors"
        isCustomThemeBrand(state.currentThemeBrand) -> "Custom color"
        else -> BrandSeeds[state.currentThemeBrand]?.first ?: "Violet"
    }
}

internal data class ThemeLookSection(val title: String, val looks: List<ThemeLookOption>)
internal data class ThemePreviewColors(val light: ColorScheme, val dark: ColorScheme)

internal fun themeLookSections(): List<ThemeLookSection> {
    val looks = themeLookOptions()
    return listOf(ThemeLookSection("Default themes", looks.filter { !it.isPreset })) +
        ThemeCollection.entries.map { collection ->
            ThemeLookSection(collection.label, looks.filter { it.isPreset && it.collection == collection })
        }
}

/** Tapping the active theme is harmless; color restoration is an explicit separate action. */
internal fun selectThemeLook(look: ThemeLookOption, state: AppearanceUiState, actions: AppearanceActions) {
    if (selectedThemeLookKey(state.currentSurfaceStyle) == look.key) return
    if (look.isPreset) {
        actions.onSetThemePreset(look.key)
    } else {
        selectSurfaceStyle(look.key, actions.onSetSurfaceStyle, actions.onToggleDynamicColor, actions.onSetThemeBrand)
    }
}

/** Selected previews show customization in both modes; other choices show what selecting them applies. */
internal fun themePreviewColors(context: Context, state: AppearanceUiState, look: ThemeLookOption, selected: Boolean): ThemePreviewColors {
    fun scheme(dark: Boolean): ColorScheme = when {
        selected -> resolveBoxLoreColorScheme(context, dark, state.isDynamicColorEnabled, state.currentThemeBrand, look.key)
        look.isPreset -> resolveFixedThemeColorScheme(look.key, dark, look.key)
        else -> resolveBoxLoreColorScheme(
            context,
            dark,
            look.key == SurfaceStyles.STANDARD,
            if (look.key == SurfaceStyles.CLASSIC_DYNAMIC) "violet" else state.currentThemeBrand,
            look.key,
        )
    }
    return ThemePreviewColors(scheme(false), scheme(true))
}

internal fun themeColorExplanation(look: ThemeLookOption): String =
    if (look.key == SurfaceStyles.STANDARD) {
        "These colors are used for buttons, icons and highlights, and also tint the background and cards."
    } else {
        "These colors are used for buttons, icons and highlights. ${look.name}'s background stays the same."
    }
