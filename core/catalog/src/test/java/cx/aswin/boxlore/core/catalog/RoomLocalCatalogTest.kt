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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLocalCatalogTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var catalog: RoomLocalCatalog

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database =
            Room
                .inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        podcastDao = database.podcastDao()
        catalog = RoomLocalCatalog(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun entity(
        id: String,
        title: String = "Show $id",
        isSubscribed: Boolean = true,
        sourceType: String = PodcastEntity.SOURCE_PODCAST_INDEX,
        linkedPodcastIndexId: String? = null,
    ) = PodcastEntity(
        podcastId = id,
        title = title,
        author = "Artist",
        imageUrl = "https://example.com/$id.jpg",
        description = "desc",
        genre = "Technology",
        isSubscribed = isSubscribed,
        sourceType = sourceType,
        linkedPodcastIndexId = linkedPodcastIndexId,
    )

    @Test
    fun getLocalPodcastMapsStoredEntity() = runTest {
        podcastDao.upsert(entity("pod-1", title = "Local Show"))

        val podcast = catalog.getLocalPodcast("pod-1")!!
        assertEquals("Local Show", podcast.title)
        assertEquals("Technology", podcast.genre)
    }

    @Test
    fun getLocalPodcastMapsCustomGenreAndIcon() = runTest {
        podcastDao.upsert(
            entity("pod-custom", title = "Custom Show").copy(
                customGenre = "My Tag",
                customGenreIcon = "headphones",
            ),
        )

        val podcast = catalog.getLocalPodcast("pod-custom")!!
        assertEquals("My Tag", podcast.customGenre)
        assertEquals("headphones", podcast.customGenreIcon)
        assertEquals("My Tag", podcast.effectiveGenre)
    }

    @Test
    fun getLocalPodcastReturnsNullWhenMissing() = runTest {
        assertNull(catalog.getLocalPodcast("nobody"))
    }

    @Test
    fun getSubscribedRssLinkedToReturnsSubscribedRss() = runTest {
        podcastDao.upsert(
            entity("-9001", sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = "920"),
        )

        assertEquals("-9001", catalog.getSubscribedRssLinkedTo("920")?.id)
    }

    @Test
    fun getSubscribedRssLinkedToIgnoresUnsubscribed() = runTest {
        podcastDao.upsert(
            entity(
                "-9002",
                isSubscribed = false,
                sourceType = PodcastEntity.SOURCE_RSS,
                linkedPodcastIndexId = "921",
            ),
        )

        assertNull(catalog.getSubscribedRssLinkedTo("921"))
    }

    @Test
    fun upsertSubscribedPodcastUpdatesExistingSubscriptionWithDefaults() = runTest {
        podcastDao.upsert(entity("pod-new"))

        catalog.upsertSubscribedPodcast(
            Podcast(
                id = "pod-new",
                title = "New Show",
                artist = "Artist",
                imageUrl = "https://example.com/new.jpg",
                genre = "News",
            ),
        )

        val stored = podcastDao.getPodcast("pod-new")!!
        assertTrue(stored.isSubscribed)
        assertEquals("newest", stored.preferredSort)
        assertEquals("episodic", stored.type)
    }

    @Test
    fun upsertSubscribedPodcastDoesNotCreateMissingSubscription() = runTest {
        catalog.upsertSubscribedPodcast(
            Podcast(
                id = "pod-missing",
                title = "Late Metadata",
                artist = "Artist",
                imageUrl = "https://example.com/missing.jpg",
            ),
        )

        assertNull(podcastDao.getPodcast("pod-missing"))
    }

    @Test
    fun upsertSubscribedPodcastDoesNotResubscribeUnsubscribedPodcast() = runTest {
        podcastDao.upsert(entity("pod-retired", title = "Stored").copy(isSubscribed = false))

        catalog.upsertSubscribedPodcast(
            Podcast(
                id = "pod-retired",
                title = "Late Metadata",
                artist = "Artist",
                imageUrl = "https://example.com/late.jpg",
            ),
        )

        val stored = podcastDao.getPodcast("pod-retired")!!
        assertFalse(stored.isSubscribed)
        assertEquals("Stored", stored.title)
    }

    @Test
    fun upsertSubscribedPodcastOldestSortImpliesSerial() = runTest {
        podcastDao.upsert(entity("pod-serial"))

        catalog.upsertSubscribedPodcast(
            Podcast(
                id = "pod-serial",
                title = "Serial",
                artist = "Artist",
                imageUrl = "https://example.com/s.jpg",
                preferredSort = "oldest",
            ),
        )

        assertEquals("serial", podcastDao.getPodcast("pod-serial")!!.type)
    }

    @Test
    fun upsertSubscribedPodcastPreservesExistingImageWhenBlank() = runTest {
        podcastDao.upsert(entity("pod-1", title = "Old").copy(imageUrl = "https://cdn/keep.jpg"))

        catalog.upsertSubscribedPodcast(
            Podcast(
                id = "pod-1",
                title = "Updated",
                artist = "Artist",
                imageUrl = "",
            ),
        )

        val stored = podcastDao.getPodcast("pod-1")!!
        assertEquals("Updated", stored.title)
        assertEquals("https://cdn/keep.jpg", stored.imageUrl)
    }

    @Test
    fun upsertSubscribedPodcastPreservesRssIdentityFields() = runTest {
        podcastDao.upsert(
            entity("-9003", sourceType = PodcastEntity.SOURCE_RSS).copy(feedUrl = "https://feed.xml"),
        )

        catalog.upsertSubscribedPodcast(
            Podcast(
                id = "-9003",
                title = "RSS Updated",
                artist = "Artist",
                imageUrl = "https://example.com/rss.jpg",
                sourceType = Podcast.SOURCE_PODCAST_INDEX,
            ),
        )

        val stored = podcastDao.getPodcast("-9003")!!
        assertEquals(PodcastEntity.SOURCE_RSS, stored.sourceType)
        assertEquals("https://feed.xml", stored.feedUrl)
    }
}
