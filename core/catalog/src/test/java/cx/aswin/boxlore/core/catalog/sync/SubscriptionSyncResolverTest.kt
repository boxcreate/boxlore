package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.FeedAddressUpdater
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.FeedAddressWrite
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.network.model.UserSubscriptionSyncDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionSyncResolverTest {

    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var fakeFolderRepository: FakeFolderRepository
    private lateinit var fakeNotificationPort: FakePodcastNotificationSyncPort
    private lateinit var resolver: SubscriptionSyncResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        podcastDao = database.podcastDao()
        fakeFolderRepository = FakeFolderRepository()
        fakeNotificationPort = FakePodcastNotificationSyncPort()
        resolver = SubscriptionSyncResolver(
            podcastDao = podcastDao,
            folderRepository = fakeFolderRepository,
            podcastRepository = null,
            rssPodcastRepository = null,
            notificationSyncPort = fakeNotificationPort,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resolveSubscription_remoteTombstoneWins_whenUnsubscribedAfterLocalSubscribed() = runTest {
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-1",
                title = "Local Show",
                isSubscribed = true,
                subscribedAt = 1000L,
                unsubscribedAt = 0L,
                isDirty = false,
                syncedAt = 500L,
                autoDownloadEnabled = true,
                notificationsEnabled = true,
                customGenre = "Tech",
            ),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-1",
            isSubscribed = false,
            subscribedAt = 1000L,
            unsubscribedAt = 2000L,
            updatedAt = 2000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val updated = podcastDao.getPodcast("pod-1")
        assertNotNull(updated)
        assertFalse(updated!!.isSubscribed)
        assertEquals(2000L, updated.unsubscribedAt)
        assertFalse(updated.isDirty)
        assertEquals(3000L, updated.syncedAt)
        assertFalse(updated.autoDownloadEnabled)
        assertFalse(updated.notificationsEnabled)
        assertEquals(listOf("pod-1"), fakeFolderRepository.removedFromAllFolders)
    }

    @Test
    fun resolveSubscription_localResubscribeWins_whenSubscribedAfterRemoteUnsubscribed() = runTest {
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-1",
                title = "Local Show",
                isSubscribed = true,
                subscribedAt = 2500L,
                unsubscribedAt = 0L,
                isDirty = false,
                syncedAt = 2500L,
            ),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-1",
            isSubscribed = false,
            subscribedAt = 1000L,
            unsubscribedAt = 2000L,
            updatedAt = 2000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val updated = podcastDao.getPodcast("pod-1")
        assertNotNull(updated)
        assertTrue(updated!!.isSubscribed)
        assertTrue(updated.isDirty) // Marked dirty to push back local resubscribe
        assertTrue(fakeFolderRepository.removedFromAllFolders.isEmpty())
    }

    @Test
    fun resolveSubscription_remoteSubscriptionWins_whenSubscribedAfterLocalUnsubscribed() = runTest {
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-1",
                title = "Local Show",
                isSubscribed = false,
                subscribedAt = 500L,
                unsubscribedAt = 1000L,
                isDirty = false,
                syncedAt = 1000L,
            ),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-1",
            isSubscribed = true,
            subscribedAt = 2000L,
            unsubscribedAt = 0L,
            autoDownloadEnabled = true,
            notificationsEnabled = true,
            customGenre = "Science",
            updatedAt = 2000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val updated = podcastDao.getPodcast("pod-1")
        assertNotNull(updated)
        assertTrue(updated!!.isSubscribed)
        assertEquals(2000L, updated.subscribedAt)
        assertEquals(0L, updated.unsubscribedAt)
        assertFalse(updated.isDirty)
        assertEquals(3000L, updated.syncedAt)
        assertTrue(updated.autoDownloadEnabled)
        assertTrue(updated.notificationsEnabled)
        assertEquals("Science", updated.customGenre)
    }

    @Test
    fun resolveSubscription_localUnsubscribeWins_whenUnsubscribedAfterRemoteSubscribed() = runTest {
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-1",
                title = "Local Show",
                isSubscribed = false,
                subscribedAt = 1000L,
                unsubscribedAt = 2500L,
                isDirty = false,
                syncedAt = 2500L,
            ),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-1",
            isSubscribed = true,
            subscribedAt = 2000L,
            unsubscribedAt = 0L,
            updatedAt = 2000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val updated = podcastDao.getPodcast("pod-1")
        assertNotNull(updated)
        assertFalse(updated!!.isSubscribed)
        assertTrue(updated.isDirty) // Marked dirty to push back local unsubscribe
    }

    @Test
    fun resolveSubscription_newRemoteSubscription_createsStubWhenNotFoundLocally() = runTest {
        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-new",
            isSubscribed = true,
            subscribedAt = 1500L,
            unsubscribedAt = 0L,
            customGenre = "History",
            autoDownloadEnabled = true,
            notificationsEnabled = false,
            feedUrl = null,
            updatedAt = 1500L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val created = podcastDao.getPodcast("pod-new")
        assertNotNull(created)
        assertTrue(created!!.isSubscribed)
        assertEquals(1500L, created.subscribedAt)
        assertEquals("Loading...", created.title)
        assertEquals("podcast_index", created.sourceType)
        org.junit.Assert.assertNull(created.feedUrl)
        assertEquals("History", created.customGenre)
        assertTrue(created.autoDownloadEnabled)
        assertFalse(created.notificationsEnabled)
        assertFalse(created.isDirty)
        assertEquals(3000L, created.syncedAt)
    }

    @Test
    fun resolveSubscription_newRssSubscription_createsRssStubWhenRssRepoNotAvailable() = runTest {
        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "rss:12345",
            isSubscribed = true,
            subscribedAt = 1500L,
            unsubscribedAt = 0L,
            feedUrl = "https://example.com/rss.xml",
            updatedAt = 1500L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val created = podcastDao.getPodcast("rss:12345")
        assertNotNull(created)
        assertTrue(created!!.isSubscribed)
        assertEquals("RSS Feed", created.title)
        assertEquals("rss", created.sourceType)
        assertEquals("https://example.com/rss.xml", created.feedUrl)
        assertFalse(created.isDirty)
        assertEquals(3000L, created.syncedAt)
    }

    @Test
    fun resolveSubscription_bothSubscribed_mergesSettingsWhenRemoteNewer() = runTest {
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-1",
                title = "Local Show",
                isSubscribed = true,
                subscribedAt = 1000L,
                unsubscribedAt = 0L,
                isDirty = false,
                syncedAt = 1000L,
                autoDownloadEnabled = false,
                notificationsEnabled = false,
                customGenre = null,
            ),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-1",
            isSubscribed = true,
            subscribedAt = 1000L,
            unsubscribedAt = 0L,
            customGenre = "Tech & Science",
            autoDownloadEnabled = true,
            notificationsEnabled = true,
            feedUrl = "https://example.com/updated.xml",
            updatedAt = 2000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val updated = podcastDao.getPodcast("pod-1")
        assertNotNull(updated)
        assertEquals("Tech & Science", updated!!.customGenre)
        assertTrue(updated.autoDownloadEnabled)
        assertTrue(updated.notificationsEnabled)
        assertEquals("https://example.com/updated.xml", updated.feedUrl)
        assertFalse(updated.isDirty)
        assertEquals(3000L, updated.syncedAt)
    }

    @Test
    fun resolveSubscription_bothSubscribed_mergesSettingsEvenWhenRemoteUpdatedAtLessThanOrEqualSyncedAt() = runTest {
        // Simulates Device B having syncedAt from a later sync, while Device A's edit had a slightly earlier timestamp
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-drift",
                title = "Drift Show",
                isSubscribed = true,
                subscribedAt = 1000L,
                unsubscribedAt = 0L,
                isDirty = false,
                syncedAt = 5000L, // Phone synced later
                autoDownloadEnabled = false,
                notificationsEnabled = false,
            ),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-drift",
            isSubscribed = true,
            subscribedAt = 1000L,
            unsubscribedAt = 0L,
            autoDownloadEnabled = true,
            notificationsEnabled = true,
            updatedAt = 4000L, // Remote timestamp is 4000L <= local syncedAt 5000L
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 6000L)

        val updated = podcastDao.getPodcast("pod-drift")
        assertNotNull(updated)
        assertTrue(updated!!.autoDownloadEnabled)
        assertTrue(updated.notificationsEnabled)
        assertFalse(updated.isDirty)
        assertEquals(6000L, updated.syncedAt)
    }

    @Test
    fun resolveSubscription_podcastIndexWithFeedUrl_isNotClassifiedAsRss() = runTest {
        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "12345",
            isSubscribed = true,
            subscribedAt = 1500L,
            unsubscribedAt = 0L,
            feedUrl = "https://example.com/feed.xml",
            updatedAt = 1500L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val created = podcastDao.getPodcast("12345")
        assertNotNull(created)
        assertTrue(created!!.isSubscribed)
        assertEquals("Loading...", created.title)
        assertEquals(PodcastEntity.SOURCE_PODCAST_INDEX, created.sourceType)
        assertEquals("https://example.com/feed.xml", created.feedUrl)
        assertFalse(created.isDirty)
    }

    @Test
    fun resolveSubscription_customGenreRemoved_clearsCustomGenreIcon() = runTest {
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-genre",
                title = "Show with Genre",
                isSubscribed = true,
                subscribedAt = 1000L,
                unsubscribedAt = 0L,
                isDirty = false,
                syncedAt = 1000L,
                customGenre = "Tech",
            ).copy(customGenreIcon = "icon_tech"),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-genre",
            isSubscribed = true,
            subscribedAt = 1000L,
            unsubscribedAt = 0L,
            customGenre = null,
            updatedAt = 2000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val updated = podcastDao.getPodcast("pod-genre")
        assertNotNull(updated)
        org.junit.Assert.assertNull(updated!!.customGenre)
        org.junit.Assert.assertNull(updated.customGenreIcon)
    }

    @Test
    fun resolveSubscription_bothSubscribed_doesNotOverwriteSettingsWhenLocalDirty() = runTest {
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-1",
                title = "Local Show",
                isSubscribed = true,
                subscribedAt = 1000L,
                unsubscribedAt = 0L,
                isDirty = true, // User edited settings locally while offline
                syncedAt = 1000L,
                autoDownloadEnabled = true,
                notificationsEnabled = true,
                customGenre = "Local Genre",
            ),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-1",
            isSubscribed = true,
            subscribedAt = 1000L,
            unsubscribedAt = 0L,
            customGenre = "Remote Genre",
            autoDownloadEnabled = false,
            notificationsEnabled = false,
            updatedAt = 2000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        val unchanged = podcastDao.getPodcast("pod-1")
        assertNotNull(unchanged)
        assertEquals("Local Genre", unchanged!!.customGenre)
        assertTrue(unchanged.autoDownloadEnabled)
        assertTrue(unchanged.notificationsEnabled)
        assertTrue(unchanged.isDirty)
    }

    /** The cloud doesn't carry a show's own playback settings, so no pull may wipe them. */
    @Test
    fun resolveSubscription_pullsKeepTheShowsOwnPlaybackSettings() = runTest {
        val settings = { row: PodcastEntity ->
            row.copy(skipBeginningOverrideMs = 30_000L, skipEndingOverrideMs = 0L, playbackSpeedOverride = 1.5f, skipSilenceOverride = false)
        }
        podcastDao.upsert(settings(createPodcast("clean", isSubscribed = true, subscribedAt = 1000L, syncedAt = 1000L)))
        podcastDao.upsert(settings(createPodcast("left", subscribedAt = 0L, unsubscribedAt = 1000L, syncedAt = 1000L)))
        podcastDao.upsert(settings(createPodcast("dropped", isSubscribed = true, subscribedAt = 1000L, syncedAt = 1000L)))

        resolver.resolveSubscription(
            UserSubscriptionSyncDto(podcastId = "clean", isSubscribed = true, subscribedAt = 1000L, autoDownloadEnabled = true, updatedAt = 2000L),
            syncedAt = 3000L,
        )
        resolver.resolveSubscription(
            UserSubscriptionSyncDto(podcastId = "left", isSubscribed = true, subscribedAt = 2000L, updatedAt = 2000L),
            syncedAt = 3000L,
        )
        resolver.resolveSubscription(
            UserSubscriptionSyncDto(podcastId = "dropped", isSubscribed = false, subscribedAt = 1000L, unsubscribedAt = 2000L, updatedAt = 2000L),
            syncedAt = 3000L,
        )

        val clean = podcastDao.getPodcast("clean")!!
        assertTrue("the pull was merged", clean.autoDownloadEnabled)
        assertTrue(podcastDao.getPodcast("left")!!.isSubscribed)
        assertFalse(podcastDao.getPodcast("dropped")!!.isSubscribed)
        for (id in listOf("clean", "left", "dropped")) {
            val row = podcastDao.getPodcast(id)!!
            assertEquals(id, 1.5f, row.playbackSpeedOverride)
            assertEquals(id, false, row.skipSilenceOverride)
            assertEquals(id, 30_000L to 0L, row.skipBeginningOverrideMs to row.skipEndingOverrideMs)
        }
    }

    @Suppress("LongParameterList")
    private fun createPodcast(
        podcastId: String,
        title: String = "Test Show",
        isSubscribed: Boolean = false,
        subscribedAt: Long = 0L,
        unsubscribedAt: Long = 0L,
        isDirty: Boolean = false,
        syncedAt: Long = 0L,
        autoDownloadEnabled: Boolean = false,
        notificationsEnabled: Boolean = false,
        customGenre: String? = null,
        feedUrl: String? = null,
    ) = PodcastEntity(
        podcastId = podcastId,
        title = title,
        author = "Author",
        imageUrl = "https://example.com/art.jpg",
        description = "Description",
        isSubscribed = isSubscribed,
        subscribedAt = subscribedAt,
        unsubscribedAt = unsubscribedAt,
        isDirty = isDirty,
        syncedAt = syncedAt,
        autoDownloadEnabled = autoDownloadEnabled,
        notificationsEnabled = notificationsEnabled,
        customGenre = customGenre,
        feedUrl = feedUrl,
    )

    @Test
    fun resolveSubscription_newSubscription_withNotificationsEnabled_notifiesTopicPort() = runTest {
        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-new",
            isSubscribed = true,
            subscribedAt = 1000L,
            notificationsEnabled = true,
            updatedAt = 1000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 2000L)

        assertEquals(listOf("pod-new" to true), fakeNotificationPort.topicUpdates)
    }

    @Test
    fun resolveSubscription_remoteTombstone_withNotificationsEnabled_unsubscribesTopicPort() = runTest {
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-tomb",
                isSubscribed = true,
                subscribedAt = 1000L,
                notificationsEnabled = true,
            ),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-tomb",
            isSubscribed = false,
            subscribedAt = 1000L,
            unsubscribedAt = 2000L,
            updatedAt = 2000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        assertEquals(listOf("pod-tomb" to false), fakeNotificationPort.topicUpdates)
    }

    @Test
    fun resolveSubscription_bothSubscribed_notificationChanged_notifiesTopicPort() = runTest {
        podcastDao.upsert(
            createPodcast(
                podcastId = "pod-toggle",
                isSubscribed = true,
                subscribedAt = 1000L,
                notificationsEnabled = false,
                isDirty = false,
            ),
        )

        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "pod-toggle",
            isSubscribed = true,
            subscribedAt = 1000L,
            notificationsEnabled = true,
            updatedAt = 2000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 3000L)

        assertEquals(listOf("pod-toggle" to true), fakeNotificationPort.topicUpdates)
    }

    @Test
    fun resolveSubscription_rssPodcast_doesNotNotifyTopicPort() = runTest {
        val remoteDto = UserSubscriptionSyncDto(
            podcastId = "rss:custom-feed",
            isSubscribed = true,
            subscribedAt = 1000L,
            notificationsEnabled = true,
            updatedAt = 1000L,
        )

        resolver.resolveSubscription(remoteDto, syncedAt = 2000L)

        assertTrue(fakeNotificationPort.topicUpdates.isEmpty())
        assertFalse(podcastDao.getPodcast("rss:custom-feed")!!.notificationsEnabled)
    }

    @Test
    fun unchangedPull_withAddressWriter_writesNothing() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val routed = SubscriptionSyncResolver(podcastDao = dao, notificationSyncPort = fakeNotificationPort, feedAddresses = FeedAddressUpdater(dao))
        podcastDao.upsert(syncedFollow())

        routed.resolveSubscription(samePull(), syncedAt = 3000L)

        assertEquals(emptyList<String>(), dao.writes)
        assertEquals(syncedFollow(), podcastDao.getPodcast("pod-1"))
        assertTrue(fakeNotificationPort.topicUpdates.isEmpty())
    }

    @Test
    fun unchangedPull_withoutAddressWriter_writesNothing() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val plain = SubscriptionSyncResolver(podcastDao = dao, notificationSyncPort = fakeNotificationPort)
        podcastDao.upsert(syncedFollow())

        plain.resolveSubscription(samePull(), syncedAt = 3000L)

        assertEquals(emptyList<String>(), dao.writes)
        assertEquals(1000L, podcastDao.getPodcast("pod-1")!!.syncedAt)
    }

    @Test
    fun unchangedPull_stampsANeverSyncedRow() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val routed = SubscriptionSyncResolver(podcastDao = dao, feedAddresses = FeedAddressUpdater(dao))
        podcastDao.upsert(syncedFollow().copy(syncedAt = 0L))

        routed.resolveSubscription(samePull(), syncedAt = 3000L)

        assertEquals(listOf("upsertKeepingFeedAddress"), dao.writes)
        assertEquals(3000L, podcastDao.getPodcast("pod-1")!!.syncedAt)
    }

    @Test
    fun pullWithANewerFollowTime_writesTheRow() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val routed = SubscriptionSyncResolver(podcastDao = dao, feedAddresses = FeedAddressUpdater(dao))
        podcastDao.upsert(syncedFollow())

        routed.resolveSubscription(samePull().copy(subscribedAt = 1500L), syncedAt = 3000L)

        assertEquals(listOf("upsertKeepingFeedAddress"), dao.writes)
        val row = podcastDao.getPodcast("pod-1")!!
        assertEquals(1500L, row.subscribedAt)
        assertEquals(3000L, row.syncedAt)
    }

    @Test
    fun pullThatOnlyClearsAStaleGenreIcon_writesTheRow() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val plain = SubscriptionSyncResolver(podcastDao = dao)
        podcastDao.upsert(syncedFollow().copy(customGenreIcon = "icon_tech"))

        plain.resolveSubscription(samePull(), syncedAt = 3000L)

        assertEquals(listOf("upsert"), dao.writes)
        org.junit.Assert.assertNull(podcastDao.getPodcast("pod-1")!!.customGenreIcon)
    }

    @Test
    fun pullThatTurnsNotificationsOn_writesTheRowAndRegistersTheTopic() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val routed = SubscriptionSyncResolver(podcastDao = dao, notificationSyncPort = fakeNotificationPort, feedAddresses = FeedAddressUpdater(dao))
        podcastDao.upsert(syncedFollow())

        routed.resolveSubscription(samePull().copy(notificationsEnabled = true), syncedAt = 3000L)

        assertEquals(listOf("upsertKeepingFeedAddress"), dao.writes)
        assertTrue(podcastDao.getPodcast("pod-1")!!.notificationsEnabled)
        assertEquals(listOf("pod-1" to true), fakeNotificationPort.topicUpdates)
    }

    @Test
    fun pullThatOnlyChangesTheAddress_appliesItThroughTheWriterWithoutARowWrite() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val routed = SubscriptionSyncResolver(podcastDao = dao, feedAddresses = FeedAddressUpdater(dao))
        podcastDao.upsert(syncedFollow())

        routed.resolveSubscription(samePull().copy(feedUrl = MOVED_FEED), syncedAt = 3000L)

        assertEquals(listOf("replaceFeedAddress"), dao.writes)
        assertEquals(MOVED_FEED, podcastDao.getPodcast("pod-1")!!.feedUrl)
    }

    @Test
    fun olderTombstoneForADirtyFollow_writesNothing() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val plain = SubscriptionSyncResolver(podcastDao = dao)
        podcastDao.upsert(syncedFollow().copy(subscribedAt = 2500L, isDirty = true))

        plain.resolveSubscription(
            UserSubscriptionSyncDto(podcastId = "pod-1", isSubscribed = false, subscribedAt = 1000L, unsubscribedAt = 2000L, updatedAt = 2000L),
            syncedAt = 3000L,
        )

        assertEquals(emptyList<String>(), dao.writes)
        assertTrue(podcastDao.getPodcast("pod-1")!!.isDirty)
    }

    @Test
    fun olderFollowForADirtyUnfollow_writesNothing() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val plain = SubscriptionSyncResolver(podcastDao = dao)
        podcastDao.upsert(
            createPodcast(podcastId = "pod-1", isSubscribed = false, subscribedAt = 500L, unsubscribedAt = 2500L, isDirty = true, syncedAt = 1000L),
        )

        plain.resolveSubscription(samePull().copy(subscribedAt = 2000L, updatedAt = 2000L), syncedAt = 3000L)

        assertEquals(emptyList<String>(), dao.writes)
        val row = podcastDao.getPodcast("pod-1")!!
        assertFalse(row.isSubscribed)
        assertTrue(row.isDirty)
    }

    @Test
    fun linkedWatchMerge_stillWritesAnUnchangedRow() = runTest {
        val dao = CountingPodcastDao(podcastDao)
        val routed = SubscriptionSyncResolver(podcastDao = dao, feedAddresses = FeedAddressUpdater(dao))
        podcastDao.upsert(syncedFollow())

        routed.resolveSubscription(samePull(), syncedAt = 0L)

        assertEquals(listOf("upsertKeepingFeedAddress"), dao.writes)
        assertEquals(syncedFollow().copy(syncedAt = 0L), podcastDao.getPodcast("pod-1"))
    }

    /** A clean followed show that a pull already stamped. */
    private fun syncedFollow() =
        createPodcast(
            podcastId = "pod-1",
            isSubscribed = true,
            subscribedAt = 1000L,
            syncedAt = 1000L,
            feedUrl = SAVED_FEED,
        )

    /** The server's copy of [syncedFollow]: every pull returns it again. */
    private fun samePull() =
        UserSubscriptionSyncDto(
            podcastId = "pod-1",
            isSubscribed = true,
            subscribedAt = 1000L,
            feedUrl = SAVED_FEED,
            updatedAt = 1000L,
        )

    /** Records each write to the podcasts table made through it. */
    private class CountingPodcastDao(private val delegate: PodcastDao) : PodcastDao by delegate {
        val writes = mutableListOf<String>()

        override suspend fun upsert(podcast: PodcastEntity) {
            writes += "upsert"
            delegate.upsert(podcast)
        }

        override suspend fun upsertRow(podcast: PodcastEntity) {
            writes += "upsertRow"
            delegate.upsertRow(podcast)
        }

        override suspend fun upsertKeepingFeedAddress(podcast: PodcastEntity) {
            writes += "upsertKeepingFeedAddress"
            delegate.upsertKeepingFeedAddress(podcast)
        }

        override suspend fun replaceFeedAddress(write: FeedAddressWrite): Boolean {
            writes += "replaceFeedAddress"
            return delegate.replaceFeedAddress(write)
        }

        override suspend fun setNotificationsEnabled(id: String, enabled: Boolean) {
            writes += "setNotificationsEnabled"
            delegate.setNotificationsEnabled(id, enabled)
        }

        override suspend fun markDirty(id: String) {
            writes += "markDirty"
            delegate.markDirty(id)
        }

        override suspend fun markFollowedDirty(id: String) {
            writes += "markFollowedDirty"
            delegate.markFollowedDirty(id)
        }
    }

    private companion object {
        const val SAVED_FEED = "https://example.com/feed.xml"
        const val MOVED_FEED = "https://example.com/moved.xml"
    }

    private class FakePodcastNotificationSyncPort : cx.aswin.boxlore.core.catalog.ports.PodcastNotificationSyncPort {
        val topicUpdates = mutableListOf<Pair<String, Boolean>>()

        override suspend fun setNotificationTopicSubscribed(podcastId: String, subscribed: Boolean) {
            topicUpdates.add(podcastId to subscribed)
        }
    }

    private class FakeFolderRepository : FolderRepository {
        val removedFromAllFolders = mutableListOf<String>()

        override val folders: Flow<List<SubscriptionFolder>> = emptyFlow()
        override val folderNames: Flow<List<String>> = emptyFlow()
        override suspend fun getFolders(): List<SubscriptionFolder> = emptyList()
        override suspend fun getFolder(folderId: String): SubscriptionFolder? = null
        override fun getFolderFlow(folderId: String): Flow<SubscriptionFolder?> = emptyFlow()
        override suspend fun createFolder(
            name: String,
            icon: String?,
            displaySize: FolderDisplaySize,
            linkedGenre: String?,
            showPodcastGrid: Boolean,
            podcastIds: List<String>,
        ): SubscriptionFolder = throw UnsupportedOperationException()
        override suspend fun restoreFolder(folder: SubscriptionFolder): SubscriptionFolder = folder
        override suspend fun updateFolder(folder: SubscriptionFolder) {}
        override suspend fun deleteFolder(folderId: String) {}
        override suspend fun addPodcastToFolder(podcastId: String, folderId: String) {}
        override suspend fun removePodcastFromFolder(podcastId: String, folderId: String) {}
        override suspend fun removePodcastFromAllFolders(podcastId: String) {
            removedFromAllFolders.add(podcastId)
        }
        override suspend fun setPodcastsForFolder(folderId: String, podcastIds: List<String>) {}
        override suspend fun syncLinkedGenres() {}
        override suspend fun autoOrganizeSubscribedShows(
            defaultDisplaySize: FolderDisplaySize?,
            showPodcastGrid: Boolean,
        ) {}
    }
}
