package cx.aswin.boxlore.core.downloads

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.AutoDownloadReleaseEntity
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import cx.aswin.boxlore.core.rss.RssFeedClient
import cx.aswin.boxlore.core.rss.RssFetchResult
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoDownloadCoordinatorTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var catalog: LocalEpisodeCatalogRepository
    private lateinit var prefs: UserPreferencesRepository
    private val feed = Feed()
    private val enqueued = mutableListOf<String>()
    private var baselineCalls = 0
    private var enqueueFails = false
    private val now = System.currentTimeMillis() / 1000L

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        prefs = UserPreferencesRepository(context)
        prefs.setAutoDownloadMaxEpisodes(2)
        catalog = LocalEpisodeCatalogRepository.create(database, feed)
        database.podcastDao().upsert(
            PodcastEntity(
                "100", "Show", "Author", "", null,
            isSubscribed = true, autoDownloadEnabled = true, notificationsEnabled = false, feedUrl = URL
            )
        )
        feed.items = listOf("archive" to now - 86_400)
        catalog.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", URL))
        Unit
    }

    @After fun teardown() {
        database.close()
    }

    private fun coordinator() = AutoDownloadCoordinator(
        database,
        catalog,
        prefs,
        enqueue = { _, episodeId, _, _ ->
            if (enqueueFails) throw java.io.IOException("process stopped around enqueue")
            enqueued += episodeId
        },
        loadInitialBaseline = {
            baselineCalls++
            emptyList()
        },
        nowSeconds = { now }
    )

    private suspend fun release(id: String = "release") {
        feed.items = listOf(id to now + 60, "archive" to now - 86_400)
        val stored = database.localEpisodeCatalogDao().getFeed("100")!!
        database.localEpisodeCatalogDao().upsertFeed(stored.copy(fetchedAt = System.currentTimeMillis() - 21_600_001))
    }

    @Test fun optedInDiscoveryRecoversMissingPushAfterSharedSixHourCooldownWithoutPodcastIndex() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        assertTrue(coordinator.discover { true })
        val canonical = catalog.findByCatalogKey("100", "release", null)!!
        assertTrue(canonical.id.toLong() < 0)
        assertEquals(listOf(canonical.id), enqueued)
        assertEquals(0, baselineCalls)
        assertEquals(2, feed.fetches)
    }

    @Test fun foregroundPersistenceFindsReleaseWithoutPush() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        feed.items = listOf("release" to now + 60, "archive" to now - 86_400)
        catalog.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", URL, reason = LocalEpisodeCatalogPort.RefreshReason.NEW_RELEASE))
        coordinator.scanCached("100")
        assertEquals(listOf(catalog.findByCatalogKey("100", "release", null)!!.id), enqueued)
    }

    @Test fun activationDoesNotDownloadArchiveOrExistingFutureDatedItem() = runBlocking {
        feed.items = listOf("future-existing" to now + 3600, "archive" to now - 86_400)
        catalog.refresh(LocalEpisodeCatalogPort.RefreshRequest("100", URL, reason = LocalEpisodeCatalogPort.RefreshReason.NEW_RELEASE))
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        coordinator.discover { true }
        assertTrue(enqueued.isEmpty())
    }

    @Test fun durablePendingClaimRecoversAfterEnqueueFailureAndProcessRecreation() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        enqueueFails = true
        assertFalse(coordinator.discover { true })
        val id = catalog.findByCatalogKey("100", "release", null)!!.id
        assertEquals(AutoDownloadReleaseEntity.PENDING, database.autoDownloadDao().getRelease(id)?.state)
        enqueueFails = false
        assertTrue(coordinator().discover { true })
        assertEquals(listOf(id), enqueued)
    }

    @Test fun completedOrDeliberatelyRemovedReleaseDoesNotReplay() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        coordinator.discover { true }
        val id = enqueued.single()
        database.autoDownloadDao().finish(id)
        coordinator.scanCached("100")
        assertEquals(1, enqueued.size)
        database.autoDownloadDao().markRemoved(id)
        coordinator.acceptRelease("100", catalog.getEpisode(id)!!)
        coordinator.scanCached("100")
        assertEquals(1, enqueued.size)
        assertEquals(AutoDownloadReleaseEntity.REMOVED, database.autoDownloadDao().getRelease(id)?.state)
    }

    @Test fun disabledAndUnsubscribedShowsDoNotClaimReleases() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        val show = database.podcastDao().getPodcast("100")!!
        database.podcastDao().upsert(show.copy(isSubscribed = false))
        assertTrue(coordinator.discover { true })
        assertTrue(enqueued.isEmpty())
        assertNull(database.autoDownloadDao().getShow("100"))
    }

    @Test fun publisherFailureKeepsPendingForRetry() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        coordinator.discover { true }
        val id = enqueued.single()
        feed.fail = true
        val stored = database.localEpisodeCatalogDao().getFeed("100")!!
        database.localEpisodeCatalogDao().upsertFeed(stored.copy(fetchedAt = 1))
        assertFalse(coordinator.discover { true })
        assertEquals(AutoDownloadReleaseEntity.PENDING, database.autoDownloadDao().getRelease(id)?.state)
    }

    @Test fun defaultDiscoveryAndDeniedBackgroundScanCannotFetchOrEnqueue() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        assertTrue(coordinator.discover())
        assertTrue(coordinator.discover { false })
        coordinator.scanCached("100", background = true)
        assertEquals(1, feed.fetches)
        assertTrue(enqueued.isEmpty())
    }

    @Test fun backgroundPersistCannotEscapeThroughForegroundCallback() = runBlocking {
        var callbackCalls = 0
        catalog = LocalEpisodeCatalogRepository.create(database, feed, onCatalogPersisted = { callbackCalls++ })
        coordinator().synchronizeSubscriptions()
        release()
        assertTrue(coordinator().discover { true })
        assertEquals(0, callbackCalls)
        assertEquals(1, enqueued.size)
    }

    @Test fun revocationDuringFetchPreventsPersistenceAndEnqueue() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        var allowed = true
        feed.afterFetch = { allowed = false }
        assertFalse(coordinator.discover { allowed })
        assertNull(catalog.findByCatalogKey("100", "release", null))
        assertTrue(enqueued.isEmpty())
    }

    @Test fun forcedBackgroundMetadataRecoveryCannotInvokeOrdinaryDownloadCallback() = runBlocking {
        var callbacks = 0
        catalog = LocalEpisodeCatalogRepository.create(database, feed, onCatalogPersisted = { callbacks++ })
        release()
        val request = LocalEpisodeCatalogPort.RefreshRequest(
            "100",
            URL,
            reason = LocalEpisodeCatalogPort.RefreshReason.NEW_RELEASE,
            runPostPersistCallback = false,
        )
        assertTrue(catalog.refresh(request) is LocalEpisodeCatalogPort.RefreshOutcome.Success)
        assertEquals(0, callbacks)
        assertTrue(catalog.refresh(request.copy(reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL, runPostPersistCallback = true)) is LocalEpisodeCatalogPort.RefreshOutcome.Success)
        assertEquals(1, callbacks)
    }

    @Test fun pureRssUsesSameReleaseLedgerRetentionBoundAndNeverLoadsCatalogBaseline() = runBlocking {
        val id = "rss:private-show"
        val context = ApplicationProvider.getApplicationContext<Context>()
        val rss = cx.aswin.boxlore.core.rss.RssPodcastRepository.createForTests(context, database, feed)
        database.podcastDao().upsert(
            PodcastEntity(
                id, "Private", "Author", "", null,
            sourceType = PodcastEntity.SOURCE_RSS, isSubscribed = true, autoDownloadEnabled = true,
            notificationsEnabled = false, feedUrl = URL
            )
        )
        rss.refreshCatalog(id).getOrThrow()
        val shared = cx.aswin.boxlore.core.catalog.SubscribedEpisodeCatalog(catalog, rss.episodeCatalog)
        val coordinator = AutoDownloadCoordinator(
            database,
            shared,
            prefs,
            enqueue = { _, episodeId, _, _ -> enqueued += episodeId },
            recoverFeedUrl = { error("RSS must not look up a catalog URL") },
            loadInitialBaseline = { error("RSS must not load a catalog baseline") },
            nowSeconds = { now }
        )
        coordinator.synchronizeSubscriptions()
        val archive = rss.episodeCatalog.findByCatalogKey(id, "archive", null)!!
        assertEquals(cx.aswin.boxlore.core.database.AutoDownloadReleaseEntity.HANDLED, database.autoDownloadDao().getRelease(archive.id)!!.state)
        feed.items = listOf("newest" to now + 120, "next" to now + 60, "archive" to now - 86_400)
        val row = database.podcastDao().getPodcast(id)!!
        database.podcastDao().upsert(row.copy(lastRssSyncAt = System.currentTimeMillis() - 21_600_001))
        assertTrue(coordinator.discover { true })
        val newest = rss.episodeCatalog.findByCatalogKey(id, "newest", null)!!
        val next = rss.episodeCatalog.findByCatalogKey(id, "next", null)!!
        assertEquals(setOf(newest.id, next.id), enqueued.toSet())
        assertFalse(database.podcastDao().getPodcast(id)!!.notificationsEnabled)
        assertTrue(newest.id.toLong() < 0)
    }

    private class Feed : RssFeedClient() {
        var items = emptyList<Pair<String, Long>>()
        var fetches = 0
        var fail = false
        var afterFetch: () -> Unit = {}
        override suspend fun fetchConditional(url: String, etag: String?, lastModified: String?) = fetch(url)
        override suspend fun fetch(url: String): RssFetchResult {
            fetches++
            if (fail) throw java.io.IOException("offline")
            val xml = "<rss version=\"2.0\"><channel><title>Show</title>" + items.joinToString("") { (id, date) ->
                val pubDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(date).atZone(ZoneOffset.UTC))
                "<item><guid>$id</guid><title>$id</title><pubDate>$pubDate</pubDate><enclosure url=\"https://cdn.example/$id.mp3\" type=\"audio/mpeg\"/></item>"
            } + "</channel></rss>"
            afterFetch()
            return RssFetchResult(url, null, null, xml.toByteArray())
        }
    }
    companion object {
        const val URL = "https://feeds.example/show.xml"
    }
}
