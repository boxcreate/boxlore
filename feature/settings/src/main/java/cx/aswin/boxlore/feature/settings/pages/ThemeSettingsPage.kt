package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.ThemeCollection
import cx.aswin.boxlore.core.designsystem.theme.computeEffectiveDarkTheme
import cx.aswin.boxlore.core.designsystem.theme.findThemePreset
import cx.aswin.boxlore.core.designsystem.theme.resolveBoxLoreColorScheme
import cx.aswin.boxlore.core.designsystem.theme.resolveFixedThemeColorScheme
import cx.aswin.boxlore.feature.settings.components.SettingsActionRow
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsLazyScaffold
import cx.aswin.boxlore.feature.settings.components.ThemeLookCard

/** One page for mode, all complete looks and inline accent customization. */
@Composable
internal fun ThemeSettingsPage(state: AppearanceUiState, actions: AppearanceActions, onBack: () -> Unit) {
    var collectionKey by rememberSaveable { mutableStateOf("all") }
    var customizing by rememberSaveable { mutableStateOf(false) }
    val collection = ThemeCollection.entries.firstOrNull { it.name == collectionKey }
    val looks = remember(collection) { themeLookOptions(collection) }
    val systemDark = isSystemInDarkTheme()
    val modeDark = when (state.currentThemeConfig) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    val dark = computeEffectiveDarkTheme(state.currentSurfaceStyle, modeDark)
    val selectedKey = selectedThemeLookKey(state.currentSurfaceStyle)
    val selectedPreset = findThemePreset(selectedKey)
    val customColors = selectedPreset != null && (state.isDynamicColorEnabled || state.currentThemeBrand != selectedKey)
    val context = LocalContext.current
    val currentScheme = MaterialTheme.colorScheme

    SettingsLazyScaffold(
        title = "Theme",
        onBack = onBack,
        content = {
            item("mode") {
                ThemeModeSection(state.currentThemeConfig, state.currentSurfaceStyle, actions.onSetThemeConfig) { style ->
                    selectSurfaceStyle(style, actions.onSetSurfaceStyle, actions.onToggleDynamicColor, actions.onSetThemeBrand)
                }
            }
            item("accent") {
                ThemeAccentControls(state, actions, customizing, { customizing = !customizing })
            }
            item("collection") {
                ThemeCollectionHeader(state, dark, collectionKey) { collectionKey = it }
            }
            items(looks, key = { it.key }, contentType = { "theme_preview" }) { look ->
                val selected = selectedKey == look.key
                val authoredScheme = remember(look.key, dark, state.currentThemeBrand) {
                    if (look.isPreset) {
                        resolveFixedThemeColorScheme(look.key, dark, look.key)
                    } else {
                        resolveBoxLoreColorScheme(
                            context = context,
                            darkTheme = dark,
                            dynamicColor = look.key == SurfaceStyles.STANDARD,
                            themeBrand = if (look.key == SurfaceStyles.CLASSIC_DYNAMIC) "violet" else state.currentThemeBrand,
                            surfaceStyle = look.key,
                        )
                    }
                }
                ThemeLookCard(
                    look = look,
                    scheme = if (selected) currentScheme else authoredScheme,
                    selected = selected,
                    customColors = selected && customColors,
                    onSelect = {
                        if (look.isPreset) {
                            actions.onSetThemePreset(look.key)
                        } else {
                            selectSurfaceStyle(look.key, actions.onSetSurfaceStyle, actions.onToggleDynamicColor, actions.onSetThemeBrand)
                        }
                    },
                )
            }
        },
    )
}

@Composable
private fun ThemeAccentControls(state: AppearanceUiState, actions: AppearanceActions, expanded: Boolean, onToggle: () -> Unit) {
    val preset = findThemePreset(state.currentSurfaceStyle)
    val customized = preset != null && (state.isDynamicColorEnabled || state.currentThemeBrand != preset.key)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SettingsGroup {
            SettingsActionRow(
                title = "Personalize colors",
                supportingText = "${themeAccentSummary(state)} · Keeps your background",
                icon = Icons.Rounded.Palette,
                onClick = onToggle,
                trailing = {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = if (expanded) "Collapse accent controls" else "Expand accent controls",
                    )
                },
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (preset != null && customized) {
                    TextButton(onClick = { actions.onSetThemePreset(preset.key) }) { Text("Restore ${preset.name} colors") }
                }
                ColorsSection(state.isDynamicColorEnabled, actions.onToggleDynamicColor, state.currentThemeBrand, actions.onSetThemeBrand)
            }
        }
    }
}

@Composable
private fun ThemeCollectionHeader(state: AppearanceUiState, dark: Boolean, collectionKey: String, onFilter: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Choose your look", style = MaterialTheme.typography.titleLarge)
        Text(
            "Tap a preview to apply its background and colors. Changes are saved immediately.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val filters = listOf("all" to "All") + ThemeCollection.entries.map { it.name to it.label }
            filters.forEach { (key, label) ->
                FilterChip(selected = collectionKey == key, onClick = { onFilter(key) }, label = { Text(label) })
            }
        }
        Text(
            "${if (dark) "Dark" else "Light"} previews · ${themeSelectionSummary(state)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
