package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarState
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cx.aswin.boxlore.core.designsystem.component.BoxloreTopBar
import cx.aswin.boxlore.core.designsystem.component.boxloreLargeTitlePresentation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreCardFamily
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.boxloreCardWidth
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Shared controls with long and translated text and large font sizes: they wrap or shorten inside
 * their own row instead of being cut, and keep their state when the window changes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SharedControlsLongTextTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `large titles step down a size for large text and narrow panes only`() {
        val typography = Typography(
            headlineLarge = TextStyle(fontSize = 32.sp, lineHeight = 40.sp),
            headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 36.sp),
            titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp),
        )
        assertEquals(32.sp, boxloreLargeTitlePresentation(typography, 0f, paneWidthDp = 412f).style.fontSize)
        assertEquals(28.sp, boxloreLargeTitlePresentation(typography, 0f, paneWidthDp = 360f).style.fontSize)
        assertEquals(28.sp, boxloreLargeTitlePresentation(typography, 0f, fontScale = 1.3f, paneWidthDp = 412f).style.fontSize)
        val collapsed = boxloreLargeTitlePresentation(typography, 0f, expanded = false, fontScale = 1.3f, paneWidthDp = 360f)
        assertEquals(22.sp, collapsed.style.fontSize)
        assertEquals(1, collapsed.maxLines)
        assertEquals(2, boxloreLargeTitlePresentation(typography, 0.5f).maxLines)
    }

    @Test
    fun `long translated page titles fit their header at 360dp and 130 percent text`() {
        val title = mutableStateOf(LONG_TITLES.first())
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Page(BoxloreTestWindows.PHONE_SMALL) {
                    BoxloreTopBar(title.value, onBack = {})
                }
            }
        }
        LONG_TITLES.forEach { text ->
            compose.runOnIdle { title.value = text }
            compose.waitForIdle()
            // The expanded header shows the title in its lower row; the hidden slim row may shorten it.
            val shown = compose.onAllNodesWithText(text).fetchSemanticsNodes().maxBy { it.boundsInRoot.top }
            val results = mutableListOf<TextLayoutResult>()
            shown.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
            val layout = results.single()
            assertFalse("$text is cut at the bottom", layout.didOverflowHeight)
            assertFalse("$text is shortened", layout.isLineEllipsized(layout.lineCount - 1))
            assertTrue("$text stays inside the window", shown.boundsInRoot.right <= BoxloreTestWindows.PHONE_SMALL.widthDp)
        }
    }

    @Test
    fun `a short pane forces the slim bar and the same bar opens up again when the list is at its top`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        val states = mutableListOf<TopAppBarState>()
        compose.setContent {
            Page(window.value) {
                val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
                states += behavior.state
                Column(Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection)) {
                    BoxloreTopBar("Downloads", Modifier.testTag("bar"), onBack = {}, scrollBehavior = behavior)
                    LazyColumn(Modifier.fillMaxSize()) { items(40) { Text("Row $it", Modifier.padding(16.dp)) } }
                }
            }
        }
        val expanded = TopAppBarDefaults.LargeAppBarExpandedHeight.value
        val slim = TopAppBarDefaults.TopAppBarExpandedHeight.value
        val heights = mapOf(BoxloreTestWindows.PHONE to expanded, BoxloreTestWindows.PHONE_LAND to slim)
        compose.walkWindows(window, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE) { step ->
            val bar = compose.onNodeWithTag("bar").fetchSemanticsNode().boundsInRoot
            assertEquals("$step", heights.getValue(step), bar.height, 0.5f)
            states.forEach { assertSame("one bar state for the page", states.first(), it) }
        }
    }

    @Test
    fun `on a tablet the title lines up with the content column`() {
        val back = mutableStateOf(false)
        compose.setContent {
            Page(BoxloreTestWindows.TABLET) {
                Column(Modifier.fillMaxWidth()) {
                    BoxloreTopBar("Library", onBack = if (back.value) ({}) else null)
                    Box(Modifier.fillMaxWidth().boxloreCardWidth(BoxloreCardFamily.Utility).padding(horizontal = 16.dp)) {
                        Text("First row", Modifier.testTag("content"))
                    }
                }
            }
        }
        listOf(false, true).forEach { withBack ->
            compose.runOnIdle { back.value = withBack }
            compose.waitForIdle()
            val content = compose.onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
            val titles = compose.onAllNodesWithText("Library").fetchSemanticsNodes().map { it.boundsInRoot }
            // With a Back button the large title is the second row's; the first row holds Back.
            val largeTitle = titles.maxBy { it.top }
            assertEquals("back $withBack", content.left, largeTitle.left, 0.5f)
        }
    }

    @Composable
    private fun Page(window: BoxloreWindowLayout, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalBoxloreWindowLayout provides window) {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                Box(Modifier.size(window.widthDp.dp, window.heightDp.dp)) { content() }
            }
        }
    }

    private companion object {
        val LONG_TITLES = listOf("Téléchargements intelligents", "Episodios que te gustan")
    }
}
