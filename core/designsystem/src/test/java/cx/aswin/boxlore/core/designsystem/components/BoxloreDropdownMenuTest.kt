package cx.aswin.boxlore.core.designsystem.components

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.walkWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The shared menu: groups, choices and red actions, placed off the hinge, kept open through recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1300dp-h1300dp-mdpi", application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BoxloreDropdownMenuTest {
    @get:Rule val compose = createComposeRule()

    private val placements = mutableListOf<IntRect>()

    @Test
    fun `a menu from a trailing anchor beside a book hinge stays in the left half, folded or not`() {
        val window = mutableStateOf(BoxloreTestWindows.FOLD_BOOK)
        compose.setContent {
            Menu(window.value, anchorX = 404, anchorY = 80, open = true) {
                listOf("Share", "Mark played", "Add to queue").forEach { item(it, onClick = {}) }
            }
        }
        compose.walkWindows(window, BoxloreTestWindows.FOLD_BOOK, BoxloreTestWindows.FOLD_FLAT, BoxloreTestWindows.FOLD_BOOK) { step ->
            compose.onNodeWithText("Share").assertExists()
            val menu = placements.last()
            if (step == BoxloreTestWindows.FOLD_BOOK) {
                assertTrue("$menu crosses the hinge at 456", menu.right <= 456)
            }
            assertTrue("$menu starts off the window", menu.left >= 0)
        }
    }

    @Test
    fun `a long menu below a tabletop hinge stays in the lower half and scrolls to its last row`() {
        compose.setContent {
            Menu(BoxloreTestWindows.FOLD_TABLETOP, anchorX = 600, anchorY = 380, open = true) {
                (1..11).forEach { item("Row $it", onClick = {}) }
            }
        }
        compose.waitForIdle()
        val menu = placements.last()
        assertTrue("$menu starts above the hinge's lower edge 336", menu.top >= 336)
        assertTrue("$menu runs past the window's bottom", menu.bottom <= 650)
        compose.onNodeWithText("Row 11").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `destructive actions are red and sit in a group of their own`() {
        val groups = boxloreMenuGroups {
            item("Share", onClick = {})
            item("Rename", onClick = {})
            item("Delete playlist", onClick = {}, leadingIcon = Icons.Rounded.Delete, destructive = true)
        }
        assertEquals(listOf(2, 1), groups.map { it.size })
        assertTrue((groups[1].single() as BoxloreMenuEntry.Action).destructive)
        val mixed = runCatching {
            boxloreMenuGroups {
                group {
            item("Share", {})
            item("Delete", {}, destructive = true)
        }
            }
        }
        assertTrue("a destructive item mixed into a group is refused", mixed.isFailure)

        var error = Color.Unspecified
        compose.setContent {
            Theme(BoxloreTestWindows.PHONE) { error = MaterialTheme.colorScheme.error }
            Menu(BoxloreTestWindows.PHONE, anchorX = 300, anchorY = 80, open = true) {
                item("Share", onClick = {})
                item("Delete playlist", onClick = {}, destructive = true)
            }
        }
        assertEquals(error, textLayout("Delete playlist").layoutInput.style.color)
        assertFalse(textLayout("Share").layoutInput.style.color == error)
    }

    @Test
    fun `the current choice is selected and choosing closes the menu`() {
        val chosen = mutableListOf<String>()
        val state = BoxloreMenuState(expanded = true)
        compose.setContent {
            Menu(BoxloreTestWindows.PHONE, anchorX = 300, anchorY = 80, state = state) {
                group {
                    groupLabel("Sort by")
                    listOf("Newest", "Oldest").forEach { label -> selectableItem(label, selected = label == "Newest", onClick = { chosen += label }) }
                }
            }
        }
        compose.onNodeWithText("Newest").assertIsSelected()
        compose.onNodeWithText("Oldest").assertIsNotSelected().performClick()
        compose.waitForIdle()
        assertEquals(listOf("Oldest"), chosen)
        assertFalse(state.expanded)
        compose.onNodeWithText("Newest").assertDoesNotExist()
    }

    @Test
    fun `an open menu is still open after the screen is recreated`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val state = rememberBoxloreMenuState(key = "episode-42")
            Theme(BoxloreTestWindows.PHONE) {
                Box(Modifier.size(412.dp, 915.dp)) {
                    Box(Modifier.offset(300.dp, 80.dp)) {
                        IconButton(onClick = state::open) { Icon(Icons.Rounded.MoreVert, contentDescription = "More") }
                        BoxloreDropdownMenu(state) { item("Share", onClick = {}) }
                    }
                }
            }
        }
        compose.onNodeWithText("Share").assertDoesNotExist()
        compose.onNodeWithContentDescription("More").performClick()
        compose.onNodeWithText("Share").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Share").assertExists()
    }

    @Test
    fun `long German rows wrap to two lines at large text instead of being cut`() {
        val label = "Auf der Uhr abspielen, sobald sie verbunden ist"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, fontScale = 1.3f)) {
                Menu(BoxloreTestWindows.PHONE_SMALL, anchorX = 300, anchorY = 80, open = true) {
                    item("Auf der Uhr abspielen", onClick = {})
                    item(label, onClick = {})
                }
            }
        }
        listOf("Auf der Uhr abspielen", label).forEach { text ->
            val layout = textLayout(text)
            assertTrue("$text takes ${layout.lineCount} lines", layout.lineCount <= 2)
            assertFalse("$text is cut at the bottom", layout.didOverflowHeight)
            assertFalse("$text is shortened", layout.isLineEllipsized(layout.lineCount - 1))
        }
        assertEquals(2, textLayout(label).lineCount)
    }

    private fun textLayout(text: String): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action?.invoke(results)
        return results.single()
    }

    @Composable
    private fun Menu(
        window: BoxloreWindowLayout,
        anchorX: Int,
        anchorY: Int,
        open: Boolean = false,
        state: BoxloreMenuState = remember { BoxloreMenuState(open) },
        content: BoxloreMenuScope.() -> Unit,
    ) {
        Theme(window) {
            CompositionLocalProvider(LocalBoxloreMenuPlacement provides { placements += it }) {
                Box(Modifier.size(window.widthDp.dp, window.heightDp.dp)) {
                    Box(Modifier.offset(anchorX.dp, anchorY.dp).size(48.dp)) {
                        BoxloreDropdownMenu(state, content = content)
                    }
                }
            }
        }
    }

    @Composable
    private fun Theme(window: BoxloreWindowLayout, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalBoxloreWindowLayout provides window) {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) { content() }
        }
    }
}
