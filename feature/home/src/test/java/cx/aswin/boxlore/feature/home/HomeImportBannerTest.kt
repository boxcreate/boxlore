package cx.aswin.boxlore.feature.home

import android.content.Context
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w430dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HomeImportBannerTest {
    @get:Rule val composeRule = createComposeRule()
    private data class Scenario(val width: Int = 320, val fontScale: Float = 1f, val direction: LayoutDirection = LayoutDirection.Ltr)
    private val scenario = mutableStateOf(Scenario())
    private val actions = mutableListOf<String>()
    private var density = 1f

    @Test fun `setup choices and dismiss retain independent callbacks and button semantics`() {
        render()
        for (tag in listOf("home_setup_suggestions", "home_setup_search", "home_setup_import")) {
            composeRule.onNodeWithTag(tag).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button)).performClick()
        }
        composeRule.onNodeWithContentDescription(copy(R.string.home_setup_dismiss)).performClick()
        assertEquals(listOf("suggestions", "search", "import", "dismiss"), actions)
    }

    @Test fun `copy and controls stay readable and aligned in narrow enlarged and RTL layouts`() {
        render()
        for (value in listOf(Scenario(width = 288), Scenario(width = 260), Scenario(fontScale = 1.6f), Scenario(width = 288, fontScale = 2f), Scenario(fontScale = 1.6f, direction = LayoutDirection.Rtl))) {
            scenario.value = value
            composeRule.waitForIdle()
            val canvas = composeRule.onNodeWithTag("home_setup_banner").fetchSemanticsNode().boundsInRoot
            val title = composeRule.onNodeWithText(copy(R.string.home_setup_title)).fetchSemanticsNode().boundsInRoot
            val suggestions = composeRule.onNodeWithTag("home_setup_suggestions").fetchSemanticsNode().boundsInRoot
            if (value.direction == LayoutDirection.Ltr) {
                assertEquals(title.left, suggestions.left, 1f)
            } else {
                assertEquals(title.right, suggestions.right, 1f)
            }
            assertTrue(title.bottom < suggestions.top)
            for (tag in listOf("home_setup_suggestions", "home_setup_search", "home_setup_import")) {
                val bounds = composeRule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
                assertTrue("$value: $tag remains within the canvas", bounds.left >= canvas.left && bounds.right <= canvas.right && bounds.bottom <= canvas.bottom)
                assertTrue("$value: $tag retains a usable target", bounds.width >= 48f * density && bounds.height >= 48f * density)
            }
            for (string in listOf(R.string.home_setup_title, R.string.home_setup_suggestions, R.string.home_setup_search, R.string.home_setup_import)) {
                val layouts = mutableListOf<TextLayoutResult>()
                composeRule.onNodeWithText(copy(string)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                val layout = layouts.single()
                assertFalse("$value: ${copy(string)} stays fully visible: size=${layout.size}, lines=${layout.lineCount}, widthOverflow=${layout.didOverflowWidth}, heightOverflow=${layout.didOverflowHeight}, lastLineBottom=${layout.getLineBottom(layout.lineCount - 1)}", layout.hasVisualOverflow)
            }
            val dismiss = composeRule.onNodeWithContentDescription(copy(R.string.home_setup_dismiss)).fetchSemanticsNode().boundsInRoot
            assertFalse(title.overlaps(dismiss))
            val headingLayouts = mutableListOf<TextLayoutResult>()
            composeRule.onNodeWithText(copy(R.string.home_setup_title)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(headingLayouts) }
            val firstLineHeight = headingLayouts.single().getLineBottom(0) - headingLayouts.single().getLineTop(0)
            assertEquals("$value: the close control aligns with the first heading line", title.top + firstLineHeight / 2f, dismiss.center.y, 2f)
        }
    }

    @Test fun `normal layout spans discovery across both secondary actions and mirrors in RTL`() {
        render()
        for (direction in LayoutDirection.entries) {
            scenario.value = Scenario(direction = direction)
            composeRule.waitForIdle()
            val suggestions = composeRule.onNodeWithTag("home_setup_suggestions").fetchSemanticsNode().boundsInRoot
            val search = composeRule.onNodeWithTag("home_setup_search").fetchSemanticsNode().boundsInRoot
            val import = composeRule.onNodeWithTag("home_setup_import").fetchSemanticsNode().boundsInRoot
            assertEquals(suggestions.top, search.top, 1f)
            assertEquals(suggestions.bottom, import.bottom, 1f)
            assertTrue(search.bottom < import.top)
            if (direction == LayoutDirection.Ltr) assertTrue(suggestions.right < search.left) else assertTrue(search.right < suggestions.left)
        }
    }

    private fun render() {
        composeRule.setContent {
            val value = scenario.value
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, value.fontScale), LocalLayoutDirection provides value.direction) {
                BoxLoreTheme(dynamicColor = false) {
                    HomeImportBanner(
                        onAiOnboardingClick = { actions.add("suggestions") },
                        onSearchClick = { actions.add("search") },
                        onImportClick = { actions.add("import") },
                        onDismiss = { actions.add("dismiss") },
                        modifier = Modifier.width(value.width.dp),
                    )
                }
            }
        }
    }

    private fun copy(id: Int): String = ApplicationProvider.getApplicationContext<Context>().getString(id)
}
