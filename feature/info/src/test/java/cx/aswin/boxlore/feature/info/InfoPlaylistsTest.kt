package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cx.aswin.boxlore.core.catalog.PlaylistAddResult
import cx.aswin.boxlore.core.catalog.PlaylistCreated
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistEpisode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Add to playlist on the show and episode pages: the sheet stays open with its unsaved new playlist
 * name while the window crosses the 600dp, 480dp and 840dp gates both ways and through recreation,
 * then adds in the page's scope and reports what happened.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfoPlaylistsTest {
    @get:Rule val compose = createComposeRule()

    private val repository = FakeRepository()
    private val playlists = InfoPlaylists.create(repository, CoroutineScope(Dispatchers.Main.immediate), InfoPlaylists.SOURCE_SHOW_PAGE)!!
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val episodes = listOf(Episode("1", "One", "", "https://cdn.example/1.mp3"), Episode("2", "Two", "", "https://cdn.example/2.mp3"))
    private lateinit var picker: InfoPlaylistPicker
    private val snackbar = SnackbarHostState()
    private var added = 0

    private val windows = listOf(
        BoxloreWindowLayout(599f, 900f),
        BoxloreWindowLayout(600f, 900f),
        BoxloreWindowLayout(1280f, 800f),
        BoxloreWindowLayout(1280f, 479f),
        BoxloreWindowLayout(839f, 1180f),
        BoxloreWindowLayout(841f, 600f),
        BoxloreWindowLayout(360f, 740f),
    )

    private val content: @androidx.compose.runtime.Composable () -> Unit = {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                picker = rememberInfoPlaylistPicker("show")
                InfoPlaylistPickerHost(picker, playlists, episodes, snackbar, onAdded = { added++ })
            }
        }
    }

    @Test
    fun `show page adds carry the page's show details the episodes lack`() {
        val show = cx.aswin.boxlore.core.model.Podcast(id = "77", title = "Show", artist = "Host", imageUrl = "https://img.example/show.jpg", genre = "History")
        val bare = Episode("1", "One", "", "https://cdn.example/1.mp3", podcastTitle = "Own title")
        val other = Episode("2", "Two", "", "https://cdn.example/2.mp3", podcastId = "88")

        val (filled, untouched) = InfoPlaylists.withShow(listOf(bare, other), show)

        assertEquals("77", filled.podcastId)
        assertEquals("Own title", filled.podcastTitle)
        assertEquals("Host", filled.podcastArtist)
        assertEquals("History", filled.podcastGenre)
        assertEquals("https://img.example/show.jpg", filled.podcastImageUrl)
        assertEquals("an episode of another show keeps its own details", other, untouched)
        assertEquals(listOf(bare), InfoPlaylists.withShow(listOf(bare), null))
    }

    @Test
    fun `a new playlist name survives window changes, then creates it with the episodes`() {
        compose.setContent(content)
        compose.runOnIdle { picker.open = true }
        compose.onNodeWithText("New playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Weekend")

        windows.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).assertTextContains("Weekend")
        }

        compose.onNodeWithText("Create and add").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("Weekend" to listOf("1", "2")), repository.created)
            assertFalse(picker.open)
            assertNull(picker.sheet.newName)
            assertEquals(1, added)
            assertEquals("Added 2 episodes to Weekend", snackbar.currentSnackbarData?.visuals?.message)
        }
    }

    @Test
    fun `the open sheet and its draft survive recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent(content)
        compose.runOnIdle { picker.open = true }
        compose.onNodeWithText("New playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Late")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNode(hasSetTextAction()).assertTextContains("Late")
    }

    @Test
    fun `picking a playlist adds and says what was already there`() {
        repository.lists.value = listOf(Playlist("p1", "Commute", 0L, 0L, 1))
        repository.nextResult = PlaylistAddResult(added = 1, alreadyIn = 1)
        compose.setContent(content)
        compose.runOnIdle { picker.open = true }
        compose.onNodeWithText("Commute").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("p1" to listOf("1", "2")), repository.adds)
            assertEquals("Added to Commute. 1 was already in it.", snackbar.currentSnackbarData?.visuals?.message)
        }
    }

    private class FakeRepository : PlaylistRepository {
        val lists = MutableStateFlow<List<Playlist>>(emptyList())
        val created = mutableListOf<Pair<String, List<String>>>()
        val adds = mutableListOf<Pair<String, List<String>>>()
        var nextResult = PlaylistAddResult(0)

        override val playlists: Flow<List<Playlist>> = lists
        override fun playlist(playlistId: String): Flow<Playlist?> = lists.map { all -> all.firstOrNull { it.id == playlistId } }
        override fun episodes(playlistId: String): Flow<List<PlaylistEpisode>> = MutableStateFlow(emptyList())
        override suspend fun getPlaylists(): List<Playlist> = lists.value
        override suspend fun getEpisodes(playlistId: String): List<PlaylistEpisode> = emptyList()
        override suspend fun createPlaylist(name: String, episodes: List<Episode>): PlaylistCreated {
            created += name to episodes.map { it.id }
            return PlaylistCreated(Playlist("new", name, 0L, 0L, episodes.size), PlaylistAddResult(added = episodes.size))
        }
        override suspend fun renamePlaylist(playlistId: String, name: String) = true
        override suspend fun deletePlaylist(playlistId: String) = Unit
        override suspend fun addEpisodes(playlistId: String, episodes: List<Episode>): PlaylistAddResult {
            adds += playlistId to episodes.map { it.id }
            return nextResult
        }
        override suspend fun removeEpisodes(playlistId: String, itemIds: Collection<Long>) = emptyList<PlaylistEpisode>()
        override suspend fun restoreEpisodes(removed: List<PlaylistEpisode>) = 0
        override suspend fun reorder(playlistId: String, orderedItemIds: List<Long>) = Unit
        override suspend fun importPlaylist(playlist: Playlist, episodes: List<PlaylistEpisode>) = PlaylistAddResult(0)
        override suspend fun deleteAll() = Unit
    }
}
