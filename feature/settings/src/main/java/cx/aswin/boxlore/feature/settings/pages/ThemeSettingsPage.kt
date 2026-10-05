package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.settings.components.SettingsLazyScaffold
import cx.aswin.boxlore.feature.settings.components.ThemeLookCard

/** Mode, compact two-mode theme choices, and colors immediately beneath the selected theme. */
@Composable
internal fun ThemeSettingsPage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit) {
    val sections = remember { themeLookSections() }
    val selectedKey = selectedThemeLookKey(state.currentSurfaceStyle)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current

    SettingsLazyScaffold(
        title = "Theme",
        onBack = onBack,
        content = {
            item("mode") {
                ThemeModeSection(state.currentThemeConfig, state.currentSurfaceStyle, actions.onSetThemeConfig) { style ->
                    selectSurfaceStyle(style, actions.onSetSurfaceStyle, actions.onToggleDynamicColor, actions.onSetThemeBrand)
                }
            }
            item("intro") {
                Text(
                    "Tap a theme to change the background and colors. Each preview shows its light and dark versions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sections.forEach { section ->
                item("section:${section.title}") { Text(section.title, style = MaterialTheme.typography.titleSmall) }
                items(section.looks, key = { it.key }, contentType = { "theme_choice" }) { look ->
                    val selected = selectedKey == look.key
                    val colors = remember(look.key, selected, state.currentThemeBrand, state.isDynamicColorEnabled, configuration) {
                        themePreviewColors(context, state, look, selected)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeLookCard(look, colors, selected) { selectThemeLook(look, state, actions) }
                        if (selected) {
                            ThemeColorsSection(state, actions, look)
                        }
                    }
                }
            }
        },
    )
}
