package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.PLAYLIST_EPISODE_LIMIT
import cx.aswin.boxlore.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The Add to playlist sheet moves between its phone sheet and tablet dialog hosts as the window
 * crosses the 600dp, 480dp and 840dp gates; a new playlist's unsaved name stays, and Create and add
 * still creates it. A full playlist can't be picked.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddToPlaylistSheetAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val state = AddToPlaylistSheetState()
    private val created = mutableListOf<String>()
    private val picked = mutableListOf<String>()

    private val playlists = listOf(
        Playlist("p1", "Commute", 0L, 0L, episodeCount = 3),
        Playlist("p2", "Archive", 0L, 0L, episodeCount = PLAYLIST_EPISODE_LIMIT),
    )

    private val windows = listOf(
        BoxloreWindowLayout(599f, 900f),
        BoxloreWindowLayout(600f, 900f),
        BoxloreWindowLayout(1280f, 800f),
        BoxloreWindowLayout(1280f, 479f),
        BoxloreWindowLayout(839f, 1180f),
        BoxloreWindowLayout(841f, 600f),
        BoxloreWindowLayout(360f, 740f),
    )

    private fun show() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                    AddToPlaylistSheet(
                        playlists = playlists,
                        episodeCount = 2,
                        state = state,
                        onAddTo = { picked += it.id },
                        onCreate = { created += it },
                        onDismissRequest = {},
                    )
                }
            }
        }
    }

    @Test
    fun `a new playlist name survives every window change, then creates`() {
        show()
        compose.onNodeWithText("New playlist").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("Weekend")

        windows.forEach { next ->
            compose.runOnIdle { window.value = next }
            compose.waitForIdle()
            compose.onNode(hasSetTextAction()).assertTextContains("Weekend")
        }

        compose.onNodeWithText("Create and add").performClick()
        compose.runOnIdle { assertEquals(listOf("Weekend"), created) }
    }

    @Test
    fun `an open playlist adds and a full one is disabled`() {
        show()
        compose.onNodeWithText("500 episodes · Full").assertExists()
        compose.onNodeWithText("Archive").assertIsNotEnabled()
        compose.onNodeWithText("Commute").performClick()
        compose.runOnIdle { assertEquals(listOf("p1"), picked) }
    }
}
