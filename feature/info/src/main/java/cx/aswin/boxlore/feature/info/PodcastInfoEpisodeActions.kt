package cx.aswin.boxlore.feature.info

import android.os.Bundle
import android.util.Log
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.ShowListTooLongException
import cx.aswin.boxlore.core.downloads.DownloadRepository
import cx.aswin.boxlore.core.downloads.DownloadToggleAction
import cx.aswin.boxlore.core.downloads.DownloadTogglePolicy
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.QueueManager
import cx.aswin.boxlore.core.playback.addToQueue
import cx.aswin.boxlore.core.playback.addToQueueNext
import cx.aswin.boxlore.core.playback.markAllEpisodesUncompleted
import cx.aswin.boxlore.core.playback.playQueue
import cx.aswin.boxlore.core.playback.removeFromQueue
import cx.aswin.boxlore.core.playback.toggleCompletion
import cx.aswin.boxlore.core.playback.toggleLike
import cx.aswin.boxlore.core.playback.togglePlayPause
import cx.aswin.boxlore.core.playback.withEpisodeAliases
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.TAG
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.sortParamFor
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPlaybackSourceLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * What the show page's episode rows and selection toolbar do: like, played, download (asking
 * before removing one), queue and play, for one episode or a selection, and the download and
 * queue state the rows show. Every action acts for the show the page shows.
 */
internal class PodcastInfoEpisodeActions(
    private val pageState: PodcastInfoPageState,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: DownloadRepository,
    private val queueManager: QueueManager,
    private val sessionTracker: PodcastInfoSessionTracker,
    private val entryPoint: String?,
    private val scope: CoroutineScope,
    /** Every episode of the given show, as Select all reads it ([PodcastInfoEpisodePaging.selectionWindow]); throws when any page fails or the page shows another show. */
    private val loadWholeShow: suspend (podcastId: String) -> List<Episode>,
    /** Where Mark all reads and writes, so leaving the page doesn't stop it: the app's scope, or [scope]. */
    workScope: CoroutineScope = scope,
) {
    private val uiState = pageState.uiState

    /** One Mark all at a time; a repeat tap while the show is read is ignored. */
    private val markAll = PodcastInfoMarkAll(scope, uiState, loadWholeShow, ::logMarkAllFailure, workScope)

    val downloadedEpisodeIds: StateFlow<Set<String>> =
        downloadRepository.completedDownloadIds
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    val downloadingEpisodeIds: StateFlow<Set<String>> =
        downloadRepository.downloadingEpisodeIds
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    private val _episodePendingDownloadRemoval = MutableStateFlow<Episode?>(null)
    val episodePendingDownloadRemoval: StateFlow<Episode?> = _episodePendingDownloadRemoval.asStateFlow()

    // Track queued episodes, under every stored ID of each queued episode: a show switched to its
    // feed lists an episode under its feed ID while the queue may hold it under the old one.
    val queuedEpisodeIds: StateFlow<Set<String>> =
        playbackRepository
            .withEpisodeAliases(
                playbackRepository.playerState
                    .map { state -> state.queue.map { it.id }.toSet() }
                    .distinctUntilChanged(),
            ).stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptySet(),
            )

    fun onToggleLike(episode: Episode) {
        val currentState = uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            scope.launch {
                playbackRepository.toggleLike(
                    episode = episode,
                    podcastId = currentState.podcast.id,
                    podcastTitle = currentState.podcast.title,
                    podcastImageUrl = currentState.podcast.imageUrl,
                )
            }
        }
    }

    fun confirmDownloadRemoval() {
        val episode = _episodePendingDownloadRemoval.value ?: return
        scope.launch {
            downloadRepository.removeDownload(episode.id)
            _episodePendingDownloadRemoval.value = null
        }
    }

    fun dismissDownloadRemoval() {
        _episodePendingDownloadRemoval.value = null
    }

    fun onToggleCompletion(episode: Episode) {
        val currentState = uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            scope.launch {
                playbackRepository.toggleCompletion(
                    episode = episode,
                    podcastId = currentState.podcast.id,
                    podcastTitle = currentState.podcast.title,
                    podcastImageUrl = currentState.podcast.imageUrl,
                )
            }
        }
    }

    /**
     * Marks every episode of the show played: the same whole-show read Select all uses
     * ([loadWholeShow]), then one batched write. A failed page changes nothing and says so.
     */
    fun markAllAsCompleted() {
        val currentState = uiState.value as? PodcastInfoUiState.Success ?: return
        val podcast = currentState.podcast
        markAll.start(podcast.id) { targets ->
            playbackRepository.markAllEpisodesCompleted(
                episodes = targets,
                podcastId = podcast.id,
                podcastTitle = podcast.title,
                podcastImageUrl = podcast.imageUrl,
            )
        }
    }

    /** [markAllAsCompleted], marking every episode unplayed. */
    fun markAllAsUncompleted() {
        val currentState = uiState.value as? PodcastInfoUiState.Success ?: return
        markAll.start(currentState.podcast.id) { targets ->
            playbackRepository.markAllEpisodesUncompleted(episodes = targets)
        }
    }

    fun toggleDownload(episode: Episode) {
        val currentState = uiState.value
        Log.d("PodcastInfoVM", "toggleDownload: title=${episode.title}, state=$currentState")
        if (currentState is PodcastInfoUiState.Success) {
            scope.launch {
                val isDownloaded = downloadRepository.isDownloaded(episode.id).first()
                val isDownloading = downloadRepository.isDownloading(episode.id).first()
                when (DownloadTogglePolicy.resolveAction(isDownloaded, isDownloading)) {
                    DownloadToggleAction.CONFIRM_REMOVAL -> {
                        _episodePendingDownloadRemoval.value = episode
                    }
                    DownloadToggleAction.CANCEL_DOWNLOAD -> {
                        downloadRepository.removeDownload(episode.id)
                    }
                    DownloadToggleAction.START_DOWNLOAD -> {
                        downloadRepository.addDownload(episode, currentState.podcast)
                    }
                }
            }
        } else {
            Log.w("PodcastInfoVM", "toggleDownload ignored, state is not Success")
        }
    }

    fun downloadEpisodes(episodes: List<Episode>) {
        val currentState = uiState.value as? PodcastInfoUiState.Success ?: return
        val unavailableIds = downloadedEpisodeIds.value + downloadingEpisodeIds.value
        episodes
            .distinctBy(Episode::id)
            .filterNot { it.id in unavailableIds }
            .forEach { episode ->
                downloadRepository.addDownload(episode, currentState.podcast)
            }
    }

    fun markEpisodesCompleted(episodes: List<Episode>) {
        val currentState = uiState.value as? PodcastInfoUiState.Success ?: return
        val targets = episodes.distinctBy(Episode::id)
        if (targets.isEmpty()) return
        scope.launch {
            playbackRepository.markAllEpisodesCompleted(
                episodes = targets,
                podcastId = currentState.podcast.id,
                podcastTitle = currentState.podcast.title,
                podcastImageUrl = currentState.podcast.imageUrl,
            )
        }
    }

    fun markEpisodesUncompleted(episodes: List<Episode>) {
        val targets = episodes.distinctBy(Episode::id)
        if (targets.isEmpty()) return
        scope.launch {
            playbackRepository.markAllEpisodesUncompleted(episodes = targets)
        }
    }

    fun playEpisodes(episodes: List<Episode>) {
        val currentState = uiState.value as? PodcastInfoUiState.Success ?: return
        val targets = episodes.distinctBy(Episode::id)
        if (targets.isEmpty()) return
        sessionTracker.played(targets.map(Episode::id))
        val entryPointContext =
            Bundle().apply {
                putString(
                    "entry_point",
                    PodcastInfoPlaybackSourceLogic.BULK_PLAY_ENTRY_POINT,
                )
                putString(
                    "source_entry_point",
                    PodcastInfoPlaybackSourceLogic.BULK_PLAY_SOURCE_ENTRY_POINT,
                )
            }
        scope.launch {
            playbackRepository.playQueue(
                episodes = targets,
                podcast = currentState.podcast,
                startIndex = 0,
                entryPoint = PlaybackEntryPoint.GENERIC,
                sourceContext = entryPointContext,
            )
        }
    }

    fun addEpisodesToQueue(episodes: List<Episode>) {
        val currentState = uiState.value as? PodcastInfoUiState.Success ?: return
        scope.launch {
            var added = 0
            try {
                episodes.distinctBy(Episode::id).forEach { episode ->
                    if (playbackRepository.addToQueue(episode = episode, podcast = currentState.podcast)) added++
                }
            } finally {
                // One event for the whole selection, with what was added even if a later add failed.
                if (added > 0) {
                    AnalyticsHelper.trackQueueModified(
                        action = "add",
                        podcastId = currentState.podcast.id,
                        queueSize = playbackRepository.playerState.value.queue.size,
                        source = "episode_selection",
                        itemsCount = added,
                    )
                }
            }
        }
    }

    fun toggleQueue(episode: Episode) {
        val currentState = uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            scope.launch {
                val isQueued = queuedEpisodeIds.value.contains(episode.id)
                if (isQueued) {
                    playbackRepository.removeFromQueue(episode.id)
                } else {
                    // User requested "Add to Queue" -> Insert as NEXT item
                    playbackRepository.addToQueueNext(episode, currentState.podcast)
                }
            }
        }
    }

    fun onPlayClick(episode: Episode) {
        Log.d(TAG, "onPlayClick triggered for: ${episode.title} (ID: ${episode.id})")
        sessionTracker.played(listOf(episode.id))
        val currentState = uiState.value as? PodcastInfoUiState.Success ?: return
        val playerState = playbackRepository.playerState.value
        val startsPlayback = playerState.currentEpisode?.id != episode.id || !playerState.isPlaying
        val retainedEntryPoint = PodcastInfoPlaybackSourceLogic.retainedEntryPoint(entryPoint)
        val (resolvedEntryPoint, sourceEntryPoint) = PodcastInfoPlaybackSourceLogic.resolvedPlaybackEntryPoints(entryPoint)
        val entryPointContext =
            Bundle().apply {
                putString("entry_point", resolvedEntryPoint)
                putString("source_entry_point", sourceEntryPoint)
            }

        if (startsPlayback && retainedEntryPoint != null) {
            AnalyticsHelper.trackVideoSpotlightPlayInitiated(
                podcastId = currentState.podcast.id,
                podcastName = currentState.podcast.title,
                episodeId = episode.id,
                episodeTitle = episode.title,
            )
        }

        scope.launch {
            if (playbackRepository.playerState.value.currentEpisode
                    ?.id == episode.id
            ) {
                Log.d(TAG, "Episode already active, toggling play/pause")
                playbackRepository.togglePlayPause(entryPointContext)
            } else {
                Log.d(TAG, "Starting new playback via queueManager")
                queueManager.playEpisode(
                    episode,
                    currentState.podcast,
                    sortParamFor(currentState.currentSort),
                    entryPointContext = entryPointContext,
                )
            }
        }
    }
}

/** Shown when Mark all as played or unplayed couldn't read the whole show and changed nothing. */
internal const val MARK_ALL_FAILED_MESSAGE = "Couldn’t load every episode, so none were marked. Check your connection and try again."

/** Shown when the show lists more episodes than boxlore can read at once, so trying again can't help. */
internal const val MARK_ALL_TOO_LONG_MESSAGE = "This show has too many episodes to mark them all, so none were marked."

/** What Mark all did ([markWholeShow]). */
internal enum class MarkAllOutcome {
    MARKED,

    /** A page couldn't be read; nothing was written. */
    FAILED,

    /** The show lists more episodes than the server's list pages through ([ShowListTooLongException]); nothing was written. */
    TOO_LONG,
}

/** Tells the page of [podcastId], if it still shows that show, that Mark all changed nothing and why. */
internal fun MutableStateFlow<PodcastInfoUiState>.showMarkAllFailed(podcastId: String, outcome: MarkAllOutcome = MarkAllOutcome.FAILED) {
    val latest = value as? PodcastInfoUiState.Success ?: return
    if (latest.podcast.id != podcastId) return
    val message = if (outcome == MarkAllOutcome.TOO_LONG) MARK_ALL_TOO_LONG_MESSAGE else MARK_ALL_FAILED_MESSAGE
    value = latest.copy(userMessage = message)
}

private fun logMarkAllFailure(error: Exception) {
    Log.w(TAG, "Mark all stopped: the whole show couldn't be read", error)
}

/**
 * Mark all as played or unplayed: reads the whole show with [loadWholeShow] before writing
 * anything, then hands every episode once to [write]. Never a part of the show passed off as all
 * of it: when any page fails nothing is written, and the answer says whether the show was too long
 * to read or a page failed.
 */
internal suspend fun markWholeShow(
    loadWholeShow: suspend () -> List<Episode>,
    onFailure: (Exception) -> Unit = {},
    write: suspend (List<Episode>) -> Unit,
): MarkAllOutcome {
    val episodes =
        try {
            loadWholeShow()
        } catch (e: CancellationException) {
            // A read that stopped under this one while it runs on: a failure.
            currentCoroutineContext().ensureActive()
            onFailure(e)
            return MarkAllOutcome.FAILED
        } catch (e: ShowListTooLongException) {
            onFailure(e)
            return MarkAllOutcome.TOO_LONG
        } catch (e: Exception) {
            onFailure(e)
            return MarkAllOutcome.FAILED
        }
    val targets = episodes.distinctBy(Episode::id)
    if (targets.isNotEmpty()) write(targets)
    return MarkAllOutcome.MARKED
}
