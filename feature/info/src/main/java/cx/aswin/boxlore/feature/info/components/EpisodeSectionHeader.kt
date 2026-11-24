package cx.aswin.boxlore.feature.info.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveMotion
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable

/**
 * One heading style for every episode page section: bold `titleLarge`, two lines at most, a
 * screen-reader heading. [trailing] is a short count after it; [icon] a small badge before it.
 * Its text starts where [modifier]'s padding puts it.
 */
@Composable
internal fun EpisodeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    icon: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        icon?.invoke()
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = GoogleSansWeight.bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).semantics { heading() },
        )
        trailing?.let {
            Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

/**
 * The row that opens or closes a long section (About, Chapters, Links): its [label] and an arrow
 * that turns when [expanded], at the bottom of the section's card. The turn uses boxlore's spring
 * and snaps when animations are off.
 */
@Composable
internal fun EpisodeExpandRow(label: String, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, shape: Shape? = null) {
    val turn by animateFloatAsState(if (expanded) 180f else 0f, ExpressiveMotion.QuickSpring, label = "expand_arrow")
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .expressiveClickable(shape = shape, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.Rounded.ExpandMore, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp).rotate(turn))
    }
}
