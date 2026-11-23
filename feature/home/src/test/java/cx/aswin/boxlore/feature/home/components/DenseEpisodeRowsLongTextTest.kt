package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Your shows' episode rows in long languages and large text: the progress bar of a half-played
 * episode used to be squeezed out and the time left cut with no "…". The bar now always shows, under
 * the date when the line is too full, and long time-left text ends with "…".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "de-w900dp-h900dp-mdpi")
class DenseEpisodeRowsLongTextTest {
    @get:Rule val composeRule = createComposeRule()
    private val width = mutableIntStateOf(360)
    private val scale = mutableFloatStateOf(1.3f)

    private fun render() {
        val episode = TestFixtures.episode(id = "e1", title = "Eine sehr lange Folge über Gesellschaft und Kultur").copy(duration = 5_400)
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, scale.floatValue)) {
                MaterialTheme {
                    Box(Modifier.width(width.intValue.dp)) {
                        DenseEpisodeRow(
                            episode,
                            TestFixtures.podcast(),
                            DenseEpisodeRowActions({}, {}),
                            playback = DenseEpisodeRowPlayback(overrideStatus = EpisodeStatus.IN_PROGRESS, overrideProgress = 0.4f),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `the progress bar keeps its place and time left ends with an ellipsis when cut`() {
        render()
        for ((rowWidth, textScale) in listOf(360 to 1.3f, 320 to 2f, 412 to 1.3f)) {
            width.intValue = rowWidth
            scale.floatValue = textScale
            composeRule.waitForIdle()
            val bar = composeRule.onNodeWithTag("home_dense_row_progress", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("$rowWidth dp at ${textScale}x: the bar is ${bar.width}dp", bar.width >= 24f - 0.5f)
            // The date gives way first: the time left gets the room it needs, ending with "…" only if even that runs out.
            val time = layout("home_dense_row_time")
            val room = time.layoutInput.constraints.maxWidth
            assertTrue("$rowWidth dp at ${textScale}x: the time left has ${room}dp", room >= time.multiParagraph.minIntrinsicWidth.coerceAtMost(200f) - 0.5f)
        }
    }

    @Test
    fun `English at normal text keeps the bar on the meta line`() {
        width.intValue = 412
        scale.floatValue = 1f
        render()
        assertEquals(false, denseRowStacksProgress(296.dp, 1f))
        assertEquals(true, denseRowStacksProgress(250.dp, 1f))
        assertEquals(true, denseRowStacksProgress(296.dp, 1.3f))
    }

    private fun layout(tag: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithTag(tag, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.single()
    }
}
