package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.feature.info.R

internal data class EpisodeCompletionState(val isCompleted: Boolean, val showTip: Boolean = false)

@Composable
internal fun EpisodeCompletionPill(isCompleted: Boolean, onToggleCompletion: () -> Unit) {
    val label = stringResource(if (isCompleted) R.string.episode_info_played else R.string.episode_info_mark_played)
    val action = stringResource(if (isCompleted) R.string.episode_info_mark_unplayed else R.string.episode_info_mark_played)
    FilterChip(
        selected = isCompleted,
        onClick = onToggleCompletion,
        label = { Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis) },
        leadingIcon = { Icon(if (isCompleted) Icons.Rounded.CheckCircle else Icons.Rounded.CheckCircleOutline, null, Modifier.size(18.dp)) },
        shape = CircleShape,
        border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            iconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        modifier = Modifier.heightIn(min = 40.dp).semantics {
            contentDescription = action
            stateDescription = label
        },
    )
}
