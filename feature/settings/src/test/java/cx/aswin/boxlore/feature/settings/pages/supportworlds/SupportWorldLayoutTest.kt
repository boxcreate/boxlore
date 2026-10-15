package cx.aswin.boxlore.feature.settings.pages.supportworlds

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SupportWorldLayoutTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val worlds = SupportWorld.entries.mapNotNull { supportWorldSkin(it) }

    @Test fun `tall windows stack, and the mission card yields before the stage shrinks too far`() {
        assertEquals(SupportWorldArrangement(sideBySide = false, showMission = true), supportWorldArrangement(392.dp, 760.dp, 1f))
        assertEquals(SupportWorldArrangement(sideBySide = false, showMission = false), supportWorldArrangement(392.dp, 600.dp, 1f))
        assertEquals(SupportWorldArrangement(sideBySide = false, showMission = true), supportWorldArrangement(392.dp, 760.dp, 1.3f))
        assertEquals(SupportWorldArrangement(sideBySide = false, showMission = false), supportWorldArrangement(392.dp, 760.dp, 1.5f))
    }

    @Test fun `short landscape windows go side by side, and the 560dp height gate keeps tablets stacked`() {
        assertEquals(SupportWorldArrangement(sideBySide = true, showMission = false), supportWorldArrangement(800.dp, 360.dp, 1f))
        assertEquals(SupportWorldArrangement(sideBySide = true, showMission = true), supportWorldArrangement(900.dp, 520.dp, 1f))
        assertEquals(true, supportWorldArrangement(900.dp, 559.dp, 1f).sideBySide)
        assertEquals(false, supportWorldArrangement(900.dp, 560.dp, 1f).sideBySide)
        assertEquals(false, supportWorldArrangement(500.dp, 520.dp, 1f).sideBySide)
    }

    @Config(qualifiers = "w392dp-h800dp")
    @Test
    fun `every world fits a portrait phone without scrolling`() = assertWorldsFitWithoutScrolling()

    @Config(qualifiers = "w800dp-h392dp-land")
    @Test
    fun `every world fits a landscape phone without scrolling`() = assertWorldsFitWithoutScrolling()

    private fun assertWorldsFitWithoutScrolling() {
        var skin by mutableStateOf(worlds.first())
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                key(skin.world) {
                    SupportWorldPage(
                        skin = skin,
                        session = SupportPageSession(
                            SupportWorldAssignment(skin.world, SupportWorldSource.Experiment),
                            RecordingSupportSink(),
                            { 0L },
                        ),
                        onBack = {},
                        totalListeningHours = 12L,
                    )
                }
            }
        }
        worlds.forEach { world ->
            skin = world
            compose.waitForIdle()
            compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(0)
            compose.onNodeWithText("${world.ctaVerb} ${world.tierNames[2]} · $2.49").assertIsDisplayed()
            SUPPORT_OFFERS.forEach { offer ->
                compose.onNode(hasContentDescription("${world.tierNames[offer.index]}, ${offer.serverTime}, ${offer.priceLabel}")).assertIsDisplayed()
            }
        }
    }
}
