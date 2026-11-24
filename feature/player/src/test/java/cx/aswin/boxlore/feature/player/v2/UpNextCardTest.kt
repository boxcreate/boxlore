package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.TestFixtures
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Up next card's title never breaks inside a word, and View queue moves under it when needed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class UpNextCardTest {
    @get:Rule val compose = createComposeRule()

    @After fun resetFontScale() = RuntimeEnvironment.setFontScale(1f)

    @Test
    @Config(qualifiers = "de-w412dp-h915dp-mdpi")
    fun `German at large text keeps whole words in the title and View queue in full`() {
        RuntimeEnvironment.setFontScale(1.3f)
        render()
        val title = compose.onNodeWithText("Als Nächstes").fetchSemanticsNode()
        assertWholeWords(title.textLayout())
        assertWholeWords(compose.onNodeWithText("Folgen", substring = true).fetchSemanticsNode().textLayout())
        val button = compose.onNodeWithText("Warteschlange ansehen", substring = true, useUnmergedTree = true).fetchSemanticsNode()
        val layout = button.textLayout()
        assertTrue("View queue is shown in full", layout.lineCount == 1 && !layout.isLineEllipsized(0))
        assertTrue("Title and View queue don't overlap", !title.boundsInRoot.overlaps(button.boundsInRoot))
    }

    @Test
    @Config(qualifiers = "en-w412dp-h915dp-mdpi")
    fun `English keeps View queue beside the title`() {
        render()
        val title = compose.onNodeWithText("Up next").fetchSemanticsNode().boundsInRoot
        val button = compose.onNodeWithText("View queue", substring = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Side by side: title $title, button $button", button.left >= title.right && button.top < title.bottom)
    }

    private fun render() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    UpNextCard(
                        queuedEpisodes = List(3) { TestFixtures.episode(id = "q$it", title = "Episode $it") },
                        colorScheme = MaterialTheme.colorScheme,
                        onOpenQueue = {},
                        onPlayNext = {},
                    )
                }
            }
        }
        compose.waitForIdle()
    }

    private fun androidx.compose.ui.semantics.SemanticsNode.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun assertWholeWords(layout: TextLayoutResult) {
        val text = layout.layoutInput.text.text
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            assertTrue("Line $line of \"$text\" ends inside a word", text[end - 1].isWhitespace() || text.getOrNull(end)?.isWhitespace() == true)
        }
    }
}
