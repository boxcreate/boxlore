package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.transform

/** One Home pass's ranking decision, made before the pass assembles. */
internal data class HomeRankingPlan(
    val previousOrder: List<String>?,
    /** The Daily Mix to keep, or null to rebuild it this pass. */
    val previousMixtape: HomeMixtapeCache?,
    val refreshOrder: Boolean,
    /** Discovery rows re-rank only when this changes, never on raw history. */
    val discoveryPass: Long,
    val listening: HomeListeningSignature,
    val generation: Long,
) {
    val ranksShows: Boolean get() = previousOrder == null || refreshOrder

    /** Scoring is the expensive step, so it runs only when something will be ranked. */
    val needsScores: Boolean get() = ranksShows || previousMixtape == null
}

/**
 * Decides when Your shows, Daily Mix and the discovery rows are ranked. Ranking on every playback
 * tick made Home stutter, so a pass ranks only:
 * - the first time Home assembles;
 * - when a re-rank was requested: a settled sync or import, a return to Home after listening
 *   changed, or a snapshot older than 30 minutes;
 * - Daily Mix alone, when the restart setting changes.
 * Subscriptions added or removed between ranks update the covers without scoring: new shows join
 * the front, and removed shows leave the covers and Daily Mix.
 */
internal class HomeYourShowsRanker {
    @Volatile var order: List<String>? = null
        private set

    @Volatile private var orderCreatedAtMs = 0L

    @Volatile private var appliedGeneration = 0L

    @Volatile private var latestListening: HomeListeningSignature? = null

    @Volatile private var appliedListening: HomeListeningSignature? = null

    @Volatile private var mixtape: HomeMixtapeCache? = null

    @Volatile private var rankingPasses = 0L

    /** Whether a return to Home should request one re-rank. */
    fun shouldRequestRefresh(requestedGeneration: Long, nowMs: Long): Boolean {
        val listening = latestListening
        return HomeShowsRefreshPolicy.shouldRequestRefresh(
            hasStableOrder = order != null,
            hasPendingRequest = requestedGeneration > appliedGeneration,
            snapshotCreatedAtMs = orderCreatedAtMs,
            nowMs = nowMs,
            listeningChanged = listening != null && listening != appliedListening,
        )
    }

    fun plan(
        subIds: Set<String>,
        history: List<HomeListeningHistoryItem>,
        staleRestartEnabled: Boolean,
        requestedGeneration: Long,
    ): HomeRankingPlan {
        val listening = HomeYourShowsSignals.listening(history)
        latestListening = listening
        val refresh = requestedGeneration > appliedGeneration
        val previousOrder = order
        val ranksShows = previousOrder == null || refresh
        return HomeRankingPlan(
            previousOrder = previousOrder,
            // A re-rank also rebuilds Daily Mix, so new episodes and shows join it.
            previousMixtape = if (refresh) null else HomeUiAssemblyLogic.reusableMixtape(mixtape, subIds, staleRestartEnabled),
            refreshOrder = refresh,
            discoveryPass = if (ranksShows) rankingPasses + 1 else rankingPasses,
            listening = listening,
            generation = requestedGeneration,
        )
    }

    fun commit(plan: HomeRankingPlan, order: List<String>, mixtape: HomeMixtapeCache?, nowMs: Long) {
        if (plan.ranksShows) {
            orderCreatedAtMs = nowMs
            appliedGeneration = plan.generation
            appliedListening = plan.listening
            rankingPasses = plan.discoveryPass
        }
        this.order = order
        if (mixtape != null) this.mixtape = mixtape
    }
}

/**
 * Turns catalog signatures into re-rank requests: one per settled burst (an import or cloud sync
 * writes many rows), and at most one per [minGapMs] while a long sync keeps trickling. The latest
 * change always gets its request. The first signature is skipped, since the first pass ranks it.
 */
@OptIn(FlowPreview::class)
internal fun Flow<Map<String, String>>.settledCatalogChanges(
    settleMs: Long = HomeShowsRefreshPolicy.CATALOG_SETTLE_MS,
    minGapMs: Long = HomeShowsRefreshPolicy.CATALOG_MIN_GAP_MS,
): Flow<Unit> = distinctUntilChanged()
    .drop(1)
    .debounce(settleMs)
    .conflate()
    .transform {
        emit(Unit)
        delay(minGapMs)
    }
