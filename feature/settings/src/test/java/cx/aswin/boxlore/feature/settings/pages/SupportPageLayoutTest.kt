package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorld
import cx.aswin.boxlore.feature.settings.pages.supportworlds.supportWorldSkin
import cx.aswin.boxlore.feature.settings.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SupportPageLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val styles = listOf(ControlSupportPageStyle) +
        SupportWorld.entries.mapNotNull { supportWorldSkin(it) }.map(::supportWorldPageStyle)

    @Test fun `phones keep today's stacked page, and the mission card yields before anything else`() {
        assertEquals(SupportPageArrangement(sideBySide = false, showMission = true), supportPageArrangement(412.dp, 780.dp, 1f))
        assertEquals(SupportPageArrangement(sideBySide = false, showMission = true), supportPageArrangement(412.dp, 645.dp, 1f))
        assertEquals(SupportPageArrangement(sideBySide = false, showMission = false), supportPageArrangement(360.dp, 644.dp, 1f))
        assertEquals(SupportPageArrangement(sideBySide = false, showMission = true), supportPageArrangement(412.dp, 780.dp, 1.3f))
        assertEquals(SupportPageArrangement(sideBySide = false, showMission = false), supportPageArrangement(412.dp, 780.dp, 1.5f))
    }

    @Test fun `short wide windows put the stage beside the controls`() {
        assertEquals(SupportPageArrangement(sideBySide = true, showMission = false), supportPageArrangement(850.dp, 330.dp, 1f))
        assertEquals(SupportPageArrangement(sideBySide = true, showMission = true), supportPageArrangement(900.dp, 490.dp, 1f))
        assertEquals(SupportPageArrangement(sideBySide = false, showMission = true), supportPageArrangement(1280.dp, 690.dp, 1f))
    }

    @Test fun `the page scrolls only when even the reduced page cannot fit, never where it fits`() {
        assertEquals(false, supportPageArrangement(443.dp, 560.dp, 1f).scrolls)
        assertEquals(false, supportPageArrangement(443.dp, 560.dp, 1.3f).scrolls)
        assertEquals(true, supportPageArrangement(443.dp, 560.dp, 1.7f).scrolls)
        assertEquals(true, supportPageArrangement(443.dp, 560.dp, 2f).scrolls)
        listOf(supportPageArrangement(392.dp, 800.dp, 1f), supportPageArrangement(360.dp, 600.dp, 1f), supportPageArrangement(800.dp, 392.dp, 1f)).forEach {
            assertEquals(false, it.scrolls)
        }
    }

    // Regression: at large text on the Fold 8 cover the tier picker and its button were pushed off the bottom.
    @Config(qualifiers = "w443dp-h649dp", fontScale = 1.7f)
    @Test
    fun `at large text on the cover screen every world's button can be reached`() {
        var style by mutableStateOf(styles.first())
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                key(style.world) { SupportDevelopmentPage(onBack = {}, totalListeningHours = 12L, style = style) }
            }
        }
        styles.forEach { next ->
            style = next
            compose.waitForIdle()
            val cta = compose.onNodeWithText(text(next.ctaVerb, next.tiers[2].title), substring = true)
            cta.performScrollTo().assertIsDisplayed().assertHasClickAction()
            // The price on the button shows whole; a long name shortens instead.
            val button = cta.fetchSemanticsNode().boundsInRoot
            val cost = next.tiers[2].cost
            val prices = compose.onAllNodesWithText(cost, useUnmergedTree = true).fetchSemanticsNodes()
                .filter { button.contains(it.boundsInRoot.center) }
            assertTrue("${next.world}: a price on the button", prices.isNotEmpty())
            prices.forEach { node ->
                val layouts = mutableListOf<TextLayoutResult>()
                node.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
                assertEquals("${next.world}: price $cost cut", cost.length, layouts.last().getLineEnd(0))
            }
        }
    }

    @Config(qualifiers = "w800dp-h1200dp")
    @Test
    fun `on a portrait tablet the page sits in the middle instead of bunching at the top`() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(800f, 1_200f)) {
                    SupportDevelopmentPage(onBack = {}, totalListeningHours = 12L)
                }
            }
        }
        compose.waitForIdle()
        val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
        val missionBottom = compose.onNodeWithText("Keeping boxlore fast, open, and ad-free").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("space under the page ($missionBottom of $rootBottom)", rootBottom - missionBottom < rootBottom / 3f)
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
    }

    @Config(qualifiers = "w392dp-h800dp")
    @Test
    fun `every world fills a portrait phone like today's page, without scrolling`() = assertEveryStyleFits(mission = true)

    @Config(qualifiers = "w360dp-h600dp")
    @Test
    fun `every world fits a small phone by dropping the mission card`() = assertEveryStyleFits(mission = false)

    @Config(qualifiers = "w800dp-h392dp-land")
    @Test
    fun `every world fits a landscape phone side by side`() = assertEveryStyleFits(mission = false)

    private fun assertEveryStyleFits(mission: Boolean) {
        var style by mutableStateOf(styles.first())
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                key(style.world) { SupportDevelopmentPage(onBack = {}, totalListeningHours = 12L, style = style) }
            }
        }
        styles.forEach { next ->
            style = next
            compose.waitForIdle()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
            val rootBottom = compose.onRoot().fetchSemanticsNode().boundsInRoot.bottom
            listOf(text(next.ctaVerb, next.tiers[2].title), "One-time contribution", text(next.tiers[2].powerImpact)).forEach { label ->
                val bounds = compose.onNodeWithText(label, substring = true).fetchSemanticsNode().boundsInRoot
                assertTrue("$label is cut off in ${next.world}", bounds.top >= 0f && bounds.bottom <= rootBottom)
            }
            val missionShown = compose.onAllNodesWithText("Keeping boxlore fast, open, and ad-free")
                .fetchSemanticsNodes()
                .any { it.boundsInRoot.bottom <= rootBottom }
            assertEquals("mission card in ${next.world}", mission, missionShown)
        }
    }
}
