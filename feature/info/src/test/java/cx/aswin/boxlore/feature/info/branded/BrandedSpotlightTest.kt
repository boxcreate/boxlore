package cx.aswin.boxlore.feature.info.branded

import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.logic.FeedItem
import cx.aswin.boxlore.feature.info.logic.PodcastEpisodeSelectionLogic
import cx.aswin.boxlore.feature.info.logic.groupEpisodes
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class BrandedSpotlightTest {
    private val latest = TestFixtures.episode(id = "latest", publishedDate = 300)
    private val older = TestFixtures.episode(id = "older", publishedDate = 200)
    private val trailer = TestFixtures.episode(id = "trailer", publishedDate = 400, duration = 120).copy(episodeType = "trailer")

    @Test
    fun `newest first shows the latest episode`() {
        assertEquals(latest, brandedSpotlightEpisode(listOf(latest, older), EpisodeSort.NEWEST, searching = false))
    }

    @Test
    fun `sorting oldest first hides the spotlight`() {
        assertNull(brandedSpotlightEpisode(listOf(older, latest), EpisodeSort.OLDEST, searching = false))
    }

    @Test
    fun `searching hides the spotlight`() {
        assertNull(brandedSpotlightEpisode(listOf(latest, older), EpisodeSort.NEWEST, searching = true))
    }

    @Test
    fun `a newer trailer stays in the list and the latest full episode leads`() {
        assertEquals(latest, brandedSpotlightEpisode(listOf(trailer, latest, older), EpisodeSort.NEWEST, searching = false))
    }

    @Test
    fun `a full-length episode the feed tags as a trailer can lead`() {
        val mislabelled = TestFixtures.episode(id = "mislabelled", publishedDate = 400, duration = 3264).copy(episodeType = "trailer")
        assertEquals(mislabelled, brandedSpotlightEpisode(listOf(mislabelled, latest), EpisodeSort.NEWEST, searching = false))
    }

    @Test
    fun `no episodes means no spotlight`() {
        assertNull(brandedSpotlightEpisode(emptyList(), EpisodeSort.NEWEST, searching = false))
        assertNull(brandedSpotlightEpisode(listOf(trailer), EpisodeSort.NEWEST, searching = false))
    }

    @Test
    fun `the spotlight episode is not repeated in the list`() {
        val items = groupEpisodes(listOf(trailer, latest, older))
        val list = feedItemsWithoutSpotlight(items, latest)
        assertEquals(listOf("trailer", "older"), list.map { it.id })
        // Positions in the full list are kept for the rest.
        assertEquals(2, (list.last() as FeedItem.NormalEpisode).globalIndex)
    }

    @Test
    fun `Select visible counts the spotlight episode while it is on screen`() {
        // Regression: the spotlight's fixed list key matched no episode, so Select visible left it out.
        val items = groupEpisodes(listOf(trailer, latest, older))
        val visibleKeys = setOf(brandedSpotlightKey(latest), "older")
        val visible = PodcastEpisodeSelectionLogic.visibleEpisodes(items, visibleKeys)
        assertEquals(listOf("latest", "older"), visible.map { it.id })
        // The list below never shows the spotlight episode, so its key is never used twice.
        assertFalse(feedItemsWithoutSpotlight(items, latest).any { it.id == brandedSpotlightKey(latest) })
    }

    @Test
    fun `without a spotlight the list is unchanged`() {
        val items = groupEpisodes(listOf(latest, older))
        assertEquals(items, feedItemsWithoutSpotlight(items, null))
    }
}
