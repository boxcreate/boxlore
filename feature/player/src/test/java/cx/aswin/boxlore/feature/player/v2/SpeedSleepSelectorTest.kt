package cx.aswin.boxlore.feature.player.v2

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
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
                    sleepTimerEnd = System.currentTimeMillis() + 25 * 60_000L,
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
