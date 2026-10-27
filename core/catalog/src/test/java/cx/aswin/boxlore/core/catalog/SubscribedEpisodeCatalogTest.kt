package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
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

    @Test fun cleanupExplicitlyVisitsBothAdapters() = runTest {
        val visits = mutableListOf<String>()
        val pi = object : LocalEpisodeCatalogPort by FakeLocalEpisodeCatalogPort() {
            override suspend fun sweepExpired(nowMillis: Long) {
                visits += "pi:$nowMillis"
            }
        }
        val rss = object : LocalEpisodeCatalogPort by FakeLocalEpisodeCatalogPort() {
            override suspend fun sweepExpired(nowMillis: Long) {
                visits += "rss:$nowMillis"
            }
        }
        SubscribedEpisodeCatalog(pi, rss).sweepExpired(123)
        assertEquals(listOf("pi:123", "rss:123"), visits)
    }

    @Test fun everyRefreshPathFollowsAFeedThatMovedForGood() = runTest {
        val moved = LocalEpisodeCatalogPort.RefreshOutcome.Success(newest = null, itemCount = 1, ready = true, movedTo = "https://new.example/feed.xml")
        val pi = FakeLocalEpisodeCatalogPort().apply { refreshOutcome = moved }
        val rss = FakeLocalEpisodeCatalogPort().apply { refreshOutcome = moved }
        var enabled = true
        val adopted = mutableListOf<Pair<String, String>>()
        val shared =
            SubscribedEpisodeCatalog(
                pi,
                rss,
                feedMoves = FeedMoveFollower(enabled = { enabled }, showTitle = { "Stored title" }, adopt = { id, url -> adopted += id to url }),
            )

        // A push, the show page or auto-download: no move check of its own, no title.
        shared.refresh(RefreshRequest("123", "https://old.example/feed.xml", reason = LocalEpisodeCatalogPort.RefreshReason.NEW_RELEASE))
        assertEquals(true, pi.lastRefreshRequest?.detectMove)
        assertEquals("Stored title", pi.lastRefreshRequest?.meta?.title)
        assertEquals(listOf("123" to "https://new.example/feed.xml"), adopted)

        // A caller that asks for move detection itself saves the move itself.
        adopted.clear()
        shared.refresh(RefreshRequest("123", "https://old.example/feed.xml", meta = LocalEpisodeCatalogPort.PodcastMeta(title = "Own"), detectMove = true))
        assertEquals("Own", pi.lastRefreshRequest?.meta?.title)
        assertEquals(emptyList<Pair<String, String>>(), adopted)

        // rss: shows keep their address; the switch turns it all off.
        shared.refresh(RefreshRequest("rss:abc", "https://old.example/feed.xml"))
        assertEquals(false, rss.lastRefreshRequest?.detectMove)
        enabled = false
        shared.refresh(RefreshRequest("123", "https://old.example/feed.xml"))
        assertEquals(false, pi.lastRefreshRequest?.detectMove)
        assertEquals(emptyList<Pair<String, String>>(), adopted)
    }

    @Test fun aFailedAdoptionStillReturnsTheRefresh() = runTest {
        val outcome = LocalEpisodeCatalogPort.RefreshOutcome.Success(newest = null, itemCount = 1, ready = true, movedTo = "https://new.example/feed.xml")
        val pi = FakeLocalEpisodeCatalogPort().apply { refreshOutcome = outcome }
        val shared =
            SubscribedEpisodeCatalog(
                pi,
                FakeLocalEpisodeCatalogPort(),
                feedMoves = FeedMoveFollower(enabled = { true }, showTitle = { null }, adopt = { _, _ -> error("database closed") }),
            )

        assertEquals(outcome, shared.refresh(RefreshRequest("123", "https://old.example/feed.xml")))
    }
}
