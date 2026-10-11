package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.toThemeBrandHex
import cx.aswin.boxlore.feature.settings.R
import kotlin.math.roundToInt

@Composable
internal fun ThemeColorControls(label: String, color: Color, hex: String, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onChange: (Color) -> Unit, onHexChange: (String) -> Unit) {
    val initial = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    var hue by rememberSaveable(label) { mutableFloatStateOf(initial[0]) }
    var saturation by rememberSaveable(label) { mutableFloatStateOf(initial[1]) }
    var brightness by rememberSaveable(label) { mutableFloatStateOf(initial[2]) }
    LaunchedEffect(color, label) {
        val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
        if (hsv[1] > 0f && hsv[2] > 0f) hue = hsv[0]
        saturation = hsv[1]
        brightness = hsv[2]
    }
    fun update() = onChange(hsvColor(hue, saturation, brightness))
    val valid = parseEditorHex(hex) != null
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeColorSwatches(label, color, onChange)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                ThemeColorSlider(label, R.string.theme_editor_hue, hue, 0f..360f, listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f).map { hsvColor(it, 1f, 1f) }) {
                    hue = it
                    update()
                }
                ThemeColorSlider(label, R.string.theme_editor_saturation, saturation, 0f..1f, listOf(hsvColor(hue, 0f, brightness), hsvColor(hue, 1f, brightness))) {
                    saturation = it
                    update()
                }
                val stateLabel = stringResource(if (expanded) R.string.theme_editor_expanded else R.string.theme_editor_collapsed)
                Surface(
                    onClick = { onExpandedChange(!expanded) },
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth().testTag("theme-editor-fine-tune").semantics { stateDescription = stateLabel },
                ) {
                    Row(Modifier.defaultMinSize(minHeight = 56.dp).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.theme_editor_fine_tune), style = MaterialTheme.typography.titleSmall)
                            Text(stringResource(if (valid) R.string.theme_editor_fine_tune_hint else R.string.theme_editor_hex_error), style = MaterialTheme.typography.bodySmall, color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        }
                        if (LocalDensity.current.fontScale <= 1.3f) Text("#$hex", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(20.dp))
                    }
                }
                if (expanded) {
                    ThemeColorSlider(label, R.string.theme_editor_brightness, brightness, 0f..1f, listOf(Color.Black, hsvColor(hue, saturation, 1f))) {
                        brightness = it
                        update()
                    }
                    ThemeHexField(hex, onHexChange)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeColorSlider(role: String, labelId: Int, value: Float, range: ClosedFloatingPointRange<Float>, gradient: List<Color>, onChange: (Float) -> Unit) {
    val label = stringResource(labelId)
    val description = stringResource(R.string.theme_editor_role_control, role, label.lowercase(java.util.Locale.getDefault()))
    val valueLabel = stringResource(if (labelId == R.string.theme_editor_hue) R.string.theme_editor_degrees else R.string.theme_editor_percent, (if (labelId == R.string.theme_editor_hue) value else value * 100).roundToInt())
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(valueLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp).semantics {
                contentDescription = description
                stateDescription = valueLabel
            },
            track = {
                Box(
                    Modifier.fillMaxWidth().height(8.dp).drawBehind {
                    val colors = if (layoutDirection == LayoutDirection.Rtl) gradient.reversed() else gradient
                    drawRoundRect(brush = Brush.horizontalGradient(colors), cornerRadius = CornerRadius(size.height / 2))
                }
                )
            },
        )
    }
}

@Composable
private fun ThemeHexField(hex: String, onHexChange: (String) -> Unit) {
    val focus = LocalFocusManager.current
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    OutlinedTextField(
        value = hex, onValueChange = onHexChange, isError = parseEditorHex(hex) == null,
        label = { Text(stringResource(R.string.theme_editor_hex)) }, prefix = { Text("#") }, singleLine = true,
        supportingText = { if (parseEditorHex(hex) == null) Text(stringResource(R.string.theme_editor_hex_error)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { focus.clearFocus() }),
        modifier = Modifier.fillMaxWidth().testTag("theme-editor-hex"),
    )
    }
}

@Composable
private fun ThemeColorSwatches(role: String, selectedColor: Color, onChange: (Color) -> Unit) {
    val swatches = listOf(0xFF6255E8, 0xFF287BDE, 0xFF008C86, 0xFF527A35, 0xFFC46A25, 0xFFBD466F, 0xFF756C82)
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(0.dp)) {
        items(swatches) { value ->
            val color = Color(value)
            val description = stringResource(R.string.theme_editor_use_swatch, color.toThemeBrandHex(), role)
            Surface(
                onClick = { onChange(color) },
                color = color,
                shape = CircleShape,
                modifier = Modifier.size(48.dp).semantics {
                    contentDescription = description
                    selected = color.toArgb() == selectedColor.toArgb()
                },
            ) {
                if (color.toArgb() == selectedColor.toArgb()) {
                    Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, Modifier.size(22.dp), tint = if (color.luminance() > 0.179f) Color.Black else Color.White)
                }
                }
            }
        }
    }
}

private fun hsvColor(hue: Float, saturation: Float, brightness: Float): Color = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
