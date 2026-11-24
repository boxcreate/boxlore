package cx.aswin.boxlore.feature.info

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.BoxloreAlertDialog
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogConfirmButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogDismissButton
import cx.aswin.boxlore.core.designsystem.components.BoxloreDialogIcon
import kotlinx.coroutines.launch

/** What the show page's menu and list need from Hide show everywhere ([rememberPodcastInfoHideShow]). */
internal data class PodcastInfoHideShowUi(
    val isHidden: Boolean,
    /** The menu item: asks to hide, or unhides a hidden show at once. */
    val onMenuClick: () -> Unit,
    val onUnhide: () -> Unit,
)

/**
 * Hide show everywhere on the show page: the confirmation, which survives window changes and
 * recreation, and the snackbars (Hidden everywhere with Undo, and No longer hidden).
 */
@Composable
internal fun rememberPodcastInfoHideShow(
    viewModel: PodcastInfoViewModel,
    state: PodcastInfoUiState.Success,
    snackbarHostState: SnackbarHostState,
    pendingUndo: MutableState<PendingUndo?> = remember { mutableStateOf(null) },
): PodcastInfoHideShowUi {
    val isHidden by viewModel.isHidden.collectAsState()
    var confirming by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val hiddenMessage = stringResource(R.string.podcast_info_hidden_snackbar)
    val undoLabel = stringResource(R.string.podcast_info_hidden_undo)
    val unhiddenMessage = stringResource(R.string.podcast_info_unhidden_snackbar)
    // A dialog restored after the show was hidden elsewhere has nothing left to ask.
    LaunchedEffect(isHidden) { if (isHidden) confirming = false }
    if (confirming && !isHidden) {
        HideShowEverywhereDialog(
            isSubscribed = state.isSubscribed,
            onDismissRequest = { confirming = false },
            onConfirm = {
                viewModel.hideShowEverywhere()
                val undo = PendingUndo(UndoKind.HideShow, emptyList(), undoClockMs() + PendingUndo.durationMs(UndoKind.HideShow))
                scope.offerUndo(snackbarHostState, pendingUndo, undo, hiddenMessage, undoLabel, SnackbarDuration.Long) { viewModel.undoHideShow() }
            },
        )
    }
    val unhide = {
        viewModel.unhideShow()
        scope.launch { snackbarHostState.showSnackbar(unhiddenMessage) }
        Unit
    }
    return PodcastInfoHideShowUi(
        isHidden = isHidden,
        onMenuClick = {
            if (isHidden) {
                unhide()
            } else {
                confirming = true
            }
        },
        onUnhide = unhide,
    )
}

@Composable
private fun HideShowEverywhereDialog(
    isSubscribed: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: () -> Unit,
) {
    TrackScreenView("podcast_hide_show_confirmation")
    BoxloreAlertDialog(
        onDismissRequest = onDismissRequest,
        icon = { BoxloreDialogIcon(Icons.Rounded.VisibilityOff) },
        title = { Text(stringResource(R.string.podcast_info_hide_title)) },
        text = {
            Text(stringResource(if (isSubscribed) R.string.podcast_info_hide_message_subscribed else R.string.podcast_info_hide_message))
        },
        // Not destructive: the snackbar that follows offers Undo.
        confirmButton = {
            BoxloreDialogConfirmButton(
                stringResource(if (isSubscribed) R.string.podcast_info_hide_confirm_subscribed else R.string.podcast_info_hide_confirm),
                onClick = {
                    onDismissRequest()
                    onConfirm()
                },
            )
        },
        dismissButton = { BoxloreDialogDismissButton(stringResource(R.string.podcast_info_hide_cancel), onDismissRequest) },
    )
}

/** The note under a hidden show's actions, with Unhide. */
@Composable
internal fun PodcastInfoHiddenNote(onUnhide: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag(PODCAST_INFO_HIDDEN_NOTE_TAG),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.VisibilityOff, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(
                text = stringResource(R.string.podcast_info_hidden_note),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onUnhide) {
                Text(stringResource(R.string.podcast_info_hidden_note_unhide))
            }
        }
    }
}

/** Test tag on [PodcastInfoHiddenNote]. */
internal const val PODCAST_INFO_HIDDEN_NOTE_TAG = "podcast_info_hidden_note"
