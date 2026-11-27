package cx.aswin.boxlore.core.network.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Turnstile step-up for a limited session on a route that answered `verification_required`
 * (AI onboarding, and what a read-only limited session may not do). One step-up runs at a time and
 * concurrent requests join it. It starts only while a host is on screen: a background request gets
 * its 401 back at once, with no cooldown. A failure starts a cooldown, separate from the sign-in
 * check's, so later requests go straight to their fallback instead of asking again, and so does the
 * listener's Skip of any check ([ApiSessionCheckGate.skipHeld]); the listener's own asks (lending
 * the account to a watch or TV) may still ask. Its check takes its turn with the sign-in check's
 * ([ApiSessionCheckGate]); a step-up that waited its turn while the app left the screen gives up
 * then, without a cooldown.
 */
@Suppress("LongParameterList") // The manager's ports and shared state, passed through for JVM tests.
internal class ApiSessionStepUp(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val timing: ApiSessionTiming,
    private val listener: ApiSessionListener,
    private val exchange: suspend (ApiSessionTrigger, Boolean?, String?) -> ApiSessionToken?,
    private val usableToken: () -> ApiSessionToken?,
    private val gate: ApiSessionCheckGate,
) {
    @Volatile
    var source: TurnstileTokenSource? = null

    /** The app's remote switch for route step-ups; sign-in checks don't read it. */
    @Volatile
    var enabled: () -> Boolean = { true }

    private val lock = Any()
    private var job: Deferred<ApiSessionToken?>? = null
    private var cooldownUntilMs = 0L

    /** Whether a step-up can run at all here: a check source, and the remote switch on (the host may still be away). */
    fun available(): Boolean = source != null && runCatching { enabled() }.getOrDefault(false)

    /**
     * A session good enough for AI routes, or null when the caller should use its fallback.
     * With [joinRunning] false, a caller that finds a step-up already running gets null at once
     * instead of waiting on it; the step-up still completes for later requests. With [pastCooldown]
     * (the listener asked for this) neither a recent failure nor a Skip holds the step-up back.
     */
    suspend fun run(joinRunning: Boolean = true, pastCooldown: Boolean = false): ApiSessionToken? {
        val held = usableToken()
        // A paired watch or TV never reaches AI routes, and has nothing to step up with.
        if (held?.attestation == ApiAttestation.Paired) return null
        held?.takeIf { !it.limited }?.let { return it }
        val turnstile = source ?: return null
        if (!available()) return null
        if (!hostReady(turnstile)) return null
        val running = synchronized(lock) {
            val active = job?.takeIf { it.isActive }
            when {
                active != null -> active.takeIf { joinRunning }
                !pastCooldown && (clock() < cooldownUntilMs || gate.skipHeld()) -> null
                else -> scope.async { attempt(turnstile) }.also { job = it }
            }
        } ?: return null
        return running.await()
    }

    private suspend fun attempt(turnstile: TurnstileTokenSource): ApiSessionToken? =
        gate.withTurn { turn ->
            when (turn) {
                is ApiSessionCheckGate.Turn.Covered -> turn.token
                // The sign-in check just failed or was skipped: don't put another one on screen now.
                ApiSessionCheckGate.Turn.Declined -> null
                // A turn that waited may find the app gone from the screen: no check can run now, and that's no failure.
                ApiSessionCheckGate.Turn.Go -> if (hostReady(turnstile)) checked(turnstile) else null
            }
        }

    private suspend fun hostReady(turnstile: TurnstileTokenSource): Boolean = nonCancellationFailure({ false }) { turnstile.ready(0L) }

    private suspend fun checked(turnstile: TurnstileTokenSource): ApiSessionToken? {
        val started = clock()
        val solved: TurnstileResult = nonCancellationFailure<TurnstileResult>({ TurnstileResult.Failed("error") }) {
            withTimeoutOrNull(timing.stepUpTimeoutMs) { turnstile.solve() } ?: TurnstileResult.Failed("timeout")
        }
        val turnstileToken = when (solved) {
            is TurnstileResult.Failed -> {
                gate.missed(skipped = solved.reason == TurnstileResult.SKIPPED)
                return fail(ApiSessionStepUpOutcome.ChallengeFailed, solved.reason, started)
            }
            is TurnstileResult.Token -> solved.value
        }
        val token = exchange(ApiSessionTrigger.StepUp, false, turnstileToken)
        if (token == null || token.limited) {
            gate.missed(skipped = false)
            return fail(ApiSessionStepUpOutcome.NotAccepted, token?.let { "limited" } ?: "exchange_failed", started)
        }
        listener.safely { onStepUp(ApiSessionStepUpOutcome.Passed, null, clock() - started) }
        return token
    }

    private fun fail(outcome: ApiSessionStepUpOutcome, reason: String, started: Long): ApiSessionToken? {
        synchronized(lock) { cooldownUntilMs = clock() + timing.stepUpCooldownMs }
        listener.safely { onStepUp(outcome, reason, clock() - started) }
        return null
    }
}
