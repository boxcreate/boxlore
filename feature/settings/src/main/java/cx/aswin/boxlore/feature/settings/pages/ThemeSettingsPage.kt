package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.settings.components.SettingsLazyScaffold
import cx.aswin.boxlore.feature.settings.components.ThemeLookCard

/** Horizontally browsable theme collections with full-width colors beneath the active collection. */
@Composable
internal fun ThemeSettingsPage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit) {
    val sections = remember { themeLookSections() }
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
                    "Swipe through themes and tap one to apply it. Each preview shows light and dark.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            sections.forEach { section ->
                item("section:${section.title}") {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(section.title, style = MaterialTheme.typography.titleSmall)
                        ThemePreviewRow(section, state, actions)
                        section.looks.firstOrNull { it.key == selectedThemeLookKey(state.currentSurfaceStyle) }?.let { look ->
                            ThemeColorsSection(state, actions, look)
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun ThemePreviewRow(section: ThemeLookSection, state: AppearanceUiState, actions: AppearanceActions) {
    val selectedKey = selectedThemeLookKey(state.currentSurfaceStyle)
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val rowState = rememberLazyListState(initialFirstVisibleItemIndex = section.looks.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0))
    BoxWithConstraints {
        val cardWidth = (maxWidth * 0.86f).coerceAtMost(340.dp)
        LazyRow(state = rowState, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.selectableGroup()) {
            items(section.looks, key = { it.key }) { look ->
                val selected = selectedKey == look.key
                val colors = remember(look.key, selected, state.currentThemeBrand, state.isDynamicColorEnabled, configuration) {
                    themePreviewColors(context, state, look, selected)
                }
                ThemeLookCard(look, colors, selected, onSelect = { selectThemeLook(look, state, actions) }, modifier = Modifier.width(cardWidth))
            }
        }
    }
}
