package cx.aswin.boxlore.feature.settings

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.settings.pages.WearOsCopy
import cx.aswin.boxlore.feature.settings.pages.WearOsSettings
import cx.aswin.boxlore.feature.settings.pages.WearOsSettingsPage
import cx.aswin.boxlore.feature.settings.pages.WearOsUiState
import java.util.Locale
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
class WearOsSettingsTest {
    @get:Rule val fixture = PhoneGoldenRule(PhoneGoldenConfiguration(1280, 1280, false))
    private val paneWidth = mutableStateOf(1200)
    private val selected = mutableStateOf(ProfileSettingsDestination.WearOs)
    private val state = mutableStateOf(WearOsUiState(watchReachable = true, watchStoredBytes = 243L * 1024 * 1024, watchDownloads = 6, storageLimitMb = 250))
    private val picked = ArrayList<String>()

    @Test fun `limits read as the watch shows them`() {
        assertEquals(listOf("250 MB", "500 MB", "1 GB", "2 GB"), WearOsUiState.STORAGE_LIMITS_MB.map(WearOsCopy::limit))
    }

    @Test fun `usage says what the watch holds against the limit`() {
        assertEquals("On your watch: 6 episodes, 243 MB of 250 MB.", WearOsCopy.usage(state.value, Locale.US))
        assertEquals("On your watch: 1 episode, 30 MB of 1 GB.", WearOsCopy.usage(WearOsUiState(watchStoredBytes = 30L * 1024 * 1024, watchDownloads = 1, storageLimitMb = 1000), Locale.US))
        assertEquals("No downloads on your watch yet.", WearOsCopy.usage(WearOsUiState(), Locale.US))
    }

    @Test fun `the connection line says when a change will arrive`() {
        assertTrue(WearOsCopy.connection(reachable = true).contains("right away"))
        assertTrue(WearOsCopy.connection(reachable = false).contains("next time it connects"))
    }

    @Test fun `links and the split know the Wear OS page`() {
        for (key in listOf("wear", "wear_os", "wear-os", "watch", " Wear_OS ")) assertEquals(ProfileSettingsDestination.WearOs, key.toSettingsDestination())
        assertEquals(ProfileSettingsDestination.WearOs, settingsCategoryOf(ProfileSettingsDestination.WearOs))
        assertEquals(null, ProfileSettingsDestination.WearOs.parent)
    }

    @Test fun `picking a layout or a limit reports the choice`() {
        render(linked = true)

        fixture.compose.onNodeWithText("List").performClick()
        fixture.compose.onNodeWithText("1 GB").performClick()

        assertEquals(listOf("layout List", "limit 1000"), picked)
    }

    // The category appears only for a phone linked with a watch, and the page survives the split opening and closing.
    @Test fun `the Wear OS category and its page survive the window crossing the split width both ways`() {
        render(linked = true)
        fixture.compose.onNodeWithTag("settings_category_pane").assertIsDisplayed()
        fixture.compose.onAllNodesWithText("Wear OS")[0].assertIsSelected()
        fixture.compose.onNodeWithTag("wear_os_usage").assertIsDisplayed()

        fixture.compose.runOnIdle { paneWidth.value = 640 }
        settle()
        assertEquals(640f, fixture.compose.onNodeWithTag("settings_detail_pane").fetchSemanticsNode().boundsInRoot.width, 1f)
        fixture.compose.onNodeWithTag("wear_os_usage").assertIsDisplayed()
        fixture.compose.runOnIdle { state.value = state.value.copy(watchReachable = false) }
        settle()
        fixture.compose.onNodeWithText(WearOsCopy.connection(reachable = false)).assertIsDisplayed()

        fixture.compose.runOnIdle { paneWidth.value = 1200 }
        settle()
        fixture.compose.onAllNodesWithText("Wear OS")[0].assertIsSelected()
        fixture.compose.onNodeWithText(WearOsCopy.connection(reachable = false)).assertIsDisplayed()
        assertEquals(ProfileSettingsDestination.WearOs, selected.value)
    }

    @Test fun `a phone with no linked watch lists no Wear OS category`() {
        selected.value = ProfileSettingsDestination.Downloads
        render(linked = false)

        fixture.compose.onNodeWithTag("settings_category_pane").assertIsDisplayed()
        fixture.compose.onNodeWithText("Wear OS").assertDoesNotExist()
    }

    private fun settle() {
        Snapshot.sendApplyNotifications()
        fixture.compose.mainClock.advanceTimeByFrame()
        fixture.compose.waitForIdle()
    }

    private fun render(linked: Boolean) {
        fixture.prepare()
        fixture.setContent {
            BoxLoreTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(LocalBoxloreWindowLayout provides BoxloreWindowLayout(1280f, 1280f)) {
                    Box(Modifier.requiredSize(paneWidth.value.dp, 800.dp)) {
                        val wearOs =
                            WearOsSettings(
                                state = state.value,
                                onSetShowsLayout = { picked += "layout $it" },
                                onSetStorageLimitMb = { picked += "limit $it" },
                            )
                        val config = SettingsScreenConfig(onBack = {}, onResetAnalytics = {}, wearOs = wearOs.takeIf { linked })
                        AdaptiveSettingsLayout(selected.value, config, { selected.value = it }) { shown ->
                            if (shown == ProfileSettingsDestination.WearOs) WearOsSettingsPage(wearOs, onBack = {})
                        }
                    }
                }
            }
        }
        fixture.compose.mainClock.autoAdvance = true
        fixture.compose.waitForIdle()
    }
}
