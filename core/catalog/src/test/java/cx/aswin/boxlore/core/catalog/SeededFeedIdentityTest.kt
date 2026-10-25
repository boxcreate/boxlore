package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Room v39 upgrade seeds each show's episode namespace once. Subscribe and the catalog's
 * show refresh rebuild the podcast row from scratch; the seeded value must survive them.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SeededFeedIdentityTest {
    private lateinit var database: BoxLoreDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    /** A row as the v39 upgrade leaves it: namespace seeded, address bookkeeping recorded. */
    private suspend fun seeded(subscribed: Boolean) {
        database.podcastDao().upsertRow(
            PodcastEntity(
                podcastId = SHOW,
                title = "Show",
                author = "Artist",
                imageUrl = "https://example.com/art.jpg",
                description = null,
                isSubscribed = subscribed,
                feedUrl = FEED,
                epNs = 100L,
                feedUrlSource = "registry",
                feedUrlCheckedAt = 9L,
            ),
        )
    }

    private val podcast = Podcast(id = SHOW, title = "Show", artist = "Artist", imageUrl = "https://example.com/art.jpg", feedUrl = FEED)

    @Test
    fun resubscribingKeepsTheSeededNamespace() = runTest {
        seeded(subscribed = false)

        SubscriptionRepository(database.podcastDao()).toggleSubscription(podcast)

        val stored = database.podcastDao().getPodcast(SHOW)!!
        assertTrue(stored.isSubscribed)
        assertEquals(100L, stored.epNs)
        assertEquals("registry", stored.feedUrlSource)
        assertEquals(9L, stored.feedUrlCheckedAt)
    }

    @Test
    fun refreshingASubscribedShowKeepsTheSeededNamespace() = runTest {
        seeded(subscribed = true)

        RoomLocalCatalog(database).upsertSubscribedPodcast(podcast.copy(title = "Renamed"))

        val stored = database.podcastDao().getPodcast(SHOW)!!
        assertEquals("Renamed", stored.title)
        assertEquals(100L, stored.epNs)
        assertEquals("registry", stored.feedUrlSource)
    }

    private companion object {
        const val SHOW = "100"
        const val FEED = "https://feeds.example/100.xml"
    }
}
