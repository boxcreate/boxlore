package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionForegroundSyncTest {
    @org.junit.jupiter.api.BeforeEach
    fun resetProcessGuard() {
        SubscriptionForegroundSync.resetProcessGuardForTests()
    }

    @Test
    fun ensureStartedRunsSyncActionOnlyOnce() =
        runTest {
            var runs = 0
            val sync =
                SubscriptionForegroundSync(
                    scope = this,
                    initialDelayMs = 2_000L,
                    syncAction = { runs++ },
                    periodicIntervalMs = 0L,
                    nowMs = { testScheduler.currentTime },
                )

            assertFalse(sync.hasStarted())
            sync.ensureStarted()
            sync.ensureStarted()
            sync.ensureStarted()
            assertTrue(sync.hasStarted())

            runCurrent()
            assertEquals(0, runs)
            advanceTimeBy(2_000L)
            runCurrent()
            assertEquals(1, runs)
        }

    @Test
    fun ensureStartedHonorsInitialDelay() =
        runTest {
            var runs = 0
            val sync =
                SubscriptionForegroundSync(
                    scope = this,
                    initialDelayMs = 500L,
                    syncAction = { runs++ },
                    periodicIntervalMs = 0L,
                    nowMs = { testScheduler.currentTime },
                )

            sync.ensureStarted()
            advanceTimeBy(499L)
            runCurrent()
            assertEquals(0, runs)

            advanceTimeBy(1L)
            runCurrent()
            assertEquals(1, runs)
        }

    @Test
    fun syncSubscribedLatestEpisodes_emptyIdsSkipsWork() =
        runTest {
            var syncCalls = 0
            SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
                loadIds = { emptySet() },
                syncChunk = {
                    syncCalls++
                    emptyMap()
                },
                saveLatest = { _, _ -> error("should not save") },
                chunkSize = 2,
            )
            assertEquals(0, syncCalls)
        }

    @Test
    fun syncSubscribedLatestEpisodes_chunksAndIsolatesFailures() =
        runTest {
            val saved = mutableListOf<String>()
            val episode =
                cx.aswin.boxlore.core.model.Episode(
                    id = "e1",
                    title = "T",
                    description = "",
                    audioUrl = "https://example.com/a.mp3",
                    imageUrl = null,
                    publishedDate = 1L,
                    duration = 60,
                    podcastId = "a",
                )
            SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
                loadIds = { setOf("a", "b", "c") },
                syncChunk = { chunk ->
                    if (chunk.contains("b")) error("boom")
                    chunk.associateWith { episode.copy(id = "e_$it", podcastId = it) }
                },
                saveLatest = { id, _ -> saved += id },
                chunkSize = 1,
            )
            assertEquals(setOf("a", "c"), saved.toSet())
        }

    @Test
    fun syncSubscribedLatestEpisodes_optedInUsesDirectFeedNotPiSync() =
        runTest {
            val piSynced = mutableListOf<List<String>>()
            val saved = mutableMapOf<String, String>()
            val feedTip =
                cx.aswin.boxlore.core.model.Episode(
                    id = "-1",
                    title = "From feed",
                    description = "",
                    audioUrl = "https://example.com/feed.mp3",
                    imageUrl = null,
                    publishedDate = 99L,
                    duration = 60,
                    podcastId = "opted",
                )
            var networkCalls = 0
            SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
                loadIds = { setOf("opted", "plain", "rss:other") },
                loadPodcastMeta = { id ->
                    DirectFeedTipMeta(
                        feedUrl = "https://feeds.example/$id.xml",
                        title = id,
                        imageUrl = null,
                        genre = null,
                        artist = null,
                        knownTip = null,
                    )
                },
                syncChunk = { chunk ->
                    piSynced += chunk
                    chunk.associateWith { feedTip.copy(id = "pi_$it", podcastId = it) }
                },
                saveLatest = { id, ep -> saved[id] = ep.id },
                chunkSize = 10,
                directFeed =
                    DirectFeedSyncSeams(
                        loadOptedInIds = { setOf("opted") },
                        loadCachedFeedTip = { null },
                        resolveFeedTip = { id, _ ->
                            networkCalls++
                            assertEquals("opted", id)
                            DirectFeedResolveResult(tip = feedTip, persisted = true)
                        },
                        feedNetworkDelayMs = 0L,
                    ),
            )
            assertEquals("-1", saved["opted"])
            assertEquals(1, networkCalls)
            assertEquals(listOf(listOf("plain", "rss:other")), piSynced)
            assertTrue(saved.containsKey("plain"))
            assertTrue(saved.containsKey("rss:other"))
        }

    @Test
    fun syncSubscribedLatestEpisodes_promotesCachedTipThenRefreshesNetwork() =
        runTest {
            val cached =
                cx.aswin.boxlore.core.model.Episode(
                    id = "-42",
                    title = "Cached tip",
                    description = "",
                    audioUrl = "https://example.com/c.mp3",
                    imageUrl = null,
                    publishedDate = 200L,
                    duration = 60,
                    podcastId = "opted",
                )
            var networkCalls = 0
            val saved = mutableMapOf<String, String>()
            SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
                loadIds = { setOf("opted") },
                loadPodcastMeta = {
                    DirectFeedTipMeta(
                        feedUrl = "https://feeds.example/x.xml",
                        title = "opted",
                        imageUrl = null,
                        genre = null,
                        artist = null,
                        knownTip = cached.copy(id = "old-pi", publishedDate = 50L),
                    )
                },
                syncChunk = { error("PI should not run for only opted-in") },
                saveLatest = { id, ep -> saved[id] = ep.id },
                directFeed =
                    DirectFeedSyncSeams(
                        loadOptedInIds = { setOf("opted") },
                        loadCachedFeedTip = { cached },
                        resolveFeedTip = { _, _ ->
                            networkCalls++
                            DirectFeedResolveResult(tip = cached, persisted = true)
                        },
                        feedNetworkDelayMs = 0L,
                    ),
            )
            assertEquals("-42", saved["opted"])
            assertEquals(1, networkCalls)
        }

    @Test
    fun syncSubscribedLatestEpisodes_loadIdsFailureSkipsWork() =
        runTest {
            var syncCalls = 0
            var saveCalls = 0
            SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
                loadIds = { error("datastore down") },
                syncChunk = {
                    syncCalls++
                    emptyMap()
                },
                saveLatest = { _, _ -> saveCalls++ },
            )
            assertEquals(0, syncCalls)
            assertEquals(0, saveCalls)
        }

    @Test
    fun `syncSubscribedLatestEpisodes caps overlapping feed resolves`() =
        runTest {
            var inFlight = 0
            var maxInFlight = 0
            val order = mutableListOf<String>()
            val ids = (1..8).map { "p$it" }
            SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
                loadIds = { ids.toSet() },
                loadPodcastMeta = { id ->
                    DirectFeedTipMeta(
                        feedUrl = "https://feeds.example/$id.xml",
                        title = id,
                        imageUrl = null,
                        genre = null,
                        artist = null,
                        knownTip = null,
                    )
                },
                syncChunk = { emptyMap() },
                saveLatest = { _, _ -> },
                directFeed =
                    DirectFeedSyncSeams(
                        loadOptedInIds = { ids.toSet() },
                        resolveFeedTip = { id, _ ->
                            order += id
                            inFlight++
                            maxInFlight = maxOf(maxInFlight, inFlight)
                            delay(50)
                            inFlight--
                            DirectFeedResolveResult(
                                tip = episode(id = "e_$id", podcastId = id),
                                persisted = true,
                            )
                        },
                        feedConcurrency = 6,
                        preferredPodcastId = { "p5" },
                    ),
            )
            advanceTimeBy(1_000)
            runCurrent()
            assertEquals(6, maxInFlight)
            assertEquals("p5", order.first())
            assertEquals(8, order.size)
        }

    @Test
    fun `syncSubscribedLatestEpisodes isolates one feed failure`() =
        runTest {
            val saved = mutableListOf<String>()
            val refreshed = mutableListOf<String>()
            SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
                loadIds = { setOf("ok", "boom", "also") },
                loadPodcastMeta = { id ->
                    DirectFeedTipMeta(
                        feedUrl = "https://feeds.example/$id.xml",
                        title = id,
                        imageUrl = null,
                        genre = null,
                        artist = null,
                        knownTip = null,
                    )
                },
                syncChunk = { emptyMap() },
                saveLatest = { id, _ -> saved += id },
                directFeed =
                    DirectFeedSyncSeams(
                        loadOptedInIds = { setOf("ok", "boom", "also") },
                        resolveFeedTip = { id, _ ->
                            if (id == "boom") error("feed down")
                            DirectFeedResolveResult(
                                tip = episode(id = "e_$id", podcastId = id),
                                persisted = true,
                            )
                        },
                        onFeedRefreshed = { refreshed += it },
                    ),
            )
            assertEquals(setOf("ok", "also"), saved.toSet())
            assertEquals(setOf("ok", "also"), refreshed.toSet())
        }

    @Test
    fun requestRefreshFetchesImmediatelyWithoutWaitingForHomeDelay() =
        runTest {
            var runs = 0
            val sync =
                SubscriptionForegroundSync(
                    scope = this,
                    initialDelayMs = 2_000L,
                    syncAction = { runs++ },
                    periodicIntervalMs = 0L,
                    cooldownMs = 5_000L,
                    nowMs = { testScheduler.currentTime },
                )
            sync.ensureStarted()
            sync.requestRefresh()
            advanceUntilIdle()
            assertEquals(1, runs)
        }

    @Test
    fun requestRefreshSkipsDuringCooldownThenRunsAgain() =
        runTest {
            var runs = 0
            val sync =
                SubscriptionForegroundSync(
                    scope = this,
                    initialDelayMs = 0L,
                    syncAction = { runs++ },
                    periodicIntervalMs = 0L,
                    cooldownMs = 5_000L,
                    nowMs = { testScheduler.currentTime },
                )
            sync.requestRefresh()
            advanceUntilIdle()
            assertEquals(1, runs)

            sync.requestRefresh()
            advanceUntilIdle()
            assertEquals(1, runs)

            advanceTimeBy(5_000L)
            sync.requestRefresh()
            advanceUntilIdle()
            assertEquals(2, runs)
        }

    @Test
    fun requestRefreshCoalescesWhileSyncIsInFlight() =
        runTest {
            var runs = 0
            val sync =
                SubscriptionForegroundSync(
                    scope = this,
                    initialDelayMs = 0L,
                    syncAction = {
                        runs++
                        delay(1_000L)
                    },
                    periodicIntervalMs = 0L,
                    cooldownMs = 0L,
                    nowMs = { testScheduler.currentTime },
                )
            sync.requestRefresh()
            runCurrent()
            sync.requestRefresh()
            sync.requestRefresh()
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(1, runs)
        }

    @Test
    fun periodicLoopRefreshesAfterInterval() =
        runTest {
            var runs = 0
            val sync =
                SubscriptionForegroundSync(
                    scope = backgroundScope,
                    initialDelayMs = 0L,
                    syncAction = { runs++ },
                    periodicIntervalMs = 1_000L,
                    cooldownMs = 0L,
                    nowMs = { testScheduler.currentTime },
                )
            sync.ensureStarted()
            runCurrent()
            assertEquals(1, runs)
            advanceTimeBy(1_000L)
            runCurrent()
            assertEquals(2, runs)
        }

    @Test
    fun recoverMissingFeedUrlsIsolatesOrdinaryFailures() =
        runTest {
            val recovered = mutableListOf<String>()
            SubscriptionForegroundSync.recoverMissingFeedUrls(
                ids = setOf("ok", "rss:skip", "bad", "also-ok"),
                concurrency = 2,
            ) { id ->
                if (id == "bad") error("lookup failed")
                recovered += id
            }
            assertEquals(setOf("ok", "also-ok"), recovered.toSet())
        }

    @Test
    fun recoverMissingFeedUrlsRethrowsCancellation() =
        runTest {
            org.junit.jupiter.api.assertThrows<kotlinx.coroutines.CancellationException> {
                SubscriptionForegroundSync.recoverMissingFeedUrls(
                    ids = setOf("a", "b"),
                    concurrency = 1,
                ) { id ->
                    if (id == "b") throw kotlinx.coroutines.CancellationException("cancelled")
                }
            }
        }

    private fun episode(
        id: String,
        podcastId: String,
    ) = cx.aswin.boxlore.core.model.Episode(
        id = id,
        title = "T",
        description = "",
        audioUrl = "https://example.com/a.mp3",
        imageUrl = null,
        publishedDate = 1L,
        duration = 60,
        podcastId = podcastId,
    )
}
