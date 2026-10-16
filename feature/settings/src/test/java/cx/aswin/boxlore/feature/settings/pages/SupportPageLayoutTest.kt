package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.settings.pages.supportworlds.SupportWorld
import cx.aswin.boxlore.feature.settings.pages.supportworlds.supportWorldSkin
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
            listOf("${next.ctaVerb} ${next.tiers[2].title}", "One-time contribution", next.tiers[2].powerImpact).forEach { text ->
                val bounds = compose.onNodeWithText(text, substring = true).fetchSemanticsNode().boundsInRoot
                assertTrue("$text is cut off in ${next.world}", bounds.top >= 0f && bounds.bottom <= rootBottom)
            }
            val missionShown = compose.onAllNodesWithText("Keeping boxlore fast, open, and ad-free")
                .fetchSemanticsNodes()
                .any { it.boundsInRoot.bottom <= rootBottom }
            assertEquals("mission card in ${next.world}", mission, missionShown)
        }
    }
}
