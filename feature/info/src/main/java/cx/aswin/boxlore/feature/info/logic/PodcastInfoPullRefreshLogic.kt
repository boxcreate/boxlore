package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.feature.info.DirectFeedChipState

/** What pull-to-refresh should run on Podcast Info. */
internal object PodcastInfoPullRefreshLogic {
    enum class Target {
        RSS_CATALOG,
        DIRECT_FEED,
        NONE,
    }

    fun target(
        isRss: Boolean,
        chip: DirectFeedChipState,
    ): Target =
        when {
            isRss -> Target.RSS_CATALOG
            chip == DirectFeedChipState.Updated -> Target.DIRECT_FEED
            else -> Target.NONE
        }
}
