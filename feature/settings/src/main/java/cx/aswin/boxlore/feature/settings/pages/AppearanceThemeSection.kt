package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsNavigationRow
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow
import cx.aswin.boxlore.feature.settings.components.ThemeLookCard

@Composable
internal fun AppearanceThemeSection(state: AppearanceUiState, actions: AppearanceActions, onEdit: () -> Unit) {
    val selected = state.selectedLookKey()
    SettingsGroup(title = "Theme & colors") {
        SettingsContent {
            Text("Display mode", style = MaterialTheme.typography.labelLarge)
            ConnectedOptionSelector(listOf("system" to "System", "light" to "Light", "dark" to "Dark"), appearanceDisplayMode(state), onSelect = { mode ->
                val unlocked = automaticSurfaceStyle(state.currentSurfaceStyle)
                if (unlocked != state.currentSurfaceStyle) actions.onApplyTheme(state.themeSelection().copy(surfaceStyle = unlocked), null)
                actions.onSetThemeConfig(mode)
            })
        }
        SettingsContent {
            ThemeChoiceRow(state, actions, onEdit)
        }
        SettingsNavigationRow(title = if (selected == CUSTOM_THEME_KEY) "Edit custom theme" else "Create custom theme", supportingText = "Choose colors with a light and dark preview", icon = Icons.Rounded.Palette, onClick = onEdit)
        SettingsDivider()
        SettingsSwitchRow(title = "Use artwork colors", supportingText = "Match episode and podcast pages to their artwork", checked = state.artworkColorsEnabled, onCheckedChange = actions.onSetArtworkColorsEnabled, icon = Icons.Rounded.AutoAwesome)
    }
}

internal fun automaticSurfaceStyle(style: String): String = when (style) {
    "classic_dark", "classic_light" -> "classic_dynamic"
    "amoled", "purewhite" -> "dynamic_oled_white"
    else -> style
}

@Composable
private fun ThemeChoiceRow(state: AppearanceUiState, actions: AppearanceActions, onEdit: () -> Unit) {
    val looks = remember { themeLookOptions() }
    val selected = state.selectedLookKey()
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val scroll = rememberLazyListState(initialFirstVisibleItemIndex = looks.indexOfFirst { it.key == selected }.coerceAtLeast(0))
    BoxWithConstraints {
        val cardWidth = (maxWidth * 0.88f).coerceAtMost(320.dp)
        LazyRow(state = scroll, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
            items(looks, key = { it.key }) { look ->
                val colors = remember(look.key, state.currentThemeBrand, state.currentSurfaceStyle, state.isDynamicColorEnabled, state.savedCustomTheme, configuration) {
                    themePreviewColors(context, state, look, selected == look.key)
                }
                ThemeLookCard(look, colors, selected == look.key, onSelect = {
                    if (look.key == CUSTOM_THEME_KEY && (selected == CUSTOM_THEME_KEY || state.savedCustomTheme == null)) onEdit() else selectThemeLook(look, state, actions)
                }, modifier = Modifier.width(cardWidth))
            }
        }
    }
}

internal fun appearanceDisplayMode(state: AppearanceUiState): String = when (state.currentSurfaceStyle) {
    "classic_dark", "amoled" -> "dark"
    "classic_light", "purewhite" -> "light"
    else -> state.currentThemeConfig
}
