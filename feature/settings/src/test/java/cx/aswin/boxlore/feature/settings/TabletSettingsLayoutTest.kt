package cx.aswin.boxlore.feature.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
class TabletSettingsLayoutTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1280, 1280, false))
    private val paneWidth = mutableStateOf(1200)
    private val selected = mutableStateOf(ProfileSettingsDestination.Appearance)
    private var backs = 0
    private lateinit var view: View
    private var surface = 0
    private var backVisible = false

    // Regression: Appearance's own pages lost their Back arrow and category highlight in the tablet split.
    @Test fun `appearance pages keep their back arrow and highlight Appearance in the split`() {
        selected.value = ProfileSettingsDestination.AppearanceHome
        render()
        fixture.compose.onNodeWithText("Appearance").assertIsSelected()
        assertTrue(backVisible)
        fixture.compose.runOnIdle { selected.value = ProfileSettingsDestination.Downloads }
        settle()
        assertFalse(backVisible)
    }

    @Test fun `split categories follow each page's parent`() {
        assertEquals(ProfileSettingsDestination.Appearance, settingsCategoryOf(ProfileSettingsDestination.AppearanceText))
        assertEquals(ProfileSettingsDestination.Appearance, settingsCategoryOf(ProfileSettingsDestination.CustomTheme))
        assertEquals(ProfileSettingsDestination.SyncAndBackups, settingsCategoryOf(ProfileSettingsDestination.Account))
        assertEquals(ProfileSettingsDestination.Downloads, settingsCategoryOf(ProfileSettingsDestination.Downloads))
        assertEquals(ProfileSettingsDestination.Library, settingsCategoryOf(ProfileSettingsDestination.Recommendations))
    }

    @Test fun `Recommendations keeps its back arrow and highlights Library in the split through resizes`() {
        selected.value = ProfileSettingsDestination.Recommendations
        render()
        fixture.compose.onNodeWithText("Library").assertIsSelected()
        assertTrue(backVisible)
        // A narrow window drops the category list; Recommendations keeps its Back arrow and its page.
        fixture.compose.runOnIdle { paneWidth.value = 640 }
        settle()
        fixture.compose.onNodeWithText("Library").assertDoesNotExist()
        assertTrue(backVisible)
        fixture.compose.onNodeWithText("draft").assertIsDisplayed()
        fixture.compose.runOnIdle { paneWidth.value = 1200 }
        settle()
        fixture.compose.onNodeWithText("Library").assertIsSelected()
        assertTrue(backVisible)
        fixture.compose.onNodeWithText("draft").assertIsDisplayed()
    }

    @Test fun `tablet categories and detail share even a player narrowed window and retain an editor draft through resize`() {
        render()
        fixture.compose.onNodeWithText("Appearance").assertIsSelected()
        fixture.compose.onNodeWithText("draft").performTextReplacement("draft retained")
        val master = fixture.compose.onNodeWithTag("settings_category_pane").fetchSemanticsNode().boundsInRoot
        val detail = fixture.compose.onNodeWithTag("settings_detail_pane").fetchSemanticsNode().boundsInRoot
        assertEquals(296f, master.width, 1f)
        assertEquals(16f, detail.left - master.right, 1f)
        fixture.compose.runOnIdle { paneWidth.value = 640 }
        settle()
        fixture.compose.onNodeWithText("draft retained").assertIsDisplayed()
        assertEquals(640f, fixture.compose.onNodeWithTag("settings_detail_pane").fetchSemanticsNode().boundsInRoot.width, 1f)
        fixture.compose.runOnIdle { paneWidth.value = 710 }
        settle()
        fixture.compose.onNodeWithText("draft retained").assertIsDisplayed()
        fixture.compose.onNodeWithText("Downloads").performClick()
        assertEquals(ProfileSettingsDestination.Downloads, selected.value)
    }

    @Test fun `onboarding and support stay single pane on a large tablet`() {
        render(onboarding = true)
        assertEquals(1200f, fixture.compose.onNodeWithTag("settings_detail_pane").fetchSemanticsNode().boundsInRoot.width, 1f)
        fixture.compose.onNodeWithText("Cloud Sync & Backups").assertDoesNotExist()
    }

    @Test fun `fixed split header allows actual upward touch scrolling`() {
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(dynamicColor = false) {
                AdaptiveSettingsLayout(ProfileSettingsDestination.Playback, SettingsScreenConfig({}, {}), {}) {
                    SettingsScaffold("Detail", {}) {
                        repeat(30) { Text("Row $it", modifier = Modifier.height(64.dp)) }
                    }
                }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        settle()
        val before = fixture.compose.onNodeWithText("Row 8").fetchSemanticsNode().boundsInRoot.top
        fixture.compose.onNodeWithTag("settings_detail_pane").performTouchInput { swipeUp() }
        settle()
        val after = fixture.compose.onNodeWithText("Row 8").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Touch scrolling moves settings below the fixed header", after < before - 20f)
    }

    @Test fun `dark split gap uses the same solid theme surface as both panes`() {
        render(dark = true)
        val master = fixture.compose.onNodeWithTag("settings_category_pane").fetchSemanticsNode().boundsInRoot
        val detail = fixture.compose.onNodeWithTag("settings_detail_pane").fetchSemanticsNode().boundsInRoot
        fixture.compose.runOnIdle {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            assertEquals(surface, bitmap.getPixel(((master.right + detail.left) / 2).toInt(), 300))
            bitmap.recycle()
        }
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.waitForIdle()
    }

    private fun render(onboarding: Boolean = false, dark: Boolean = false) {
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(darkTheme = dark, dynamicColor = false) {
                view = LocalView.current
                surface = MaterialTheme.colorScheme.surface.toArgb()
                CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1280f, 1280f)) {
                    Box(Modifier.requiredSize(paneWidth.value.dp, 800.dp)) {
                        AdaptiveSettingsLayout(selected.value, SettingsScreenConfig(onBack = { backs++ }, onResetAnalytics = {}, isOnboarding = onboarding), { selected.value = it }) {
                            backVisible = LocalSettingsPaneBackVisible.current
                            var draft by remember { mutableStateOf("draft") }
                            TextField(draft, { draft = it }, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.waitForIdle()
    }
}
