package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeCatalogDao
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LocalEpisodeCatalogRepositoryTest {
    @Test
    fun resolveHttpsPrefersPrimaryThenFallback() {
        assertEquals(
            "https://a.example/feed.xml",
            LocalEpisodeCatalogRepository.resolveHttps(
                "https://a.example/feed.xml",
                "https://b.example/feed.xml",
            ),
        )
        assertEquals(
            "https://b.example/feed.xml",
            LocalEpisodeCatalogRepository.resolveHttps("", "https://b.example/feed.xml"),
        )
        assertNull(LocalEpisodeCatalogRepository.resolveHttps("http://insecure.example/f.xml", null))
    }

    @Test
    fun stubFeedIsNotReady() {
        val stub = LocalEpisodeCatalogRepository.stubFeed("100")
        assertEquals(true, stub.needsFullBackfill)
        assertEquals(false, stub.ready)
        assertEquals(false, LocalCatalogReadyLogic.isReady(stub))
    }

    @Test
    fun quietSkipIgnoresNeedsFullBackfillEvenWhenFresh() {
        val now = System.currentTimeMillis()
        assertEquals(
            false,
            shouldSkipQuiet(
                LocalEpisodeCatalogRepository
                    .stubFeed("100", "https://feeds.example/show.xml")
                    .copy(
                        fetchedAt = now,
                        needsFullBackfill = true,
                    ),
            ),
        )
        assertEquals(
            true,
            shouldSkipQuiet(
                LocalEpisodeCatalogRepository
                    .stubFeed("100", "https://feeds.example/show.xml")
                    .copy(
                        fetchedAt = now,
                        needsFullBackfill = false,
                        ready = true,
                        feedEtag = null,
                        feedLastModified = null,
                    ),
            ),
        )
        assertEquals(
            false,
            shouldSkipQuiet(
                LocalEpisodeCatalogRepository
                    .stubFeed("100", "https://feeds.example/show.xml")
                    .copy(
                        fetchedAt = now,
                        needsFullBackfill = false,
                        ready = false,
                    ),
            ),
        )
    }

    @Test
    fun piBaselineReloadsForExistingCatalogThatNeverBecameReady() {
        assertTrue(shouldLoadPiBaseline(null))
        assertTrue(
            shouldLoadPiBaseline(
                LocalEpisodeCatalogRepository
                    .stubFeed("100", "https://feeds.example/show.xml")
                    .copy(
                        needsFullBackfill = false,
                        ready = false,
                    ),
            ),
        )
        assertFalse(
            shouldLoadPiBaseline(
                LocalEpisodeCatalogRepository
                    .stubFeed("100", "https://feeds.example/show.xml")
                    .copy(
                        needsFullBackfill = false,
                        ready = true,
                    ),
            ),
        )
    }

    @Test
    fun refreshFailsForBlankOrRssPodcastId() =
        runTest {
            val repo = catalogRepo()
            val blank =
                repo.refresh(
                    LocalEpisodeCatalogPort.RefreshRequest(
                        podcastIndexId = "",
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                )
            val rss =
                repo.refresh(
                    LocalEpisodeCatalogPort.RefreshRequest(
                        podcastIndexId = "rss:show",
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                )
            assertTrue(blank is RefreshOutcome.Failure)
            assertTrue(rss is RefreshOutcome.Failure)
        }

    @Test
    fun refreshReturnsUnchangedWhenValidatorsMatch() =
        runTest {
            val dao = FakeCatalogDao()
            dao.feeds["100"] =
                LocalEpisodeCatalogRepository.stubFeed("100", "https://feeds.example/show.xml").copy(
                    feedEtag = "etag-1",
                    feedLastModified = "Wed, 01 Jan 2020 00:00:00 GMT",
                    needsFullBackfill = false,
                    ready = true,
                    fetchedAt = 1L,
                )
            val repo = catalogRepo(dao = dao, isFeedUnchanged = { _, _, _ -> true })
            val outcome =
                repo.refresh(
                    LocalEpisodeCatalogPort.RefreshRequest(
                        podcastIndexId = "100",
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                )
            assertTrue(outcome is RefreshOutcome.Unchanged)
        }

    @Test
    fun refreshFailsWhenParsedFeedHasNoEpisodes() =
        runTest {
            val repo = catalogRepo(feedClient = EmptyFeedClient())
            val outcome =
                repo.refresh(
                    LocalEpisodeCatalogPort.RefreshRequest(
                        podcastIndexId = "100",
                        feedUrl = "https://feeds.example/show.xml",
                    ),
                )
            assertTrue(outcome is RefreshOutcome.Failure)
        }

    @Test
    fun isPublisherFeedUnchangedIsFalseWhenFeedUrlChanges() =
        runTest {
            val dao = FakeCatalogDao()
            dao.feeds["100"] =
                LocalEpisodeCatalogRepository.stubFeed("100", "https://feeds.example/old.xml").copy(
                    feedEtag = "etag-1",
                    feedLastModified = "Wed, 01 Jan 2020 00:00:00 GMT",
                )
            var headCalls = 0
            val repo =
                catalogRepo(
                    dao = dao,
                    isFeedUnchanged = { _, _, _ ->
                        headCalls++
                        true
                    },
                )
            assertFalse(
                repo.isPublisherFeedUnchanged("100", "https://feeds.example/new.xml"),
            )
            assertEquals(0, headCalls)
        }

    @Test
    fun isPublisherFeedUnchangedIsFalseWhileCatalogNeedsRepair() =
        runTest {
            val dao = FakeCatalogDao()
            dao.feeds["100"] =
                LocalEpisodeCatalogRepository.stubFeed("100", "https://feeds.example/show.xml").copy(
                    feedEtag = "etag-1",
                    feedLastModified = "Wed, 01 Jan 2020 00:00:00 GMT",
                    needsFullBackfill = false,
                    itemCount = 100,
                    ready = false,
                )
            var headCalls = 0
            val repo =
                catalogRepo(
                    dao = dao,
                    isFeedUnchanged = { _, _, _ ->
                        headCalls++
                        true
                    },
                )

            assertFalse(
                repo.isPublisherFeedUnchanged("100", "https://feeds.example/show.xml"),
            )
            assertEquals(0, headCalls)
        }

    @Test
    fun getWindowNeverExceedsBound() =
        runTest {
            val dao = FakeCatalogDao()
            dao.episodes +=
                (1..12).map { index ->
                    localEpisode(episodeId = "-$index", publishedDate = index.toLong())
                }
            val repo = catalogRepo(dao = dao)
            val window =
                repo.getWindow(
                    podcastId = "100",
                    sort = "newest",
                    bound = 5,
                    aroundEpisodeId = null,
                )
            assertEquals(5, window.size)
        }

    @Test
    fun sweepExpiredSkipsCatalogWhenTtlClearedAfterScan() =
        runTest {
            val dao = FakeCatalogDao()
            dao.feeds["100"] =
                LocalEpisodeCatalogRepository.stubFeed("100", "https://feeds.example/show.xml").copy(
                    ttlExpiresAt = 1L,
                )
            dao.episodes += localEpisode(episodeId = "-1")
            dao.clearTtlAfterListingExpired = true
            val repo = catalogRepo(dao = dao)
            repo.sweepExpired(nowMillis = 10L)
            assertEquals(1, dao.episodes.size)
            assertTrue(dao.feeds.containsKey("100"))
        }

    private fun catalogRepo(
        dao: FakeCatalogDao = FakeCatalogDao(),
        isFeedUnchanged: suspend (String, String?, String?) -> Boolean = { _, _, _ -> false },
        feedClient: RssFeedClient = RssFeedClient(),
    ) = LocalEpisodeCatalogRepository(
        dao = dao,
        feedClient = feedClient,
        runInTransaction = { it() },
        isFeedUnchanged = isFeedUnchanged,
        reconcileListenerState = { _, _ -> },
        megaGetGate = Semaphore(1),
    )

    private class EmptyFeedClient : RssFeedClient() {
        override suspend fun fetch(url: String) =
            RssFetchResult(
                finalUrl = url,
                etag = null,
                lastModified = null,
                body = ByteArray(0),
            )

        override suspend fun parse(
            feedUrl: String,
            bytes: ByteArray,
            podcastId: String,
        ) = ParsedRssFeed(
            title = "Empty",
            author = "",
            description = null,
            imageUrl = null,
            genre = null,
            podcastType = "episodic",
            podcastGuid = null,
            declaredUpdatedAt = null,
            episodes = emptyList(),
        )
    }

    private class FakeCatalogDao : LocalEpisodeCatalogDao {
        val feeds = mutableMapOf<String, LocalEpisodeFeedEntity>()
        val episodes = mutableListOf<LocalEpisodeEntity>()
        var clearTtlAfterListingExpired = false

        override suspend fun upsertFeed(feed: LocalEpisodeFeedEntity): Unit = feeds.set(feed.podcastId, feed)

        override suspend fun upsertEpisodes(episodes: List<LocalEpisodeEntity>) {
            this.episodes.removeAll { stored -> episodes.any { it.episodeId == stored.episodeId } }
            this.episodes += episodes
        }

        override suspend fun getFeed(podcastId: String): LocalEpisodeFeedEntity? = feeds[podcastId]

        override suspend fun getEpisode(episodeId: String): LocalEpisodeEntity? = findEpisode(episodeId)

        private fun findEpisode(id: String) = episodes.find { it.episodeId == id }

        override suspend fun getByGuid(
            podcastId: String,
            guid: String,
        ): LocalEpisodeEntity? = episodes.firstOrNull { it.podcastId == podcastId && it.guid == guid }

        override suspend fun getByAudioUrl(
            podcastId: String,
            audioUrl: String,
        ): LocalEpisodeEntity? = episodes.firstOrNull { it.podcastId == podcastId && it.audioUrl == audioUrl }

        override suspend fun listIdentities(podcastId: String): List<LocalEpisodeIdentity> =
            episodes.filter { it.podcastId == podcastId }.map {
                LocalEpisodeIdentity(it.episodeId, it.guid, it.audioUrl)
            }

        override suspend fun getNewestPage(
            podcastId: String,
            limit: Int,
            offset: Int,
        ): List<LocalEpisodeEntity> =
            episodes
                .filter { it.podcastId == podcastId }
                .sortedWith(compareByDescending<LocalEpisodeEntity> { it.publishedDate }.thenBy { it.episodeId })
                .drop(offset)
                .take(limit)

        override suspend fun getOldestPage(
            podcastId: String,
            limit: Int,
            offset: Int,
        ): List<LocalEpisodeEntity> =
            episodes
                .filter { it.podcastId == podcastId }
                .sortedWith(compareBy<LocalEpisodeEntity> { it.publishedDate }.thenByDescending { it.episodeId })
                .drop(offset)
                .take(limit)

        override suspend fun getNewest(podcastId: String): LocalEpisodeEntity? =
            getNewestPage(podcastId, limit = 1, offset = 0).firstOrNull()

        override suspend fun count(podcastId: String): Int = episodes.count { it.podcastId == podcastId }

        override suspend fun search(
            podcastId: String,
            query: String,
        ): List<LocalEpisodeEntity> = emptyList()

        override suspend fun getOlderThan(
            podcastId: String,
            publishedDate: Long,
            episodeId: String,
            limit: Int,
        ): List<LocalEpisodeEntity> = emptyList()

        override suspend fun getNewerThan(
            podcastId: String,
            publishedDate: Long,
            episodeId: String,
            limit: Int,
        ): List<LocalEpisodeEntity> = emptyList()

        override suspend fun setTtl(
            podcastId: String,
            ttlExpiresAt: Long?,
        ) {
            feeds[podcastId] = feeds.getValue(podcastId).copy(ttlExpiresAt = ttlExpiresAt)
        }

        override suspend fun setFeedUrlLookupAt(
            podcastId: String,
            atMillis: Long,
        ) {
            feeds[podcastId] = feeds.getValue(podcastId).copy(feedUrlLookupAt = atMillis)
        }

        override suspend fun deleteEpisodes(podcastId: String) {
            episodes.removeAll { it.podcastId == podcastId }
        }

        override suspend fun deleteFeed(podcastId: String) {
            feeds.remove(podcastId)
        }

        override suspend fun listExpiredFeedIds(now: Long): List<String> {
            val ids =
                feeds.values
                    .filter { feed -> feed.ttlExpiresAt != null && feed.ttlExpiresAt!! <= now }
                    .map { it.podcastId }
            if (clearTtlAfterListingExpired) {
                for (id in ids) {
                    feeds[id] = feeds.getValue(id).copy(ttlExpiresAt = null)
                }
            }
            return ids
        }
    }

    private fun localEpisode(
        episodeId: String,
        publishedDate: Long = 1L,
    ) = LocalEpisodeEntity(
        episodeId = episodeId,
        podcastId = "100",
        guid = "g$episodeId",
        title = "Ep $episodeId",
        description = "",
        audioUrl = "https://cdn.example/$episodeId.mp3",
        imageUrl = null,
        duration = 1,
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
