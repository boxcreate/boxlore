package cx.aswin.boxlore.feature.info.components

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The Episode titles only row: what it shows in each state, and which tap does what. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class EpisodeTitleRowTest {
    @get:Rule val rule = createComposeRule()

    private val episode = TestFixtures.episode(id = "e1", title = "How we find our way", duration = 2_700, description = "A description the row never shows.")
    private val state = mutableStateOf(EpisodeTitleRowState())
    private val taps = mutableListOf<String>()

    private fun render() {
        rule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                EpisodeTitleRow(
                    episode = episode,
                    state = state.value,
                    accentColor = Color(0xFF6750A4),
                    onClick = { taps += "open" },
                    onLongClick = { taps += "select" },
                    onPlayClick = { taps += "play" },
                )
            }
        }
    }

    @Test
    fun `a row shows the title and details with a play button, and no description or episode buttons`() {
        render()
        rule.onNodeWithText("How we find our way").assertIsDisplayed()
        rule.onNodeWithText("A description the row never shows.").assertDoesNotExist()
        rule.onNodeWithContentDescription("Like", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithContentDescription("Add to queue", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithTag(EPISODE_TITLE_ROW_PLAY_TAG).performClick()
        rule.onNodeWithText("How we find our way").performClick()
        rule.onNodeWithText("How we find our way").performTouchInput { longClick() }
        assertEquals(listOf("play", "open", "select"), taps)
    }

    @Test
    fun `the play button says what it does and the row marks played and up next episodes`() {
        render()
        rule.onNode(hasContentDescription("Play")).assertExists()
        rule.runOnIdle { state.value = EpisodeTitleRowState(isResume = true, progress = 0.4f, timeLeft = "27m left", isUpNext = true) }
        rule.onNode(hasContentDescription("Resume")).assertExists()
        rule.onNodeWithText("27m left", substring = true).assertExists()
        rule.onNodeWithText("Up next").assertExists()
        rule.runOnIdle { state.value = EpisodeTitleRowState(isPlaying = true, isResume = true, progress = 0.4f) }
        rule.onNode(hasContentDescription("Pause")).assertExists()
        rule.runOnIdle { state.value = EpisodeTitleRowState(isCompleted = true) }
        rule.onNodeWithContentDescription("Played", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("45m", substring = true).assertExists()
    }

    @Test
    fun `during selection a leading mark replaces the play button and a tap toggles the row`() {
        state.value = EpisodeTitleRowState(selectionActive = true)
        render()
        rule.onNodeWithTag(EPISODE_TITLE_ROW_PLAY_TAG).assertDoesNotExist()
        rule.onNodeWithContentDescription("Selected", useUnmergedTree = true).assertDoesNotExist()
        rule.runOnIdle { state.value = EpisodeTitleRowState(selectionActive = true, isSelected = true) }
        rule.onNodeWithContentDescription("Selected", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("How we find our way").performClick()
        assertEquals(listOf("open"), taps)
    }
}
