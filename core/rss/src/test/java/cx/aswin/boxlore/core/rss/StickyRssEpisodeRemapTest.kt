package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeIdentity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class StickyRssEpisodeRemapTest {
    @Test
    fun refreshKeepsStoredIdWhenGuidMatches() {
        val namespace = RssIdGenerator.podcastId("https://example.com/feed.xml")
        val minted =
            RssIdGenerator.episodeIdForPodcast(
                podcastId = namespace,
                guid = "g1",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 10L,
                title = "New title",
            )
        val parsed =
            listOf(
                rss(namespace, minted, "g1", "New title"),
            )
        val remapped =
            StickyRssEpisodeRemap.remap(
                parsed = parsed,
                existing =
                listOf(
                    RssEpisodeIdentity(
                        episodeId = "-12345",
                        guid = "g1",
                        audioUrl = "https://cdn.example/a.mp3",
                    ),
                ),
            )
        assertEquals(listOf("-12345"), remapped.map { it.episodeId })
    }

    @Test
    fun unmatchedGuidKeepsParsedEpisodeId() {
        val namespace = RssIdGenerator.podcastId("https://example.com/feed.xml")
        val minted =
            RssIdGenerator.episodeIdForPodcast(
                podcastId = namespace,
                guid = "g2",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 10L,
                title = "New",
            )
        val remapped =
            StickyRssEpisodeRemap.remap(
                parsed = listOf(rss(namespace, minted, "g2", "New")),
                existing =
                listOf(
                    RssEpisodeIdentity(
                        episodeId = "-12345",
                        guid = "g1",
                        audioUrl = "https://cdn.example/a.mp3",
                    ),
                ),
            )
        assertEquals(listOf(minted), remapped.map { it.episodeId })
    }

    @Test
    fun duplicateGuidKeepsFirstRow() {
        val namespace = RssIdGenerator.podcastId("https://example.com/feed.xml")
        val first =
            RssIdGenerator.episodeIdForPodcast(
                podcastId = namespace,
                guid = "dup",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 10L,
                title = "First",
            )
        val second =
            RssIdGenerator.episodeIdForPodcast(
                podcastId = namespace,
                guid = "dup",
                enclosureUrl = "https://cdn.example/b.mp3",
                publishedDate = 11L,
                title = "Second",
            )
        val remapped =
            StickyRssEpisodeRemap.remap(
                parsed =
                listOf(
                    rss(namespace, first, "dup", "First"),
                    rss(namespace, second, "dup", "Second").copy(audioUrl = "https://cdn.example/b.mp3"),
                ),
                existing = emptyList(),
            )
        assertEquals(1, remapped.size)
        assertEquals("First", remapped.single().title)
    }

    @Test
    fun prepareUsesStickyIdForLatestEpisode() {
        val namespace = RssIdGenerator.podcastId("https://example.com/feed.xml")
        val minted =
            RssIdGenerator.episodeIdForPodcast(
                podcastId = namespace,
                guid = "g1",
                enclosureUrl = "https://cdn.example/a.mp3",
                publishedDate = 10L,
                title = "New title",
            )
        val prepared =
            StickyRssEpisodeRemap.prepare(
                parsed = listOf(rss(namespace, minted, "g1", "New title")),
                existing =
                listOf(
                    RssEpisodeIdentity(
                        episodeId = "-12345",
                        guid = "g1",
                        audioUrl = "https://cdn.example/a.mp3",
                    ),
                ),
                podcastTitle = "Show",
            )
        assertEquals(listOf("-12345"), prepared.episodes.map { it.episodeId })
        assertEquals("-12345", prepared.latestEpisode?.id)
        assertEquals("New title", prepared.latestEpisode?.title)
        assertEquals("Show", prepared.latestEpisode?.podcastTitle)
    }

    private fun rss(podcastId: String, episodeId: String, guid: String, title: String,) = RssEpisodeEntity(
        episodeId = episodeId,
        podcastId = podcastId,
        guid = guid,
        title = title,
        description = "",
        audioUrl = "https://cdn.example/a.mp3",
        imageUrl = null,
        duration = 1,
        publishedDate = 10L,
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
