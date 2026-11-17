package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshReason
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** How a [SubscriptionForegroundSync] pass reads a followed show's catalog and falls back to the server. */
class SubscriptionForegroundSyncCatalogReadTest {
    /**
     * Regression: the fallback went through the library `/sync` path, which leaves out every show
     * whose local catalog is ready, so a followed show whose own feed kept failing never reached
     * the server.
     */
    @Test
    fun `a ready show whose own feed fails is asked from the server directly and its tip saved`() = runTest {
        val libraryChunks = mutableListOf<String>()
        val serverAsked = mutableListOf<String>()
        val saved = mutableMapOf<String, String>()
        SubscriptionForegroundSync.syncSubscribedLatestEpisodes(
            loadIds = { setOf("ready") },
            loadPodcastMeta = { id -> DirectFeedTipMeta("https://feeds.example/$id.xml", id, null, null, null, null) },
            syncChunk = { chunk ->
                libraryChunks += chunk
                emptyMap()
            },
            saveLatest = { id, ep -> saved[id] = ep.id },
            directFeed =
            DirectFeedSyncSeams(
                loadOptedInIds = { setOf("ready") },
                loadReadyIds = { setOf("ready") },
                resolveFeedTip = { _, _ -> DirectFeedResolveResult(tip = null, persisted = false, failed = true) },
                syncFallbackEnabled = { true },
                fetchServerTips = { ids ->
                    serverAsked += ids
                    ids.associateWith { episode(id = "server_$it", podcastId = it) }
                },
            ),
        )
        assertEquals(listOf("ready"), serverAsked)
        assertTrue(libraryChunks.isEmpty())
        assertEquals("server_ready", saved["ready"])
    }

    @Test
    fun `a catalog read inside a pull skips the quiet wait and is never forced`() = runTest {
        val meta = DirectFeedTipMeta("https://feeds.example/feed.xml", "Show", null, null, null, null)
        suspend fun reason(): RefreshReason? {
            val catalog = FakeLocalEpisodeCatalogPort(readyIds = setOf("123"))
            SubscriptionForegroundSync.resolveCatalogRefresh("123", meta, catalog)
            return catalog.lastRefreshRequest?.reason
        }

        assertEquals(RefreshReason.NORMAL, reason())
        assertEquals(
            RefreshReason.PULL,
            withContext(ManualRefreshPass()) { reason() },
        )
    }

    /** Regression: an unchanged answer was taken before the catalog's refresh, so the check was never recorded. */
    @Test
    fun `a catalog read always goes through the catalog's own refresh so an unchanged answer is recorded`() = runTest {
        val catalog =
            FakeLocalEpisodeCatalogPort(readyIds = setOf("123")).apply {
                publisherFeedUnchanged = true
            }
        val result = SubscriptionForegroundSync.resolveCatalogRefresh("123", DirectFeedTipMeta("https://feeds.example/feed.xml", "Show", null, null, null, null), catalog)
        assertEquals(1, catalog.refreshCalls)
        assertFalse(result.failed)
        assertFalse(result.persisted)
    }

    private fun episode(id: String, podcastId: String) = Episode(
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
