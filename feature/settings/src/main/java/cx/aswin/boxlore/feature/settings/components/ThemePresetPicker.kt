package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ThemePreset
import cx.aswin.boxlore.core.designsystem.theme.ThemePresets
import cx.aswin.boxlore.core.designsystem.theme.resolveFixedThemeColorScheme
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemePresetPicker(
    selectedKey: String,
    hasCustomColors: Boolean,
    dark: Boolean,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var closing by remember { mutableStateOf(false) }
    fun closePicker() {
        if (closing) return
        closing = true
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }
    fun selectPreset(key: String) {
        if (closing) return
        onSelect(key)
        closePicker()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        ThemePresetPickerHeader(dark, ::closePicker)
        BoxWithConstraints {
            val columns = themePresetColumnCount(maxWidth.value, LocalDensity.current.fontScale)
            LazyColumn(
                modifier = Modifier.selectableGroup(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(ThemePresets.chunked(columns), key = { it.first().key }) { presets ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        presets.forEach { preset ->
                            ThemePresetCard(
                                preset = preset,
                                scheme = remember(preset.key, dark) {
                                    resolveFixedThemeColorScheme(preset.key, dark, preset.key)
                                },
                                selected = selectedKey == preset.key,
                                customColors = selectedKey == preset.key && hasCustomColors,
                                onSelect = { selectPreset(preset.key) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Keep labels readable at large font sizes and on narrow windows. */
internal fun themePresetColumnCount(widthDp: Float, fontScale: Float): Int =
    if (widthDp >= 360f && fontScale <= 1.3f) 2 else 1

@Composable
private fun ThemePresetPickerHeader(dark: Boolean, onDismiss: () -> Unit) {
    Column(modifier = Modifier.padding(start = 24.dp, end = 16.dp, bottom = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Ready-made themes",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Rounded.Close, contentDescription = "Close themes")
            }
        }
        Text(
            "Matching backgrounds and colors in one tap. Follows Theme above; you can personalise Colors below anytime.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            if (dark) "Dark previews" else "Light previews",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 8.dp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
internal fun ThemePresetColorsSummary(preset: ThemePreset) {
    SettingsContent {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = MaterialTheme.shapes.medium) {
                ThemePresetPreview(MaterialTheme.colorScheme, Modifier.size(80.dp, 60.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("${preset.name} colors", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Matched palette. Pick an accent below to personalise it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    SettingsDivider()
}

@Composable
private fun ThemePresetCard(
    preset: ThemePreset,
    scheme: ColorScheme,
    selected: Boolean,
    customColors: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = modifier
            .clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) {
                if (customColors) stateDescription = "Selected background, personalised colors"
            },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Surface(shape = MaterialTheme.shapes.medium, color = scheme.background) {
                ThemePresetPreview(scheme, Modifier.fillMaxWidth().height(86.dp))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(preset.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(
                        Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
            Text(
                preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (customColors) {
                Text("Personalised colors", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

/** A small schematic of the background, elevated cards and all three accent families. */
@Composable
internal fun ThemePresetPreview(scheme: ColorScheme, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val inset = 12.dp.toPx()
        val gap = 6.dp.toPx()
        val radius = CornerRadius(6.dp.toPx())
        drawRect(scheme.background)
        drawRoundRect(
            scheme.onSurface,
            topLeft = Offset(inset, inset),
            size = Size((size.width - inset * 2) * 0.42f, 4.dp.toPx()),
            cornerRadius = radius,
        )
        drawRoundRect(
            scheme.onSurfaceVariant,
            topLeft = Offset(inset, inset + 9.dp.toPx()),
            size = Size((size.width - inset * 2) * 0.6f, 3.dp.toPx()),
            cornerRadius = radius,
        )
        val cardWidth = (size.width - inset * 2 - gap * 2) / 3
        listOf(scheme.primary, scheme.secondary, scheme.tertiary).forEachIndexed { index, color ->
            val left = inset + index * (cardWidth + gap)
            val top = 35.dp.toPx()
            val cardHeight = size.height - top - inset
            drawRoundRect(
                scheme.surfaceContainerHigh,
                topLeft = Offset(left, top),
                size = Size(cardWidth, cardHeight),
                cornerRadius = radius,
            )
            drawCircle(color, radius = 5.dp.toPx(), center = Offset(left + cardWidth / 2, top + cardHeight / 2))
        }
    }
}
