package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.PodcastInfoUiState

/** What pull-to-refresh should run on Podcast Info. */
internal object PodcastInfoPullRefreshLogic {
    enum class Target {
        RSS_CATALOG,

        /** A feed-first show page: read the newest episodes into its stored copy (P4 PR 16). */
        FEED_FIRST,
        SUBSCRIBED_DIRECT_FEED,
        DIRECT_FEED,
        PI_CATALOG,
        NONE,
    }

    /** [feedFirst]: the page reads the show's own feed (P4 PR 16); it replaces the Missing episodes? path. */
    fun target(
        isRss: Boolean,
        isSubscribed: Boolean = false,
        chip: DirectFeedChipState,
        feedFirst: Boolean = false,
    ): Target = when {
        isRss -> Target.RSS_CATALOG
        feedFirst -> Target.FEED_FIRST
        chip == DirectFeedChipState.Fetching -> Target.NONE
        isSubscribed -> Target.SUBSCRIBED_DIRECT_FEED
        chip == DirectFeedChipState.Updated -> Target.DIRECT_FEED
        else -> Target.PI_CATALOG
    }

    fun target(
        isRss: Boolean,
        chip: DirectFeedChipState,
    ): Target = target(isRss = isRss, isSubscribed = false, chip = chip)

    fun shouldApply(currentPodcastId: String, targetPodcastId: String): Boolean = currentPodcastId == targetPodcastId

    fun shouldAcceptPageSort(
        initialSort: cx.aswin.boxlore.feature.info.EpisodeSort,
        activeSort: cx.aswin.boxlore.feature.info.EpisodeSort,
    ): Boolean = initialSort == activeSort

    fun shouldPersistLibraryTip(isSubscribed: Boolean, hasTip: Boolean): Boolean = isSubscribed && hasTip

    fun extractTip(outcome: cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome): cx.aswin.boxlore.core.model.Episode? =
        when (outcome) {
            is cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome.Success -> outcome.newest
            is cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome.Unchanged -> outcome.newest
            is cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome.Failure -> null
        }
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
                skipBeginningOverrideMs = current.podcast.skipBeginningOverrideMs,
                skipEndingOverrideMs = current.podcast.skipEndingOverrideMs,
                fallbackImageUrl = current.podcast.fallbackImageUrl ?: result.podcast.fallbackImageUrl,
                preferredSort = current.podcast.preferredSort ?: result.podcast.preferredSort,
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
