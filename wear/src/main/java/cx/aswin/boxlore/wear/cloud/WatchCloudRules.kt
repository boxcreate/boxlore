package cx.aswin.boxlore.wear.cloud

import cx.aswin.boxlore.core.network.model.SyncPullResponse
import cx.aswin.boxlore.core.network.model.SyncPushRequest
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import cx.aswin.boxlore.wear.data.WatchLibraryState
import cx.aswin.boxlore.wear.data.confirmed
import cx.aswin.boxlore.wear.link.WatchLinkRules
import cx.aswin.boxlore.wear.link.syncRow
import cx.aswin.boxlore.wear.model.WatchShow

/** One push to the cloud, and the revision it brings the cloud up to. */
internal class CloudPush(val request: SyncPushRequest, val covered: Long)

/**
 * How the watch's library and the listener's account meet. The rows are the ones the phone
 * exchanges with the cloud, and listening merges by the rules of the phone link
 * ([WatchLinkRules]). The cloud sends only what changed since the last pull, so its follows and
 * unfollows are applied one by one instead of replacing the watch's list.
 */
internal object WatchCloudRules {
    /** Listening changes sent at once; the rest follow with the next sync. */
    const val MAX_PUSHED_HISTORY = 100

    /** A first pull carries an account's whole history; the watch keeps only the most recent of it. */
    const val MAX_PULLED_HISTORY = 200

    /** What the cloud still has to hear from this watch; null when nothing waits. */
    fun push(state: WatchLibraryState, now: Long, maxHistory: Int = MAX_PUSHED_HISTORY): CloudPush? {
        val waiting = state.pendingEpisodes.entries.filter { it.value > state.cloudAcked }.sortedBy { it.value }
        val sent = waiting.take(maxHistory)
        // With more waiting than fits, this push covers only up to the last row it carries.
        val covered = if (waiting.size > sent.size) sent.last().value else state.revision
        val shows = state.pendingShows.values.filter { it.revision > state.cloudAcked && it.revision <= covered }.sortedBy { it.revision }
        if (sent.isEmpty() && shows.isEmpty()) return null
        val request =
            SyncPushRequest(
                subscriptions = shows.map { it.syncRow() },
                history = sent.mapNotNull { WatchLinkRules.historyRow(state, it.key) },
                clientTimestamp = now,
            )
        return CloudPush(request, covered)
    }

    /** The library after the cloud took a push that [covered] this revision. */
    fun pushed(state: WatchLibraryState, covered: Long): WatchLibraryState = state.copy(cloudAcked = maxOf(state.cloudAcked, covered)).confirmed()

    /** Shows the cloud says are followed that the watch has no name or artwork for yet. */
    fun unknownShows(state: WatchLibraryState, pull: SyncPullResponse): List<String> =
        pull.subscriptions.filter { it.isSubscribed && applies(state, it) && !state.isSubscribed(it.podcastId) }.map { it.podcastId }.distinct()

    /**
     * The library after a pull. [shows] are the followed shows the watch looked up for it. A show
     * that could not be looked up leaves the pull's time where it was, so the next pull names the
     * show again. [playingId] is the episode in the watch's player, which the cloud never moves.
     */
    fun withCloud(state: WatchLibraryState, pull: SyncPullResponse, shows: Map<String, WatchShow>, playingId: String?): WatchLibraryState {
        var missing = false
        var followed = state.shows
        for (row in pull.subscriptions.sortedBy { it.updatedAt }) {
            val known = followed.any { it.id == row.podcastId }
            val named = shows[row.podcastId]
            when {
                !applies(state, row) -> Unit
                !row.isSubscribed -> followed = followed.filterNot { it.id == row.podcastId }
                known -> Unit
                named != null -> followed = listOf(named.copy(subscribed = true)) + followed
                else -> missing = true
            }
        }
        var next = state.copy(shows = followed)
        for (row in pull.history.sortedByDescending { it.lastPlayedAt }.take(MAX_PULLED_HISTORY)) next = WatchLinkRules.withRow(next, row, playingId)
        return WatchLinkRules.settled(next).copy(cloudSince = if (missing) state.cloudSince else pull.syncedAt)
    }

    /** A follow or unfollow made on this watch after the cloud's row is still on its way up; it stands. */
    private fun applies(state: WatchLibraryState, row: UserSubscriptionSyncDto): Boolean =
        state.pendingShows[row.podcastId]?.let { it.atMillis < row.updatedAt } ?: true
}
