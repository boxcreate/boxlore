package cx.aswin.boxlore.updates

import java.util.ArrayDeque
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DirectUpdateSourceTest {
    @Test fun `only a newer compatible valid manifest becomes available`() = runBlocking {
        val responses = ArrayDeque(listOf(updateManifest().encode(), updateManifest(28).encode(), updateManifest().copy(minSdk = 35).encode()))
        var cacheHeader: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            cacheHeader = chain.request().header("Cache-Control")
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(responses.removeFirst().toResponseBody()).build()
        }.build()
        val source = DirectUpdateSource(client, "https://example.invalid/update.json", 28, 34)
        assertEquals(29L, source.lookup().offer?.versionCode)
        assertEquals("no-cache", cacheHeader)
        assertNull(source.lookup().offer)
        assertEquals(UpdateLookup(incompatible = true), source.lookup())
    }

    @Test fun `HTTP failures malformed or oversized data never report current`() = runBlocking {
        for ((code, body) in listOf(404 to "missing", 200 to "{broken}", 200 to "a".repeat(65 * 1024))) {
            val client = OkHttpClient.Builder().addInterceptor { chain ->
                Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                    .body(body.toResponseBody()).build()
            }.build()
            val source = DirectUpdateSource(client, "https://example.invalid/update.json", 28, 34)
            assertTrue(runCatching { source.lookup() }.isFailure)
        }
    }
}
