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
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * A watch or TV against a real HTTP server: once signed in it trades its account token for a paired
 * session and sends everything else with that one; signed out, only its pairing routes go out (with
 * a limited session) and the rest is answered on the device. A refused token signs it out; 429 and
 * 503 back off and keep it signed in; a refused session is traded again.
 */
class PairedSessionTest {
    private lateinit var server: MockWebServer
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val clock = FakeClock()
    private val store = FakeStore()
    private val deviceKey = FakeDeviceKey()
    private val appCheckCalls = AtomicInteger()
    private val limitedCount = AtomicInteger()
    private val exchanger = FakeExchanger { issued(sessionToken(sid = "limited-${limitedCount.incrementAndGet()}", att = "none", tag = "l")) }
    private val account = FakeAccount()

    /** What `/auth/watch/session` answers, in turn; then a fresh paired session each time. */
    private val tradeAnswers = ArrayDeque<MockResponse>()
    private val trades = CopyOnWriteArrayList<RecordedRequest>()
    private val routes = CopyOnWriteArrayList<RecordedRequest>()
    private val routeAnswers = ArrayDeque<MockResponse>()
    private val pairedCount = AtomicInteger()

    private class FakeAccount : PairedSessionAccount {
        @Volatile var token: String? = null
        val refused = CopyOnWriteArrayList<String>()

        @Volatile var onRefused: () -> Unit = {}

        override fun accountToken(): String? = token

        override fun onTokenRefused(token: String) {
            refused += token
            if (this.token == token) this.token = null
            onRefused()
        }
    }

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.dispatcher =
            object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (request.path == ApiSessionManager.PAIRED_SESSION_PATH) {
                        trades += request
                        return synchronized(tradeAnswers) { tradeAnswers.removeFirstOrNull() } ?: paired("paired-${pairedCount.incrementAndGet()}")
                    }
                    routes += request
                    return synchronized(routeAnswers) { routeAnswers.removeFirstOrNull() } ?: MockResponse().setBody("{}")
                }
            }
        server.start()
    }

    @AfterEach
    fun tearDown() {
        scope.cancel()
        server.shutdown()
    }

    /** A manager for [account]; [beforeStart] runs before its first exchange. */
    private fun manager(store: FakeStore = this.store, beforeStart: (ApiSessionManager) -> Unit = {}): ApiSessionManager {
        val base = server.url("/").toString()
        return ApiSessionManager(
            apiBaseUrl = base,
            exchanger = exchanger,
            store = store,
            deviceKey = deviceKey,
            appCheck = {
                appCheckCalls.incrementAndGet()
                AppCheckResult.Failed("unavailable")
            },
            scope = scope,
            clock = clock,
            timing = ApiSessionTiming(requestWaitMs = 3_000, loadWaitMs = 1_000),
            pairedTrader = OkHttpPairedSessionTrader(base, OkHttpClient(), { "1.2.3" }, { "tv" }),
        ).also {
            it.pairedAccount = account
            beforeStart(it)
            it.start()
        }
    }

    private fun client(manager: ApiSessionManager) = OkHttpClient.Builder().addInterceptor(ApiSessionInterceptor { manager }).build()

    private fun get(client: OkHttpClient, path: String = "/trending") = client.newCall(Request.Builder().url(server.url(path)).build()).execute()

    private fun post(client: OkHttpClient, path: String) =
        client.newCall(Request.Builder().url(server.url(path)).post("{}".toRequestBody("application/json".toMediaType())).build()).execute()

    private fun pairedToken(sid: String) = sessionToken(sid = sid, att = "paired", tag = sid)

    private fun paired(sid: String) = MockResponse().setBody("""{"session":"${pairedToken(sid)}","attestation":"paired","limited":false}""")

    private fun error(status: Int, code: String) = MockResponse().setResponseCode(status).setBody("""{"error":"$code"}""")

    private fun RecordedRequest.session() = getHeader(ApiSessionInterceptor.SESSION_HEADER)

    private fun routesTo(path: String) = routes.filter { it.path == path }

    @Test
    fun `signed in, every route goes out with a paired session traded for the account token and signed by the device key`() {
        account.token = "bwt1.account"
        val client = client(manager())

        get(client).use { assertEquals(200, it.code) }
        get(client).use { assertEquals(200, it.code) }

        assertEquals(1, trades.size, "one trade serves every request while its session lasts")
        val trade = trades.single()
        val carrier = ApiSessionToken.parse(trade.session())!!
        assertEquals("limited-1", carrier.sid, "the trade carries a limited session when none is held")
        assertEquals("Bearer bwt1.account", trade.getHeader("Authorization"))
        assertEquals("$BASE_S.${deviceKey.signatureOf(RequestProofSigner.message("limited-1", "POST", ApiSessionManager.PAIRED_SESSION_PATH, BASE_S))}", trade.getHeader(RequestProofInterceptor.PROOF_HEADER))
        assertEquals("tv", trade.getHeader("X-App-Channel"))
        assertEquals(listOf(pairedToken("paired-1"), pairedToken("paired-1")), routesTo("/trending").map { it.session() })
        assertEquals(0, appCheckCalls.get(), "watches and TVs never ask App Check")
        eventually { ApiSessionToken.parse(store.state.token)?.attestation == ApiAttestation.Paired }
    }

    @Test
    fun `signed out, only the pairing routes go out, with a limited session, and the rest is answered on the device`() {
        val client = client(manager())

        get(client).use { response ->
            assertEquals(401, response.code)
            assertEquals(ApiErrorCodes.ATTESTATION_REQUIRED, ApiErrorCodes.parse(response.body?.string()))
        }
        post(client, "/auth/tv/start").use { assertEquals(200, it.code) }

        assertTrue(routesTo("/trending").isEmpty(), "nothing but pairing reaches the API before sign-in")
        assertEquals(ApiAttestation.None, ApiSessionToken.parse(routesTo("/auth/tv/start").single().session())?.attestation)
        assertTrue(trades.isEmpty())
    }

    @Test
    fun `a refused account token signs the device out, whether 401 watch_token_invalid or 403 attested_session_required`() {
        for ((status, code) in listOf(401 to ApiErrorCodes.WATCH_TOKEN_INVALID, 403 to ApiErrorCodes.ATTESTED_SESSION_REQUIRED)) {
            assertEquals(PairedSessionResult.TokenRefused, OkHttpPairedSessionTrader.resultOf(status, code, null))
        }
        assertEquals(PairedSessionResult.SessionRefused(ApiErrorCodes.SESSION_EXPIRED), OkHttpPairedSessionTrader.resultOf(401, ApiErrorCodes.SESSION_EXPIRED, null))
        assertEquals(PairedSessionResult.Failed("http_503_sign_in_unavailable", 60), OkHttpPairedSessionTrader.resultOf(503, "sign_in_unavailable", 60))
        assertEquals(PairedSessionResult.Failed("http_429_too_many_attempts", 60), OkHttpPairedSessionTrader.resultOf(429, "too_many_attempts", 60))
        assertEquals(PairedSessionResult.SessionRefused(ApiErrorCodes.DEVICE_KEY_REQUIRED), OkHttpPairedSessionTrader.resultOf(400, ApiErrorCodes.DEVICE_KEY_REQUIRED, null))

        val givenUp = "bwt1.given-up"
        account.token = givenUp
        tradeAnswers += error(403, ApiErrorCodes.ATTESTED_SESSION_REQUIRED)
        val client = client(manager { account.onRefused = it::onPairedAccountChanged })

        get(client).use { assertEquals(401, it.code) }
        post(client, "/auth/tv/start").use { assertEquals(200, it.code) }

        assertEquals(listOf(givenUp), account.refused)
        assertNull(account.token)
        assertTrue(routesTo("/trending").isEmpty())
        assertEquals(ApiAttestation.None, ApiSessionToken.parse(routesTo("/auth/tv/start").single().session())?.attestation, "a new TV code starts with the limited session")
        assertEquals(1, trades.size)
    }

    @Test
    fun `429 and 503 back off without signing out, and the trade runs again once the wait is over`() {
        account.token = "bwt1.account"
        tradeAnswers += error(503, "sign_in_unavailable").setHeader("Retry-After", "60")
        val client = client(manager())

        get(client).use { assertEquals(401, it.code, "no paired session yet: answered on the device") }
        get(client).use { assertEquals(401, it.code) }
        assertEquals(1, trades.size, "nothing is traded again inside the backoff")
        assertTrue(account.refused.isEmpty())
        assertEquals("bwt1.account", account.token)

        clock.advanceSeconds(61)
        get(client).use { assertEquals(200, it.code) }
        assertEquals(2, trades.size)
        assertEquals(listOf(pairedToken("paired-1")), routesTo("/trending").map { it.session() })
    }

    @Test
    fun `a paired session the API refuses is traded again, carried by the paired session no longer`() {
        account.token = "bwt1.account"
        store.state = StoredApiSession(token = pairedToken("old"), installId = "install-123")
        routeAnswers += error(401, ApiErrorCodes.ATTESTATION_REQUIRED)
        val client = client(manager())

        get(client).use { assertEquals(200, it.code) }

        assertEquals(listOf(pairedToken("old"), pairedToken("paired-1")), routesTo("/trending").map { it.session() })
        assertEquals("limited-1", ApiSessionToken.parse(trades.single().session())?.sid, "the refused session is dropped, so a limited one carries the trade")
    }

    @Test
    fun `a trade whose carrier session is refused gets one fresh limited session and trades again`() {
        account.token = "bwt1.account"
        store.state = StoredApiSession(token = sessionToken(sid = "stale", att = "none", tag = "stale"), installId = "install-123")
        tradeAnswers += error(401, ApiErrorCodes.SESSION_INVALID)
        val client = client(manager())

        get(client).use { assertEquals(200, it.code) }

        assertEquals(listOf("stale", "limited-1"), trades.map { ApiSessionToken.parse(it.session())?.sid })
        assertEquals(1, exchanger.calls)
        assertTrue(account.refused.isEmpty())
    }

    // Regression: a held session without the device key backed every trade off unsent, for up to a day.
    @Test
    fun `a held session without the device key is dropped, and a fresh one carries the trade`() {
        account.token = "bwt1.account"
        store.state = StoredApiSession(token = sessionToken(sid = "keyless", att = "none", deviceKey = false, tag = "keyless"), installId = "install-123")
        val client = client(manager())

        get(client).use { assertEquals(200, it.code) }

        assertEquals(listOf("limited-1"), trades.map { ApiSessionToken.parse(it.session())?.sid })
        assertEquals(1, exchanger.calls)
    }

    // Regression: each trade's fresh limited sign-in cleared the backoff, so it never grew past 2 seconds.
    @Test
    fun `the trade backoff keeps growing when every trade needs a fresh limited session`() {
        account.token = "bwt1.account"
        repeat(8) { tradeAnswers += error(401, ApiErrorCodes.SESSION_INVALID) }
        val client = client(manager())

        get(client).use { assertEquals(401, it.code) }
        eventually { trades.size == 2 }
        clock.advanceSeconds(3) // past the first wait (2 s)
        get(client).use { assertEquals(401, it.code) }
        assertEquals(4, trades.size)

        clock.advanceSeconds(3) // inside the second wait (5 s)
        get(client).use { assertEquals(401, it.code) }
        assertEquals(4, trades.size, "the second failure waits longer than the first")

        clock.advanceSeconds(3)
        get(client).use { assertEquals(401, it.code) }
        assertEquals(6, trades.size)
    }

    @Test
    fun `signing out drops the paired session, and one held from before a sign-out is dropped at start`() {
        account.token = "bwt1.account"
        val manager = manager()
        val client = client(manager)
        get(client).use { assertEquals(200, it.code) }

        account.token = null
        manager.onPairedAccountChanged()
        get(client).use { assertEquals(401, it.code) }
        post(client, "/auth/tv/start").use { assertEquals(200, it.code) }
        assertEquals(ApiAttestation.None, ApiSessionToken.parse(routesTo("/auth/tv/start").single().session())?.attestation)

        // A process that died signed out with a paired session stored keeps none.
        val restarted = client(manager(FakeStore(StoredApiSession(token = pairedToken("left-over"), installId = "install-123"))))
        post(restarted, "/auth/watch/revoke").use { assertEquals(200, it.code) }
        assertEquals(ApiAttestation.None, ApiSessionToken.parse(routesTo("/auth/watch/revoke").single().session())?.attestation)
    }
}
