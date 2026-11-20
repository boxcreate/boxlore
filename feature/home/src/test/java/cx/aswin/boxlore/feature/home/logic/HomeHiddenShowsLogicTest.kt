package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.home.HomeEditorialIcon
import cx.aswin.boxlore.feature.home.HomeEditorialRow
import cx.aswin.boxlore.feature.home.HomeUiState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Home without hidden shows: curated rows, the kept Daily Mix, Because you like and the category list. */
class HomeHiddenShowsLogicTest {
    private val kept = TestFixtures.podcast(id = "1", title = "Kept")
    private val hidden = TestFixtures.podcast(id = "2", title = "Hidden")
    private val hiddenIds = setOf("2")

    @Test
    fun `curated rows keep their order without hidden shows`() {
        val rows = listOf(HomeEditorialRow("morning", "Good Morning", "", HomeEditorialIcon.HEADLINES, listOf(hidden, kept)))
        assertEquals(listOf("1"), HomeHiddenShowsLogic.editorialRows(rows, hiddenIds).single().podcasts.map { it.id })
        assertSame(rows, HomeHiddenShowsLogic.editorialRows(rows, emptySet()))
    }

    @Test
    fun `the kept Daily Mix loses a hidden show's card and episode and recounts what is unplayed`() {
        val cache =
            HomeMixtapeCache(
                podcasts = listOf(kept, hidden, TestFixtures.podcast(id = "3").copy(episodeStatus = EpisodeStatus.COMPLETED)),
                unplayedCount = 2,
                episodes = listOf(TestFixtures.episode(id = "a", podcastId = "1"), TestFixtures.episode(id = "b", podcastId = "2")),
                subSignature = setOf("1", "2", "3"),
            )

        val mix = HomeHiddenShowsLogic.mixtape(cache, hiddenIds)!!

        assertEquals(listOf("1", "3"), mix.podcasts.map { it.id })
        assertEquals(listOf("a"), mix.episodes.map { it.id })
        assertEquals(1, mix.unplayedCount)
        assertEquals(cache.subSignature, mix.subSignature)
        // Nothing to drop keeps the same cache, and no cache stays none so Home rebuilds as before.
        assertSame(cache, HomeHiddenShowsLogic.mixtape(cache, setOf("9")))
        assertNull(HomeHiddenShowsLogic.mixtape(null, hiddenIds))
    }

    @Test
    fun `a hidden Because you like show hides the whole row and a hidden pick leaves it`() {
        val state =
            HomeUiState(
                heroItems = emptyList(),
                discoverPodcasts = listOf(hidden, kept),
                seemsToLikePodcast = kept,
                becauseYouLikePodcasts = listOf(hidden, kept),
                becauseYouLikeRecommendations = listOf(TestFixtures.episode(id = "b", podcastId = "2"), TestFixtures.episode(id = "a", podcastId = "1")),
            )

        val filtered = HomeHiddenShowsLogic.discovery(state, hiddenIds)
        assertEquals(listOf("1"), filtered.discoverPodcasts.map { it.id })
        assertEquals(listOf("1"), filtered.becauseYouLikePodcasts.map { it.id })
        assertEquals(listOf("a"), filtered.becauseYouLikeRecommendations.map { it.id })

        val seedHidden = HomeHiddenShowsLogic.discovery(state.copy(seemsToLikePodcast = hidden), hiddenIds)
        assertNull(seedHidden.seemsToLikePodcast)
        assertTrue(seedHidden.becauseYouLikePodcasts.isEmpty())
        assertTrue(seedHidden.becauseYouLikeRecommendations.isEmpty())
        assertSame(state, HomeHiddenShowsLogic.discovery(state, emptySet()))
    }
}
