package cx.aswin.boxlore.feature.settings.pages.supportworlds

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/** Debug-build choice of Support world. Process-scoped so it survives leaving and reopening the page. */
internal object SupportWorldDebugOverride {
    @Volatile var world: SupportWorld? = null
}

/** Debug builds only: pick a world to preview, or go back to the live experiment assignment. */
@Composable
internal fun SupportWorldDebugMenu(
    current: SupportWorld,
    override: SupportWorld?,
    tint: Color,
    onChoose: (SupportWorld?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Rounded.Science, contentDescription = "Preview a Support world", tint = tint)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Use experiment") },
                trailingIcon = { if (override == null) Icon(Icons.Rounded.Check, contentDescription = null) },
                onClick = {
                    expanded = false
                    onChoose(null)
                },
            )
            SupportWorld.entries.forEach { world ->
                DropdownMenuItem(
                    text = { Text(world.variantKey.replaceFirstChar { it.uppercase() }) },
                    trailingIcon = {
                        if (override == world || (override == null && current == world)) {
                            Icon(Icons.Rounded.Check, contentDescription = null)
                        }
                    },
                    onClick = {
                        expanded = false
                        onChoose(world)
                    },
                )
            }
        }
    }
}
