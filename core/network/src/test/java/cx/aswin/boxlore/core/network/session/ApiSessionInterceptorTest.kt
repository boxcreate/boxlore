package cx.aswin.boxlore.core.network.session

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The session interceptor against a real HTTP server: requests never wait while a usable session
 * is stored, one exchange serves every waiting request, renewals and proofs follow `exp`, refused
 * sessions are replaced once, sign-ins App Check can't vouch for send a Turnstile token (on screen
 * only), and limited sessions step up (or fall back) on routes that ask for verification.
 */
class ApiSessionInterceptorTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = FakeClock()
    private val store = FakeStore()
    private val deviceKey = FakeDeviceKey()
    private val listener = RecordingListener()
    private val appCheckCalls = AtomicInteger()
    private var appCheck: AppCheckResult = AppCheckResult.Token("app-check-token")
    private val exchanger = FakeExchanger { issued(sessionToken(tag = "minted")) }

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    /** A manager that has started; [check] is in place before its first exchange. */
    private fun manager(
        timing: ApiSessionTiming = ApiSessionTiming(appCheckTimeoutMs = 500, requestWaitMs = 3_000, loadWaitMs = 1_000, stepUpTimeoutMs = 2_000, checkHostWaitMs = 100),
        check: TurnstileTokenSource? = null,
    ) =
        ApiSessionManager(
            apiBaseUrl = server.url("/").toString(),
            exchanger = exchanger,
            store = store,
            deviceKey = deviceKey,
            appCheck = {
                appCheckCalls.incrementAndGet()
                appCheck
            },
            scope = scope,
            listener = listener,
            clock = clock,
            timing = timing,
        ).also {
            it.turnstile = check
            it.start()
        }

    private fun client(manager: ApiSessionManager) = OkHttpClient.Builder().addInterceptor(ApiSessionInterceptor { manager }).build()

    private fun get(client: OkHttpClient, path: String = "/trending") =
        client.newCall(Request.Builder().url(server.url(path)).header("X-App-Key", "bundled-key").build()).execute()

    private fun ok(body: String = "{}") = MockResponse().setResponseCode(200).setBody(body)

    private fun error(status: Int, code: String) = MockResponse().setResponseCode(status).setBody("""{"error":"$code"}""")

    private fun RecordedRequest.session() = getHeader(ApiSessionInterceptor.SESSION_HEADER)

    private fun challengeToken(sid: String = "c1") = sessionToken(sid = sid, att = "challenge", exp = BASE_S + DAY_S, attExp = BASE_S + 7 * DAY_S, tag = sid)

    private val refused = SessionExchangeResult.Failed("http_401_attestation_required", code = ApiErrorCodes.ATTESTATION_REQUIRED, serverReason = "no-attestation")

    /** A Turnstile source whose host can leave the screen, answering [results] in turn (then the last one). */
    private class FakeCheck(vararg results: TurnstileResult) : TurnstileTokenSource {
        private val answers = results.toList()
        val solves = AtomicInteger()

        @Volatile var onScreen = true

        override suspend fun solve(): TurnstileResult = answers[minOf(solves.getAndIncrement(), answers.lastIndex)]

        override suspend fun ready(waitMs: Long): Boolean = onScreen
    }

    /** A Turnstile source whose first check waits for [release], then answers [first]; later checks answer [later]. */
    private class HeldCheck(private val first: TurnstileResult, private val later: TurnstileResult = first) : TurnstileTokenSource {
        val release = CompletableDeferred<Unit>()
        val solves = AtomicInteger()

        override suspend fun solve(): TurnstileResult {
            if (solves.getAndIncrement() > 0) return later
            release.await()
            return first
        }
    }

    /** Answers a request sent with [limited]: 401 `verification_required`, or [refusing] routes 401 `attestation_required`; anything else 200. */
    private fun limitedRefused(limited: String, refusing: Set<String> = emptySet()) =
        object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                when {
                    request.session() != limited -> ok()
                    request.path in refusing -> error(401, ApiErrorCodes.ATTESTATION_REQUIRED)
                    else -> error(401, ApiErrorCodes.VERIFICATION_REQUIRED)
                }
        }

    @Test
    fun `a stored session goes out at once even while an exchange hangs, and the app key never does`() {
        val stored = sessionToken(exp = BASE_S + 3_600)
        store.state = StoredApiSession(token = stored, installId = "install-123")
        exchanger.handler = { awaitCancellation() }
        val client = client(manager())
        server.enqueue(ok())

        val started = System.nanoTime()
        get(client).use { assertEquals(200, it.code) }
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        val recorded = server.takeRequest()
        assertEquals(stored, recorded.session())
        assertNull(recorded.getHeader("X-App-Key"))
        assertNull(recorded.getHeader(ApiSessionInterceptor.PROOF_HEADER))
        assertEquals(0, exchanger.calls)
        assertTrue(elapsedMs < 1_000, "request waited ${elapsedMs}ms")
    }

    @Test
    fun `a fresh install shares one exchange across concurrent requests`() {
        val minted = sessionToken(tag = "shared")
        exchanger.handler = {
            delay(300)
            issued(minted)
        }
        val client = client(manager())
        repeat(5) { server.enqueue(ok()) }

        val pool = Executors.newFixedThreadPool(5)
        val codes = pool.invokeAll((1..5).map { Callable { get(client).use { it.code } } }).map { it.get(5, TimeUnit.SECONDS) }
        pool.shutdown()

        assertEquals(List(5) { 200 }, codes)
        assertEquals(1, exchanger.calls)
        repeat(5) { assertEquals(minted, server.takeRequest().session()) }
        val request = exchanger.requests.single()
        assertEquals("app-check-token", request.appCheckToken)
        assertEquals("device-pk", request.devicePublicKey)
        assertTrue(ApiSessionBook.INSTALL_ID.matches(request.installId))
        eventually { store.state.token == minted && store.state.installId == request.installId }
        assertEquals(ApiSessionTrigger.Launch, listener.exchanges.single().trigger)
    }

    @Test
    fun `a renewal header is stored and replaces the expired token`() {
        val expired = sessionToken(sid = "s1", iat = BASE_S - 2 * DAY_S, exp = BASE_S - 10, attExp = BASE_S + 20 * DAY_S, tag = "old")
        val renewed = sessionToken(sid = "s1", iat = BASE_S, exp = BASE_S + DAY_S, attExp = BASE_S + 20 * DAY_S, tag = "new")
        store.state = StoredApiSession(token = expired, installId = "install-123")
        val client = client(manager())
        server.enqueue(ok().addHeader(ApiSessionInterceptor.RENEW_HEADER, renewed))
        server.enqueue(ok())

        get(client).close()
        get(client).close()

        val first = server.takeRequest()
        assertEquals(expired, first.session())
        val message = ApiSessionManager.renewalProofMessage("s1", BASE_S)
        assertEquals("$BASE_S.${deviceKey.signatureOf(message)}", first.getHeader(ApiSessionInterceptor.PROOF_HEADER))
        val second = server.takeRequest()
        assertEquals(renewed, second.session())
        assertNull(second.getHeader(ApiSessionInterceptor.PROOF_HEADER), "a renewed token needs no proof")
        eventually { store.state.token == renewed }
        assertEquals(listOf(ApiSessionRenewOutcome.Renewed), listener.renewals.toList())
        assertEquals(0, exchanger.calls)
    }

    @Test
    fun `a renewal for another session or an older token is ignored`() {
        val held = sessionToken(sid = "s1", exp = BASE_S + 600, tag = "held")
        store.state = StoredApiSession(token = held, installId = "install-123")
        val client = client(manager())
        server.enqueue(ok().addHeader(ApiSessionInterceptor.RENEW_HEADER, sessionToken(sid = "other", exp = BASE_S + DAY_S)))
        server.enqueue(ok().addHeader(ApiSessionInterceptor.RENEW_HEADER, sessionToken(sid = "s1", exp = BASE_S + 60, tag = "older")))
        server.enqueue(ok())

        repeat(3) { get(client).close() }

        repeat(3) { assertEquals(held, server.takeRequest().session()) }
    }

    @Test
    fun `the proof is added only once the token is past exp`() {
        val token = sessionToken(sid = "s1", iat = BASE_S, exp = BASE_S + 3_600, attExp = BASE_S + 30 * DAY_S)
        store.state = StoredApiSession(token = token, installId = "install-123")
        val client = client(manager())
        repeat(3) { server.enqueue(ok()) }

        get(client).close()
        clock.advanceSeconds(3_600 - ApiSessionPolicy.PROOF_LEAD_SECONDS - 1)
        get(client).close()
        clock.advanceSeconds(ApiSessionPolicy.PROOF_LEAD_SECONDS + 600)
        get(client).close()

        assertNull(server.takeRequest().getHeader(ApiSessionInterceptor.PROOF_HEADER))
        assertNull(server.takeRequest().getHeader(ApiSessionInterceptor.PROOF_HEADER))
        val now = BASE_S + 3_600 + 599
        assertEquals("$now.${deviceKey.signatureOf(ApiSessionManager.renewalProofMessage("s1", now))}", server.takeRequest().getHeader(ApiSessionInterceptor.PROOF_HEADER))
    }

    @Test
    fun `a missing device key sends no proof and is reported once`() {
        deviceKey.available = false
        store.state = StoredApiSession(token = sessionToken(sid = "s1", exp = BASE_S - 5, attExp = BASE_S + DAY_S), installId = "install-123")
        val client = client(manager())
        repeat(2) { server.enqueue(ok()) }

        repeat(2) { get(client).close() }

        repeat(2) { assertNull(server.takeRequest().getHeader(ApiSessionInterceptor.PROOF_HEADER)) }
        eventually { listener.renewals.isNotEmpty() }
        assertEquals(listOf(ApiSessionRenewOutcome.ProofUnavailable), listener.renewals.toList())
    }

    @Test
    fun `a refused session is replaced once and the request retried`() {
        val stale = sessionToken(sid = "s1", tag = "stale")
        val fresh = sessionToken(sid = "s2", tag = "fresh")
        store.state = StoredApiSession(token = stale, installId = "install-123")
        exchanger.handler = { issued(fresh) }
        val client = client(manager())
        server.enqueue(error(401, ApiErrorCodes.SESSION_REVOKED))
        server.enqueue(ok())

        get(client).use { assertEquals(200, it.code) }

        assertEquals(stale, server.takeRequest().session())
        assertEquals(fresh, server.takeRequest().session())
        assertEquals(1, exchanger.calls)
        assertEquals(ApiSessionTrigger.Rejected, listener.exchanges.single().trigger)
        eventually { store.state.token == fresh }
    }

    @Test
    fun `a brand-new session refused again backs off instead of looping`() {
        exchanger.handler = { issued(sessionToken(tag = "minted")) }
        val client = client(manager())
        server.enqueue(error(401, ApiErrorCodes.SESSION_INVALID))
        server.enqueue(error(401, ApiErrorCodes.SESSION_INVALID))

        get(client).use { assertEquals(401, it.code) }
        get(client).use { assertEquals(401, it.code) }

        assertEquals(1, exchanger.calls, "no exchange storm while the API refuses new sessions")
        assertEquals(2, server.requestCount)
        server.takeRequest()
        assertNull(server.takeRequest().session(), "inside the backoff the next request goes without a session")
    }

    @Test
    fun `failed exchanges back off and never fall back to the app key`() {
        exchanger.handler = { SessionExchangeResult.Failed("network") }
        val client = client(manager())
        repeat(2) { server.enqueue(error(401, "Unauthorized")) }

        get(client).close()
        get(client).close()

        assertEquals(1, exchanger.calls)
        repeat(2) {
            val recorded = server.takeRequest()
            assertNull(recorded.session())
            assertNull(recorded.getHeader("X-App-Key"))
        }
        assertFalse(listener.exchanges.single().success)
        assertEquals("network", listener.exchanges.single().failureReason)
    }

    @Test
    fun `App Check failure gives a limited session, and later launches skip App Check until its retry is due`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val limited = sessionToken(att = "none", tag = "limited")
        exchanger.handler = { request -> if (request.appCheckToken == null) issued(limited) else issued(sessionToken()) }
        val client = client(manager())
        server.enqueue(ok())

        get(client).close()

        assertEquals(limited, server.takeRequest().session())
        val event = listener.exchanges.single()
        assertEquals(true, event.limited)
        assertEquals("play_integrity_-9", event.appCheckFailure)
        assertEquals("appcheck:invalid", event.serverReason)
        eventually { store.state.appCheckFailures == 1 && store.state.nextUpgradeAtMs == BASE_MS + 60_000 }

        // Relaunch inside the retry backoff without the session: App Check is not on the critical path.
        clock.advanceSeconds(30)
        store.state = store.state.copy(token = null)
        val calls = appCheckCalls.get()
        val relaunched = client(manager())
        server.enqueue(ok())
        get(relaunched).close()
        server.takeRequest()
        assertEquals(calls, appCheckCalls.get())
        assertEquals(2, exchanger.calls)

        // Two days later the limited session has run out and the retry is due: App Check is asked again.
        clock.advanceSeconds(2 * DAY_S)
        server.enqueue(ok())
        get(client(manager())).close()
        server.takeRequest()
        assertEquals(calls + 1, appCheckCalls.get())
    }

    @Test
    fun `an App Check failure counts even when the exchange fails, so the next attempt skips Play Integrity`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        exchanger.handler = { SessionExchangeResult.Failed("network") }
        val client = client(manager())
        repeat(2) { server.enqueue(error(401, ApiErrorCodes.SESSION_REQUIRED)) }

        get(client).close()
        eventually { store.state.appCheckFailures == 1 }
        clock.advanceSeconds(10) // past the exchange backoff, inside App Check's
        get(client).close()

        assertEquals(2, exchanger.calls)
        assertEquals(1, appCheckCalls.get())
        assertEquals(listOf(true, false), listener.exchanges.map { it.appCheckAttempted })
    }

    @Test
    fun `a sign-in App Check can't vouch for sends a Turnstile token in the same exchange`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val challenge = challengeToken()
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else issued(sessionToken(att = "none")) }
        val check = FakeCheck(TurnstileResult.Token("ts"))
        val manager = manager(check = check)
        val client = client(manager)
        server.enqueue(ok())

        get(client).use { assertEquals(200, it.code) }

        assertEquals(challenge, server.takeRequest().session())
        val request = exchanger.requests.single()
        assertNull(request.appCheckToken)
        assertEquals("ts", request.turnstileToken)
        assertEquals("token", listener.exchanges.single().turnstile)
        eventually { store.state.appCheckFailures == 1 }
    }

    @Test
    fun `with no host on screen a sign-in takes what the API gives, without a check`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val limited = sessionToken(att = "none", tag = "limited")
        exchanger.handler = { issued(limited) }
        val check = FakeCheck(TurnstileResult.Token("ts")).apply { onScreen = false }
        val manager = manager(check = check)
        server.enqueue(ok())

        get(client(manager)).close()

        assertEquals(limited, server.takeRequest().session())
        assertEquals(0, check.solves.get())
        assertNull(exchanger.requests.single().turnstileToken)
        assertEquals("no_host", listener.exchanges.single().turnstile)
    }

    @Test
    fun `a refusal on screen runs the check and signs in once more`() {
        val challenge = challengeToken()
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else refused }
        val manager = manager(check = FakeCheck(TurnstileResult.Token("ts")))
        server.enqueue(ok())

        get(client(manager)).use { assertEquals(200, it.code) }

        assertEquals(challenge, server.takeRequest().session())
        val (first, second) = exchanger.requests
        assertEquals("app-check-token", first.appCheckToken, "App Check went first, and the API refused it")
        assertNull(first.turnstileToken)
        assertNull(second.appCheckToken)
        assertEquals("ts", second.turnstileToken)
        assertEquals(listOf("no-attestation", null), listener.exchanges.map { it.serverReason })
        eventually { store.state.appCheckFailures == 1 }
    }

    @Test
    fun `a refusal in the background waits for the foreground instead of backing off`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val challenge = challengeToken()
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else refused }
        val check = FakeCheck(TurnstileResult.Token("ts")).apply { onScreen = false }
        val manager = manager(check = check)
        val client = client(manager)
        repeat(2) { server.enqueue(error(401, ApiErrorCodes.SESSION_REQUIRED)) }

        get(client).use { assertEquals(401, it.code) }
        clock.advanceSeconds(600)
        get(client).use { assertEquals(401, it.code) }

        assertEquals(1, exchanger.calls, "no exchange until the app is on screen again")
        server.takeRequest()
        assertNull(server.takeRequest().session())

        check.onScreen = true
        manager.onForegroundChanged(true)
        eventually { store.state.token == challenge }
        assertEquals(2, exchanger.calls)
        assertEquals(1, check.solves.get())
    }

    @Test
    fun `a session the gate stops taking is replaced, with the check, and the request retried`() {
        val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
        val challenge = challengeToken()
        store.state = StoredApiSession(token = limited, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else refused }
        val manager = manager(check = FakeCheck(TurnstileResult.Token("ts")))
        val client = client(manager)
        server.enqueue(error(401, ApiErrorCodes.ATTESTATION_REQUIRED))
        server.enqueue(ok())

        get(client, "/feedback").use { assertEquals(200, it.code) }

        assertEquals(limited, server.takeRequest().session())
        assertEquals(challenge, server.takeRequest().session())
        val request = exchanger.requests.single()
        assertNull(request.appCheckToken, "App Check waits out its retry backoff")
        assertEquals("ts", request.turnstileToken)
        assertEquals(ApiSessionTrigger.Rejected, listener.exchanges.single().trigger)
    }

    @Test
    fun `an expired challenge session tries App Check again, then the check`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val expired = sessionToken(sid = "c0", att = "challenge", iat = BASE_S - 8 * DAY_S, exp = BASE_S - 7 * DAY_S, attExp = BASE_S - DAY_S, tag = "old")
        val challenge = challengeToken()
        store.state = StoredApiSession(token = expired, installId = "install-123", appCheckFailures = 5, nextUpgradeAtMs = BASE_MS - 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else refused }
        val manager = manager(check = FakeCheck(TurnstileResult.Token("ts")))
        server.enqueue(ok())

        get(client(manager)).close()

        assertEquals(challenge, server.takeRequest().session())
        assertEquals(1, appCheckCalls.get())
        assertEquals(ApiSessionTrigger.Expired, listener.exchanges.single().trigger)
        assertEquals("ts", exchanger.requests.single().turnstileToken)
    }

    @Test
    fun `a limited session steps up with Turnstile before an AI route and retries`() {
        val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
        val challenge = sessionToken(sid = "c1", att = "challenge", exp = BASE_S + DAY_S, attExp = BASE_S + 7 * DAY_S, tag = "challenge")
        store.state = StoredApiSession(token = limited, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "turnstile-ok") issued(challenge) else issued(limited) }
        val manager = manager()
        val solves = AtomicInteger()
        manager.turnstile = TurnstileTokenSource {
            solves.incrementAndGet()
            TurnstileResult.Token("turnstile-ok")
        }
        val client = client(manager)
        server.enqueue(error(401, ApiErrorCodes.VERIFICATION_REQUIRED))
        server.enqueue(ok())

        get(client, "/onboarding/next-turn").use { assertEquals(200, it.code) }

        assertEquals(limited, server.takeRequest().session())
        assertEquals(challenge, server.takeRequest().session())
        assertEquals(1, solves.get())
        val request = exchanger.requests.single()
        assertNull(request.appCheckToken, "the step-up does not retry App Check on the critical path")
        assertEquals(ApiSessionTrigger.StepUp, listener.exchanges.single().trigger)
        assertEquals(ApiSessionStepUpOutcome.Passed, listener.stepUps.single().first)
    }

    @Test
    fun `a failed step-up returns the 401 for the fallback and cools down`() {
        store.state = StoredApiSession(token = sessionToken(att = "none"), installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        val manager = manager()
        val solves = AtomicInteger()
        manager.turnstile = TurnstileTokenSource {
            solves.incrementAndGet()
            TurnstileResult.Failed("cancelled")
        }
        val client = client(manager)
        repeat(2) { server.enqueue(error(401, ApiErrorCodes.VERIFICATION_REQUIRED)) }

        get(client, "/onboarding/next-turn").use { assertEquals(401, it.code) }
        get(client, "/onboarding/genre-synth").use {
            assertEquals(401, it.code)
            assertEquals(ApiErrorCodes.VERIFICATION_REQUIRED, ApiErrorCodes.parse(it.body?.string()))
        }

        assertEquals(1, solves.get(), "the cooldown sends later AI requests straight to their fallback")
        assertEquals(0, exchanger.calls)
        assertEquals(ApiSessionStepUpOutcome.ChallengeFailed to "cancelled", listener.stepUps.single())
    }

    @Test
    fun `a step-up holds only its own request, briefly, and still upgrades later requests`() {
        val limited = sessionToken(sid = "l2", att = "none", tag = "limited")
        val challenge = sessionToken(sid = "c2", att = "challenge", exp = BASE_S + DAY_S, attExp = BASE_S + 7 * DAY_S, tag = "challenge")
        store.state = StoredApiSession(token = limited, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "turnstile-ok") issued(challenge) else issued(limited) }
        val manager = manager(ApiSessionTiming(appCheckTimeoutMs = 500, requestWaitMs = 3_000, loadWaitMs = 1_000, stepUpTimeoutMs = 10_000, stepUpRequestWaitMs = 300))
        val release = CompletableDeferred<Unit>()
        val solves = AtomicInteger()
        manager.turnstile = TurnstileTokenSource {
            solves.incrementAndGet()
            release.await()
            TurnstileResult.Token("turnstile-ok")
        }
        val client = client(manager)
        repeat(2) { server.enqueue(error(401, ApiErrorCodes.VERIFICATION_REQUIRED)) }
        server.enqueue(ok())
        val pool = Executors.newSingleThreadExecutor()
        try {
            val starter = pool.submit(Callable { get(client, "/onboarding/next-turn").use { it.code } })
            eventually { solves.get() == 1 }

            val started = System.nanoTime()
            val second = get(client, "/onboarding/genre-synth").use { it.code }
            assertEquals(401, second, "a request that finds a step-up running takes its fallback")
            assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started) < 2_000, "and does not wait on the step-up")
            assertEquals(401, starter.get(5, TimeUnit.SECONDS), "the starter stops waiting at stepUpRequestWaitMs")
            assertEquals(1, solves.get())

            release.complete(Unit)
            eventually { listener.stepUps.isNotEmpty() }
            get(client, "/onboarding/similar-shows").use { assertEquals(200, it.code) }
            server.takeRequest()
            server.takeRequest()
            assertEquals(challenge, server.takeRequest().session(), "the finished step-up serves later AI requests")
        } finally {
            release.complete(Unit)
            pool.shutdownNow()
        }
    }

    @Test
    fun `the remote switch turns the step-up off`() {
        store.state = StoredApiSession(token = sessionToken(att = "none"), installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        val check = FakeCheck(TurnstileResult.Token("unused"))
        val manager = manager(check = check)
        manager.stepUpEnabled = { false }
        val client = client(manager)
        server.enqueue(error(401, ApiErrorCodes.VERIFICATION_REQUIRED))

        get(client, "/onboarding/next-turn").use { assertEquals(401, it.code) }

        assertEquals(0, check.solves.get())
        assertTrue(listener.stepUps.isEmpty())
    }

    @Test
    fun `a background request asked for verification gets its 401 without a step-up or a cooldown`() {
        val challenge = challengeToken()
        store.state = StoredApiSession(token = sessionToken(att = "none"), installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else refused }
        val check = FakeCheck(TurnstileResult.Token("ts")).apply { onScreen = false }
        val manager = manager(check = check)
        val client = client(manager)
        repeat(2) { server.enqueue(error(401, ApiErrorCodes.VERIFICATION_REQUIRED)) }
        server.enqueue(ok())

        get(client, "/v2/episodes/register").use { assertEquals(401, it.code) }
        assertEquals(0, check.solves.get())
        assertTrue(listener.stepUps.isEmpty())

        check.onScreen = true
        get(client, "/v2/episodes/register").use { assertEquals(200, it.code) }
        assertEquals(1, check.solves.get(), "no cooldown held the step-up back")
    }

    @Test
    fun `a failed step-up doesn't hold back the sign-in check`() {
        val challenge = challengeToken()
        store.state = StoredApiSession(token = sessionToken(att = "none"), installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else refused }
        val check = FakeCheck(TurnstileResult.Failed("cancelled"), TurnstileResult.Token("ts"))
        val manager = manager(check = check)
        val client = client(manager)
        server.enqueue(error(401, ApiErrorCodes.VERIFICATION_REQUIRED))
        server.enqueue(error(401, ApiErrorCodes.ATTESTATION_REQUIRED))
        server.enqueue(ok())

        get(client, "/onboarding/next-turn").use { assertEquals(401, it.code) }
        get(client, "/feedback").use { assertEquals(200, it.code) }

        assertEquals(2, check.solves.get())
        assertEquals("ts", exchanger.requests.single().turnstileToken)
    }

    @Test
    fun `a failed sign-in check doesn't hold back the step-up`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
        val challenge = challengeToken()
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else issued(limited) }
        val check = FakeCheck(TurnstileResult.Failed("cancelled"), TurnstileResult.Token("ts"))
        val manager = manager(check = check)
        val client = client(manager)
        server.enqueue(error(401, ApiErrorCodes.VERIFICATION_REQUIRED))
        server.enqueue(ok())

        get(client, "/onboarding/next-turn").use { assertEquals(200, it.code) }

        assertEquals(limited, server.takeRequest().session(), "the skipped check left a limited session")
        assertEquals(challenge, server.takeRequest().session())
        assertEquals(2, check.solves.get())
        assertEquals(listOf("cancelled", "token"), listener.exchanges.map { it.turnstile })
    }

    // Regression: a sign-in check queued behind a step-up's reopened the sheet at once after a Skip.
    @Test
    fun `a sign-in check queued behind a step-up the listener skipped doesn't ask again`() {
        val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
        store.state = StoredApiSession(token = limited, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challengeToken()) else issued(sessionToken(sid = "l2", att = "none", tag = "limited-2")) }
        val check = HeldCheck(TurnstileResult.Failed(TurnstileResult.SKIPPED), TurnstileResult.Token("ts"))
        val manager = manager(check = check)
        val client = client(manager)
        server.dispatcher = limitedRefused(limited, refusing = setOf("/feedback"))
        val pool = Executors.newFixedThreadPool(2)
        try {
            val stepUp = pool.submit(Callable { get(client, "/onboarding/next-turn").use { it.code } })
            eventually { check.solves.get() == 1 }
            val refusedSession = pool.submit(Callable { get(client, "/feedback").use { it.code } })
            eventually { manager.checks.waiting == 1 }
            check.release.complete(Unit)

            assertEquals(401, stepUp.get(5, TimeUnit.SECONDS), "the skipped step-up leaves its request to its fallback")
            assertEquals(200, refusedSession.get(5, TimeUnit.SECONDS), "the refused session is replaced without a check")
            assertEquals(1, check.solves.get())
            assertNull(exchanger.requests.single().turnstileToken)
            assertEquals("cooldown", listener.exchanges.single().turnstile)
        } finally {
            check.release.complete(Unit)
            pool.shutdownNow()
        }
    }

    // Regression: a sign-in check queued behind a step-up that passed ran a second check and a second exchange.
    @Test
    fun `a sign-in check queued behind a step-up that passed uses the step-up's session`() {
        val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
        val challenge = challengeToken()
        store.state = StoredApiSession(token = limited, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else issued(sessionToken(sid = "l2", att = "none", tag = "limited-2")) }
        val check = HeldCheck(TurnstileResult.Token("ts"))
        val manager = manager(check = check)
        val client = client(manager)
        server.dispatcher = limitedRefused(limited, refusing = setOf("/feedback"))
        val pool = Executors.newFixedThreadPool(2)
        try {
            val stepUp = pool.submit(Callable { get(client, "/onboarding/next-turn").use { it.code } })
            eventually { check.solves.get() == 1 }
            val refusedSession = pool.submit(Callable { get(client, "/feedback").use { it.code } })
            eventually { manager.checks.waiting == 1 }
            check.release.complete(Unit)

            assertEquals(200, stepUp.get(5, TimeUnit.SECONDS))
            assertEquals(200, refusedSession.get(5, TimeUnit.SECONDS))
            assertEquals(1, check.solves.get())
            assertEquals(listOf(ApiSessionTrigger.StepUp), listener.exchanges.map { it.trigger }, "one exchange, the step-up's")
        } finally {
            check.release.complete(Unit)
            pool.shutdownNow()
        }
    }

    // Regression: every request but one that met a step-up already running got its 401, so nothing reloaded.
    @Test
    fun `a request to a route that isn't AI joins the step-up already running`() {
        val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
        val challenge = challengeToken()
        store.state = StoredApiSession(token = limited, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else issued(limited) }
        val check = HeldCheck(TurnstileResult.Token("ts"))
        val manager = manager(check = check)
        val client = client(manager)
        server.dispatcher = limitedRefused(limited)
        val pool = Executors.newFixedThreadPool(2)
        try {
            val starter = pool.submit(Callable { get(client, "/v2/episodes/register").use { it.code } })
            eventually { check.solves.get() == 1 }
            val joiner = pool.submit(Callable { get(client, "/feedback").use { it.code } })
            eventually { server.requestCount == 2 }
            Thread.sleep(300) // the second request's 401 reaches the running step-up
            check.release.complete(Unit)

            assertEquals(200, starter.get(5, TimeUnit.SECONDS))
            assertEquals(200, joiner.get(5, TimeUnit.SECONDS), "it waited for the step-up and retried with its session")
            assertEquals(1, check.solves.get())
            assertEquals(1, exchanger.calls)
        } finally {
            check.release.complete(Unit)
            pool.shutdownNow()
        }
    }

    @Test
    fun `lending the account to a watch or TV asks for the check even right after a failed step-up`() {
        val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
        store.state = StoredApiSession(token = limited, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challengeToken()) else issued(limited) }
        val check = FakeCheck(TurnstileResult.Failed(TurnstileResult.SKIPPED), TurnstileResult.Token("ts"))
        val client = client(manager(check = check))
        server.dispatcher = limitedRefused(limited)

        get(client, "/onboarding/next-turn").use { assertEquals(401, it.code) }
        get(client, "/auth/tv/approve").use { assertEquals(200, it.code) }

        assertEquals(2, check.solves.get(), "the listener's own ask goes past the step-up cooldown")
    }

    // Regression: a first request on a fresh install stopped waiting after 15 s, while the sign-in's check was still on screen.
    @Test
    fun `a request waits for a sign-in check on screen, past the usual wait`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val challenge = challengeToken()
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challenge) else issued(sessionToken(att = "none")) }
        val manager =
            manager(
                ApiSessionTiming(appCheckTimeoutMs = 500, requestWaitMs = 300, loadWaitMs = 1_000, stepUpTimeoutMs = 3_000, checkHostWaitMs = 100),
                check = {
                    delay(900)
                    TurnstileResult.Token("ts")
                },
            )
        server.enqueue(ok())

        get(client(manager)).use { assertEquals(200, it.code) }

        assertEquals(challenge, server.takeRequest().session())
    }

    // Regression: Skip, a quick switch to another app and back, and the next background exchange reopened the check.
    @Test
    fun `after a Skip, sign-in checks stay away across foregrounds`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val counter = AtomicInteger()
        exchanger.handler = { request ->
            // Short-lived, so background upkeep wants a fresh one as soon as it runs.
            if (request.turnstileToken == "ts") issued(challengeToken()) else issued(sessionToken(sid = "l${counter.incrementAndGet()}", att = "none", exp = BASE_S + 120, tag = "l"))
        }
        val check = FakeCheck(TurnstileResult.Failed(TurnstileResult.SKIPPED), TurnstileResult.Token("ts"))
        val manager = manager(check = check)
        server.enqueue(ok())
        get(client(manager)).close()
        assertEquals(TurnstileResult.SKIPPED, listener.exchanges.single().turnstile)

        // Back in the foreground: the 10-minute hold on sign-in checks lifts, and background upkeep exchanges at once.
        manager.onForegroundChanged(true)
        eventually { listener.exchanges.size == 2 }

        assertEquals(1, check.solves.get())
        assertNull(exchanger.requests[1].turnstileToken)
        assertEquals("cooldown", listener.exchanges[1].turnstile)
    }

    @Test
    fun `a passed check whose exchange didn't get through isn't asked for again straight away`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val calls = AtomicInteger()
        exchanger.handler = { if (calls.getAndIncrement() == 0) SessionExchangeResult.Failed("network") else issued(sessionToken(att = "none")) }
        val check = FakeCheck(TurnstileResult.Token("ts"))
        val client = client(manager(check = check))
        repeat(2) { server.enqueue(ok()) }

        get(client).close()
        clock.advanceSeconds(3) // past the exchange backoff
        get(client).close()

        assertEquals(1, check.solves.get())
        assertEquals(listOf("ts", null), exchanger.requests.map { it.turnstileToken })
    }

    @Test
    fun `the daily AI limit is passed straight to the caller`() {
        store.state = StoredApiSession(token = sessionToken(), installId = "install-123")
        val client = client(manager())
        server.enqueue(error(429, ApiErrorCodes.DAILY_AI_LIMIT))

        get(client, "/onboarding/synthesize").use { assertEquals(429, it.code) }

        assertEquals(1, server.requestCount)
        assertEquals(0, exchanger.calls)
    }

    // Regression: a 403 carrying a session code (the bundled app key's answer) replaced the session.
    @Test
    fun `a 403 is passed straight to the caller, whatever code it carries`() {
        store.state = StoredApiSession(token = sessionToken(), installId = "install-123")
        val client = client(manager())
        server.enqueue(error(403, ApiErrorCodes.ATTESTATION_REQUIRED))
        server.enqueue(error(403, ApiErrorCodes.VERIFICATION_REQUIRED))

        get(client, "/feedback").use { assertEquals(403, it.code) }
        get(client, "/feedback").use { assertEquals(403, it.code) }

        assertEquals(2, server.requestCount)
        assertEquals(0, exchanger.calls)
        assertTrue(listener.stepUps.isEmpty())
    }

    @Test
    fun `other hosts get no session, and nothing gets the app key`() {
        val other = MockWebServer().apply { start() }
        try {
            store.state = StoredApiSession(token = sessionToken(), installId = "install-123")
            val client = client(manager())
            other.enqueue(ok())

            client.newCall(Request.Builder().url(other.url("/x")).header("X-App-Key", "bundled-key").build()).execute().close()

            val recorded = other.takeRequest()
            assertNull(recorded.session())
            assertNull(recorded.getHeader("X-App-Key"))
        } finally {
            other.shutdown()
        }
    }

    @Test
    fun `the exchange route itself is never signed`() {
        val manager = manager()

        assertFalse(manager.covers(server.url("/auth/session")))
        assertTrue(manager.covers(server.url("/auth/sessions-list")))
        assertTrue(manager.covers(server.url("/onboarding/next-turn")))
    }
}
