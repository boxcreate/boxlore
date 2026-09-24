package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Minimal in-memory Room DAO smoke (B4).
 *
 * If this suite fails to configure under AGP (schemas / includeAndroidResources /
 * AAR metadata), treat documentation in `core/database/README.md` as the B4 exit
 * and keep RSS ID fixtures as the identity safety net — see `docs/TESTING.md`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PodcastDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = database.podcastDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun upsertAndGetPodcast_roundTrips() = runTest {
        val entity =
            PodcastEntity(
                podcastId = "920666",
                title = "The Daily",
                author = "The New York Times",
                imageUrl = "https://example.com/daily.jpg",
                description = "News",
                isSubscribed = true,
            )

        dao.upsert(entity)

        val loaded = dao.getPodcast("920666")
        assertEquals("The Daily", loaded?.title)
        assertEquals("The New York Times", loaded?.author)
        assertNull(dao.getPodcast("missing"))
    }

    @Suppress("LongParameterList")
    private fun createPodcast(
        podcastId: String,
        title: String = "Podcast $podcastId",
        author: String = "Author",
        imageUrl: String = "https://example.com/art.jpg",
        description: String? = "Desc",
        isSubscribed: Boolean = false,
        subscribedAt: Long = 0L,
        unsubscribedAt: Long = 0L,
        autoDownloadEnabled: Boolean = false,
        notificationsEnabled: Boolean = false,
        customGenre: String? = null,
        feedUrl: String? = null,
        isDirty: Boolean = false,
        syncedAt: Long = 0L,
    ) = PodcastEntity(
        podcastId = podcastId,
        title = title,
        author = author,
        imageUrl = imageUrl,
        description = description,
        isSubscribed = isSubscribed,
        subscribedAt = subscribedAt,
        unsubscribedAt = unsubscribedAt,
        autoDownloadEnabled = autoDownloadEnabled,
        notificationsEnabled = notificationsEnabled,
        customGenre = customGenre,
        feedUrl = feedUrl,
        isDirty = isDirty,
        syncedAt = syncedAt,
    )

    @Test
    fun markPodcastSyncedIfUnchanged_clearsDirtyWhenSnapshotMatches() = runTest {
        val entity = createPodcast(
            podcastId = "pod_1",
            title = "Podcast 1",
            isSubscribed = true,
            subscribedAt = 1000L,
            unsubscribedAt = 0L,
            autoDownloadEnabled = true,
            notificationsEnabled = false,
            customGenre = "Tech",
            feedUrl = "https://example.com/feed.xml",
            isDirty = true,
            syncedAt = 0L,
        )
        dao.upsert(entity)

        val updated = dao.markPodcastSyncedIfUnchanged(
            id = "pod_1",
            snapshotIsSubscribed = true,
            snapshotSubscribedAt = 1000L,
            snapshotUnsubscribedAt = 0L,
            snapshotAutoDownload = true,
            snapshotNotifications = false,
            snapshotCustomGenre = "Tech",
            snapshotFeedUrl = "https://example.com/feed.xml",
            syncedAt = 5000L,
        )

        assertEquals(1, updated)
        val stored = dao.getPodcast("pod_1")!!
        assertEquals(false, stored.isDirty)
        assertEquals(5000L, stored.syncedAt)
    }

    @Test
    fun markPodcastSyncedIfUnchanged_doesNotClearWhenSnapshotDiffers() = runTest {
        val entity = createPodcast(
            podcastId = "pod_2",
            title = "Podcast 2",
            isSubscribed = true,
            subscribedAt = 2000L,
            unsubscribedAt = 0L,
            autoDownloadEnabled = false,
            notificationsEnabled = true,
            customGenre = null,
            feedUrl = null,
            isDirty = true,
            syncedAt = 0L,
        )
        dao.upsert(entity)

        // Snapshot isSubscribed was false, but entity has true
        val updated = dao.markPodcastSyncedIfUnchanged(
            id = "pod_2",
            snapshotIsSubscribed = false,
            snapshotSubscribedAt = 2000L,
            snapshotUnsubscribedAt = 0L,
            snapshotAutoDownload = false,
            snapshotNotifications = true,
            snapshotCustomGenre = null,
            snapshotFeedUrl = null,
            syncedAt = 5000L,
        )

        assertEquals(0, updated)
        val stored = dao.getPodcast("pod_2")!!
        assertEquals(true, stored.isDirty)
        assertEquals(0L, stored.syncedAt)
    }

    @Test
    fun markPodcastSyncedIfUnchanged_clearsDirtyWhenCustomGenreAndFeedUrlAreNullAndMatch() = runTest {
        val entity = createPodcast(
            podcastId = "pod_nulls",
            title = "Podcast Nulls",
            isSubscribed = true,
            subscribedAt = 3000L,
            unsubscribedAt = 0L,
            autoDownloadEnabled = false,
            notificationsEnabled = false,
            customGenre = null,
            feedUrl = null,
            isDirty = true,
            syncedAt = 0L,
        )
        dao.upsert(entity)

        val updated = dao.markPodcastSyncedIfUnchanged(
            id = "pod_nulls",
            snapshotIsSubscribed = true,
            snapshotSubscribedAt = 3000L,
            snapshotUnsubscribedAt = 0L,
            snapshotAutoDownload = false,
            snapshotNotifications = false,
            snapshotCustomGenre = null,
            snapshotFeedUrl = null,
            syncedAt = 6000L,
        )

        assertEquals(1, updated)
        val stored = dao.getPodcast("pod_nulls")!!
        assertEquals(false, stored.isDirty)
        assertEquals(6000L, stored.syncedAt)
    }

    @Test
    fun clearAllSubscriptionsForAccountSwitch_resetsSubscriptionsAndDirty() = runTest {
        dao.upsert(
            createPodcast(
                podcastId = "pod_sub",
                title = "Subscribed Show",
                isSubscribed = true,
                subscribedAt = 1000L,
                autoDownloadEnabled = true,
                notificationsEnabled = true,
                customGenre = "News",
                isDirty = true,
            )
        )

        dao.clearAllSubscriptionsForAccountSwitch()

        val stored = dao.getPodcast("pod_sub")!!
        assertEquals(false, stored.isSubscribed)
        assertEquals(0L, stored.subscribedAt)
        assertEquals(0L, stored.unsubscribedAt)
        assertEquals(false, stored.isDirty)
        assertEquals(false, stored.autoDownloadEnabled)
        assertEquals(false, stored.notificationsEnabled)
        assertNull(stored.customGenre)
    }
}
