package cx.aswin.boxlore.core.rss

import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test

class RssConditionalFetchTest {
    @Test fun conditionalGetSendsValidatorsAnd304SkipsBodyWithoutHead() = runTest {
        val requests = mutableListOf<Request>()
        val client = client(requests, 304)
        assertNull(client.fetchConditional("https://example.com/feed", "etag-1", "yesterday"))
        assertEquals(1, requests.size)
        assertEquals("GET", requests.single().method)
        assertEquals("etag-1", requests.single().header("If-None-Match"))
        assertEquals("yesterday", requests.single().header("If-Modified-Since"))
    }

    @Test fun serverWithoutValidatorsReturnsBodyInOneGet() = runTest {
        val requests = mutableListOf<Request>()
        val result = client(requests, 200).fetchConditional("https://example.com/feed", null, null)!!
        assertEquals("<rss/>", result.body.decodeToString())
        assertEquals("new-etag", result.etag)
        assertEquals(1, requests.size)
        assertNull(requests.single().header("If-None-Match"))
    }

    @Test fun httpFailureIsNotReportedAsUnchanged() = runTest {
        try {
            client(mutableListOf(), 403).fetchConditional("https://example.com/feed", "etag", null)
            fail("403 must fail")
        } catch (error: IllegalArgumentException) {
            assertEquals("Feed returned HTTP 403", error.message)
        }
    }

    private fun client(requests: MutableList<Request>, code: Int): RssFeedClient = RssFeedClient(
        OkHttpClient.Builder().addInterceptor { chain ->
            requests += chain.request()
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                .header("ETag", "new-etag").body("<rss/>".toResponseBody("application/rss+xml".toMediaType())).build()
        }.build(),
    )
}
