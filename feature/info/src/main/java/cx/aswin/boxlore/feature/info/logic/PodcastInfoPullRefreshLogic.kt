package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.PodcastInfoUiState

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
    ): Target = when {
        isRss -> Target.RSS_CATALOG
        chip == DirectFeedChipState.Updated -> Target.DIRECT_FEED
        chip == DirectFeedChipState.Fetching -> Target.NONE
        else -> Target.PI_CATALOG
    }

    fun shouldApply(currentPodcastId: String, targetPodcastId: String): Boolean = currentPodcastId == targetPodcastId

    fun shouldPersistLibraryTip(isSubscribed: Boolean, hasTip: Boolean): Boolean = isSubscribed && hasTip
}

/** Keeps a late async refresh from restoring subscription state captured before a toggle. */
internal object PodcastInfoAsyncResultLogic {
    fun preserveCurrentSubscription(
        current: PodcastInfoUiState.Success,
        result: PodcastInfoUiState.Success,
        targetPodcastId: String,
    ): PodcastInfoUiState.Success? {
        if (current.podcast.id != targetPodcastId || result.podcast.id != targetPodcastId) {
            return null
        }

        val subscriptionChanged = current.isSubscribed != result.isSubscribed
        return result.copy(
            podcast =
            result.podcast.copy(
                subscribedAt = current.podcast.subscribedAt,
                notificationsEnabled = current.podcast.notificationsEnabled,
                autoDownloadEnabled = current.podcast.autoDownloadEnabled,
            ),
            isSubscribed = current.isSubscribed,
            directFeedChip =
            if (subscriptionChanged) {
                current.directFeedChip
            } else {
                result.directFeedChip
            },
        )
    }
}
