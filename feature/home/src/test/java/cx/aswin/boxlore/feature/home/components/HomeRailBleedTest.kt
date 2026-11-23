package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's side rails used to clip their cards at an invisible line 16dp in from the screen edge. They
 * now run to the edge of the feed, so a card slides off the edge, while at rest the first card still
 * starts on the gutter, as before.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi")
class HomeRailBleedTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun `on a phone a rail reaches the screen edges and keeps its first card on the gutter`() = check(412)

    @Test
    fun `on a wide pane the rail stops at the pane's edges`() = check(1000)

    private fun check(feedWidth: Int) {
        run {
            render(feedWidth)
            val rail = composeRule.onNodeWithTag("home_discovery_rail").fetchSemanticsNode().boundsInRoot
            assertEquals("$feedWidth: rail start", 0f, rail.left, 0.5f)
            assertEquals("$feedWidth: rail end", feedWidth.toFloat(), rail.right, 0.5f)
            assertEquals("$feedWidth: first card at rest", 16f, card(0).left, 0.5f)
            composeRule.onNodeWithTag("home_discovery_rail").performSemanticsAction(SemanticsActions.ScrollBy) { it(100f, 0f) }
            composeRule.waitForIdle()
            val visible = composeRule.onAllNodesWithTag("rail_card", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
            assertTrue("$feedWidth: no card slides past the gutter", visible.any { it.left < 16f && it.right > 0f })
        }
    }

    private fun card(index: Int) = composeRule.onAllNodesWithTag("rail_card", useUnmergedTree = true).fetchSemanticsNodes()[index].boundsInRoot

    private fun render(feedWidth: Int) {
        composeRule.setContent {
            // Home's grid: the feed's width, 16dp of gutter each side.
            Box(Modifier.width(feedWidth.dp).padding(horizontal = 16.dp)) {
                HomeDiscoveryRail { cardWidth ->
                    items(List(10) { it }) { Box(Modifier.width(cardWidth).height(120.dp).testTag("rail_card")) }
                }
            }
        }
    }
}
