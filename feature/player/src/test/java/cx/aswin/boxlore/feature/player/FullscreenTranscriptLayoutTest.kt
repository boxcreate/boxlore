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
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.TranscriptSegment
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
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

    @Test fun `video controls and the queue Undo message stay within the visible height`() {
        val host = androidx.compose.material3.SnackbarHostState()
        composeRule.setContent {
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true).requiredSize(400.dp, 900.dp)) {
                cx.aswin.boxlore.feature.player.v2.VideoFullscreenOverlay(
                    content = cx.aswin.boxlore.feature.player.v2.VideoFullscreenContent(
                        visible = true,
                        episodeTitle = "Episode",
                        isPlaying = false,
                        durationMs = 60_000L,
                        seekBackwardSeconds = 10,
                        seekForwardSeconds = 30,
                        positionFlow = flowOf(0L),
                        controller = null,
                        visibleHeight = 800.dp,
                    ),
                    colorScheme = lightColorScheme(),
                    actions = cx.aswin.boxlore.feature.player.v2.VideoFullscreenActions({}, {}, {}, {}, {}),
                )
                cx.aswin.boxlore.feature.player.v2.PlayerSnackbarHost(host, visibleHeight = 800.dp, bottomInset = 0.dp)
            }
        }
        composeRule.runOnIdle { kotlinx.coroutines.MainScope().launch { host.showSnackbar("Removed from queue", "Undo") } }
        composeRule.waitForIdle()
        val slider = composeRule.onNodeWithContentDescription("Playback position").getUnclippedBoundsInRoot().bottom.value
        assertTrue("slider at ${slider}dp, past the 800dp visible", slider <= 800f)
        val message = composeRule.onNodeWithText("Undo").getUnclippedBoundsInRoot().bottom.value
        assertTrue("Undo at ${message}dp, past the 800dp visible", message <= 800f)
    }

    @Test fun `half-open on a table the transcript sits above the hinge and the controls below, and back`() {
        val hinge = cx.aswin.boxlore.core.designsystem.layout.BoxloreFold(
            cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState.HalfOpened,
            cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation.Horizontal,
            cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds(0f, 480f, 400f, 500f),
            true,
            true,
        )
        val window = androidx.compose.runtime.mutableStateOf(cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout(400f, 1_000f, listOf(hinge)))
        composeRule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout provides window.value) {
                FullscreenTranscriptScreen(
                    transcript = List(30) { TranscriptSegment(it * 1_000L, it * 1_000L + 999L, "Line $it") },
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
                )
            }
        }
        composeRule.waitForIdle()
        val lines = composeRule.onAllNodes(androidx.compose.ui.test.hasText("Line ", substring = true)).fetchSemanticsNodes()
        assertTrue("some transcript shows", lines.isNotEmpty())
        lines.forEach { assertTrue("a transcript line at ${it.boundsInRoot} crosses the hinge", it.boundsInRoot.bottom <= 480f) }
        val play = composeRule.onNodeWithContentDescription("Play").getUnclippedBoundsInRoot()
        assertTrue("play at ${play.top} sits just below the hinge", play.top.value >= 500f && play.bottom.value <= 800f)
        composeRule.runOnIdle { window.value = cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout(400f, 1_000f) }
        composeRule.waitForIdle()
        assertTrue("flat: controls at the foot", playBottom() > 900f)
        composeRule.runOnIdle { window.value = cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout(400f, 1_000f, listOf(hinge)) }
        composeRule.waitForIdle()
        assertTrue(composeRule.onNodeWithContentDescription("Play").getUnclippedBoundsInRoot().top.value >= 500f)
    }

    @Test fun `lines dim around the spoken one only while following the audio`() {
        assertTrue(transcriptLineOpacity(isActive = false, following = true) < 1f)
        assertTrue(transcriptLineOpacity(isActive = false, following = false) == 1f)
        assertTrue(transcriptLineOpacity(isActive = true, following = true) == 1f)
    }
}
