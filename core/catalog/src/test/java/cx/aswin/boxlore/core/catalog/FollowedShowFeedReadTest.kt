package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.testing.TestFixtures
import cx.aswin.boxlore.core.testing.fakes.FakeLocalEpisodeCatalogPort
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FollowedShowFeedReadTest {
    private val meta = DirectFeedTipMeta("https://feeds.example/daily.xml", "The Daily", null, "News", "Host", null)
    private val fresh = TestFixtures.episode(id = "fresh", podcastId = "daily")

    @Test
    fun `a chip read is a manual read past the six-hour wait`() = runTest {
        // Before, selecting a show read within six hours changed nothing. A manual read skips the wait
        // (shouldSkipQuiet in :core:rss) and never consults the unchanged-feed shortcut.
        val catalog =
            FakeLocalEpisodeCatalogPort().apply {
                publisherFeedUnchanged = true
                refreshOutcome = LocalEpisodeCatalogPort.RefreshOutcome.Success(fresh, itemCount = 1, ready = true)
            }

        val read = FollowedShowFeedRead.readPastWait("daily", meta, catalog)

        assertEquals(1, catalog.refreshCalls)
        assertEquals(LocalEpisodeCatalogPort.RefreshReason.MANUAL, catalog.lastRefreshRequest?.reason)
        assertEquals("https://feeds.example/daily.xml", catalog.lastRefreshRequest?.feedUrl)
        assertEquals("The Daily", catalog.lastRefreshRequest?.meta?.title)
        assertTrue(read.persisted)
        assertEquals(fresh, read.tip)
    }

    @Test
    fun `Home's chip read goes past the six-hour wait`() = runTest {
        // The read readNow passes to the ingest; FollowedShowFeedReadNowTest checks readNow itself.
        val catalog =
            FakeLocalEpisodeCatalogPort().apply {
                publisherFeedUnchanged = true
                refreshOutcome = LocalEpisodeCatalogPort.RefreshOutcome.Success(fresh, itemCount = 1, ready = true)
            }

        val read = FollowedShowFeedRead.chipRefresh(catalog)("daily", meta)

        assertEquals(1, catalog.refreshCalls)
        assertEquals(LocalEpisodeCatalogPort.RefreshReason.MANUAL, catalog.lastRefreshRequest?.reason)
        assertTrue(read.persisted)
        assertEquals(fresh, read.tip)
    }

    @Test
    fun `an ingest that is not a chip read never makes a manual read`() = runTest {
        val catalog =
            FakeLocalEpisodeCatalogPort().apply {
                refreshOutcome = LocalEpisodeCatalogPort.RefreshOutcome.Success(fresh, itemCount = 1, ready = true)
            }

        SubscriptionForegroundSyncIngest.catalogRefresh(catalog, pastWait = false)("daily", meta)

        assertNotEquals(LocalEpisodeCatalogPort.RefreshReason.MANUAL, catalog.lastRefreshRequest?.reason)
    }

    @Test
    fun `a chip read without a local catalog reads nothing`() = runTest {
        val read = FollowedShowFeedRead.chipRefresh(null)("daily", meta)

        assertFalse(read.persisted)
        assertNull(read.tip)
    }

    @Test
    fun `the read's newest episode becomes the show's latest`() = runTest {
        val catalog =
            FakeLocalEpisodeCatalogPort().apply {
                refreshOutcome = LocalEpisodeCatalogPort.RefreshOutcome.Success(fresh, itemCount = 1, ready = true)
            }
        val saved = mutableMapOf<String, String>()
        var piCalls = 0

        SubscriptionForegroundSyncIngest.ingestSubscribedLocalCatalog(
            podcastId = "daily",
            isSubscribed = { true },
            loadMeta = { meta },
            refreshCatalog = FollowedShowFeedRead.chipRefresh(catalog),
            saveLatest = { id, episode -> saved[id] = episode.id },
            markUnsubscribedTtl = {},
            syncPiTip = { piCalls++ },
            onFeedRefreshed = {},
        )

        assertEquals(mapOf("daily" to "fresh"), saved)
        assertEquals(0, piCalls)
    }

    @Test
    fun `a failed read reports what it saw and leaves no tip`() = runTest {
        val catalog =
            FakeLocalEpisodeCatalogPort().apply {
                refreshOutcome = LocalEpisodeCatalogPort.RefreshOutcome.Failure("gone", addressGone = true, reportStatus = "http_404")
            }

        val read = FollowedShowFeedRead.readPastWait("daily", meta, catalog)

        assertTrue(read.failed)
        assertTrue(read.addressGone)
        assertEquals("http_404", read.reportStatus)
        assertFalse(read.persisted)
        assertNull(read.tip)
    }

    @Test
    fun `an unchanged feed keeps the stored newest episode without persisting`() = runTest {
        val catalog =
            FakeLocalEpisodeCatalogPort().apply {
                refreshOutcome = LocalEpisodeCatalogPort.RefreshOutcome.Unchanged(fresh)
            }

        val read = FollowedShowFeedRead.readPastWait("daily", meta, catalog)

        assertFalse(read.persisted)
        assertEquals(fresh, read.tip)
    }
}
