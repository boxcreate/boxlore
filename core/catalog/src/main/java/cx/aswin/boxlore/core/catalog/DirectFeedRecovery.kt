package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import kotlinx.coroutines.CancellationException

/**
 * Another address to try for a show whose saved one failed, and the path that offered it
 * ([FeedUrlCorrectionStep.FEED_REPORT] or the daily check's `/podcast` fallback,
 * [FeedUrlCorrectionStep.DAILY_CHECK]). [replacing] is the saved address that failed: the candidate
 * replaces only that one, so a newer address saved meanwhile (a cloud pull, the daily check) stays.
 */
internal data class AddressCandidate(val feedUrl: String, val step: FeedUrlCorrectionStep, val replacing: String? = null) {
    /** The address change saved once [feedUrl] read successfully. */
    fun change(podcastId: String, nowMillis: Long): FeedAddressChange = FeedAddressChange(
        podcastId = podcastId,
        feedUrl = feedUrl,
        source = if (step == FeedUrlCorrectionStep.FEED_REPORT) FeedUrlSources.FEED_REPORT else FeedUrlSources.PODCAST_LOOKUP,
        checkedAt = nowMillis,
        onlyReplacing = replacing,
        reason = FeedUrlCorrectionReason.DEAD,
        step = step,
    )
}

/**
 * Shows whose own feed failed in a [SubscriptionForegroundSync] pass (P4a; reports since P4 PR 14).
 *
 * Every failed read of a Podcast Index show is reported to the API ([DirectFeedSyncSeams.reportFeed],
 * `/v2/feed-report`, limited per show and never while offline), which queues a re-check there. When
 * the saved address looks dead ([DirectFeedResolveResult.addressGone]) another address is tried:
 * the one the report answered with, else the server's confirmed address from `/podcast`
 * ([DirectFeedSyncSeams.recheckAddress]), which runs only when the daily address check hasn't
 * reached the show today (they share one daily stamp per show). The new address is read at once and
 * saved only when that read works, so a server that offers another dead address (say, Podcast
 * Index's own) never replaces the saved one. Shows still failing take their latest episode from
 * `/sync` (which serves stale shows from their live feed), through the ordinary save that never
 * replaces a newer tip. Never for `rss:` shows: their address is their identity and notification consent.
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
            val candidate = candidate(directFeed, id, result)
            val retried = candidate?.let { readAgain(id, it.feedUrl) }
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

    /** Reports the failed read, then picks another address only when the saved one looks dead. */
    private suspend fun candidate(directFeed: DirectFeedSyncSeams, id: String, result: DirectFeedResolveResult): AddressCandidate? {
        val reported = guarded("Feed report", id) { directFeed.reportFeed(id, result.reportStatus) }
        if (!result.addressGone) return null
        reported?.let { return AddressCandidate(it, FeedUrlCorrectionStep.FEED_REPORT, result.readFeedUrl) }
        return guarded("Feed address re-check", id) { directFeed.recheckAddress(id) }
            ?.let { AddressCandidate(it, FeedUrlCorrectionStep.DAILY_CHECK, result.readFeedUrl) }
    }

    private suspend fun guarded(what: String, id: String, block: suspend () -> String?): String? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "$what failed for $id", e)
        null
    }

    /** A failed read remembers the saved address it used, so a recovered one replaces only that address. */
    fun withReadAddress(result: DirectFeedResolveResult, feedUrl: String?): DirectFeedResolveResult =
        if (result.failed) result.copy(readFeedUrl = feedUrl?.trim()?.takeIf(String::isNotEmpty)) else result

    /**
     * The production read of [FeedAddressDailyCheck] (P4 PR 14): the show's catalog is refreshed
     * from the registry's address before that address is saved, and the address counts as readable
     * when the read worked. Without a local catalog nothing is read, so nothing is swapped.
     */
    fun addressRead(subscriptionRepository: SubscriptionRepository, catalog: LocalEpisodeCatalogPort?): suspend (String, String) -> Boolean = { id, url ->
        val row = subscriptionRepository.getPodcastEntity(id)
        if (catalog == null || row == null) {
            false
        } else {
            val meta = DirectFeedTipMeta(feedUrl = url, title = row.title, imageUrl = row.imageUrl, genre = row.genre, artist = row.author, knownTip = row.latestEpisode)
            !SubscriptionForegroundSync.resolveCatalogRefresh(id, meta, catalog).failed
        }
    }

    /**
     * The production [DirectFeedSyncSeams.recheckAddress]: [recheckGoneFeedAddress] behind its
     * switch, stamped with the daily address check's per-show stamp (`podcasts.feedUrlCheckedAt`).
     */
    fun addressRecheck(
        feedFixes: FeedFixToggles,
        subscriptionRepository: SubscriptionRepository,
        podcastRepository: PodcastRepository,
    ): suspend (String) -> String? = { id ->
        if (!feedFixes.addressRecheck()) {
            null
        } else {
            val row = subscriptionRepository.getPodcastEntity(id)
            recheckGoneFeedAddress(
                id = id,
                now = System.currentTimeMillis(),
                savedFeedUrl = row?.feedUrl,
                lastLookupAt = { row?.feedUrlCheckedAt ?: 0L },
                markLookup = { at -> subscriptionRepository.markFeedAddressChecked(id, at) },
                lookupFeedUrl = { podcastRepository.getPodcastDetails(id)?.feedUrl },
            )
        }
    }

    /**
     * Re-checks a dead saved HTTPS address against the server's confirmed one (`/podcast`, which
     * returns the registry's address), at most once per [FeedAddressDailyCheck.CHECK_INTERVAL_MS]
     * per show, sharing the daily check's stamp. The lookup counts only once the server answers, so
     * an offline pass tries again. Nothing is saved here: [recover] saves the address only after it reads.
     * This is the P4a per-show check, kept as the fallback of [FeedAddressDailyCheck] for shows
     * it couldn't reach (`/v2/shows/feeds` failed, or the registry was unavailable).
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
        if (last in 1..now && now - last < FeedAddressDailyCheck.CHECK_INTERVAL_MS) return null
        val confirmed = lookupFeedUrl() ?: return null
        markLookup(now)
        val https = TrackedPodcastRtdbLogic.httpsFeedUrl(confirmed) ?: return null
        return https.takeIf { it != current }
    }
}
