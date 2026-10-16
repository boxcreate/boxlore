package cx.aswin.boxlore.core.network.session

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
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
 * sessions are replaced once, and limited sessions step up (or fall back) on AI routes.
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

    private fun manager(timing: ApiSessionTiming = ApiSessionTiming(appCheckTimeoutMs = 500, requestWaitMs = 3_000, loadWaitMs = 1_000, stepUpTimeoutMs = 2_000)) =
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
        ).also { it.start() }

    private fun client(manager: ApiSessionManager) = OkHttpClient.Builder().addInterceptor(ApiSessionInterceptor { manager }).build()

    private fun get(client: OkHttpClient, path: String = "/trending") =
        client.newCall(Request.Builder().url(server.url(path)).header("X-App-Key", "bundled-key").build()).execute()

    private fun ok(body: String = "{}") = MockResponse().setResponseCode(200).setBody(body)

    private fun error(status: Int, code: String) = MockResponse().setResponseCode(status).setBody("""{"error":"$code"}""")

    private fun RecordedRequest.session() = getHeader(ApiSessionInterceptor.SESSION_HEADER)

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
    fun `App Check failure gives a limited session and the next launch skips App Check`() {
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

        // Relaunch two days later: the limited session has run out, and App Check is not on the critical path.
        clock.advanceSeconds(2 * DAY_S)
        val calls = appCheckCalls.get()
        val relaunched = client(manager())
        server.enqueue(ok())
        get(relaunched).close()
        server.takeRequest()
        assertEquals(calls, appCheckCalls.get())
        assertEquals(2, exchanger.calls)
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
        manager.stepUpEnabled = { true }
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
        manager.stepUpEnabled = { true }
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
    fun `the step-up stays off until it is enabled`() {
        store.state = StoredApiSession(token = sessionToken(att = "none"), installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        val manager = manager()
        val solves = AtomicInteger()
        manager.turnstile = TurnstileTokenSource {
            solves.incrementAndGet()
            TurnstileResult.Token("unused")
        }
        val client = client(manager)
        server.enqueue(error(401, ApiErrorCodes.VERIFICATION_REQUIRED))

        get(client, "/onboarding/next-turn").use { assertEquals(401, it.code) }

        assertEquals(0, solves.get())
        assertTrue(listener.stepUps.isEmpty())
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
