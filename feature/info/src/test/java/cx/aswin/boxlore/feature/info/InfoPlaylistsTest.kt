package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PlaylistRepository
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.PlaylistShow
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The show page's Add to playlist: the sheet keeps its unsaved new playlist name while the window
 * crosses the 600dp, 480dp and 840dp gates both ways and through recreation; each playlist shows
 * whether it holds the show and a tap toggles it; a new playlist holds the show. Each change sends
 * one `playlist_modified` from the show page. A show that isn't followed gets no sheet.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class InfoPlaylistsTest {
    @get:Rule val compose = createComposeRule()

    private val repository = FakeRepository()
    private val playlists = InfoPlaylists.create(repository, CoroutineScope(Dispatchers.Main.immediate))!!
    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val show = mutableStateOf<Podcast?>(Podcast(id = "77", title = "The Show", artist = "Host", imageUrl = ""))
    private lateinit var picker: InfoPlaylistPicker
    private val snackbar = SnackbarHostState()
    private val events = mutableListOf<Pair<String, Map<String, Any>>>()
    private lateinit var restore: () -> Unit

    private val windows = listOf(
        BoxloreWindowLayout(599f, 900f),
        BoxloreWindowLayout(600f, 900f),
        BoxloreWindowLayout(1280f, 800f),
        BoxloreWindowLayout(1280f, 479f),
        BoxloreWindowLayout(1280f, 481f),
        BoxloreWindowLayout(839f, 1180f),
        BoxloreWindowLayout(841f, 600f),
        BoxloreWindowLayout(360f, 740f),
    )

    private val content: @androidx.compose.runtime.Composable () -> Unit = {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                picker = rememberInfoPlaylistPicker("show")
                InfoPlaylistPickerHost(picker, playlists, show.value, snackbar)
            }
        }
    }

    @Before fun record() {
        restore = AnalyticsHelper.installRecordingSink(events)
    }

    @After fun stopRecording() = restore()

    private fun modified() = events.filter { it.first == "playlist_modified" }.map { it.second["action"] to it.second["source"] }

    @Test
    fun `a new playlist name survives window changes, then creates it with the show`() {
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
            assertEquals(listOf("Weekend" to listOf("77")), repository.created)
            assertFalse(picker.open)
            assertNull(picker.sheet.newName)
            assertEquals("Added to Weekend", snackbar.currentSnackbarData?.visuals?.message)
            assertEquals(listOf("create" to "show_page"), modified())
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
    fun `a tap adds the show to a playlist or takes it out, and the sheet stays open`() {
        repository.lists.value = listOf(Playlist("p1", "Comedy", 0L, 0L, showCount = 2), Playlist("p2", "History", 0L, 0L, showCount = 1))
        repository.members.value = setOf("p2")
        compose.setContent(content)
        compose.runOnIdle { picker.open = true }

        compose.onNode(hasText("History") and isToggleable()).assertIsOn()
        compose.onNode(hasText("Comedy") and isToggleable()).assertIsOff().performClick()
        compose.waitForIdle()
        compose.onNode(hasText("Comedy") and isToggleable()).assertIsOn()
        compose.runOnIdle { assertEquals("Added to Comedy", snackbar.currentSnackbarData?.visuals?.message) }

        compose.onNode(hasText("History") and isToggleable()).performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("p1" to "77"), repository.added)
            assertEquals(listOf("p2" to "77"), repository.removed)
            assertEquals("Removed from History", snackbar.currentSnackbarData?.visuals?.message)
            assertEquals(listOf("add_show" to "show_page", "remove_show" to "show_page"), modified())
            assertEquals(true, picker.open)
        }
        compose.onNode(hasText("History") and isToggleable()).assertIsOff()
    }

    @Test
    fun `a show that isn't followed gets no sheet`() {
        show.value = null
        compose.setContent(content)
        compose.runOnIdle { picker.open = true }
        compose.onNodeWithText("Add to playlist").assertDoesNotExist()
    }

    @Test
    fun `a show followed through its feed or a retired number goes into playlists under that follow's ID`() {
        val asked = mutableListOf<String>()
        val linked = InfoPlaylists.create(repository, CoroutineScope(Dispatchers.Unconfined)) { shown ->
            asked += shown
            "rss:$shown"
        }!!
        repository.lists.value = listOf(Playlist("p1", "Comedy", 0L, 0L, showCount = 0))

        linked.toggle(repository.lists.value.single(), "77", isMember = false) {}
        linked.toggle(repository.lists.value.single(), "77", isMember = true) {}
        linked.create("Weekend", "77") {}
        kotlinx.coroutines.runBlocking { linked.memberIds("77").first() }

        assertEquals(listOf("p1" to "rss:77"), repository.added)
        assertEquals(listOf("p1" to "rss:77"), repository.removed)
        assertEquals(listOf("Weekend" to listOf("rss:77")), repository.created)
        assertEquals(listOf("rss:77"), repository.memberQueries)
        assertEquals(listOf("77", "77", "77", "77"), asked)
    }

    private class FakeRepository : PlaylistRepository {
        val lists = MutableStateFlow<List<Playlist>>(emptyList())
        val members = MutableStateFlow<Set<String>>(emptySet())
        val created = mutableListOf<Pair<String, List<String>>>()
        val added = mutableListOf<Pair<String, String>>()
        val removed = mutableListOf<Pair<String, String>>()
        val memberQueries = mutableListOf<String>()

        override val playlists: Flow<List<Playlist>> = lists
        override fun playlist(playlistId: String): Flow<Playlist?> = lists.map { all -> all.firstOrNull { it.id == playlistId } }
        override fun shows(playlistId: String): Flow<List<PlaylistShow>> = MutableStateFlow(emptyList())
        override fun playlistIdsForShow(podcastId: String): Flow<Set<String>> = members.also { memberQueries += podcastId }
        override suspend fun getPlaylists(): List<Playlist> = lists.value
        override suspend fun getShowIds(): Map<String, List<String>> = emptyMap()
        override suspend fun createPlaylist(name: String, podcastIds: List<String>, mix: PlaylistMix, perShowLimit: Int?, iconKey: String?): Playlist {
            created += name to podcastIds
            return Playlist("new", name, 0L, 0L, podcastIds.size)
        }
        override suspend fun renamePlaylist(playlistId: String, name: String) = true
        override suspend fun setPlayback(playlistId: String, mix: PlaylistMix, perShowLimit: Int?) = true
        override suspend fun setIcon(playlistId: String, iconKey: String?) = true
        override suspend fun deletePlaylist(playlistId: String) = Unit
        override suspend fun addShows(playlistId: String, podcastIds: List<String>): Int {
            podcastIds.forEach { added += playlistId to it }
            members.value = members.value + playlistId
            return podcastIds.size
        }
        override suspend fun removeShow(playlistId: String, podcastId: String): PlaylistShow {
            removed += playlistId to podcastId
            members.value = members.value - playlistId
            return PlaylistShow(playlistId, podcastId, 0, 0L, "Show", null, isFollowed = true)
        }
        override suspend fun replaceShow(oldPodcastId: String, newPodcastId: String) = Unit
        override suspend fun restoreShow(removed: PlaylistShow) = true
        override suspend fun nextEpisodes(playlistId: String, limit: Int, excludeIds: Collection<String>) = emptyList<Episode>()
        override suspend fun importPlaylist(playlist: Playlist, podcastIds: List<String>) = 0
        override suspend fun deleteAll() = Unit
    }
}
