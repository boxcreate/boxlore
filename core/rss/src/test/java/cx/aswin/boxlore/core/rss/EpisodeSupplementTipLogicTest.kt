package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
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

    @Test
    fun `a match-only baseline row keeps its id and takes the rest from the feed`() {
        // /episodes/match rows carry only the ID, title, audio and date.
        val feed =
            rssEpisode(episodeId = "-99", title = "Fresh", audioUrl = "https://cdn.example/fresh.mp3", publishedDate = 200L)
                .copy(description = "From the feed", imageUrl = "https://img/ep.jpg", chaptersUrl = "https://c/ch.json", episodeNumber = 12)
        val lean = Episode(id = "42", title = "Fresh", description = "", audioUrl = "https://cdn.example/fresh.mp3", podcastId = "pi-1", publishedDate = 200L)

        val tip = EpisodeSupplementTipLogic.resolveNewestTip(feed, "pi-1", listOf(lean), "Show", "https://img", "News", "Host")

        assertEquals("42", tip.id)
        assertEquals("From the feed", tip.description)
        assertEquals("https://img/ep.jpg", tip.imageUrl)
        assertEquals(60, tip.duration)
        assertEquals("https://c/ch.json", tip.chaptersUrl)
        assertEquals(12, tip.episodeNumber)
        assertEquals("Show", tip.podcastTitle)
    }

    @Test
    fun `a full baseline row keeps its own fields`() {
        val feed =
            rssEpisode(episodeId = "-99", title = "Fresh", audioUrl = "https://cdn.example/fresh.mp3", publishedDate = 200L)
                .copy(description = "From the feed", imageUrl = "https://img/feed.jpg")
        val full =
            Episode(
                id = "42",
                title = "Fresh",
                description = "From Podcast Index",
                audioUrl = "https://cdn.example/fresh.mp3",
                imageUrl = "https://img/pi.jpg",
                duration = 1800,
                podcastId = "pi-1",
                publishedDate = 200L,
            )

        val tip = EpisodeSupplementTipLogic.resolveNewestTip(feed, "pi-1", listOf(full), null, null, null, null)

        assertEquals(full.copy(podcastId = "pi-1"), tip)
    }

    private fun rssEpisode(episodeId: String, title: String, audioUrl: String, publishedDate: Long,): RssEpisodeEntity = RssEpisodeEntity(
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
