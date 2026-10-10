package cx.aswin.boxlore.updates

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import cx.aswin.boxlore.core.designsystem.components.LocalUpdateAvailableAction
import cx.aswin.boxlore.core.designsystem.components.TopBarUtilityActions
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class UpdateEntryPointsTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun `update icon only appears for available action and preserves utility buttons`() {
        var updateClicks = 0
        var settingsClicks = 0
        var feedbackClicks = 0
        var feedbackLongClicks = 0
        var settingsLongClicks = 0
        val action = mutableStateOf<(() -> Unit)?>(null)
        composeRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(LocalUpdateAvailableAction provides action.value) {
                    TopBarUtilityActions(onFeedbackClick = { feedbackClicks++ }, onSettingsClick = { settingsClicks++ }, onFeedbackLongClick = { feedbackLongClicks++ }, onSettingsLongClick = { settingsLongClicks++ })
                }
            }
        }
        composeRule.onNodeWithContentDescription("Update available").assertDoesNotExist()
        val utilityCenter = composeRule.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot.center.y
        composeRule.runOnIdle { action.value = { updateClicks++ } }
        composeRule.onNodeWithContentDescription("Update available").assertIsDisplayed().performClick()
        assertEquals(utilityCenter, composeRule.onNodeWithContentDescription("Settings").fetchSemanticsNode().boundsInRoot.center.y, 0.01f)
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.onNodeWithContentDescription("Send Feedback").performClick()
        assertEquals(1, updateClicks)
        assertEquals(1, settingsClicks)
        assertEquals(1, feedbackClicks)
        composeRule.onNodeWithContentDescription("Settings").performTouchInput { longClick() }
        composeRule.onNodeWithContentDescription("Send Feedback").performTouchInput { longClick() }
        assertEquals(1, settingsLongClicks)
        assertEquals(1, feedbackLongClicks)
        assertEquals(1, settingsClicks)
        assertEquals(1, feedbackClicks)
        composeRule.runOnIdle { action.value = null }
        composeRule.onNodeWithContentDescription("Update available").assertDoesNotExist()
    }
}
