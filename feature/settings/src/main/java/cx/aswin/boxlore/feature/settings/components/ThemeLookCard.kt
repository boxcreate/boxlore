package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.settings.pages.ThemeLookOption
import cx.aswin.boxlore.feature.settings.pages.ThemePreviewColors

/** A compact choice with both modes visible. A selected choice never resets customized colors. */
@Composable
internal fun ThemeLookCard(
    look: ThemeLookOption,
    colors: ThemePreviewColors,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Surface(
        modifier = Modifier.fillMaxWidth().clip(shape)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .semantics(mergeDescendants = true) { stateDescription = if (selected) "Selected" else "Not selected" },
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(look.name, style = MaterialTheme.typography.titleMedium, fontWeight = GoogleSansWeight.semiBold, modifier = Modifier.weight(1f))
                if (selected) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicThemePreview("Light", colors.light, Modifier.weight(1f))
                BasicThemePreview("Dark", colors.dark, Modifier.weight(1f))
            }
            Text(look.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Background, one card and one button: a simple color comparison, not a miniature screen. */
@Composable
private fun BasicThemePreview(label: String, scheme: ColorScheme, modifier: Modifier) {
    Column(modifier.clip(MaterialTheme.shapes.medium).background(scheme.background)) {
        Text(
            label,
            modifier = Modifier.padding(start = 10.dp, top = 8.dp),
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        Canvas(Modifier.fillMaxWidth().height(64.dp)) {
            val inset = 10.dp.toPx()
            val width = (size.width - 2 * inset).coerceAtLeast(1f)
            val radius = CornerRadius(7.dp.toPx())
            drawRoundRect(scheme.surfaceContainerHigh, Offset(inset, 6.dp.toPx()), Size(width, 42.dp.toPx()), radius)
            drawRoundRect(scheme.onSurface, Offset(inset + 8.dp.toPx(), 14.dp.toPx()), Size(width * 0.45f, 3.dp.toPx()), radius)
            drawRoundRect(scheme.onSurfaceVariant, Offset(inset + 8.dp.toPx(), 22.dp.toPx()), Size(width * 0.62f, 2.dp.toPx()), radius)
            drawRoundRect(scheme.primary, Offset(inset + 8.dp.toPx(), 32.dp.toPx()), Size(width * 0.32f, 8.dp.toPx()), radius)
        }
    }
}
