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
        enqueue = { _, episodeId, _ ->
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
        database.localEpisodeCatalogDao().upsertFeed(stored.copy(fetchedAt = System.currentTimeMillis() - 3_600_001))
    }

    @Test fun missingPushIsRecoveredInsideSixHourQuietPeriodWithoutPodcastIndex() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        assertTrue(coordinator.discover())
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
        coordinator.discover()
        assertTrue(enqueued.isEmpty())
    }

    @Test fun durablePendingClaimRecoversAfterEnqueueFailureAndProcessRecreation() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        enqueueFails = true
        assertFalse(coordinator.discover())
        val id = catalog.findByCatalogKey("100", "release", null)!!.id
        assertEquals(AutoDownloadReleaseEntity.PENDING, database.autoDownloadDao().getRelease(id)?.state)
        enqueueFails = false
        assertTrue(coordinator().discover())
        assertEquals(listOf(id), enqueued)
    }

    @Test fun completedOrDeliberatelyRemovedReleaseDoesNotReplay() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        coordinator.discover()
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
        assertTrue(coordinator.discover())
        assertTrue(enqueued.isEmpty())
        assertNull(database.autoDownloadDao().getShow("100"))
    }

    @Test fun publisherFailureKeepsPendingForRetry() = runBlocking {
        val coordinator = coordinator()
        coordinator.synchronizeSubscriptions()
        release()
        coordinator.discover()
        val id = enqueued.single()
        feed.fail = true
        val stored = database.localEpisodeCatalogDao().getFeed("100")!!
        database.localEpisodeCatalogDao().upsertFeed(stored.copy(fetchedAt = 1))
        assertFalse(coordinator.discover())
        assertEquals(AutoDownloadReleaseEntity.PENDING, database.autoDownloadDao().getRelease(id)?.state)
    }

    private class Feed : RssFeedClient() {
        var items = emptyList<Pair<String, Long>>()
        var fetches = 0
        var fail = false
        override suspend fun fetch(url: String): RssFetchResult {
            fetches++
            if (fail) throw java.io.IOException("offline")
            val xml = "<rss version=\"2.0\"><channel><title>Show</title>" + items.joinToString("") { (id, date) ->
                val pubDate = DateTimeFormatter.RFC_1123_DATE_TIME.format(Instant.ofEpochSecond(date).atZone(ZoneOffset.UTC))
                "<item><guid>$id</guid><title>$id</title><pubDate>$pubDate</pubDate><enclosure url=\"https://cdn.example/$id.mp3\" type=\"audio/mpeg\"/></item>"
            } + "</channel></rss>"
            return RssFetchResult(url, null, null, xml.toByteArray())
        }
    }
    companion object {
        const val URL = "https://feeds.example/show.xml"
    }
}
