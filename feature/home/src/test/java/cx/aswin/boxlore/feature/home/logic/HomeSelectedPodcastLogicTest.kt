package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HomeSelectedPodcastLogicTest {
    @Test
    fun `build signal returns null without podcast id`() {
        assertNull(
            HomeSelectedPodcastLogic.buildSignal(
                podcastId = null,
                allHistory = emptyList(),
                subs = emptyList(),
                rssRefreshVersion = 0L,
            ),
        )
    }

    @Test
    fun `build signal of a show listened to in order carries the place and preferred sort`() {
        val subs = listOf(TestFixtures.podcast(id = "pod-1").copy(preferredSort = "oldest"))
        val history =
            listOf(
                HomeListeningHistoryItem(
                    episodeId = "ep-1",
                    podcastId = "pod-1",
                    podcastName = "Show",
                    podcastImageUrl = null,
                    progressMs = 1L,
                    durationMs = 10L,
                    isCompleted = false,
                    isLiked = false,
                    lastPlayedAt = 1L,
                ),
                HomeListeningHistoryItem(
                    episodeId = "ep-2",
                    podcastId = "pod-1",
                    podcastName = "Show",
                    podcastImageUrl = null,
                    progressMs = 1L,
                    durationMs = 10L,
                    isCompleted = false,
                    isLiked = false,
                    lastPlayedAt = 9L,
                ),
            )

        val signal =
            HomeSelectedPodcastLogic.buildSignal(
                podcastId = "pod-1",
                allHistory = history,
                subs = subs,
                rssRefreshVersion = 3L,
            )

        assertEquals("pod-1", signal?.podcastId)
        // Both are in progress; the place is the one played last.
        assertEquals("ep-2", signal?.placeHistoryId)
        assertEquals("oldest", signal?.sort)
        assertEquals(3L, signal?.rssRefreshVersion)
        assertEquals(subs[0].latestEpisode?.id, signal?.latestEpisodeId)
        assertEquals(false, signal?.rssHasNewEpisodes)
    }

    @Test
    fun `build signal of a show switched to its feed reads its old show's last play`() {
        val feedShow = TestFixtures.podcast(id = "rss:feed").copy(sourceType = cx.aswin.boxlore.core.model.Podcast.SOURCE_RSS, linkedPodcastIndexId = "pi-show", preferredSort = "oldest")
        val played = HomeListeningHistoryItem("pi-2", "pi-show", "Show", null, 1L, 10L, false, false, 5L)

        val signal = HomeSelectedPodcastLogic.buildSignal(podcastId = "rss:feed", allHistory = listOf(played), subs = listOf(feedShow), rssRefreshVersion = 0L)

        assertEquals("pi-2", signal?.placeHistoryId)
        // Another show's rows never count.
        assertNull(HomeSelectedPodcastLogic.buildSignal(podcastId = "rss:feed", allHistory = listOf(played.copy(podcastId = "other")), subs = listOf(feedShow), rssRefreshVersion = 0L)?.placeHistoryId)
    }

    @Test
    fun `build signal includes latest episode id and rss new flag`() {
        val latest = TestFixtures.episode(id = "tip-9")
        val subs =
            listOf(
                TestFixtures.podcast(id = "pod-1").copy(
                    latestEpisode = latest,
                    rssHasNewEpisodes = true,
                ),
            )
        val signal =
            HomeSelectedPodcastLogic.buildSignal(
                podcastId = "pod-1",
                allHistory = emptyList(),
                subs = subs,
                rssRefreshVersion = 0L,
            )
        assertEquals("tip-9", signal?.latestEpisodeId)
        assertEquals(true, signal?.rssHasNewEpisodes)
    }

    @Test
    fun `newest-first shows don't reload when another episode is played`() {
        val subs = listOf(TestFixtures.podcast(id = "pod-1").copy(preferredSort = "newest"))
        val history = listOf(HomeListeningHistoryItem("ep-1", "pod-1", "Show", null, 1L, 10L, false, false, 5L))

        val signal = HomeSelectedPodcastLogic.buildSignal(podcastId = "pod-1", allHistory = history, subs = subs, rssRefreshVersion = 0L)

        assertNull(signal?.placeHistoryId)
        assertEquals(false, signal?.inOrder)
    }

    @Test
    fun `try again changes the signal so the show loads again`() {
        val subs = listOf(TestFixtures.podcast(id = "pod-1"))

        val first = HomeSelectedPodcastLogic.buildSignal("pod-1", emptyList(), subs, rssRefreshVersion = 0L, retryVersion = 0L)
        val retried = HomeSelectedPodcastLogic.buildSignal("pod-1", emptyList(), subs, rssRefreshVersion = 0L, retryVersion = 1L)

        assertNotEquals(first, retried)
    }

    @Test
    fun `should show loading only when switching or list is empty`() {
        assertEquals(true, HomeSelectedPodcastLogic.shouldShowLoading(switchingPodcast = true, listEmpty = false))
        assertEquals(true, HomeSelectedPodcastLogic.shouldShowLoading(switchingPodcast = false, listEmpty = true))
        assertEquals(false, HomeSelectedPodcastLogic.shouldShowLoading(switchingPodcast = false, listEmpty = false))
    }
}
