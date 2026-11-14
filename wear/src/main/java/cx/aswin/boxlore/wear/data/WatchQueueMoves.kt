package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.WatchEpisode

/**
 * Puts [episodes] at the front of Up next, in order: the queue that came along when playback
 * moved to the watch. One already waiting moves up instead of repeating, and [playingId] never
 * queues behind itself.
 */
internal fun WatchLibraryState.takeOver(episodes: List<WatchEpisode>, playingId: String? = null): WatchLibraryState {
    val taken = episodes.map(::merged).filterNot { it.id == playingId }.distinctBy { it.id }
    val ids = taken.map { it.id }
    val kept = taken.fold(this) { state, episode -> state.remember(episode) }
    return kept.copy(queue = ids + kept.queue.filterNot { it in ids })
}

/** Empties Up next: its episodes went to the phone together with the player. */
internal fun WatchLibraryState.handOver(): WatchLibraryState = copy(queue = emptyList()).forgetUnused()
