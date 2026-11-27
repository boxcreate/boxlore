package cx.aswin.boxlore.core.network.session

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A phone lending its sign-in to a watch or TV (`/auth/watch/code`, `/auth/tv/approve`), against a
 * real HTTP server. The API records how the lending phone attested in the token it lends, and a
 * watch or TV can't trade one lent from a limited session, so a limited session steps up first and
 * never lends without a better session when a check could give one.
 */
class ApiSessionLendingTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val store = FakeStore()
    private val listener = RecordingListener()
    private val limited = sessionToken(sid = "l1", att = "none", tag = "limited")
    private val challenge = sessionToken(sid = "c1", att = "challenge", exp = BASE_S + DAY_S, attExp = BASE_S + 7 * DAY_S, tag = "c1")
    private val exchanger = FakeExchanger { request -> if (request.turnstileToken == "ts") issued(challenge) else issued(limited) }
    private val received = CopyOnWriteArrayList<RecordedRequest>()

    /** What the API answers a lending sent with each session; 200 for anything not listed. */
    private val answers = mutableMapOf<String, MockResponse>()

    @BeforeEach
    fun setUp() {
        store.state = StoredApiSession(token = limited, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    received += request
                    return answers[request.getHeader(ApiSessionInterceptor.SESSION_HEADER)] ?: MockResponse().setBody("""{"code":"bwc1.code"}""")
                }
            }
        server.start()
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    private fun manager(check: TurnstileTokenSource?, stepUps: () -> Boolean = { true }) =
        ApiSessionManager(
            apiBaseUrl = server.url("/").toString(),
            exchanger = exchanger,
            store = store,
            deviceKey = FakeDeviceKey(),
            appCheck = { AppCheckResult.Failed("play_integrity_-9") },
            scope = scope,
            listener = listener,
            clock = FakeClock(),
            timing = ApiSessionTiming(appCheckTimeoutMs = 500, requestWaitMs = 3_000, loadWaitMs = 1_000, stepUpTimeoutMs = 2_000, checkHostWaitMs = 100),
        ).also {
            it.turnstile = check
            it.stepUpEnabled = stepUps
            it.start()
        }

    private fun client(manager: ApiSessionManager) = OkHttpClient.Builder().addInterceptor(ApiSessionInterceptor { manager }).build()

    private fun lend(client: OkHttpClient, path: String = "/auth/watch/code"): Response =
        client.newCall(Request.Builder().url(server.url(path)).post("{}".toRequestBody("application/json".toMediaType())).build()).execute()

    private fun error(status: Int, code: String) = MockResponse().setResponseCode(status).setBody("""{"error":"$code"}""")

    private fun sessions() = received.map { it.getHeader(ApiSessionInterceptor.SESSION_HEADER) }

    // Regression: a limited phone lent at once (the API takes it while limited sessions are allowed), so the watch got a token it couldn't trade.
    @Test
    fun `a limited session steps up before it lends, so the lending goes out with the checked session`() {
        val check = FakeCheck(TurnstileResult.Token("ts"))
        val client = client(manager(check))

        lend(client, "/auth/tv/approve").use { response ->
            assertEquals(200, response.code)
            assertTrue(ApiSessionInterceptor.sentAttestedSession(response))
        }

        assertEquals(listOf(challenge), sessions(), "nothing went out with the limited session")
        assertEquals(1, check.solves.get())
        assertEquals(listOf(ApiSessionTrigger.StepUp), listener.exchanges.map { it.trigger })
    }

    @Test
    fun `with no check on screen a limited session doesn't lend, and the answer asks for the check`() {
        val check = FakeCheck(TurnstileResult.Token("ts")).apply { onScreen = false }
        val client = client(manager(check))

        lend(client).use { response ->
            assertEquals(401, response.code)
            assertEquals(ApiErrorCodes.VERIFICATION_REQUIRED, ApiErrorCodes.parse(response.body?.string()))
            assertFalse(ApiSessionInterceptor.sentAttestedSession(response))
        }
        assertTrue(received.isEmpty(), "answered on the phone: no lending spent on a token nobody can trade")
        assertEquals(0, check.solves.get())

        check.onScreen = true
        lend(client).use { assertEquals(200, it.code, "the app on screen runs the check, with no cooldown from before") }
        assertEquals(listOf(challenge), sessions())
    }

    @Test
    fun `a skipped check doesn't lend either, and the next try asks again`() {
        val check = FakeCheck(TurnstileResult.Failed(TurnstileResult.SKIPPED), TurnstileResult.Token("ts"))
        val client = client(manager(check))

        lend(client).use { assertEquals(401, it.code) }
        lend(client).use { assertEquals(200, it.code) }

        assertEquals(listOf(challenge), sessions())
        assertEquals(2, check.solves.get(), "lending is the listener's own ask: past the cooldown and the Skip")
    }

    @Test
    fun `with step-ups switched off, a limited session lends as it is and the API decides`() {
        val check = FakeCheck(TurnstileResult.Token("ts"))
        val client = client(manager(check, stepUps = { false }))

        lend(client).use { assertEquals(200, it.code) }

        assertEquals(listOf(limited), sessions())
        assertEquals(0, check.solves.get())
    }

    @Test
    fun `a limited session's lending refused for want of an attested phone steps up and is sent once more`() {
        // Step-ups come on just after the request went out, so it reached the API with the limited session.
        val asked = AtomicInteger()
        val check = FakeCheck(TurnstileResult.Token("ts"))
        val client = client(manager(check, stepUps = { asked.getAndIncrement() > 0 }))
        answers[limited] = error(403, ApiErrorCodes.ATTESTED_SESSION_REQUIRED)

        lend(client).use { assertEquals(200, it.code) }

        assertEquals(listOf(limited, challenge), sessions())
        assertEquals(1, check.solves.get())
    }

    @Test
    fun `a checked session refused for want of App Check is passed on as it is`() {
        store.state = StoredApiSession(token = challenge, installId = "install-123", appCheckFailures = 1, nextUpgradeAtMs = BASE_MS + 3_600_000)
        val check = FakeCheck(TurnstileResult.Token("ts"))
        val client = client(manager(check))
        answers[challenge] = error(403, ApiErrorCodes.ATTESTED_SESSION_REQUIRED)

        lend(client).use { response ->
            assertEquals(403, response.code)
            assertTrue(ApiSessionInterceptor.sentAttestedSession(response), "so the phone says it can't lend, rather than asking for a check")
        }

        assertEquals(listOf(challenge), sessions())
        assertEquals(0, check.solves.get(), "no check on screen gives App Check")
    }
}
