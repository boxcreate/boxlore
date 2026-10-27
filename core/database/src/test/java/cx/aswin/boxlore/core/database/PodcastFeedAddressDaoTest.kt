package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

/**
 * [PodcastDao.replaceFeedAddress], the one saved-address writer (own-episode-IDs P4 PR 14, plan
 * section 11): every column and every stored validator changes together or not at all.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PodcastFeedAddressDaoTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PodcastDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.podcastDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `one update writes both addresses, keeps the old one, and clears every validator of the show`() = runTest {
        dao.upsertRow(show("920666", feedUrl = OLD).copy(feedEtag = "\"rss\"", feedLastModified = "Mon", isDirty = false))
        database.localEpisodeCatalogDao().upsertFeed(catalogFeed("920666"))
        database.localEpisodeCatalogDao().upsertFeed(catalogFeed("other"))
        database.episodeSupplementDao().upsertSupplement(EpisodeSupplementEntity("920666", OLD, "rss:ns", "\"sup\"", "Tue", 1L))

        val replaced = dao.replaceFeedAddress(write("920666", expected = OLD, source = "registry:apple", checkedAt = 42L))

        assertTrue(replaced)
        val row = dao.getPodcast("920666")!!
        assertEquals(NEW, row.feedUrl)
        assertEquals(OLD, row.previousFeedUrl)
        assertEquals("registry:apple", row.feedUrlSource)
        assertEquals(42L, row.feedUrlCheckedAt)
        assertNull(row.transportUrl)
        assertNull(row.feedEtag)
        assertNull(row.feedLastModified)
        assertTrue(row.isDirty)
        val catalog = database.localEpisodeCatalogDao().getFeed("920666")!!
        assertNull(catalog.feedEtag)
        assertNull(catalog.feedLastModified)
        assertEquals("other shows keep theirs", "\"etag\"", database.localEpisodeCatalogDao().getFeed("other")!!.feedEtag)
        val supplement = database.episodeSupplementDao().getSupplement("920666")!!
        assertNull(supplement.feedEtag)
        assertNull(supplement.feedLastModified)
    }

    @Test
    fun `a row that changed since it was read, or an rss show, is left alone`() = runTest {
        dao.upsertRow(show("920666", feedUrl = "https://other.example/feed.xml").copy(isDirty = false))
        dao.upsertRow(show("rss:abc", feedUrl = OLD).copy(sourceType = PodcastEntity.SOURCE_RSS))
        database.localEpisodeCatalogDao().upsertFeed(catalogFeed("920666"))

        assertFalse(dao.replaceFeedAddress(write("920666", expected = OLD)))
        assertFalse(dao.replaceFeedAddress(write("rss:abc", expected = OLD)))

        val row = dao.getPodcast("920666")!!
        assertEquals("https://other.example/feed.xml", row.feedUrl)
        assertNull(row.previousFeedUrl)
        assertFalse(row.isDirty)
        assertEquals("a refused write clears nothing", "\"etag\"", database.localEpisodeCatalogDao().getFeed("920666")!!.feedEtag)
        assertEquals(OLD, dao.getPodcast("rss:abc")!!.feedUrl)
    }

    @Test
    fun `a missing address can be filled, and an address from the cloud leaves the row clean`() = runTest {
        dao.upsertRow(show("920666", feedUrl = null).copy(isDirty = false))

        assertTrue(dao.replaceFeedAddress(write("920666", expected = null, markDirty = false)))

        val row = dao.getPodcast("920666")!!
        assertEquals(NEW, row.feedUrl)
        assertNull(row.previousFeedUrl)
        assertFalse(row.isDirty)
    }

    @Test
    fun `undo swaps back and keeps the replaced address`() = runTest {
        dao.upsertRow(show("920666", feedUrl = OLD))
        dao.replaceFeedAddress(write("920666", expected = OLD))

        assertTrue(dao.replaceFeedAddress(write("920666", expected = NEW, feedUrl = OLD)))

        val row = dao.getPodcast("920666")!!
        assertEquals(OLD, row.feedUrl)
        assertEquals(NEW, row.previousFeedUrl)
    }

    @Test
    fun `the daily check stamp leaves the row clean`() = runTest {
        dao.upsertRow(show("920666", feedUrl = OLD).copy(isDirty = false))

        dao.markFeedUrlChecked("920666", 99L)

        val row = dao.getPodcast("920666")!!
        assertEquals(99L, row.feedUrlCheckedAt)
        assertFalse(row.isDirty)
        assertEquals(OLD, row.feedUrl)
    }

    @Test
    fun `a write the registry didn't confirm keeps the daily check stamp`() = runTest {
        dao.upsertRow(show("920666", feedUrl = OLD).copy(feedUrlCheckedAt = 50L))

        assertTrue(dao.replaceFeedAddress(write("920666", OLD, checkedAt = 0L)))
        assertEquals("a cloud pull, a feed move or a fill never re-opens today's check", 50L, dao.getPodcast("920666")!!.feedUrlCheckedAt)

        assertTrue(dao.replaceFeedAddress(write("920666", NEW, feedUrl = OLD, checkedAt = 70L)))
        assertEquals(70L, dao.getPodcast("920666")!!.feedUrlCheckedAt)
    }

    @Test
    fun `only a followed show is marked for a push to keep its address`() = runTest {
        dao.upsertRow(show("1", feedUrl = OLD).copy(isDirty = false))
        dao.upsertRow(show("2", feedUrl = OLD).copy(isDirty = false, isSubscribed = false))

        dao.markFollowedDirty("1")
        dao.markFollowedDirty("2")

        assertTrue(dao.getPodcast("1")!!.isDirty)
        assertFalse(dao.getPodcast("2")!!.isDirty)
    }

    @Test
    fun `a merged row built from an older copy keeps an address saved meanwhile`() = runTest {
        dao.upsertRow(show("920666", feedUrl = OLD).copy(isDirty = false))
        val older = dao.getPodcast("920666")!!
        dao.replaceFeedAddress(write("920666", OLD, source = "registry:apple", checkedAt = 9L))

        dao.upsertKeepingFeedAddress(older.copy(title = "Renamed", isDirty = false))
        val kept = dao.getPodcast("920666")!!
        assertEquals(NEW, kept.feedUrl)
        assertEquals(OLD, kept.previousFeedUrl)
        assertEquals("registry:apple", kept.feedUrlSource)
        assertEquals("Renamed", kept.title)
        assertTrue(kept.isDirty)

        dao.upsertKeepingFeedAddress(kept.copy(title = "Again", isDirty = false))
        assertFalse("an unchanged address writes the row as given", dao.getPodcast("920666")!!.isDirty)
    }

    private fun write(
        id: String,
        expected: String?,
        feedUrl: String = NEW,
        source: String? = "feed_move",
        checkedAt: Long = 0L,
        markDirty: Boolean = true,
    ) = FeedAddressWrite(id, expected, feedUrl, transportUrl = null, source = source, checkedAt = checkedAt, markDirty = markDirty)

    private fun show(id: String, feedUrl: String?) = PodcastEntity(
        podcastId = id,
        title = "Show",
        author = "Author",
        imageUrl = "",
        description = null,
        isSubscribed = true,
        feedUrl = feedUrl,
    )

    private fun catalogFeed(id: String) = LocalEpisodeFeedEntity(
        podcastId = id,
        feedUrl = OLD,
        feedEtag = "\"etag\"",
        feedLastModified = "Wed",
        fetchedAt = 1L,
        itemCount = 1,
        feedOrder = LocalFeedOrder.NEWEST_FIRST,
        ttlExpiresAt = null,
        needsFullBackfill = false,
        copiedExtrasCount = 0,
        ready = true,
        feedUrlLookupAt = 0L,
    )

    private companion object {
        const val OLD = "https://old.example/feed.xml"
        const val NEW = "https://new.example/feed.xml"
    }
}
