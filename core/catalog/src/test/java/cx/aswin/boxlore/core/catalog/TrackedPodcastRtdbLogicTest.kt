package cx.aswin.boxlore.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackedPodcastRtdbLogicTest {
    @Test
    fun payloadOmitsFeedUrlWhenMissingOrNotHttps() {
        assertEquals(
            mapOf("title" to "Show", "imageUrl" to "https://img"),
            TrackedPodcastRtdbLogic.payload("Show", "https://img", null),
        )
        assertEquals(
            mapOf("title" to "Show", "imageUrl" to "https://img"),
            TrackedPodcastRtdbLogic.payload("Show", "https://img", "http://insecure.example/feed"),
        )
        assertEquals(
            mapOf("title" to "Show", "imageUrl" to "https://img"),
            TrackedPodcastRtdbLogic.payload("Show", "https://img", "  "),
        )
    }

    @Test
    fun payloadIncludesHttpsFeedUrl() {
        val data =
            TrackedPodcastRtdbLogic.payload(
                title = "Show",
                imageUrl = "https://img",
                feedUrl = " https://feeds.example/show.xml ",
            )
        assertEquals("https://feeds.example/show.xml", data["feedUrl"])
        assertEquals("Show", data["title"])
    }

    @Test
    fun attachableFeedUrlRequiresTipToSeedLastRssKey() {
        assertNull(
            TrackedPodcastRtdbLogic.attachableFeedUrl(
                feedUrl = "https://feeds.example/show.xml",
                latestEpisodeId = null,
            ),
        )
        assertNull(
            TrackedPodcastRtdbLogic.attachableFeedUrl(
                feedUrl = "https://feeds.example/show.xml",
                latestEpisodeId = "  ",
            ),
        )
        assertEquals(
            "https://feeds.example/show.xml",
            TrackedPodcastRtdbLogic.attachableFeedUrl(
                feedUrl = "https://feeds.example/show.xml",
                latestEpisodeId = "-9",
            ),
        )
    }

    @Test
    fun httpsFeedUrlRejectsBlankAndHttp() {
        assertNull(TrackedPodcastRtdbLogic.httpsFeedUrl(null))
        assertNull(TrackedPodcastRtdbLogic.httpsFeedUrl("http://x"))
        assertEquals(
            "https://feeds.example/a.xml",
            TrackedPodcastRtdbLogic.httpsFeedUrl("https://feeds.example/a.xml"),
        )
    }
}
