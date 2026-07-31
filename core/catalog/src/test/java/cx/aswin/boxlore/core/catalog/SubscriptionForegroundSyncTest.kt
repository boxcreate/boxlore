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
