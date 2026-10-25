package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeCatalogDao
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.LocalEpisodeOwner
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.test.runCurrent
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
    fun automaticChecksShareSixHourCooldownWithOrWithoutValidatorsAndExplicitRefreshBypassesIt() {
        val now = System.currentTimeMillis()
        val feed = LocalEpisodeCatalogRepository.stubFeed("100", "https://feeds.example/show.xml")
            .copy(fetchedAt = now - 3_600_001, ready = true, needsFullBackfill = false)
        assertTrue(shouldSkipQuiet(feed, nowMillis = now))
        assertTrue(shouldSkipQuiet(feed, LocalEpisodeCatalogPort.RefreshReason.AUTO_DOWNLOAD, now))
        assertTrue(shouldSkipQuiet(feed.copy(feedEtag = "etag"), nowMillis = now))
        assertFalse(shouldSkipQuiet(feed.copy(fetchedAt = now - LocalEpisodeCatalogRepository.QUIET_INTERVAL_MS), nowMillis = now))
        assertFalse(shouldSkipQuiet(feed.copy(fetchedAt = now), LocalEpisodeCatalogPort.RefreshReason.MANUAL, now))
        assertFalse(shouldSkipQuiet(feed.copy(fetchedAt = now), LocalEpisodeCatalogPort.RefreshReason.NEW_RELEASE, now))
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
    fun refreshFailsForBlankOrRssPodcastId() = runTest {
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
    fun refreshReturnsUnchangedWhenValidatorsMatch() = runTest {
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
    fun refreshFailsWhenParsedFeedHasNoEpisodes() = runTest {
        val repo = catalogRepo(feedClient = EmptyFeedClient())
        val outcome =
            repo.refresh(
                LocalEpisodeCatalogPort.RefreshRequest(
                    podcastIndexId = "100",
                    feedUrl = "https://feeds.example/show.xml",
                ),
            )
        assertTrue(outcome is RefreshOutcome.Failure)
        assertTrue((outcome as RefreshOutcome.Failure).addressGone, "an address with no episodes is worth re-checking")
    }

    @Test
    fun refreshReportsAPermanentMoveOnlyWhenAskedAndTheShowMatches() = runTest {
        val moved =
            object : RssFeedClient() {
                private val shared = SharedFeedClient()

                override suspend fun fetch(url: String) = shared.fetch(url).copy(finalUrl = "https://new.example/shared.xml", movedTo = "https://new.example/shared.xml")

                override suspend fun parse(feedUrl: String, bytes: ByteArray, podcastId: String,) = shared.parse(feedUrl, bytes, podcastId)
            }
        suspend fun movedTo(detectMove: Boolean, title: String) =
            (
                catalogRepo(feedClient = moved).refresh(
                    LocalEpisodeCatalogPort.RefreshRequest(
                        podcastIndexId = "100",
                        feedUrl = "https://feeds.example/shared.xml",
                        meta = LocalEpisodeCatalogPort.PodcastMeta(title = title),
                        reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL,
                        detectMove = detectMove,
                    ),
                ) as RefreshOutcome.Success
                ).movedTo
        assertEquals("https://new.example/shared.xml", movedTo(detectMove = true, title = "Shared"))
        assertNull(movedTo(detectMove = false, title = "Shared"))
        assertNull(movedTo(detectMove = true, title = "Another show"))
    }

    @Test
    fun refreshFailureSaysWhetherTheSavedAddressLooksGone() = runTest {
        val outcomes =
            listOf<Exception>(FeedHttpException(410), FeedHttpException(503), java.net.SocketTimeoutException("slow")).map { error ->
                catalogRepo(
                    feedClient =
                    object : RssFeedClient() {
                        override suspend fun fetch(url: String): RssFetchResult = throw error
                    },
                ).refresh(LocalEpisodeCatalogPort.RefreshRequest(podcastIndexId = "100", feedUrl = "https://feeds.example/show.xml"))
            }
        assertEquals(listOf(true, false, false), outcomes.map { (it as RefreshOutcome.Failure).addressGone })
    }

    @Test
    fun isPublisherFeedUnchangedIsFalseWhenFeedUrlChanges() = runTest {
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
    fun isPublisherFeedUnchangedIsFalseWhileCatalogNeedsRepair() = runTest {
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
    fun getWindowNeverExceedsBound() = runTest {
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
    fun sweepExpiredSkipsCatalogWhenTtlClearedAfterScan() = runTest {
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

    @Test
    fun getWindowWithAroundEpisodeIdReturnsAnchorAndSubsequentEpisodes() = runTest {
        val dao = FakeCatalogDao()
        dao.feeds["100"] = LocalEpisodeCatalogRepository.stubFeed("100", "https://feeds.example/show.xml")
        dao.episodes +=
            listOf(
                localEpisode(episodeId = "-1", publishedDate = 1000L),
                localEpisode(episodeId = "-2", publishedDate = 2000L),
                localEpisode(episodeId = "-3", publishedDate = 3000L),
                localEpisode(episodeId = "-4", publishedDate = 500L),
            )
        val meta =
            cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta(
                title = "Show",
                imageUrl = "https://example.com/art.jpg",
                genre = "News",
                artist = "Author",
            )
        val repo = catalogRepo(dao)
        val window =
            repo.getWindow(
                podcastId = "100",
                sort = "newest",
                bound = 10,
                aroundEpisodeId = "-1",
                meta = meta,
            )
        assertEquals(listOf("-1", "-2", "-3"), window.map { it.id })
    }

    @Test fun recentValidatedFeedDoesNotEvenPerformHeadCheck() = runTest {
        val dao = FakeCatalogDao()
        dao.feeds["100"] = LocalEpisodeCatalogRepository.stubFeed("100", "https://feeds.example/show.xml")
            .copy(fetchedAt = System.currentTimeMillis(), ready = true, needsFullBackfill = false, feedEtag = "etag")
        var heads = 0
        val repo = catalogRepo(dao, isFeedUnchanged = { _, _, _ ->
            heads++
            true
        })
        assertTrue(repo.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", "https://feeds.example/show.xml")) is RefreshOutcome.Unchanged)
        assertEquals(0, heads)
        assertFalse(repo.isRefreshDue("100", "https://feeds.example/show.xml", System.currentTimeMillis()))
        assertTrue(repo.isRefreshDue("100", "https://feeds.example/changed.xml", System.currentTimeMillis()))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun permissionIsRecheckedAfterWaitingForSharedFetchSlot() = runTest {
        var allowed = true
        var fetches = 0
        val slots = Semaphore(1, acquiredPermits = 1)
        val repo = catalogRepo(
            feedClient = object : RssFeedClient() {
            override suspend fun fetch(url: String): RssFetchResult {
                fetches++
                error("revoked fetch must not run")
            }
        },
            megaGetGate = slots
        )
        val pending = async {
            repo.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", "https://feeds.example/show.xml", canProceed = { allowed }))
        }
        runCurrent()
        allowed = false
        slots.release()
        assertTrue(pending.await() is RefreshOutcome.Failure)
        assertEquals(0, fetches)
    }

    @Test
    fun twoShowsReadingTheSameFeedKeepTheirOwnEpisodes() = runTest {
        // A feed listed twice in Podcast Index: both shows mint the same IDs from one feed namespace,
        // and the second refresh used to replace the first show's row (wrong show, title and audio).
        val dao = FakeCatalogDao()
        val repo = catalogRepo(dao = dao, feedClient = SharedFeedClient())
        val idsAfter = mutableListOf<Set<String>>()
        for (show in listOf("100", "200", "200", "100")) {
            val outcome =
                repo.refresh(
                    LocalEpisodeCatalogPort.RefreshRequest(
                        podcastIndexId = show,
                        feedUrl = "https://feeds.example/shared.xml",
                        reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL,
                    ),
                )
            assertTrue(outcome is RefreshOutcome.Success)
            idsAfter += dao.episodes.map { it.episodeId }.toSet()
        }

        assertEquals(listOf("100", "200"), dao.episodes.map { it.podcastId }.sorted())
        val first = dao.episodes.single { it.podcastId == "100" }
        val second = dao.episodes.single { it.podcastId == "200" }
        assertEquals(SharedFeedClient.mintedId(), first.episodeId)
        assertTrue(second.episodeId != first.episodeId && second.episodeId.toLong() < 0L)
        assertEquals(first.guid, second.guid, "both keep the episode itself")
        assertEquals(idsAfter[1], idsAfter[3], "IDs stay put on later refreshes")
    }

    @Test
    fun aRowWithNoFreeIdFailsTheRefreshAndWritesNothing() = runTest {
        // Every ID the second show could take already belongs to a third show.
        val dao = FakeCatalogDao()
        val namespace = RssIdGenerator.podcastId("https://feeds.example/shared.xml")
        val taken =
            listOf(SharedFeedClient.mintedId()) +
                (1..LocalEpisodeCollisionGuard.MAX_RESERVE_ATTEMPTS).map {
                    StickyEpisodeIdentity.reservedEpisodeId(namespace, "200", SharedFeedClient.GUID, it)
                }
        dao.episodes += taken.map { localEpisode(it).copy(podcastId = "300", guid = "other-$it") }
        val repo = catalogRepo(dao = dao, feedClient = SharedFeedClient())

        val outcome =
            repo.refresh(
                LocalEpisodeCatalogPort.RefreshRequest(
                    podcastIndexId = "200",
                    feedUrl = "https://feeds.example/shared.xml",
                    reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL,
                ),
            )

        assertTrue(outcome is RefreshOutcome.Failure)
        assertTrue(dao.episodes.none { it.podcastId == "200" })
        assertEquals(taken.size, dao.episodes.count { it.podcastId == "300" })
        assertNull(dao.feeds["200"], "no catalog row marked complete")
    }

    @Test
    fun aLegacyKeyWithTrailingWhitespaceKeepsItsIdAndGetsNoSecondRow() = runTest {
        // Room migration 31→32 trimmed keys with SQLite TRIM (spaces only), so a stored key can keep
        // a newline, tab or no-break space. The refresh re-trims it and must update that same row.
        for (suffix in listOf("\n", "\t", " \n", "\u00A0", "\u2028", "\u180E")) {
            val dao = FakeCatalogDao()
            dao.episodes += localEpisode("-501").copy(guid = SharedFeedClient.GUID + suffix)
            val outcome = catalogRepo(dao = dao, feedClient = SharedFeedClient()).refresh(sharedFeedRequest("100"))

            assertTrue(outcome is RefreshOutcome.Success)
            val row = dao.episodes.single()
            assertEquals("-501", row.episodeId, "the stored ID stays for key suffix ${suffix.map { "U+%04X".format(it.code) }}")
            assertEquals(SharedFeedClient.GUID, row.guid)
            assertEquals("Shared episode", row.title)
        }
    }

    @Test
    fun aStoredKeyIsNeverDeletedThroughTheUniqueIndex() = runTest {
        // Two rows of one show whose keys trim to the same value: the refresh may update either, but
        // it must never write another ID under the exact key and so delete the row stored there.
        val dao = FakeCatalogDao()
        dao.episodes += localEpisode("-601").copy(guid = SharedFeedClient.GUID + "\n")
        dao.episodes += localEpisode("-602").copy(guid = SharedFeedClient.GUID)
        val outcome = catalogRepo(dao = dao, feedClient = SharedFeedClient()).refresh(sharedFeedRequest("100"))

        assertTrue(outcome is RefreshOutcome.Success)
        assertEquals(setOf("-601", "-602"), dao.episodes.map { it.episodeId }.toSet())
        assertEquals("Shared episode", dao.episodes.single { it.guid == SharedFeedClient.GUID }.title)
    }

    @Test
    fun aLegacyRowHoldingTheItemsOwnIdHealsToTheRealKey() = runTest {
        // Legacy rows keyed `id:<ownId>`, or by audio address while the item has a GUID: when the
        // feed's item mints exactly the stored ID, it is the same episode and its key is corrected.
        val minted = SharedFeedClient.mintedId()
        for (legacyKey in listOf("id:$minted", SharedFeedClient.AUDIO_URL)) {
            val dao = FakeCatalogDao()
            dao.episodes += localEpisode(minted).copy(guid = legacyKey, audioUrl = SharedFeedClient.AUDIO_URL)
            val outcome = catalogRepo(dao = dao, feedClient = SharedFeedClient()).refresh(sharedFeedRequest("100"))

            assertTrue(outcome is RefreshOutcome.Success)
            val row = dao.episodes.single()
            assertEquals(minted, row.episodeId, "no second row for $legacyKey")
            assertEquals(SharedFeedClient.GUID, row.guid)
        }
    }

    @Test
    fun aLegacyRowHealsOnAFirstReadThatMatchesAPodcastIndexEpisode() = runTest {
        // A catalog that is not ready yet reads the Podcast Index list, so the item arrives with the
        // matching PI ID. The legacy row holding the item's own mint is the same episode: it keeps its
        // ID (and the listener state on it) instead of staying beside a second row under the PI ID.
        val minted = SharedFeedClient.mintedId()
        for (legacyKey in listOf("id:$minted", SharedFeedClient.AUDIO_URL)) {
            val dao = FakeCatalogDao()
            dao.episodes += localEpisode(minted).copy(guid = legacyKey, audioUrl = SharedFeedClient.AUDIO_URL)
            var baselineReads = 0
            val reconciled = mutableListOf<String>()
            val request =
                sharedFeedRequest("100").copy(
                    loadPiBaseline = {
                        baselineReads++
                        listOf(Episode(id = "777", title = "Shared episode", description = "", audioUrl = SharedFeedClient.AUDIO_URL, publishedDate = 10L))
                    },
                )
            val outcome =
                catalogRepo(dao = dao, feedClient = SharedFeedClient(), reconcileListenerState = { _, rows -> reconciled += rows.map { it.episodeId } })
                    .refresh(request)

            assertTrue(outcome is RefreshOutcome.Success)
            assertEquals(1, baselineReads, "the first read uses the Podcast Index list")
            val row = dao.episodes.single()
            assertEquals(minted, row.episodeId, "no second row under the PI ID for $legacyKey")
            assertEquals(SharedFeedClient.GUID, row.guid)
            assertEquals(listOf(minted), reconciled, "listener state stays on the stored ID")
        }
    }

    @Test
    fun aFirstReadStillUsesThePodcastIndexIdForANewEpisode() = runTest {
        // No stored row holds the item's own mint, so the PI match is kept as before.
        val dao = FakeCatalogDao()
        val request =
            sharedFeedRequest("100").copy(
                loadPiBaseline = {
                    listOf(Episode(id = "777", title = "Shared episode", description = "", audioUrl = SharedFeedClient.AUDIO_URL, publishedDate = 10L))
                },
            )
        val outcome = catalogRepo(dao = dao, feedClient = SharedFeedClient()).refresh(request)

        assertTrue(outcome is RefreshOutcome.Success)
        assertEquals("777", dao.episodes.single().episodeId)
    }

    @Test
    fun anotherEpisodeOfTheSameShowHoldingTheIdStaysApart() = runTest {
        // Same ID and show, but a real GUID that differs: two episodes, so both rows survive.
        val minted = SharedFeedClient.mintedId()
        val dao = FakeCatalogDao()
        dao.episodes += localEpisode(minted).copy(guid = "another-guid")
        val outcome = catalogRepo(dao = dao, feedClient = SharedFeedClient()).refresh(sharedFeedRequest("100"))

        assertTrue(outcome is RefreshOutcome.Success)
        assertEquals("another-guid", dao.episodes.single { it.episodeId == minted }.guid)
        assertEquals(2, dao.episodes.size)
    }

    private fun sharedFeedRequest(show: String) = LocalEpisodeCatalogPort.RefreshRequest(
        podcastIndexId = show,
        feedUrl = "https://feeds.example/shared.xml",
        reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL,
    )

    private fun catalogRepo(
        dao: FakeCatalogDao = FakeCatalogDao(),
        isFeedUnchanged: suspend (String, String?, String?) -> Boolean = { _, _, _ -> false },
        feedClient: RssFeedClient = RssFeedClient(),
        megaGetGate: Semaphore = Semaphore(1),
        reconcileListenerState: suspend (String, List<LocalEpisodeEntity>) -> Unit = { _, _ -> },
    ) = LocalEpisodeCatalogRepository(
        dao = dao,
        feedClient = feedClient,
        runInTransaction = { it() },
        isFeedUnchanged = isFeedUnchanged,
        reconcileListenerState = reconcileListenerState,
        megaGetGate = megaGetGate,
    )

    private class EmptyFeedClient : RssFeedClient() {
        override suspend fun fetch(url: String) = RssFetchResult(
            finalUrl = url,
            etag = null,
            lastModified = null,
            body = ByteArray(0),
        )

        override suspend fun parse(feedUrl: String, bytes: ByteArray, podcastId: String,) = ParsedRssFeed(
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

    /** One episode in one feed, read by every show that points at it. */
    private class SharedFeedClient : RssFeedClient() {
        override suspend fun fetch(url: String) = RssFetchResult(finalUrl = url, etag = null, lastModified = null, body = ByteArray(1))

        override suspend fun parse(feedUrl: String, bytes: ByteArray, podcastId: String,) = ParsedRssFeed(
            title = "Shared",
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
                    episodeId = mintedId(),
                    podcastId = podcastId,
                    guid = GUID,
                    title = "Shared episode",
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

        companion object {
            const val GUID = "shared-guid"
            const val AUDIO_URL = "https://cdn.example/shared.mp3"

            fun mintedId(): String = RssIdGenerator.episodeIdForPodcast(
                podcastId = RssIdGenerator.podcastId("https://feeds.example/shared.xml"),
                guid = GUID,
                enclosureUrl = AUDIO_URL,
                publishedDate = 10L,
                title = "Shared episode",
            )
        }
    }

    private class FakeCatalogDao : LocalEpisodeCatalogDao {
        val feeds = mutableMapOf<String, LocalEpisodeFeedEntity>()
        val episodes = mutableListOf<LocalEpisodeEntity>()
        var clearTtlAfterListingExpired = false

        override suspend fun upsertFeed(feed: LocalEpisodeFeedEntity): Unit = feeds.set(feed.podcastId, feed)

        override suspend fun replaceFeedRow(feed: LocalEpisodeFeedEntity): Unit = feeds.set(feed.podcastId, feed)

        /** Room's REPLACE: a stored row is deleted when it shares the primary key or the unique (podcastId, guid) index. */
        override suspend fun upsertEpisodes(episodes: List<LocalEpisodeEntity>) {
            for (row in episodes) {
                this.episodes.removeAll { stored ->
                    stored.episodeId == row.episodeId || (stored.podcastId == row.podcastId && stored.guid == row.guid)
                }
                this.episodes += row
            }
        }

        override suspend fun getFeed(podcastId: String): LocalEpisodeFeedEntity? = feeds[podcastId]

        override suspend fun getEpisode(episodeId: String): LocalEpisodeEntity? = findEpisode(episodeId)

        private fun findEpisode(id: String) = episodes.find { it.episodeId == id }

        override suspend fun getByGuid(podcastId: String, guid: String,): LocalEpisodeEntity? = episodes.firstOrNull { it.podcastId == podcastId && it.guid == guid }

        override suspend fun getByAudioUrl(podcastId: String, audioUrl: String,): LocalEpisodeEntity? = episodes.firstOrNull { it.podcastId == podcastId && it.audioUrl == audioUrl }

        override suspend fun listIdentities(podcastId: String): List<LocalEpisodeIdentity> = episodes.filter { it.podcastId == podcastId }.map {
            LocalEpisodeIdentity(it.episodeId, it.guid, it.audioUrl)
        }

        override suspend fun findOwners(episodeIds: List<String>): List<LocalEpisodeOwner> = episodes
            .filter { it.episodeId in episodeIds }
            .map { LocalEpisodeOwner(it.episodeId, it.podcastId, it.guid) }

        override suspend fun getNewestPage(podcastId: String, limit: Int, offset: Int,): List<LocalEpisodeEntity> = episodes
            .filter { it.podcastId == podcastId }
            .sortedWith(compareByDescending<LocalEpisodeEntity> { it.publishedDate }.thenBy { it.episodeId })
            .drop(offset)
            .take(limit)

        override suspend fun getOldestPage(podcastId: String, limit: Int, offset: Int,): List<LocalEpisodeEntity> = episodes
            .filter { it.podcastId == podcastId }
            .sortedWith(compareBy<LocalEpisodeEntity> { it.publishedDate }.thenByDescending { it.episodeId })
            .drop(offset)
            .take(limit)

        override suspend fun getNewest(podcastId: String): LocalEpisodeEntity? = getNewestPage(podcastId, limit = 1, offset = 0).firstOrNull()

        override suspend fun count(podcastId: String): Int = episodes.count { it.podcastId == podcastId }

        override suspend fun search(podcastId: String, query: String,): List<LocalEpisodeEntity> = emptyList()

        override suspend fun getEpisodesAfter(
            podcastId: String,
            publishedDate: Long,
            episodeId: String,
            limit: Int,
        ): List<LocalEpisodeEntity> {
            val comparator = compareBy<LocalEpisodeEntity> { it.publishedDate }.thenByDescending { it.episodeId }
            val matching =
                episodes.filter {
                    it.podcastId == podcastId &&
                        (it.publishedDate > publishedDate || (it.publishedDate == publishedDate && it.episodeId < episodeId))
                }
            return matching.sortedWith(comparator).take(limit)
        }

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

        override suspend fun setTtl(podcastId: String, ttlExpiresAt: Long?,) {
            feeds[podcastId] = feeds.getValue(podcastId).copy(ttlExpiresAt = ttlExpiresAt)
        }

        override suspend fun setFeedUrlLookupAt(podcastId: String, atMillis: Long,) {
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

    private fun localEpisode(episodeId: String, publishedDate: Long = 1L,) = LocalEpisodeEntity(
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
