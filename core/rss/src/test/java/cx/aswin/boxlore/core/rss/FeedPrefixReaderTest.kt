package cx.aswin.boxlore.core.rss

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The streaming read of a feed's newest items stops early and leaves a document that still parses as a whole. */
class FeedPrefixReaderTest {
    @Test
    fun stopsAfterTheItemLimitAndClosesTheRssDocument() {
        val prefix = FeedPrefixReader.read(input(rss(150)), maxItems = 100, maxBytes = MAX)

        assertFalse(prefix.complete)
        assertEquals(100, prefix.itemCount)
        val text = prefix.bytes.decodeToString()
        assertTrue(text.contains("<guid>g99</guid>"))
        assertFalse(text.contains("<guid>g100</guid>"))
        assertTrue(text.trimEnd().endsWith("</channel>\n</rss>"))
    }

    @Test
    fun aShortFeedIsReadWhole() {
        val document = rss(30)
        val prefix = FeedPrefixReader.read(input(document), maxItems = 100, maxBytes = MAX)

        assertTrue(prefix.complete)
        assertEquals(30, prefix.itemCount)
        assertEquals(document, prefix.bytes.decodeToString())
    }

    @Test
    fun tagsSplitAcrossReadsAndAnyCaseAreFound() {
        val document = rss(5).replace("</item>", "</ITEM>")
        val prefix = FeedPrefixReader.read(TrickleStream(document.encodeToByteArray(), chunk = 3), maxItems = 3, maxBytes = MAX)

        assertFalse(prefix.complete)
        assertEquals(3, prefix.itemCount)
        assertTrue(prefix.bytes.decodeToString().contains("<guid>g2</guid>"))
        assertFalse(prefix.bytes.decodeToString().contains("<guid>g3</guid>"))
    }

    @Test
    fun atomEntriesCloseTheFeedElement() {
        val entries = (0 until 4).joinToString("") { "<entry><id>e$it</id></entry>" }
        val prefix = FeedPrefixReader.read(input("<feed>$entries</feed>"), maxItems = 2, maxBytes = MAX)

        assertEquals(2, prefix.itemCount)
        assertTrue(prefix.bytes.decodeToString().trimEnd().endsWith("<id>e1</id></entry>\n</feed>"))
    }

    @Test
    fun aDocumentPastTheByteCapFails() {
        assertThrows(IllegalArgumentException::class.java) {
            FeedPrefixReader.read(input(rss(50)), maxItems = 100, maxBytes = 200)
        }
    }

    @Test
    fun fetchNewestSendsValidatorsTreatsA304AsCurrentAndReportsCompleteness() = runTest {
        val requests = mutableListOf<Request>()
        var code = 304
        val client =
            RssFeedClient(
                OkHttpClient.Builder().addInterceptor { chain ->
                    requests += chain.request()
                    Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(code).message("test")
                        .header("ETag", "whole-doc").body(rss(150).toResponseBody("application/rss+xml".toMediaType())).build()
                }.build(),
            )

        assertNull(client.fetchNewest("https://example.com/feed", 100, "etag-1", null))
        assertEquals("etag-1", requests.single().header("If-None-Match"))

        code = 200
        val read = requireNotNull(client.fetchNewest("https://example.com/feed", 100))
        assertFalse(read.complete)
        assertEquals("whole-doc", read.result.etag)
        assertNull(requests.last().header("If-None-Match"))
        assertTrue(requireNotNull(client.fetchNewest("https://example.com/feed", 500)).complete)
    }

    private fun input(text: String): InputStream = ByteArrayInputStream(text.encodeToByteArray())

    /** Hands out at most [chunk] bytes per read, like a slow connection. */
    private class TrickleStream(private val bytes: ByteArray, private val chunk: Int) : InputStream() {
        private var position = 0

        override fun read(): Int = if (position < bytes.size) bytes[position++].toInt() and 0xFF else -1

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (position >= bytes.size) return -1
            val count = minOf(chunk, length, bytes.size - position)
            System.arraycopy(bytes, position, buffer, offset, count)
            position += count
            return count
        }
    }

    private companion object {
        const val MAX = 25L * 1024L * 1024L

        fun rss(items: Int): String {
            val body = (0 until items).joinToString("") { "<item><title>T$it</title><guid>g$it</guid></item>" }
            return "<?xml version=\"1.0\"?><rss version=\"2.0\"><channel><title>Show</title>$body</channel></rss>"
        }
    }
}
