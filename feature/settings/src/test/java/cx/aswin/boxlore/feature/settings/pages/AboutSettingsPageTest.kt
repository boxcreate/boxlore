package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** About: a full-size Podcast Index button and its three rows, each doing its one thing once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w412dp-h1400dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AboutSettingsPageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun `the Podcast Index button is a full touch target and every row runs once`() {
        val calls = mutableListOf<String>()
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                AboutSettingsPage(
                    AppInfo("1.0", 1L, "cx.aswin.boxlore", "13", 33),
                    onVisitPodcastIndex = { calls += "index" },
                    onOpenChangelog = { calls += "changelog" },
                    onSendFeedback = { calls += "feedback" },
                    onBack = {},
                    onCheckForUpdates = { calls += "updates" },
                )
            }
        }
        val button = compose.onNode(hasText("Visit podcastindex.org") and hasClickAction())
        assertTrue(button.fetchSemanticsNode().boundsInRoot.height >= 48f)
        button.performClick()
        listOf("Send feedback", "Changelog", "Check for updates").forEach { compose.onNodeWithText(it).performScrollTo().performClick() }
        assertEquals(listOf("index", "feedback", "changelog", "updates"), calls)
    }
}
