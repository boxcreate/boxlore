package cx.aswin.boxlore.ui

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletPlayerReflowTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1_600, 1_400, false))
    private val window = mutableStateOf(
        BoxloreWindowLayout(
            673f,
            841f,
            listOf(BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)),
        ),
    )
    private val episode = TestFixtures.episode(title = "A curious journey", imageUrl = PhoneGoldenRule.ARTWORK_URL)

    @Test
    fun `tabletop mini expands across the full window with art above and transport below the hinge`() {
        render()
        fixture.compose.onNodeWithText(episode.title).performClick()
        advance()
        val art = fixture.compose.onNodeWithTag("tablet_player_artwork_region", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val controls = fixture.compose.onNodeWithTag("tablet_player_controls_region", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue(art.bottom <= 410f)
        assertTrue(controls.top >= 431f)
        fixture.compose.runOnUiThread { fixture.compose.activity.onBackPressedDispatcher.onBackPressed() }
        advance()
        fixture.compose.onNodeWithText(episode.title).assertExists()
        fixture.compose.onNodeWithTag("tablet_player_artwork_region", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun `context fullscreen opens immersive bounds and minimize returns to its pane`() {
        window.value = BoxloreWindowLayout(1_280f, 800f)
        render()
        val pane = fixture.compose.onNodeWithTag("tablet_player_pane").fetchSemanticsNode().boundsInRoot
        assertTrue(pane.width in 360f..520f)
        fixture.compose.onNodeWithContentDescription("Open full-screen player").performClick()
        advance()
        val immersive = fixture.compose.onNodeWithTag("tablet_player_pane").fetchSemanticsNode().boundsInRoot
        assertEquals(0f, immersive.left, 1f)
        assertEquals(1_280f, immersive.width, 1f)
        fixture.compose.onNodeWithContentDescription(fixture.compose.activity.getString(cx.aswin.boxlore.feature.player.R.string.minimal_collapse)).performClick()
        advance()
        assertEquals(pane.width, fixture.compose.onNodeWithTag("tablet_player_pane").fetchSemanticsNode().boundsInRoot.width, 1f)
    }

    @Test
    fun `a docked player returns to its pane when reopened in either orientation`() {
        window.value = BoxloreWindowLayout(1_280f, 800f)
        render()
        val pane = fixture.compose.onNodeWithTag("tablet_player_pane").fetchSemanticsNode().boundsInRoot
        val collapse = fixture.compose.activity.getString(cx.aswin.boxlore.feature.player.R.string.minimal_collapse)
        fun dock() {
            fixture.compose.onNodeWithContentDescription(collapse).performClick()
            advance()
            assertEquals(1_200f, fixture.compose.onNodeWithTag("tablet_player_pane").fetchSemanticsNode().boundsInRoot.width, 1f)
        }
        dock()
        fixture.compose.onNodeWithText(episode.title).performClick()
        advance()
        assertEquals(pane, fixture.compose.onNodeWithTag("tablet_player_pane").fetchSemanticsNode().boundsInRoot)

        dock()
        fixture.compose.runOnIdle { window.value = BoxloreWindowLayout(840f, 1_280f) }
        advance()
        fixture.compose.onNodeWithText(episode.title).performClick()
        advance()
        fixture.compose.onNodeWithTag("tablet_player_artwork_region", useUnmergedTree = true).assertExists()
        fixture.compose.runOnIdle { window.value = BoxloreWindowLayout(1_280f, 800f) }
        advance()
        assertEquals(pane, fixture.compose.onNodeWithTag("tablet_player_pane").fetchSemanticsNode().boundsInRoot)
    }

    @Test
    fun `the first player frame already shows the current episode`() {
        window.value = BoxloreWindowLayout(1_280f, 800f)
        fixture.compose.mainClock.autoAdvance = false
        render(advanceAfter = false)
        fixture.compose.onNodeWithText(episode.title, useUnmergedTree = true).assertExists()
    }

    private fun render(advanceAfter: Boolean = true) {
        fixture.prepare()
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(window.value.widthDp.dp).height(window.value.heightDp.dp)) {
                        TabletShellCapture("home", episode, TestFixtures.podcast(imageUrl = PhoneGoldenRule.ARTWORK_URL), true) { Text("Browsing") }
                    }
                }
            }
        }
        if (advanceAfter) advance()
    }

    private fun advance() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
