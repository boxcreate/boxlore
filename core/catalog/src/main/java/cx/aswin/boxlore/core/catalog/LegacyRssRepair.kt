package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.catalog.backup.OpmlImportLogic
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.PendingPodcastIdRepair
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.LegacyRssFeedSnapshot
import cx.aswin.boxlore.core.rss.LegacyRssUpgradeOutcome
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal sealed interface ExactRepairMatch {
    data class Found(val podcast: Podcast,) : ExactRepairMatch

    data object NoMatch : ExactRepairMatch

    data object TransientFailure : ExactRepairMatch
}

class LegacyRssRepairCatalog(
    val podcastDao: PodcastDao,
    val rssRepository: RssPodcastRepository,
    val podcastRepository: PodcastRepository,
    val userPreferences: UserPreferencesRepository,
    val boxcastPrefs: BoxcastPrefs,
    val adaptiveRanking: AdaptiveRankingRepository,
)

class LegacyRssRepairRuntime(
    val isOnline: () -> Boolean,
    val activation: LegacyRssRepairActivation,
    val scope: CoroutineScope,
    val restoreNotifications: suspend (Podcast) -> Unit,
)

internal object LegacyRssRepairLogic {
    const val MAX_CONSECUTIVE_TRANSIENT_FAILURES = 3

    fun isEligibleSource(source: PodcastEntity): Boolean = source.isSubscribed &&
        source.isRss &&
        source.linkedPodcastIndexId == null

    fun shouldStartPass(hasEligibleSources: Boolean, isOnline: Boolean,): Boolean = hasEligibleSources && isOnline

    fun shouldMarkCompleted(hasPendingIdRepair: Boolean): Boolean = !hasPendingIdRepair

    fun shouldStopPass(consecutiveTransientFailures: Int): Boolean = consecutiveTransientFailures >= MAX_CONSECUTIVE_TRANSIENT_FAILURES

    fun selectMatch(urlLookup: ExactPodcastLookupResult, guidLookup: ExactPodcastLookupResult,): ExactRepairMatch {
        if (urlLookup is ExactPodcastLookupResult.Failed ||
            guidLookup is ExactPodcastLookupResult.Failed
        ) {
            return ExactRepairMatch.TransientFailure
        }
        val urlPodcast = (urlLookup as? ExactPodcastLookupResult.Found)?.podcast
        val guidPodcast = (guidLookup as? ExactPodcastLookupResult.Found)?.podcast
        if (urlPodcast != null && guidPodcast != null && urlPodcast.id != guidPodcast.id) {
            return ExactRepairMatch.NoMatch
        }
        val match = urlPodcast ?: guidPodcast ?: return ExactRepairMatch.NoMatch
        if (match.isRss || match.id.toLongOrNull()?.let { it <= 0L } != false) {
            return ExactRepairMatch.NoMatch
        }
        return ExactRepairMatch.Found(match)
    }
}

internal class LegacyRssRepairOneShotGate {
    private val attempted = AtomicBoolean(false)

    fun hasAttempted(): Boolean = attempted.get()

    fun tryBegin(): Boolean = attempted.compareAndSet(false, true)

    fun resetAfterFailure() {
        attempted.set(false)
    }
}

enum class LegacyRssRepairActivation {
    ENABLED,
    SETTLE_WITHOUT_REPAIR,
    DISABLED,
}

/**
 * One-time, conservative repair for legacy OPML rows that were stored as true RSS subscriptions.
 *
 * It never searches by title/author. A row moves only when Podcast Index resolves its publisher
 * feed URL or podcast GUID and the RSS module can preserve the complete listener-facing catalog.
 */
class LegacyRssRepair private constructor(
    private val catalog: LegacyRssRepairCatalog,
    private val runtime: LegacyRssRepairRuntime,
    private val lookup: suspend (ExactPodcastLookupKey) -> ExactPodcastLookupResult,
) {
    private val podcastDao get() = catalog.podcastDao
    private val rssRepository get() = catalog.rssRepository
    private val userPreferences get() = catalog.userPreferences
    private val boxcastPrefs get() = catalog.boxcastPrefs
    private val adaptiveRanking get() = catalog.adaptiveRanking
    private val isOnline get() = runtime.isOnline
    private val activation get() = runtime.activation
    private val scope get() = runtime.scope
    private val running = AtomicBoolean(false)
    private val oneShotGate = LegacyRssRepairOneShotGate()
    private val progressState = MutableStateFlow(false)

    val inProgress: StateFlow<Boolean> = progressState.asStateFlow()

    fun ensureStarted() {
        if (oneShotGate.hasAttempted()) return
        if (activation == LegacyRssRepairActivation.SETTLE_WITHOUT_REPAIR) {
            settleFreshInstall()
            return
        }
        if (activation != LegacyRssRepairActivation.ENABLED) return
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                runIfNeeded()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "Legacy RSS repair stopped before completion", error)
            } finally {
                progressState.value = false
                running.set(false)
            }
        }
    }

    private fun settleFreshInstall() {
        if (!oneShotGate.tryBegin()) return
        scope.launch {
            try {
                userPreferences.markLegacyRssRepairVersion(REPAIR_VERSION)
            } catch (error: CancellationException) {
                oneShotGate.resetAfterFailure()
                throw error
            } catch (error: Exception) {
                oneShotGate.resetAfterFailure()
                Log.w(TAG, "Could not persist fresh-install repair exclusion", error)
            }
        }
    }

    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth")
    internal suspend fun runIfNeeded() {
        if (userPreferences.legacyRssRepairVersion() >= REPAIR_VERSION) return
        reconcilePendingRepair(userPreferences.pendingPodcastIdRepair())

        val pendingOldId = userPreferences.pendingPodcastIdRepair()?.oldPodcastId
        val sources =
            podcastDao
                .getSubscribedRssPodcasts()
                .filter(LegacyRssRepairLogic::isEligibleSource)
                .sortedByDescending { it.podcastId == pendingOldId }
        if (sources.isEmpty()) {
            if (
                LegacyRssRepairLogic.shouldMarkCompleted(
                    hasPendingIdRepair = userPreferences.pendingPodcastIdRepair() != null,
                )
            ) {
                userPreferences.markLegacyRssRepairVersion(REPAIR_VERSION)
            }
            return
        }
        if (!LegacyRssRepairLogic.shouldStartPass(sources.isNotEmpty(), isOnline())) return
        if (!oneShotGate.tryBegin()) return

        progressState.value = true
        try {
            var consecutiveTransientFailures = 0
            for (source in sources) {
                consecutiveTransientFailures = processSource(source.podcastId, consecutiveTransientFailures)
                if (LegacyRssRepairLogic.shouldStopPass(consecutiveTransientFailures)) break
            }

            if (userPreferences.pendingPodcastIdRepair() == null) {
                userPreferences.markLegacyRssRepairVersion(REPAIR_VERSION)
            }
        } finally {
            progressState.value = false
        }
    }

    private suspend fun processSource(sourcePodcastId: String, consecutiveTransientFailures: Int,): Int {
        val snapshot =
            try {
                rssRepository.legacySubscriptionRepair.inspect(sourcePodcastId).getOrThrow()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                return consecutiveTransientFailures + 1
            }
        return when (val match = findExactMatch(snapshot)) {
            ExactRepairMatch.NoMatch -> 0
            ExactRepairMatch.TransientFailure -> consecutiveTransientFailures + 1
            is ExactRepairMatch.Found -> {
                migrate(sourcePodcastId, match.podcast, snapshot)
                0
            }
        }
    }

    private suspend fun findExactMatch(snapshot: LegacyRssFeedSnapshot): ExactRepairMatch {
        val urlCandidates =
            (
                OpmlImportLogic.urlLookupCandidates(snapshot.sourceFeedUrl) +
                    OpmlImportLogic.urlLookupCandidates(snapshot.finalFeedUrl)
                ).distinct()
        val urlLookup =
            OpmlImportLogic.firstExactLookup(urlCandidates) { candidate ->
                lookup(ExactPodcastLookupKey(ExactPodcastLookupType.FEED_URL, candidate))
            }
        val guidLookup =
            snapshot.podcastGuid
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { guid ->
                    lookup(ExactPodcastLookupKey(ExactPodcastLookupType.PODCAST_GUID, guid))
                } ?: ExactPodcastLookupResult.NotFound
        return LegacyRssRepairLogic.selectMatch(urlLookup, guidLookup)
    }

    private suspend fun migrate(oldPodcastId: String, target: Podcast, snapshot: LegacyRssFeedSnapshot,): Boolean {
        userPreferences.beginPodcastIdRepair(oldPodcastId, target.id)
        return try {
            when (
                rssRepository.legacySubscriptionRepair.upgrade(
                    sourcePodcastId = oldPodcastId,
                    target = target,
                    snapshot = snapshot,
                )
            ) {
                LegacyRssUpgradeOutcome.MIGRATED,
                LegacyRssUpgradeOutcome.ALREADY_MIGRATED,
                -> {
                    finishIdRepair(oldPodcastId, target.id)
                    restoreNotificationRouting(target)
                    true
                }
                LegacyRssUpgradeOutcome.SOURCE_NOT_ELIGIBLE,
                LegacyRssUpgradeOutcome.TARGET_ALREADY_IN_USE,
                LegacyRssUpgradeOutcome.IDENTITY_MISMATCH,
                LegacyRssUpgradeOutcome.INCOMPLETE_CATALOG,
                -> {
                    userPreferences.cancelPodcastIdRepair(oldPodcastId, target.id)
                    true
                }
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun reconcilePendingRepair(pending: PendingPodcastIdRepair?) {
        pending ?: return
        val source = podcastDao.getPodcast(pending.oldPodcastId)
        val target = podcastDao.getPodcast(pending.newPodcastId)
        when {
            source != null && LegacyRssRepairLogic.isEligibleSource(source) -> Unit
            target?.isSubscribed == true && !target.isRss ->
                finishIdRepair(pending.oldPodcastId, pending.newPodcastId)
            else ->
                userPreferences.cancelPodcastIdRepair(
                    pending.oldPodcastId,
                    pending.newPodcastId,
                )
        }
    }

    private suspend fun finishIdRepair(oldPodcastId: String, newPodcastId: String,) {
        try {
            adaptiveRanking.migrateShowFacet(oldPodcastId, newPodcastId)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not migrate legacy RSS ranking affinity", error)
        }
        userPreferences.finishPodcastIdRepair(oldPodcastId, newPodcastId)
        boxcastPrefs.clearBylCacheIfPodcastId(oldPodcastId)
    }

    private suspend fun restoreNotificationRouting(target: Podcast) {
        val row = podcastDao.getPodcast(target.id) ?: return
        if (!row.notificationsEnabled) return
        try {
            runtime.restoreNotifications(
                target.copy(feedUrl = row.feedUrl ?: target.feedUrl),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "Could not restore notification routing after RSS repair", error)
        }
    }

    companion object {
        private const val TAG = "LegacyRssRepair"
        private const val REPAIR_VERSION = 1

        fun create(catalog: LegacyRssRepairCatalog, runtime: LegacyRssRepairRuntime,): LegacyRssRepair = LegacyRssRepair(
            catalog = catalog,
            runtime = runtime,
            lookup = catalog.podcastRepository::lookupExactPodcastIndex,
        )
    }
}
