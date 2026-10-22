package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import kotlinx.coroutines.CancellationException

/**
 * Shows whose own feed failed in a [SubscriptionForegroundSync] pass (P4a).
 *
 * Before, a saved HTTPS address was never re-checked, and a show with one never went to `/sync`,
 * so a dead feed got no new episodes at all. Now a saved address that looks dead
 * ([DirectFeedResolveResult.addressGone]) is re-checked against the server's confirmed address, at
 * most once a day per show, and a new address is read at once. Shows still failing take their
 * latest episode from `/sync` (which serves stale shows from their live feed), through the ordinary
 * save that never replaces a newer tip. Never for `rss:` shows: their address is their identity and
 * notification consent.
 */
internal object DirectFeedRecovery {
    private const val TAG = "DirectFeedRecovery"

    /**
     * @param readAgain reads one show's own feed again and saves its tip (after a new address)
     * @param syncFromServer asks `/sync` for these shows and saves their tips
     */
    suspend fun recover(
        failed: Map<String, DirectFeedResolveResult>,
        directFeed: DirectFeedSyncSeams,
        readAgain: suspend (String) -> DirectFeedResolveResult?,
        syncFromServer: suspend (List<String>) -> Unit,
    ) {
        if (failed.isEmpty()) return
        val stillFailing = mutableListOf<String>()
        for ((id, result) in failed) {
            if (id.startsWith("rss:")) continue
            val retried = if (result.addressGone && recheck(directFeed, id)) readAgain(id) else null
            if (retried == null || retried.failed) stillFailing += id
        }
        if (stillFailing.isEmpty() || !directFeed.syncFallbackEnabled()) return
        Log.d(TAG, "Asking /sync for ${stillFailing.size} shows whose own feed failed")
        syncFromServer(stillFailing)
    }

    private suspend fun recheck(directFeed: DirectFeedSyncSeams, id: String): Boolean = try {
        directFeed.recheckAddress(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Feed address re-check failed for $id", e)
        false
    }

    /** The production [DirectFeedSyncSeams.recheckAddress]: [recheckGoneFeedAddress] behind its switch. */
    fun addressRecheck(
        feedFixes: FeedFixToggles,
        subscriptionRepository: SubscriptionRepository,
        podcastRepository: PodcastRepository,
        localEpisodeCatalog: LocalEpisodeCatalogPort?,
    ): suspend (String) -> Boolean = { id ->
        feedFixes.addressRecheck() &&
            recheckGoneFeedAddress(
                id = id,
                now = System.currentTimeMillis(),
                savedFeedUrl = subscriptionRepository.getPodcastEntity(id)?.feedUrl,
                lastLookupAt = { localEpisodeCatalog?.lastFeedUrlLookupAt(id) ?: 0L },
                markLookup = { at -> localEpisodeCatalog?.markFeedUrlLookup(id, at) },
                lookupFeedUrl = { podcastRepository.getPodcastDetails(id)?.feedUrl },
                saveFeedUrl = { url -> subscriptionRepository.ensureHttpsFeedUrl(id, url) },
            )
    }

    /**
     * Re-checks a dead saved HTTPS address against the server's confirmed one (`/podcast`, which
     * returns the registry's address), at most once per [LocalEpisodeCatalogRepository.FEED_URL_LOOKUP_INTERVAL_MS]
     * per show. The lookup counts only once the server answers, so an offline pass tries again.
     * @return true when a different HTTPS address was saved
     */
    @Suppress("LongParameterList")
    suspend fun recheckGoneFeedAddress(
        id: String,
        now: Long,
        savedFeedUrl: String?,
        lastLookupAt: suspend () -> Long,
        markLookup: suspend (Long) -> Unit,
        lookupFeedUrl: suspend () -> String?,
        saveFeedUrl: suspend (String) -> Unit,
    ): Boolean {
        if (id.startsWith("rss:")) return false
        val current = TrackedPodcastRtdbLogic.httpsFeedUrl(savedFeedUrl) ?: return false
        val last = lastLookupAt()
        if (last > 0L && now - last < LocalEpisodeCatalogRepository.FEED_URL_LOOKUP_INTERVAL_MS) return false
        val confirmed = lookupFeedUrl() ?: return false
        markLookup(now)
        val https = TrackedPodcastRtdbLogic.httpsFeedUrl(confirmed) ?: return false
        if (https == current) return false
        saveFeedUrl(https)
        return true
    }
}
