package cx.aswin.boxlore.feature.settings.pages

import cx.aswin.boxlore.core.designsystem.theme.BrandSeeds
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.ThemeCollection
import cx.aswin.boxlore.core.designsystem.theme.ThemePresets
import cx.aswin.boxlore.core.designsystem.theme.findThemePreset
import cx.aswin.boxlore.core.designsystem.theme.isCustomThemeBrand

internal data class ThemeLookOption(
    val key: String,
    val name: String,
    val description: String,
    val collection: ThemeCollection,
    val isPreset: Boolean = false,
)

internal fun themeLookOptions(collection: ThemeCollection? = null): List<ThemeLookOption> {
    val essentials = listOf(
        ThemeLookOption(SurfaceStyles.CLASSIC_DYNAMIC, "boxlore classic", "Warm cream by day. Gentle charcoal by night.", ThemeCollection.MINIMAL),
        ThemeLookOption(SurfaceStyles.DYNAMIC_OLED_WHITE, "Pure", "Pure white by day. True black by night.", ThemeCollection.MINIMAL),
        ThemeLookOption(SurfaceStyles.STANDARD, "Material 3", "Soft, tonal surfaces with wallpaper colors.", ThemeCollection.COLORFUL),
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
        isCustomThemeBrand(state.currentThemeBrand) -> "Custom accent"
        else -> "${BrandSeeds[state.currentThemeBrand]?.first ?: "Violet"} accent"
    }
}
