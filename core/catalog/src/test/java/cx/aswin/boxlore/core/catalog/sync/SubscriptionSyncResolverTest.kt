package cx.aswin.boxlore.core.catalog.sync

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
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
    private lateinit var resolver: SubscriptionSyncResolver

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        podcastDao = database.podcastDao()
        fakeFolderRepository = FakeFolderRepository()
        resolver = SubscriptionSyncResolver(
            podcastDao = podcastDao,
            folderRepository = fakeFolderRepository,
            podcastRepository = null,
            rssPodcastRepository = null,
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
