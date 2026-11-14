package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.feature.explore.logic.ExploreForYouRefreshPolicy.Action
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Runs the For you refreshes [policy] decides on, in [scope]: one at a time, a newer one cancelling
 * the older. [fetch] returns picks for a key, or null when the request brought none (the shown
 * picks stay); [publish] shows them. Picks [isFresh] rejects (the last good picks kept from
 * earlier) are shown but count as a failure, so they are asked for again. A cancelled refresh never
 * publishes, even when [fetch] could not be interrupted. [onLoadingChanged] is true while a refresh
 * runs for a key not asked for before; retries and stale refreshes run without it, so For you never
 * flips back to its loading state while it waits for the server.
 *
 * Call it from the thread [scope] runs on.
 */
class ExploreForYouRefresher<T : Any>(
    private val scope: CoroutineScope,
    private val clockMs: () -> Long,
    private val fetch: suspend (ExploreForYouRefreshKey) -> T?,
    private val publish: (T) -> Unit,
    private val onLoadingChanged: (Boolean) -> Unit,
    private val isFresh: (T) -> Boolean = { true },
    private val policy: ExploreForYouRefreshPolicy = ExploreForYouRefreshPolicy(),
) {
    private var refreshJob: Job? = null
    private var wakeJob: Job? = null
    private var generation = 0

    /**
     * Follows the key's inputs until cancelled. [followedIds] may repeat the same set (a database
     * re-emitting after an unrelated write); an unchanged key asks nothing.
     */
    suspend fun follow(
        region: Flow<String>,
        followedIds: Flow<Set<String>>,
        languages: Flow<List<String>>,
        pickedForYou: Flow<Boolean>,
    ) {
        combine(region, followedIds, languages, pickedForYou) { code, ids, langs, on -> ExploreForYouRefreshKey.of(code, ids, langs, on) }
            .distinctUntilChanged()
            .collect { onKey(it) }
    }

    /** The latest key; null while Picked for you is off. */
    fun onKey(key: ExploreForYouRefreshKey?) = act(policy.onKey(key, clockMs()))

    /** Whether the Explore screen is visible. */
    fun onVisibilityChanged(visible: Boolean) = act(policy.onVisibilityChanged(visible, clockMs()))

    private fun act(action: Action) {
        wakeJob?.cancel()
        wakeJob = null
        when (action) {
            Action.None -> Unit
            is Action.Cancel -> {
                cancelRefresh()
                action.wakeAtMs?.let(::wakeAt)
            }
            is Action.Start -> start(action.key, action.quiet)
            is Action.WaitUntil -> wakeAt(action.atMs)
        }
    }

    private fun cancelRefresh() {
        generation++
        refreshJob?.cancel()
        refreshJob = null
        onLoadingChanged(false)
    }

    private fun start(key: ExploreForYouRefreshKey, quiet: Boolean) {
        refreshJob?.cancel()
        val mine = ++generation
        onLoadingChanged(!quiet)
        // Started after it is recorded, so a dispatcher that runs it at once still sees itself as current.
        val job = scope.launch(start = CoroutineStart.LAZY) { run(key, mine) }
        refreshJob = job
        job.start()
    }

    private suspend fun run(key: ExploreForYouRefreshKey, mine: Int) {
        val picks =
            try {
                fetch(key)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
        if (picks != null) {
            // A cancelled refresh can still return from a call that ignored cancellation.
            currentCoroutineContext().ensureActive()
            if (mine != generation) return
            publish(picks)
        }
        if (mine != generation) return
        refreshJob = null
        onLoadingChanged(false)
        act(policy.onFinished(key, fresh = picks != null && isFresh(picks), nowMs = clockMs()))
    }

    private fun wakeAt(atMs: Long) {
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                delay((atMs - clockMs()).coerceAtLeast(0L))
                wakeJob = null
                act(policy.onWake(clockMs()))
            }
        wakeJob = job
        job.start()
    }
}
