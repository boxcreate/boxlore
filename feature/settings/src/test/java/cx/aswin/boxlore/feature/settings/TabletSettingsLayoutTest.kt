package cx.aswin.boxlore.feature.settings

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
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
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.settings.components.SettingsScaffold
import cx.aswin.boxlore.feature.settings.pages.AppearanceActions
import cx.aswin.boxlore.feature.settings.pages.AppearanceUiState
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
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1600, 1600, false))
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
        assertEquals(ProfileSettingsDestination.Appearance, settingsCategoryOf(ProfileSettingsDestination.AppearanceShowPages))
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

    @Test fun `Show pages keeps its switch, back arrow and Appearance highlight through resizes both ways`() {
        selected.value = ProfileSettingsDestination.AppearanceShowPages
        val state = mutableStateOf(AppearanceUiState("system", false, "violet", "standard"))
        val saved = mutableListOf<Boolean>()
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1280f, 800f)) {
                    Box(Modifier.requiredSize(paneWidth.value.dp, 800.dp)) {
                        AdaptiveSettingsLayout(selected.value, SettingsScreenConfig(onBack = { backs++ }, onResetAnalytics = {}), { selected.value = it }) {
                            backVisible = LocalSettingsPaneBackVisible.current
                            val actions = AppearanceActions({}, {}, {}, {}, onSetEpisodeTitlesOnly = {
                                saved += it
                                state.value = state.value.copy(episodeTitlesOnly = it)
                            })
                            cx.aswin.boxlore.feature.settings.pages.AppearanceShowPagesPage(state.value, actions, onBack = {})
                        }
                    }
                }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.waitForIdle()
        fixture.compose.onNodeWithText("Appearance").assertIsSelected()
        fixture.compose.onNodeWithText("Episode titles only").performClick()
        assertEquals(listOf(true), saved)
        listOf(640, 1200, 640).forEach { width ->
            fixture.compose.runOnIdle { paneWidth.value = width }
            settle()
            assertTrue(backVisible)
            fixture.compose.onNode(androidx.compose.ui.test.isToggleable()).assertIsOn()
            if (width > 640) fixture.compose.onNodeWithText("Appearance").assertIsSelected() else fixture.compose.onNodeWithText("Appearance").assertDoesNotExist()
        }
        fixture.compose.onNodeWithText("Episode titles only").performClick()
        assertEquals(listOf(true, false), saved)
        fixture.compose.onNode(androidx.compose.ui.test.isToggleable()).assertIsOff()
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

    @Test fun `Settings splits only on wide landscape windows, as the rest of the app does`() {
        val window = mutableStateOf(BoxloreTestWindows.TABLET)
        renderIn(window)
        for ((size, split) in listOf(
            BoxloreTestWindows.TABLET_PORTRAIT to false,
            BoxloreTestWindows.UPRIGHT_BOOK to false,
            BoxloreTestWindows.PIXEL_FOLD to true,
            BoxloreTestWindows.FOLD_FLAT to true,
            BoxloreTestWindows.TABLET to true,
        )) {
            fixture.compose.runOnIdle { window.value = size }
            settle()
            val list = fixture.compose.onNodeWithTag("settings_category_pane").fetchSemanticsNode().boundsInRoot.width
            assertEquals("$size", split, list > 0f)
        }
    }

    @Test fun `a page and its draft stay through a fold-to-portrait-tablet resize both ways`() {
        val window = mutableStateOf(BoxloreTestWindows.PIXEL_FOLD)
        renderIn(window)
        fixture.compose.onNodeWithText("draft").performTextReplacement("kept")
        for (size in listOf(BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.PIXEL_FOLD, BoxloreTestWindows.TABLET_PORTRAIT)) {
            fixture.compose.runOnIdle { window.value = size }
            settle()
            fixture.compose.onNodeWithText("kept").assertIsDisplayed()
            assertEquals(ProfileSettingsDestination.Appearance, selected.value)
        }
    }

    @Test fun `Check for updates is a button under the categories, never a picked item, and runs once`() {
        var checks = 0
        val window = mutableStateOf(BoxloreTestWindows.TABLET)
        renderIn(window, onCheckForUpdates = { checks++ })
        val button = fixture.compose.onNodeWithTag(SETTINGS_CHECK_UPDATES_TAG)
        assertFalse(button.fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Selected))
        button.performClick()
        assertEquals(1, checks)
        val height = button.fetchSemanticsNode().boundsInRoot.height
        assertTrue("one line in a 48dp button ($height)", height < 56f)
    }

    @Test fun `the tablet list's colour chips use the hub cards' colours`() {
        val expected = mapOf(
            ProfileSettingsDestination.SyncAndBackups to SettingsCategoryTone.Primary,
            ProfileSettingsDestination.Library to SettingsCategoryTone.Secondary,
            ProfileSettingsDestination.Appearance to SettingsCategoryTone.Primary,
            ProfileSettingsDestination.Playback to SettingsCategoryTone.Tertiary,
            ProfileSettingsDestination.Downloads to SettingsCategoryTone.Neutral,
            ProfileSettingsDestination.WearOs to SettingsCategoryTone.Primary,
            ProfileSettingsDestination.Privacy to SettingsCategoryTone.Secondary,
            ProfileSettingsDestination.About to SettingsCategoryTone.Tertiary,
        )
        expected.forEach { (destination, tone) -> assertEquals("$destination", tone, settingsCategoryTone(destination)) }
    }

    @Test fun `beside the list a page starts by the list with a heavier title, and keeps its width between pages`() {
        val window = mutableStateOf(BoxloreTestWindows.DESKTOP)
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(dynamicColor = false) {
                val size = window.value
                CompositionLocalProvider(LocalBoxloreWindowLayout provides size) {
                    Box(Modifier.requiredSize(size.widthDp.dp, size.heightDp.dp)) {
                        AdaptiveSettingsLayout(selected.value, SettingsScreenConfig(onBack = {}, onResetAnalytics = {}), { selected.value = it }) {
                            SettingsScaffold("Page title", {}) { Text("Row 0", modifier = Modifier.fillMaxWidth().testTag("row")) }
                        }
                    }
                }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        settle()
        val list = fixture.compose.onNodeWithTag("settings_category_pane").fetchSemanticsNode().boundsInRoot
        val row = fixture.compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot
        assertTrue("the page starts by the list (${row.left - 16f} vs ${list.right})", row.left - 16f - list.right <= 41f)
        val pageTitle = fixture.compose.onNodeWithText("Page title").fetchSemanticsNode().boundsInRoot.height
        val listTitle = fixture.compose.onNodeWithText("Settings").fetchSemanticsNode().boundsInRoot.height
        assertTrue("page title ($pageTitle) is larger than the list title ($listTitle)", pageTitle > listTitle)
        fixture.compose.runOnIdle { selected.value = ProfileSettingsDestination.Account }
        settle()
        assertEquals(row.width, fixture.compose.onNodeWithTag("row").fetchSemanticsNode().boundsInRoot.width, 1f)
        for (size in listOf(BoxloreTestWindows.TABLET, BoxloreTestWindows.TABLET_PORTRAIT, BoxloreTestWindows.TABLET)) {
            fixture.compose.runOnIdle { window.value = size }
            settle()
            assertEquals(ProfileSettingsDestination.Account, selected.value)
        }
    }

    private fun renderIn(window: androidx.compose.runtime.MutableState<BoxloreWindowLayout>, onCheckForUpdates: (() -> Unit)? = null) {
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(dynamicColor = false) {
                val size = window.value
                CompositionLocalProvider(LocalBoxloreWindowLayout provides size) {
                    Box(Modifier.requiredSize(size.widthDp.dp, size.heightDp.dp)) {
                        AdaptiveSettingsLayout(selected.value, SettingsScreenConfig(onBack = {}, onResetAnalytics = {}, onCheckForUpdates = onCheckForUpdates), { selected.value = it }) {
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
                CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1280f, 800f)) {
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
