package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** How long a saved swipe-to-played waits for the list to show it before letting go. */
internal const val PLAYED_SETTLE_TIMEOUT_MS = 3_000L

/** What New Episodes needs from the player's queue. */
internal class LatestQueue(
    val queuedIds: () -> List<String>,
    val add: suspend (Episode, Podcast) -> Boolean,
    /** Removes an undone add without counting it as turning the episode down. */
    val removeUndone: suspend (episodeId: String) -> Unit,
    val limit: Int,
)

/**
 * New Episodes pull to refresh and swipes. One refresh runs at a time. A swipe to played is held
 * (the row reads as played, nothing saved) until its Undo closes, because marking played clears
 * the listening position; it is let go once the list shows it played, or after [settleTimeoutMs].
 */
internal class LatestEpisodeActions(
    private val scope: CoroutineScope,
    private val refreshNow: suspend () -> Unit,
    private val queue: LatestQueue,
    private val markPlayed: suspend (Episode, Podcast) -> Unit,
    /** Returns once the list shows [episodeId] as played. */
    private val awaitShownPlayed: suspend (episodeId: String) -> Unit,
    private val settleTimeoutMs: Long = PLAYED_SETTLE_TIMEOUT_MS,
) {
    private val refreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = refreshing.asStateFlow()

    private val pendingPlayed = MutableStateFlow<Set<String>>(emptySet())
    val pendingPlayedIds: StateFlow<Set<String>> = pendingPlayed.asStateFlow()

    fun refresh() {
        if (!refreshing.compareAndSet(expect = false, update = true)) return
        scope.launch {
            try {
                refreshNow()
            } finally {
                refreshing.value = false
            }
        }
    }

    suspend fun addToQueue(episode: Episode, podcast: Podcast): LatestQueueOutcome {
        val wasQueued = episode.id in queue.queuedIds()
        val added = !wasQueued && queue.add(episode, podcast)
        return latestQueueOutcome(added = added, wasQueued = wasQueued, queueSize = queue.queuedIds().size, queueLimit = queue.limit)
    }

    fun undoAddToQueue(episodeId: String) {
        scope.launch { queue.removeUndone(episodeId) }
    }

    fun holdPlayed(episodeId: String) {
        pendingPlayed.update { it + episodeId }
    }

    fun undoPlayed(episodeId: String) {
        pendingPlayed.update { it - episodeId }
    }

    fun commitPlayed(episode: Episode, podcast: Podcast) {
        if (episode.id !in pendingPlayed.value) return
        scope.launch {
            try {
                withContext(NonCancellable) { markPlayed(episode, podcast) }
                withTimeoutOrNull(settleTimeoutMs) { awaitShownPlayed(episode.id) }
            } finally {
                pendingPlayed.update { it - episode.id }
            }
        }
    }
}
