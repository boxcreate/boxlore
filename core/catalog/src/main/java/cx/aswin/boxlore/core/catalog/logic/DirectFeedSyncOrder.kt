package cx.aswin.boxlore.core.catalog.logic

/** Launch-sync ordering for opted-in publisher-feed refreshes. */
internal object DirectFeedSyncOrder {
    /**
     * Puts [preferredPodcastId] first when it is in [ids] so the open Home chip
     * is not stuck behind other catalog fetches.
     */
    fun prioritize(ids: List<String>, preferredPodcastId: String?,): List<String> {
        val preferred = preferredPodcastId?.takeIf { it in ids } ?: return ids
        return listOf(preferred) + ids.filter { it != preferred }
    }
}
