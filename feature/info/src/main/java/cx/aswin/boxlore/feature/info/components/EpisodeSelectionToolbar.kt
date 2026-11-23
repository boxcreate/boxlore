package cx.aswin.boxlore.feature.info.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLoader
import cx.aswin.boxlore.core.designsystem.components.BoxloreDropdownMenu
import cx.aswin.boxlore.core.designsystem.components.rememberBoxloreMenuState
import cx.aswin.boxlore.core.designsystem.theme.ExpressiveShapes
import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight
import cx.aswin.boxlore.feature.info.R

internal data class EpisodeSelectionToolbarState(
    val selectedCount: Int,
    val canDownload: Boolean,
    val markAsUnplayed: Boolean,
    /** Every selected episode is archived: the menu offers Unarchive instead of Archive. */
    val unarchive: Boolean = false,
    val canAddToQueue: Boolean,
    val hasRangeAnchor: Boolean,
    val isLoadingFullSelection: Boolean,
    /** False while a recreated page is still looking its selected episodes up again. */
    val actionsEnabled: Boolean = true,
)

internal data class EpisodeSelectionToolbarActions(
    val onClear: () -> Unit,
    val onDownload: () -> Unit,
    val onToggleCompletion: () -> Unit,
    val onPlay: () -> Unit,
    val onAddToQueue: () -> Unit,
    /** Archives the selection, or brings it back when [EpisodeSelectionToolbarState.unarchive]. */
    val onToggleArchive: () -> Unit = {},
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
                enabled = state.actionsEnabled && state.canDownload,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Download,
                    contentDescription = stringResource(R.string.episode_selection_download),
                )
            }
            IconButton(
                onClick = actions.onToggleCompletion,
                enabled = state.actionsEnabled,
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
            FilledIconButton(onClick = actions.onPlay, enabled = state.actionsEnabled) {
                Icon(
                    imageVector = Icons.Rounded.PlayArrow,
                    contentDescription = stringResource(R.string.episode_selection_play),
                )
            }
            IconButton(
                onClick = actions.onAddToQueue,
                enabled = state.actionsEnabled && state.canAddToQueue,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.PlaylistAdd,
                    contentDescription = stringResource(R.string.episode_selection_add_queue),
                )
            }
            EpisodeSelectionRangeMenu(state = state, actions = actions)
        }
    }
}

/**
 * The selection's More menu in three groups: Archive or Unarchive, the ranges to add, and Clear
 * selection. It stays open through a fold that recreates the page.
 */
@Composable
private fun EpisodeSelectionRangeMenu(
    state: EpisodeSelectionToolbarState,
    actions: EpisodeSelectionToolbarActions,
) {
    val menu = rememberBoxloreMenuState()
    val archive = stringResource(if (state.unarchive) R.string.episode_selection_unarchive else R.string.episode_selection_archive)
    val visible = stringResource(R.string.episode_selection_select_visible)
    val all = stringResource(R.string.episode_selection_select_all)
    val older = stringResource(R.string.episode_selection_select_older)
    val newer = stringResource(R.string.episode_selection_select_newer)
    val clear = stringResource(R.string.episode_selection_clear)
    val run: (() -> Unit) -> () -> Unit = { action ->
        {
            menu.dismiss()
            action()
        }
    }
    Box {
        IconButton(
            onClick = menu::open,
            enabled = !state.isLoadingFullSelection,
        ) {
            if (state.isLoadingFullSelection) {
                BoxLoreLoader.CircularWavy(size = 20.dp, modifier = Modifier.testTag(EPISODE_SELECTION_PROGRESS_TAG))
            } else {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = stringResource(R.string.episode_selection_more),
                )
            }
        }
        BoxloreDropdownMenu(menu) {
            // Leaves the selection's lists without marking it played; Undo comes in a snackbar.
            group {
                item(archive, run(actions.onToggleArchive), leadingIcon = if (state.unarchive) Icons.Rounded.Unarchive else Icons.Rounded.Archive, enabled = state.actionsEnabled)
            }
            group {
                item(visible, run(actions.onSelectVisible))
                item(all, run(actions.onSelectAll))
                item(older, run(actions.onSelectOlder), enabled = state.hasRangeAnchor)
                item(newer, run(actions.onSelectNewer), enabled = state.hasRangeAnchor)
            }
            group {
                item(clear, run(actions.onClear))
            }
        }
    }
}

/** Test tag on the selection bar's loader while a range is read. */
internal const val EPISODE_SELECTION_PROGRESS_TAG = "episode_selection_progress"
