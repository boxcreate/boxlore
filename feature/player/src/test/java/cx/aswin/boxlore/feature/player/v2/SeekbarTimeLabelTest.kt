package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The end time label switches between time left and the total for the episode on screen, not the one before. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h915dp-mdpi", application = Application::class)
class SeekbarTimeLabelTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `a tap after the episode changes switches the new episode's label`() {
        val first = mutableStateOf(false)
        val second = mutableStateOf(false)
        val current = mutableStateOf(first)
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                PlayerSeekbar(
                    PlayerProgressFlows(flowOf(30_000L), flowOf(0L)),
                    durationMs = 600_000L,
                    isPlaying = false,
                    colorScheme = MaterialTheme.colorScheme,
                    onSeek = {},
                    showTotalDuration = current.value,
                )
            }
        }
        compose.onNodeWithText("-", substring = true).performClick()
        assertTrue("the first tap shows the total", first.value)
        current.value = second
        compose.waitForIdle()
        compose.onNodeWithText("-", substring = true).performClick()
        assertTrue("the tap reached the new episode's label", second.value)
        assertTrue("the episode before kept its choice", first.value)
        assertFalse("the new episode's label still shows the time left", compose.onAllNodes(hasText("-", substring = true)).fetchSemanticsNodes().isNotEmpty())
    }
}
