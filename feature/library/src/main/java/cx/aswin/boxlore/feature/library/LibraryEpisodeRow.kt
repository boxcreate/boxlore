package cx.aswin.boxlore.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

/** A row in select mode: it ticks as a whole and says whether it is selected. */
internal data class LibraryRowSelection(val selected: Boolean, val onSelectedChange: (Boolean) -> Unit)

/**
 * One episode row across Library (New Episodes, Liked, a show's downloads): 72dp artwork with its
 * progress strip ([artwork]), a two-line title, the show ([subtitle]) and a short [meta] line, and a
 * [trailing] control (a play button, or the download state). In select mode ([selection]) the row
 * shows a checkbox and the whole row toggles; otherwise a tap opens or plays and [onLongClick] (when
 * given) starts select mode.
 */
@Composable
@Suppress("LongParameterList") // The row's parts and its two ways to respond.
internal fun LibraryEpisodeRow(
    title: String,
    artwork: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    meta: (@Composable RowScope.() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    onLongClickLabel: String? = null,
    selection: LibraryRowSelection? = null,
) {
    val interaction = if (selection != null) {
        Modifier.toggleable(value = selection.selected, role = Role.Checkbox, onValueChange = selection.onSelectedChange)
    } else if (onLongClick != null) {
        Modifier.expressiveClickable(onLongClickLabel = onLongClickLabel, onLongClick = onLongClick, onClick = onClick)
    } else {
        Modifier.expressiveClickable(onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(interaction)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selection != null) {
            Checkbox(checked = selection.selected, onCheckedChange = null)
            Spacer(modifier = Modifier.width(12.dp))
        }
        artwork()
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = GoogleSansWeight.semiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (meta != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), content = meta)
            }
        }
        if (trailing != null && selection == null) {
            Spacer(modifier = Modifier.width(10.dp))
            trailing()
        }
    }
}
