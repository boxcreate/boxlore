package cx.aswin.boxlore.feature.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.TranscriptSegment
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The player sheet's container runs past the screen's foot (by the navigation bar and 50dp) so it can be
 * dragged; the full-screen transcript fits the visible height so its play controls stay on screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w400dp-h1000dp-mdpi")
class FullscreenTranscriptLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    private fun render(visibleHeight: Dp) {
        composeRule.setContent {
            // A container 100dp taller than the 800dp the listener can see, as the sheet's is.
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(400.dp, 900.dp)) {
                FullscreenTranscriptScreen(
                    transcript = listOf(TranscriptSegment(0L, 1_000L, "Hello")),
                    positionFlow = flowOf(0L),
                    isPlaying = false,
                    isLoading = false,
                    durationMs = 60_000L,
                    colorScheme = lightColorScheme(),
                    isSyncEnabled = true,
                    onSyncEnabledChange = {},
                    onSeek = {},
                    onPlayPause = {},
                    onClose = {},
                    modifier = Modifier.fitVisibleHeight(visibleHeight),
                )
            }
        }
    }

    private fun playBottom(): Float = composeRule.onNodeWithContentDescription("Play").getUnclippedBoundsInRoot().bottom.value

    @Test fun `controls stay within the visible height when the container runs past the screen`() {
        render(visibleHeight = 800.dp)
        assertTrue("play sits at ${playBottom()}dp, past the 800dp visible", playBottom() <= 800f)
    }

    @Test fun `without a visible height the screen fills its container, as before`() {
        render(visibleHeight = Dp.Unspecified)
        assertTrue("play sits at ${playBottom()}dp", playBottom() > 800f)
    }
}
