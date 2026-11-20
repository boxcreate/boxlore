package cx.aswin.boxlore.feature.library.playlists

import android.app.Application
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PLAYLIST_DEFAULT_PER_SHOW_LIMIT
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistMix
import cx.aswin.boxlore.core.model.PlaylistShow
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.model.text.resolve
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
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Playlists and playlist pages keep what is open (New playlist with its step, picks, name and
 * how it plays; Add shows; Rename; Delete) while the window moves between phone, tablet, split and
 * short layouts, both ways, and when the activity is recreated; Create, Add, Save, Delete, Play,
 * How it plays and Remove still do the current thing afterwards, from one state owner.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlaylistPagesAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private lateinit var state: PlaylistPagesState
    private val owners = mutableSetOf<PlaylistPagesState>()

    private val created = mutableListOf<PlaylistCreateRequest>()
    private val renamed = mutableListOf<Pair<String, String>>()
    private val deleted = mutableListOf<String>()
    private val added = mutableListOf<List<String>>()
    private val removed = mutableListOf<String>()
    private val playbacks = mutableListOf<Pair<PlaylistMix, Int?>>()
    private var plays = 0

    private val followed = listOf("Alpha" to "Sports", "Bravo" to "Sports", "Charlie" to "News").map { (title, genre) ->
        Podcast(title.lowercase(), title, "Host", "", genre = genre)
    }
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
        override fun create(request: PlaylistCreateRequest) {
            created += request
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
        override fun setPlayback(mix: PlaylistMix, perShowLimit: Int?) {
            playbacks += mix to perShowLimit
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
    fun `a suggested playlist keeps its name and how it plays across window changes, then creates`() {
        compose.setContent { listPage() }
        compose.onNodeWithTag(NEW_PLAYLIST_TILE_TAG).performClick()
        compose.onNodeWithText("Made from your shows").assertExists()
        compose.onNodeWithTag("playlist_suggestion_genre:Sports").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("Sports")

        moveThroughWindows {
            compose.onNode(hasSetTextAction()).assertTextContains("Sports")
            compose.onNodeWithText("2 shows picked").assertExists()
        }

        compose.onNodeWithTag(PLAYLIST_PLAYBACK_SETTINGS_TAG).performScrollTo()
        compose.onNodeWithText("One show at a time").performScrollTo().performClick()
        compose.onNodeWithText("5").performScrollTo().performClick()
        compose.onNodeWithText("Create playlist").performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(PlaylistCreateRequest("Sports", listOf("alpha", "bravo"), PlaylistMix.ONE_SHOW_AT_A_TIME, 5, "sports", "genre")),
                created,
            )
            assertNull(state.newDraft)
        }
    }

    @Test
    fun `picking shows yourself survives recreation, then names the playlist after their genre`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { listPage() }
        compose.onNodeWithTag(NEW_PLAYLIST_TILE_TAG).performClick()
        compose.onNodeWithText("Pick shows yourself").performClick()
        compose.onNodeWithContentDescription("Charlie").performClick()

        restoration.emulateSavedInstanceStateRestore()

        compose.onNodeWithContentDescription("Charlie").assertIsSelected()
        compose.onNodeWithText("Next").performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("News")
        compose.runOnIdle { assertEquals(listOf("charlie"), state.newDraft?.pickedIds) }
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
        compose.onNodeWithContentDescription("Alpha").assertDoesNotExist()
        compose.onNodeWithContentDescription("Charlie").performClick()

        moveThroughWindows { compose.onNodeWithContentDescription("Charlie").assertIsSelected() }

        compose.onNodeWithText("Add 1 show").performClick()
        compose.runOnIdle {
            assertEquals(listOf(listOf("charlie")), added)
            assertNull(state.newDraft)
        }
    }

    @Test
    fun `the page plays, changes how it plays, marks unfollowed shows and removes them`() {
        compose.setContent { detailPage() }
        compose.onNodeWithText("First up").assertExists()
        compose.onNodeWithText("Play").performClick()
        compose.onNodeWithTag(PLAYLIST_HOW_IT_PLAYS_TAG).performClick()
        compose.onNodeWithText("Newest first").performScrollTo().performClick()
        compose.onNodeWithText("No limit").performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(
                listOf(PlaylistMix.NEWEST_FIRST to PLAYLIST_DEFAULT_PER_SHOW_LIMIT, PlaylistMix.TAKE_TURNS to null),
                playbacks,
            )
        }
        compose.onNodeWithText("Newest first").performScrollTo() // the sheet stays open for more changes
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag(PLAYLIST_DETAIL_TAG).performScrollToNode(hasText("Gone show"))
        compose.onNodeWithText("Not following, skipped").assertExists()
        compose.onNodeWithContentDescription("Remove Gone show").performClick()
        compose.runOnIdle {
            assertEquals(1, plays)
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
        compose.runOnIdle { assertEquals(NewPlaylistFlowLogic.addShows("p1"), state.newDraft) }
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
        assertEquals("Show removed", PlaylistDetailCopy.removed(unknown).resolve(RuntimeEnvironment.getApplication()))
        assertEquals("Removed Alpha", PlaylistDetailCopy.removed(shows.first()).resolve(RuntimeEnvironment.getApplication()))
    }
}
