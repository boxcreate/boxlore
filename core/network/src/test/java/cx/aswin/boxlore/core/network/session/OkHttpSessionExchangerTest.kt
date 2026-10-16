package cx.aswin.boxlore.core.network.session

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The `POST /auth/session` contract from the API shield README. */
class OkHttpSessionExchangerTest {
    private lateinit var server: MockWebServer
    private lateinit var exchanger: OkHttpSessionExchanger

    @BeforeEach
    fun setUp() {
        server = MockWebServer().apply { start() }
        exchanger = OkHttpSessionExchanger(
            apiBaseUrl = server.url("/").toString().trimEnd('/'),
            client = OkHttpClient(),
            appVersion = { "0.0.29" },
            channel = { "github" },
        )
    }

    @AfterEach
    fun tearDown() = server.shutdown()

    private fun body(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun `sends the install, platform, channel and device key with App Check in a header`() = runTest {
        val token = sessionToken()
        server.enqueue(MockResponse().setBody("""{"session":"$token","attestation":"appcheck","limited":false,"expiresAt":1,"attestedUntil":2}"""))

        val result = exchanger.exchange(SessionExchangeRequest("install-123", "device-pk", appCheckToken = "ac-token", turnstileToken = null))

        assertEquals(SessionExchangeResult.Issued(token, ApiAttestation.AppCheck, limited = false, reason = null), result)
        val recorded = server.takeRequest()
        assertEquals("POST", recorded.method)
        assertEquals("/auth/session", recorded.path)
        assertEquals("ac-token", recorded.getHeader("X-Firebase-AppCheck"))
        assertEquals("0.0.29", recorded.getHeader("X-App-Version"))
        assertEquals("github", recorded.getHeader("X-App-Channel"))
        assertNull(recorded.getHeader("X-App-Key"))
        val sent = body(recorded.body.readUtf8())
        assertEquals("install-123", sent["installId"]?.jsonPrimitive?.content)
        assertEquals("android", sent["platform"]?.jsonPrimitive?.content)
        assertEquals("github", sent["channel"]?.jsonPrimitive?.content)
        assertEquals("device-pk", sent["devicePublicKey"]?.jsonPrimitive?.content)
        assertFalse("turnstileToken" in sent, "absent fields are left out, not sent as null")
    }

    @Test
    fun `a step-up sends the Turnstile token and reads a limited answer with its reason`() = runTest {
        val token = sessionToken(att = "none")
        server.enqueue(MockResponse().setBody("""{"session":"$token","attestation":"none","limited":true,"reason":"turnstile:not-configured"}"""))

        val result = exchanger.exchange(SessionExchangeRequest("install-123", null, appCheckToken = null, turnstileToken = "ts"))

        assertEquals(SessionExchangeResult.Issued(token, ApiAttestation.None, limited = true, reason = "turnstile:not-configured"), result)
        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("X-Firebase-AppCheck"))
        val sent = body(recorded.body.readUtf8())
        assertEquals("ts", sent["turnstileToken"]?.jsonPrimitive?.content)
        assertFalse("devicePublicKey" in sent)
    }

    @Test
    fun `failures carry the status, the error code and any Retry-After`() = runTest {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60").setBody("""{"error":"too_many_attempts"}"""))
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"error":"sessions_unavailable"}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"install_revoked"}"""))
        server.enqueue(MockResponse().setBody("not json"))
        val request = SessionExchangeRequest("install-123", null, null, null)

        assertEquals(SessionExchangeResult.Failed("http_429_too_many_attempts", 60), exchanger.exchange(request))
        assertEquals(SessionExchangeResult.Failed("http_503_sessions_unavailable", null), exchanger.exchange(request))
        assertEquals(SessionExchangeResult.Failed("http_403_install_revoked", 3_600), exchanger.exchange(request))
        assertEquals(SessionExchangeResult.Failed("bad_response"), exchanger.exchange(request))
        val unreachable = OkHttpSessionExchanger("http://127.0.0.1:1", OkHttpClient(), { null }, { null })
        assertEquals(SessionExchangeResult.Failed("network"), unreachable.exchange(request))
    }
}
