package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The tray's speed and sleep choices say which one is on, and a running timer stays selected. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w360dp-h780dp-mdpi")
class SpeedSleepSelectorTest {
    @get:Rule val compose = createComposeRule()
    private val shape = RoundedCornerShape(8.dp)
    private val shapes = QuickControlShapes(shape, shape, shape, shape, shape)

    @Test
    fun `a 30-minute timer reopened five minutes later still shows 30 minutes selected`() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                InlineSleepSelector(
                    sleepTimerEnd = System.currentTimeMillis() + 25 * 60_000L - 1_000L,
                    sleepAtEndOfEpisode = false,
                    colorScheme = MaterialTheme.colorScheme,
                    shapes = shapes,
                    onBack = {},
                    onDurationSelected = {},
                    presetMinutes = 30,
                )
            }
        }
        compose.onNodeWithText("30", substring = true).assertIsSelected()
        compose.onNodeWithText("Off").assertIsNotSelected()
        compose.onNodeWithText("15", substring = true).assertIsNotSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        // The selected length shows the time left under its name; the choices are announced as one group.
        compose.onNodeWithText("30", substring = true).assert(hasText("24:", substring = true))
        compose.onNodeWithText("15", substring = true).assert(hasText(":", substring = true).not())
        compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup)).assertCountEquals(2)
    }

    @Test
    fun `the sleep sheet keeps the running timer's length selected, and Off only with no timer`() {
        val picked = mutableListOf<Int>()
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                SleepSheet(
                    sleepTimerEnd = System.currentTimeMillis() + 25 * 60_000L - 1_000L,
                    colorScheme = MaterialTheme.colorScheme,
                    onDurationSelected = { picked += it },
                    onDismiss = {},
                    presetMinutes = 30,
                )
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("30", substring = true, useUnmergedTree = false).assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        compose.onNodeWithText("Off").assertIsNotSelected()
        compose.onNodeWithText("15", substring = true).assertIsNotSelected().performSemanticsAction(SemanticsActions.OnClick)
        compose.runOnIdle { assertEquals(listOf(15), picked) }
    }

    @Test
    fun `speed presets say which is on and the current speed is a value, not a disabled button`() {
        compose.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                InlineSpeedSelector(1.5f, MaterialTheme.colorScheme, shapes, onBack = {}, onSpeedSelected = {})
            }
        }
        compose.onNodeWithContentDescription("Playback speed")
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Disabled))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, cx.aswin.boxlore.feature.player.v2.logic.formatSpeedLabel(1.5f)))
        compose.onNodeWithText(cx.aswin.boxlore.feature.player.v2.logic.formatSpeedLabel(2f)).assertIsNotSelected()
    }
}
