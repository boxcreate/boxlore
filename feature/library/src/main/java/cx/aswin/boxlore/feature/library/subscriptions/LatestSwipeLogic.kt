package cx.aswin.boxlore.feature.library.subscriptions

import androidx.annotation.StringRes
import androidx.compose.material3.SwipeToDismissBoxValue
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.library.R

/** What a swipe on a New Episodes row does. */
internal enum class LatestSwipeAction { AddToQueue, MarkPlayed }

/** Start to end (right in left-to-right layouts) queues the episode; end to start marks it played. */
internal fun latestSwipeAction(value: SwipeToDismissBoxValue): LatestSwipeAction? = when (value) {
    SwipeToDismissBoxValue.StartToEnd -> LatestSwipeAction.AddToQueue
    SwipeToDismissBoxValue.EndToStart -> LatestSwipeAction.MarkPlayed
    SwipeToDismissBoxValue.Settled -> null
}

/** Result of a swipe to queue, for its message. */
internal enum class LatestQueueOutcome { Added, AlreadyQueued, QueueFull }

/**
 * An add that was refused is "already queued" unless the queue is at [queueLimit]: the player
 * also refuses an episode queued under another of its IDs, which reads the same to the listener.
 */
internal fun latestQueueOutcome(added: Boolean, wasQueued: Boolean, queueSize: Int, queueLimit: Int): LatestQueueOutcome = when {
    added -> LatestQueueOutcome.Added
    wasQueued -> LatestQueueOutcome.AlreadyQueued
    queueSize >= queueLimit -> LatestQueueOutcome.QueueFull
    else -> LatestQueueOutcome.AlreadyQueued
}

@StringRes
internal fun latestQueueMessage(outcome: LatestQueueOutcome): Int = when (outcome) {
    LatestQueueOutcome.Added -> R.string.library_queue_added
    LatestQueueOutcome.AlreadyQueued -> R.string.library_queue_already_queued
    LatestQueueOutcome.QueueFull -> R.string.library_queue_full
}

/** Shows with a latest episode New Episodes can list: an archived one ([dismissedIds]) never is. */
internal fun latestWithoutArchived(podcasts: List<Podcast>, dismissedIds: Set<String>): List<Podcast> =
    podcasts.filter { podcast -> podcast.latestEpisode?.let { it.id !in dismissedIds } == true }

/**
 * Rows New Episodes shows. A row swiped to played (its id in [pendingPlayedIds]) counts as
 * played before it is saved, so with [hideCompleted] on it leaves at once.
 */
internal fun visibleLatestPodcasts(
    podcastsWithLatest: List<Podcast>,
    hideCompleted: Boolean,
    pendingPlayedIds: Set<String>,
): List<Podcast> = if (hideCompleted) {
    podcastsWithLatest.filter { podcast ->
        podcast.episodeStatus != EpisodeStatus.COMPLETED && podcast.latestEpisode?.id !in pendingPlayedIds
    }
} else {
    podcastsWithLatest
}

/** True once the list no longer shows [episodeId] as unplayed, so a held swipe can let go. */
internal fun isLatestShownPlayed(podcasts: List<Podcast>, episodeId: String): Boolean =
    podcasts.none { it.latestEpisode?.id == episodeId && it.episodeStatus != EpisodeStatus.COMPLETED }
