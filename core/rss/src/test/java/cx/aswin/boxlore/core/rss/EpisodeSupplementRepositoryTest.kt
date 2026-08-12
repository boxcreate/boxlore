package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeSupplementDao
import cx.aswin.boxlore.core.database.EpisodeSupplementEntity
import cx.aswin.boxlore.core.database.EpisodeSupplementItemEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EpisodeSupplementRepositoryTest {
    @Test
    fun `requirePiPodcastId rejects blank and rss ids`() {
        assertThrows(IllegalArgumentException::class.java) {
            EpisodeSupplementRepository.requirePiPodcastId("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            EpisodeSupplementRepository.requirePiPodcastId("rss:show")
        }
        EpisodeSupplementRepository.requirePiPodcastId("123")
    }

    @Test
    fun `pickMatchingFeedEpisode uses newest when no match constraint`() {
        val newest = rssEpisode("-1", guid = "g-new", audioUrl = "https://cdn/new.mp3")
        val older = rssEpisode("-2", guid = "g-old", audioUrl = "https://cdn/old.mp3")
        assertEquals(
            newest,
            EpisodeSupplementRepository.pickMatchingFeedEpisode(
                episodes = listOf(newest, older),
                match = null,
            ),
        )
    }

    @Test
    fun `pickMatchingFeedEpisode matches guid or enclosure not newest`() {
        val newest = rssEpisode("-1", guid = "g-new", audioUrl = "https://cdn/new.mp3")
        val older = rssEpisode("-2", guid = "g-old", audioUrl = "https://cdn/old.mp3")
        assertEquals(
            older,
            EpisodeSupplementRepository.pickMatchingFeedEpisode(
                episodes = listOf(newest, older),
                match = EpisodeSupplementPort.FeedItemMatch(guid = "g-old"),
            ),
        )
        assertEquals(
            older,
            EpisodeSupplementRepository.pickMatchingFeedEpisode(
                episodes = listOf(newest, older),
                match = EpisodeSupplementPort.FeedItemMatch(
                    enclosureUrl = "https://cdn/old.mp3",
                ),
            ),
        )
        assertNull(
            EpisodeSupplementRepository.pickMatchingFeedEpisode(
                episodes = listOf(newest, older),
                match = EpisodeSupplementPort.FeedItemMatch(
                    guid = "missing",
                    enclosureUrl = "https://cdn/other.mp3",
                ),
            ),
        )
    }

    @Test
    fun `refreshFromFeed maps errors to a generic user message`() =
        runTest {
            val repo =
                EpisodeSupplementRepository(
                    dao = FakeDao(),
                    feedClient = RssFeedClient(),
                    runInTransaction = { it() },
                )
            val outcome =
                repo.refreshFromFeed(
                    podcastIndexId = "rss:nope",
                    feedUrl = "https://feeds.example/show.xml",
                    baselineEpisodes = emptyList(),
                )
            assertTrue(outcome is EpisodeSupplementOutcome.Failure)
            assertEquals(
                EpisodeSupplementRepository.FEED_LOAD_FAILED_MESSAGE,
                (outcome as EpisodeSupplementOutcome.Failure).message,
            )
        }

    @Test
    fun `opted-in helpers are empty without a supplement row`() =
        runTest {
            val repo =
                EpisodeSupplementRepository(
                    dao = FakeDao(),
                    feedClient = RssFeedClient(),
                    runInTransaction = { it() },
                )
            assertFalse(repo.hasDirectFeedOptIn("123"))
            assertTrue(repo.listOptedInPodcastIds().isEmpty())
            assertTrue(repo.getEpisodesForPodcast("123").isEmpty())
            assertTrue(repo.search("123", "  ").isEmpty())
            assertNull(
                repo.resolveNewestTipFromFeed(
                    EpisodeSupplementPort.NewestTipRequest(
                        podcastIndexId = "123",
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                ),
            )
        }

    private fun rssEpisode(
        episodeId: String,
        guid: String,
        audioUrl: String,
    ) = RssEpisodeEntity(
        episodeId = episodeId,
        podcastId = "rss:show",
        guid = guid,
        title = "T",
        description = "",
        audioUrl = audioUrl,
        imageUrl = null,
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

    private class FakeDao : EpisodeSupplementDao {
        var supplement: EpisodeSupplementEntity? = null
        val items = mutableListOf<EpisodeSupplementItemEntity>()

        override suspend fun upsertSupplement(entity: EpisodeSupplementEntity) {
            supplement = entity
        }

        override suspend fun upsertItems(items: List<EpisodeSupplementItemEntity>) {
            this.items.removeAll { existing -> items.any { it.episodeId == existing.episodeId } }
            this.items.addAll(items)
        }

        override suspend fun getSupplement(podcastId: String): EpisodeSupplementEntity? =
            supplement?.takeIf { it.podcastId == podcastId }

        override suspend fun listOptedInPodcastIds(): List<String> =
            listOfNotNull(supplement?.podcastId)

        override suspend fun getEpisode(episodeId: String): EpisodeSupplementItemEntity? =
            items.find { it.episodeId == episodeId }

        override suspend fun getAllNewest(podcastId: String): List<EpisodeSupplementItemEntity> =
            items.filter { it.podcastId == podcastId }.sortedByDescending { it.publishedDate }

        override suspend fun search(
            podcastId: String,
            query: String,
        ): List<EpisodeSupplementItemEntity> =
            getAllNewest(podcastId).filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
            }

        override suspend fun deleteItemsForPodcast(podcastId: String) {
            items.removeAll { it.podcastId == podcastId }
        }

        override suspend fun deleteSupplement(podcastId: String) {
            if (supplement?.podcastId == podcastId) supplement = null
        }
    }
}
