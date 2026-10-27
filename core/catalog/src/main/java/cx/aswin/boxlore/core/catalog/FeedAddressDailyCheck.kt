package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2FetchVia
import cx.aswin.boxlore.core.network.model.V2ShowAddress
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsRequest
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsResponse
import kotlinx.coroutines.CancellationException
import retrofit2.awaitResponse

/**
 * The daily address check of followed shows: asks `/v2/shows/feeds` for every followed Podcast
 * Index show not checked in the last day, in chunks of at most [V2ShowsFeedsRequest.MAX_IDS], and
 * swaps a saved address only for a confirmed move ([confirmedMove]) through [FeedAddressUpdater],
 * and only once the phone has read the new address ([readsAt]), the rule every dead-address path
 * follows: an address the phone can't read (blocked, an old TLS stack, a wrong registry row) never
 * replaces one it can, and the next day's check tries again.
 *
 * Each show checked is stamped (`podcasts.feedUrlCheckedAt`) once the registry answers for it (a
 * swap writes the stamp with the address, so a show is never stamped and left unswapped). The
 * per-show `/podcast` re-check of a dead address (`DirectFeedRecovery`) reads the same stamp, so it
 * runs only for shows this check couldn't reach (the call failed, or `registryAvailable` was false)
 * and a show is never checked twice a day. Rows the phone changed and hasn't pushed yet
 * ([changedOnPhone]) wait until they are clean. With [enabled] off (`address-check-off`) the check
 * pauses without stamping anything.
 *
 * Replaces the P4a per-show `/podcast` re-check, which stays as the fallback.
 * Added in P4 PR 14 of the own-episode-IDs plan (plan section 11, followed shows: daily check; decision 2).
 * COMPAT(own-ids): followed shows stay reachable. Permanent: never remove (MIGRATION.md §5).
 */
class FeedAddressDailyCheck(
    private val followedShows: suspend () -> List<PodcastEntity>,
    private val fetchAddresses: suspend (List<Long>) -> V2ShowsFeedsResponse?,
    private val readsAt: suspend (podcastId: String, feedUrl: String) -> Boolean,
    private val replaceAddress: suspend (FeedAddressChange) -> FeedAddressUpdate,
    private val markChecked: suspend (podcastId: String, atMillis: Long) -> Unit,
    private val enabled: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    /** What one [run] did. */
    data class Outcome(val checked: Int, val swapped: Int, val unchecked: Int) {
        companion object {
            val NOTHING = Outcome(checked = 0, swapped = 0, unchecked = 0)
        }
    }

    suspend fun run(): Outcome {
        if (!enabled()) return Outcome.NOTHING
        val now = nowMillis()
        val due =
            followedShows()
                .filter { isDue(it, now) }
                .mapNotNull { row -> V2EpisodeIds.parseShowId(row.podcastId)?.let { it to row } }
                .toMap()
        if (due.isEmpty()) return Outcome.NOTHING
        var outcome = Outcome.NOTHING
        for (chunk in due.keys.chunked(V2ShowsFeedsRequest.MAX_IDS)) {
            val answer = fetch(chunk)
            outcome =
                if (answer == null || !answer.registryAvailable) {
                    outcome.copy(unchecked = outcome.unchecked + chunk.size)
                } else {
                    val swapped = apply(answer.results, due, now)
                    outcome.copy(checked = outcome.checked + answer.results.count { it.id in due }, swapped = outcome.swapped + swapped)
                }
        }
        Log.d(TAG, "Daily address check: $outcome")
        return outcome
    }

    private suspend fun fetch(ids: List<Long>): V2ShowsFeedsResponse? = try {
        fetchAddresses(ids)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Address check of ${ids.size} shows failed", e)
        null
    }

    private suspend fun apply(results: List<V2ShowAddress>, due: Map<Long, PodcastEntity>, now: Long): Int =
        results.count { result -> due[result.id]?.let { row -> applyOne(row, result, now) } == true }

    /** Swaps or stamps one show the registry answered for; true when its address was swapped. */
    private suspend fun applyOne(row: PodcastEntity, result: V2ShowAddress, now: Long): Boolean {
        noteNamespace(row, result)
        val target = confirmedMove(row, result)
        val swapped =
            target != null &&
                readable(row.podcastId, target) &&
                replaceAddress(change(row, result, target, now)) == FeedAddressUpdate.UPDATED
        // A swap stamps the show with its address; anything else is stamped here, after the swap had its chance.
        if (!swapped) markChecked(row.podcastId, now)
        return swapped
    }

    private fun change(row: PodcastEntity, result: V2ShowAddress, target: String, now: Long) = FeedAddressChange(
        podcastId = row.podcastId,
        feedUrl = target,
        source = result.feedSource ?: FeedUrlSources.REGISTRY,
        checkedAt = now,
        onlyReplacing = row.feedUrl,
        reason = FeedUrlCorrectionReason.REGISTRY,
        step = FeedUrlCorrectionStep.DAILY_CHECK,
    )

    private suspend fun readable(podcastId: String, feedUrl: String): Boolean {
        val read =
            try {
                readsAt(podcastId, feedUrl)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Reading the registry address of $podcastId failed", e)
                false
            }
        if (!read) Log.i(TAG, "Show $podcastId keeps its address: the registry's can't be read from this phone")
        return read
    }

    /** The phone keeps its own namespace (decision D12); a different one is only logged. */
    private fun noteNamespace(row: PodcastEntity, result: V2ShowAddress) {
        val served = result.validEpNs ?: return
        if (row.epNs != null && row.epNs != served) Log.w(TAG, "Show ${row.podcastId} has namespace ${row.epNs} here, $served on the server")
    }

    companion object {
        private const val TAG = "FeedAddressDailyCheck"

        /** How long a check holds for one show. */
        const val CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L

        /** A followed Podcast Index show with a saved address, not checked in the last day, not waiting to be pushed. */
        internal fun isDue(row: PodcastEntity, now: Long): Boolean {
            val checkable = row.isSubscribed && !row.isRss && !row.feedUrl.isNullOrBlank()
            if (!checkable || changedOnPhone(row)) return false
            val last = row.feedUrlCheckedAt
            return last <= 0L || last > now || now - last >= CHECK_INTERVAL_MS
        }

        /**
         * The follow carries a change made on this phone that the account hasn't received yet: an
         * account copy exists ([PodcastEntity.syncedAt]) and the row is dirty. Its address is left
         * as it is until the push (decision 2: confirmed moves only, on follows not changed on the
         * phone). Follows never synced to an account are this phone's alone and are checked.
         */
        internal fun changedOnPhone(row: PodcastEntity): Boolean = row.isDirty && row.syncedAt > 0L

        /**
         * The registry's address when it is a confirmed move away from the saved one: the registry
         * flags the show for address repair, the address is the registry's own (`registry:` source),
         * the phone can read it directly (HTTPS), it differs from the saved one, and it isn't the
         * address the saved one replaced, whatever replaced it (a feed move, dead-address recovery
         * or a cloud pull): going back would swap back and forth. `fetchUrl: null` keeps the saved
         * address. The caller still reads the address before it is saved.
         */
        internal fun confirmedMove(row: PodcastEntity, result: V2ShowAddress): String? {
            if (result.flags?.addressRepair != true) return null
            if (result.feedSource?.startsWith(REGISTRY_SOURCE_PREFIX) != true) return null
            if (result.fetchVia != null && result.fetchVia != V2FetchVia.DIRECT) return null
            val https = TrackedPodcastRtdbLogic.httpsFeedUrl(result.fetchUrl) ?: return null
            if (https == row.feedUrl?.trim()) return null
            return https.takeUnless { it == row.previousFeedUrl?.trim() }
        }

        /** The production fetch: one `POST /v2/shows/feeds`; null for any answer but 200. */
        suspend fun fetchWith(api: BoxLoreApiV2, ids: List<Long>): V2ShowsFeedsResponse? {
            val response = api.getShowFeeds(V2ShowsFeedsRequest(ids)).awaitResponse()
            return if (response.isSuccessful) response.body() else null
        }

        private const val REGISTRY_SOURCE_PREFIX = "registry:"
    }
}
