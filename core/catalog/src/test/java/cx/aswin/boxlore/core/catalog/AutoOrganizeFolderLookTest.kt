package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.runBlocking
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

/** Genre folders made when a show is followed use the look saved on the Auto-organize page. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoOrganizeFolderLookTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var prefs: UserPreferencesRepository
    private lateinit var folders: RoomFolderRepository
    private lateinit var subscriptions: SubscriptionRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        prefs = UserPreferencesRepository(context)
        resetPrefs()
        folders = RoomFolderRepository(database.folderDao(), database.podcastDao())
        subscriptions = SubscriptionRepository(database.podcastDao(), folderRepository = folders, userPreferencesRepository = prefs)
    }

    @After
    fun tearDown() {
        resetPrefs()
        database.close()
    }

    private fun resetPrefs() = runBlocking {
        prefs.setAutoOrganizeFolders(false)
        prefs.setAutoOrganizeLook(folderSizeName = null, tileCovers = true)
    }

    private fun podcast(id: String, genre: String) = Podcast(
        id = id,
        title = "Show $id",
        artist = "Artist",
        imageUrl = "https://example.com/$id.jpg",
        description = "desc",
        genre = genre,
    )

    @Test
    fun followingAShowWithAutoOnMakesItsGenreFolderInTheSavedLook() = runTest {
        prefs.setAutoOrganizeFolders(true)
        prefs.setAutoOrganizeLook(folderSizeName = FolderDisplaySize.PANEL.name, tileCovers = false)

        subscriptions.subscribe(podcast("pod-1", genre = "News"))

        val news = folders.getFolders().single { it.linkedGenre == "News" }
        // One show would get a compact tile by size; the saved look wins.
        assertEquals(FolderDisplaySize.PANEL, news.displaySize)
        assertEquals(listOf("pod-1"), news.podcastIds)
    }

    @Test
    fun withoutASavedLookTheFolderIsSizedByItsShows() = runTest {
        prefs.setAutoOrganizeFolders(true)

        subscriptions.subscribe(podcast("pod-1", genre = "News"))

        val news = folders.getFolders().single { it.linkedGenre == "News" }
        assertEquals(FolderDisplaySize.COMPACT, news.displaySize)
        assertTrue(news.showPodcastGrid)
    }

    @Test
    fun savedLookReadsBackAndIgnoresUnknownSizes() = runTest {
        assertEquals(AutoOrganizeFolderLook(fixedSize = null, tileCovers = true), prefs.autoOrganizeFolderLook())

        prefs.setAutoOrganizeLook(folderSizeName = FolderDisplaySize.COMPACT.name, tileCovers = false)
        val saved = prefs.autoOrganizeFolderLook()
        assertEquals(FolderDisplaySize.COMPACT, saved.fixedSize)
        assertFalse(saved.tileCovers)

        assertNull(savedFolderDisplaySize("NOT_A_SIZE"))
        assertNull(savedFolderDisplaySize(null))
    }
}
