package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import kotlinx.coroutines.test.runTest
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Moved feeds (P4a): only permanent moves to HTTPS that still serve the same show are adopted. */
class FeedMoveTest {
    private val old = "https://old.example.com/feed.xml"
    private val new = "https://new.example.com/feed.xml"

    @Test
    fun `the library parser reads itunes new-feed-url`() = runTest {
        val bytes = requireNotNull(javaClass.getResourceAsStream("/feed_moved.xml")).readBytes()
        assertEquals(new, RssFeedClient().parse(old, bytes).newFeedUrl)
    }

    @Test
    fun `only an all-permanent redirect chain is a move`() {
        assertEquals(new, permanentMoveTarget(response(new, prior = listOf(301))))
        assertEquals(new, permanentMoveTarget(response(new, prior = listOf(308, 301))))
        assertNull(permanentMoveTarget(response(new, prior = listOf(302))))
        assertNull(permanentMoveTarget(response(new, prior = listOf(301, 307))), "one temporary hop")
        assertNull(permanentMoveTarget(response(old, prior = emptyList())), "no redirect")
    }

    @Test
    fun `a permanent redirect is adopted only for the same show`() = runTest {
        val client = FakeClient(emptyMap())
        val fetched = RssFetchResult(finalUrl = new, etag = null, lastModified = null, body = ByteArray(0), movedTo = new)
        assertEquals(new, confirmedMove(client, request("Moving Show"), fetched, parsed("Moving show!")))
        assertNull(confirmedMove(client, request("Moving Show"), fetched, parsed("Another Show")), "different title")
        assertNull(confirmedMove(client, request(null), fetched, parsed("Moving Show")), "no title to compare")
        assertNull(confirmedMove(client, request("Moving Show"), fetched.copy(movedTo = "http://new.example.com/feed.xml"), parsed("Moving Show")), "not HTTPS")
    }

    @Test
    fun `a declared new feed address is read once and adopted when it serves the same show`() = runTest {
        val fetched = RssFetchResult(finalUrl = old, etag = null, lastModified = null, body = ByteArray(0))
        val same = FakeClient(mapOf(new to "Moving Show"))
        assertEquals(new, confirmedMove(same, request("Moving Show"), fetched, parsed("Moving Show", newFeedUrl = new)))
        assertEquals(listOf(new), same.fetched)

        val other = FakeClient(mapOf(new to "Something Else"))
        assertNull(confirmedMove(other, request("Moving Show"), fetched, parsed("Moving Show", newFeedUrl = new)))

        val self = FakeClient(emptyMap())
        assertNull(confirmedMove(self, request("Moving Show"), fetched, parsed("Moving Show", newFeedUrl = old)), "declares itself")
        assertTrue(self.fetched.isEmpty())

        val broken = FakeClient(emptyMap())
        assertNull(confirmedMove(broken, request("Moving Show"), fetched, parsed("Moving Show", newFeedUrl = new)), "new address fails")
    }

    @Test
    fun `a declared new feed address is never read when the caller's gate refuses another fetch`() = runTest {
        val fetched = RssFetchResult(finalUrl = old, etag = null, lastModified = null, body = ByteArray(0))
        val client = FakeClient(mapOf(new to "Moving Show"))
        val gated = request("Moving Show").copy(canProceed = { false })

        assertNull(confirmedMove(client, gated, fetched, parsed("Moving Show", newFeedUrl = new)))
        assertTrue(client.fetched.isEmpty())
    }

    @Test
    fun `titles match ignoring case, spacing and punctuation`() {
        assertTrue(sameShow("The Daily", "the daily"))
        assertTrue(sameShow("Teen Taal – Aaj Tak", "Teen Taal - Aaj Tak"))
        assertFalse(sameShow("", ""))
        assertFalse(sameShow("The Daily", "The Weekly"))
    }

    private fun request(title: String?) = LocalEpisodeCatalogPort.RefreshRequest(
        podcastIndexId = "123",
        feedUrl = old,
        meta = LocalEpisodeCatalogPort.PodcastMeta(title = title),
        detectMove = true,
    )

    private fun parsed(title: String, newFeedUrl: String? = null) = ParsedRssFeed(
        title = title,
        author = "",
        description = null,
        imageUrl = null,
        genre = null,
        podcastType = "episodic",
        podcastGuid = null,
        declaredUpdatedAt = null,
        episodes = emptyList(),
        newFeedUrl = newFeedUrl,
    )

    private fun response(finalUrl: String, prior: List<Int>): Response {
        var previous: Response? = null
        for (code in prior.reversed()) {
            previous = Response.Builder()
                .request(Request.Builder().url(old).build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message("redirect")
                .apply { if (previous != null) priorResponse(previous) }
                .build()
        }
        return Response.Builder()
            .request(Request.Builder().url(finalUrl).build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .apply { if (previous != null) priorResponse(previous) }
            .build()
    }

    /** Serves a parsed title per URL; any other URL fails like a dead address. */
    private class FakeClient(private val titles: Map<String, String>) : RssFeedClient() {
        val fetched = mutableListOf<String>()

        override suspend fun fetch(url: String): RssFetchResult {
            fetched += url
            if (url !in titles) throw FeedHttpException(404)
            return RssFetchResult(finalUrl = url, etag = null, lastModified = null, body = titles.getValue(url).toByteArray())
        }

        override suspend fun parse(feedUrl: String, bytes: ByteArray, podcastId: String,) = ParsedRssFeed(
            title = String(bytes),
            author = "",
            description = null,
            imageUrl = null,
            genre = null,
            podcastType = "episodic",
            podcastGuid = null,
            declaredUpdatedAt = null,
            episodes = emptyList(),
        )
    }
}
