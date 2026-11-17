package cx.aswin.boxlore.fcm

import cx.aswin.boxlore.fcm.NewEpisodeFcmLogic.PostedEpisodeNotification

/**
 * The new-episode notifications this process has just posted or cancelled. Android lists a post or a
 * cancel in `activeNotifications` only after a short delay, so two pushes that arrive close together
 * would otherwise both see nothing showing: they would share a tap slot, skip the summary, or cancel
 * the summary while another show's notification is still on its way, which takes that one with it.
 *
 * Each change counts until Android lists it or [graceMillis] passes; after that a post Android does
 * not list is taken as dismissed. Not thread-safe: callers hold one lock. It is lost with the process,
 * which is fine because the delay lasts a fraction of a second.
 */
internal class RecentEpisodePosts(private val graceMillis: Long = GRACE_MILLIS) {
    private class Post(val notification: PostedEpisodeNotification, val atMillis: Long)

    private class SummaryChange(val showing: Boolean, val atMillis: Long)

    private val posts = HashMap<String, Post>()
    private val cancels = HashMap<String, Long>()
    private var summaryChange: SummaryChange? = null

    /**
     * The notifications to plan against: the [active] ones Android lists, with this process's posts it
     * does not list yet added and the ones this process cancelled but it still lists taken out.
     */
    fun merge(active: List<PostedEpisodeNotification>, nowMillis: Long): List<PostedEpisodeNotification> {
        expire(nowMillis)
        val listed = active.associateBy { it.podcastId }
        // A cancel has landed once Android stops listing the show; a post once it lists that exact post.
        cancels.keys.retainAll { it in listed }
        posts.values.removeAll { listed[it.notification.podcastId] == it.notification }
        val merged = active.filterNot { it.podcastId in cancels || it.podcastId in posts }.toMutableList()
        posts.values.mapTo(merged) { it.notification }
        return merged
    }

    /** Whether the summary is showing, counting a post or cancel of it Android does not list yet. */
    fun summaryShowing(activeListsSummary: Boolean, nowMillis: Long): Boolean {
        expire(nowMillis)
        val change = summaryChange ?: return activeListsSummary
        if (change.showing == activeListsSummary) summaryChange = null
        return change.showing
    }

    /**
     * Whether a release of [podcastId] other than [releaseKey] was just posted or the show's
     * notification was just cancelled, so a late update of [releaseKey] must not go up.
     */
    fun supersedes(podcastId: String, releaseKey: String, nowMillis: Long): Boolean {
        expire(nowMillis)
        if (podcastId in cancels) return true
        val pending = posts[podcastId]?.notification?.releaseKey ?: return false
        return pending != releaseKey
    }

    fun recordPost(notification: PostedEpisodeNotification, nowMillis: Long) {
        cancels.remove(notification.podcastId)
        posts[notification.podcastId] = Post(notification, nowMillis)
    }

    fun recordCancel(podcastId: String, nowMillis: Long) {
        posts.remove(podcastId)
        cancels[podcastId] = nowMillis
    }

    fun recordSummary(showing: Boolean, nowMillis: Long) {
        summaryChange = SummaryChange(showing, nowMillis)
    }

    fun clear() {
        posts.clear()
        cancels.clear()
        summaryChange = null
    }

    private fun expire(nowMillis: Long) {
        posts.values.removeAll { nowMillis - it.atMillis >= graceMillis }
        cancels.values.removeAll { nowMillis - it >= graceMillis }
        if (summaryChange?.let { nowMillis - it.atMillis >= graceMillis } == true) summaryChange = null
    }

    companion object {
        /** Long enough for Android to list a post, including the wait for a notification assistant. */
        const val GRACE_MILLIS = 3_000L
    }
}
