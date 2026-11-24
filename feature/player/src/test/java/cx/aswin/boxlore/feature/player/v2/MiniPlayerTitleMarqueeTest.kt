package cx.aswin.boxlore.feature.player.v2

import android.app.Application
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A long episode title glides across the mini player once when the episode first shows, after a
 * pause, and then ends in '…' again. It never moves for a title that fits, for the same episode
 * again, while the bar is changing shape, or with animations turned off.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // Real text measurement, so a long title overflows.
class MiniPlayerTitleMarqueeTest {
    @get:Rule val compose = createComposeRule()
    private val episode = mutableStateOf("episode-1")
    private val title = mutableStateOf(LONG_TITLE)
    private val settled = mutableStateOf(true)
    private val passDone: MutableState<String?> = mutableStateOf(null)
    private var passes = 0

    private fun render(motionAllowed: Boolean = true) {
        compose.mainClock.autoAdvance = false
        compose.setContent {
            MiniPlayerTitle(
                episodeId = episode.value,
                title = title.value,
                color = Color.Black,
                settled = settled.value,
                motionAllowed = motionAllowed,
                passDoneFor = passDone,
                modifier = Modifier.width(220.dp),
            )
        }
        compose.mainClock.advanceTimeByFrame()
    }

    /** Advances [ms] in frames, counting each start of a pass. */
    private fun advance(ms: Long) {
        var scrolling = isScrolling()
        repeat((ms / 100).toInt()) {
            compose.mainClock.advanceTimeBy(100)
            val now = isScrolling()
            if (now && !scrolling) passes++
            scrolling = now
        }
    }

    private fun isScrolling(): Boolean = runCatching { compose.onNodeWithTag(MINI_TITLE_SCROLLING_TAG).assertExists() }.isSuccess

    @Test fun `a long title rests, passes once, and ends in an ellipsis again`() {
        render()
        compose.onNodeWithTag(MINI_TITLE_TAG).assertExists()
        advance(2_500)
        assertEquals("No pass before the pause ends", 0, passes)
        advance(1_000)
        assertEquals(1, passes)
        compose.onNodeWithTag(MINI_TITLE_SCROLLING_TAG).assertExists()
        advance(40_000)
        compose.onNodeWithTag(MINI_TITLE_TAG).assertExists()
        assertEquals(1, passes)
        assertEquals("episode-1", passDone.value)
    }

    @Test fun `the same episode never passes twice, and a new one passes once`() {
        render()
        advance(45_000)
        assertEquals(1, passes)
        // The bar shrinks and comes back (or the window changes): the same episode stays still.
        compose.runOnIdle { settled.value = false }
        advance(500)
        compose.runOnIdle { settled.value = true }
        advance(45_000)
        assertEquals(1, passes)

        compose.runOnIdle { episode.value = "episode-2" }
        advance(45_000)
        assertEquals(2, passes)
    }

    @Test fun `it stays still while the bar is changing shape or with animations off`() {
        settled.value = false
        render()
        advance(10_000)
        assertEquals(0, passes)
        compose.runOnIdle { settled.value = true }
        advance(4_000)
        assertEquals("It passes once the bar settles", 1, passes)
    }

    @Test fun `with animations off it never moves`() {
        render(motionAllowed = false)
        advance(10_000)
        assertEquals(0, passes)
        compose.onNodeWithTag(MINI_TITLE_TAG).assertExists()
    }

    @Test fun `a title that fits never moves`() {
        title.value = "Short"
        render()
        advance(10_000)
        assertEquals(0, passes)
        assertEquals("episode-1", passDone.value)
    }

    private companion object {
        const val LONG_TITLE = "The long way round: how curiosity, patience and a few wrong turns led us to the best stories"
    }
}
