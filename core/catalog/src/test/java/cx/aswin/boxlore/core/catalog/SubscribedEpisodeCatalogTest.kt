package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class SubscribedEpisodeCatalogTest {
    @Test fun negativePiSupplementsRemainUnderCatalogSourceAndRssShowsRouteToRss() = runTest {
        val pi = FakeLocalEpisodeCatalogPort(episodes = mutableMapOf("-1" to episode("-1", "123")))
        val rss = FakeLocalEpisodeCatalogPort(episodes = mutableMapOf("-2" to episode("-2", "rss:abc")))
        val shared = SubscribedEpisodeCatalog(pi, rss)
        assertEquals(listOf("-1"), shared.getPage("123", 5, 0, "newest").map { it.id })
        assertEquals(listOf("-2"), shared.getPage("rss:abc", 5, 0, "newest").map { it.id })
        assertEquals("123", shared.getEpisode("-1")!!.podcastId)
        assertEquals("rss:abc", shared.getEpisode("-2")!!.podcastId)
        shared.refresh(RefreshRequest("rss:abc", "https://example.com/feed"))
        assertEquals(0, pi.refreshCalls)
        assertEquals(1, rss.refreshCalls)
    }

    @Test fun sourceSwitchResolvesSharedNegativeIdToActiveSourceWithoutRekeyingEpisode() = runTest {
        val pi = FakeLocalEpisodeCatalogPort(episodes = mutableMapOf("-99" to episode("-99", "123")))
        val rss = FakeLocalEpisodeCatalogPort(episodes = mutableMapOf("-99" to episode("-99", "rss:abc")))
        var rssActive = true
        val shared = SubscribedEpisodeCatalog(pi, rss, isSubscribed = { it == "rss:abc" && rssActive })
        assertEquals("rss:abc", shared.getEpisode("-99")!!.podcastId)
        rssActive = false
        assertEquals("123", shared.getEpisode("-99")!!.podcastId)
        assertEquals("-99", shared.getEpisode("-99")!!.id)
    }

    private fun episode(id: String, podcastId: String) = Episode(id, "Episode", "", "https://example.com/$id.mp3", duration = 10, podcastId = podcastId)
}
