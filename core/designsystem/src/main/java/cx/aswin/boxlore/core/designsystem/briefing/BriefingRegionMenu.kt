package cx.aswin.boxlore.core.designsystem.briefing

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.components.BoxloreDropdownMenu
import cx.aswin.boxlore.core.designsystem.components.rememberBoxloreMenuState
import cx.aswin.boxlore.core.model.BriefingArtwork

/** Localizable edition labels use the same aliases as player and cover artwork. */
@StringRes
fun briefingRegionLabel(region: String): Int = when (BriefingArtwork.forRegion(region)) {
    BriefingArtwork.USA -> R.string.briefing_region_us
    BriefingArtwork.INDIA -> R.string.briefing_region_india
    BriefingArtwork.UK -> R.string.briefing_region_uk
    BriefingArtwork.GLOBAL -> R.string.briefing_region_global
}

private val BRIEFING_EDITIONS = listOf("global", "in", "us", "gb")

@Composable
fun BriefingRegionMenu(region: String, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    // Saved, so an open edition menu stays open when a fold or unfold recreates the screen.
    val menu = rememberBoxloreMenuState("brief_edition")
    val labels = BRIEFING_EDITIONS.associateWith { code -> stringResource(R.string.briefing_region_edition, stringResource(briefingRegionLabel(code))) }
    Box(modifier) {
        FilledTonalButton(
            onClick = menu::open,
            colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface),
        ) {
            Text(stringResource(briefingRegionLabel(region)))
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Rounded.ExpandMore, stringResource(R.string.briefing_choose_edition), Modifier.size(18.dp))
        }
        // The current edition is a filled row with a check ("uk" and "gb" are the same edition).
        BoxloreDropdownMenu(menu) {
            BRIEFING_EDITIONS.forEach { code ->
                selectableItem(labels.getValue(code), selected = BriefingArtwork.forRegion(code) == BriefingArtwork.forRegion(region), onClick = { onSelect(code) })
            }
        }
    }
}
