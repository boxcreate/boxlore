package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.UpgradeRepairDao
import cx.aswin.boxlore.core.database.UpgradeRepairShowEntity
import cx.aswin.boxlore.core.network.BoxLoreApiV2
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2ShowAddress
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsRequest
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsResponse
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.awaitResponse

/**
 * What one `POST /v2/shows/feeds` request of the upgrade repair's step 1 got.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair).
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
sealed interface AddressCheckAnswer {
    /** A 200 answer. One with `registryAvailable: false` counts as [Unavailable]. */
    data class Answered(val response: V2ShowsFeedsResponse) : AddressCheckAnswer

    /**
     * The server couldn't answer now (503 `lookup_unavailable`, 429, another 5xx) or the request
     * never got through. Never counts as a try; a show unanswered for
     * [UpgradeAddressRepair.MAX_UNANSWERED_MS] goes on without an answer.
     */
    data object Unavailable : AddressCheckAnswer

    /**
     * The server refused the request (another 4xx) or its answer couldn't be read: the same
     * request would fail again, so it counts as a try for each show asked.
     */
    data object Rejected : AddressCheckAnswer

    companion object {
        private const val TOO_MANY_REQUESTS = 429
        private const val SERVER_ERRORS = 500

        /** The production step 1 request: [api]'s `/v2/shows/feeds`, its status sorted as above. */
        fun fetchWith(api: BoxLoreApiV2): suspend (List<Long>) -> AddressCheckAnswer = { ids ->
            try {
                val response = api.getShowFeeds(V2ShowsFeedsRequest(ids)).awaitResponse()
                val body = response.body()
                when {
                    response.isSuccessful && body != null -> Answered(body)
                    response.isSuccessful -> Rejected
                    response.code() == TOO_MANY_REQUESTS || response.code() >= SERVER_ERRORS -> Unavailable
                    else -> Rejected
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                Unavailable
            } catch (e: Exception) {
                Log.w("UpgradeAddressRepair", "Address check answer couldn't be read", e)
                Rejected
            }
        }
    }
}

/**
 * What one [UpgradeAddressRepair.run] did, and why it stopped early.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair).
 */
internal class UpgradeRepairPass {
    var offline = false
    var paused = false
    var read = 0
    var notModified = 0
    var failed = 0

    /** Reads of shows whose namespace on the server differs from the one the phone keeps (D12). */
    var namespaceMismatches = 0

    /** Saved addresses step 2 replaced (P4 PR 18, for `own_ids_upgrade_completed`). */
    var swapped = 0

    /** Shows step 4 asked to register again (P4 PR 18, for `own_ids_upgrade_completed`). */
    var reregistered = 0

    val stopped: Boolean get() = offline || paused
}

/**
 * Step 1 of [UpgradeAddressRepair]: asks the server for the current address of every followed
 * show still at step 0, in chunks of at most [V2ShowsFeedsRequest.MAX_IDS], and records each
 * answer (see the repair's class description).
 *
 * A chunk the server couldn't answer ([AddressCheckAnswer.Unavailable], or the registry down)
 * counts no try; one it refused ([AddressCheckAnswer.Rejected]) counts a try for each show. A
 * show unanswered for [UpgradeAddressRepair.MAX_UNANSWERED_MS] since its first failed check, or
 * refused [UpgradeAddressRepair.MAX_ATTEMPTS] times, goes on to steps 2–4 with no answer: no swap,
 * and the daily check looks after its address. While a show is at step 0, `lastAttemptAt` holds
 * its first failed check. Offline, nothing is recorded and the run stops.
 *
 * An answer older than [FeedAddressDailyCheck.CHECK_INTERVAL_MS] that still holds a move to swap
 * to is dropped at the start of a run ([expireStaleAnswers]), so a repair paused or stopped for a
 * while asks again instead of applying an old answer.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair; decision D12).
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
internal class UpgradeAddressCheckStep(
    private val repairDao: UpgradeRepairDao,
    private val podcastDao: PodcastDao,
    private val ports: UpgradeRepairPorts,
    private val nowMillis: () -> Long,
    private val followedRow: suspend (UpgradeRepairShowEntity) -> PodcastEntity?,
    private val stopsHere: (UpgradeRepairPass) -> Boolean,
) {
    /** Sends shows at step 1 whose move to swap to is too old back to step 0. */
    suspend fun expireStaleAnswers() {
        val now = nowMillis()
        for (entry in repairDao.pendingShows()) {
            if (entry.completedStep != 1 || entry.checkedFetchUrl == null || !answerExpired(entry.lastAttemptAt, now)) continue
            Log.i(TAG, "Address check of ${entry.podcastId} is out of date; asking again")
            repairDao.upsert(entry.withoutAnswer().copy(completedStep = 0, lastAttemptAt = 0L, lastError = null))
        }
    }

    /** Step 1 for every followed show still at step 0. */
    suspend fun check(pass: UpgradeRepairPass) {
        val askable = LinkedHashMap<Long, UpgradeRepairShowEntity>()
        for (entry in repairDao.pendingShows().filter { it.completedStep == 0 }) {
            val row = followedRow(entry) ?: continue
            val showId = V2EpisodeIds.parseShowId(row.podcastId)
            if (showId == null) recordAnswer(entry, row, null) else askable[showId] = entry
        }
        for (chunk in askable.keys.chunked(V2ShowsFeedsRequest.MAX_IDS)) {
            if (stopsHere(pass)) return
            val answer = fetch(chunk)
            if (answer is AddressCheckAnswer.Answered && answer.response.registryAvailable) {
                recordChunk(chunk, askable, answer.response)
            } else {
                unanswered(chunk.map(askable::getValue), counts = answer is AddressCheckAnswer.Rejected, pass)
            }
        }
    }

    private suspend fun fetch(ids: List<Long>): AddressCheckAnswer = try {
        ports.fetchAddresses(ids)
    } catch (e: CancellationException) {
        throw e
    } catch (e: IOException) {
        Log.w(TAG, "Address check of ${ids.size} shows failed", e)
        AddressCheckAnswer.Unavailable
    } catch (e: Exception) {
        Log.w(TAG, "Address check of ${ids.size} shows failed", e)
        AddressCheckAnswer.Rejected
    }

    private suspend fun recordChunk(chunk: List<Long>, askable: Map<Long, UpgradeRepairShowEntity>, answer: V2ShowsFeedsResponse) {
        val byId = answer.results.associateBy(V2ShowAddress::id)
        for (showId in chunk) {
            val entry = askable.getValue(showId)
            podcastDao.getPodcast(entry.podcastId)?.let { row -> recordAnswer(entry, row, byId[showId]) }
        }
    }

    /** Records step 1 for one show; the registry's answer also counts as today's daily check. */
    private suspend fun recordAnswer(entry: UpgradeRepairShowEntity, row: PodcastEntity, result: V2ShowAddress?) {
        val now = nowMillis()
        val served = result?.validEpNs
        if (UpgradeAddressRepair.namespaceDiffers(row, served)) {
            Log.w(TAG, "Show ${row.podcastId} has another namespace on the server ($served); keeping the phone's")
        }
        if (result != null && !FeedAddressDailyCheck.changedOnPhone(row)) podcastDao.markFeedUrlChecked(row.podcastId, now)
        repairDao.upsert(
            entry.copy(
                completedStep = 1,
                attempts = 0,
                checkedFetchUrl = result?.let { FeedAddressDailyCheck.confirmedMove(row, it) },
                checkedTransportUrl = result?.transportUrl,
                checkedFeedSource = result?.feedSource,
                checkedEpNs = served,
                lastAttemptAt = now,
                lastError = null,
            ),
        )
    }

    /** A chunk without an answer: counted against its shows ([counts]) unless offline, which stops the run. */
    private suspend fun unanswered(entries: List<UpgradeRepairShowEntity>, counts: Boolean, pass: UpgradeRepairPass) {
        if (!ports.isOnline()) {
            pass.offline = true
            return
        }
        val now = nowMillis()
        for (entry in entries) {
            val current = repairDao.getShow(entry.podcastId)?.takeIf { it.completedStep == 0 } ?: continue
            val since = current.lastAttemptAt.takeIf { it in 1..now } ?: now
            val attempts = if (counts) current.attempts + 1 else current.attempts
            if (attempts >= UpgradeAddressRepair.MAX_ATTEMPTS || now - since >= UpgradeAddressRepair.MAX_UNANSWERED_MS) {
                Log.w(TAG, "No address answer for ${current.podcastId}; going on without one")
                val reason = if (counts) REFUSED else UNANSWERED
                repairDao.upsert(current.withoutAnswer().copy(completedStep = 1, attempts = 0, lastAttemptAt = now, lastError = reason))
            } else {
                repairDao.upsert(current.copy(attempts = attempts, lastAttemptAt = since, lastError = if (counts) REFUSED else UNAVAILABLE))
            }
        }
    }

    private fun UpgradeRepairShowEntity.withoutAnswer() = copy(checkedFetchUrl = null, checkedTransportUrl = null, checkedFeedSource = null, checkedEpNs = null)

    companion object {
        private const val TAG = "UpgradeAddressRepair"
        private const val UNAVAILABLE = "address_unavailable"
        private const val UNANSWERED = "address_unanswered"
        private const val REFUSED = "address_refused"

        /** Whether a step 1 answer recorded at [answeredAt] is too old to swap to at [now]. */
        fun answerExpired(answeredAt: Long, now: Long): Boolean = answeredAt <= 0L || answeredAt > now || now - answeredAt >= FeedAddressDailyCheck.CHECK_INTERVAL_MS
    }
}
