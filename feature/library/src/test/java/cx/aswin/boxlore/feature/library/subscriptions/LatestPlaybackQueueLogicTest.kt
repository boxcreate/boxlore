package cx.aswin.boxlore.feature.library.subscriptions

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LatestPlaybackQueueLogicTest {
    private fun podcast(id: String, status: EpisodeStatus = EpisodeStatus.UNPLAYED) = Podcast(
        id = "show-$id",
        title = "Show $id",
        artist = "Host $id",
        imageUrl = "art-$id",
        genre = "News",
        latestEpisode = Episode(id, "Episode $id", "description", "https://example.com/$id.mp3"),
        episodeStatus = status,
    )

    @Test fun `row tap snapshots the visible chronological suffix`() {
        val visible = listOf(podcast("3"), podcast("2"), podcast("1"))
        assertEquals(listOf("2", "1"), latestPlaybackEpisodes(visible, "2").map { it.id })
    }

    @Test fun `smart order and filter order are preserved without sorting again`() {
        val visible = listOf(podcast("1"), podcast("3"), podcast("2"))
        assertEquals(listOf("3", "2"), latestPlaybackEpisodes(visible, "3").map { it.id })
        assertEquals(listOf("1", "3", "2"), latestPlaybackEpisodes(visible).map { it.id })
    }

    @Test fun `completed upcoming rows are skipped but explicitly selected replay is allowed`() {
        val visible = listOf(podcast("1", EpisodeStatus.COMPLETED), podcast("2"), podcast("3", EpisodeStatus.COMPLETED))
        assertEquals(listOf("2"), latestPlaybackEpisodes(visible).map { it.id })
        assertEquals(listOf("1", "2"), latestPlaybackEpisodes(visible, "1").map { it.id })
    }

    @Test fun `missing row does not fall back to another episode`() {
        assertTrue(latestPlaybackEpisodes(listOf(podcast("1")), "gone").isEmpty())
    }

    @Test fun `mixed show metadata and opaque RSS ids survive the snapshot`() {
        val visible = listOf(podcast("rss:opaque"), podcast("2"))
        val queue = latestPlaybackEpisodes(visible)
        assertEquals(listOf("rss:opaque", "2"), queue.map { it.id })
        assertEquals(listOf("show-rss:opaque", "show-2"), queue.map { it.podcastId })
        assertEquals(listOf("Show rss:opaque", "Show 2"), queue.map { it.podcastTitle })
        assertEquals(listOf("Host rss:opaque", "Host 2"), queue.map { it.podcastArtist })
        assertEquals(listOf("art-rss:opaque", "art-2"), queue.map { it.podcastImageUrl })
    }

    @Test fun `missing episodes and duplicates do not create invalid queue entries`() {
        val visible = listOf(podcast("1").copy(latestEpisode = null), podcast("2"), podcast("2"))
        assertEquals(listOf("2"), latestPlaybackEpisodes(visible).map { it.id })
    }
}
