package cx.aswin.boxlore.core.database

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeToDomainArtworkTest {
    @Test
    fun `supplement toEpisode treats blank item art as missing`() {
        val episode =
            supplementItem(imageUrl = "").toEpisode(
                podcastTitle = "Show",
                podcastImageUrl = "https://cdn/show.jpg",
            )
        assertEquals("https://cdn/show.jpg", episode.imageUrl)
        assertEquals("https://cdn/show.jpg", episode.podcastImageUrl)
    }

    @Test
    fun `rss toEpisode treats blank item art as missing`() {
        val episode =
            rssItem(imageUrl = "  ").toEpisode(podcastImageUrl = "https://cdn/show.jpg")
        assertEquals("https://cdn/show.jpg", episode.imageUrl)
        assertNull(rssItem(imageUrl = null).toEpisode().imageUrl)
    }

    private fun supplementItem(imageUrl: String?) =
        EpisodeSupplementItemEntity(
            episodeId = "-1",
            podcastId = "123",
            guid = "g",
            title = "T",
            description = "",
            audioUrl = "https://cdn/a.mp3",
            imageUrl = imageUrl,
            duration = 60,
            publishedDate = 1L,
            chaptersUrl = null,
            transcriptUrl = null,
            transcripts = null,
            persons = null,
            seasonNumber = null,
            episodeNumber = null,
            episodeType = null,
            enclosureType = null,
        )

    private fun rssItem(imageUrl: String?) =
        RssEpisodeEntity(
            episodeId = "-1",
            podcastId = "rss:show",
            guid = "g",
            title = "T",
            description = "",
            audioUrl = "https://cdn/a.mp3",
            imageUrl = imageUrl,
            duration = 60,
            publishedDate = 1L,
            chaptersUrl = null,
            transcriptUrl = null,
            transcripts = null,
            persons = null,
            seasonNumber = null,
            episodeNumber = null,
            episodeType = null,
            enclosureType = null,
        )
}
