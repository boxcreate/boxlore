package cx.aswin.boxlore.core.catalog

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackedPodcastRtdbLogicTest {
    @Test fun acceptedFeedScopesUseTheSameHashAsTheCheckerAndKeepCatalogTopicsStable() {
        val url = "https://publisher.example/public.xml"
        assertEquals("new_ep_rss_012345_4828c14697465b35180be700e5bc4ce4122e206d27db253f0232414f6f656026", TrackedPodcastRtdbLogic.topic("rss:012345", url))
        assertNotEquals(TrackedPodcastRtdbLogic.topic("rss:012345", url), TrackedPodcastRtdbLogic.topic("rss:012345", "$url?different"))
        assertEquals("new_ep_123", TrackedPodcastRtdbLogic.topic("123", url))
        assertNotEquals(TrackedPodcastRtdbLogic.registrationKey("rss:012345", "device", url), TrackedPodcastRtdbLogic.registrationKey("rss:012345", "device", "$url?different"))
    }

    @Test fun mismatchedOrMissingRssPayloadUrlsFailBeforePresentation() {
        val url = "https://publisher.example/feed"
        assertTrue(TrackedPodcastRtdbLogic.acceptsRelease("rss:one", url, url))
        assertFalse(TrackedPodcastRtdbLogic.acceptsRelease("rss:one", url, "$url?different"))
        assertFalse(TrackedPodcastRtdbLogic.acceptsRelease("rss:one", url, null))
        assertFalse(TrackedPodcastRtdbLogic.acceptsRelease("rss:one", null, url))
        assertTrue(TrackedPodcastRtdbLogic.acceptsRelease("123", null, null))
    }

    @Test fun notificationTopicsKeepCatalogNamesAndMapRssColon() {
        assertEquals("new_ep_123", TrackedPodcastRtdbLogic.topic("123"))
        assertEquals("new_ep_rss_012345", TrackedPodcastRtdbLogic.topic("rss:012345"))
        assertEquals("rss:012345~device-a", TrackedPodcastRtdbLogic.registrationKey("rss:012345", "device-a"))
        assertEquals("123", TrackedPodcastRtdbLogic.registrationKey("123", "device-a"))
    }

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
