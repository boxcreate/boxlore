package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.feature.info.DirectFeedChipState

/** What pull-to-refresh should run on Podcast Info. */
internal object PodcastInfoPullRefreshLogic {
    enum class Target {
        RSS_CATALOG,
        DIRECT_FEED,
        PI_CATALOG,
        NONE,
    }

    fun target(
        isRss: Boolean,
        chip: DirectFeedChipState,
    ): Target =
        when {
            isRss -> Target.RSS_CATALOG
            chip == DirectFeedChipState.Updated -> Target.DIRECT_FEED
            chip == DirectFeedChipState.Fetching -> Target.NONE
            else -> Target.PI_CATALOG
        }

    fun shouldApply(currentPodcastId: String, targetPodcastId: String): Boolean =
        currentPodcastId == targetPodcastId

    fun shouldPersistLibraryTip(isSubscribed: Boolean, hasTip: Boolean): Boolean =
        isSubscribed && hasTip
}
