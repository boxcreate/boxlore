package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.ConnectedOptionSelector
import cx.aswin.boxlore.core.designsystem.theme.BrandSeeds
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.isCustomThemeBrand
import cx.aswin.boxlore.core.designsystem.theme.isExactThemeBrand
import cx.aswin.boxlore.core.designsystem.theme.resolveThemeSeedColor
import cx.aswin.boxlore.feature.settings.components.AccentSwatchGrid
import cx.aswin.boxlore.feature.settings.components.InlineAccentPicker
import cx.aswin.boxlore.feature.settings.components.SettingsChoiceRow
import cx.aswin.boxlore.feature.settings.components.SettingsContent
import cx.aswin.boxlore.feature.settings.components.SettingsDivider
import cx.aswin.boxlore.feature.settings.components.SettingsGroup
import cx.aswin.boxlore.feature.settings.components.SettingsSwitchRow

@Composable
internal fun ThemeModeSection(
    currentThemeConfig: String,
    currentSurfaceStyle: String,
    onSetThemeConfig: (String) -> Unit,
    onUnlockToAutomatic: (String) -> Unit,
) {
    val modeLock = themeModeLockFor(currentSurfaceStyle)
    val selectedMode = modeLock?.mode ?: ThemeMode.fromKey(currentThemeConfig) ?: ThemeMode.SYSTEM

    SettingsGroup(
        title = "Display mode",
        footer =
        modeLock?.let {
            "Choose a different display mode to change this saved setting."
        } ?: "System follows your phone’s light or dark setting.",
    ) {
        SettingsContent {
            if (modeLock != null) {
                ForcedModeBadge(modeLock.mode)
            }
            ConnectedOptionSelector(
                options = ThemeMode.entries.map { it.key to it.label },
                selected = selectedMode.key,
                onSelect = { key ->
                    val mode = ThemeMode.fromKey(key) ?: return@ConnectedOptionSelector
                    if (modeLock != null && mode != modeLock.mode) {
                        onUnlockToAutomatic(modeLock.automaticSiblingStyle)
                    }
                    onSetThemeConfig(mode.key)
                },
            )
        }
    }
}

@Composable
private fun ForcedModeBadge(mode: ThemeMode) {
    Surface(
        modifier = Modifier.padding(bottom = 12.dp),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Always ${mode.label.lowercase()}",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = GoogleSansWeight.semiBold,
            )
        }
    }
}

@Composable
internal fun ThemeColorsSection(state: AppearanceUiState, actions: AppearanceActions, look: ThemeLookOption) {
    var editingCustomColor by remember { mutableStateOf(false) }
    SettingsGroup(
        title = "Colors for ${look.name}",
        footer = if (look.isPreset) "Choosing a different theme applies that theme's colors." else null,
    ) {
        SettingsContent {
            Text(themeColorExplanation(look), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (look.isPreset && (state.isDynamicColorEnabled || state.currentThemeBrand != look.key)) {
                TextButton(onClick = { actions.onSetThemePreset(look.key) }) { Text("Use ${look.name}'s original colors") }
            }
        }
        SettingsSwitchRow(
            title = "Use wallpaper colors",
            supportingText = "Automatically choose colors from your phone's wallpaper. Turn off to choose your own color.",
            checked = state.isDynamicColorEnabled,
            onCheckedChange = actions.onToggleDynamicColor,
        )
        if (!state.isDynamicColorEnabled) {
            FixedThemeColors(state, actions) { editingCustomColor = !editingCustomColor }
        }
    }
    if (editingCustomColor && !state.isDynamicColorEnabled) {
        SettingsGroup {
            SettingsContent {
                InlineAccentPicker(
                    initialColor = resolveThemeSeedColor(state.currentThemeBrand),
                    initialExact = isExactThemeBrand(state.currentThemeBrand),
                    onConfirm = { hex ->
                        actions.onSetThemeBrand(hex)
                        editingCustomColor = false
                    },
                    onDismiss = { editingCustomColor = false },
                )
            }
        }
    }
}

@Composable
private fun FixedThemeColors(state: AppearanceUiState, actions: AppearanceActions, onCustomColor: () -> Unit) {
    val seeds = remember { BrandSeeds.map { (key, brand) -> Triple(key, brand.first, brand.second) } }
    SettingsDivider()
    SettingsContent {
        Text("Current colors: ${themeAccentSummary(state)}", style = MaterialTheme.typography.labelLarge)
        Text("Tap a color to use it for buttons, icons and highlights.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AccentSwatchGrid(seeds = seeds, selectedKey = state.currentThemeBrand, onSelect = actions.onSetThemeBrand)
    SettingsDivider()
    SettingsChoiceRow(
        title = "Custom color",
        supportingText = "Adjust a color below, then tap Use color to apply it.",
        selected = isCustomThemeBrand(state.currentThemeBrand),
        onClick = onCustomColor,
    )
}

internal fun selectSurfaceStyle(
    style: String,
    onSetSurfaceStyle: (String) -> Unit,
    onToggleDynamicColor: (Boolean) -> Unit,
    onSetThemeBrand: (String) -> Unit,
) {
    onSetSurfaceStyle(style)
    when (style) {
        SurfaceStyles.CLASSIC_DYNAMIC -> {
            onToggleDynamicColor(false)
            onSetThemeBrand(DEFAULT_BRAND)
        }

        SurfaceStyles.STANDARD -> {
            onToggleDynamicColor(true)
        }

        SurfaceStyles.DYNAMIC_OLED_WHITE -> {
            onToggleDynamicColor(false)
        }
    }
}

/**
 * Legacy locked styles still resolve in the theme engine. Changing Theme unlocks them
 * to Soft/Pure automatic so Theme can drive light/dark again.
 */
private fun themeModeLockFor(surfaceStyle: String): ThemeModeLock? = when (surfaceStyle) {
    SurfaceStyles.AMOLED ->
        ThemeModeLock(
            mode = ThemeMode.DARK,
            automaticSiblingStyle = SurfaceStyles.DYNAMIC_OLED_WHITE,
        )
    SurfaceStyles.PURE_WHITE ->
        ThemeModeLock(
            mode = ThemeMode.LIGHT,
            automaticSiblingStyle = SurfaceStyles.DYNAMIC_OLED_WHITE,
        )
    SurfaceStyles.CLASSIC_DARK ->
        ThemeModeLock(
            mode = ThemeMode.DARK,
            automaticSiblingStyle = SurfaceStyles.CLASSIC_DYNAMIC,
        )
    SurfaceStyles.CLASSIC_LIGHT ->
        ThemeModeLock(
            mode = ThemeMode.LIGHT,
            automaticSiblingStyle = SurfaceStyles.CLASSIC_DYNAMIC,
        )
    else -> null
}

private data class ThemeModeLock(
    val mode: ThemeMode,
    val automaticSiblingStyle: String,
)

private enum class ThemeMode(
    val key: String,
    val label: String,
) {
    SYSTEM("system", "System"),
    LIGHT("light", "Light"),
    DARK("dark", "Dark"),
    ;

    companion object {
        fun fromKey(key: String): ThemeMode? = entries.firstOrNull { it.key == key }
    }
}

private const val DEFAULT_BRAND = "violet"
