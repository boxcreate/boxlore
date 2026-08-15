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
                existing = listOf(
                    RssEpisodeIdentity(
                        episodeId = "-12345",
                        guid = "g1",
                        audioUrl = "https://cdn.example/a.mp3",
                    ),
                ),
            )
        assertEquals(listOf("-12345"), remapped.map { it.episodeId })
    }

    private fun rss(
        podcastId: String,
        episodeId: String,
        guid: String,
        title: String,
    ) = RssEpisodeEntity(
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
