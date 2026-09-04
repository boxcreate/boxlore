package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SubscriptionForegroundSyncIngestTest {
    @org.junit.jupiter.api.BeforeEach
    fun resetProcessGuard() {
        SubscriptionForegroundSync.resetProcessGuardForTests()
    }

    @Test
    fun ingestSubscribedLocalCatalogSkipsRssAndUnsubscribed() = runTest {
        var refreshCalls = 0
        val rss =
            SubscriptionForegroundSyncIngest.ingestSubscribedLocalCatalog(
                podcastId = "rss:abc",
                isSubscribed = { true },
                loadMeta = { error("should not load") },
                refreshCatalog = { _, _ ->
                    refreshCalls++
                    DirectFeedResolveResult(tip = null, persisted = false)
                },
                saveLatest = { _, _ -> error("should not save") },
                markUnsubscribedTtl = {},
                syncPiTip = {},
                onFeedRefreshed = {},
            )
        val unsubscribed =
            SubscriptionForegroundSyncIngest.ingestSubscribedLocalCatalog(
                podcastId = "123",
                isSubscribed = { false },
                loadMeta = { error("should not load") },
                refreshCatalog = { _, _ ->
                    refreshCalls++
                    DirectFeedResolveResult(tip = null, persisted = false)
                },
                saveLatest = { _, _ -> error("should not save") },
                markUnsubscribedTtl = {},
                syncPiTip = {},
                onFeedRefreshed = {},
            )
        assertFalse(rss)
        assertFalse(unsubscribed)
        assertEquals(0, refreshCalls)
    }

    @Test
    fun ingestSubscribedLocalCatalogFallsBackToPiWhenFeedMissing() = runTest {
        var piCalls = 0
        val persisted =
            SubscriptionForegroundSyncIngest.ingestSubscribedLocalCatalog(
                podcastId = "123",
                isSubscribed = { true },
                loadMeta = {
                    DirectFeedTipMeta(
                        feedUrl = "http://insecure.example/feed.xml",
                        title = "T",
                        imageUrl = null,
                        genre = null,
                        artist = null,
                        knownTip = null,
                    )
                },
                refreshCatalog = { _, _ -> error("no https feed") },
                saveLatest = { _, _ -> error("should not save") },
                markUnsubscribedTtl = {},
                syncPiTip = { piCalls++ },
                onFeedRefreshed = { error("should not emit") },
            )
        assertFalse(persisted)
        assertEquals(1, piCalls)
    }

    @Test
    fun ingestSubscribedLocalCatalogEmitsRefreshOnlyOnPersist() = runTest {
        val refreshed = mutableListOf<String>()
        val saved = mutableListOf<String>()
        val tip = episode(id = "e1", podcastId = "123")
        val persisted =
            SubscriptionForegroundSyncIngest.ingestSubscribedLocalCatalog(
                podcastId = "123",
                isSubscribed = { true },
                loadMeta = {
                    DirectFeedTipMeta(
                        feedUrl = "https://feeds.example/x.xml",
                        title = "T",
                        imageUrl = null,
                        genre = null,
                        artist = null,
                        knownTip = null,
                    )
                },
                refreshCatalog = { _, _ ->
                    DirectFeedResolveResult(tip = tip, persisted = true)
                },
                saveLatest = { id, _ -> saved += id },
                markUnsubscribedTtl = {},
                syncPiTip = { error("has tip") },
                onFeedRefreshed = { refreshed += it },
            )
        assertTrue(persisted)
        assertEquals(listOf("123"), refreshed)
        assertEquals(listOf("123"), saved)
    }

    @Test
    fun ingestSubscribedLocalCatalogReappliesTtlIfUnsubscribedDuringRefresh() = runTest {
        var subscribed = true
        var ttlMarks = 0
        val refreshed = mutableListOf<String>()
        val persisted =
            SubscriptionForegroundSyncIngest.ingestSubscribedLocalCatalog(
                podcastId = "123",
                isSubscribed = { subscribed },
                loadMeta = {
                    DirectFeedTipMeta(
                        feedUrl = "https://feeds.example/x.xml",
                        title = "T",
                        imageUrl = null,
                        genre = null,
                        artist = null,
                        knownTip = null,
                    )
                },
                refreshCatalog = { _, _ ->
                    subscribed = false
                    DirectFeedResolveResult(
                        tip = episode(id = "e1", podcastId = "123"),
                        persisted = true,
                    )
                },
                saveLatest = { _, _ -> error("should not save") },
                markUnsubscribedTtl = { ttlMarks++ },
                syncPiTip = {},
                onFeedRefreshed = { refreshed += it },
            )
        assertFalse(persisted)
        assertEquals(1, ttlMarks)
        assertEquals(emptyList<String>(), refreshed)
    }

    @Test
    fun ingestSubscribedLocalCatalogSyncsPiWhenRefreshHasNoTip() = runTest {
        var piCalls = 0
        val persisted =
            SubscriptionForegroundSyncIngest.ingestSubscribedLocalCatalog(
                podcastId = "123",
                isSubscribed = { true },
                loadMeta = {
                    DirectFeedTipMeta(
                        feedUrl = "https://feeds.example/x.xml",
                        title = "T",
                        imageUrl = null,
                        genre = null,
                        artist = null,
                        knownTip = null,
                    )
                },
                refreshCatalog = { _, _ ->
                    DirectFeedResolveResult(tip = null, persisted = false)
                },
                saveLatest = { _, _ -> error("should not save") },
                markUnsubscribedTtl = {},
                syncPiTip = { piCalls++ },
                onFeedRefreshed = { error("not persisted") },
            )
        assertFalse(persisted)
        assertEquals(1, piCalls)
    }

    @Test
    fun requestCatalogIngestRunsWhileRefreshIsInFlightAndDuringCooldown() = runTest {
        var ingestRuns = 0
        var syncRuns = 0
        val finished = mutableListOf<String>()
        val sync =
            SubscriptionForegroundSync(
                scope = this,
                initialDelayMs = 0L,
                syncAction = {
                    syncRuns++
                    delay(1_000L)
                },
                periodicIntervalMs = 0L,
                cooldownMs = 60_000L,
                nowMs = { testScheduler.currentTime },
                catalogIngestAction = { ingestRuns++ },
            )
        val collectJob =
            launch {
                sync.catalogIngestFinished.collect { finished += it }
            }
        sync.requestRefresh()
        runCurrent()
        sync.requestCatalogIngest("123")
        runCurrent()
        assertEquals(1, ingestRuns)
        assertEquals(listOf("123"), finished)
        advanceTimeBy(1_000L)
        runCurrent()
        assertEquals(1, syncRuns)
        sync.requestCatalogIngest("456")
        runCurrent()
        assertEquals(2, ingestRuns)
        assertEquals(listOf("123", "456"), finished)
        collectJob.cancel()
    }

    @Test
    fun requestCatalogIngestIgnoresRssIds() = runTest {
        var ingestRuns = 0
        val finished = mutableListOf<String>()
        val sync =
            SubscriptionForegroundSync(
                scope = this,
                initialDelayMs = 0L,
                syncAction = {},
                periodicIntervalMs = 0L,
                catalogIngestAction = { ingestRuns++ },
            )
        val collectJob =
            launch {
                sync.catalogIngestFinished.collect { finished += it }
            }
        runCurrent()
        sync.requestCatalogIngest("rss:abc")
        advanceUntilIdle()
        assertEquals(0, ingestRuns)
        assertEquals(emptyList<String>(), finished)
        collectJob.cancel()
    }

    @Test
    fun requestCatalogIngestEmitsFinishedAfterFailure() = runTest {
        val finished = mutableListOf<String>()
        val sync =
            SubscriptionForegroundSync(
                scope = this,
                initialDelayMs = 0L,
                syncAction = {},
                periodicIntervalMs = 0L,
                catalogIngestAction = { error("ingest failed") },
            )
        val collectJob =
            launch {
                sync.catalogIngestFinished.collect { finished += it }
            }
        runCurrent()
        sync.requestCatalogIngest("123")
        advanceUntilIdle()
        assertEquals(listOf("123"), finished)
        collectJob.cancel()
    }

    @Test
    fun sweepExpiredLocalCatalogsNoopsWhenCatalogMissing() = runTest {
        SubscriptionForegroundSyncIngest.sweepExpiredLocalCatalogs(catalog = null, nowMs = 9L)
    }

    @Test
    fun sweepExpiredLocalCatalogsInvokesPort() = runTest {
        val catalog = FakeLocalEpisodeCatalogPort()
        SubscriptionForegroundSyncIngest.sweepExpiredLocalCatalogs(catalog, nowMs = 42L)
        assertEquals(1, catalog.sweepCalls)
        assertEquals(42L, catalog.lastSweepNowMs)
    }

    @Test
    fun sweepExpiredLocalCatalogsIsolatesOrdinaryFailures() = runTest {
        val catalog =
            FakeLocalEpisodeCatalogPort().apply {
                sweepError = IllegalStateException("sweep failed")
            }
        SubscriptionForegroundSyncIngest.sweepExpiredLocalCatalogs(catalog, nowMs = 1L)
        assertEquals(1, catalog.sweepCalls)
    }

    @Test
    fun sweepExpiredLocalCatalogsRethrowsCancellation() = runTest {
        val catalog =
            FakeLocalEpisodeCatalogPort().apply {
                sweepError = kotlinx.coroutines.CancellationException("cancelled")
            }
        org.junit.jupiter.api.assertThrows<kotlinx.coroutines.CancellationException> {
            SubscriptionForegroundSyncIngest.sweepExpiredLocalCatalogs(catalog, nowMs = 1L)
        }
    }

    private fun episode(id: String, podcastId: String,) = cx.aswin.boxlore.core.model.Episode(
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
