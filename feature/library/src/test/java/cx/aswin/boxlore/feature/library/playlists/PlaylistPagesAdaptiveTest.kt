package cx.aswin.boxlore.feature.library.playlists

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Playlists and playlist pages keep their open dialog, its unsaved name and a reorder not yet
 * saved while the window moves between phone, tablet, split and short layouts, both ways, and when
 * the activity is recreated; Create, Save, Delete, Play and Back still do the current thing afterwards.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistPagesAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private var showDetail by mutableStateOf(true)
    private lateinit var state: PlaylistPagesState
    private val owners = mutableSetOf<PlaylistPagesState>()

    private val created = mutableListOf<String>()
    private val renamed = mutableListOf<Pair<String, String>>()
    private val deleted = mutableListOf<String>()
    private val plays = mutableListOf<Pair<Long?, List<Long>>>()
    private val savedOrders = mutableListOf<List<Long>>()

    private val commute = Playlist("p1", "Commute", 1L, 1L, episodeCount = 3)
    private val rows = listOf("Alpha", "Bravo", "Charlie").mapIndexed { index, title ->
        PlaylistEpisode(itemId = index + 1L, playlistId = "p1", position = index, addedAt = 0L, episode = Episode("e$index", title, "", "https://cdn.example/$index.mp3", podcastTitle = "Show"))
    }

    /** Both sides of the 600dp width and 480dp height gates, the 840dp split, and back to phone. */
    private val windows = listOf(
        BoxloreWindowLayout(599f, 900f),
        BoxloreWindowLayout(600f, 900f),
        BoxloreWindowLayout(1280f, 800f),
        BoxloreWindowLayout(1280f, 479f),
        BoxloreWindowLayout(839f, 1180f),
        BoxloreWindowLayout(841f, 600f),
        BoxloreWindowLayout(360f, 740f),
    )

    private val actions = object : PlaylistDetailActions {
        override fun open(playlistId: String) = Unit
        override fun create(name: String) {
            created += name
        }
        override fun rename(playlistId: String, name: String) {
            renamed += playlistId to name
        }
        override fun delete(playlistId: String) {
            deleted += playlistId
        }
        override fun play(startItemId: Long?, order: List<Long>) {
            plays += startItemId to order
        }
        override fun remove(entry: PlaylistEpisode) = Unit
        override fun saveOrder(order: List<Long>) {
            savedOrders += order
        }
        override fun openEpisode(episode: Episode) = Unit
    }

    @Composable
    private fun listPage() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                state = rememberPlaylistPagesState().also { owners += it }
                PlaylistsContent(listOf(commute), state, actions, onBack = {})
            }
        }
    }

    @Composable
    private fun detailPage() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                state = rememberPlaylistPagesState().also { owners += it }
                if (showDetail) PlaylistDetailContent(PlaylistDetailState.Ready(commute, rows, completedIds = emptySet()), state, actions, onBack = {})
            }
        }
    }

    private fun moveThroughWindows(check: () -> Unit) {
        windows.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            check()
        }
        compose.runOnIdle { assertEquals("one state owner across every window", 1, owners.size) }
    }

    @Test
    fun `a new playlist keeps its unsaved name across window changes, then creates`() {
        compose.setContent { listPage() }
        compose.onNodeWithContentDescription("New playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Road trip")

        moveThroughWindows { compose.onNode(hasSetTextAction()).assertTextContains("Road trip") }

        compose.onNodeWithText("Create").performClick()
        compose.runOnIdle {
            assertEquals(listOf("Road trip"), created)
            assertNull(state.editor)
        }
    }

    @Test
    fun `a rename keeps its draft through recreation and Cancel leaves the name`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { listPage() }
        compose.onNodeWithContentDescription("More options for Commute").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Drive")

        restoration.emulateSavedInstanceStateRestore()

        compose.onNode(hasSetTextAction()).assertTextContains("Drive")
        compose.runOnIdle { assertEquals(PlaylistEditor.Rename("p1"), state.editor) }
        compose.onNodeWithText("Cancel").performClick()
        compose.runOnIdle {
            assertEquals(emptyList<Pair<String, String>>(), renamed)
            assertNull(state.editor)
        }
    }

    @Test
    fun `delete asks first and its dialog survives window changes`() {
        compose.setContent { listPage() }
        compose.onNodeWithContentDescription("More options for Commute").performClick()
        compose.onNodeWithText("Delete").performClick()

        moveThroughWindows { compose.onNodeWithText("Delete playlist?").assertIsDisplayed() }

        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(listOf("p1"), deleted) }
    }

    @Test
    fun `a reorder not yet saved stays as shown across window changes, plays in that order and is saved when the page goes`() {
        compose.setContent { detailPage() }
        compose.runOnIdle { state.move(rows.map { it.itemId }, fromId = 3L, toId = 1L) }

        moveThroughWindows {
            compose.onNodeWithText("Charlie").assertIsDisplayed()
            compose.runOnIdle { assertEquals(listOf(3L, 1L, 2L), state.pendingOrder) }
        }
        val owner = state

        compose.onNodeWithText("Play All").performClick()
        compose.runOnIdle { assertEquals(null to listOf(3L, 1L, 2L), plays.single()) }

        compose.runOnIdle { showDetail = false }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(owner, state)
            assertEquals(listOf(listOf(3L, 1L, 2L)), savedOrders)
        }
    }

    @Test
    fun `move down from TalkBack saves at once, and rename on the playlist page survives window changes`() {
        compose.setContent { detailPage() }
        val moves = compose.onNodeWithText("Alpha").fetchSemanticsNode().config[SemanticsActions.CustomActions]
        compose.runOnIdle { moves.first { it.label == "Move down" }.action() }
        compose.runOnIdle { assertEquals(listOf(listOf(2L, 1L, 3L)), savedOrders) }

        compose.onNodeWithContentDescription("More options for Commute").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Evening")
        moveThroughWindows { compose.onNode(hasSetTextAction()).assertTextContains("Evening") }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf("p1" to "Evening"), renamed) }
    }
}
