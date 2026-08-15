package cx.aswin.boxlore.core.catalog.logic

/**
 * Cooldown / in-flight rules for [cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync].
 *
 * A [lastCompletedAtMs] below 0 means no successful pass yet, so Subscriptions-first
 * cold starts must still hit the network instead of showing Room cache only.
 * 0 is a valid completion timestamp (test virtual clocks start there).
 */
internal object SubscriptionForegroundSyncLogic {
    const val NEVER_COMPLETED_MS = -1L

    fun shouldSkipRefresh(
        inFlight: Boolean,
        lastCompletedAtMs: Long,
        nowMs: Long,
        cooldownMs: Long,
    ): Boolean {
        if (inFlight) return true
        if (lastCompletedAtMs < 0L) return false
        return nowMs - lastCompletedAtMs < cooldownMs
    }

    /** True RSS library rows keep their own catalog path; blank ids are not ingest targets. */
    fun shouldRequestCatalogIngest(podcastId: String): Boolean = podcastId.isNotBlank() && !podcastId.startsWith("rss:")
}
