package cx.aswin.boxlore.feature.home

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.testing.TestFixtures
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class HomeDiscoveryRankingCacheTest {
    private val input = HomeDiscoveryRankingInput(
        trending = listOf(TestFixtures.podcast()),
        recommendations = listOf(TestFixtures.episode()),
        history = emptyList(),
        subscribedIds = emptySet(),
    )

    @Test
    fun `repeated feed emissions reuse rankings until actual ranking inputs change`() = runTest {
        val cache = HomeDiscoveryRankingCache()
        var calls = 0
        val rank: suspend (HomeDiscoveryRankingInput) -> HomeDiscoveryRankingResult = {
            calls++
            HomeDiscoveryRankingResult(it.trending, it.recommendations)
        }
        val result = cache.get(input, rank)
        repeat(10) { assertSame(result, cache.get(input.copy(), rank)) }
        assertEquals(1, calls)
        cache.get(input.copy(trending = input.trending.reversed() + TestFixtures.podcast(id = "new")), rank)
        cache.get(input.copy(recommendations = listOf(TestFixtures.episode(id = "new-episode"))), rank)
        cache.get(input.copy(subscribedIds = setOf("subscribed")), rank)
        cache.get(input.copy(history = listOf(history())), rank)
        assertEquals(5, calls)
    }

    @Test
    fun `fresh payload for existing IDs replaces cached discovery content`() = runTest {
        val cache = HomeDiscoveryRankingCache()
        var calls = 0
        val rank: suspend (HomeDiscoveryRankingInput) -> HomeDiscoveryRankingResult = {
            calls++
            HomeDiscoveryRankingResult(it.trending, it.recommendations)
        }
        cache.get(input, rank)
        val fresh = input.copy(
            trending = input.trending.map { it.copy(title = "Updated trending show") },
            recommendations = input.recommendations.map { it.copy(title = "Updated recommended episode") },
        )
        val result = cache.get(fresh, rank)
        assertEquals(2, calls)
        assertEquals("Updated trending show", result.trending.single().title)
        assertEquals("Updated recommended episode", result.recommendations.single().title)
        assertSame(result, cache.get(fresh, rank))
    }

    @Test
    fun `empty fresh response clears cached discovery results`() = runTest {
        val cache = HomeDiscoveryRankingCache()
        val rank: suspend (HomeDiscoveryRankingInput) -> HomeDiscoveryRankingResult = {
            HomeDiscoveryRankingResult(it.trending, it.recommendations)
        }
        cache.get(input, rank)
        val result = cache.get(input.copy(trending = emptyList(), recommendations = emptyList()), rank)
        assertEquals(emptyList<Any>(), result.trending)
        assertEquals(emptyList<Any>(), result.recommendations)
    }

    @Test
    fun `failed ranking does not poison cache and a new region collector starts fresh`() = runTest {
        val cache = HomeDiscoveryRankingCache()
        var calls = 0
        val rank: suspend (HomeDiscoveryRankingInput) -> HomeDiscoveryRankingResult = {
            calls++
            if (calls == 1) error("cancelled or failed ranking")
            HomeDiscoveryRankingResult(it.trending, it.recommendations)
        }
        runCatching { cache.get(input, rank) }
        cache.get(input, rank)
        cache.get(input, rank)
        assertEquals(2, calls)
        HomeDiscoveryRankingCache().get(input, rank)
        assertEquals(3, calls)
    }

    private fun history() = ListeningHistoryEntity(
        episodeId = "history-episode",
        podcastId = "history-podcast",
        episodeTitle = "History",
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = null,
        podcastName = "History show",
        progressMs = 60_000,
        durationMs = 120_000,
        isCompleted = false,
        lastPlayedAt = 1,
    )
}
