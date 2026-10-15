package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.prefs.WidgetAppearance
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsPictureGrid
import cx.aswin.boxlore.feature.settings.components.SettingsPictureTile
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow
import cx.aswin.boxlore.feature.settings.components.SettingsTileSection
import cx.aswin.boxlore.feature.settings.components.ThemeLookPicture

/**
 * Theme & colors: the four looks as picture tiles (each in light and dark), the custom theme
 * editor, artwork colors for episode and podcast pages, and the widgets, which follow these colors.
 */
@Composable
internal fun AppearanceThemePage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit, onEditCustomTheme: () -> Unit) {
    val selected = state.selectedLookKey()
    SettingsScaffold(title = "Theme & colors", onBack = onBack) {
        ThemeLookTiles(state, actions, onEditCustomTheme)
        SettingsGroup {
            SettingsNavigationRow(
                title = if (selected == CUSTOM_THEME_KEY) "Edit custom theme" else "Create custom theme",
                supportingText = "Choose colors with a light and dark preview",
                icon = Icons.Rounded.Palette,
                onClick = onEditCustomTheme,
            )
            SettingsDivider()
            SettingsSwitchRow(
                title = "Use artwork colors",
                supportingText = "Match episode and podcast pages to their artwork",
                checked = state.artworkColorsEnabled,
                onCheckedChange = actions.onSetArtworkColorsEnabled,
                icon = Icons.Rounded.AutoAwesome,
            )
        }
        SettingsGroup(
            title = "Widgets",
            footer = "App theme matches your chosen colors and mode. System follows your phone’s mode and wallpaper colors.",
        ) {
            SettingsContent {
                ConnectedOptionSelector(
                    options = listOf(WidgetAppearance.APP to "App theme", WidgetAppearance.SYSTEM to "System"),
                    selected = WidgetAppearance.sanitize(state.currentWidgetAppearance),
                    onSelect = actions.onSetWidgetAppearance,
                )
            }
        }
    }
}

@Composable
private fun ThemeLookTiles(state: AppearanceUiState, actions: AppearanceActions, onEdit: () -> Unit) {
    val looks = remember { themeLookOptions() }
    val selected = state.selectedLookKey()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    SettingsTileSection(title = "Colors") {
        SettingsPictureGrid(looks, columns = 2) { look, modifier ->
            val colors = remember(look.key, state.currentThemeBrand, state.currentSurfaceStyle, state.isDynamicColorEnabled, state.savedCustomTheme, configuration, selected) {
                themePreviewColors(context, state, look, selected == look.key)
            }
            SettingsPictureTile(
                label = look.name,
                selected = selected == look.key,
                description = look.description,
                onClick = {
                    // Custom without a saved theme, or tapped again, opens the editor.
                    if (look.key == CUSTOM_THEME_KEY && (selected == CUSTOM_THEME_KEY || state.savedCustomTheme == null)) onEdit() else selectThemeLook(look, state, actions)
                },
                modifier = modifier,
            ) {
                ThemeLookPicture(colors)
            }
        }
    }
}

internal fun automaticSurfaceStyle(style: String): String = when (style) {
    "classic_dark", "classic_light" -> "classic_dynamic"
    "amoled", "purewhite" -> "dynamic_oled_white"
    else -> style
}

internal fun appearanceDisplayMode(state: AppearanceUiState): String = when (state.currentSurfaceStyle) {
    "classic_dark", "amoled" -> "dark"
    "classic_light", "purewhite" -> "light"
    else -> state.currentThemeConfig
}
