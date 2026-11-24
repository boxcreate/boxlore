package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Home's side rails and the phone genre row used to clip their cards and chips at an invisible line
 * 16dp in from the screen edge. Rails now run to the leading edge, so a scrolled card slides off the
 * edge, while at rest they are unchanged: the first card on the gutter at the same size and the next
 * card cut just as visibly. The genre row runs to both edges. With side insets (a sideways phone's
 * buttons or cutout) a rail stops at the safe edge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w1000dp-h1000dp-mdpi")
class HomeRailBleedTest {
    @get:Rule val composeRule = createComposeRule()

    private val feedWidth = mutableStateOf(412.dp)
    private val safeStart = mutableStateOf(0.dp)
    private val safeEnd = mutableStateOf(0.dp)

    @Test
    fun `rails reach the leading edge and keep their resting layout on phones, wide panes and inside side insets`() {
        renderRail()
        for ((width, insets) in listOf(412.dp to (0.dp to 0.dp), 1000.dp to (0.dp to 0.dp), 915.dp to (32.dp to 48.dp), 412.dp to (0.dp to 0.dp))) {
            feedWidth.value = width
            safeStart.value = insets.first
            safeEnd.value = insets.second
            composeRule.onNodeWithTag("home_discovery_rail").performSemanticsAction(SemanticsActions.ScrollBy) { it(-10_000f, 0f) }
            composeRule.waitForIdle()
            val label = "${width.value}dp with insets $insets"
            val rail = composeRule.onNodeWithTag("home_discovery_rail").fetchSemanticsNode().boundsInRoot
            assertEquals("$label: rail start", insets.first.value, rail.left, 0.5f)
            assertEquals("$label: rail end", (width - insets.second - 16.dp).value, rail.right, 0.5f)
            // At rest: the first card on the gutter, at the size the slot inside the gutter gives it.
            val slot = width - insets.first - insets.second - 32.dp
            val first = card(0)
            assertEquals("$label: first card at rest", (insets.first + 16.dp).value, first.left, 0.5f)
            assertEquals("$label: card width", HomeFeedSpacing.railCardWidth(slot, false).value, first.width, 0.5f)
            assertEquals("$label: second card at rest", first.right + HomeFeedSpacing.RailItemGap.value, card(1).left, 0.5f)
            composeRule.onNodeWithTag("home_discovery_rail").performSemanticsAction(SemanticsActions.ScrollBy) { it(100f, 0f) }
            composeRule.waitForIdle()
            val visible = composeRule.onAllNodesWithTag("rail_card", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
            assertTrue("$label: no card slides past the gutter", visible.any { it.left < insets.first.value + 16f && it.right > insets.first.value })
            assertTrue("$label: a card goes past the safe edge", visible.none { it.left < insets.first.value - 0.5f })
        }
    }

    @Test
    fun `the phone genre row runs to the screen edge and keeps its first chip on the gutter`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(412.dp).padding(horizontal = 16.dp)) {
                        GenreSelector(selectedCategory = null, onCategorySelected = {}, modifier = Modifier.testTag("genre_row"))
                    }
                }
            }
        }
        val chips = composeRule.onAllNodesWithTag("home_genre_chip", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertEquals("first chip at rest", 16f, chips.minOf { it.left }, 0.5f)
        assertTrue("chips run past the gutter to the screen edge: $chips", chips.maxOf { it.right } > 396.5f)
        // Scrolled to More genres at the end, the chips before it slide off past the screen edge.
        composeRule.onNodeWithTag("genre_row").performScrollToNode(hasTestTag("home_genres_more"))
        composeRule.waitForIdle()
        val scrolled = composeRule.onAllNodesWithTag("home_genre_chip", useUnmergedTree = true).fetchSemanticsNodes().map { it.boundsInRoot }
        assertTrue("a chip shows between the screen edge and the gutter: $scrolled", scrolled.any { it.left < 15.5f && it.right > 0f })
    }

    private fun card(index: Int): Rect {
        val node = composeRule.onAllNodesWithTag("rail_card", useUnmergedTree = true).fetchSemanticsNodes()[index]
        return Rect(node.positionInRoot, node.size.toSize())
    }

    private fun renderRail() {
        composeRule.setContent {
            // Home's grid: the feed's width less its safe insets, then 16dp of gutter each side.
            Box(Modifier.width(feedWidth.value).padding(start = safeStart.value + 16.dp, end = safeEnd.value + 16.dp)) {
                HomeDiscoveryRail { cardWidth: Dp ->
                    items(List(10) { it }) { Box(Modifier.width(cardWidth).height(120.dp).testTag("rail_card")) }
                }
            }
        }
    }
}
