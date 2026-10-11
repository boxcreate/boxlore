package cx.aswin.boxlore.core.designsystem.briefing

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.model.BriefingArtwork

/** Localizable edition labels use the same aliases as player and cover artwork. */
@StringRes
fun briefingRegionLabel(region: String): Int = when (BriefingArtwork.forRegion(region)) {
    BriefingArtwork.USA -> R.string.briefing_region_us
    BriefingArtwork.INDIA -> R.string.briefing_region_india
    BriefingArtwork.UK -> R.string.briefing_region_uk
    BriefingArtwork.GLOBAL -> R.string.briefing_region_global
}

@Composable
fun BriefingRegionMenu(region: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        FilledTonalButton(
            onClick = { expanded = true },
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(stringResource(briefingRegionLabel(region)))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ExpandMore, stringResource(R.string.briefing_choose_edition), Modifier.size(18.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("global", "in", "us", "gb").forEach { code ->
                DropdownMenuItem(text = { Text(stringResource(R.string.briefing_region_edition, stringResource(briefingRegionLabel(code)))) }, onClick = {
                    expanded = false
                    onSelect(code)
                })
            }
        }
    }
}
