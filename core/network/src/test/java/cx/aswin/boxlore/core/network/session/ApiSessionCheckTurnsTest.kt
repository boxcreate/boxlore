package cx.aswin.boxlore.core.network.session

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * The Turnstile check on a phone, against a real HTTP server: the sign-in check and route step-ups
 * take turns and never queue a second check, requests that aren't AI join a running step-up,
 * lending asks past the step-up cooldown, requests wait for a check on screen, and a Skip sticks,
 * for step-ups too.
 */
class ApiSessionCheckTurnsTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = FakeClock()
    private val store = FakeStore()
    private val listener = RecordingListener()
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
            deviceKey = FakeDeviceKey(),
            appCheck = { appCheck },
            scope = scope,
            listener = listener,
            clock = clock,
            timing = timing,
        ).also {
            it.turnstile = check
            it.start()
        }

    private fun client(manager: ApiSessionManager) = OkHttpClient.Builder().addInterceptor(ApiSessionInterceptor { manager }).build()

    private fun get(client: OkHttpClient, path: String = "/trending") = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    private fun ok() = MockResponse().setResponseCode(200).setBody("{}")

    private fun error(status: Int, code: String) = MockResponse().setResponseCode(status).setBody("""{"error":"$code"}""")

    private fun RecordedRequest.session() = getHeader(ApiSessionInterceptor.SESSION_HEADER)

    private fun challengeToken(sid: String = "c1") = sessionToken(sid = sid, att = "challenge", exp = BASE_S + DAY_S, attExp = BASE_S + 7 * DAY_S, tag = sid)

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

    // Regression: after a Skip at sign-in, the first route that asked for verification put the check straight back on screen.
    @Test
    fun `after a Skip at sign-in, a route step-up doesn't ask, but lending still does`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challengeToken()) else issued(limited) }
        val check = FakeCheck(TurnstileResult.Failed(TurnstileResult.SKIPPED), TurnstileResult.Token("ts"))
        val client = client(manager(check = check))
        server.dispatcher = limitedRefused(limited)

        get(client, "/onboarding/next-turn").use { assertEquals(401, it.code, "the AI route takes its fallback") }
        get(client, "/v2/episodes/register").use { assertEquals(401, it.code) }
        assertEquals(1, check.solves.get(), "only the sign-in's check, which was skipped")
        assertEquals(emptyList<Pair<ApiSessionStepUpOutcome, String?>>(), listener.stepUps.toList())

        get(client, "/auth/watch/code").use { assertEquals(200, it.code) }
        assertEquals(2, check.solves.get(), "lending a watch its sign-in is the listener's own ask")
    }

    // Regression: a step-up that waited for the sign-in check's turn ran its check with the app gone, timed out and cooled down for 30 minutes.
    @Test
    fun `a step-up that waited its turn while the app left the screen gives up without a cooldown`() {
        appCheck = AppCheckResult.Failed("play_integrity_-9")
        val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
        // App Check is due, so background upkeep runs a sign-in check as soon as the app is in front.
        store.state = StoredApiSession(token = limited, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS)
        exchanger.handler = { request -> if (request.turnstileToken == "ts") issued(challengeToken()) else issued(sessionToken(sid = "l2", att = "none", tag = "limited-2")) }
        val host = LeavingHost()
        val manager = manager(check = host)
        val client = client(manager)
        server.dispatcher = limitedRefused(limited)
        val pool = Executors.newFixedThreadPool(1)
        try {
            manager.onForegroundChanged(true)
            eventually { host.signInWaiting.isCompleted }
            val stepUp = pool.submit(Callable { get(client, "/onboarding/next-turn").use { it.code } })
            eventually { manager.checks.waiting == 1 }
            host.onScreen = false
            host.leave.complete(Unit)

            assertEquals(401, stepUp.get(5, TimeUnit.SECONDS))
            assertEquals(0, host.solves.get(), "no check ran with nothing on screen")
            assertEquals(emptyList<Pair<ApiSessionStepUpOutcome, String?>>(), listener.stepUps.toList(), "and nothing failed")

            host.onScreen = true
            get(client, "/onboarding/next-turn").use { assertEquals(200, it.code, "no cooldown holds the next step-up back") }
            assertEquals(1, host.solves.get())
        } finally {
            host.leave.complete(Unit)
            pool.shutdownNow()
        }
    }

    /**
     * A check host the sign-in check finds coming up ([signInWaiting]) until [leave], when it is
     * [onScreen] or not; a check run with nothing on screen times out, as a real one would.
     */
    private class LeavingHost : TurnstileTokenSource {
        val signInWaiting = CompletableDeferred<Unit>()
        val leave = CompletableDeferred<Unit>()
        val solves = AtomicInteger()

        @Volatile var onScreen = true

        override suspend fun ready(waitMs: Long): Boolean {
            if (waitMs > 0 && signInWaiting.complete(Unit)) leave.await()
            return onScreen
        }

        override suspend fun solve(): TurnstileResult {
            if (!onScreen) return TurnstileResult.Failed("timeout")
            solves.incrementAndGet()
            return TurnstileResult.Token("ts")
        }
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
}
