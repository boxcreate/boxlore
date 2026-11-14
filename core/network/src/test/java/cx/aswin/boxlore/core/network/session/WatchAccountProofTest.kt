package cx.aswin.boxlore.core.network.session

import cx.aswin.boxlore.core.network.NetworkModule
import cx.aswin.boxlore.core.network.model.WatchTokenRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The watch account API, as [NetworkModule] builds it, signs each request with the device key. */
class WatchAccountProofTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceKey = FakeDeviceKey()
    private val server = MockWebServer()
    private var before: ApiSessionManager? = null

    @BeforeEach
    fun setUp() {
        server.start()
        before = NetworkModule.sessionManager
        NetworkModule.sessionManager =
            ApiSessionManager(
                apiBaseUrl = "https://api.example.com/",
                exchanger = FakeExchanger { issued(sessionToken()) },
                store = FakeStore(),
                deviceKey = deviceKey,
                appCheck = { AppCheckResult.Failed("unused") },
                scope = scope,
                clock = { BASE_MS },
            )
    }

    @AfterEach
    fun tearDown() {
        NetworkModule.sessionManager = before
        server.shutdown()
        scope.cancel()
    }

    /** A client that attaches [token] as the session, the way the session interceptor does. */
    private fun clientWith(token: String?): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                val request = if (token == null) chain.request() else chain.request().newBuilder().header(ApiSessionInterceptor.SESSION_HEADER, token).build()
                chain.proceed(request)
            }.build()

    private fun redeemThrough(client: OkHttpClient): String? {
        server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody("""{"token":"watch-token"}"""))
        NetworkModule.createWatchAccountApi(server.url("/").toString(), client).redeem(WatchTokenRequest("code-1")).execute()
        return server.takeRequest().getHeader(RequestProofInterceptor.PROOF_HEADER)
    }

    // Regression guard: without the interceptor on this API the account routes refuse the watch.
    @Test
    fun `a request that carries a session is signed over that session, the method and the path`() {
        val proof = redeemThrough(clientWith(sessionToken(sid = "sid-9")))

        val message = RequestProofSigner.message("sid-9", "POST", "/auth/watch/token", BASE_S)
        assertEquals("$BASE_S.${deviceKey.signatureOf(message)}", proof)
    }

    @Test
    fun `no session, or a session without a device key, goes out unsigned`() {
        assertNull(redeemThrough(clientWith(null)))
        assertNull(redeemThrough(clientWith(sessionToken(deviceKey = false))))
    }
}
