package cx.aswin.boxlore.feature.library.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistOrder
import cx.aswin.boxlore.core.model.PlaylistShow
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.UpNextSnapshot
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One playlist's page: loading, gone (deleted, or a stale link), or its shows and what plays next. */
sealed interface PlaylistDetailState {
    data object Loading : PlaylistDetailState
    data object Missing : PlaylistDetailState
    data class Ready(val playlist: Playlist, val shows: List<PlaylistShow>, val comingUp: List<Episode>) : PlaylistDetailState
}

/** What the app hands the playlist pages besides storage: the shows to pick from, and the queue. */
class PlaylistPlaybackPorts(
    /** Followed shows, for picking a playlist's shows. */
    val followedShows: Flow<List<Podcast>>,
    /** Library folders, whose followed shows pre-fill a pick. */
    val folders: Flow<List<SubscriptionFolder>>,
    /** Finished episode IDs; a change refreshes Coming up. */
    val completedEpisodeIds: Flow<Set<String>>,
    /** Replaces the queue with a playlist's first batch (the app passes `QueueManager.playPlaylist`). */
    val startPlayback: (playlistId: String, episodes: List<Episode>) -> Unit,
    /** The queue before Play replaces it (`QueueManager.upNextSnapshot`). */
    val upNextSnapshot: () -> UpNextSnapshot? = { null },
    /** Undo for Play (`QueueManager.restoreUpNext`). */
    val restoreUpNext: (UpNextSnapshot) -> Unit = {},
)

/**
 * The Playlists page and, with [playlistId], one playlist's page. Storage is [PlaylistRepository]
 * (`:core:catalog`). Play queues the playlist's first [batchSize] episodes (never more than
 * [queueLimit]); the playback service tops the queue up from the playlist after that. Create,
 * delete, add_show, remove_show and play send `playlist_modified` with source `library`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistsViewModel(
    private val repository: PlaylistRepository,
    private val ports: PlaylistPlaybackPorts,
    private val playlistId: String? = null,
    private val queueLimit: Int = PlaybackRepository.QUEUE_LIMIT,
    private val batchSize: Int = PLAY_BATCH,
) : ViewModel() {
    /** Every playlist, oldest first; null until the first read. */
    val playlists: StateFlow<List<Playlist>?> =
        repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    val followedShows: StateFlow<List<Podcast>> =
        ports.followedShows.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val folders: StateFlow<List<SubscriptionFolder>> =
        ports.folders.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    /** Bumped by [refreshComingUp]: stored episodes and in-progress plays change without a cheap signal. */
    private val refreshes = MutableStateFlow(0)

    /**
     * What a playlist page shows: the playlist, its shows and Coming up, worked out again when they
     * change, an episode is finished, or the page comes back ([refreshComingUp]); never per playback tick.
     */
    val detail: StateFlow<PlaylistDetailState> =
        if (playlistId == null) {
            MutableStateFlow(PlaylistDetailState.Missing)
        } else {
            combine(
                repository.playlist(playlistId),
                repository.shows(playlistId),
                ports.completedEpisodeIds.onStart { emit(emptySet()) },
                refreshes,
            ) { playlist, shows, _, _ -> playlist to shows }.mapLatest { (playlist, shows) ->
                if (playlist == null) {
                    PlaylistDetailState.Missing
                } else {
                    PlaylistDetailState.Ready(playlist, shows, repository.nextEpisodes(playlistId, COMING_UP))
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PlaylistDetailState.Loading)
        }

    /** Works Coming up out again (the page came back: new episodes may be stored, plays may have moved on). */
    fun refreshComingUp() {
        refreshes.value++
    }

    fun create(name: String, podcastIds: List<String>, onCreated: (Playlist) -> Unit = {}) {
        viewModelScope.launch {
            val created = repository.createPlaylist(name, podcastIds)
            AnalyticsHelper.trackPlaylistModified("create", SOURCE, playlistSize = created.showCount)
            onCreated(created)
        }
    }

    fun rename(playlistId: String, name: String) {
        viewModelScope.launch { repository.renamePlaylist(playlistId, name) }
    }

    fun delete(playlistId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            AnalyticsHelper.trackPlaylistModified("delete", SOURCE)
            onDeleted()
        }
    }

    fun setOrder(order: PlaylistOrder) {
        val id = playlistId ?: return
        viewModelScope.launch { repository.setOrder(id, order) }
    }

    /** Adds [podcastIds] to this playlist: one `add_show` with how many went in. */
    fun addShows(podcastIds: List<String>) {
        val id = playlistId ?: return
        viewModelScope.launch {
            val added = repository.addShows(id, podcastIds)
            val size = (detail.value as? PlaylistDetailState.Ready)?.shows?.size?.plus(added)
            if (added > 0) AnalyticsHelper.trackPlaylistModified("add_show", SOURCE, itemsCount = added, playlistSize = size)
        }
    }

    /** Removes [show]; [onRemoved] gets it back for Undo. */
    fun removeShow(show: PlaylistShow, onRemoved: (PlaylistShow) -> Unit = {}) {
        viewModelScope.launch {
            val removed = repository.removeShow(show.playlistId, show.podcastId) ?: return@launch
            val size = (detail.value as? PlaylistDetailState.Ready)?.shows?.size?.minus(1)?.coerceAtLeast(0)
            AnalyticsHelper.trackPlaylistModified("remove_show", SOURCE, playlistSize = size)
            onRemoved(removed)
        }
    }

    fun undoRemove(show: PlaylistShow) {
        viewModelScope.launch { repository.restoreShow(show) }
    }

    /**
     * Replaces the queue with this playlist's next episodes. [onReplaced] gets what the queue held,
     * for Undo, when there was anything; [onNothingToPlay] runs when the playlist has nothing to play.
     */
    fun play(onReplaced: (UpNextSnapshot) -> Unit = {}, onNothingToPlay: () -> Unit = {}) {
        val id = playlistId ?: return
        viewModelScope.launch {
            val episodes = repository.nextEpisodes(id, minOf(batchSize, queueLimit))
            if (episodes.isEmpty()) {
                onNothingToPlay()
                return@launch
            }
            val previous = ports.upNextSnapshot()
            ports.startPlayback(id, episodes)
            val size = (detail.value as? PlaylistDetailState.Ready)?.shows?.size
            AnalyticsHelper.trackPlaylistModified("play", SOURCE, itemsCount = episodes.size, playlistSize = size)
            previous?.let(onReplaced)
        }
    }

    fun undoPlay(previous: UpNextSnapshot) = ports.restoreUpNext(previous)

    companion object {
        /** Episodes Play queues; the service adds the rest as the queue runs out. */
        const val PLAY_BATCH = 20

        /** Episodes the page previews under Coming up. */
        const val COMING_UP = 10

        private const val SOURCE = "library"
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}
