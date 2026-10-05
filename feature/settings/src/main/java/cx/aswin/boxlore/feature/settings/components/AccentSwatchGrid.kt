package cx.aswin.boxlore.feature.settings.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.contrastColor

/** Adaptive accent grid with full touch targets and spoken color names. */
internal fun accentSwatchColumnCount(widthDp: Float, maximumColumns: Int = 5): Int =
    if (!widthDp.isFinite() || widthDp <= 0f) 1 else (widthDp / 56f).toInt().coerceIn(1, maximumColumns.coerceAtLeast(1))

@Composable
internal fun AccentSwatchGrid(
    seeds: List<Triple<String, String, Color>>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    columns: Int = 5,
) {
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp)) {
        val count = accentSwatchColumnCount(maxWidth.value, columns)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.selectableGroup()) {
            seeds.chunked(count).forEach { rowSeeds ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    rowSeeds.forEach { (key, name, color) ->
                        AccentSwatch(color, name, key == selectedKey) { onSelect(key) }
                    }
                    repeat(count - rowSeeds.size) { Spacer(Modifier.size(52.dp)) }
                }
            }
        }
    }
}

@Composable
private fun AccentSwatch(color: Color, name: String, selected: Boolean, onClick: () -> Unit) {
    val ringColor = MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier.size(52.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics {
                contentDescription = name
                stateDescription = if (selected) "Selected" else "Not selected"
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.size(if (selected) 44.dp else 48.dp),
            shape = androidx.compose.foundation.shape.CircleShape,
            color = color,
            contentColor = color.contrastColor(),
            border = if (selected) BorderStroke(3.dp, ringColor) else null,
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(22.dp))
            }
        }
    }
}
