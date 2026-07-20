package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast

/** Queue-facing [PlaybackRepository] API (thin forwards to [PlaybackQueueCoordinator]). */
suspend fun PlaybackRepository.playQueue(
    episodes: List<Episode>,
    podcast: Podcast,
    startIndex: Int = 0,
    entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
    initialPositionMs: Long? = null,
    sourceContext: android.os.Bundle? = null,
) = queueCoordinator.playQueue(episodes, podcast, startIndex, entryPoint, initialPositionMs, sourceContext)

suspend fun PlaybackRepository.addToQueue(
    episode: Episode,
    podcast: Podcast,
    entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
): Boolean = queueCoordinator.addToQueue(episode, podcast, entryPoint)

suspend fun PlaybackRepository.addToQueueNext(
    episode: Episode,
    podcast: Podcast,
) = queueCoordinator.addToQueueNext(episode, podcast)

suspend fun PlaybackRepository.removeFromQueue(
    episodeId: String,
    deferSkipSignal: Boolean = false,
): PlaybackRepository.RemovedQueueItem? = queueCoordinator.removeFromQueue(episodeId, deferSkipSignal)

fun PlaybackRepository.confirmQueueRemoval(removed: PlaybackRepository.RemovedQueueItem) = queueCoordinator.confirmQueueRemoval(removed)

suspend fun PlaybackRepository.undoQueueRemoval(removed: PlaybackRepository.RemovedQueueItem) = queueCoordinator.undoQueueRemoval(removed)

fun PlaybackRepository.moveQueueItem(
    fromQueueIndex: Int,
    toQueueIndex: Int,
) = queueCoordinator.moveQueueItem(fromQueueIndex, toQueueIndex)

suspend fun PlaybackRepository.persistQueueOrder(
    movedEpisodeId: String? = null,
    fromQueueIndex: Int = -1,
    toQueueIndex: Int = -1,
) = queueCoordinator.persistQueueOrder(movedEpisodeId, fromQueueIndex, toQueueIndex)

suspend fun PlaybackRepository.hasNonLoreQueue(): Boolean = queueCoordinator.hasNonLoreQueue()

suspend fun PlaybackRepository.stopAndClearQueue() {
    sleepController.cancelTimer()
    queueCoordinator.stopAndClearQueue()
}

suspend fun PlaybackRepository.playEpisode(
    episode: Episode,
    podcast: Podcast,
    entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
    initialPositionMs: Long? = null,
) = queueCoordinator.playEpisode(episode, podcast, entryPoint, initialPositionMs)

suspend fun PlaybackRepository.playFromQueueIndex(
    episodeId: String,
    queueList: List<Episode>,
    podcast: Podcast,
) = queueCoordinator.playFromQueueIndex(episodeId, queueList, podcast)
