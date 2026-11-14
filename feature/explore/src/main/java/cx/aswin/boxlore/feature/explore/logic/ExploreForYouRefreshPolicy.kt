package cx.aswin.boxlore.feature.explore.logic

/**
 * What a For you refresh asks for. [followedIds] holds Podcast Index shows only: the request
 * leaves `rss:` shows out, so following or unfollowing one changes nothing. [languages] is the
 * normalised content-language list the request carries; it leaves English out exactly when the
 * listener switched English off, so that choice is part of it.
 */
data class ExploreForYouRefreshKey(
    val region: String,
    val followedIds: Set<String>,
    val languages: List<String>,
) {
    /** True when [other] asks for the same region and languages, so at most its followed shows differ. */
    fun hasSameRegionAndLanguages(other: ExploreForYouRefreshKey): Boolean = region == other.region && languages == other.languages

    companion object {
        /** The key for these inputs, or null while Picked for you is off. */
        fun of(region: String, followedIds: Set<String>, languages: List<String>, pickedForYou: Boolean): ExploreForYouRefreshKey? =
            if (pickedForYou) {
                ExploreForYouRefreshKey(region, followedIds.filterNot { it.startsWith(RSS_PREFIX) }.toSet(), languages)
            } else {
                null
            }

        private const val RSS_PREFIX = "rss:"
    }
}

/**
 * Decides when Explore asks for its For you picks again, so a burst of follows (a sign-in restore,
 * an OPML import) or a screen kept alive off-screen never floods the recommendations API, while
 * the picks still stay fresh.
 *
 * - Nothing is asked while the screen is stopped (another tab or screen in front; a player sheet or
 *   detail pane drawn over it does not stop it). Starting it again applies the rules below to what
 *   changed meanwhile; with nothing changed (a rotation, a resize, a fold) it asks nothing.
 * - The first key, a region or language change and switching Picked for you on refresh at once. A
 *   change of the followed shows alone refreshes at most once per [setChangeIntervalMs] from the
 *   previous start, and the last refresh always carries the latest key.
 * - Picks older than [staleAfterMs] since the last fresh answer are asked for again once: when the
 *   screen shows, or while it stays visible.
 * - A failed refresh, or one that only brought the last good picks back, is tried again after
 *   [retryIntervalMs], doubling after each further failure up to [maxRetryIntervalMs]. A key change
 *   or a fresh answer starts the backoff again.
 * - Switching Picked for you off cancels a running refresh.
 *
 * Every call takes the time in milliseconds from one clock and returns what to do next. It is not
 * thread-safe: call it from one thread.
 */
class ExploreForYouRefreshPolicy(
    private val setChangeIntervalMs: Long = SET_CHANGE_INTERVAL_MS,
    private val retryIntervalMs: Long = RETRY_INTERVAL_MS,
    private val maxRetryIntervalMs: Long = MAX_RETRY_INTERVAL_MS,
    private val staleAfterMs: Long = STALE_AFTER_MS,
) {
    /** What the caller does next. Each action replaces any wake-up asked for earlier. */
    sealed interface Action {
        /** Nothing to start; a running refresh carries on. */
        data object None : Action

        /** Cancel the running refresh and start nothing; call [onWake] at [wakeAtMs] when set. */
        data class Cancel(val wakeAtMs: Long? = null) : Action

        /**
         * Start a refresh for [key], cancelling a running one. [quiet] when the key was asked for
         * before (a retry or a stale refresh): For you then shows no loading state for it.
         */
        data class Start(val key: ExploreForYouRefreshKey, val quiet: Boolean) : Action

        /** Call [onWake] at [atMs]. */
        data class WaitUntil(val atMs: Long) : Action
    }

    private var visible = false
    private var latest: ExploreForYouRefreshKey? = null
    private var running: ExploreForYouRefreshKey? = null
    private var lastStarted: ExploreForYouRefreshKey? = null
    private var lastStartAtMs = 0L
    private var refreshed: ExploreForYouRefreshKey? = null
    private var refreshedAtMs = 0L
    private var failed: ExploreForYouRefreshKey? = null
    private var failedAtMs = 0L
    private var failures = 0

    /** The latest key; null while Picked for you is off. */
    fun onKey(key: ExploreForYouRefreshKey?, nowMs: Long): Action {
        if (key == null) {
            // Switched off: the picks are cleared, so switching on again refreshes at once.
            lastStarted = null
            refreshed = null
        }
        if (key != failed) clearFailures()
        latest = key
        return next(nowMs)
    }

    /** Whether the Explore screen is visible (started, so split-screen counts). */
    fun onVisibilityChanged(isVisible: Boolean, nowMs: Long): Action {
        visible = isVisible
        return next(nowMs)
    }

    /**
     * The refresh [Action.Start] asked for ended; [fresh] when it brought a fresh answer (not a
     * failure and not the last good picks kept from earlier).
     */
    fun onFinished(key: ExploreForYouRefreshKey, fresh: Boolean, nowMs: Long): Action {
        if (running == key) running = null
        if (fresh) {
            refreshed = key
            refreshedAtMs = nowMs
            clearFailures()
        } else {
            failures = if (key == failed) failures + 1 else 1
            failed = key
            failedAtMs = nowMs
        }
        return next(nowMs)
    }

    /** The time [Action.WaitUntil] or [Action.Cancel] asked for came. */
    fun onWake(nowMs: Long): Action = next(nowMs)

    private fun next(nowMs: Long): Action {
        val key = latest ?: return cancelRunning(wakeAtMs = null)
        if (!visible || key == running) return Action.None
        val readyAtMs =
            when (key) {
                failed -> failedAtMs + retryDelayMs()
                refreshed -> refreshedAtMs + staleAfterMs
                else -> newKeyReadyAtMs(key, nowMs)
            }
        if (readyAtMs > nowMs) {
            // Back to picks that are still fresh: a refresh for an older change is no longer wanted.
            return if (key == refreshed && running != null) cancelRunning(readyAtMs) else Action.WaitUntil(readyAtMs)
        }
        val quiet = key == failed || key == refreshed
        running = key
        lastStarted = key
        lastStartAtMs = nowMs
        return Action.Start(key, quiet)
    }

    /** The first key, Picked for you switched on, or a region or language change: at once. */
    private fun newKeyReadyAtMs(key: ExploreForYouRefreshKey, nowMs: Long): Long {
        val previous = lastStarted ?: return nowMs
        return if (key.hasSameRegionAndLanguages(previous)) lastStartAtMs + setChangeIntervalMs else nowMs
    }

    /** [retryIntervalMs], doubled for each failure after the first, at most [maxRetryIntervalMs]. */
    private fun retryDelayMs(): Long {
        var delayMs = retryIntervalMs
        repeat(failures - 1) {
            if (delayMs >= maxRetryIntervalMs) return maxRetryIntervalMs
            delayMs *= 2
        }
        return delayMs.coerceAtMost(maxRetryIntervalMs)
    }

    private fun clearFailures() {
        failed = null
        failures = 0
    }

    private fun cancelRunning(wakeAtMs: Long?): Action {
        if (running == null) return wakeAtMs?.let { Action.WaitUntil(it) } ?: Action.None
        running = null
        return Action.Cancel(wakeAtMs)
    }

    companion object {
        const val SET_CHANGE_INTERVAL_MS = 30_000L
        const val RETRY_INTERVAL_MS = 60_000L
        const val MAX_RETRY_INTERVAL_MS = 30 * 60_000L
        const val STALE_AFTER_MS = 30 * 60 * 1000L
    }
}
