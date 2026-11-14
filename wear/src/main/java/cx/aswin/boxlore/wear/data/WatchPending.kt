package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.PendingShowChange
import cx.aswin.boxlore.wear.model.WatchShow

/** Records a follow or unfollow of [show] for the phone; the newest change for a show replaces the last. */
internal fun WatchLibraryState.notingShow(show: WatchShow, subscribed: Boolean, nowMs: Long): WatchLibraryState {
    val next = revision + 1
    return copy(revision = next, pendingShows = pendingShows + (show.id to PendingShowChange(next, subscribed, nowMs, show)))
}

/**
 * Drops the changes everyone who needs them has confirmed. A change waits for the cloud while
 * the watch is signed in, and for the phone once one has linked. With neither, it waits for
 * the phone, the first that can take it.
 */
internal fun WatchLibraryState.confirmed(): WatchLibraryState {
    val forPhone = phoneLinked || !cloudLinked
    val waits = { revision: Long -> (forPhone && revision > phoneAcked) || (cloudLinked && revision > cloudAcked) }
    return copy(pendingShows = pendingShows.filterValues { waits(it.revision) }, pendingEpisodes = pendingEpisodes.filterValues(waits))
}

/** Records that the listening of [episodeId] changed on this watch. */
internal fun WatchLibraryState.notingEpisode(episodeId: String): WatchLibraryState {
    val next = revision + 1
    return copy(revision = next, pendingEpisodes = pendingEpisodes + (episodeId to next))
}
