package cx.aswin.boxlore.feature.home.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import cx.aswin.boxlore.feature.home.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxlore.feature.home.settings.dialogs.SettingsRssTestTags
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Optional screenshot baseline stub (P26).
 *
 * Not wired into CI. Enable locally when capturing baselines under
 * `screenshots/baselines/` — see `docs/screenshots/README.md`.
 *
 * Approach: Compose `createComposeRule` + manual / Papyrus-style capture.
 * Roborazzi is intentionally not added to keep deps light.
 */
@RunWith(AndroidJUnit4::class)
class AddRssFeedDialogScreenshotStubTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Ignore("Optional local screenshot baseline; see docs/screenshots/README.md")
    @Test
    fun captureAddRssDialog_baselineStub() {
        composeRule.setContent {
            AddRssFeedDialog(
                url = "https://example.com/feed.xml",
                error = null,
                isAdding = false,
                onUrlChange = {},
                onConfirm = {},
                onDismiss = {},
            )
        }

        // Assert the tagged control is present so a future capture step has an anchor.
        composeRule.onNodeWithTag(SettingsRssTestTags.URL_FIELD).assertIsDisplayed()
        composeRule.onRoot().assertIsDisplayed()
        // Capture tip (manual): use Android Studio Layout Inspector / device screenshot,
        // or composeRule.onRoot().captureToImage() and write PNG under screenshots/baselines/.
    }
}
