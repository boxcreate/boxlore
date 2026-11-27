package cx.aswin.boxlore.core.network.session

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Bounds on every wait the manager may impose. Tests shorten them. */
data class ApiSessionTiming(
    /** App Check (Play Integrity) gets this long inside an exchange before the app asks without it. */
    val appCheckTimeoutMs: Long = 6_000L,
    /**
     * App Check's limit in background upgrades and refreshes instead. A usable session is held
     * then and no request waits, so slow Play Integrity gets time to finish.
     */
    val backgroundAppCheckTimeoutMs: Long = 30_000L,
    /** A request with no usable session waits this long for the shared exchange. */
    val requestWaitMs: Long = 15_000L,
    /** A request made before the stored session has been read waits this long for the read. */
    val loadWaitMs: Long = 2_000L,
    /** The whole Turnstile run, at sign-in or in a step-up, including any interaction. */
    val stepUpTimeoutMs: Long = 150_000L,
    /**
     * How long the request that started a step-up holds its OkHttp thread. Past this it
     * takes its fallback while the step-up finishes in the background for later requests.
     */
    val stepUpRequestWaitMs: Long = 45_000L,
    /** After a failed step-up, routes that need verification go straight to their fallback for this long. */
    val stepUpCooldownMs: Long = 30 * 60_000L,
    /** A sign-in that needs Turnstile waits this long for the check's host to come up (a cold start on screen). */
    val checkHostWaitMs: Long = 3_000L,
    /**
     * After a sign-in check failed or wasn't accepted, sign-ins go without one for this long, or
     * until the app next comes to the foreground. Route step-ups keep their own cooldown.
     */
    val signInCheckCooldownMs: Long = 10 * 60_000L,
    /** A check that passed but whose exchange didn't get through (no network, 429) holds sign-in checks back this long. */
    val signInCheckRetryMs: Long = 2 * 60_000L,
    /**
     * After the listener skips a check, sign-in checks wait this long, across foregrounds; a refused
     * session (nothing works without the check) and route step-ups the listener's own action needs still ask.
     */
    val checkSkipHoldMs: Long = 30 * 60_000L,
)

/** Headers one request carries: the session and, when renewing, its `X-Session-Proof`. */
data class SessionHeaders(val token: String, val proof: String?)

/**
 * Owns this install's API session (`X-Boxlore-Session`). Nothing here sends the bundled app key.
 *
 * - One shared `POST /auth/session` exchange runs at a time. [start] begins it at launch when no
 *   usable session is stored; requests wait for it only then (a fresh install, or after the 30-day
 *   attestation window). Otherwise requests attach the stored session without waiting.
 * - Renewals are silent: past `exp` a request adds a device-key proof and the API answers with
 *   `X-Session-Renew`, which [captureRenewal] stores.
 * - A sign-in App Check can't vouch for (it failed, or is waiting out its retry backoff) runs the
 *   Turnstile check and sends its token in the same exchange, so the install gets a challenge
 *   session. That needs a host on screen; in the background the exchange takes what the API gives.
 *   When the API refuses to sign in without attestation (`attestation_required`), the app on
 *   screen runs the check and asks once more; in the background it waits for the foreground.
 * - A refused session ([recoverFromRejection]), `attestation_required` included, is replaced once,
 *   then the request is retried.
 * - Weaker sessions keep retrying App Check in the background ([ApiSessionScheduler]); a limited
 *   session on a route that answers `verification_required` can step up with Turnstile
 *   ([stepUpForRoute]) while the app is on screen.
 * - Failed exchanges back off; there is no fallback to the app key.
 * - A watch or TV (built with a [PairedSessionTrader]) uses the API only once signed in: its
 *   requests go out with a paired session it trades its account token for ([ApiSessionPairing]),
 *   kept for a day and traded again an hour before it runs out or when the API refuses it. Until
 *   then only its pairing routes ([PAIRING_ROUTES]) go out, with a limited session; anything else
 *   is answered on the device ([ApiSessionInterceptor]). The device says when it signs in or out
 *   ([onPairedAccountChanged]).
 */
// Ports for App Check, storage, device key, HTTP and analytics stay injectable for JVM tests; one
// owner keeps the phone's sign-ins and a watch's or TV's paired sessions on the same lock and exchange.
@Suppress("LongParameterList", "TooManyFunctions")
class ApiSessionManager(
    apiBaseUrl: String,
    exchanger: SessionExchanger,
    private val store: ApiSessionStore,
    private val deviceKey: DeviceKeySigner,
    appCheck: AppCheckTokenSource,
    private val scope: CoroutineScope,
    private val listener: ApiSessionListener = ApiSessionListener.None,
    private val clock: () -> Long = System::currentTimeMillis,
    private val timing: ApiSessionTiming = ApiSessionTiming(),
    /** A watch's or TV's trade for paired sessions; null on a phone. */
    pairedTrader: PairedSessionTrader? = null,
) {
    private val base: HttpUrl = (apiBaseUrl.trimEnd('/') + "/").toHttpUrl()
    private val lock = Any()
    private val book = ApiSessionBook(clock)
    private val loaded = CompletableDeferred<Unit>()
    private val saveMutex = Mutex()
    private var exchangeJob: Deferred<ApiSessionToken?>? = null
    private val scheduler = ApiSessionScheduler(scope, clock, lock, book, ::exchangeNow) { pairing?.let { it.accountToken() != null } }

    /** The sign-in check's and the step-up's turns; internal for tests. */
    internal val checks = ApiSessionCheckGate(clock, timing) { synchronized(lock) { book.usableToken } }
    private val stepUp = ApiSessionStepUp(scope, clock, timing, listener, ::exchangeNow, { synchronized(lock) { book.usableToken } }, checks)
    private val signIn = ApiSessionSignIn(exchanger, appCheck, lock, book, clock, timing, listener, { stepUp.source }, checks)

    /** Supplies Turnstile tokens for sign-ins and step-ups; null means sessions never use Turnstile. */
    var turnstile: TurnstileTokenSource? by stepUp::source

    /** The remote switch for route step-ups (on unless the app turns it off); sign-in checks ignore it. */
    var stepUpEnabled: () -> Boolean by stepUp::enabled

    /** Signs requests to routes that hold this install's private data (see [RequestProofSigner]). */
    val requestProofs = RequestProofSigner(deviceKey) { synchronized(lock) { book.serverNow } }

    private val pairing = pairedTrader?.let { ApiSessionPairing(it, lock, book, requestProofs, base.encodedPath.trimEnd('/') + PAIRED_SESSION_PATH) }

    /** A watch's or TV's account, read for its token before every trade; set it before [start]. Ignored on a phone. */
    var pairedAccount: PairedSessionAccount?
        get() = pairing?.account
        set(value) {
            pairing?.account = value
        }

    fun start() {
        scope.launch {
            val stored = runCatching { store.load() }.getOrElse { StoredApiSession() }
            val pairedSignedIn = pairing?.accountToken() != null
            synchronized(lock) {
                book.restore(stored)
                // A watch or TV that signed out keeps no paired session.
                if (pairing != null && !pairedSignedIn) book.dropPaired()
                // Started before requests are released, so they all join this one exchange.
                if (book.usableToken == null || (pairedSignedIn && book.usableToken?.attestation != ApiAttestation.Paired)) {
                    joinOrStartExchangeLocked(if (stored.token == null) ApiSessionTrigger.Launch else ApiSessionTrigger.Expired)
                }
            }
            loaded.complete(Unit)
            scheduler.markLoaded()
        }
    }

    /** True for requests to the API this manager signs for, except the exchange itself. */
    fun covers(url: HttpUrl): Boolean =
        url.scheme == base.scheme &&
            url.host == base.host &&
            url.port == base.port &&
            !url.encodedPath.endsWith(AUTH_SESSION_PATH)

    /**
     * Whether a request to [url] goes out only with a paired session: on a watch or TV, every route
     * but its pairing routes ([PAIRING_ROUTES]). Without one, [ApiSessionInterceptor] answers it on the device.
     */
    fun needsPairedSession(url: HttpUrl): Boolean = pairing != null && covers(url) && routeOf(url) !in PAIRING_ROUTES

    /**
     * Headers for one request, without waiting whenever a usable session is stored. Only a missing
     * (or long-expired) session waits, at most [ApiSessionTiming.requestWaitMs], for the shared
     * exchange (longer while that exchange runs a Turnstile check on screen, up to the check's own
     * limit); inside an exchange backoff the request goes out without a session at once.
     * With [pairedOnly] ([needsPairedSession]) only a paired session will do: a signed-in watch or
     * TV waits for its trade, a signed-out one gets null at once.
     */
    fun headersForRequest(pairedOnly: Boolean = false): SessionHeaders? {
        if (!loaded.isCompleted) runBlocking { withTimeoutOrNull(timing.loadWaitMs) { loaded.await() } }
        current(pairedOnly)?.let { return it }
        if (pairedOnly && pairing?.accountToken() == null) return null
        // A paired-only request that joined a limited sign-in (one already running) asks once more, for the trade.
        repeat(if (pairedOnly) 2 else 1) {
            val job = synchronized(lock) {
                val trigger = if (book.token == null) ApiSessionTrigger.Launch else ApiSessionTrigger.Expired
                if (exchangeJob?.isActive == true || book.mayExchange) joinOrStartExchangeLocked(trigger) else null
            } ?: return current(pairedOnly)
            awaitExchange(job)
            current(pairedOnly)?.let { return it }
        }
        return null
    }

    /** Stores an `X-Session-Renew` token from a network response. */
    fun captureRenewal(header: String?) {
        val renewed = ApiSessionToken.parse(header) ?: return
        val renewedFromExpired = synchronized(lock) {
            val held = book.token
            if (held == null || !ApiSessionPolicy.acceptsRenewal(held, renewed)) return
            val wasDue = ApiSessionPolicy.needsProof(held, book.serverNow)
            book.hold(renewed)
            wasDue
        }
        if (renewedFromExpired) listener.safely { onRenewal(ApiSessionRenewOutcome.Renewed, renewed.attestation) }
        persist()
        scheduler.reschedule()
    }

    /**
     * The API refused [sentToken] with [code] (see [ApiErrorCodes.SESSION_REJECTIONS]). Returns
     * headers for one retry, or null when no better session can be had right now.
     */
    fun recoverFromRejection(sentToken: String?, code: String, pairedOnly: Boolean = false): SessionHeaders? {
        val (plan, job) = synchronized(lock) {
            val plan = book.planRejection(sentToken, code, clock())
            val canExchange = exchangeJob?.isActive == true || book.mayExchange
            plan to if (plan.step == RejectionStep.Exchange && canExchange) joinOrStartExchangeLocked(ApiSessionTrigger.Rejected) else null
        }
        plan.refusedRenewal?.let { refused -> scope.launch { listener.safely { onRenewal(ApiSessionRenewOutcome.Rejected, refused.attestation) } } }
        // Another request already replaced the refused session; retry with that one.
        if (plan.step == RejectionStep.UseCurrent) return current(pairedOnly)
        persist()
        if (job == null) return null
        awaitExchange(job)
        return current(pairedOnly)
    }

    /**
     * A limited session reached [url], a route that needs more (`verification_required`). Starts
     * the Turnstile step-up, or joins the one running, and returns headers for one retry, or null so
     * the caller uses its fallback (always null in the background, where no host can run the check).
     * This runs on an OkHttp thread, so the wait is capped at [ApiSessionTiming.stepUpRequestWaitMs]
     * (the API client allows enough requests per host that waiting ones don't hold up the rest).
     * An AI route ([AI_ROUTES]) that finds a step-up already running doesn't join it: it takes its
     * fallback. Lending the account ([LENDING_ROUTES]) is the listener's own ask, so neither a recent
     * failed step-up nor a Skip holds it back.
     */
    fun stepUpForRoute(url: HttpUrl): SessionHeaders? {
        val route = routeOf(url)
        val token = runBlocking {
            withTimeoutOrNull(timing.stepUpRequestWaitMs) { stepUp.run(joinRunning = route !in AI_ROUTES, pastCooldown = route in LENDING_ROUTES) }
        }
        return token?.let { current() }
    }

    /** Coming to the foreground lifts a wait for it and lets a sign-in ask for the check again. */
    fun onForegroundChanged(foreground: Boolean) {
        if (foreground) {
            synchronized(lock) {
                book.awaitingForeground = false
                book.signInCheckAfterMs = 0L
            }
        }
        scheduler.setForeground(foreground)
    }

    fun onNetworkChanged(online: Boolean) = scheduler.setOnline(online)

    /**
     * A watch or TV signed in or out (its [PairedSessionAccount]'s token changed): signed in, it
     * trades for a paired session now (unless the exchange backoff holds it back); signed out, it
     * drops the one it holds, so its pairing routes go out with a limited session again. Ignored on
     * a phone.
     */
    fun onPairedAccountChanged() {
        val pairing = pairing ?: return
        val signedIn = pairing.accountToken() != null
        synchronized(lock) {
            if (!signedIn) {
                book.dropPaired()
            } else if (book.usableToken?.attestation != ApiAttestation.Paired && (exchangeJob?.isActive == true || book.mayExchange)) {
                joinOrStartExchangeLocked(ApiSessionTrigger.Retry)
            }
        }
        persist()
        scheduler.reschedule()
    }

    private fun routeOf(url: HttpUrl): String = "/" + url.encodedPath.removePrefix(base.encodedPath)

    /**
     * Waits for [job] up to [ApiSessionTiming.requestWaitMs], and while a sign-in is running a
     * Turnstile check on screen (or waiting its turn behind a step-up's), until that check's own limit.
     */
    private fun awaitExchange(job: Deferred<ApiSessionToken?>) {
        runBlocking {
            withTimeoutOrNull(timing.requestWaitMs) { job.await() }
            val checking = signIn.checkingSinceMs ?: return@runBlocking
            if (!job.isActive) return@runBlocking
            val since = maxOf(checking, checks.busySinceMs ?: checking)
            val left = since + timing.stepUpTimeoutMs + timing.requestWaitMs - clock()
            if (left > 0) withTimeoutOrNull(left) { job.await() }
        }
    }

    private fun current(pairedOnly: Boolean = false): SessionHeaders? {
        val (token, now) = synchronized(lock) { book.usableToken to book.serverNow }
        if (token == null) return null
        if (pairedOnly && token.attestation != ApiAttestation.Paired) return null
        if (!ApiSessionPolicy.needsProof(token, now)) return SessionHeaders(token.raw, null)
        return SessionHeaders(token.raw, proofFor(token, now))
    }

    private fun proofFor(token: ApiSessionToken, now: Long): String? {
        synchronized(lock) { book.cachedProof(token.sid, now) }?.let { return it }
        val signature = runCatching { deviceKey.sign(renewalProofMessage(token.sid, now)) }.getOrNull()
        if (signature == null) {
            val first = synchronized(lock) { book.firstReport(token.sid, ApiSessionRenewOutcome.ProofUnavailable) }
            if (first) scope.launch { listener.safely { onRenewal(ApiSessionRenewOutcome.ProofUnavailable, token.attestation) } }
            return null
        }
        val header = "$now.$signature"
        synchronized(lock) { book.cacheProof(token.sid, now, header) }
        return header
    }

    /** Joins the running exchange or starts one. Background callers ignore the request backoff. */
    private suspend fun exchangeNow(trigger: ApiSessionTrigger, useAppCheck: Boolean?, turnstileToken: String?): ApiSessionToken? {
        if (turnstileToken != null) return mint(trigger, useAppCheck = false, turnstileToken = turnstileToken)
        val job = synchronized(lock) { joinOrStartExchangeLocked(trigger, useAppCheck) }
        return job.await()
    }

    /** The single in-flight exchange: callers join it rather than start a second one. */
    private fun joinOrStartExchangeLocked(trigger: ApiSessionTrigger, useAppCheck: Boolean? = null): Deferred<ApiSessionToken?> {
        exchangeJob?.takeIf { it.isActive }?.let { return it }
        // Phones that recently failed App Check skip it until its retry is due; the scheduler retries it.
        val withAppCheck = useAppCheck ?: book.appCheckDue
        return scope.async { mint(trigger, withAppCheck, null) }.also { exchangeJob = it }
    }

    /**
     * One sign-in ([ApiSessionSignIn]); a refusal nothing on screen could answer waits for the
     * foreground. A signed-in watch or TV trades for a paired session instead ([ApiSessionPairing]),
     * signing in for a limited session first only when it holds none to carry the trade.
     */
    private suspend fun mint(trigger: ApiSessionTrigger, useAppCheck: Boolean, turnstileToken: String?): ApiSessionToken? {
        loaded.await()
        val installId = synchronized(lock) { book.installIdOrCreate() }
        val publicKey = runCatching { deviceKey.publicKey() }.getOrNull()
        // Watches and TVs have no App Check.
        val withAppCheck = useAppCheck && pairing == null
        val token = if (pairing != null && turnstileToken == null && pairing.accountToken() != null) {
            pairing.trade { signIn.run(trigger, useAppCheck = false, turnstileToken = null, installId, publicKey).token }
        } else {
            val outcome = signIn.run(trigger, withAppCheck, turnstileToken, installId, publicKey)
            if (outcome.refused) {
                synchronized(lock) { if (book.usableToken == null && !scheduler.isForeground()) book.awaitingForeground = true }
            }
            outcome.token
        }
        persist()
        scheduler.reschedule()
        return token
    }

    private fun persist() {
        scope.launch {
            saveMutex.withLock {
                val snapshot = synchronized(lock) { book.snapshot() }
                runCatching { store.save(snapshot) }
            }
        }
    }

    companion object {
        const val AUTH_SESSION_PATH = "/auth/session"

        /** Where a signed-in watch or TV trades its account token for a paired session. */
        const val PAIRED_SESSION_PATH = "/auth/watch/session"

        /** Routes that spend AI and have a fallback of their own (onboarding): they never wait on a step-up another request started. */
        val AI_ROUTES = setOf("/onboarding/next-turn", "/onboarding/probe-deck", "/onboarding/synthesize", "/onboarding/genre-synth", "/onboarding/similar-shows")

        /** Where this phone lends its sign-in to a watch or a TV: only ever because the listener asked. */
        val LENDING_ROUTES = setOf("/auth/watch/code", "/auth/tv/approve")

        /**
         * What a watch or TV may call without a paired session (the API's own list for limited
         * sessions): show and collect a TV code, redeem a watch code, trade, and give a token up.
         */
        val PAIRING_ROUTES = setOf("/auth/tv/start", "/auth/tv/poll", "/auth/watch/token", PAIRED_SESSION_PATH, "/auth/watch/revoke")

        /** The string the device key signs to renew: `bls-renew.<sid>.<unix seconds>`. */
        fun renewalProofMessage(sid: String, unixSeconds: Long): String = "bls-renew.$sid.$unixSeconds"
    }
}
