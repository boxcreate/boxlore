package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PLAYLIST_EPISODE_LIMIT
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.Transcript
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomPlaylistRepositoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var repository: RoomPlaylistRepository
    private var clock = 1_000L
    private var ids = 0

    /** 1 and 100 are one episode stored under two IDs. */
    private val aliases = mapOf("1" to listOf("1", "100"), "100" to listOf("1", "100"))

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomPlaylistRepository(
            dao = database.playlistDao(),
            episodeIds = { aliases[it] ?: listOf(it) },
            now = { clock++ },
            newId = { "list-${++ids}" },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun episode(id: String, show: String = "show") = Episode(
        id = id,
        title = "Episode $id",
        description = "Notes $id",
        audioUrl = "https://cdn.example/$id.mp3",
        imageUrl = "https://img.example/$id.jpg",
        podcastImageUrl = "https://img.example/$show.jpg",
        podcastTitle = "Show $show",
        podcastId = show,
        duration = 60,
        publishedDate = 5L,
    )

    @Test
    fun `a new playlist keeps its episodes in order with their copy`() = runTest {
        val rich = episode("7").copy(
            persons = listOf(Person(name = "Host", role = "host")),
            transcripts = listOf(Transcript(url = "https://t.example/7.vtt", type = "text/vtt")),
            seasonNumber = 2,
            episodeNumber = 3,
            enclosureType = "audio/mpeg",
        )
        val created = repository.createPlaylist("  Road   trip ", listOf(episode("2"), rich))

        assertEquals("Road trip", created.playlist.name)
        assertEquals(2, created.result.added)
        val episodes = repository.episodes(created.playlist.id).first()
        assertEquals(listOf("2", "7"), episodes.map { it.episode.id })
        assertEquals(rich, episodes[1].episode)
        val listed = repository.playlists.first().single()
        assertEquals(2, listed.episodeCount)
        assertEquals(listOf("https://img.example/2.jpg", "https://img.example/7.jpg"), listed.coverUrls)
        assertEquals("the created playlist matches the listed one", listed, created.playlist)
    }

    @Test
    fun `a new playlist reports only its own episodes, not other playlists'`() = runTest {
        repository.createPlaylist("Other", (1..6).map { episode("o$it") })

        val created = repository.createPlaylist("New", listOf(episode("n1")))
        val empty = repository.createPlaylist("Empty")

        assertEquals(1, created.playlist.episodeCount)
        assertEquals(listOf("https://img.example/n1.jpg"), created.playlist.coverUrls)
        assertEquals(0, empty.playlist.episodeCount)
        assertEquals(emptyList<String>(), empty.playlist.coverUrls)
    }

    @Test
    fun `an episode is added once across its stored IDs`() = runTest {
        val id = repository.createPlaylist("Mix", listOf(episode("1"))).playlist.id

        val result = repository.addEpisodes(id, listOf(episode("100"), episode("2"), episode("2")))

        assertEquals(PlaylistAddResult(added = 1, alreadyIn = 2), result)
        assertEquals(listOf("1", "2"), repository.getEpisodes(id).map { it.episode.id })
    }

    @Test
    fun `a full playlist says what didn't fit`() = runTest {
        val id = repository.createPlaylist("Big", (1..PLAYLIST_EPISODE_LIMIT - 1).map { episode("e$it") }).playlist.id

        val result = repository.addEpisodes(id, listOf(episode("x"), episode("y"), episode("z")))

        assertEquals(PlaylistAddResult(added = 1, didNotFit = 2), result)
        assertTrue(repository.getPlaylists().single().isFull)
        assertEquals(PlaylistAddResult(added = 0, didNotFit = 1), repository.addEpisodes(id, listOf(episode("w"))))
    }

    @Test
    fun `removing then undoing puts the episode back in its place`() = runTest {
        val id = repository.createPlaylist("Mix", listOf(episode("a"), episode("b"), episode("c"))).playlist.id
        val middle = repository.getEpisodes(id)[1]

        val removed = repository.removeEpisodes(id, listOf(middle.itemId))
        assertEquals(listOf("a", "c"), repository.getEpisodes(id).map { it.episode.id })

        assertEquals(1, repository.restoreEpisodes(removed))
        assertEquals(listOf("a", "b", "c"), repository.getEpisodes(id).map { it.episode.id })
        assertEquals(0, repository.restoreEpisodes(removed))
    }

    @Test
    fun `undo does nothing once the playlist is gone`() = runTest {
        val id = repository.createPlaylist("Mix", listOf(episode("a"))).playlist.id
        val removed = repository.removeEpisodes(id, repository.getEpisodes(id).map { it.itemId })
        repository.deletePlaylist(id)

        assertEquals(0, repository.restoreEpisodes(removed))
        assertTrue(repository.getPlaylists().isEmpty())
    }

    @Test
    fun `reorder, rename and delete`() = runTest {
        val id = repository.createPlaylist("Mix", listOf(episode("a"), episode("b"), episode("c"))).playlist.id
        val items = repository.getEpisodes(id)

        repository.reorder(id, listOf(items[2].itemId, items[0].itemId))
        assertEquals(listOf("c", "a", "b"), repository.getEpisodes(id).map { it.episode.id })

        assertFalse(repository.renamePlaylist(id, "   "))
        assertTrue(repository.renamePlaylist(id, "Commute"))
        assertEquals("Commute", repository.playlist(id).first()?.name)

        repository.deletePlaylist(id)
        assertEquals(null, repository.playlist(id).first())
        assertTrue(repository.getEpisodes(id).isEmpty())
    }

    @Test
    fun `a blank name becomes Playlist and episodes without audio are skipped`() = runTest {
        val created = repository.createPlaylist(" ", listOf(episode("a").copy(audioUrl = ""), episode("b")))
        assertEquals("Playlist", created.playlist.name)
        assertEquals(listOf("b"), repository.getEpisodes(created.playlist.id).map { it.episode.id })
    }

    @Test
    fun `importing merges into a playlist the phone has and adds a missing one`() = runTest {
        val local = repository.createPlaylist("Mine", listOf(episode("a"))).playlist
        val backupRows = repository.getEpisodes(local.id).map { it.copy(itemId = 0) } +
            repository.getEpisodes(local.id).map { it.copy(itemId = 0, episode = episode("b"), position = 1) }

        val merged = repository.importPlaylist(local.copy(name = "Theirs"), backupRows)
        val added = repository.importPlaylist(Playlist("from-backup", "Backup", 1L, 2L, 0), backupRows.map { it.copy(playlistId = "from-backup") })

        assertEquals(PlaylistAddResult(added = 1, alreadyIn = 1), merged)
        assertEquals(2, added.added)
        val playlists = repository.getPlaylists().associateBy { it.id }
        assertEquals("Mine", playlists.getValue(local.id).name)
        assertEquals(listOf("a", "b"), repository.getEpisodes(local.id).map { it.episode.id })
        assertEquals("Backup", playlists.getValue("from-backup").name)
        assertEquals(2L, playlists.getValue("from-backup").updatedAt)
    }

    @Test
    fun `deleteAll clears every playlist`() = runTest {
        repository.createPlaylist("One", listOf(episode("a")))
        repository.createPlaylist("Two")
        repository.deleteAll()
        assertTrue(repository.playlists.first().isEmpty())
        assertTrue(database.playlistDao().getAllItems().isEmpty())
    }
}
