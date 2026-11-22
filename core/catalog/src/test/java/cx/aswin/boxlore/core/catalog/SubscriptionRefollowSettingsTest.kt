package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
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

/**
 * [SubscriptionRepository.subscribe] and restore on a show already followed (an OPML import,
 * onboarding, a backup restore) keep its alerts and auto-download; a new follow starts with both off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SubscriptionRefollowSettingsTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var repository: SubscriptionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        podcastDao = database.podcastDao()
        repository = SubscriptionRepository(podcastDao, autoDownloadDao = database.autoDownloadDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun followingAFollowedShowAgainKeepsItsAlertsAndAutoDownload() = runTest {
        repository.subscribe(podcast())
        podcastDao.setNotificationsEnabled(SHOW, true)
        repository.setAutoDownloadEnabled(SHOW, true)

        repository.subscribe(podcast())

        val refollowed = podcastDao.getPodcast(SHOW)!!
        assertTrue("a second follow keeps alerts on", refollowed.notificationsEnabled)
        assertTrue("and keeps auto-download on", refollowed.autoDownloadEnabled)
        assertNotNull("the auto-download record stays with the row", database.autoDownloadDao().getShow(SHOW))
        assertTrue("new-episode alerts still show", repository.canPresentEpisodeNotification(SHOW, null))

        repository.restoreSubscription(podcast(), RESTORED_AT)

        val restored = podcastDao.getPodcast(SHOW)!!
        assertTrue("restoring a backup over the follow keeps alerts on", restored.notificationsEnabled)
        assertTrue(restored.autoDownloadEnabled)
        assertEquals(RESTORED_AT, restored.subscribedAt)
    }

    @Test
    fun aNewFollowStartsWithAlertsAndAutoDownloadOff() = runTest {
        // A row kept for a show that isn't followed, carrying old settings.
        podcastDao.upsert(
            PodcastEntity(
                podcastId = SHOW,
                title = "Show",
                author = "Artist",
                imageUrl = "https://example.com/$SHOW.jpg",
                description = "desc",
                isSubscribed = false,
                notificationsEnabled = true,
                autoDownloadEnabled = true,
            ),
        )

        repository.subscribe(podcast())

        val followed = podcastDao.getPodcast(SHOW)!!
        assertTrue(followed.isSubscribed)
        assertFalse(followed.notificationsEnabled)
        assertFalse(followed.autoDownloadEnabled)

        podcastDao.setNotificationsEnabled(SHOW, true)
        podcastDao.setAutoDownloadEnabled(SHOW, true)
        repository.toggleSubscription(podcast())
        repository.subscribe(podcast())

        val again = podcastDao.getPodcast(SHOW)!!
        assertFalse("a follow after an unfollow is a new follow", again.notificationsEnabled)
        assertFalse(again.autoDownloadEnabled)
    }

    private fun podcast() = Podcast(
        id = SHOW,
        title = "Show",
        artist = "Artist",
        imageUrl = "https://example.com/$SHOW.jpg",
        description = "desc",
        genre = "Technology",
        sourceType = Podcast.SOURCE_PODCAST_INDEX,
    )

    private companion object {
        const val SHOW = "pod-1"
        const val RESTORED_AT = 1_600_000_000_000L
    }
}
