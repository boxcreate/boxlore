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
 * most once a day per show. The new address is read at once and saved only when that read works,
 * so a server that offers another dead address (say, Podcast Index's own) never replaces the
 * saved one. Shows still failing take their
 * latest episode from `/sync` (which serves stale shows from their live feed), through the ordinary
 * save that never replaces a newer tip. Never for `rss:` shows: their address is their identity and
 * notification consent.
 */
internal object DirectFeedRecovery {
    private const val TAG = "DirectFeedRecovery"

    /**
     * @param readAgain reads one show's feed at this address and saves its tip
     * @param syncFromServer asks `/sync` for these shows and saves their tips
     */
    suspend fun recover(
        failed: Map<String, DirectFeedResolveResult>,
        directFeed: DirectFeedSyncSeams,
        readAgain: suspend (id: String, feedUrl: String) -> DirectFeedResolveResult?,
        syncFromServer: suspend (List<String>) -> Unit,
    ) {
        if (failed.isEmpty()) return
        val stillFailing = mutableListOf<String>()
        for ((id, result) in failed) {
            if (id.startsWith("rss:")) continue
            val candidate = if (result.addressGone) recheck(directFeed, id) else null
            val retried = candidate?.let { readAgain(id, it) }
            if (candidate != null && retried != null && !retried.failed) {
                directFeed.saveAddress(id, candidate)
            } else {
                stillFailing += id
            }
        }
        if (stillFailing.isEmpty() || !directFeed.syncFallbackEnabled()) return
        Log.d(TAG, "Asking /sync for ${stillFailing.size} shows whose own feed failed")
        syncFromServer(stillFailing)
    }

    private suspend fun recheck(directFeed: DirectFeedSyncSeams, id: String): String? = try {
        directFeed.recheckAddress(id)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Feed address re-check failed for $id", e)
        null
    }

    /** The production [DirectFeedSyncSeams.recheckAddress]: [recheckGoneFeedAddress] behind its switch. */
    fun addressRecheck(
        feedFixes: FeedFixToggles,
        subscriptionRepository: SubscriptionRepository,
        podcastRepository: PodcastRepository,
        localEpisodeCatalog: LocalEpisodeCatalogPort?,
    ): suspend (String) -> String? = { id ->
        if (!feedFixes.addressRecheck()) {
            null
        } else {
            recheckGoneFeedAddress(
                id = id,
                now = System.currentTimeMillis(),
                savedFeedUrl = subscriptionRepository.getPodcastEntity(id)?.feedUrl,
                lastLookupAt = { localEpisodeCatalog?.lastFeedUrlLookupAt(id) ?: 0L },
                markLookup = { at -> localEpisodeCatalog?.markFeedUrlLookup(id, at) },
                lookupFeedUrl = { podcastRepository.getPodcastDetails(id)?.feedUrl },
            )
        }
    }

    /**
     * Re-checks a dead saved HTTPS address against the server's confirmed one (`/podcast`, which
     * returns the registry's address), at most once per [LocalEpisodeCatalogRepository.FEED_URL_LOOKUP_INTERVAL_MS]
     * per show. The lookup counts only once the server answers, so an offline pass tries again.
     * Nothing is saved here: [recover] saves the address only after it reads.
     * @return a different HTTPS address to try, or null
     */
    suspend fun recheckGoneFeedAddress(
        id: String,
        now: Long,
        savedFeedUrl: String?,
        lastLookupAt: suspend () -> Long,
        markLookup: suspend (Long) -> Unit,
        lookupFeedUrl: suspend () -> String?,
    ): String? {
        if (id.startsWith("rss:")) return null
        val current = TrackedPodcastRtdbLogic.httpsFeedUrl(savedFeedUrl) ?: return null
        val last = lastLookupAt()
        if (last > 0L && now - last < LocalEpisodeCatalogRepository.FEED_URL_LOOKUP_INTERVAL_MS) return null
        val confirmed = lookupFeedUrl() ?: return null
        markLookup(now)
        val https = TrackedPodcastRtdbLogic.httpsFeedUrl(confirmed) ?: return null
        return https.takeIf { it != current }
    }
}
