package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.analytics.AnalyticsHelper

/**
 * The show page's screen analytics: the screen-viewed event, and on exit how long the listener
 * stayed and what they did (followed or unfollowed, searched, sorted, played or opened episodes).
 */
internal class PodcastInfoSessionTracker(
    private val routeArgs: PodcastInfoRouteArgs,
) {
    private var sessionStartTime = System.currentTimeMillis()
    private var wasSubscribedAtStart: Boolean? = null
    private var didSubscribe = false
    private var didUnsubscribe = false
    private var didSearch = false
    private var didSortEpisodes = false
    private val playedEpisodes = mutableSetOf<String>()
    private val clickedEpisodes = mutableSetOf<String>()
    private var hasTrackedExit = false

    /** Keeps the follow state of the first page shown; later loads don't change it. */
    fun followStateAtOpen(isSubscribed: Boolean) {
        if (wasSubscribedAtStart == null) {
            wasSubscribedAtStart = isSubscribed
        }
    }

    fun subscriptionToggled(wasSubscribed: Boolean) {
        if (wasSubscribed) {
            didUnsubscribe = true
        } else {
            didSubscribe = true
        }
    }

    fun searched() {
        didSearch = true
    }

    fun sorted() {
        didSortEpisodes = true
    }

    fun played(episodeIds: Collection<String>) {
        playedEpisodes.addAll(episodeIds)
    }

    fun clicked(episodeId: String) {
        clickedEpisodes.add(episodeId)
    }

    fun screenViewed(
        podcastId: String,
        podcastName: String?,
    ) {
        AnalyticsHelper.trackPodcastInfoScreenViewed(
            podcastId = podcastId,
            podcastName = podcastName,
            entryPoint = routeArgs.entryPoint,
            genreFilter = routeArgs.genreFilter,
            scrollDepth = routeArgs.scrollDepth,
            searchQuery = routeArgs.searchQuery,
        )
    }

    fun screenResumed() {
        if (hasTrackedExit) {
            // Restart the session timer when coming back from background
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    /** Sends the session once per visit to [podcastId]'s page; nothing before a show loads. */
    fun screenExited(
        podcastId: String,
        podcastName: () -> String,
    ) {
        if (hasTrackedExit || podcastId.isEmpty()) return
        hasTrackedExit = true
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        AnalyticsHelper.trackPodcastInfoScreenSession(
            podcastId = podcastId,
            podcastName = podcastName(),
            timeSpentSeconds = timeSpent,
            wasSubscribed = wasSubscribedAtStart ?: false,
            didSubscribe = didSubscribe,
            didUnsubscribe = didUnsubscribe,
            didSearch = didSearch,
            didSortEpisodes = didSortEpisodes,
            episodesPlayedCount = playedEpisodes.size,
            episodesClickedCount = clickedEpisodes.size,
        )
    }
}
