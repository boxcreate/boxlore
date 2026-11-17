package cx.aswin.boxlore.core.catalog.backup

import cx.aswin.boxlore.core.catalog.ApiRouteBudget
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One OPML run at a time in the process: the import screen's and the foreground catch-up's share
 * pending outlines. The screen comes first: while a screen import waits for the gate, the catch-up
 * is told to stop ([catchUp]'s `yieldToScreen`) after the outline it is on, leaving the rest pending.
 */
internal class OpmlRunGate {
    private val lock = Mutex()
    private val screensWaiting = AtomicInteger()

    /** Runs the import screen's [block] once the gate is free, asking a running catch-up to stop. */
    suspend fun <T> screen(block: suspend () -> T): T {
        screensWaiting.incrementAndGet()
        var waiting = true
        try {
            return lock.withLock {
                screensWaiting.decrementAndGet()
                waiting = false
                block()
            }
        } finally {
            if (waiting) screensWaiting.decrementAndGet()
        }
    }

    /** Runs the catch-up's [block] unless another run holds the gate (null then); `yieldToScreen` turns true once a screen import waits. */
    suspend fun <T> catchUp(block: suspend (yieldToScreen: () -> Boolean) -> T): T? {
        if (!lock.tryLock()) return null
        try {
            return block { screensWaiting.get() > 0 }
        } finally {
            lock.unlock()
        }
    }
}

/**
 * The import screen's pacing: each request waits for room on its route, but only until [budgetMs]
 * after the screen started. Past that, or when room wouldn't come in time, nothing more is sent
 * ([pace] refuses and [expired] is true): the outlines left stay pending and the foreground
 * catch-up finishes them, so a large file never keeps the Importing screen up for long.
 */
internal class OpmlScreenPace(
    private val budget: ApiRouteBudget,
    private val nowMs: () -> Long = System::currentTimeMillis,
    budgetMs: Long = SCREEN_BUDGET_MS,
) {
    private val deadlineMs = nowMs() + budgetMs

    fun expired(): Boolean = nowMs() >= deadlineMs

    suspend fun pace(route: String): Boolean = budget.acquireBefore(route, deadlineMs)

    companion object {
        /** How long the Importing screen sends requests: about 50 at 25 a minute. */
        const val SCREEN_BUDGET_MS = 2 * 60_000L
    }
}

/**
 * The foreground catch-up's pacing: each request waits for room on its route, checking at least
 * every [POLL_MS] whether to give way ([yieldToScreen]) so an import the listener starts doesn't
 * wait out the catch-up's pause. Giving way refuses the request, and the run stops.
 */
internal class OpmlCatchUpPace(
    private val budget: ApiRouteBudget,
    private val yieldToScreen: () -> Boolean,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun pace(route: String): Boolean {
        while (!yieldToScreen()) {
            if (budget.tryAcquire(route)) return true
            sleep(budget.waitMs(route).coerceIn(1L, POLL_MS))
        }
        return false
    }

    companion object {
        const val POLL_MS = 1_000L
    }
}
