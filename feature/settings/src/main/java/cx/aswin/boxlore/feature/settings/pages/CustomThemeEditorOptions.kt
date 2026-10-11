package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.CustomThemeSeeds
import cx.aswin.boxlore.core.designsystem.theme.SurfaceStyles
import cx.aswin.boxlore.core.designsystem.theme.findThemePreset
import cx.aswin.boxlore.feature.settings.R

internal fun normalizeEditorHex(input: String): String = input.trim().removePrefix("#").uppercase(java.util.Locale.ROOT).take(8)

internal fun parseEditorHex(input: String): Color? = normalizeEditorHex(input)
    .takeIf { it.matches(Regex("[0-9A-F]{6}")) }?.toLongOrNull(16)?.let { Color(it or 0xFF000000) }

internal fun editorSurfaceStyle(surface: String): String {
    val style = automaticSurfaceStyle(surface)
    val supported = style in listOf(SurfaceStyles.STANDARD, SurfaceStyles.CLASSIC_DYNAMIC, SurfaceStyles.DYNAMIC_OLED_WHITE)
    return if (supported || findThemePreset(style) != null) style else SurfaceStyles.STANDARD
}

internal fun themeRoleColor(seeds: CustomThemeSeeds, colors: ColorScheme, role: Int): Color = when (role) {
    1 -> seeds.secondary ?: colors.secondary
    2 -> seeds.tertiary ?: colors.tertiary
    else -> seeds.primary
}

internal fun CustomThemeSeeds.withRoleColor(role: Int, color: Color?): CustomThemeSeeds = when (role) {
    1 -> copy(secondary = color)
    2 -> copy(tertiary = color)
    else -> copy(primary = color ?: primary)
}

@Composable
internal fun ThemeRoleSelector(labels: List<String>, seeds: CustomThemeSeeds, colors: ColorScheme, role: Int, onSelect: (Int) -> Unit) {
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        labels.forEachIndexed { index, label ->
            val selected = index == role
            Surface(
                modifier = Modifier.clip(MaterialTheme.shapes.extraLarge).selectable(selected, onClick = { onSelect(index) }, role = Role.RadioButton),
                shape = MaterialTheme.shapes.extraLarge,
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = if (selected) BorderStroke(1.dp, MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.22f)) else null,
            ) {
                Row(Modifier.defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = themeRoleColor(seeds, colors, index), shape = CircleShape, modifier = Modifier.size(16.dp)) {}
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
internal fun ThemeRoleExplanation(role: Int, seeds: CustomThemeSeeds, onSuggested: () -> Unit) {
    val description = stringResource(
        when (role) {
        1 -> R.string.theme_editor_secondary_description
        2 -> R.string.theme_editor_tertiary_description
        else -> R.string.theme_editor_primary_description
    }
    )
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (role != 0) {
            val suggested = if (role == 1) seeds.secondary == null else seeds.tertiary == null
            TextButton(onClick = onSuggested) {
                Icon(if (suggested) Icons.Rounded.Check else Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                Text(stringResource(if (suggested) R.string.theme_editor_suggested else R.string.theme_editor_use_suggested), modifier = Modifier.padding(start = 6.dp))
            }
            if (suggested) Text(stringResource(R.string.theme_editor_suggested_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
internal fun ThemeBackgroundOptions(surface: String, savedSurface: String, onSelect: (String) -> Unit) {
    val options = listOf(SurfaceStyles.STANDARD to R.string.theme_editor_tinted, SurfaceStyles.CLASSIC_DYNAMIC to R.string.theme_editor_neutral, SurfaceStyles.DYNAMIC_OLED_WHITE to R.string.theme_editor_pure)
        .let { if (findThemePreset(savedSurface) == null) it else it + (savedSurface to R.string.theme_editor_saved) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.theme_editor_background), style = MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            options.forEach { (key, label) ->
                FilterChip(
                    selected = surface == key,
                    onClick = { onSelect(key) },
                    label = { Text(stringResource(label)) },
                    modifier = Modifier.defaultMinSize(minHeight = 48.dp).semantics { role = Role.RadioButton },
                    leadingIcon = {
                        if (surface == key) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) else Box(Modifier.size(18.dp))
                    },
                )
            }
        }
        Text(
            stringResource(
                when (surface) {
            SurfaceStyles.STANDARD -> R.string.theme_editor_tinted_description
            SurfaceStyles.CLASSIC_DYNAMIC -> R.string.theme_editor_neutral_description
            SurfaceStyles.DYNAMIC_OLED_WHITE -> R.string.theme_editor_pure_description
            else -> R.string.theme_editor_saved_description
        }
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
