package cx.aswin.boxlore.core.network.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The next background exchange: when it runs and whether it asks App Check. */
internal data class ApiSessionStep(val atMs: Long, val trigger: ApiSessionTrigger, val useAppCheck: Boolean)

/**
 * Background session upkeep, only while the app is in the foreground with a network:
 * - App Check sessions re-attest a day before their 30-day window ends;
 * - weaker sessions retry App Check about a minute after the first failure, then hourly, then
 *   daily ([ApiSessionPolicy.upgradeDelayMs]), and swap in the full session when it passes;
 * - weaker sessions also get a fresh session (without App Check) an hour before they run out;
 * - a missing session is fetched again with the exchange backoff.
 * Consecutive background exchanges are at least [MIN_SPACING_MS] apart.
 */
internal class ApiSessionScheduler(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val lock: Any,
    private val book: ApiSessionBook,
    private val exchange: suspend (ApiSessionTrigger, Boolean?, String?) -> ApiSessionToken?,
) {
    private var foreground = false
    private var online = true
    private var loaded = false
    private var job: Job? = null

    fun setForeground(value: Boolean) {
        synchronized(lock) { foreground = value }
        reschedule()
    }

    fun setOnline(value: Boolean) {
        synchronized(lock) { online = value }
        reschedule()
    }

    /** Called once the stored session has been read; nothing runs before that. */
    fun markLoaded() {
        synchronized(lock) { loaded = true }
        reschedule()
    }

    /** Recomputes the plan after anything changed: a new token, a failure, or the app's state. */
    fun reschedule() {
        synchronized(lock) {
            job?.cancel()
            job = if (loaded && foreground && online) scope.launch { run() } else null
        }
    }

    private suspend fun run() {
        while (true) {
            val step = synchronized(lock) { nextStep(book, clock()) }
            val wait = step.atMs - clock()
            if (wait > 0) delay(wait)
            synchronized(lock) { book.lastMaintenanceAtMs = clock() }
            // The exchange reschedules this loop when it finishes; a cancelled loop just stops here.
            exchange(step.trigger, step.useAppCheck, null)
        }
    }

    companion object {
        const val MIN_SPACING_MS = 60_000L

        /** Pure plan for the next background exchange from [book] at local time [nowMs]. */
        fun nextStep(book: ApiSessionBook, nowMs: Long): ApiSessionStep {
            val floor = maxOf(nowMs, book.nextExchangeAtMs, book.lastMaintenanceAtMs + MIN_SPACING_MS)
            val token = book.usableToken
                ?: return ApiSessionStep(floor, ApiSessionTrigger.Retry, useAppCheck = book.appCheckFailures == 0 || nowMs >= book.nextUpgradeAtMs)
            val refreshMs = book.toLocalMs(ApiSessionPolicy.refreshAt(token))
            if (token.attestation == ApiAttestation.AppCheck) {
                return ApiSessionStep(maxOf(floor, refreshMs, book.nextUpgradeAtMs), ApiSessionTrigger.Refresh, useAppCheck = true)
            }
            val upgradeMs = if (book.nextUpgradeAtMs > 0L) book.nextUpgradeAtMs else nowMs + ApiSessionPolicy.upgradeDelayMs(1)
            return if (upgradeMs <= refreshMs) {
                ApiSessionStep(maxOf(floor, upgradeMs), ApiSessionTrigger.Upgrade, useAppCheck = true)
            } else {
                ApiSessionStep(maxOf(floor, refreshMs), ApiSessionTrigger.Refresh, useAppCheck = false)
            }
        }
    }
}
