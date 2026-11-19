package cx.aswin.boxlore.feature.library.playlists

import android.app.Application
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistOrder
import cx.aswin.boxlore.core.model.PlaylistShow
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Playlists and playlist pages keep what is open (New playlist with its name and picked shows,
 * Add shows, Rename, Delete) while the window moves between phone, tablet, split and short layouts,
 * both ways, and when the activity is recreated; Create, Add, Save, Delete, Play, the order toggle
 * and Remove still do the current thing afterwards, from one state owner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistPagesAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private lateinit var state: PlaylistPagesState
    private val owners = mutableSetOf<PlaylistPagesState>()

    private val created = mutableListOf<Pair<String, List<String>>>()
    private val renamed = mutableListOf<Pair<String, String>>()
    private val deleted = mutableListOf<String>()
    private val added = mutableListOf<List<String>>()
    private val removed = mutableListOf<String>()
    private val orders = mutableListOf<PlaylistOrder>()
    private var plays = 0

    private val followed = listOf("Alpha", "Bravo", "Charlie").map { Podcast(it.lowercase(), it, "Host", "") }
    private val pickSource = PlaylistPickSource(followed, listOf(SubscriptionFolder("f1", "Funny", podcastIds = listOf("bravo", "charlie"))))
    private val comedy = Playlist("p1", "Comedy", 1L, 1L, showCount = 2)
    private val shows = listOf(
        PlaylistShow("p1", "alpha", 0, 0L, "Alpha", null, isFollowed = true),
        PlaylistShow("p1", "gone", 1, 0L, "Gone show", null, isFollowed = false),
    )
    private val ready = PlaylistDetailState.Ready(comedy, shows, comingUp = listOf(Episode("e1", "First up", "", "https://cdn.example/1.mp3", podcastTitle = "Alpha")))

    /** Both sides of the 600dp width and 480dp height gates, the 840dp split, and back to phone. */
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

    private val actions = object : PlaylistDetailActions {
        override fun open(playlistId: String) = Unit
        override fun create(name: String, podcastIds: List<String>) {
            created += name to podcastIds
        }
        override fun rename(playlistId: String, name: String) {
            renamed += playlistId to name
        }
        override fun delete(playlistId: String) {
            deleted += playlistId
        }
        override fun play() {
            plays++
        }
        override fun setOrder(order: PlaylistOrder) {
            orders += order
        }
        override fun addShows(podcastIds: List<String>) {
            added += podcastIds
        }
        override fun removeShow(show: PlaylistShow) {
            removed += show.podcastId
        }
    }

    @Composable
    private fun listPage() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                state = rememberPlaylistPagesState().also { owners += it }
                PlaylistsContent(listOf(comedy), state, actions, onBack = {}, pickSource = pickSource)
            }
        }
    }

    @Composable
    private fun detailPage() {
        BoxLoreTheme(dynamicColor = false) {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                state = rememberPlaylistPagesState().also { owners += it }
                PlaylistDetailContent(ready, state, actions, onBack = {}, pickSource = pickSource)
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
    fun `a new playlist keeps its name and picked shows across window changes, then creates`() {
        compose.setContent { listPage() }
        compose.onNodeWithContentDescription("New playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Laughs")
        compose.onNodeWithText("Funny").performClick()
        compose.onNode(hasText("Alpha") and isToggleable()).performClick()

        moveThroughWindows {
            compose.onNode(hasSetTextAction()).assertTextContains("Laughs")
            compose.onNodeWithText("3 shows picked").assertExists()
        }

        compose.onNodeWithText("Create").performClick()
        compose.runOnIdle {
            assertEquals(listOf("Laughs" to listOf("bravo", "charlie", "alpha")), created)
            assertNull(state.editor)
        }
    }

    @Test
    fun `the picker and its picks survive recreation`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { listPage() }
        compose.onNodeWithContentDescription("New playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Late")
        compose.onNode(hasText("Bravo") and isToggleable()).performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNode(hasSetTextAction()).assertTextContains("Late")
        compose.onNode(hasText("Bravo") and isToggleable()).assertIsOn()
        compose.runOnIdle { assertEquals(listOf("bravo"), state.picked) }
    }

    @Test
    fun `delete asks first and its dialog survives window changes`() {
        compose.setContent { listPage() }
        compose.onNodeWithContentDescription("More options for Comedy").performClick()
        compose.onNodeWithText("Delete").performClick()

        moveThroughWindows { compose.onNodeWithText("Delete playlist?").assertIsDisplayed() }

        compose.onNodeWithText("Delete").performClick()
        compose.runOnIdle { assertEquals(listOf("p1"), deleted) }
    }

    @Test
    fun `add shows on a playlist page offers only shows not in it and keeps picks across window changes`() {
        compose.setContent { detailPage() }
        compose.onNodeWithTag(PLAYLIST_DETAIL_TAG).performScrollToNode(hasText("Add shows"))
        compose.onNodeWithText("Add shows").performClick()
        compose.onNode(hasText("Alpha") and isToggleable()).assertDoesNotExist()
        compose.onNode(hasText("Charlie") and isToggleable()).performClick()

        moveThroughWindows { compose.onNode(hasText("Charlie") and isToggleable()).assertIsOn() }

        compose.onNodeWithText("Add").performClick()
        compose.runOnIdle {
            assertEquals(listOf(listOf("charlie")), added)
            assertNull(state.editor)
        }
    }

    @Test
    fun `the page plays, flips its order, marks unfollowed shows and removes them`() {
        compose.setContent { detailPage() }
        compose.onNodeWithText("First up").assertExists()
        compose.onNodeWithText("Play").performClick()
        compose.onNodeWithText("Oldest first").performClick()
        compose.onNodeWithTag(PLAYLIST_DETAIL_TAG).performScrollToNode(hasText("Gone show"))
        compose.onNodeWithText("Not following, skipped").assertExists()
        compose.onNodeWithContentDescription("Remove Gone show").performClick()
        compose.runOnIdle {
            assertEquals(1, plays)
            assertEquals(listOf(PlaylistOrder.OLDEST), orders)
            assertEquals(listOf("gone"), removed)
        }
    }

    @Test
    fun `rename on the playlist page survives window changes`() {
        compose.setContent { detailPage() }
        compose.onNodeWithContentDescription("More options for Comedy").performClick()
        compose.onNodeWithText("Rename").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("Evening")
        moveThroughWindows { compose.onNode(hasSetTextAction()).assertTextContains("Evening") }
        compose.onNodeWithText("Save").performClick()
        compose.runOnIdle { assertEquals(listOf("p1" to "Evening"), renamed) }
    }

    @Test
    fun `an empty playlist explains how to add shows`() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                state = rememberPlaylistPagesState()
                PlaylistDetailContent(ready.copy(shows = emptyList(), comingUp = emptyList()), state, actions, onBack = {}, pickSource = pickSource)
            }
        }
        compose.onNodeWithText("No shows yet").assertExists()
        compose.onNodeWithText("Add shows").performClick()
        compose.runOnIdle { assertEquals(PlaylistEditor.AddShows("p1"), state.editor) }
    }

    @Test
    fun `undo snackbars stay above the mini player and navigation as the window changes`() {
        val host = SnackbarHostState()
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    state = rememberPlaylistPagesState()
                    PlaylistDetailContent(ready, state, actions, onBack = {}, pickSource = pickSource, snackbarHostState = host, isPlayerActive = true)
                }
            }
        }
        compose.runOnIdle { CoroutineScope(Dispatchers.Main).launch { host.showSnackbar("Up next replaced", "Undo", duration = SnackbarDuration.Indefinite) } }
        fun gapUnderSnackbar() = compose.onRoot().getUnclippedBoundsInRoot().bottom - compose.onNodeWithText("Up next replaced").getUnclippedBoundsInRoot().bottom

        assertTrue("phone: above the 160dp of mini player and floating nav", gapUnderSnackbar() >= 160.dp)
        compose.runOnIdle { window.value = BoxloreWindowLayout(1280f, 800f) }
        assertTrue("tablet: above the tablet chrome", gapUnderSnackbar() >= 24.dp)
        compose.runOnIdle { window.value = BoxloreWindowLayout(360f, 740f) }
        assertTrue("back on a phone", gapUnderSnackbar() >= 160.dp)
        compose.onNodeWithText("Undo").assertIsDisplayed()
    }

    @Test
    fun `a show without details reads as an unknown show, never as the playlist's name`() {
        val unknown = PlaylistShow("p1", "restored", 0, 0L, "", null, isFollowed = false)
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                state = rememberPlaylistPagesState()
                PlaylistDetailContent(ready.copy(shows = listOf(unknown)), state, actions, onBack = {}, pickSource = pickSource)
            }
        }
        compose.onNodeWithTag(PLAYLIST_DETAIL_TAG).performScrollToNode(hasText("Unknown show"))
        compose.onNodeWithContentDescription("Remove Unknown show").performClick()
        compose.runOnIdle { assertEquals(listOf("restored"), removed) }
        assertEquals("Show removed", PlaylistDetailCopy.removed(unknown))
        assertEquals("Removed Alpha", PlaylistDetailCopy.removed(shows.first()))
    }
}
