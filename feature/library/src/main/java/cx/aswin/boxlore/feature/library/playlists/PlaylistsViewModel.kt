package cx.aswin.boxlore.feature.library.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistEpisode
import cx.aswin.boxlore.core.playback.PlaybackRepository
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One playlist's page: loading, gone (deleted, or a stale link), or its episodes with their played state. */
sealed interface PlaylistDetailState {
    data object Loading : PlaylistDetailState
    data object Missing : PlaylistDetailState
    data class Ready(val playlist: Playlist, val episodes: List<PlaylistEpisode>, val completedIds: Set<String>,) : PlaylistDetailState
}

/**
 * The Playlists page and, with [playlistId], one playlist's page. Storage is [PlaylistRepository]
 * (`:core:catalog`); [startPlayback] hands the queue copy to playback (the app passes
 * `QueueManager.playPlaylist`). Create, delete, reorder and play send `playlist_modified` with source `library`.
 */
class PlaylistsViewModel(
    private val repository: PlaylistRepository,
    completedEpisodeIds: Flow<Set<String>>,
    private val startPlayback: (playlistId: String, episodes: List<Episode>) -> Unit,
    private val playlistId: String? = null,
    private val queueLimit: Int = PlaybackRepository.QUEUE_LIMIT,
) : ViewModel() {
    /** Every playlist, oldest first; null until the first read. */
    val playlists: StateFlow<List<Playlist>?> =
        repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val detail: StateFlow<PlaylistDetailState> =
        if (playlistId == null) {
            MutableStateFlow(PlaylistDetailState.Missing)
        } else {
            combine(repository.playlist(playlistId), repository.episodes(playlistId), completedEpisodeIds.onStart { emit(emptySet()) }) { playlist, episodes, completed ->
                if (playlist == null) PlaylistDetailState.Missing else PlaylistDetailState.Ready(playlist, episodes, completed)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistDetailState.Loading)
        }

    fun create(name: String, onCreated: (Playlist) -> Unit = {}) {
        viewModelScope.launch {
            val created = repository.createPlaylist(name)
            AnalyticsHelper.trackPlaylistModified("create", SOURCE, playlistSize = 0)
            onCreated(created.playlist)
        }
    }

    fun rename(playlistId: String, name: String) {
        viewModelScope.launch {
            repository.renamePlaylist(playlistId, name)
        }
    }

    fun delete(playlistId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            AnalyticsHelper.trackPlaylistModified("delete", SOURCE)
            onDeleted()
        }
    }

    /** Removes [entry]; [onRemoved] gets the removed rows for Undo. */
    fun remove(entry: PlaylistEpisode, onRemoved: (List<PlaylistEpisode>) -> Unit = {}) {
        viewModelScope.launch {
            val removed = repository.removeEpisodes(entry.playlistId, listOf(entry.itemId))
            if (removed.isEmpty()) return@launch
            onRemoved(removed)
        }
    }

    fun undoRemove(removed: List<PlaylistEpisode>) {
        viewModelScope.launch {
            repository.restoreEpisodes(removed)
        }
    }

    /**
     * Saves the order on screen. Not cancellable: the page saves a drag Back interrupted as it
     * leaves, and that write must finish after this ViewModel is cleared.
     */
    fun reorder(orderedItemIds: List<Long>) {
        val id = playlistId ?: return
        viewModelScope.launch(NonCancellable) {
            repository.reorder(id, orderedItemIds)
            AnalyticsHelper.trackPlaylistModified("reorder", SOURCE)
        }
    }

    /**
     * Replaces the queue with this playlist: from [startItemId], else the first unplayed episode
     * ([PlaylistPlaybackLogic.playbackEpisodes]). [order] is the order on screen, so a drag not yet
     * saved plays as shown.
     */
    fun play(startItemId: Long? = null, order: List<Long>? = null) {
        val ready = detail.value as? PlaylistDetailState.Ready ?: return
        val entries = PlaylistPlaybackLogic.applyOrder(ready.episodes, order)
        val queue = PlaylistPlaybackLogic.playbackEpisodes(entries, ready.completedIds, startItemId, queueLimit)
        if (queue.isEmpty()) return
        startPlayback(ready.playlist.id, queue)
        AnalyticsHelper.trackPlaylistModified("play", SOURCE, itemsCount = queue.size, playlistSize = ready.episodes.size)
    }

    private companion object {
        const val SOURCE = "library"
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
