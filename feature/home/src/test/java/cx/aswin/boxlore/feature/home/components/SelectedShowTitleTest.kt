package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SelectedShowTitleTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `RSS icon stays centered beside a short title`() {
        assertInlineLayout("Show", fontScale = 1f, direction = LayoutDirection.Ltr)
    }

    @Test fun `long title leaves space for RSS icon in a narrow header`() {
        assertInlineLayout("A very long podcast title that cannot fit on one line", fontScale = 1f, direction = LayoutDirection.Ltr)
    }

    @Test fun `enlarged text retains a centered RSS icon`() {
        assertInlineLayout("A very long podcast title", fontScale = 2f, direction = LayoutDirection.Ltr)
    }

    @Test fun `RTL places RSS icon after the title with the same alignment`() {
        assertInlineLayout("A very long podcast title", fontScale = 1f, direction = LayoutDirection.Rtl)
    }

    @Test fun `catalog title omits the RSS source icon`() {
        composeRule.setContent {
            MaterialTheme { SelectedShowTitle("Show", isRss = false, modifier = Modifier.width(160.dp)) }
        }
        composeRule.onNodeWithText("Show").assertExists()
        composeRule.onNodeWithContentDescription("RSS feed").assertDoesNotExist()
    }

    private fun assertInlineLayout(title: String, fontScale: Float, direction: LayoutDirection) {
        var density = 1f
        composeRule.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(
                LocalDensity provides Density(density, fontScale),
                LocalLayoutDirection provides direction,
            ) {
                MaterialTheme {
                    SelectedShowTitle(title, isRss = true, modifier = Modifier.width(160.dp).testTag("title_row"))
                }
            }
        }
        val text = composeRule.onNodeWithText(title).fetchSemanticsNode().boundsInRoot
        val icon = composeRule.onNodeWithContentDescription("RSS feed").fetchSemanticsNode().boundsInRoot
        val row = composeRule.onNodeWithTag("title_row").fetchSemanticsNode().boundsInRoot
        assertEquals(text.center.y, icon.center.y, 1f)
        val gap = if (direction == LayoutDirection.Ltr) icon.left - text.right else text.left - icon.right
        assertEquals(6f * density, gap, 1f)
        assertTrue(icon.left >= row.left && icon.right <= row.right)
        assertTrue(text.left >= row.left && text.right <= row.right)
    }
}
