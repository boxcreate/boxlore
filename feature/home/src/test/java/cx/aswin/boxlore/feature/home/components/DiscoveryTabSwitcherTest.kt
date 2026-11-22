package cx.aswin.boxlore.feature.home.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTab
import cx.aswin.boxlore.core.designsystem.components.DiscoveryTabSwitcher
import cx.aswin.boxlore.core.designsystem.components.discoveryTabSwitcherHeight
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DiscoveryTabSwitcherTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `tabs preserve selection semantics and invoke the page callback once`() {
        val selected = mutableIntStateOf(0)
        val clicks = mutableListOf<Int>()
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                DiscoveryTabSwitcher(
                    tabs = listOf(DiscoveryTab("For you"), DiscoveryTab("Top")),
                    selectedIndex = selected.intValue,
                    onTabSelected = {
                        clicks.add(it)
                        selected.intValue = it
                    },
                )
            }
        }
        composeRule.onNodeWithText("For you").assertIsSelected()
        composeRule.onNodeWithText("Top").assertIsNotSelected().performClick()
        composeRule.onNodeWithText("Top").assertIsSelected()
        composeRule.onNodeWithText("For you").assertIsNotSelected()
        assertEquals(listOf(1), clicks)
    }

    @Test fun `both presentations preserve capped badge and hide zero count`() {
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                Column {
                    listOf(true, false).forEach { floating ->
                        DiscoveryTabSwitcher(
                            tabs = listOf(DiscoveryTab("Shows"), DiscoveryTab("New Eps", badgeCount = 120)),
                            selectedIndex = 0,
                            onTabSelected = {},
                            floating = floating,
                            modifier = Modifier.testTag(if (floating) "floating" else "fixed"),
                        )
                    }
                }
            }
        }
        composeRule.onNodeWithText("0", useUnmergedTree = true).assertDoesNotExist()
        assertEquals(2, composeRule.onAllNodesWithText("99+", useUnmergedTree = true).fetchSemanticsNodes().size)
    }

    @Test fun `RTL and large text share actual height with page clearance`() {
        var clearance = 0f
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f), LocalLayoutDirection provides LayoutDirection.Rtl) {
                BoxLoreTheme(dynamicColor = false) {
                    clearance = discoveryTabSwitcherHeight().value
                    DiscoveryTabSwitcher(
                        tabs = listOf(DiscoveryTab("First"), DiscoveryTab("Second")),
                        selectedIndex = 0,
                        onTabSelected = {},
                        modifier = Modifier.testTag("switcher"),
                    )
                }
            }
        }
        val first = composeRule.onNodeWithText("First").getUnclippedBoundsInRoot()
        val second = composeRule.onNodeWithText("Second").getUnclippedBoundsInRoot()
        val control = composeRule.onNodeWithTag("switcher").getUnclippedBoundsInRoot()
        assertTrue(first.left > second.left)
        assertTrue(clearance > 48f)
        assertEquals(clearance, (control.bottom - control.top).value, 0.1f)
        assertTrue(first.bottom - first.top <= control.bottom - control.top)
        composeRule.onNodeWithText("First").assertIsSelected()
    }

    @Test fun `target styling waits for the moving indicator and shares its clock`() {
        val selected = mutableIntStateOf(0)
        composeRule.setContent {
            BoxLoreTheme(dynamicColor = false) {
                DiscoveryTabSwitcher(
                    tabs = listOf(DiscoveryTab("First"), DiscoveryTab("Second")),
                    selectedIndex = selected.intValue,
                    onTabSelected = { selected.intValue = it },
                )
            }
        }
        val initialStyle = labelStyle("Second")
        val selectedColor = labelStyle("First").color
        val start = composeRule.onNodeWithTag("discovery_tab_indicator").getUnclippedBoundsInRoot()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Second").performClick()
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Second").assertIsSelected()
        assertEquals(start, composeRule.onNodeWithTag("discovery_tab_indicator").getUnclippedBoundsInRoot())
        assertEquals(initialStyle.color, labelStyle("Second").color)
        assertEquals(initialStyle.fontWeight, labelStyle("Second").fontWeight)
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.waitForIdle()
        val moving = composeRule.onNodeWithTag("discovery_tab_indicator").getUnclippedBoundsInRoot()
        val coverage = ((moving.left - start.left) / (start.right - start.left)).coerceIn(0f, 1f)
        assertTrue(coverage > 0f && coverage < 1f)
        val expected = lerp(initialStyle.color, selectedColor, coverage)
        val actual = labelStyle("Second").color
        // Indicator placement is rounded to pixels; foreground interpolation remains continuous.
        assertTrue(kotlin.math.abs(expected.red - actual.red) < 0.01f)
        assertTrue(kotlin.math.abs(expected.green - actual.green) < 0.01f)
        assertTrue(kotlin.math.abs(expected.blue - actual.blue) < 0.01f)
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        assertEquals(selectedColor, labelStyle("Second").color)
        assertEquals(initialStyle.fontWeight, labelStyle("Second").fontWeight)
    }

    @Test fun `rapid reversed selections keep one indicator and settle on the latest tab`() {
        val selected = mutableIntStateOf(0)
        composeRule.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BoxLoreTheme(dynamicColor = false) {
                    DiscoveryTabSwitcher(
                        tabs = listOf(DiscoveryTab("First"), DiscoveryTab("Second")),
                        selectedIndex = selected.intValue,
                        onTabSelected = { selected.intValue = it },
                    )
                }
            }
        }
        val start = composeRule.onNodeWithTag("discovery_tab_indicator").getUnclippedBoundsInRoot()
        composeRule.mainClock.autoAdvance = false
        composeRule.onNodeWithText("Second").performClick()
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(80)
        composeRule.waitForIdle()
        assertTrue("Start: $start; moving: ${composeRule.onNodeWithTag("discovery_tab_indicator").getUnclippedBoundsInRoot()}", composeRule.onNodeWithTag("discovery_tab_indicator").getUnclippedBoundsInRoot().left < start.left)
        composeRule.onNodeWithText("First").performClick()
        composeRule.runOnUiThread { Snapshot.sendApplyNotifications() }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("First").assertIsSelected()
        assertEquals(start, composeRule.onNodeWithTag("discovery_tab_indicator").getUnclippedBoundsInRoot())
    }

    @Test fun `long Portuguese tab names at large text with a badge are never cut`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(360.dp)) {
                        DiscoveryTabSwitcher(
                            tabs = listOf(DiscoveryTab("Insights", Icons.Rounded.AutoAwesome), DiscoveryTab("Linha do tempo", Icons.Rounded.History, badgeCount = 12)),
                            selectedIndex = 1,
                            onTabSelected = {},
                        )
                    }
                }
            }
        }
        listOf("Insights", "Linha do tempo").forEach { label ->
            val layout = textLayout(label)
            assertFalse("$label is cut", layout.hasVisualOverflow)
            assertFalse("$label is shortened", layout.isLineEllipsized(0))
        }
    }

    @Test fun `English tabs on a phone keep the 280dp floating pill`() {
        composeRule.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreTestWindows.PHONE) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(412.dp)) {
                        DiscoveryTabSwitcher(
                            tabs = listOf(DiscoveryTab("For you", Icons.Rounded.AutoAwesome), DiscoveryTab("Top", Icons.Rounded.History)),
                            selectedIndex = 0,
                            onTabSelected = {},
                            modifier = Modifier.testTag("switcher"),
                        )
                    }
                }
            }
        }
        val pill = composeRule.onNodeWithTag("switcher").getUnclippedBoundsInRoot()
        assertEquals(280f, (pill.right - pill.left).value, 0.5f)
        val tabs = tabBounds()
        assertEquals("equal tabs", tabs[0].width, tabs[1].width, 1f)
    }

    @Config(qualifiers = "w1300dp-h1000dp-mdpi")
    @Test
    fun `the docked switcher stops at 560dp on tablets and keeps its tab through resizes`() {
        val window = mutableStateOf(BoxloreTestWindows.PHONE)
        val selected = mutableIntStateOf(1)
        composeRule.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides window.value) {
                BoxLoreTheme(dynamicColor = false) {
                    Box(Modifier.width(window.value.widthDp.dp)) {
                        DiscoveryTabSwitcher(
                            tabs = listOf(DiscoveryTab("Shows", Icons.Rounded.AutoAwesome), DiscoveryTab("New Eps", Icons.Rounded.History, badgeCount = 4)),
                            selectedIndex = selected.intValue,
                            onTabSelected = { selected.intValue = it },
                            floating = false,
                        )
                    }
                }
            }
        }
        val widths = mapOf(BoxloreTestWindows.PHONE to 412f, BoxloreTestWindows.TABLET to 560f)
        composeRule.walkWindows(window, BoxloreTestWindows.PHONE, BoxloreTestWindows.TABLET, BoxloreTestWindows.PHONE, BoxloreTestWindows.TABLET) { step ->
            val tabs = tabBounds()
            assertEquals("$step", widths.getValue(step), tabs.last().right - tabs.first().left, 1f)
            composeRule.onNodeWithText("New Eps").assertIsSelected()
            val indicator = composeRule.onNodeWithTag("discovery_tab_indicator").getUnclippedBoundsInRoot()
            assertEquals("$step indicator", tabs[1].left, indicator.left.value, 1f)
            assertEquals("$step indicator", tabs[1].right, indicator.right.value, 1f)
        }
        assertEquals(1, selected.intValue)
    }

    private fun tabBounds() = composeRule.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        .fetchSemanticsNodes().map { it.boundsInRoot }.sortedBy { it.left }

    private fun textLayout(label: String): TextLayoutResult {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single()
    }

    private fun labelStyle(label: String): TextStyle {
        val layouts = mutableListOf<TextLayoutResult>()
        composeRule.onNodeWithText(label, useUnmergedTree = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts)
        return layouts.single().layoutInput.style
    }
}
