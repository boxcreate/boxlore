package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.explore.ExploreUiState
import cx.aswin.boxlore.feature.explore.LearnCuriosityCard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

/** Explore and Lore without hidden shows; Find a show keeps them so they can be unhidden. */
class ExploreHiddenShowsLogicTest {
    private val hidden = setOf("2")
    private val shows = listOf(TestFixtures.podcast(id = "1"), TestFixtures.podcast(id = "2"))
    private val episodes = listOf(TestFixtures.episode(id = "a", podcastId = "1"), TestFixtures.episode(id = "b", podcastId = "2"))

    @Test
    fun `For you, the chart, Ask anything and a mood topic leave hidden shows out`() {
        val state =
            ExploreUiState.Success(
                trending = shows,
                recommendations = episodes,
                searchResults = shows,
                currentVibe = "Deep dives",
                semanticSearchResults = episodes,
                semanticPodcastResults = shows,
            )

        val filtered = ExploreHiddenShowsLogic.withoutHiddenShows(state, hidden)

        assertEquals(listOf("1"), filtered.trending.map { it.id })
        assertEquals(listOf("a"), filtered.recommendations.map { it.id })
        assertEquals(listOf("1"), filtered.searchResults.map { it.id })
        assertEquals(listOf("a"), filtered.semanticSearchResults.map { it.id })
        assertEquals(listOf("1"), filtered.semanticPodcastResults.map { it.id })
    }

    @Test
    fun `Find a show keeps a hidden show, and nothing hidden changes nothing`() {
        val search = ExploreUiState.Success(searchResults = shows, alsoFoundResults = shows, searchQuery = "show")
        val filtered = ExploreHiddenShowsLogic.withoutHiddenShows(search, hidden)
        assertEquals(listOf("1", "2"), filtered.searchResults.map { it.id })
        assertEquals(listOf("1", "2"), filtered.alsoFoundResults.map { it.id })
        assertSame(search, ExploreHiddenShowsLogic.withoutHiddenShows(search, emptySet()))
    }

    @Test
    fun `hiding a show takes its Lore cards out and loads more or ends the deck`() {
        val cards = listOf(card("e1", "1"), card("e2", "2"), card("e3", "1"), card("e4", "1"))
        assertEquals(LoreHiddenShows.Change.None, LoreHiddenShows.change(cards, setOf("9"), endOfContent = false))
        assertEquals(
            LoreHiddenShows.Change.Deck(listOf(card("e1", "1"), card("e3", "1"), card("e4", "1")), loadMore = false),
            LoreHiddenShows.change(cards, hidden, endOfContent = false),
        )
        assertEquals(LoreHiddenShows.Change.Deck(listOf(card("e1", "1")), loadMore = true), LoreHiddenShows.change(cards.take(2), hidden, endOfContent = false))
        assertEquals(LoreHiddenShows.Change.Deck(emptyList(), loadMore = true), LoreHiddenShows.change(listOf(card("e2", "2")), hidden, endOfContent = false))
        assertEquals(LoreHiddenShows.Change.CaughtUp, LoreHiddenShows.change(listOf(card("e2", "2")), hidden, endOfContent = true))
        // A card without a show is never taken for a hidden one.
        assertEquals(LoreHiddenShows.Change.None, LoreHiddenShows.change(listOf(card("e5", null)), hidden, endOfContent = true))
    }

    private fun card(episodeId: String, podcastId: String?) = LearnCuriosityCard(
        episodeId = episodeId,
        question = "Why?",
        explanation = null,
        curiosityScore = 5,
        episodeTitle = "Episode",
        podcastTitle = "Show",
        imageUrl = null,
        feedImage = null,
        podcastId = podcastId,
        audioUrl = null,
        duration = 0,
        description = null,
    )
}
