package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private const val SHOW_URL = "https://feeds.example/show.xml"

/** A followed show's catalog read records its conditional check, and pull to refresh skips only the quiet wait. */
class LocalEpisodeCatalogFeedCheckTest {
    /**
     * Regression: the foreground sync took an unchanged answer before calling refresh, so a quiet
     * feed's read time never moved and it stayed due, first in line, after every restart.
     */
    @Test fun anUnchangedAnswerRecordsTheCheckAsARead() = runTest {
        val dao = FakeCatalogDao()
        val sevenHoursAgo = System.currentTimeMillis() - 7L * 60 * 60 * 1000
        dao.feeds["100"] = readyFeed(fetchedAt = sevenHoursAgo)
        val client = CountingFeedClient()
        var checks = 0
        val repo = catalogRepo(dao, feedClient = client, isFeedUnchanged = { _, _, _ ->
            checks++
            true
        })
        assertTrue(repo.isRefreshDue("100", SHOW_URL, System.currentTimeMillis()))

        val before = System.currentTimeMillis()
        val outcome = repo.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", SHOW_URL))

        assertTrue(outcome is RefreshOutcome.Unchanged)
        assertEquals(1, checks)
        assertEquals(0, client.fetches, "no full download")
        assertTrue(dao.feeds.getValue("100").fetchedAt >= before)
        assertTrue(repo.lastFeedReadAt("100") >= before)
        assertFalse(repo.isRefreshDue("100", SHOW_URL, System.currentTimeMillis()))
    }

    /**
     * Regression: an unchanged answer rewrote the row from the copy read before the check, so an
     * unfollow that landed while the check ran lost its TTL and the cleanup never swept the copy.
     */
    @Test fun anUnchangedAnswerKeepsAnUnfollowMadeDuringTheCheck() = runTest {
        val dao = FakeCatalogDao()
        val sevenHoursAgo = System.currentTimeMillis() - 7L * 60 * 60 * 1000
        val ttl = System.currentTimeMillis() + 24L * 60 * 60 * 1000
        dao.feeds["100"] = readyFeed(fetchedAt = sevenHoursAgo).copy(ttlExpiresAt = null, followed = true)
        val repo = catalogRepo(dao, feedClient = CountingFeedClient(), isFeedUnchanged = { _, _, _ ->
            dao.setTtlAndFollowed("100", ttlExpiresAt = ttl, followed = false)
            true
        })

        val outcome = repo.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", SHOW_URL))

        assertTrue(outcome is RefreshOutcome.Unchanged)
        val stored = dao.feeds.getValue("100")
        assertEquals(ttl, stored.ttlExpiresAt)
        assertFalse(stored.followed)
        assertTrue(stored.fetchedAt > sevenHoursAgo)
    }

    /** Regression: a changed feed's write cleared the TTL of an unfollow that landed while the feed was read. */
    @Test fun aChangedFeedKeepsAnUnfollowMadeDuringTheRead() = runTest {
        val dao = FakeCatalogDao()
        val ttl = System.currentTimeMillis() + 24L * 60 * 60 * 1000
        dao.feeds["100"] = readyFeed(fetchedAt = System.currentTimeMillis() - 7L * 60 * 60 * 1000).copy(ttlExpiresAt = null, followed = true)
        val client = CountingFeedClient(onFetch = { dao.setTtlAndFollowed("100", ttlExpiresAt = ttl, followed = false) })
        val repo = catalogRepo(dao, feedClient = client, isFeedUnchanged = { _, _, _ -> false })

        assertTrue(repo.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", SHOW_URL)) is RefreshOutcome.Success)
        assertEquals(1, client.fetches)
        val stored = dao.feeds.getValue("100")
        assertEquals(ttl, stored.ttlExpiresAt)
        assertFalse(stored.followed)
    }

    @Test fun aPullInsideTheQuietWaitStillChecksAndDownloadsOnlyAChangedFeed() = runTest {
        val dao = FakeCatalogDao()
        val client = CountingFeedClient()
        var unchanged = true
        var checks = 0
        val repo = catalogRepo(dao, feedClient = client, isFeedUnchanged = { _, _, _ ->
            checks++
            unchanged
        })
        val pull = LocalEpisodeCatalogPort.RefreshRequest("100", SHOW_URL, reason = LocalEpisodeCatalogPort.RefreshReason.PULL)
        val oneHourAgo = System.currentTimeMillis() - 60L * 60 * 1000
        dao.feeds["100"] = readyFeed(fetchedAt = oneHourAgo)
        assertTrue(shouldSkipQuiet(dao.feeds.getValue("100")), "a NORMAL read would wait")
        assertFalse(shouldSkipQuiet(dao.feeds.getValue("100"), LocalEpisodeCatalogPort.RefreshReason.PULL))

        val before = System.currentTimeMillis()
        assertTrue(repo.refresh(pull) is RefreshOutcome.Unchanged)
        assertEquals(1, checks)
        assertEquals(0, client.fetches, "an unchanged feed is not downloaded")
        assertTrue(dao.feeds.getValue("100").fetchedAt >= before)

        dao.feeds["100"] = dao.feeds.getValue("100").copy(fetchedAt = oneHourAgo)
        unchanged = false
        assertTrue(repo.refresh(pull) is RefreshOutcome.Success)
        assertEquals(2, checks)
        assertEquals(1, client.fetches, "a changed feed is read in full")
        assertTrue(dao.episodes.any { it.podcastId == "100" })
    }

    /**
     * Regression: episodes an older parser stored without a date (feeds that write EST or PDT)
     * stayed undated, because the feed kept answering "not modified" and was never parsed again.
     */
    @Test fun aStoredEpisodeWithoutADateIsReadInFullUntilItHasOne() = runTest {
        val dao = FakeCatalogDao()
        val sevenHoursAgo = System.currentTimeMillis() - 7L * 60 * 60 * 1000
        dao.feeds["100"] = readyFeed(fetchedAt = sevenHoursAgo)
        dao.episodes += undatedEpisode()
        val client = CountingFeedClient()
        var checks = 0
        val repo = catalogRepo(dao, feedClient = client, isFeedUnchanged = { _, _, _ ->
            checks++
            true
        })
        assertFalse(repo.isPublisherFeedUnchanged("100", SHOW_URL))

        assertTrue(repo.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", SHOW_URL)) is RefreshOutcome.Success)
        assertEquals(0, checks, "no conditional check while an episode has no date")
        assertEquals(1, client.fetches)
        assertEquals(listOf(10L), dao.episodes.filter { it.podcastId == "100" }.map { it.publishedDate })

        dao.feeds["100"] = dao.feeds.getValue("100").copy(fetchedAt = sevenHoursAgo)
        assertTrue(repo.isPublisherFeedUnchanged("100", SHOW_URL))
        assertTrue(repo.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", SHOW_URL)) is RefreshOutcome.Unchanged)
        assertEquals(1, client.fetches, "a dated catalog trusts the unchanged answer again")
    }

    private fun undatedEpisode() = LocalEpisodeEntity(
        episodeId = "-42",
        podcastId = "100",
        guid = "guid-1",
        title = "Episode",
        description = "",
        audioUrl = "https://cdn.example/episode.mp3",
        imageUrl = null,
        duration = 60,
        publishedDate = 0L,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    private fun catalogRepo(dao: FakeCatalogDao, feedClient: RssFeedClient, isFeedUnchanged: suspend (String, String?, String?) -> Boolean) =
        LocalEpisodeCatalogRepository(
            dao = dao,
            feedClient = feedClient,
            runInTransaction = { it() },
            isFeedUnchanged = isFeedUnchanged,
            reconcileListenerState = { _, _ -> },
            megaGetGate = Semaphore(1),
            identity = InMemoryCatalogIdentityStore(),
        )

    private fun readyFeed(fetchedAt: Long) = LocalEpisodeCatalogRepository.stubFeed("100", SHOW_URL).copy(
        fetchedAt = fetchedAt,
        ready = true,
        needsFullBackfill = false,
        feedEtag = "etag-1",
    )

    /** A one-episode feed that counts full downloads. */
    private class CountingFeedClient(private val onFetch: suspend () -> Unit = {}) : RssFeedClient() {
        var fetches = 0

        override suspend fun fetch(url: String): RssFetchResult {
            fetches++
            onFetch()
            return RssFetchResult(finalUrl = url, etag = "etag-2", lastModified = null, body = ByteArray(1))
        }

        override suspend fun parse(feedUrl: String, bytes: ByteArray, podcastId: String,) = ParsedRssFeed(
            title = "Show",
            author = "",
            description = null,
            imageUrl = null,
            genre = null,
            podcastType = "episodic",
            podcastGuid = null,
            declaredUpdatedAt = null,
            episodes =
            listOf(
                RssEpisodeEntity(
                    episodeId = RssIdGenerator.episodeIdForPodcast(podcastId, "guid-1", AUDIO_URL, 10L, "Episode"),
                    podcastId = podcastId,
                    guid = "guid-1",
                    title = "Episode",
                    description = "",
                    audioUrl = AUDIO_URL,
                    imageUrl = null,
                    duration = 60,
                    publishedDate = 10L,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeType = null,
                    enclosureType = null,
                ),
            ),
        )

        private companion object {
            const val AUDIO_URL = "https://cdn.example/episode.mp3"
        }
    }
}
