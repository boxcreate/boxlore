package cx.aswin.boxlore.core.network.session

import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Mutex

/**
 * One Turnstile check at a time for [ApiSessionManager], together with the exchange its token goes
 * into: the sign-in check ([ApiSessionSignIn]) and the route step-up ([ApiSessionStepUp]) both take
 * a turn here, so the listener never gets two checks in a row. A caller that had to wait for the
 * other learns what it did ([Turn]): it got a session that isn't limited (use that one), or its
 * check failed or was skipped (don't ask again now).
 *
 * It also keeps the listener's Skip: for [ApiSessionTiming.checkSkipHoldMs] afterwards, sign-in
 * checks nothing needs right now don't ask ([skipHeld]), across foregrounds.
 */
internal class ApiSessionCheckGate(
    private val clock: () -> Long,
    private val timing: ApiSessionTiming,
    /** The session the manager holds now. */
    private val held: () -> ApiSessionToken?,
) {
    private val mutex = Mutex()
    private val queued = AtomicInteger()

    // Written only by the turn that holds the mutex.
    @Volatile private var misses = 0

    @Volatile private var turnSinceMs: Long? = null

    @Volatile private var skippedUntilMs = 0L

    /** What a caller may do with its turn. */
    sealed interface Turn {
        /** Run the check. */
        data object Go : Turn

        /** The turn before this one got [token], which isn't limited: use it instead of a check. */
        data class Covered(val token: ApiSessionToken) : Turn

        /** The turn before this one ran a check that failed or was skipped: don't ask again now. */
        data object Declined : Turn
    }

    /** When the turn holding the gate began, while one does. */
    val busySinceMs: Long? get() = turnSinceMs

    /** Callers waiting for a turn. */
    val waiting: Int get() = queued.get()

    /** Runs [block] alone, with what this turn may do; only a caller that had to wait gets anything but [Turn.Go]. */
    suspend fun <T> withTurn(block: suspend (Turn) -> T): T {
        val before = held()?.raw
        val seen = misses
        val waited = !mutex.tryLock()
        if (waited) {
            queued.incrementAndGet()
            try {
                mutex.lock()
            } finally {
                queued.decrementAndGet()
            }
        }
        try {
            turnSinceMs = clock()
            return block(if (waited) afterWaiting(before, seen) else Turn.Go)
        } finally {
            turnSinceMs = null
            mutex.unlock()
        }
    }

    private fun afterWaiting(before: String?, seen: Int): Turn {
        val now = held()
        return when {
            now != null && !now.limited && now.raw != before -> Turn.Covered(now)
            misses != seen -> Turn.Declined
            else -> Turn.Go
        }
    }

    /** A check this turn ran ended without a session better than limited; [skipped] when the listener skipped it. */
    fun missed(skipped: Boolean) {
        misses++
        if (skipped) skippedUntilMs = clock() + timing.checkSkipHoldMs
    }

    /** The listener skipped a check within [ApiSessionTiming.checkSkipHoldMs]. */
    fun skipHeld(): Boolean = clock() < skippedUntilMs
}
