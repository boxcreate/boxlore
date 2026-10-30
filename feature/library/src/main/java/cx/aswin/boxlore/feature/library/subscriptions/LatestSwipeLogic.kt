package cx.aswin.boxlore.feature.library.subscriptions

import androidx.compose.material3.SwipeToDismissBoxValue
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast

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

internal fun latestQueueMessage(outcome: LatestQueueOutcome): String = when (outcome) {
    LatestQueueOutcome.Added -> "Added to queue"
    LatestQueueOutcome.AlreadyQueued -> "Already in your queue"
    LatestQueueOutcome.QueueFull -> "Your queue is full. Remove an episode to add this one."
}

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
