package cx.aswin.boxlore.core.network

import cx.aswin.boxlore.core.network.session.ApiSessionInterceptor
import java.util.concurrent.CopyOnWriteArrayList
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The shared API client's limits, and what its debug header logs keep out. */
class NetworkModuleTest {
    @Test
    fun `the API client runs enough requests per host that a few waiting on a step-up hold up nothing else`() {
        assertEquals(NetworkModule.MAX_REQUESTS_PER_HOST, NetworkModule.apiDispatcher.maxRequestsPerHost)
        assertTrue(NetworkModule.MAX_REQUESTS_PER_HOST >= 16)
    }

    @Test
    fun `debug header logs redact credentials, a watch's or TV's fresh account token included`() {
        val server = MockWebServer().apply { start() }
        try {
            server.enqueue(
                MockResponse()
                    .setHeader(WatchAccountApi.RENEW_HEADER, "bwt1.fresh-account-token")
                    .setHeader(ApiSessionInterceptor.RENEW_HEADER, "bls1.fresh-session")
                    .setBody("{}"),
            )
            val lines = CopyOnWriteArrayList<String>()
            val client = OkHttpClient.Builder().addInterceptor(NetworkModule.headerLogger { lines += it }).build()

            client.newCall(Request.Builder().url(server.url("/user/sync/pull")).header("Authorization", "Bearer bwt1.sent-token").build()).execute().close()

            assertTrue(lines.any { it.startsWith("${WatchAccountApi.RENEW_HEADER}:") }, "the header is named")
            assertFalse(lines.any { "bwt1." in it || "bls1." in it }, "no token value reaches the log")
        } finally {
            server.shutdown()
        }
    }
}
