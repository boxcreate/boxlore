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
    fun `a finished latest episode is hidden with completed episodes, not replaced`() {
        // With completed episodes hidden, the list drops a finished latest episode; the card used to
        // show it anyway, next to the caught-up card.
        assertNull(brandedSpotlightEpisode(listOf(latest, older), EpisodeSort.NEWEST, searching = false, hiddenIds = setOf("latest")))
        assertEquals(latest, brandedSpotlightEpisode(listOf(latest, older), EpisodeSort.NEWEST, searching = false, hiddenIds = setOf("older")))
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

    @Test
    fun `an episode with its own picture keeps it`() {
        val episode = latest.copy(imageUrl = "https://art/episode-2572.jpg", podcastImageUrl = "https://art/show.jpg")
        assertEquals("https://art/episode-2572.jpg", episodeOwnPicture(episode, listOf("https://art/show.jpg")))
    }

    @Test
    fun `the page's fallback image is not the show's cover`() {
        // The page fills a show's fallback image from its latest episode's own picture (JRE #2574);
        // treating it as the cover hid the very picture it was taken from.
        val episode = latest.copy(imageUrl = "https://art/episode-2574.jpg", podcastImageUrl = "https://art/show.jpg")
        val podcast = TestFixtures.podcast(id = "550168", imageUrl = "https://art/show.jpg").copy(fallbackImageUrl = "https://art/episode-2574.jpg")
        assertEquals("https://art/episode-2574.jpg", brandedEpisodePicture(episode, podcast))
        assertNull(brandedEpisodePicture(episode.copy(imageUrl = "https://art/show.jpg"), podcast))
    }

    @Test
    fun `the show's cover filled in for a missing episode picture counts as none`() {
        // The catalogue fills a missing episode picture with the feed's cover, like JRE #2573's.
        val filled = latest.copy(imageUrl = "https://art/show.jpg?w=3000", podcastImageUrl = "https://art/show.jpg?w=3000")
        assertNull(episodeOwnPicture(filled, emptyList()))
        // The same file under another scheme or size, from the page's own show.
        val fromShow = latest.copy(imageUrl = "http://art/show.jpg?w=600", podcastImageUrl = null)
        assertNull(episodeOwnPicture(fromShow, listOf(null, "", "https://art/show.jpg")))
        assertNull(episodeOwnPicture(latest.copy(imageUrl = " "), listOf("https://art/show.jpg")))
        assertNull(episodeOwnPicture(latest.copy(imageUrl = null), emptyList()))
    }

    @Test
    fun `a picture named in its query is not mistaken for the cover`() {
        // Some hosts serve every picture from one path and name the file in the query.
        val cover = "https://img.host/art?id=show&w=600"
        assertEquals("https://img.host/art?id=ep-12", episodeOwnPicture(latest.copy(imageUrl = "https://img.host/art?id=ep-12"), listOf(cover)))
        // The cover at another size, with its parameters in another order, is still the cover.
        assertNull(episodeOwnPicture(latest.copy(imageUrl = "https://img.host/art?w=3000&id=show&fm=jpg"), listOf(cover)))
    }

    @Test
    fun `a card falls back on the show's cover, or its fallback image without one`() {
        assertEquals("https://art/show.jpg", brandedShowPicture(TestFixtures.podcast(id = "1", imageUrl = "https://art/show.jpg")))
        val noCover = TestFixtures.podcast(id = "1", imageUrl = "").copy(fallbackImageUrl = "https://art/episode.jpg")
        assertEquals("https://art/episode.jpg", brandedShowPicture(noCover))
        assertNull(brandedShowPicture(noCover.copy(fallbackImageUrl = " ")))
    }
}
