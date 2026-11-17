package cx.aswin.boxlore.feature.home.logic

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Binding between Home chip selection and [cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync]. */
internal object HomeForegroundSyncLogic {
    fun preferredFeedPodcastId(selectedPodcastId: String?): String? = selectedPodcastId

    fun shouldReloadSelectedChip(
        refreshedPodcastId: String,
        selectedPodcastId: String?,
    ): Boolean = selectedPodcastId != null && refreshedPodcastId == selectedPodcastId

    /**
     * Bumps [reloadVersion] once a chip's own refresh or feed read for [readPodcastId] is done, only
     * when that show is still the open chip in [selectedPodcastId]. A read that finishes after a move
     * to another chip leaves that chip's episode load alone.
     */
    fun reloadIfStillOpen(
        readPodcastId: String,
        selectedPodcastId: StateFlow<String?>,
        reloadVersion: MutableStateFlow<Long>,
    ) {
        if (shouldReloadSelectedChip(readPodcastId, selectedPodcastId.value)) {
            reloadVersion.update { it + 1L }
        }
    }
}
