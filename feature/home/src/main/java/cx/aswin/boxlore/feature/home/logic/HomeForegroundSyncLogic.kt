package cx.aswin.boxlore.feature.home.logic

/** Binding between Home chip selection and [cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync]. */
internal object HomeForegroundSyncLogic {
    fun preferredFeedPodcastId(selectedPodcastId: String?): String? = selectedPodcastId

    fun shouldReloadSelectedChip(
        refreshedPodcastId: String,
        selectedPodcastId: String?,
    ): Boolean = selectedPodcastId != null && refreshedPodcastId == selectedPodcastId
}
