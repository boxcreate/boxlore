package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.feature.info.DirectFeedChipState
import cx.aswin.boxlore.feature.info.PodcastInfoUiState
import cx.aswin.boxlore.feature.info.ShowFeedStatus
import kotlinx.coroutines.flow.MutableStateFlow

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

/**
 * The page body's status line while the catalog refresh after subscribing runs, in place of a
 * spinner in the top-bar pill. The pill's own state comes back when the refreshed page remounts.
 */
internal object PodcastInfoPostSubscribeLogic {
    /**
     * Shows "Checking the feed for new episodes…" on [targetPodcastId]'s page while [refresh] runs,
     * then clears it however [refresh] ends: done, failed or cancelled. A line another read already
     * shows (a feed-first read, a fallback or an offline line) is left as it is.
     */
    suspend fun withStatusLine(
        uiState: MutableStateFlow<PodcastInfoUiState>,
        targetPodcastId: String,
        currentPodcastId: () -> String,
        refresh: suspend () -> Unit,
    ) {
        val state = (uiState.value as? PodcastInfoUiState.Success)?.takeIf { it.podcast.id == targetPodcastId }
        val showsLine = state != null && takesLine(state.feedStatus)
        if (state != null) uiState.value = started(state)
        try {
            refresh()
        } finally {
            if (showsLine) uiState.value = finished(uiState.value, currentPodcastId(), targetPodcastId)
        }
    }

    /** No line shows: the list is current, or it is the recent stored copy. */
    fun takesLine(status: ShowFeedStatus): Boolean = status == ShowFeedStatus.NONE || status == ShowFeedStatus.CACHED

    /** A followed show is never offered Missing episodes?, so the offer goes while the refresh runs. */
    fun started(state: PodcastInfoUiState.Success): PodcastInfoUiState.Success = state.copy(
        feedStatus = if (takesLine(state.feedStatus)) ShowFeedStatus.READING_NEWEST else state.feedStatus,
        directFeedChip = if (state.directFeedChip == DirectFeedChipState.Offer) DirectFeedChipState.Hidden else state.directFeedChip,
    )

    /** Clears the line on the same show only, and only while it still says the feed is being read. */
    fun finished(
        state: PodcastInfoUiState,
        currentPodcastId: String,
        targetPodcastId: String,
    ): PodcastInfoUiState {
        val success = state as? PodcastInfoUiState.Success ?: return state
        if (!PodcastInfoPullRefreshLogic.shouldApply(currentPodcastId, targetPodcastId) ||
            success.podcast.id != targetPodcastId ||
            success.feedStatus != ShowFeedStatus.READING_NEWEST
        ) {
            return state
        }
        return success.copy(feedStatus = ShowFeedStatus.NONE)
    }
}
