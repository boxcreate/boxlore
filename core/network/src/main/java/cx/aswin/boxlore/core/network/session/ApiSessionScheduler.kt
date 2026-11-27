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
 * - weaker sessions also get a fresh session (without App Check) before they run out: a day early
 *   for a challenge session (its 7-day window), so the check runs while the app is on screen, an
 *   hour early for a limited one;
 * - a refresh that got nothing better than the attested session held waits
 *   [ApiSessionPolicy.REFRESH_RETRY_MS] before the next ([ApiSessionBook.nextRefreshAtMs]);
 * - a missing session is fetched again with the exchange backoff.
 * Every exchange App Check can't vouch for also runs the Turnstile check, which this foreground
 * work can show ([ApiSessionManager]), so a limited session moves up to a challenge one.
 * A watch or TV ([pairedSignedIn] not null) never asks App Check: signed in, it trades for a paired
 * session as soon as it holds none and again an hour before that one runs out ([ApiSessionPairing]).
 * Consecutive background exchanges are at least [MIN_SPACING_MS] apart.
 */
internal class ApiSessionScheduler(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val lock: Any,
    private val book: ApiSessionBook,
    private val exchange: suspend (ApiSessionTrigger, Boolean?, String?) -> ApiSessionToken?,
    /** Null on a phone; on a watch or TV, whether it is signed in. Not called under [lock]. */
    private val pairedSignedIn: () -> Boolean? = { null },
) {
    private var foreground = false
    private var online = true
    private var loaded = false
    private var job: Job? = null

    fun setForeground(value: Boolean) {
        synchronized(lock) { foreground = value }
        reschedule()
    }

    fun isForeground(): Boolean = synchronized(lock) { foreground }

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
            val paired = pairedSignedIn()
            val step = synchronized(lock) { nextStep(book, clock(), paired) }
            val wait = step.atMs - clock()
            if (wait > 0) delay(wait)
            synchronized(lock) { book.lastMaintenanceAtMs = clock() }
            // The exchange reschedules this loop when it finishes; a cancelled loop just stops here.
            exchange(step.trigger, step.useAppCheck, null)
        }
    }

    companion object {
        const val MIN_SPACING_MS = 60_000L

        /**
         * Pure plan for the next background exchange from [book] at local time [nowMs]. [pairedSignedIn]
         * is null on a phone; on a watch or TV it says whether the device is signed in.
         */
        fun nextStep(book: ApiSessionBook, nowMs: Long, pairedSignedIn: Boolean? = null): ApiSessionStep {
            val floor = maxOf(nowMs, book.nextExchangeAtMs, book.lastMaintenanceAtMs + MIN_SPACING_MS)
            if (pairedSignedIn != null) return pairedStep(book, floor, pairedSignedIn)
            val token = book.usableToken
                ?: return ApiSessionStep(floor, ApiSessionTrigger.Retry, useAppCheck = book.appCheckFailures == 0 || nowMs >= book.nextUpgradeAtMs)
            val refreshMs = maxOf(book.toLocalMs(ApiSessionPolicy.refreshAt(token)), book.nextRefreshAtMs)
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

        /**
         * A watch or TV: signed in without a paired session, trade (with the backoff); otherwise
         * replace the held session an hour before it runs out. No App Check upgrades.
         */
        private fun pairedStep(book: ApiSessionBook, floor: Long, signedIn: Boolean): ApiSessionStep {
            val token = book.usableToken
            if (token == null || (signedIn && token.attestation != ApiAttestation.Paired)) {
                return ApiSessionStep(floor, ApiSessionTrigger.Retry, useAppCheck = false)
            }
            return ApiSessionStep(maxOf(floor, book.toLocalMs(ApiSessionPolicy.refreshAt(token))), ApiSessionTrigger.Refresh, useAppCheck = false)
        }
    }
}
