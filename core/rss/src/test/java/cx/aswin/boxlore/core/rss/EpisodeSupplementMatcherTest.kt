package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeSupplementMatcherTest {
    @Test
    fun `findMatchingBaseline returns the PI episode`() {
        val rss =
            rssEpisode(
                episodeId = "-1",
                title = "Different title",
                audioUrl = "https://cdn.example/ep.mp3",
                publishedDate = 10L,
            )
        val baseline =
            listOf(
                TestFixtures.episode(
                    id = "pi-1",
                    title = "PI title",
                    audioUrl = "https://cdn.example/ep.mp3",
                ),
            )
        assertEquals("pi-1", EpisodeSupplementMatcher.findMatchingBaseline(rss, baseline)?.id)
    }

    @Test
    fun `audio URL match counts as present`() {
        val rss =
            rssEpisode(
                episodeId = "-1",
                title = "Different title",
                audioUrl = "https://cdn.example/ep.mp3",
                publishedDate = 10L,
            )
        val baseline =
            listOf(
                TestFixtures.episode(
                    id = "pi-1",
                    title = "PI title",
                    audioUrl = "https://cdn.example/ep.mp3",
                ),
            )
        assertTrue(EpisodeSupplementMatcher.isPresentInBaseline(rss, baseline))
    }

    @Test
    fun `unique title match counts as present`() {
        val rss =
            rssEpisode(
                episodeId = "-2",
                title = "Only One",
                audioUrl = "https://cdn.example/other.mp3",
                publishedDate = 0L,
            )
        val baseline =
            listOf(
                TestFixtures.episode(id = "pi-1", title = "Only One", audioUrl = "https://cdn.example/a.mp3"),
            )
        assertTrue(EpisodeSupplementMatcher.isPresentInBaseline(rss, baseline))
    }

    @Test
    fun `ambiguous title with publishedDate within one day matches`() {
        val day = 24L * 60L * 60L
        val rss =
            rssEpisode(
                episodeId = "-3",
                title = "Daily",
                audioUrl = "https://cdn.example/rss.mp3",
                publishedDate = 1_000L,
            )
        val baseline =
            listOf(
                TestFixtures.episode(
                    id = "pi-1",
                    title = "Daily",
                    audioUrl = "https://cdn.example/a.mp3",
                    publishedDate = 1_000L + day / 2,
                ),
                TestFixtures.episode(
                    id = "pi-2",
                    title = "Daily",
                    audioUrl = "https://cdn.example/b.mp3",
                    publishedDate = 1_000L + day * 10,
                ),
            )
        assertTrue(EpisodeSupplementMatcher.isPresentInBaseline(rss, baseline))
    }

    @Test
    fun `unrelated episode is not present`() {
        val rss =
            rssEpisode(
                episodeId = "-4",
                title = "Brand New",
                audioUrl = "https://cdn.example/new.mp3",
                publishedDate = 50L,
            )
        val baseline =
            listOf(
                TestFixtures.episode(
                    id = "pi-1",
                    title = "Other",
                    audioUrl = "https://cdn.example/old.mp3",
                    publishedDate = 10L,
                ),
            )
        assertFalse(EpisodeSupplementMatcher.isPresentInBaseline(rss, baseline))
    }

    @Test
    fun `empty normalized titles do not match`() {
        val rss =
            rssEpisode(
                episodeId = "-5",
                title = "!!!",
                audioUrl = "https://cdn.example/rss.mp3",
                publishedDate = 10L,
            )
        val baseline =
            listOf(
                TestFixtures.episode(
                    id = "pi-1",
                    title = "???",
                    audioUrl = "https://cdn.example/pi.mp3",
                    publishedDate = 10L,
                ),
            )
        assertFalse(EpisodeSupplementMatcher.isPresentInBaseline(rss, baseline))
    }

    @Test
    fun `unique title with distant dates is not a match`() {
        val day = 24L * 60L * 60L
        val rss =
            rssEpisode(
                episodeId = "-6",
                title = "Only One",
                audioUrl = "https://cdn.example/rss.mp3",
                publishedDate = 1_000L,
            )
        val baseline =
            listOf(
                TestFixtures.episode(
                    id = "pi-1",
                    title = "Only One",
                    audioUrl = "https://cdn.example/pi.mp3",
                    publishedDate = 1_000L + day * 10,
                ),
            )
        assertFalse(EpisodeSupplementMatcher.isPresentInBaseline(rss, baseline))
    }

    private fun rssEpisode(
        episodeId: String,
        title: String,
        audioUrl: String,
        publishedDate: Long,
    ): RssEpisodeEntity =
        RssEpisodeEntity(
            episodeId = episodeId,
            podcastId = "rss:show",
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
