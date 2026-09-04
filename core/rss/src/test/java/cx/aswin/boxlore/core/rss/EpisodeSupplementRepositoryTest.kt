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
    fun `resolveHttpsFeedUrl prefers request https then stored https`() {
        assertEquals(
            "https://feeds.example/live.xml",
            EpisodeSupplementRepository.resolveHttpsFeedUrl(
                "https://feeds.example/live.xml",
                "https://feeds.example/stored.xml",
            ),
        )
        assertEquals(
            "https://feeds.example/stored.xml",
            EpisodeSupplementRepository.resolveHttpsFeedUrl(
                "http://feeds.example/insecure.xml",
                "https://feeds.example/stored.xml",
            ),
        )
        assertEquals(
            "https://feeds.example/stored.xml",
            EpisodeSupplementRepository.resolveHttpsFeedUrl(
                "",
                "https://feeds.example/stored.xml",
            ),
        )
        assertNull(
            EpisodeSupplementRepository.resolveHttpsFeedUrl(
                "http://feeds.example/insecure.xml",
                "http://feeds.example/also-insecure.xml",
            ),
        )
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
    fun `refreshFromFeed maps errors to a generic user message`() = runTest {
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
    fun `opted-in helpers are empty without a supplement row`() = runTest {
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

    @Test
    fun `restoreDirectFeedOptIn writes https stub`() = runTest {
        val dao = FakeDao()
        val repo =
            EpisodeSupplementRepository(
                dao = dao,
                feedClient = RssFeedClient(),
                runInTransaction = { it() },
            )
        repo.restoreDirectFeedOptIn("123", "https://feeds.example/show.xml")
        assertTrue(repo.hasDirectFeedOptIn("123"))
        val listed = repo.listDirectFeedOptIns()
        assertEquals(1, listed.size)
        assertEquals("123", listed.single().podcastIndexId)
        assertEquals("https://feeds.example/show.xml", listed.single().feedUrl)
        assertEquals(0L, dao.supplement!!.fetchedAt)
        assertNull(dao.supplement!!.feedEtag)
    }

    @Test
    fun `restoreDirectFeedOptIn ignores http and rss ids`() = runTest {
        val repo =
            EpisodeSupplementRepository(
                dao = FakeDao(),
                feedClient = RssFeedClient(),
                runInTransaction = { it() },
            )
        repo.restoreDirectFeedOptIn("123", "http://feeds.example/show.xml")
        repo.restoreDirectFeedOptIn("rss:show", "https://feeds.example/show.xml")
        assertTrue(repo.listDirectFeedOptIns().isEmpty())
        assertFalse(repo.hasDirectFeedOptIn("123"))
    }

    @Test
    fun `restoreDirectFeedOptIn does not replace existing extras`() = runTest {
        val dao = FakeDao()
        dao.supplement =
            EpisodeSupplementEntity(
                podcastId = "123",
                feedUrl = "https://feeds.example/show.xml",
                rssNamespaceId = "rss:show",
                feedEtag = "etag",
                feedLastModified = null,
                fetchedAt = 9L,
            )
        dao.items.add(
            EpisodeSupplementItemEntity(
                episodeId = "-1",
                podcastId = "123",
                guid = "g",
                title = "Extra",
                description = "",
                audioUrl = "https://cdn.example/a.mp3",
                imageUrl = null,
                duration = 1,
                publishedDate = 1L,
                chaptersUrl = null,
                transcriptUrl = null,
                transcripts = null,
                persons = null,
                seasonNumber = null,
                episodeNumber = null,
                episodeType = null,
                enclosureType = null,
            ),
        )
        val repo =
            EpisodeSupplementRepository(
                dao = dao,
                feedClient = RssFeedClient(),
                runInTransaction = { it() },
            )
        repo.restoreDirectFeedOptIn("123", "https://feeds.example/other.xml")
        assertEquals("https://feeds.example/show.xml", dao.supplement!!.feedUrl)
        assertEquals("etag", dao.supplement!!.feedEtag)
        assertEquals(1, dao.items.size)
    }

    @Test
    fun `refreshFromFeed without HTTPS url is a generic failure`() = runTest {
        val repo =
            EpisodeSupplementRepository(
                dao = FakeDao(),
                feedClient = RssFeedClient(),
                runInTransaction = { it() },
            )
        val outcome =
            repo.refreshFromFeed(
                podcastIndexId = "123",
                feedUrl = "",
                baselineEpisodes = emptyList(),
            )
        assertTrue(outcome is EpisodeSupplementOutcome.Failure)
        assertEquals(
            EpisodeSupplementRepository.FEED_LOAD_FAILED_MESSAGE,
            (outcome as EpisodeSupplementOutcome.Failure).message,
        )
    }

    @Test
    fun `isPublisherFeedUnchanged is false without stored validators`() = runTest {
        val dao = FakeDao()
        dao.supplement =
            EpisodeSupplementEntity(
                podcastId = "123",
                feedUrl = "https://feeds.example/show.xml",
                rssNamespaceId = "rss:show",
                feedEtag = null,
                feedLastModified = null,
                fetchedAt = 1L,
            )
        val repo =
            EpisodeSupplementRepository(
                dao = dao,
                feedClient = RssFeedClient(),
                runInTransaction = { it() },
                isFeedUnchanged = { _, _, _ -> error("should not HEAD") },
            )
        assertFalse(repo.isPublisherFeedUnchanged("123", "https://feeds.example/show.xml"))
    }

    @Test
    fun `isPublisherFeedUnchanged is true when HEAD matches`() = runTest {
        val dao = FakeDao()
        dao.supplement =
            EpisodeSupplementEntity(
                podcastId = "123",
                feedUrl = "https://feeds.example/show.xml",
                rssNamespaceId = "rss:show",
                feedEtag = "etag-1",
                feedLastModified = null,
                fetchedAt = 1L,
            )
        val repo =
            EpisodeSupplementRepository(
                dao = dao,
                feedClient = RssFeedClient(),
                runInTransaction = { it() },
                isFeedUnchanged = { _, _, _ -> true },
            )
        assertTrue(repo.isPublisherFeedUnchanged("123", "https://feeds.example/show.xml"))
    }

    @Test
    fun `refreshFromFeed keeps existing rows when loadBaseline throws`() = runTest {
        val dao = FakeDao()
        dao.supplement =
            EpisodeSupplementEntity(
                podcastId = "123",
                feedUrl = "https://feeds.example/show.xml",
                rssNamespaceId = "rss:show",
                feedEtag = "etag-1",
                feedLastModified = null,
                fetchedAt = 1L,
            )
        dao.items.add(
            EpisodeSupplementItemEntity(
                episodeId = "-1",
                podcastId = "123",
                guid = "g1",
                title = "Kept",
                description = "",
                audioUrl = "https://cdn.example/kept.mp3",
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
            ),
        )
        val repo =
            EpisodeSupplementRepository(
                dao = dao,
                feedClient = RssFeedClient(),
                runInTransaction = { it() },
            )
        val outcome =
            repo.refreshFromFeed(
                EpisodeSupplementPort.RefreshFromFeedRequest(
                    podcastIndexId = "123",
                    feedUrl = "https://feeds.example/show.xml",
                    loadBaseline = { error("PI down") },
                ),
            )
        assertTrue(outcome is EpisodeSupplementOutcome.Failure)
        assertEquals("123", dao.supplement?.podcastId)
        assertEquals(listOf("-1"), dao.items.map { it.episodeId })
    }

    private fun rssEpisode(episodeId: String, guid: String, audioUrl: String,) = RssEpisodeEntity(
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

        override suspend fun getSupplement(podcastId: String): EpisodeSupplementEntity? = supplement?.takeIf { it.podcastId == podcastId }

        override suspend fun listOptedInPodcastIds(): List<String> = listOfNotNull(supplement?.podcastId)

        override suspend fun listSupplements(): List<EpisodeSupplementEntity> = listOfNotNull(supplement)

        override suspend fun getEpisode(episodeId: String): EpisodeSupplementItemEntity? = items.find { it.episodeId == episodeId }

        override suspend fun getAllNewest(podcastId: String): List<EpisodeSupplementItemEntity> = items.filter { it.podcastId == podcastId }.sortedByDescending { it.publishedDate }

        override suspend fun search(podcastId: String, query: String,): List<EpisodeSupplementItemEntity> = getAllNewest(podcastId).filter {
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
