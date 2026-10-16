package cx.aswin.boxlore.feature.settings.pages.supportworlds

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.settings.pages.SettingsHub
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class SupportEntryImpressionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val reported = mutableListOf<String>()

    @Test fun `one view reports once, even across recompositions`() {
        var tick by mutableIntStateOf(0)
        compose.setContent {
            tick.let { SupportEntryImpression(SupportEntrySurface.SettingsTopBar) { reported += it } }
        }
        tick++
        compose.waitForIdle()
        assertEquals(listOf("settings_top_bar"), reported)
    }

    @Test fun `a configuration restore does not report a second impression`() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { SupportEntryImpression(SupportEntrySurface.SettingsCategoryList) { reported += it } }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        assertEquals(listOf("settings_category_list"), reported)
    }

    @Test fun `leaving and returning to the hub counts as a new impression`() {
        var shown by mutableStateOf(true)
        compose.setContent {
            if (shown) SupportEntryImpression(SupportEntrySurface.SettingsTopBar) { reported += it }
        }
        shown = false
        compose.waitForIdle()
        shown = true
        compose.waitForIdle()
        assertEquals(listOf("settings_top_bar", "settings_top_bar"), reported)
    }

    @Test fun `the settings hub reports its top-bar Support button`() {
        val captured = mutableListOf<Pair<String, Map<String, Any>>>()
        val restore = AnalyticsHelper.installRecordingSink(captured)
        try {
            compose.setContent { BoxLoreTheme(dynamicColor = false) { SettingsHub(onBack = {}, onNavigate = {}) } }
            compose.waitForIdle()
        } finally {
            restore()
        }
        val impressions = captured.filter { it.first == "support_entry_viewed" }
        assertEquals(1, impressions.size)
        assertEquals("settings_top_bar", impressions.single().second["surface"])
    }
}
