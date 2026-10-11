package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.network.model.EpisodeItem
import cx.aswin.boxlore.core.network.model.TrendingFeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ArtworkMappingTest {
    @Test
    fun `both episode mapper paths retain show art when episode art is blank`() {
        for (blank in listOf(null, "", "  ")) {
            val item = EpisodeItem(1, "Episode", enclosureUrl = "https://audio/1.mp3", image = blank, feedImage = " http://art/show.jpg ")
            assertEquals("https://art/show.jpg", item.toEpisode().imageUrl)
            assertEquals("https://art/show.jpg", item.toEpisode().podcastImageUrl)
            assertEquals("https://art/show.jpg", mapToEpisode(item)?.imageUrl)
            assertEquals("https://art/show.jpg", mapToEpisode(item)?.podcastImageUrl)
        }
    }

    @Test
    fun `feed artwork blanks cannot mask the alternate image across feed mappers`() {
        val feed = TrendingFeed(2, "Show", artwork = "  ", image = " http://art/show.jpg ")
        assertEquals("https://art/show.jpg", mapFeedsToPodcasts(listOf(feed)).single().imageUrl)
        assertEquals("https://art/show.jpg", mapToPodcast(feed).imageUrl)
    }

    @Test
    fun `episode specific artwork and signed parameters survive mapping`() {
        val item = EpisodeItem(3, "Episode", enclosureUrl = "https://audio/1.mp3", image = "https://art/episode.jpg?token=a%2Bb", feedImage = "https://art/show.jpg")
        assertEquals("https://art/episode.jpg?token=a%2Bb", item.toEpisode().imageUrl)
        assertEquals("https://art/show.jpg", item.toEpisode().podcastImageUrl)
    }
}
