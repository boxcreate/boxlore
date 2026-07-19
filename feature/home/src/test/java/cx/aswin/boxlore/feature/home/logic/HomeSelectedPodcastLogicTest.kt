package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem
import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `build signal uses last played episode and preferred sort`() {
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
        assertEquals("ep-2", signal?.lastPlayedEpisodeId)
        assertEquals("oldest", signal?.sort)
        assertEquals(3L, signal?.rssRefreshVersion)
    }

    @Test
    fun `oldest sort window centers near last played with lookback`() {
        val episodes = (0..9).map { TestFixtures.episode(id = "ep-$it") }
        val window =
            HomeSelectedPodcastLogic.oldestSortWindow(
                allEpisodes = episodes,
                lastPlayedEpisodeId = "ep-5",
                windowSize = 4,
                lookback = 2,
            )

        assertEquals(listOf("ep-3", "ep-4", "ep-5", "ep-6"), window.map { it.id })
    }

    @Test
    fun `filter completed if needed removes completed ids`() {
        val episodes = listOf(TestFixtures.episode(id = "a"), TestFixtures.episode(id = "b"))
        assertEquals(
            listOf("a"),
            HomeSelectedPodcastLogic
                .filterCompletedIfNeeded(episodes, hideCompleted = true, completedIds = setOf("b"))
                .map { it.id },
        )
        assertEquals(
            listOf("a", "b"),
            HomeSelectedPodcastLogic
                .filterCompletedIfNeeded(episodes, hideCompleted = false, completedIds = setOf("b"))
                .map { it.id },
        )
    }
}
