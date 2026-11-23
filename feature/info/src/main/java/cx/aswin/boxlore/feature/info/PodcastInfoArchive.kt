package cx.aswin.boxlore.feature.info

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.feature.info.logic.EpisodeArchiveLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The show an archive is written for, read when the listener acts so leaving the page can't lose it. */
internal data class ArchiveShow(val id: String, val title: String, val imageUrl: String?)

/**
 * Archive and Unarchive on the show page and the episode page. Archiving takes episodes out of
 * every list without marking them played (`PlaybackRepository.setEpisodesDismissed`). Each action
 * sends one `episode_archived` event, however many episodes it covers; Undo sends none.
 *
 * @param scope the page's scope, for reading [archivedIds]
 * @param writeScope where the writes run: the app's scope, so leaving the page doesn't cancel one
 * @param show the show the page shows, or null while it loads (nothing is archived then)
 * @param write archives ([archive] true) or brings back episodes of [show]
 */
@Suppress("LongParameterList")
internal class EpisodeArchiver(
    scope: CoroutineScope,
    private val writeScope: CoroutineScope,
    archivedIds: Flow<Set<String>>,
    private val surface: String,
    private val show: () -> ArchiveShow?,
    private val write: suspend (show: ArchiveShow, episodes: List<Episode>, archive: Boolean) -> Unit,
) {
    /** Every archived episode ID, across each episode's IDs. */
    val archivedIds: StateFlow<Set<String>> = archivedIds.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Archives [episodes] ([archive] true) or brings them back, and returns the ones it changed, for
     * Undo. Nothing changes, and nothing is sent, when every episode already is as asked or the page
     * has no show yet.
     */
    fun setArchived(episodes: List<Episode>, archive: Boolean, undo: Boolean = false): List<Episode> {
        // Undo puts back exactly what the action changed, even before the archive list catches up.
        val changed = if (undo) episodes.distinctBy(Episode::id) else EpisodeArchiveLogic.changedBy(episodes, archive, archivedIds.value)
        if (changed.isEmpty()) return changed
        val target = show() ?: return emptyList()
        if (!undo) AnalyticsHelper.trackEpisodeArchived(isArchived = archive, surface = surface, itemsCount = changed.size)
        writeScope.launch { write(target, changed, archive) }
        return changed
    }

    companion object {
        const val SURFACE_SHOW_PAGE = "show_page"
        const val SURFACE_EPISODE_PAGE = "episode_page"
    }
}

/**
 * The show page's Archive / Unarchive for a selection: applies it, then shows how many episodes it
 * changed with Undo, which puts exactly those back.
 */
@Composable
internal fun rememberSelectionArchive(
    viewModel: PodcastInfoViewModel,
    snackbarHostState: SnackbarHostState,
    pendingUndo: MutableState<PendingUndo?> = remember { mutableStateOf(null) },
): (List<Episode>, Boolean) -> Unit {
    val scope = rememberCoroutineScope()
    val resources = LocalContext.current.resources
    return { selected, archive ->
        val changed = viewModel.setEpisodesArchived(selected, archive)
        if (changed.isNotEmpty()) {
            val message = resources.getQuantityString(
                if (archive) R.plurals.episode_archive_done else R.plurals.episode_unarchive_done,
                changed.size,
                changed.size,
            )
            val kind = if (archive) UndoKind.Archive else UndoKind.Unarchive
            val undo = PendingUndo(kind, changed.map(Episode::id), undoClockMs() + PendingUndo.durationMs(kind))
            scope.offerUndo(snackbarHostState, pendingUndo, undo, message, resources.getString(R.string.episode_archive_undo), SnackbarDuration.Short) {
                viewModel.setEpisodesArchived(changed, !archive, undo = true)
            }
        }
    }
}
