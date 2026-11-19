package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import cx.aswin.boxlore.feature.player.QueueSheetActions
import cx.aswin.boxlore.feature.player.QueueSheetContent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Save queue as playlist: offered in the phone queue header and at the top of the tablet Up Next
 * pane; its dialog keeps the typed name while the window crosses the 600dp, 480dp and 840dp gates
 * both ways (the draft lives in the player's retained UI state), then saves the queue in order.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SaveQueueAsPlaylistTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val ui = FullPlayerUiState()
    private val created = mutableListOf<Pair<String, List<String>>>()
    private val snackbar = SnackbarHostState()
    private var opened = 0
    private var showHeader by mutableStateOf(true)
    private val queue = listOf("now", "next", "later").map { Episode(it, it, "", "https://cdn.example/$it.mp3") }

    private val windows = listOf(
        BoxloreWindowLayout(599f, 900f),
        BoxloreWindowLayout(600f, 900f),
        BoxloreWindowLayout(1280f, 800f),
        BoxloreWindowLayout(1280f, 479f),
        BoxloreWindowLayout(839f, 1180f),
        BoxloreWindowLayout(841f, 600f),
        BoxloreWindowLayout(360f, 740f),
    )

    @Test
    fun `the phone header and the tablet pane both offer it`() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                QueueSheetContent(
                    queue = queue.drop(1),
                    currentPodcast = null,
                    colorScheme = MaterialTheme.colorScheme,
                    actions = QueueSheetActions(onPlayEpisode = {}, onRemoveEpisode = {}, onClose = {}, onSaveAsPlaylist = { opened++ }),
                    showHeader = showHeader,
                    scrollContinuation = !showHeader,
                )
            }
        }
        compose.onNodeWithContentDescription("Save queue as playlist").performClick()
        compose.runOnIdle { showHeader = false }
        compose.onNodeWithText("Save queue as playlist").performClick()
        compose.runOnIdle { assertEquals(2, opened) }
    }

    @Test
    fun `the dialog keeps its name across window changes, then saves the queue in order`() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    SaveQueueAsPlaylistDialog(Repository(), ui, queue, rememberCoroutineScope(), snackbar)
                }
            }
        }
        compose.runOnIdle { ui.saveQueueName = "" }
        compose.onNodeWithText("Saves the episode playing now and 2 episodes up next.").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Road trip")

        windows.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).assertTextContains("Road trip")
        }

        compose.onNodeWithText("Save").performClick()
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(listOf("Road trip" to listOf("now", "next", "later")), created)
            assertNull(ui.saveQueueName)
            assertEquals("Saved as Road trip", snackbar.currentSnackbarData?.visuals?.message)
        }
    }

    private inner class Repository : PlaylistRepository {
        override val playlists: Flow<List<Playlist>> = flowOf(emptyList())
        override fun playlist(playlistId: String): Flow<Playlist?> = flowOf(null)
        override fun episodes(playlistId: String): Flow<List<PlaylistEpisode>> = flowOf(emptyList())
        override suspend fun getPlaylists(): List<Playlist> = emptyList()
        override suspend fun getEpisodes(playlistId: String): List<PlaylistEpisode> = emptyList()
        override suspend fun createPlaylist(name: String, episodes: List<Episode>): PlaylistCreated {
            created += name to episodes.map { it.id }
            return PlaylistCreated(Playlist("p", name, 0L, 0L, episodes.size), PlaylistAddResult(episodes.size))
        }
        override suspend fun renamePlaylist(playlistId: String, name: String) = true
        override suspend fun deletePlaylist(playlistId: String) = Unit
        override suspend fun addEpisodes(playlistId: String, episodes: List<Episode>) = PlaylistAddResult(0)
        override suspend fun removeEpisodes(playlistId: String, itemIds: Collection<Long>) = emptyList<PlaylistEpisode>()
        override suspend fun restoreEpisodes(removed: List<PlaylistEpisode>) = 0
        override suspend fun reorder(playlistId: String, orderedItemIds: List<Long>) = Unit
        override suspend fun importPlaylist(playlist: Playlist, episodes: List<PlaylistEpisode>) = PlaylistAddResult(0)
        override suspend fun deleteAll() = Unit
    }
}
