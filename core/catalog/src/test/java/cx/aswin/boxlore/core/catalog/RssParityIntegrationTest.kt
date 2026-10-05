package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshReason
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import cx.aswin.boxlore.core.rss.ParsedRssFeed
import cx.aswin.boxlore.core.rss.RssFeedClient
import cx.aswin.boxlore.core.rss.RssFetchResult
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RssParityIntegrationTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var repository: RssPodcastRepository
    private val feed = Feed()
    private val id = "rss:saved-identity"
    private val url = "https://publisher.example/private?token=secret"
    private var callbacks = 0

    @Before fun setup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        repository = RssPodcastRepository.createForTests(context, database, feed)
        repository.episodeCatalog.onCatalogPersisted = { callbacks++ }
        database.podcastDao().upsert(
            PodcastEntity(
                id,
                "Saved",
                "Author",
                "",
                null,
            sourceType = PodcastEntity.SOURCE_RSS,
                isSubscribed = true,
                feedUrl = url
            )
        )
        feed.rows = listOf(row("-1", "release", 100))
    }

    @After fun teardown() {
        database.close()
    }

    private fun row(episodeId: String, guid: String, date: Long) = RssEpisodeEntity(
        episodeId, id, guid, guid, "", "https://audio.example/$guid", null, 10, date,
        null, null, null, null, null, null, null, "audio/mpeg",
    )

    private fun request(reason: RefreshReason = RefreshReason.NORMAL, canProceed: suspend () -> Boolean = { true }) =
        RefreshRequest(id, url, reason = reason, canProceed = canProceed)

    @Test fun existingImportsRepairInPlaceAndPreserveStoredEpisodeIdsAndSettings() = runBlocking {
        database.rssEpisodeDao().upsertAll(listOf(row("-99", "release", 100), row("-88", "archive", 1)))
        val old = database.podcastDao().getPodcast(id)!!
        database.podcastDao().upsert(old.copy(autoDownloadEnabled = true, preferredSort = "oldest", type = "serial", customGenre = "My genre"))
        val outcome = repository.episodeCatalog.refresh(request())
        assertTrue(outcome is RefreshOutcome.Success)
        assertEquals("-99", repository.episodeCatalog.findByCatalogKey(id, "release", null)!!.id)
        assertNotNull(database.rssEpisodeDao().getEpisode("-88"))
        val stored = database.podcastDao().getPodcast(id)!!
        assertEquals(id, stored.podcastId)
        assertNull(stored.linkedPodcastIndexId)
        assertEquals(PodcastEntity.RSS_REFRESH_AUTOMATIC, stored.rssRefreshCapability)
        assertTrue(stored.autoDownloadEnabled)
        assertEquals("oldest", stored.preferredSort)
        assertEquals("serial", stored.type)
        assertEquals("My genre", stored.customGenre)
        assertEquals(1, callbacks)
    }

    @Test fun quietIntervalSkipsNetworkAndDueFeedUsesConditionalGet() = runBlocking {
        repository.episodeCatalog.refresh(request())
        assertTrue(repository.episodeCatalog.refresh(request()) is RefreshOutcome.Unchanged)
        assertEquals(1, feed.gets)
        makeDue()
        feed.unchanged = true
        assertTrue(repository.episodeCatalog.refresh(request()) is RefreshOutcome.Unchanged)
        assertEquals(1, feed.conditionals)
        assertEquals(1, callbacks)
        assertTrue(database.podcastDao().getPodcast(id)!!.lastRssSyncAt > 0)
    }

    @Test fun anchoredRssPlaybackContinuesForwardRegardlessOfListSort() = runBlocking {
        database.rssEpisodeDao().upsertAll(listOf(row("-1", "first", 1), row("-2", "middle", 2), row("-3", "next", 3)))
        for (sort in listOf("oldest", "newest")) {
            assertEquals(listOf("-2", "-3"), repository.episodeCatalog.getWindow(id, sort, 10, "-2").map { it.id })
            assertEquals(listOf("-2"), repository.episodeCatalog.getWindow(id, sort, 1, "-2").map { it.id })
        }
        assertEquals(listOf("-3", "-2", "-1"), repository.episodeCatalog.getWindow(id, "newest", 10, null).map { it.id })
        assertTrue(repository.episodeCatalog.search(id, "   ").isEmpty())
    }

    @Test fun feedsWithoutValidatorsFetchNewEpisodesAndMetadataChangesDoNotCreateReleaseBadge() = runBlocking {
        repository.episodeCatalog.refresh(request())
        makeDue()
        feed.rows = listOf(row("-123", "release", 100).copy(description = "edited"))
        repository.episodeCatalog.refresh(request())
        assertFalse(database.podcastDao().getPodcast(id)!!.rssHasNewEpisodes)
        makeDue()
        feed.rows = listOf(row("-2", "new", 200), row("-123", "release", 100))
        repository.episodeCatalog.refresh(request())
        assertEquals("-2", database.podcastDao().getPodcast(id)!!.latestEpisode!!.id)
        assertTrue(database.podcastDao().getPodcast(id)!!.rssHasNewEpisodes)
        assertEquals(2, feed.conditionals)
    }

    @Test fun manualAndPushBypassCooldownAndBackgroundDoesNotInvokeOrdinaryCallback() = runBlocking {
        repository.episodeCatalog.refresh(request())
        repository.episodeCatalog.refresh(request(RefreshReason.MANUAL))
        repository.episodeCatalog.refresh(request(RefreshReason.NEW_RELEASE))
        makeDue()
        repository.episodeCatalog.refresh(request(RefreshReason.AUTO_DOWNLOAD))
        assertEquals(3, feed.gets)
        assertEquals(1, feed.conditionals)
        assertEquals(3, callbacks)
    }

    @Test fun concurrentSettingsChangeSurvivesRefresh() = runBlocking {
        feed.onGet = {
            val current = database.podcastDao().getPodcast(id)!!
            database.podcastDao().upsert(current.copy(autoDownloadEnabled = true, preferredSort = "oldest"))
        }
        repository.episodeCatalog.refresh(request())
        assertTrue(database.podcastDao().getPodcast(id)!!.autoDownloadEnabled)
        assertEquals("oldest", database.podcastDao().getPodcast(id)!!.preferredSort)
    }

    @Test fun unsubscribeDuringFetchCannotResurrectSubscriptionOrCatalog() = runBlocking {
        feed.onGet = {
            val current = database.podcastDao().getPodcast(id)!!
            database.podcastDao().upsert(current.copy(isSubscribed = false))
        }
        assertTrue(repository.episodeCatalog.refresh(request()) is RefreshOutcome.Failure)
        assertFalse(database.podcastDao().getPodcast(id)!!.isSubscribed)
        assertEquals(0, database.rssEpisodeDao().count(id))
        assertEquals(0, callbacks)
    }

    @Test fun revokedConsentBeforePersistKeepsLastGoodCatalog() = runBlocking {
        var allowed = true
        feed.onGet = { allowed = false }
        assertTrue(repository.episodeCatalog.refresh(request(canProceed = { allowed })) is RefreshOutcome.Failure)
        assertEquals(0, database.rssEpisodeDao().count(id))
    }

    @Test fun cancellationPropagatesAndDoesNotBecomeSuccessfulRefresh() = runBlocking {
        feed.onGet = { throw CancellationException("cancelled") }
        try {
            repository.refreshCatalog(id)
            fail("Cancellation must propagate")
        } catch (_: CancellationException) {
            assertEquals(0, database.rssEpisodeDao().count(id))
            assertTrue(repository.refreshingPodcastIds.value.isEmpty())
        }
    }

    @Test fun invalidOrEmptyFeedKeepsLastGoodRowsAndRetries() = runBlocking {
        repository.episodeCatalog.refresh(request())
        feed.rows = emptyList()
        assertTrue(repository.episodeCatalog.refresh(request(RefreshReason.MANUAL)) is RefreshOutcome.Failure)
        assertEquals("-1", database.podcastDao().getPodcast(id)!!.latestEpisode!!.id)
        assertNotNull(database.rssEpisodeDao().getEpisode("-1"))
    }

    @Test fun restoreRetainsOriginalPodcastIdAcrossRedirects() = runBlocking {
        feed.finalUrl = "https://publisher.example/moved"
        val restored = repository.restoreSubscription(url, id)
        assertEquals(id, restored.podcast.id)
        assertEquals(id, database.rssEpisodeDao().getEpisode("-1")!!.podcastId)
        assertEquals(1, database.podcastDao().getSubscribedRssPodcasts().size)
    }

    @Test fun consentIsBoundToUrlAndIsNotPresentInAnotherDeviceStore() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val firstFile = File(context.noBackupFilesDir, "rss-consent-test-${System.nanoTime()}")
        val secondFile = File(context.noBackupFilesDir, "rss-consent-test-${System.nanoTime()}")
        try {
            val consent = DeviceRssNotificationConsent(firstFile)
            assertFalse(consent.isAccepted(id, url))
            consent.accept(id, url)
            assertTrue(DeviceRssNotificationConsent(firstFile).isAccepted(id, url))
            assertEquals(consent.registrationId, DeviceRssNotificationConsent(firstFile).registrationId)
            assertFalse(consent.registrationId == DeviceRssNotificationConsent(secondFile).registrationId)
            assertFalse(consent.isAccepted(id, "$url-new"))
            assertFalse(DeviceRssNotificationConsent(secondFile).isAccepted(id, url))
            assertFalse(firstFile.readText().contains("secret"))
            consent.revoke(id)
            assertFalse(consent.isAccepted(id, url))
        } finally {
            firstFile.delete()
            secondFile.delete()
        }
    }

    @Test fun notificationEnableRequiresExplicitConsentAndUrlChangesRevokeEffectivePermission() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.noBackupFilesDir, "rss-consent-enable-${System.nanoTime()}")
        try {
            val subscriptions = SubscriptionRepository(database.podcastDao(), autoDownloadDao = database.autoDownloadDao(), rssNotificationConsent = DeviceRssNotificationConsent(file))
            repository.refreshCatalog(id).getOrThrow()
            val show = database.podcastDao().getPodcast(id)!!.toPodcast()
            subscriptions.setAutoDownloadEnabled(id, true)
            assertFalse(subscriptions.setNotificationsEnabled(show, true))
            assertTrue(subscriptions.setNotificationsEnabled(show, true, acceptRssDisclosure = true, disclosedFeedUrl = url))
            assertTrue(database.podcastDao().getPodcast(id)!!.notificationsEnabled)
            assertFalse(subscriptions.setNotificationsEnabled(show, false))
            assertTrue(database.podcastDao().getPodcast(id)!!.autoDownloadEnabled)
            assertFalse(subscriptions.setNotificationsEnabled(show, true))
            assertTrue(subscriptions.setNotificationsEnabled(show, true, acceptRssDisclosure = true, disclosedFeedUrl = url))
            database.podcastDao().setFeedUrl(id, "$url-changed")
            subscriptions.reconcileFcmTopicSubscriptions()
            assertFalse(database.podcastDao().getPodcast(id)!!.notificationsEnabled)
            assertTrue(database.podcastDao().getPodcast(id)!!.autoDownloadEnabled)
        } finally {
            file.delete()
        }
    }

    @Test fun canonicalRssIdentityCannotBypassDisclosureWithStaleSourceMetadata() = runBlocking {
        val old = database.podcastDao().getPodcast(id)!!
        database.podcastDao().upsert(old.copy(sourceType = PodcastEntity.SOURCE_PODCAST_INDEX, autoDownloadEnabled = true))
        val subscriptions = SubscriptionRepository(database.podcastDao())
        assertFalse(subscriptions.setNotificationsEnabled(database.podcastDao().getPodcast(id)!!.toPodcast(), true))
        assertFalse(database.podcastDao().getPodcast(id)!!.notificationsEnabled)
        assertTrue(database.podcastDao().getPodcast(id)!!.autoDownloadEnabled)
    }

    @Test fun clearedLibraryDuringDisclosureAcceptanceCannotRegisterTheDeletedShow() = runBlocking {
        val consent = object : RssNotificationConsent {
            override fun isAccepted(podcastId: String, feedUrl: String) = true
            override fun accept(podcastId: String, feedUrl: String) = database.clearAllTables()
            override fun revoke(podcastId: String) = Unit
        }
        val subscriptions = SubscriptionRepository(database.podcastDao(), rssNotificationConsent = consent)
        val show = database.podcastDao().getPodcast(id)!!.toPodcast()
        assertFalse(subscriptions.setNotificationsEnabled(show, true, acceptRssDisclosure = true, disclosedFeedUrl = url))
        assertNull(database.podcastDao().getPodcast(id))
    }

    private suspend fun makeDue() {
        val row = database.podcastDao().getPodcast(id)!!
        database.podcastDao().upsert(row.copy(lastRssSyncAt = System.currentTimeMillis() - 21_600_001))
    }

    private class Feed : RssFeedClient() {
        var rows = emptyList<RssEpisodeEntity>()
        var gets = 0
        var conditionals = 0
        var unchanged = false
        var finalUrl = "https://publisher.example/private?token=secret"
        var onGet: suspend () -> Unit = {}
        override suspend fun fetch(url: String): RssFetchResult {
            gets++
            onGet()
            return RssFetchResult(finalUrl, null, null, byteArrayOf())
        }
        override suspend fun fetchConditional(url: String, etag: String?, lastModified: String?): RssFetchResult? {
            conditionals++
            onGet()
            return if (unchanged) null else RssFetchResult(finalUrl, null, null, byteArrayOf())
        }
        override suspend fun parse(feedUrl: String, bytes: ByteArray, podcastId: String): ParsedRssFeed =
            ParsedRssFeed("Show", "Author", null, null, null, "episodic", null, null, rows.map { it.copy(podcastId = podcastId) })
    }
}
