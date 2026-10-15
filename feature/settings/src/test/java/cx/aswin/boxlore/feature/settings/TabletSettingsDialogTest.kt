package cx.aswin.boxlore.feature.settings

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.unit.Density
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFold
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldOrientation
import cx.aswin.boxlore.core.designsystem.layout.BoxloreFoldState
import cx.aswin.boxlore.core.designsystem.layout.BoxloreLayoutBounds
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxlore.feature.settings.dialogs.ResetAnalyticsDialog
import cx.aswin.boxlore.feature.settings.dialogs.SettingsRssTestTags
import cx.aswin.boxlore.feature.settings.pages.AccountSettingsDialogs
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
class TabletSettingsDialogTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(673, 841, false))

    @Test fun `folded analytics explanation scrolls while owner actions remain reachable`() {
        var cancelled = 0
        render { ResetAnalyticsDialog({}, { cancelled++ }) }
        scrollToBottom()
        fixture.compose.onNodeWithText("This does not reset the separate device ID used for recommendations and transcripts. To delete PostHog data, send a deletion request from Privacy.").assertIsDisplayed()
        fixture.compose.onNodeWithText("Cancel").assertIsDisplayed().performClick()
        assertEquals(1, cancelled)
    }

    @Test fun `folded RSS field with a long error has a scroll viewport and visible actions`() {
        render { AddRssFeedDialog("https://example.com/feed.xml", "The feed could not be loaded. ".repeat(20), false, {}, {}, {}) }
        scrollToBottom()
        fixture.compose.onNodeWithTag(SettingsRssTestTags.CONFIRM).assertIsDisplayed()
        fixture.compose.onNodeWithTag(SettingsRssTestTags.CANCEL).assertIsDisplayed()
    }

    @Test fun `folded account explanation scrolls to its information link without deleting`() {
        var opened = 0
        render(1.6f) { AccountSettingsDialogs(true, false, {}, {}, {}, {}, { opened++ }) }
        scrollToBottom(requireOverflow = false)
        fixture.compose.onNodeWithText("Learn how your data is handled after deletion").assertIsDisplayed().performClick()
        assertEquals(1, opened)
        fixture.compose.onNodeWithText("Cancel").assertIsDisplayed()
    }

    private fun render(fontScale: Float = 1.3f, content: @Composable () -> Unit) {
        val fold = BoxloreFold(BoxloreFoldState.HalfOpened, BoxloreFoldOrientation.Horizontal, BoxloreLayoutBounds(0f, 410f, 673f, 431f), true, true)
        fixture.setContent {
            CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(673f, 841f, listOf(fold)), LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                BoxLoreTheme(dynamicColor = false, content = content)
            }
        }
        settle()
        val panel = fixture.compose.onNodeWithTag("boxlore_dialog_panel").fetchSemanticsNode().boundsInRoot
        assertTrue(panel.top >= 431f && panel.bottom <= 841f)
    }

    private fun scrollToBottom(requireOverflow: Boolean = true) {
        val body = fixture.compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        if (requireOverflow) assertTrue(body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue() > 0f)
        body.performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 10_000f) }
        settle()
        if (requireOverflow) assertTrue(body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.mainClock.advanceTimeBy(1_600)
        fixture.compose.waitForIdle()
    }
}
