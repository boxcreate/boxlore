package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.playlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun playlist(id: String, name: String = "List $id", createdAt: Long = 1L,) =
        dao.insertPlaylist(PlaylistEntity(playlistId = id, name = name, createdAt = createdAt, updatedAt = createdAt))

    private fun item(playlistId: String, episodeId: String, addedAt: Long = 10L, image: String? = null, showImage: String? = null,) =
        PlaylistItemEntity(
            playlistId = playlistId,
            position = 99,
            addedAt = addedAt,
            episodeId = episodeId,
            title = "Episode $episodeId",
            podcastId = "show",
            podcastTitle = "Show",
            podcastImageUrl = showImage,
            imageUrl = image,
            audioUrl = "https://cdn.example/$episodeId.mp3",
            duration = 60,
            pubDate = 0L,
            description = null,
        )

    private suspend fun episodeIds(playlistId: String) = dao.observeItems(playlistId).first().map { it.episodeId }

    @Test
    fun appendAddsAfterTheLastEpisodeOnceAndTouchesThePlaylist() = runTest {
        playlist("a")
        dao.appendItem(item("a", "1", addedAt = 20L))
        dao.appendItem(item("a", "2", addedAt = 30L))
        assertNull("already in the playlist", dao.appendItem(item("a", "1", addedAt = 40L)))
        assertNull("no such playlist", dao.appendItem(item("missing", "1")))

        assertEquals(listOf("1", "2"), episodeIds("a"))
        assertEquals(listOf(0, 1), dao.getItems("a").map { it.position })
        assertEquals(30L, dao.getPlaylist("a")!!.updatedAt)
        assertEquals(2, dao.countItems("a"))
        assertTrue(dao.getAllItems().none { it.playlistId == "missing" })
    }

    @Test
    fun appendItemsCountsOnlyTheNewOnes() = runTest {
        playlist("a")
        dao.appendItem(item("a", "1"))
        assertEquals(2, dao.appendItems(listOf(item("a", "1"), item("a", "2"), item("a", "3"))))
        assertEquals(listOf("1", "2", "3"), episodeIds("a"))
        assertEquals(listOf("2"), dao.episodeIdsIn("a", listOf("2", "9")))
    }

    @Test
    fun theSameEpisodeCanBeInTwoPlaylists() = runTest {
        playlist("a")
        playlist("b", createdAt = 2L)
        dao.appendItem(item("a", "1"))
        dao.appendItem(item("b", "1"))
        assertEquals(listOf("1"), episodeIds("a"))
        assertEquals(listOf("1"), episodeIds("b"))
    }

    @Test
    fun summariesCountEpisodesIncludingEmptyPlaylistsOldestFirst() = runTest {
        playlist("new", createdAt = 5L)
        playlist("old", createdAt = 1L)
        dao.appendItems(listOf(item("old", "1"), item("old", "2")))

        val summaries = dao.observePlaylistSummaries().first()
        assertEquals(listOf("old" to 2, "new" to 0), summaries.map { it.playlist.playlistId to it.itemCount })
        assertEquals(2, dao.countPlaylists())
    }

    @Test
    fun coversAreTheFirstFourEpisodesArtworkInOrder() = runTest {
        playlist("a")
        dao.appendItems((1..6).map { item("a", "$it", image = "https://img/$it.jpg") })
        dao.appendItem(item("a", "blank", image = "", showImage = "https://img/show.jpg"))
        dao.reorder("a", listOf(dao.getItems("a").last().id))

        val covers = dao.observePlaylistCovers().first()
        assertEquals(
            listOf("https://img/show.jpg", "https://img/1.jpg", "https://img/2.jpg", "https://img/3.jpg"),
            covers.map { it.imageUrl },
        )
        assertTrue(covers.all { it.playlistId == "a" })
    }

    @Test
    fun reorderPutsListedItemsFirstAndKeepsTheRestInOrder() = runTest {
        playlist("a")
        dao.appendItems(listOf(item("a", "1"), item("a", "2"), item("a", "3"), item("a", "4")))
        val ids = dao.getItems("a").associate { it.episodeId to it.id }

        dao.reorder("a", listOf(ids.getValue("3"), ids.getValue("1"), 9_999L, ids.getValue("3")))

        assertEquals(listOf("3", "1", "2", "4"), episodeIds("a"))
        assertEquals(listOf(0, 1, 2, 3), dao.getItems("a").map { it.position })
    }

    @Test
    fun reorderLeavesOtherPlaylistsAlone() = runTest {
        playlist("a")
        playlist("b", createdAt = 2L)
        dao.appendItems(listOf(item("a", "1"), item("a", "2"), item("b", "x"), item("b", "y")))
        val other = dao.getItems("b").first { it.episodeId == "y" }.id

        dao.reorder("a", listOf(other))

        assertEquals(listOf("1", "2"), episodeIds("a"))
        assertEquals(listOf("x", "y"), episodeIds("b"))
    }

    @Test
    fun renameSetsTheNameAndUpdatedAt() = runTest {
        playlist("a", name = "Old")
        assertEquals(1, dao.renamePlaylist("a", "New", updatedAt = 50L))
        assertEquals(0, dao.renamePlaylist("missing", "New", updatedAt = 50L))
        val renamed = dao.observePlaylist("a").first()!!
        assertEquals("New" to 50L, renamed.name to renamed.updatedAt)
        assertEquals(1L, renamed.createdAt)
    }

    @Test
    fun removingItemsByIdOrEpisodeTouchesOnlyThatPlaylist() = runTest {
        playlist("a")
        playlist("b", createdAt = 2L)
        dao.appendItems(listOf(item("a", "1"), item("a", "2"), item("a", "3"), item("b", "2")))

        dao.deleteItem(dao.getItems("a").first { it.episodeId == "1" }.id)
        assertEquals(1, dao.deleteItemsByEpisodeIds("a", listOf("2", "9")))

        assertEquals(listOf("3"), episodeIds("a"))
        assertEquals(listOf("2"), episodeIds("b"))
    }

    @Test
    fun deletingAPlaylistDeletesItsItems() = runTest {
        playlist("a")
        playlist("b", createdAt = 2L)
        dao.appendItems(listOf(item("a", "1"), item("b", "2")))

        dao.deletePlaylist("a")

        assertNull(dao.getPlaylist("a"))
        assertEquals(listOf("2"), dao.getAllItems().map { it.episodeId })
    }

    @Test
    fun deleteAllEmptiesBothTables() = runTest {
        playlist("a")
        dao.appendItem(item("a", "1"))
        dao.deleteAll()
        assertEquals(0, dao.countPlaylists())
        assertTrue(dao.getAllItems().isEmpty())
        assertTrue(dao.getAllPlaylists().isEmpty())
    }

    @Test
    fun itemsConvertToEpisodeItemsWithTheShowArtworkFallback() = runTest {
        playlist("a")
        dao.appendItem(item("a", "42", image = null, showImage = "https://img/show.jpg"))
        val episode = dao.getItems("a").single().toEpisodeItem()
        assertEquals(42L, episode.id)
        assertEquals("https://cdn.example/42.mp3", episode.enclosureUrl)
        assertEquals("https://img/show.jpg", episode.feedImage)
    }
}
