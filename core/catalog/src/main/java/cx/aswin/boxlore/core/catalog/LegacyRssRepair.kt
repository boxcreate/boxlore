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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal sealed interface ExactRepairMatch {
    data class Found(
        val podcast: Podcast,
    ) : ExactRepairMatch

    data object NoMatch : ExactRepairMatch

    data object TransientFailure : ExactRepairMatch
}

internal object LegacyRssRepairLogic {
    fun isEligibleSource(source: PodcastEntity): Boolean =
        source.isSubscribed &&
            source.isRss &&
            source.linkedPodcastIndexId == null

    fun shouldStartPass(
        hasEligibleSources: Boolean,
        isOnline: Boolean,
    ): Boolean = hasEligibleSources && isOnline

    fun shouldMarkCompleted(hasPendingIdRepair: Boolean): Boolean = !hasPendingIdRepair

    fun selectMatch(
        urlLookup: ExactPodcastLookupResult,
        guidLookup: ExactPodcastLookupResult,
    ): ExactRepairMatch {
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
    private val podcastDao: PodcastDao,
    private val rssRepository: RssPodcastRepository,
    private val userPreferences: UserPreferencesRepository,
    private val boxcastPrefs: BoxcastPrefs,
    private val adaptiveRanking: AdaptiveRankingRepository,
    private val lookup: suspend (ExactPodcastLookupKey) -> ExactPodcastLookupResult,
    private val isOnline: () -> Boolean,
    private val activation: LegacyRssRepairActivation,
    private val scope: CoroutineScope,
) {
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
            for (source in sources) {
                val snapshot =
                    try {
                        rssRepository.legacySubscriptionRepair.inspect(source.podcastId).getOrThrow()
                    } catch (error: CancellationException) {
                        throw error
                    } catch (_: Exception) {
                        continue
                    }
                when (val match = findExactMatch(snapshot)) {
                    ExactRepairMatch.NoMatch -> Unit
                    ExactRepairMatch.TransientFailure -> Unit
                    is ExactRepairMatch.Found -> {
                        migrate(source.podcastId, match.podcast, snapshot)
                    }
                }
            }

            if (userPreferences.pendingPodcastIdRepair() == null) {
                userPreferences.markLegacyRssRepairVersion(REPAIR_VERSION)
            }
        } finally {
            progressState.value = false
        }
    }

    private suspend fun findExactMatch(snapshot: LegacyRssFeedSnapshot): ExactRepairMatch {
        val urlCandidates =
            (
                OpmlImportLogic.urlLookupCandidates(snapshot.sourceFeedUrl) +
                    OpmlImportLogic.urlLookupCandidates(snapshot.finalFeedUrl)
            ).distinct()
        val urlLookup = firstUrlMatch(urlCandidates)
        val guidLookup =
            snapshot.podcastGuid
                ?.trim()
                ?.takeIf(String::isNotEmpty)
                ?.let { guid ->
                    lookup(ExactPodcastLookupKey(ExactPodcastLookupType.PODCAST_GUID, guid))
                } ?: ExactPodcastLookupResult.NotFound
        return LegacyRssRepairLogic.selectMatch(urlLookup, guidLookup)
    }

    private suspend fun firstUrlMatch(candidates: List<String>): ExactPodcastLookupResult {
        var failed = false
        for (candidate in candidates) {
            when (
                val result =
                    lookup(
                        ExactPodcastLookupKey(
                            type = ExactPodcastLookupType.FEED_URL,
                            value = candidate,
                        ),
                    )
            ) {
                is ExactPodcastLookupResult.Found -> return result
                ExactPodcastLookupResult.Failed -> failed = true
                ExactPodcastLookupResult.NotFound -> Unit
            }
        }
        return if (failed) ExactPodcastLookupResult.Failed else ExactPodcastLookupResult.NotFound
    }

    private suspend fun migrate(
        oldPodcastId: String,
        target: Podcast,
        snapshot: LegacyRssFeedSnapshot,
    ): Boolean {
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

    private suspend fun finishIdRepair(
        oldPodcastId: String,
        newPodcastId: String,
    ) {
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

    companion object {
        private const val TAG = "LegacyRssRepair"
        private const val REPAIR_VERSION = 1

        @Suppress("LongParameterList")
        fun create(
            podcastDao: PodcastDao,
            rssRepository: RssPodcastRepository,
            podcastRepository: PodcastRepository,
            userPreferences: UserPreferencesRepository,
            boxcastPrefs: BoxcastPrefs,
            adaptiveRanking: AdaptiveRankingRepository,
            isOnline: () -> Boolean,
            activation: LegacyRssRepairActivation,
            scope: CoroutineScope,
        ): LegacyRssRepair =
            LegacyRssRepair(
                podcastDao = podcastDao,
                rssRepository = rssRepository,
                userPreferences = userPreferences,
                boxcastPrefs = boxcastPrefs,
                adaptiveRanking = adaptiveRanking,
                lookup = podcastRepository::lookupExactPodcastIndex,
                isOnline = isOnline,
                activation = activation,
                scope = scope,
            )
    }
}
