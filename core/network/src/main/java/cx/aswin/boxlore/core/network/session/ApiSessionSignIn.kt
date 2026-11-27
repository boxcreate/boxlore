package cx.aswin.boxlore.core.network.session

import kotlinx.coroutines.withTimeoutOrNull

/** What a sign-in got: the best [token] it was issued, and whether the API last refused it for want of attestation. */
internal data class ApiSignInOutcome(val token: ApiSessionToken?, val refused: Boolean)

/**
 * One `/auth/session` sign-in for [ApiSessionManager]: App Check when asked, else (or when it fails)
 * the Turnstile check, both sent in one exchange. If the API then refuses (`attestation_required`)
 * or gives only a limited session although App Check went, the check runs and the exchange is
 * asked once more. A route step-up brings its own Turnstile token and skips both.
 *
 * The check runs only while its host is on screen ([TurnstileTokenSource.ready]); a failed or
 * refused check holds later sign-in checks back for [ApiSessionTiming.signInCheckCooldownMs]
 * (the manager lifts that in the foreground). Book state is read and written under [lock].
 */
@Suppress("LongParameterList") // The manager's ports and shared state, passed through for JVM tests.
internal class ApiSessionSignIn(
    private val exchanger: SessionExchanger,
    private val appCheck: AppCheckTokenSource,
    private val lock: Any,
    private val book: ApiSessionBook,
    private val clock: () -> Long,
    private val timing: ApiSessionTiming,
    private val listener: ApiSessionListener,
    private val turnstile: () -> TurnstileTokenSource?,
) {
    suspend fun run(trigger: ApiSessionTrigger, useAppCheck: Boolean, turnstileToken: String?, installId: String, publicKey: String?): ApiSignInOutcome {
        val started = clock()
        val appCheckResult = if (useAppCheck) fetchAppCheck(trigger) else null
        val appCheckToken = (appCheckResult as? AppCheckResult.Token)?.value
        val checkMayHelp = turnstileToken == null && checkHelps(trigger)
        val check = when {
            turnstileToken != null -> CheckRun(turnstileToken, CheckRun.SENT)
            appCheckToken == null && checkMayHelp -> signInCheck()
            else -> null
        }
        val first = exchangeOnce(
            Attempt(trigger, started, SessionExchangeRequest(installId, publicKey, appCheckToken, check?.token), appCheckResult, check, fromSignInCheck = turnstileToken == null),
        )
        // App Check went and the API didn't take it: ask Turnstile and exchange once more.
        val retry = if (check == null && checkMayHelp && first.needsAttestation) signInCheck() else null
        val last = retry?.token?.let { token ->
            exchangeOnce(Attempt(trigger, clock(), SessionExchangeRequest(installId, publicKey, null, token), null, retry, fromSignInCheck = true))
        } ?: first
        return ApiSignInOutcome(last.token ?: first.token, last.refused)
    }

    private suspend fun fetchAppCheck(trigger: ApiSessionTrigger): AppCheckResult {
        val waitMs = if (trigger in BACKGROUND_TRIGGERS) timing.backgroundAppCheckTimeoutMs else timing.appCheckTimeoutMs
        return nonCancellationFailure<AppCheckResult>({ AppCheckResult.Failed("error") }) {
            withTimeoutOrNull(waitMs) { appCheck.fetch() } ?: AppCheckResult.Failed("timeout")
        }
    }

    /** Turnstile helps unless this is an App Check upgrade of a session that already passed a check. */
    private fun checkHelps(trigger: ApiSessionTrigger): Boolean =
        trigger != ApiSessionTrigger.Upgrade || synchronized(lock) { book.usableToken?.limited != false }

    /** Runs the sign-in check when a host is on screen and no recent failure holds it back. */
    private suspend fun signInCheck(): CheckRun {
        val source = turnstile() ?: return CheckRun(null, null)
        if (clock() < synchronized(lock) { book.signInCheckAfterMs }) return CheckRun(null, CheckRun.COOLDOWN)
        if (!nonCancellationFailure({ false }) { source.ready(timing.checkHostWaitMs) }) return CheckRun(null, CheckRun.NO_HOST)
        val solved: TurnstileResult = nonCancellationFailure<TurnstileResult>({ TurnstileResult.Failed("error") }) {
            withTimeoutOrNull(timing.stepUpTimeoutMs) { source.solve() } ?: TurnstileResult.Failed("timeout")
        }
        return when (solved) {
            is TurnstileResult.Token -> CheckRun(solved.value, CheckRun.SENT)
            is TurnstileResult.Failed -> {
                holdSignInCheck()
                CheckRun(null, solved.reason)
            }
        }
    }

    private fun holdSignInCheck() {
        synchronized(lock) { book.signInCheckAfterMs = clock() + timing.signInCheckCooldownMs }
    }

    /** One `/auth/session` request and its bookkeeping: the session, the backoff, App Check's verdict and analytics. */
    private suspend fun exchangeOnce(attempt: Attempt): Exchanged {
        val request = attempt.request
        val result: SessionExchangeResult = nonCancellationFailure<SessionExchangeResult>({ SessionExchangeResult.Failed("error") }) {
            exchanger.exchange(request)
        }
        val token = (result as? SessionExchangeResult.Issued)?.let { ApiSessionToken.parse(it.session) }
        val refused = token == null && (result as? SessionExchangeResult.Failed)?.code == ApiErrorCodes.ATTESTATION_REQUIRED
        val appCheckPassed = appCheckVerdict(attempt, token, refused)
        synchronized(lock) {
            if (token == null) {
                book.recordExchangeFailure((result as? SessionExchangeResult.Failed)?.retryAfterSeconds)
            } else {
                book.recordExchangeSuccess()
                book.offer(token)
            }
            appCheckPassed?.let(book::recordAppCheck)
        }
        // A pass the API didn't take is no better than a failed check: don't ask again at once.
        val checkNotTaken = refused || token?.limited == true
        if (attempt.fromSignInCheck && request.turnstileToken != null && checkNotTaken) holdSignInCheck()
        listener.safely { onExchange(attempt.event(result, token, clock())) }
        return Exchanged(token, refused)
    }

    /**
     * App Check's verdict, recorded whatever the exchange did so a failing phone isn't asked every
     * time: failed with no token, or a token the API didn't take; null when it can't be told.
     */
    private fun appCheckVerdict(attempt: Attempt, token: ApiSessionToken?, refused: Boolean): Boolean? = when {
        attempt.appCheckResult == null -> null
        attempt.request.appCheckToken == null -> false
        token != null -> token.attestation == ApiAttestation.AppCheck
        refused -> false
        else -> null
    }

    /** One exchange's inputs. */
    private data class Attempt(
        val trigger: ApiSessionTrigger,
        val started: Long,
        val request: SessionExchangeRequest,
        val appCheckResult: AppCheckResult?,
        val check: CheckRun?,
        val fromSignInCheck: Boolean,
    ) {
        fun event(result: SessionExchangeResult, token: ApiSessionToken?, now: Long): ApiSessionExchangeEvent {
            val issued = result as? SessionExchangeResult.Issued
            val failed = result as? SessionExchangeResult.Failed
            return ApiSessionExchangeEvent(
                trigger = trigger,
                success = token != null,
                durationMs = now - started,
                attestation = token?.attestation,
                limited = token?.limited,
                appCheckAttempted = appCheckResult != null,
                appCheckObtained = request.appCheckToken != null,
                appCheckFailure = (appCheckResult as? AppCheckResult.Failed)?.reason,
                failureReason = if (token != null) null else failed?.reason ?: "bad_token",
                serverReason = issued?.reason ?: failed?.serverReason,
                turnstile = check?.note,
            )
        }
    }

    /** A sign-in check's result: [token] when it passed; [note] is its analytics value (see [ApiSessionExchangeEvent.turnstile]). */
    private data class CheckRun(val token: String?, val note: String?) {
        companion object {
            const val SENT = "token"
            const val NO_HOST = "no_host"
            const val COOLDOWN = "cooldown"
        }
    }

    /** What one exchange gave: a [token], or a refusal for want of attestation ([refused]). */
    private data class Exchanged(val token: ApiSessionToken?, val refused: Boolean) {
        /** The API wanted more than it got: no session, or only a limited one. */
        val needsAttestation: Boolean get() = refused || token?.limited == true
    }

    private companion object {
        /** Scheduler exchanges that run while a usable session is held, so nothing waits on them. */
        val BACKGROUND_TRIGGERS = setOf(ApiSessionTrigger.Upgrade, ApiSessionTrigger.Refresh)
    }
}
