package cx.aswin.boxlore.feature.info.components

import androidx.compose.runtime.Composable
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.PodcastInfoViewModel

@Composable
internal fun PodcastInfoMarkDialogs(
    uiState: PodcastInfoUiState,
    showMarkAllPlayedDialog: Boolean,
    showMarkAllUnplayedDialog: Boolean,
    onDismissPlayed: () -> Unit,
    onDismissUnplayed: () -> Unit,
    viewModel: PodcastInfoViewModel,
    episodePendingDownloadRemoval: cx.aswin.boxlore.core.model.Episode? = null,
) {
    val currentState = uiState as? PodcastInfoUiState.Success ?: return
    if (showMarkAllPlayedDialog) {
        MarkAllEpisodesDialog(
            podcastTitle = currentState.podcast.title,
            markAsPlayed = true,
            onDismiss = onDismissPlayed,
            onConfirm = {
                onDismissPlayed()
                viewModel.markAllAsCompleted()
            },
        )
    }
    if (showMarkAllUnplayedDialog) {
        MarkAllEpisodesDialog(
            podcastTitle = currentState.podcast.title,
            markAsPlayed = false,
            onDismiss = onDismissUnplayed,
            onConfirm = {
                onDismissUnplayed()
                viewModel.markAllAsUncompleted()
            },
        )
    }
    if (episodePendingDownloadRemoval != null) {
        cx.aswin.boxlore.core.designsystem.components.RemoveDownloadConfirmationDialog(
            episodeTitle = episodePendingDownloadRemoval.title,
            onConfirm = viewModel::confirmDownloadRemoval,
            onDismiss = viewModel::dismissDownloadRemoval,
        )
    }
}
