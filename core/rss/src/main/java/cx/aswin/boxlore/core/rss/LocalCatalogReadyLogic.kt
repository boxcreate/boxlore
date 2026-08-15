package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity

/** Per-show cutover gate: Room-only lists only when the full catalog is proven. */
object LocalCatalogReadyLogic {
    fun isReady(feed: LocalEpisodeFeedEntity?): Boolean {
        if (feed == null) return false
        if (!feed.feedUrl.startsWith("https://", ignoreCase = true)) return false
        if (feed.needsFullBackfill) return false
        if (feed.itemCount < feed.copiedExtrasCount) return false
        return feed.ready
    }

    fun listenerIdsResolve(
        catalogIds: Set<String>,
        listenerIds: Set<String>,
    ): Boolean =
        listenerIds.all { id ->
            id in catalogIds || id.toLongOrNull()?.let { it > 0L } == true
        }

    fun tipIsSafe(
        existingTipId: String?,
        existingPublishedDate: Long?,
        newTipId: String?,
        newPublishedDate: Long?,
    ): Boolean {
        if (existingTipId.isNullOrBlank()) return true
        if (existingTipId == newTipId) return true
        return newPublishedDate != null &&
            existingPublishedDate != null &&
            newPublishedDate > existingPublishedDate
    }

    fun isReadyToFlip(
        feedReady: Boolean,
        catalogIds: Set<String>,
        listenerIds: Set<String>,
        existingTipId: String?,
        existingPublishedDate: Long?,
        newTipId: String?,
        newPublishedDate: Long?,
    ): Boolean =
        feedReady &&
            listenerIdsResolve(catalogIds, listenerIds) &&
            tipIsSafe(existingTipId, existingPublishedDate, newTipId, newPublishedDate)
}
