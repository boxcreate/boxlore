package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalEpisodeCatalogPersistTest {
    private val namespace = RssIdGenerator.podcastId("https://example.com/feed.xml")

    @Test
    fun refreshReusesExistingEpisodeId() {
        val parsed = listOf(rssEpisode(guid = "g1", title = "Renamed"))
        val existing = listOf(LocalEpisodeIdentity(episodeId = "-9", guid = "g1", audioUrl = "https://cdn.example/a.mp3"))
        val rows =
            LocalEpisodeCatalogPersist.toLocalEpisodes(
                podcastIndexId = "100",
                rssNamespaceId = namespace,
                parsed = parsed,
                existing = existing,
                piBaseline = listOf(TestFixtures.episode(id = "55", title = "Renamed")),
                channelImageUrl = null,
                showImageUrl = null,
            )
        assertEquals(listOf("-9"), rows.map { it.episodeId })
    }

    @Test
    fun firstInsertUsesPiIdWhenMatched() {
        val parsed = listOf(rssEpisode(guid = "g1", title = "Same", audioUrl = "https://cdn.example/match.mp3"))
        val baseline =
            listOf(
                TestFixtures.episode(
                    id = "77",
                    title = "Same",
                    audioUrl = "https://cdn.example/match.mp3",
                ),
            )
        val rows =
            LocalEpisodeCatalogPersist.toLocalEpisodes(
                podcastIndexId = "100",
                rssNamespaceId = namespace,
                parsed = parsed,
                existing = emptyList(),
                piBaseline = baseline,
                channelImageUrl = null,
                showImageUrl = null,
            )
        assertEquals(listOf("77"), rows.map { it.episodeId })
    }

    @Test
    fun duplicateGuidKeepsFirstRow() {
        val parsed =
            listOf(
                rssEpisode(guid = "dup", title = "First"),
                rssEpisode(guid = "dup", title = "Second", audioUrl = "https://cdn.example/b.mp3"),
            )
        val rows =
            LocalEpisodeCatalogPersist.toLocalEpisodes(
                podcastIndexId = "100",
                rssNamespaceId = namespace,
                parsed = parsed,
                existing = emptyList(),
                piBaseline = null,
                channelImageUrl = null,
                showImageUrl = null,
            )
        assertEquals(1, rows.size)
        assertEquals("First", rows.single().title)
    }

    @Test
    fun skipsItemWithNoGuidOrEnclosure() {
        val parsed = listOf(rssEpisode(guid = null, audioUrl = "  ", title = "Nope"))
        val rows =
            LocalEpisodeCatalogPersist.toLocalEpisodes(
                podcastIndexId = "100",
                rssNamespaceId = namespace,
                parsed = parsed,
                existing = emptyList(),
                piBaseline = null,
                channelImageUrl = null,
                showImageUrl = null,
            )
        assertTrue(rows.isEmpty())
    }

    private fun rssEpisode(
        guid: String? = "g1",
        title: String = "Ep",
        audioUrl: String = "https://cdn.example/a.mp3",
    ): RssEpisodeEntity {
        val id =
            if (StickyEpisodeIdentity.catalogKey(guid, audioUrl) == null) {
                "-1"
            } else {
                RssIdGenerator.episodeIdForPodcast(
                    podcastId = namespace,
                    guid = guid,
                    enclosureUrl = audioUrl,
                    publishedDate = 10L,
                    title = title,
                )
            }
        return RssEpisodeEntity(
            episodeId = id,
            podcastId = namespace,
            guid = guid,
            title = title,
            description = "",
            audioUrl = audioUrl,
            imageUrl = null,
            duration = 60,
            publishedDate = 10L,
            chaptersUrl = null,
            transcriptUrl = null,
            transcripts = null,
            persons = null,
            seasonNumber = 1,
            episodeNumber = 2,
            episodeType = "full",
            enclosureType = "audio/mpeg",
        )
    }
}
