package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastDao
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.rss.MemoryRenumberedShows
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Playlists and the IDs a follow lives under, on real Room: a show Podcast Index renumbered keeps
 * its playlist places (once, when the playlist already holds the new number), and the show page's
 * follow ID is the one stored: its own row, its followed `rss:` feed, the route's show, or a
 * retired number.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenumberedShowPlaylistsTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var podcastDao: PodcastDao
    private lateinit var playlists: RoomPlaylistRepository
    private lateinit var repository: SubscriptionRepository
    private val shows = MemoryRenumberedShows()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        podcastDao = database.podcastDao()
        playlists = RoomPlaylistRepository(database.playlistDao())
        repository =
            SubscriptionRepository(
                podcastDao = podcastDao,
                playlistRepository = playlists,
                renumbering = RenumberingWiring(shows = shows),
            )
    }

    @After
    fun tearDown() {
        database.close()
        RssPodcastRepository.clearInstanceForTests()
    }

    @Test
    fun aRenumberedShowKeepsItsPlaylistPlacesAndStaysFollowedThere() = runTest {
        podcastDao.upsert(row(OLD))
        val comedy = playlists.createPlaylist("Comedy", listOf("other", OLD))

        assertTrue(repository.renumberedFollows.adoptAnswer(OLD, answer()))

        val held = playlists.shows(comedy.id).first()
        assertEquals(listOf("other", NEW), held.map { it.podcastId })
        assertTrue("the moved follow plays", held.single { it.podcastId == NEW }.isFollowed)
    }

    @Test
    fun aPlaylistHoldingBothNumbersKeepsTheShowOnce() = runTest {
        podcastDao.upsert(row(OLD))
        val both = playlists.createPlaylist("Both", listOf(OLD, NEW))

        assertTrue(repository.renumberedFollows.adoptAnswer(OLD, answer()))

        assertEquals(listOf(NEW), playlists.shows(both.id).first().map { it.podcastId })
        assertEquals(mapOf(both.id to listOf(NEW)), playlists.getShowIds())
    }

    @Test
    fun aShowFollowedThroughItsFeedGoesToTheFeedsPlaces() = runTest {
        podcastDao.upsert(row(OLD))
        podcastDao.upsert(row(FEED_SHOW).copy(sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = NEW))
        val list = playlists.createPlaylist("Crime", listOf(OLD))

        assertTrue(repository.renumberedFollows.adoptAnswer(OLD, answer()))

        assertEquals(listOf(FEED_SHOW), playlists.shows(list.id).first().map { it.podcastId })
    }

    @Test
    fun theShowPagesFollowIdIsTheIdTheFollowIsStoredUnder() = runTest {
        assertNull("not followed", repository.followIdOf(NEW, NEW))

        podcastDao.upsert(row(NEW))
        assertEquals(NEW, repository.followIdOf(NEW, NEW))

        podcastDao.upsert(row(NEW).copy(isSubscribed = false))
        podcastDao.upsert(row(FEED_SHOW).copy(sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = NEW))
        assertEquals("followed through its linked feed", FEED_SHOW, repository.followIdOf(NEW, NEW))

        podcastDao.upsert(row(FEED_SHOW).copy(isSubscribed = false, sourceType = PodcastEntity.SOURCE_RSS, linkedPodcastIndexId = NEW))
        podcastDao.upsert(row(OLD))
        assertEquals("followed as the show the page was opened as", OLD, repository.followIdOf(OLD, NEW))

        assertTrue(shows.record(OLD, NEW))
        assertEquals("followed under a retired number", OLD, repository.followIdOf(NEW, NEW))
    }

    private fun answer() = Podcast(id = NEW, title = "The Show", artist = "Host", imageUrl = "https://img.example/new.jpg", feedUrl = FEED)

    private fun row(id: String) = PodcastEntity(
        podcastId = id,
        title = "The Show",
        author = "Host",
        imageUrl = "https://img.example/$id.jpg",
        description = null,
        isSubscribed = true,
        subscribedAt = FOLLOWED_AT,
        isDirty = false,
        feedUrl = FEED,
    )

    private companion object {
        const val OLD = "1258562"
        const val NEW = "7976624"
        const val FEED_SHOW = "rss:abc"
        const val FOLLOWED_AT = 1_700_000_000_000L
        const val FEED = "https://feeds.example/public/shows/the-show"
    }
}
