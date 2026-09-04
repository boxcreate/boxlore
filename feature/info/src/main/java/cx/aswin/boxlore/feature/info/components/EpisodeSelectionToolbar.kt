package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.info.R

internal data class EpisodeSelectionToolbarState(
    val selectedCount: Int,
    val canDownload: Boolean,
    val markAsUnplayed: Boolean,
    val canAddToQueue: Boolean,
    val hasRangeAnchor: Boolean,
    val isLoadingFullSelection: Boolean,
)

internal data class EpisodeSelectionToolbarActions(
    val onClear: () -> Unit,
    val onDownload: () -> Unit,
    val onToggleCompletion: () -> Unit,
    val onPlay: () -> Unit,
    val onAddToQueue: () -> Unit,
    val onSelectVisible: () -> Unit,
    val onSelectAll: () -> Unit,
    val onSelectOlder: () -> Unit,
    val onSelectNewer: () -> Unit,
)

@Composable
internal fun EpisodeSelectionToolbar(
    state: EpisodeSelectionToolbarState,
    actions: EpisodeSelectionToolbarActions,
    modifier: Modifier = Modifier,
) {
    var rangeMenuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier,
        shape = ExpressiveShapes.Pill,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = state.selectedCount.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = GoogleSansWeight.bold,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            IconButton(
                onClick = actions.onDownload,
                enabled = state.canDownload,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.episode_selection_download),
                )
            }
            IconButton(
                onClick = actions.onToggleCompletion,
            ) {
                Icon(
                    imageVector =
                    if (state.markAsUnplayed) {
                        Icons.Rounded.RadioButtonUnchecked
                    } else {
                        Icons.Rounded.DoneAll
                    },
                    contentDescription =
                    stringResource(
                        if (state.markAsUnplayed) {
                            R.string.episode_selection_mark_unplayed
                        } else {
                            R.string.episode_selection_mark_completed
                        },
                    ),
                )
            }
            FilledIconButton(onClick = actions.onPlay) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.episode_selection_play),
                )
            }
            IconButton(
                onClick = actions.onAddToQueue,
                enabled = state.canAddToQueue,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = stringResource(R.string.episode_selection_add_queue),
                )
            }
            EpisodeSelectionRangeMenu(
                state = state,
                actions = actions,
                expanded = rangeMenuExpanded,
                onExpandedChange = { rangeMenuExpanded = it },
            )
        }
    }
}

@Composable
private fun EpisodeSelectionRangeMenu(
    state: EpisodeSelectionToolbarState,
    actions: EpisodeSelectionToolbarActions,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
) {
    Box {
        IconButton(
            onClick = { onExpandedChange(true) },
            enabled = !state.isLoadingFullSelection,
        ) {
            if (state.isLoadingFullSelection) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.episode_selection_more),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            RangeMenuItem(
                text = stringResource(R.string.episode_selection_select_visible),
                onClick = {
                    onExpandedChange(false)
                    actions.onSelectVisible()
                },
            )
            RangeMenuItem(
                text = stringResource(R.string.episode_selection_select_all),
                onClick = {
                    onExpandedChange(false)
                    actions.onSelectAll()
                },
            )
            RangeMenuItem(
                text = stringResource(R.string.episode_selection_select_older),
                enabled = state.hasRangeAnchor,
                onClick = {
                    onExpandedChange(false)
                    actions.onSelectOlder()
                },
            )
            RangeMenuItem(
                text = stringResource(R.string.episode_selection_select_newer),
                enabled = state.hasRangeAnchor,
                onClick = {
                    onExpandedChange(false)
                    actions.onSelectNewer()
                },
            )
            HorizontalDivider()
            RangeMenuItem(
                text = stringResource(R.string.episode_selection_clear),
                onClick = {
                    onExpandedChange(false)
                    actions.onClear()
                },
            )
        }
    }
}

@Composable
private fun RangeMenuItem(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(text) },
        enabled = enabled,
        onClick = onClick,
    )
}
