package cx.aswin.boxlore.feature.player.v2

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.designsystem.components.AddToPlaylistCopy
import cx.aswin.boxlore.core.designsystem.components.PlaylistCopy
import cx.aswin.boxlore.core.designsystem.components.PlaylistNameDialog
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The confirmation after Save queue as playlist. */
internal fun saveQueueMessage(name: String, added: Int, didNotFit: Int): String =
    if (added > 0 && didNotFit == 0) "Saved as $name" else AddToPlaylistCopy.result(name, added, didNotFit = didNotFit)

/**
 * Save queue as playlist: names a new playlist holding the episode playing now and Up Next, in
 * queue order. The draft lives in [ui] (saved, and kept by the retained player store), so it survives
 * the queue moving between the phone sheet and the tablet pane. Hidden without [repository].
 */
@Composable
internal fun SaveQueueAsPlaylistDialog(
    repository: PlaylistRepository?,
    ui: FullPlayerUiState,
    queue: List<Episode>,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
) {
    val draft = ui.saveQueueName ?: return
    if (repository == null || queue.isEmpty()) return
    TrackScreenView("save_queue_as_playlist")
    PlaylistNameDialog(
        title = PlaylistCopy.SAVE_QUEUE,
        supportingText = PlaylistCopy.saveQueueBody(queue.size - 1),
        name = draft,
        onNameChange = { ui.saveQueueName = it },
        confirmLabel = PlaylistCopy.SAVE,
        onConfirm = { name ->
            ui.saveQueueName = null
            val episodes = queue.toList()
            scope.launch {
                val created = repository.createPlaylist(name, episodes)
                AnalyticsHelper.trackPlaylistModified("create", "player_queue", itemsCount = created.result.added, playlistSize = created.playlist.episodeCount)
                snackbarHostState.showSnackbar(saveQueueMessage(created.playlist.name, created.result.added, created.result.didNotFit))
            }
        },
        onDismissRequest = { ui.saveQueueName = null },
    )
}
