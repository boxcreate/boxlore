package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class EpisodeSupplementTipLogicTest {
    @Test
    fun `prefers matching PI baseline id for tip`() {
        val feed =
            rssEpisode(
                episodeId = "-99",
                title = "Fresh",
                audioUrl = "https://cdn.example/fresh.mp3",
                publishedDate = 200L,
            )
        val pi =
            TestFixtures.episode(
                id = "42",
                title = "Fresh",
                audioUrl = "https://cdn.example/fresh.mp3",
                publishedDate = 200L,
                podcastId = "pi-1",
            )
        val tip =
            EpisodeSupplementTipLogic.resolveNewestTip(
                newestFeedEpisode = feed,
                podcastIndexId = "pi-1",
                knownEpisodes = listOf(pi),
                podcastTitle = "Show",
                podcastImageUrl = "https://img",
                podcastGenre = "News",
                podcastArtist = "Host",
            )
        assertEquals("42", tip.id)
        assertEquals("pi-1", tip.podcastId)
        assertEquals("Show", tip.podcastTitle)
    }

    @Test
    fun `uses negative feed id when no baseline match`() {
        val feed =
            rssEpisode(
                episodeId = "-77",
                title = "Only on feed",
                audioUrl = "https://cdn.example/new.mp3",
                publishedDate = 300L,
            )
        val tip =
            EpisodeSupplementTipLogic.resolveNewestTip(
                newestFeedEpisode = feed,
                podcastIndexId = "pi-9",
                knownEpisodes = emptyList(),
                podcastTitle = "Show",
                podcastImageUrl = null,
                podcastGenre = null,
                podcastArtist = null,
            )
        assertEquals("-77", tip.id)
        assertEquals("pi-9", tip.podcastId)
        assertNull(EpisodeSupplementMatcher.findMatchingBaseline(feed, emptyList()))
    }

    @Test
    fun `unknown feed date still uses negative id when unmatched`() {
        val feed =
            rssEpisode(
                episodeId = "-5",
                title = "Undated",
                audioUrl = "https://cdn.example/u.mp3",
                publishedDate = 0L,
            )
        val tip =
            EpisodeSupplementTipLogic.resolveNewestTip(
                newestFeedEpisode = feed,
                podcastIndexId = "pi-2",
                knownEpisodes = listOf(
                    TestFixtures.episode(
                        id = "42",
                        title = "Other",
                        audioUrl = "https://cdn.example/other.mp3",
                        publishedDate = 9L,
                    ),
                ),
                podcastTitle = "Show",
                podcastImageUrl = null,
                podcastGenre = null,
                podcastArtist = null,
            )
        assertEquals("-5", tip.id)
        assertEquals("pi-2", tip.podcastId)
    }

    @Test
    fun `blank metadata does not overwrite matched PI fields`() {
        val feed =
            rssEpisode(
                episodeId = "-1",
                title = "Fresh",
                audioUrl = "https://cdn.example/fresh.mp3",
                publishedDate = 200L,
            )
        val pi =
            TestFixtures.episode(
                id = "42",
                title = "Fresh",
                audioUrl = "https://cdn.example/fresh.mp3",
                publishedDate = 200L,
                podcastId = "pi-1",
            ).copy(
                podcastTitle = "Kept title",
                podcastImageUrl = "https://kept",
                podcastGenre = "News",
                podcastArtist = "Host",
            )
        val tip =
            EpisodeSupplementTipLogic.resolveNewestTip(
                newestFeedEpisode = feed,
                podcastIndexId = "pi-1",
                knownEpisodes = listOf(pi),
                podcastTitle = "  ",
                podcastImageUrl = "",
                podcastGenre = "",
                podcastArtist = "",
            )
        assertEquals("Kept title", tip.podcastTitle)
        assertEquals("https://kept", tip.podcastImageUrl)
        assertEquals("News", tip.podcastGenre)
        assertEquals("Host", tip.podcastArtist)
    }

    private fun rssEpisode(
        episodeId: String,
        title: String,
        audioUrl: String,
        publishedDate: Long,
    ): RssEpisodeEntity =
        RssEpisodeEntity(
            episodeId = episodeId,
            podcastId = "rss:unused",
            guid = null,
            title = title,
            description = "",
            audioUrl = audioUrl,
            imageUrl = null,
            duration = 60,
            publishedDate = publishedDate,
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
