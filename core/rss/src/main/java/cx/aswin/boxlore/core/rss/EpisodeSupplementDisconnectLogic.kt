package cx.aswin.boxlore.core.rss

/**
 * Whether a PI show should be auto-opted into direct-feed supplements.
 *
 * Disconnect = publisher feed has episodes absent from the PI baseline, and/or
 * the live feed tip is newer than the newest baseline tip.
 */
object EpisodeSupplementDisconnectLogic {
    fun shouldOptIn(feedOnlyCount: Int, newestFeedPublishedDate: Long, newestBaselinePublishedDate: Long,): Boolean {
        if (feedOnlyCount > 0) return true
        if (newestFeedPublishedDate <= 0L) return false
        if (newestBaselinePublishedDate <= 0L) return newestFeedPublishedDate > 0L
        return newestFeedPublishedDate > newestBaselinePublishedDate
    }
}
