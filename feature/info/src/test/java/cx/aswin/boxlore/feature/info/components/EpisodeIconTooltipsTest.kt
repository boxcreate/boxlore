package cx.aswin.boxlore.feature.info.components

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Icon-only buttons on the episode page's Play row and a Showcase spotlight name themselves on long-press and hover. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EpisodeIconTooltipsTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(412, 915, false))

    private var likes = 0
    private var queues = 0
    private var liked by mutableStateOf(false)

    @Test
    fun `long-press names the Play row's buttons without eating their taps`() {
        render()
        fixture.compose.onAllNodesWithContentDescriptionFirst("Download", spotlight = false).performTouchInput { longClick() }
        fixture.compose.waitForIdle()
        assertTrue("long-press names Download", shown("Download"))

        // Hover uses the same tooltip; Robolectric doesn't deliver mouse hover to it, so only long-press is checked here.
        fixture.compose.onAllNodesWithContentDescriptionFirst("Like", spotlight = false).performClick()
        fixture.compose.waitForIdle()
        assertEquals("one tap, one like", 1, likes)
        fixture.compose.onNodeWithContentDescription("Unlike episode").performTouchInput { longClick() }
        fixture.compose.waitForIdle()
        assertTrue("the name follows the new state", shown("Unlike episode"))
        assertEquals("a long-press doesn't like again", 1, likes)
    }

    @Test
    fun `a Showcase spotlight's buttons name themselves too`() {
        render()
        fixture.compose.onAllNodesWithContentDescriptionFirst("Add to queue", spotlight = true).performTouchInput { longClick() }
        fixture.compose.waitForIdle()
        assertTrue(shown("Add to queue"))
        fixture.compose.onAllNodesWithContentDescriptionFirst("Add to queue", spotlight = true).performClick()
        fixture.compose.waitForIdle()
        assertEquals(1, queues)
    }

    private fun shown(label: String) = fixture.compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()

    private fun render() {
        fixture.prepare()
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                Column(Modifier.padding(top = 200.dp)) {
                    EpisodeActionRail(
                        state = EpisodeActionRailState(
                            isPlaying = false,
                            isPlaybackLoading = false,
                            isLiked = liked,
                            isDownloaded = false,
                            isDownloading = false,
                            isQueued = false,
                            isCompleted = false,
                            positionMs = 0L,
                            durationMs = 0L,
                        ),
                        callbacks = EpisodeActionRailCallbacks(onMainActionClick = {}, onLikeClick = {
                            likes++
                            liked = !liked
                        }, onDownloadClick = {}, onQueueClick = {}),
                    )
                    PodcastEpisodeActions(
                        state = PodcastEpisodeActionsState(isPlaying = false, isResume = false, isLiked = false, isDownloaded = false, isDownloading = false, isQueued = false),
                        callbacks = PodcastEpisodeActionsCallbacks(onPlay = {}, onLike = {}, onDownload = {}, onQueue = { queues++ }),
                        accentColor = Color.Red,
                        tooltips = true,
                    )
                }
            }
        }
        fixture.compose.waitForIdle()
    }

    /** The rail's button comes first; [spotlight] picks the spotlight's. */
    private fun androidx.compose.ui.test.junit4.ComposeContentTestRule.onAllNodesWithContentDescriptionFirst(label: String, spotlight: Boolean) =
        onAllNodes(androidx.compose.ui.test.hasContentDescription(label))[if (spotlight) 1 else 0]
}
