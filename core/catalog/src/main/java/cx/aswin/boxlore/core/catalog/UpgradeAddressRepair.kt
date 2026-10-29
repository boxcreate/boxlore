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
import cx.aswin.boxlore.core.model.UpgradeRepairReport
import cx.aswin.boxlore.core.network.model.V2EpisodeIds
import cx.aswin.boxlore.core.network.model.V2FetchVia
import cx.aswin.boxlore.core.network.model.V2ShowAddress
import cx.aswin.boxlore.core.network.model.V2ShowFlags
import cx.aswin.boxlore.core.network.model.V2ShowsFeedsRequest
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
 *   IDs ([AddressCheckAnswer.fetchWith]).
 * - [replaceAddress]: the one address writer ([FeedAddressUpdater.update]).
 * - [reread]: reads a followed show's feed again into the local catalog, passing the namespace the
 *   server reported ([rereadWith]); moves it finds are followed by the catalog itself.
 * - [requestNotificationRegistration]: asks for a show's notifications to be registered again by
 *   the app's capped, acknowledged job, and returns once the request is saved; throws when it
 *   couldn't be saved.
 * - [onAddressStepsFinished]: called once, after every show's steps 1–3 are over, so the cloud
 *   sync's identity catch-up runs again for the episodes the reads settled.
 * - [isOnline]: failures while offline never count against a show's cap.
 * - [telemetry]: one `feed_fetch_result` summary per run that read feeds, and the one
 *   `own_ids_upgrade_completed` when the repair of an upgraded install finishes.
 *
 * Added in P4 PR 15 of the own-episode-IDs plan (plan section 11, upgrade repair).
 */
data class UpgradeRepairPorts(
    val fetchAddresses: suspend (List<Long>) -> AddressCheckAnswer,
    val replaceAddress: suspend (FeedAddressChange) -> FeedAddressUpdate,
    val reread: suspend (show: PodcastEntity, serverEpNs: Long?) -> RefreshOutcome,
    val requestNotificationRegistration: suspend (podcastId: String) -> Unit,
    val onAddressStepsFinished: suspend () -> Unit = {},
    val isOnline: () -> Boolean = { true },
    val telemetry: FeedTelemetryPort = FeedTelemetryPort.NONE,
)

/**
 * The one-time repair of saved feed addresses after the update, for every Podcast Index show
 * followed when the phone upgraded (the v39 upgrade seeds one pending `upgrade_repair_shows` row
 * each; a fresh install has none). Per show:
 *
 * 1. Ask the server for the show's current address ([UpgradeAddressCheckStep]): `POST
 *    /v2/shows/feeds`, every waiting show in chunks of at most [V2ShowsFeedsRequest.MAX_IDS]. The
 *    answer is recorded: `checkedFetchUrl` holds the address to swap to, set only for a confirmed
 *    move ([FeedAddressDailyCheck.confirmedMove]: the registry flags the show for repair and its
 *    own HTTPS address differs from the saved one); `fetchUrl: null` keeps the saved address. A
 *    chunk the server couldn't answer stays at step 0 without counting a try, for at most
 *    [MAX_UNANSWERED_MS]; one it refused counts a try. Either way the show then goes on without
 *    an answer. A namespace (`epNs`) the server reports differently from the phone's is recorded
 *    in `checkedEpNs`, counted on `feed_fetch_result` when the show is read, and never applied to
 *    issued IDs (decision D12).
 * 2. Swap the saved address through the one address writer, reported as `feed_url_corrected`
 *    (step `upgrade_repair`) and registered again by the writer. A follow changed on this phone and
 *    not pushed yet keeps its address (decision 2); the daily check swaps it once it is pushed. An
 *    answer older than a day is asked again first, and one the daily check has since replaced
 *    with a newer answer is not applied.
 * 3. Read the feed again, without the auto-download pass a foreground read starts (the background
 *    pass applies the listener's rules). A failure while online counts against the show's cap of
 *    [MAX_ATTEMPTS]; the show then ends [UpgradeRepairState.GAVE_UP], and the daily check keeps
 *    watching it.
 * 4. Register notifications again for every followed show with notifications on, once its own
 *    steps are over, recorded in [UpgradeNotificationMarker] (`noBackupFilesDir`), so a restored
 *    phone registers again even when Room says steps 1–3 are done.
 *
 * Once no show has steps 1–3 left, [UpgradeRepairPorts.onAddressStepsFinished] runs once per
 * install (recorded in the same marker), so the sync's one-time identity catch-up runs again for
 * the episodes the reads settled. A fresh install never calls it.
 *
 * Each run is counted in [tally] (runs, addresses swapped, notifications registered again), and
 * the run that finishes the repair of an upgraded install sends `own_ids_upgrade_completed` once
 * through [UpgradeRepairPorts.telemetry] (P4 PR 18). A fresh install never sends it.
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
    private val tally: UpgradeRepairTally = MemoryUpgradeRepairTally(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val runLock = Mutex()
    private val addresses = UpgradeAddressCheckStep(repairDao, podcastDao, ports, nowMillis, ::followedRow, ::stopsHere)
    private val notifications = UpgradeNotificationStep(repairDao, podcastDao, marker, ports.requestNotificationRegistration)

    /**
     * Whether a run has anything to do: shows with steps left, the sync catch-up not asked for yet,
     * or step 4 not finished with followed shows to register. With none, step 4 is recorded as
     * finished here, without network.
     */
    suspend fun needsWork(): Boolean {
        if (repairDao.pendingCount() > 0 || catchUpDue()) return true
        if (marker.isComplete) return false
        if (notifications.targets().isNotEmpty()) return true
        marker.markComplete()
        return false
    }

    /** Runs every step that is due; see the class description. */
    suspend fun run(): UpgradeRepairRun = runLock.withLock {
        if (!enabled()) return@withLock UpgradeRepairRun.PAUSED
        val startedAt = nowMillis()
        val pass = UpgradeRepairPass()
        addresses.expireStaleAnswers()
        addresses.check(pass)
        repairShows(pass)
        if (!pass.stopped) {
            askForSyncCatchUp()
            pass.reregistered = notifications.register()
        }
        report(pass)
        val result =
            when {
                pass.paused -> UpgradeRepairRun.PAUSED
                repairDao.pendingCount() == 0 && marker.isComplete && !catchUpDue() -> UpgradeRepairRun.DONE
                else -> UpgradeRepairRun.RETRY
            }
        tallyRun(startedAt, pass, finished = result == UpgradeRepairRun.DONE)
        result
    }

    /**
     * Adds this run to [tally] on an install the update queued shows for, and, once a run of this
     * build finishes the repair, sends `own_ids_upgrade_completed` once
     * ([FeedTelemetryPort.upgradeRepairCompleted]). The sent record is saved first, so a crash
     * can lose the report but never send it twice. A fresh install, or one that finished before
     * this build counted runs, reports nothing.
     *
     * Added in P4 PR 18 of the own-episode-IDs plan (plan section 11, upgrade repair; analytics).
     */
    private suspend fun tallyRun(startedAt: Long, pass: UpgradeRepairPass, finished: Boolean) {
        if (!repairDao.hasShows()) return
        tally.recordRun(startedAt, nowMillis(), pass.swapped, pass.reregistered)
        if (!finished || tally.reported) return
        val counts = tally.read().takeIf { it.runs > 0 } ?: return
        val shows = repairDao.showCounts()
        if (!tally.markReported()) return
        ports.telemetry.upgradeRepairCompleted(
            UpgradeRepairReport(
                showsCount = shows.shows,
                showsGaveUp = shows.gaveUp,
                addressesSwapped = counts.addressesSwapped,
                notificationsReregistered = counts.notificationsReregistered,
                runs = counts.runs,
                elapsedMillis = counts.lastRunAt - counts.firstRunAt,
            ),
        )
    }

    /** Steps 1–3 are over for an install that had shows to repair, and the sync catch-up wasn't asked for yet. */
    private suspend fun catchUpDue(): Boolean = !marker.syncCatchUpRequested && repairDao.pendingCount() == 0 && repairDao.hasShows()

    private suspend fun askForSyncCatchUp() {
        if (!catchUpDue()) return
        try {
            ports.onAddressStepsFinished()
            marker.markSyncCatchUpRequested()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't ask for the sync catch-up", e)
        }
    }

    /** One `feed_fetch_result` summary for the feeds this run read, if any. */
    private fun report(pass: UpgradeRepairPass) {
        if (pass.read + pass.notModified + pass.failed == 0) return
        ports.telemetry.feedFetched(
            FeedFetchReport.BackgroundPass(FeedFetchSource.FEED, pass.read, pass.notModified, pass.failed, namespaceMismatchCount = pass.namespaceMismatches),
        )
    }

    /** Steps 2 and 3 for every show past step 1, one show at a time. */
    private suspend fun repairShows(pass: UpgradeRepairPass) {
        for (entry in repairDao.pendingShows().filter { it.completedStep != 0 }) {
            if (stopsHere(pass)) return
            try {
                val current = if (entry.completedStep == 1) swapAddress(entry, pass) else entry
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
    private suspend fun swapAddress(entry: UpgradeRepairShowEntity, pass: UpgradeRepairPass): UpgradeRepairShowEntity? {
        val row = followedRow(entry) ?: return null
        val target = swapTarget(row, entry, nowMillis())?.takeUnless { FeedAddressDailyCheck.changedOnPhone(row) }
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
            val update = ports.replaceAddress(change)
            if (update == FeedAddressUpdate.UPDATED) pass.swapped++
            Log.i(TAG, "Address swap for ${row.podcastId}: $update")
        }
        return entry.copy(completedStep = 2, attempts = 0, lastAttemptAt = nowMillis(), lastError = null).also { repairDao.upsert(it) }
    }

    /** Step 3. */
    private suspend fun readAgain(entry: UpgradeRepairShowEntity, pass: UpgradeRepairPass) {
        val row = followedRow(entry) ?: return
        if (row.feedUrl.isNullOrBlank()) {
            finish(entry)
            return
        }
        val outcome = ports.reread(row, entry.checkedEpNs)
        if (namespaceDiffers(row, entry.checkedEpNs)) pass.namespaceMismatches++
        when (outcome) {
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

    /**
     * Counts a failed try against the show's cap, unless the phone is offline (then the run
     * stops). A show at step 1 keeps the time of its answer, so an old answer still expires.
     */
    private suspend fun recordFailure(podcastId: String, reason: String, pass: UpgradeRepairPass) {
        if (!ports.isOnline()) {
            pass.offline = true
            return
        }
        val current = repairDao.getShow(podcastId) ?: return
        val attempts = current.attempts + 1
        val state = if (attempts >= MAX_ATTEMPTS) UpgradeRepairState.GAVE_UP else current.state
        if (state == UpgradeRepairState.GAVE_UP) Log.w(TAG, "Giving up the repair of $podcastId after $attempts tries ($reason)")
        val triedAt = if (current.completedStep <= 1) current.lastAttemptAt else nowMillis()
        repairDao.upsert(current.copy(attempts = attempts, state = state, lastAttemptAt = triedAt, lastError = reason.take(MAX_ERROR_LENGTH)))
    }

    /** The show's row while it is still a followed Podcast Index show; otherwise its repair ends done. */
    private suspend fun followedRow(entry: UpgradeRepairShowEntity): PodcastEntity? {
        val row = podcastDao.getPodcast(entry.podcastId)
        if (row != null && row.isSubscribed && !row.isRss) return row
        repairDao.upsert(entry.copy(state = UpgradeRepairState.DONE, lastAttemptAt = nowMillis()))
        return null
    }

    private fun stopsHere(pass: UpgradeRepairPass): Boolean {
        if (!pass.stopped && !enabled()) pass.paused = true
        return pass.stopped
    }

    companion object {
        private const val TAG = "UpgradeAddressRepair"
        private const val READ_FAILED = "read_failed"
        private const val MAX_ERROR_LENGTH = 80

        /** Failed tries of one show (while online) before its repair gives up, or goes on without an address answer. */
        const val MAX_ATTEMPTS = 5

        /** How long a show waits for an address answer the server couldn't give before it goes on without one. */
        const val MAX_UNANSWERED_MS = 7L * FeedAddressDailyCheck.CHECK_INTERVAL_MS

        /**
         * The address step 2 swaps to: step 1's confirmed move, checked again against the row as it
         * is now (the saved address may have changed since). Step 1 kept it only when the registry
         * flagged the show for repair with its own direct address, so the same rule applies. None
         * when the answer is older than a day at [now], or the daily check has a newer one.
         */
        internal fun swapTarget(row: PodcastEntity, entry: UpgradeRepairShowEntity, now: Long): String? {
            val fetchUrl = entry.checkedFetchUrl ?: return null
            if (UpgradeAddressCheckStep.answerExpired(entry.lastAttemptAt, now) || row.feedUrlCheckedAt > entry.lastAttemptAt) return null
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

        /** True when [served], the namespace the server reported, differs from the one the phone keeps for [row]. */
        internal fun namespaceDiffers(row: PodcastEntity, served: Long?): Boolean {
            val kept = row.epNs ?: EpisodeRecipeId.showIdOrNull(row.podcastId)
            return served != null && kept != null && served != kept
        }

        /**
         * The production step 3: a catalog refresh of the saved address, with the server's namespace
         * for comparison only. It never runs the auto-download pass a foreground read starts: the
         * repair runs in the background, where the regular pass applies the listener's background
         * download rules.
         */
        fun rereadWith(catalog: LocalEpisodeCatalogPort): suspend (PodcastEntity, Long?) -> RefreshOutcome = { row, serverEpNs ->
            catalog.refresh(
                LocalEpisodeCatalogPort.RefreshRequest(
                    podcastIndexId = row.podcastId,
                    feedUrl = row.feedUrl.orEmpty(),
                    meta = LocalEpisodeCatalogPort.PodcastMeta(title = row.title, imageUrl = row.imageUrl, genre = row.genre, artist = row.author),
                    runPostPersistCallback = false,
                    serverEpNs = serverEpNs,
                ),
            )
        }
    }
}
