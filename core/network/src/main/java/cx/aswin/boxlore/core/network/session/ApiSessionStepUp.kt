package cx.aswin.boxlore.core.network.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The Turnstile step-up for a limited session on an AI route. One step-up runs at a time and
 * concurrent AI requests join it. A failure starts a cooldown so later AI requests go straight to
 * onboarding's non-AI fallback instead of asking again.
 */
internal class ApiSessionStepUp(
    private val scope: CoroutineScope,
    private val clock: () -> Long,
    private val timing: ApiSessionTiming,
    private val listener: ApiSessionListener,
    private val exchange: suspend (ApiSessionTrigger, Boolean?, String?) -> ApiSessionToken?,
    private val usableToken: () -> ApiSessionToken?,
) {
    @Volatile
    var source: TurnstileTokenSource? = null

    @Volatile
    var enabled: () -> Boolean = { false }

    private val lock = Any()
    private var job: Deferred<ApiSessionToken?>? = null
    private var cooldownUntilMs = 0L

    /**
     * A session good enough for AI routes, or null when the caller should use its fallback.
     * With [joinRunning] false, a caller that finds a step-up already running gets null at once
     * instead of waiting on it; the step-up still completes for later requests.
     */
    suspend fun run(joinRunning: Boolean = true): ApiSessionToken? {
        usableToken()?.takeIf { !it.limited }?.let { return it }
        val turnstile = source ?: return null
        if (!runCatching { enabled() }.getOrDefault(false)) return null
        val running = synchronized(lock) {
            val active = job?.takeIf { it.isActive }
            when {
                active != null -> active.takeIf { joinRunning }
                clock() < cooldownUntilMs -> null
                else -> scope.async { attempt(turnstile) }.also { job = it }
            }
        } ?: return null
        return running.await()
    }

    private suspend fun attempt(turnstile: TurnstileTokenSource): ApiSessionToken? {
        val started = clock()
        val solved: TurnstileResult = nonCancellationFailure<TurnstileResult>({ TurnstileResult.Failed("error") }) {
            withTimeoutOrNull(timing.stepUpTimeoutMs) { turnstile.solve() } ?: TurnstileResult.Failed("timeout")
        }
        val turnstileToken = when (solved) {
            is TurnstileResult.Failed -> return fail(ApiSessionStepUpOutcome.ChallengeFailed, solved.reason, started)
            is TurnstileResult.Token -> solved.value
        }
        val token = exchange(ApiSessionTrigger.StepUp, false, turnstileToken)
        if (token == null || token.limited) return fail(ApiSessionStepUpOutcome.NotAccepted, token?.let { "limited" } ?: "exchange_failed", started)
        listener.safely { onStepUp(ApiSessionStepUpOutcome.Passed, null, clock() - started) }
        return token
    }

    private fun fail(outcome: ApiSessionStepUpOutcome, reason: String, started: Long): ApiSessionToken? {
        synchronized(lock) { cooldownUntilMs = clock() + timing.stepUpCooldownMs }
        listener.safely { onStepUp(outcome, reason, clock() - started) }
        return null
    }
}
