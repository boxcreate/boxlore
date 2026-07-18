package cx.aswin.boxlore.feature.home.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.feature.home.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxlore.feature.home.settings.dialogs.SettingsRssTestTags
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Focused Compose UI smoke for the Settings "Add RSS feed" dialog.
 * Hosted in `:feature:home` so it needs no app-level DI.
 */
@RunWith(AndroidJUnit4::class)
class AddRssFeedDialogUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyUrl_disablesAdd_andTypingEnablesConfirm() {
        var confirmed = false

        composeRule.setContent {
            var url by remember { mutableStateOf("") }
            AddRssFeedDialog(
                url = url,
                error = null,
                isAdding = false,
                onUrlChange = { url = it },
                onConfirm = { confirmed = true },
                onDismiss = {},
            )
        }

        composeRule.onNodeWithText("Add RSS feed").assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsRssTestTags.CONFIRM).assertIsNotEnabled()
        composeRule.onNodeWithTag(SettingsRssTestTags.CANCEL).assertIsEnabled()

        composeRule.onNodeWithTag(SettingsRssTestTags.URL_FIELD)
            .performTextInput("https://example.com/feed.xml")

        composeRule.onNodeWithTag(SettingsRssTestTags.CONFIRM).assertIsEnabled()
        composeRule.onNodeWithTag(SettingsRssTestTags.CONFIRM).performClick()
        assertTrue(confirmed)
    }

    @Test
    fun cancel_invokesDismiss() {
        var dismissed = false

        composeRule.setContent {
            AddRssFeedDialog(
                url = "",
                error = null,
                isAdding = false,
                onUrlChange = {},
                onConfirm = {},
                onDismiss = { dismissed = true },
            )
        }

        composeRule.onNodeWithTag(SettingsRssTestTags.CANCEL).performClick()
        assertTrue(dismissed)
    }
}
