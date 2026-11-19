package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.model.Playlist
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The show page's Add to playlist sheet moves between its phone sheet and tablet dialog hosts as the
 * window crosses the 600dp, 480dp and 840dp gates in both directions; a new playlist's unsaved name
 * stays, and Create and add still creates it. Each playlist shows whether it holds the show and toggles it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AddToPlaylistSheetAdaptiveTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreWindowLayout(360f, 740f))
    private val state = AddToPlaylistSheetState()
    private val created = mutableListOf<String>()
    private val toggled = mutableListOf<String>()

    private val playlists = listOf(
        Playlist("p1", "Comedy", 0L, 0L, showCount = 3),
        Playlist("p2", "History", 0L, 0L, showCount = 1),
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
                        showTitle = "The Show",
                        playlists = playlists,
                        memberIds = setOf("p2"),
                        state = state,
                        onToggle = { toggled += it.id },
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
    fun `each playlist shows whether it holds the show and a tap toggles it`() {
        show()
        compose.onNodeWithText("3 shows").assertExists()
        compose.onNode(hasText("History") and isToggleable()).assertIsOn()
        compose.onNode(hasText("Comedy") and isToggleable()).assertIsOff().performClick()
        compose.onNode(hasText("History") and isToggleable()).performClick()
        compose.runOnIdle { assertEquals(listOf("p1", "p2"), toggled) }
    }
}
