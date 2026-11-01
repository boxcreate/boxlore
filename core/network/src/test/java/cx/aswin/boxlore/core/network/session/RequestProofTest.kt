package cx.aswin.boxlore.core.network.session

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/** Support requests carry the device key's signature over the session, method and path. */
class RequestProofTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val deviceKey = FakeDeviceKey()

    @AfterEach
    fun tearDown() = scope.cancel()

    private fun manager() = ApiSessionManager(
        apiBaseUrl = "https://api.example.com/",
        exchanger = FakeExchanger { issued(sessionToken()) },
        store = FakeStore(),
        deviceKey = deviceKey,
        appCheck = { AppCheckResult.Failed("unused") },
        scope = scope,
        clock = { BASE_MS },
    )

    @Test
    fun `signs the session, method and path on the API's clock`() {
        val proof = manager().requestProofs.proof(sessionToken(sid = "sid-9"), "post", "/support/threads")
        val message = RequestProofSigner.message("sid-9", "POST", "/support/threads", BASE_S)
        assertEquals("bls-request.sid-9.POST./support/threads.$BASE_S", message)
        assertEquals("$BASE_S.${deviceKey.signatureOf(message)}", proof)
    }

    @Test
    fun `no proof without a device key or a readable session`() {
        assertNull(manager().requestProofs.proof(sessionToken(deviceKey = false), "GET", "/support/threads"))
        assertNull(manager().requestProofs.proof("not-a-token", "GET", "/support/threads"))
        deviceKey.available = false
        assertNull(manager().requestProofs.proof(sessionToken(), "GET", "/support/threads"))
    }

    @Test
    fun `support requests carry the account token when signed in, and nothing when signed out`() {
        val server = MockWebServer().apply { start() }
        try {
            var token: String? = "id-token-1"
            val client = OkHttpClient.Builder().addInterceptor(cx.aswin.boxlore.core.network.AccountTokenInterceptor { token }).build()
            server.enqueue(MockResponse())
            server.enqueue(MockResponse())
            server.enqueue(MockResponse())
            client.newCall(Request.Builder().url(server.url("/support/threads")).build()).execute().close()
            client.newCall(Request.Builder().url(server.url("/support/threads")).header("Authorization", "Bearer other").build()).execute().close()
            token = null
            client.newCall(Request.Builder().url(server.url("/support/threads")).build()).execute().close()
            assertEquals("Bearer id-token-1", server.takeRequest().getHeader("Authorization"))
            assertEquals("Bearer other", server.takeRequest().getHeader("Authorization"))
            assertNull(server.takeRequest().getHeader("Authorization"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `the interceptor signs the session the request carries, and leaves other requests alone`() {
        val server = MockWebServer().apply { start() }
        try {
            val signed = mutableListOf<Triple<String, String, String>>()
            val client = OkHttpClient.Builder()
                .addNetworkInterceptor(
                    RequestProofInterceptor { token, method, path ->
                        signed += Triple(token, method, path)
                        "proof-$method"
                    },
                ).build()
            server.enqueue(MockResponse())
            server.enqueue(MockResponse())
            client.newCall(Request.Builder().url(server.url("/support/threads")).header(ApiSessionInterceptor.SESSION_HEADER, "token-1").build()).execute().close()
            client.newCall(Request.Builder().url(server.url("/support/threads")).build()).execute().close()
            assertEquals("proof-GET", server.takeRequest().getHeader(RequestProofInterceptor.PROOF_HEADER))
            assertNull(server.takeRequest().getHeader(RequestProofInterceptor.PROOF_HEADER))
            assertEquals(listOf(Triple("token-1", "GET", "/support/threads")), signed)
        } finally {
            server.shutdown()
        }
    }
}
