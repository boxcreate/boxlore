package cx.aswin.boxlore.core.rss

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression (0.0.18 OutOfMemoryError in the RSS library parser): the library copies the whole
 * feed into one String, so large feeds use only the streaming pull parser.
 */
class RssParsePolicyTest {
    @Test
    fun `library parser only runs for feeds up to the limit`() {
        assertTrue(RssParsePolicy.useLibraryParser(RssParsePolicy.LIBRARY_PARSER_MAX_BYTES))
        assertFalse(RssParsePolicy.useLibraryParser(RssParsePolicy.LIBRARY_PARSER_MAX_BYTES + 1))
    }

    @Test
    fun `a large feed still parses through the streaming parser`() = runTest {
        val feed = requireNotNull(javaClass.getResourceAsStream("/feed_standard.xml")).readBytes().decodeToString()
        val padding = "<!--" + "x".repeat(RssParsePolicy.LIBRARY_PARSER_MAX_BYTES) + "-->"
        val large = feed.replaceFirst("<channel>", "<channel>$padding").encodeToByteArray()

        val parsed = RssFeedClient().parse("https://example.com/feed.xml", large)

        assertEquals("Test Standard Podcast", parsed.title)
        assertEquals(2, parsed.episodes.size)
    }
}
