package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
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
            assertEquals(listOf("a", "c"), saved)
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
                loadOptedInIds = { setOf("opted") },
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
                loadCachedFeedTip = { null },
                resolveFeedTip = { id, _ ->
                    networkCalls++
                    assertEquals("opted", id)
                    feedTip
                },
                syncChunk = { chunk ->
                    piSynced += chunk
                    chunk.associateWith { feedTip.copy(id = "pi_$it", podcastId = it) }
                },
                saveLatest = { id, ep -> saved[id] = ep.id },
                chunkSize = 10,
                feedNetworkDelayMs = 0L,
            )
            assertEquals("-1", saved["opted"])
            assertEquals(1, networkCalls)
            assertEquals(listOf(listOf("plain", "rss:other")), piSynced)
            assertTrue(saved.containsKey("plain"))
            assertTrue(saved.containsKey("rss:other"))
        }

    @Test
    fun syncSubscribedLatestEpisodes_promotesCachedTipWithoutNetworkWhenCurrent() =
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
                loadOptedInIds = { setOf("opted") },
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
                loadCachedFeedTip = { cached },
                resolveFeedTip = { _, _ ->
                    networkCalls++
                    cached
                },
                syncChunk = { error("PI should not run for only opted-in") },
                saveLatest = { id, ep -> saved[id] = ep.id },
                feedNetworkDelayMs = 0L,
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
}
