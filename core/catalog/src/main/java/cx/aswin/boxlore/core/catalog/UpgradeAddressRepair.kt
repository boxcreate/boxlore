package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.UpgradeRepairDao
import cx.aswin.boxlore.core.database.UpgradeRepairShowEntity
import cx.aswin.boxlore.core.database.UpgradeRepairState
import cx.aswin.boxlore.core.domain.ports.FeedTelemetryPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.model.FeedFetchReport
import cx.aswin.boxlore.core.model.FeedFetchSource
import cx.aswin.boxlore.core.model.FeedUrlCorrectionReason
import cx.aswin.boxlore.core.model.FeedUrlCorrectionStep
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2FetchVia
import cx.aswin.boxlore.core.network.model.V2ShowAddress
import cx.aswin.boxlore.core.network.model.V2ShowFlags
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsRequest
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** How one [UpgradeAddressRepair.run] ended. */
enum class UpgradeRepairRun {
    /** Every step finished, or there was nothing to do. */
    DONE,

    /** Steps are left: offline, the server didn't answer, or a show failed below its cap. */
    RETRY,

    /** `upgrade-repair-off` is on: the repair stopped without marking anything done. */
    PAUSED,
}

/**
 * What [UpgradeAddressRepair] reaches outside Room.
 *
 * - [fetchAddresses]: one `POST /v2/shows/feeds` for at most [V2ShowsFeedsRequest.MAX_IDS] show
 *   IDs; null for any answer but 200 ([FeedAddressDailyCheck.fetchWith]).
 * - [replaceAddress]: the one address writer ([FeedAddressUpdater.update]).
 * - [reread]: reads a followed show's feed again into the local catalog, passing the namespace the
 *   server reported ([rereadWith]); moves it finds are followed by the catalog itself.
 * - [requestNotificationRegistration]: asks for a show's notifications to be registered again,
 *   acknowledged and retried by the app's capped job; throws [IllegalStateException] when it
 *   couldn't be asked.
 * - [isOnline]: failures while offline never count against a show's cap.
 * - [telemetry]: one `feed_fetch_result` summary per run that read feeds.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair).
 */
data class UpgradeRepairPorts(
    val fetchAddresses: suspend (List<Long>) -> V2ShowsFeedsResponse?,
    val replaceAddress: suspend (FeedAddressChange) -> FeedAddressUpdate,
    val reread: suspend (show: PodcastEntity, serverEpNs: Long?) -> RefreshOutcome,
    val requestNotificationRegistration: (podcastId: String) -> Unit,
    val isOnline: () -> Boolean = { true },
    val telemetry: FeedTelemetryPort = FeedTelemetryPort.NONE,
)

/**
 * The one-time repair of saved feed addresses after the update, for every Podcast Index show
 * followed when the phone upgraded (the v39 upgrade seeds one pending `upgrade_repair_shows` row
 * each; a fresh install has none). Per show:
 *
 * 1. Ask the server for the show's current address: `POST /v2/shows/feeds`, every waiting show
 *    in chunks of at most [V2ShowsFeedsRequest.MAX_IDS]. A chunk that fails, or that the address
 *    registry couldn't answer, stays at step 0 for the next run; the other chunks go on. The answer
 *    is recorded: `checkedFetchUrl` holds the address to swap to, set only for a confirmed move
 *    ([FeedAddressDailyCheck.confirmedMove]: the registry flags the show for repair and its own
 *    HTTPS address differs from the saved one); `fetchUrl: null` keeps the saved address. A
 *    namespace (`epNs`) that differs from the one the phone keeps is recorded in `checkedEpNs` and
 *    logged, and never applied to issued IDs (decision D12).
 * 2. Swap the saved address through the one address writer, reported as `feed_url_corrected`
 *    (step `upgrade_repair`) and registered again by the writer. A follow changed on this phone and
 *    not pushed yet keeps its address (decision 2); the daily check swaps it once it is pushed.
 * 3. Read the feed again. A failure while online counts against the show's cap of [MAX_ATTEMPTS];
 *    the show then ends [UpgradeRepairState.GAVE_UP], and the daily check keeps watching it.
 * 4. Register notifications again for every followed show with notifications on, once its own
 *    steps are over, recorded in [UpgradeNotificationMarker] (`noBackupFilesDir`), so a restored
 *    phone registers again even when Room says steps 1–3 are done.
 *
 * Each finished step is written at once, so process death resumes at the next step. Before each
 * step the show's row is read again: a show no longer followed ends [UpgradeRepairState.DONE]
 * without further steps. Shows followed after the upgrade have no repair row and are left to the
 * daily check. With [enabled] off (`upgrade-repair-off`) the repair stops without marking anything
 * done. [needsWork] settles a fresh install, or one with only `rss:` follows, without network.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair; decisions 2 and D12).
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
class UpgradeAddressRepair(
    private val repairDao: UpgradeRepairDao,
    private val podcastDao: PodcastDao,
    private val ports: UpgradeRepairPorts,
    private val marker: UpgradeNotificationMarker,
    private val enabled: () -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val runLock = Mutex()
    private val notifications = UpgradeNotificationStep(repairDao, podcastDao, marker, ports.requestNotificationRegistration)

    /** What one run did, and why it stopped early. */
    private class Pass {
        var offline = false
        var paused = false
        var read = 0
        var notModified = 0
        var failed = 0

        val stopped: Boolean get() = offline || paused

        /** One `feed_fetch_result` summary for the feeds this run read, if any. */
        fun report(telemetry: FeedTelemetryPort) {
            if (read + notModified + failed == 0) return
            telemetry.feedFetched(FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, read, notModified, failed))
        }
    }

    /**
     * Whether a run has anything to do: shows with steps left, or step 4 not finished with followed
     * shows to register. With neither, step 4 is recorded as finished here, without network.
     */
    suspend fun needsWork(): Boolean {
        if (repairDao.pendingCount() > 0) return true
        if (marker.isComplete) return false
        if (notifications.targets().isNotEmpty()) return true
        marker.markComplete()
        return false
    }

    /** Runs every step that is due; see the class description. */
    suspend fun run(): UpgradeRepairRun = runLock.withLock {
        if (!enabled()) return@withLock UpgradeRepairRun.PAUSED
        val pass = Pass()
        checkAddresses(pass)
        repairShows(pass)
        if (!pass.stopped) notifications.register()
        pass.report(ports.telemetry)
        when {
            pass.paused -> UpgradeRepairRun.PAUSED
            repairDao.pendingCount() == 0 && marker.isComplete -> UpgradeRepairRun.DONE
            else -> UpgradeRepairRun.RETRY
        }
    }

    /** Step 1 for every followed show still at step 0. */
    private suspend fun checkAddresses(pass: Pass) {
        val askable = LinkedHashMap<Long, UpgradeRepairShowEntity>()
        for (entry in repairDao.pendingShows().filter { it.completedStep == 0 }) {
            val row = followedRow(entry) ?: continue
            val showId = V2EpisodeIds.parseShowId(row.podcastId)
            if (showId == null) recordAnswer(entry, row, null) else askable[showId] = entry
        }
        for (chunk in askable.keys.chunked(V2ShowsFeedsRequest.MAX_IDS)) {
            if (stopsHere(pass)) return
            val answer = fetch(chunk)?.takeIf(V2ShowsFeedsResponse::registryAvailable)
            if (answer == null) pass.offline = !ports.isOnline() else recordChunk(chunk, askable, answer)
        }
    }

    private suspend fun recordChunk(chunk: List<Long>, askable: Map<Long, UpgradeRepairShowEntity>, answer: V2ShowsFeedsResponse) {
        val byId = answer.results.associateBy(V2ShowAddress::id)
        for (showId in chunk) {
            val entry = askable.getValue(showId)
            podcastDao.getPodcast(entry.podcastId)?.let { row -> recordAnswer(entry, row, byId[showId]) }
        }
    }

    private suspend fun fetch(ids: List<Long>): V2ShowsFeedsResponse? = try {
        ports.fetchAddresses(ids)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Address check of ${ids.size} shows failed", e)
        null
    }

    /** Records step 1 for one show; the registry's answer also counts as today's daily check. */
    private suspend fun recordAnswer(entry: UpgradeRepairShowEntity, row: PodcastEntity, result: V2ShowAddress?) {
        val now = nowMillis()
        val served = result?.validEpNs
        val kept = row.epNs ?: EpisodeRecipeId.showIdOrNull(row.podcastId)
        if (served != null && kept != null && served != kept) {
            Log.w(TAG, "Show ${row.podcastId} has namespace $kept here, $served on the server; keeping $kept")
        }
        if (result != null && !FeedAddressDailyCheck.changedOnPhone(row)) podcastDao.markFeedUrlChecked(row.podcastId, now)
        repairDao.upsert(
            entry.copy(
                completedStep = 1,
                checkedFetchUrl = result?.let { FeedAddressDailyCheck.confirmedMove(row, it) },
                checkedTransportUrl = result?.transportUrl,
                checkedFeedSource = result?.feedSource,
                checkedEpNs = served,
                lastAttemptAt = now,
                lastError = null,
            ),
        )
    }

    /** Steps 2 and 3 for every show past step 1, one show at a time. */
    private suspend fun repairShows(pass: Pass) {
        for (entry in repairDao.pendingShows().filter { it.completedStep != 0 }) {
            if (stopsHere(pass)) return
            try {
                val current = if (entry.completedStep == 1) swapAddress(entry) else entry
                if (current?.completedStep == 2) readAgain(current, pass)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Repair of ${entry.podcastId} failed", e)
                recordFailure(entry.podcastId, e.javaClass.simpleName, pass)
            }
        }
    }

    /** Step 2; null when the show is no longer followed. */
    private suspend fun swapAddress(entry: UpgradeRepairShowEntity): UpgradeRepairShowEntity? {
        val row = followedRow(entry) ?: return null
        val target = swapTarget(row, entry)?.takeUnless { FeedAddressDailyCheck.changedOnPhone(row) }
        if (target != null) {
            val change =
                FeedAddressChange(
                    podcastId = row.podcastId,
                    feedUrl = target,
                    source = entry.checkedFeedSource ?: FeedUrlSources.REGISTRY,
                    checkedAt = nowMillis(),
                    onlyReplacing = row.feedUrl,
                    reason = FeedUrlCorrectionReason.REGISTRY,
                    step = FeedUrlCorrectionStep.UPGRADE_REPAIR,
                )
            Log.i(TAG, "Address swap for ${row.podcastId}: ${ports.replaceAddress(change)}")
        }
        return entry.copy(completedStep = 2, lastAttemptAt = nowMillis(), lastError = null).also { repairDao.upsert(it) }
    }

    /** Step 3. */
    private suspend fun readAgain(entry: UpgradeRepairShowEntity, pass: Pass) {
        val row = followedRow(entry) ?: return
        if (row.feedUrl.isNullOrBlank()) {
            finish(entry)
            return
        }
        when (val outcome = ports.reread(row, entry.checkedEpNs)) {
            is RefreshOutcome.Success -> {
                pass.read++
                finish(entry)
            }
            is RefreshOutcome.Unchanged -> {
                pass.notModified++
                finish(entry)
            }
            is RefreshOutcome.Failure -> {
                pass.failed++
                recordFailure(entry.podcastId, outcome.reportStatus ?: READ_FAILED, pass)
            }
        }
    }

    private suspend fun finish(entry: UpgradeRepairShowEntity) {
        repairDao.upsert(entry.copy(completedStep = 3, state = UpgradeRepairState.DONE, lastAttemptAt = nowMillis(), lastError = null))
    }

    /** Counts a failed try against the show's cap, unless the phone is offline (then the run stops). */
    private suspend fun recordFailure(podcastId: String, reason: String, pass: Pass) {
        if (!ports.isOnline()) {
            pass.offline = true
            return
        }
        val current = repairDao.getShow(podcastId) ?: return
        val attempts = current.attempts + 1
        val state = if (attempts >= MAX_ATTEMPTS) UpgradeRepairState.GAVE_UP else current.state
        if (state == UpgradeRepairState.GAVE_UP) Log.w(TAG, "Giving up the repair of $podcastId after $attempts tries ($reason)")
        repairDao.upsert(current.copy(attempts = attempts, state = state, lastAttemptAt = nowMillis(), lastError = reason.take(MAX_ERROR_LENGTH)))
    }

    /** The show's row while it is still a followed Podcast Index show; otherwise its repair ends done. */
    private suspend fun followedRow(entry: UpgradeRepairShowEntity): PodcastEntity? {
        val row = podcastDao.getPodcast(entry.podcastId)
        if (row != null && row.isSubscribed && !row.isRss) return row
        repairDao.upsert(entry.copy(state = UpgradeRepairState.DONE, lastAttemptAt = nowMillis()))
        return null
    }

    private fun stopsHere(pass: Pass): Boolean {
        if (!pass.stopped && !enabled()) pass.paused = true
        return pass.stopped
    }

    companion object {
        private const val TAG = "UpgradeAddressRepair"
        private const val READ_FAILED = "read_failed"
        private const val MAX_ERROR_LENGTH = 80

        /** Failed tries of one show (while online) before its repair gives up. */
        const val MAX_ATTEMPTS = 5

        /**
         * The address step 2 swaps to: step 1's confirmed move, checked again against the row as it
         * is now (the saved address may have changed since). Step 1 kept it only when the registry
         * flagged the show for repair with its own direct address, so the same rule applies.
         */
        internal fun swapTarget(row: PodcastEntity, entry: UpgradeRepairShowEntity): String? {
            val fetchUrl = entry.checkedFetchUrl ?: return null
            val confirmed =
                V2ShowAddress(
                    id = V2EpisodeIds.parseShowId(row.podcastId) ?: return null,
                    fetchUrl = fetchUrl,
                    fetchVia = V2FetchVia.DIRECT,
                    feedSource = entry.checkedFeedSource,
                    flags = V2ShowFlags(addressRepair = true),
                )
            return FeedAddressDailyCheck.confirmedMove(row, confirmed)
        }

        /** The production step 3: a catalog refresh of the saved address, with the server's namespace for comparison only. */
        fun rereadWith(catalog: LocalEpisodeCatalogPort): suspend (PodcastEntity, Long?) -> RefreshOutcome = { row, serverEpNs ->
            catalog.refresh(
                LocalEpisodeCatalogPort.RefreshRequest(
                    podcastIndexId = row.podcastId,
                    feedUrl = row.feedUrl.orEmpty(),
                    meta = LocalEpisodeCatalogPort.PodcastMeta(title = row.title, imageUrl = row.imageUrl, genre = row.genre, artist = row.author),
                    serverEpNs = serverEpNs,
                ),
            )
        }
    }
}

/**
 * Step 4 of [UpgradeAddressRepair]: asks for every followed Podcast Index show with notifications
 * on, whose own repair steps are over, to be registered again, records each request in [marker],
 * and records the step done once no show has steps left and every request was made.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair; "Android backup").
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
internal class UpgradeNotificationStep(
    private val repairDao: UpgradeRepairDao,
    private val podcastDao: PodcastDao,
    private val marker: UpgradeNotificationMarker,
    private val requestRegistration: (podcastId: String) -> Unit,
) {
    suspend fun register() {
        if (marker.isComplete) return
        val requested = mutableListOf<String>()
        var allRequested = true
        for (podcastId in targets()) {
            if (request(podcastId)) requested += podcastId else allRequested = false
            if (requested.size >= MARKER_BATCH) {
                marker.addRegistered(requested)
                requested.clear()
            }
        }
        marker.addRegistered(requested)
        if (allRequested && repairDao.pendingCount() == 0) marker.markComplete()
    }

    /** Followed Podcast Index shows with notifications on, not yet registered here, whose repair steps are over. */
    suspend fun targets(): List<String> {
        val registered = marker.registered()
        val waiting = repairDao.pendingShows().mapTo(HashSet()) { it.podcastId }
        return podcastDao
            .getSubscribedPodcastsList()
            .filter { !it.isRss && it.notificationsEnabled && it.podcastId !in registered && it.podcastId !in waiting }
            .map { it.podcastId }
    }

    private fun request(podcastId: String): Boolean = try {
        requestRegistration(podcastId)
        true
    } catch (e: IllegalStateException) {
        Log.e(TAG, "Couldn't ask to register notifications again for $podcastId", e)
        false
    }

    private companion object {
        const val TAG = "UpgradeAddressRepair"
        const val MARKER_BATCH = 50
    }
}
