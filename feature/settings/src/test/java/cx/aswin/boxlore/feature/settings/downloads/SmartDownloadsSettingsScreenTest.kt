package cx.aswin.boxlore.feature.settings.downloads

import android.app.Application
import android.os.PowerManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.designsystem.layout.BoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneWidth
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.BoxloreTestWindows
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.text
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The Smart Downloads page: its switches, choices and the turn-off question across window changes and recreation. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class, qualifiers = "w1000dp-h1000dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SmartDownloadsSettingsScreenTest {
    @get:Rule val compose = createComposeRule()

    private val window = mutableStateOf(BoxloreTestWindows.PHONE)
    private val enabled = MutableStateFlow(true)
    private val calls = mutableListOf<String>()

    private val writes = object : SmartDownloadsPrefs {
        override suspend fun setEnabled(enabled: Boolean) {
            calls += "enabled=$enabled"
            this@SmartDownloadsSettingsScreenTest.enabled.value = enabled
        }
        override suspend fun setWifiOnly(wifiOnly: Boolean) {
            calls += "wifi=$wifiOnly"
        }
        override suspend fun setChargingOnly(chargingOnly: Boolean) {
            calls += "charging=$chargingOnly"
        }
        override suspend fun setMaxEpisodes(count: Int) {
            calls += "max=$count"
        }
        override suspend fun setStorageBudget(megabytes: Long) {
            calls += "budget=$megabytes"
        }
        override suspend fun setCleanupRule(rule: String) {
            calls += "rule=$rule"
        }
    }

    private val scheduler = object : SmartDownloadScheduler {
        override fun schedule(wifiOnly: Boolean, chargingOnly: Boolean) {
            calls += "schedule"
        }
        override fun cancel() {
            calls += "cancel"
        }
        override fun purgeAll() {
            calls += "purge"
        }
    }

    private fun render(): StateRestorationTester {
        val prefs = mock(UserPreferencesRepository::class.java)
        `when`(prefs.smartDownloadsEnabledStream).thenReturn(enabled)
        `when`(prefs.smartDownloadsMaxEpisodesStream).thenReturn(MutableStateFlow(10))
        `when`(prefs.smartDownloadsStorageBudgetStream).thenReturn(MutableStateFlow(1000L))
        `when`(prefs.smartDownloadsWifiOnlyStream).thenReturn(MutableStateFlow(true))
        `when`(prefs.smartDownloadsChargingOnlyStream).thenReturn(MutableStateFlow(false))
        `when`(prefs.smartDownloadsCleanupRuleStream).thenReturn(MutableStateFlow("after_24h"))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val size = window.value
            CompositionLocalProvider(LocalBoxloreWindowLayout provides size, LocalBoxlorePaneWidth provides size.widthDp) {
                Box(Modifier.width(size.widthDp.dp).height(size.heightDp.dp)) {
                    BoxLoreTheme(dynamicColor = false) { SmartDownloadsSettingsContent(prefs, scheduler, onBack = {}, writes = writes) }
                }
            }
        }
        compose.waitForIdle()
        return restoration
    }

    // Regression: tapping the Enable row's title turned Smart Downloads off and removed its episodes at once.
    @Test fun `the turn-off question survives rotation and recreation, and only its confirm removes anything`() {
        val restoration = render()
        compose.onNodeWithText(text(R.string.settings_smart_downloads_enable)).performClick()
        compose.waitForIdle()
        val question = text(R.string.settings_smart_downloads_off_title)
        compose.onNodeWithText(question).assertExists()
        for (size in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE, BoxloreTestWindows.PHONE_LAND)) {
            compose.runOnIdle { window.value = size }
            compose.waitForIdle()
            compose.onNodeWithText(question).assertExists()
        }
        restoration.emulateSavedInstanceStateRestore()
        compose.waitForIdle()
        compose.onNodeWithText(question).assertExists()
        assertEquals(emptyList<String>(), calls)
        compose.onNodeWithText(text(R.string.settings_smart_downloads_off_confirm)).performClick()
        compose.waitForIdle()
        assertEquals(listOf("enabled=false", "cancel", "purge"), calls)
        assertEquals(0, compose.onAllNodes(isDialog()).fetchSemanticsNodes().size)
    }

    @Test fun `cancel keeps Smart Downloads on and changes nothing`() {
        render()
        compose.onNodeWithText(text(R.string.settings_smart_downloads_enable)).performClick()
        compose.onNodeWithText(text(R.string.settings_cancel)).performClick()
        compose.waitForIdle()
        assertEquals(emptyList<String>(), calls)
        assertTrue(enabled.value)
    }

    @Test fun `limits are one switch each, the size choices are radio buttons, and a new count is saved`() {
        render()
        compose.onNodeWithText(text(R.string.settings_smart_downloads_charging_only)).performScrollTo().performClick()
        compose.onNodeWithText("25").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(listOf("charging=true", "schedule", "max=25"), calls)
        val radios = compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton)).fetchSemanticsNodes()
        // Four counts, six sizes and four clean-up choices.
        assertEquals(14, radios.size)
    }

    @Test fun `the help opens from the header and stays open through rotation`() {
        render()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text(R.string.settings_smart_downloads_about)))).performClick()
        compose.waitForIdle()
        for (size in listOf(BoxloreTestWindows.PHONE_LAND, BoxloreTestWindows.PHONE)) {
            compose.runOnIdle { window.value = size }
            compose.waitForIdle()
            assertEquals(1, compose.onAllNodes(isDialog()).fetchSemanticsNodes().size)
        }
        // The page says it in one line; the help holds the full explanation once, and the background
        // note matches this phone (not yet allowed here).
        assertEquals(1, compose.onAllNodesWithText(text(R.string.settings_smart_downloads_description)).fetchSemanticsNodes().size)
        compose.onNodeWithText(text(R.string.settings_downloads_smart_description)).assertExists()
        compose.onNode(hasText(text(R.string.settings_smart_downloads_background_blocked)) and hasAnyAncestor(isDialog())).assertExists()
    }

    @Test fun `once background downloads are allowed the help says so instead of warning`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app.getSystemService(PowerManager::class.java)).setIgnoringBatteryOptimizations(app.packageName, true)
        render()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf(text(R.string.settings_smart_downloads_about)))).performClick()
        compose.waitForIdle()
        compose.onNode(hasText(text(R.string.settings_smart_downloads_background_allowed)) and hasAnyAncestor(isDialog())).assertExists()
        compose.onNode(hasText(text(R.string.settings_smart_downloads_background_blocked)) and hasAnyAncestor(isDialog())).assertDoesNotExist()
    }

    @Test fun `on a sideways phone the cards sit in the 720dp reading column`() {
        window.value = BoxloreWindowLayout(915f, 412f)
        render()
        val row = compose.onNodeWithText(text(R.string.settings_smart_downloads_enable)).fetchSemanticsNode().boundsInRoot
        assertTrue("the first card starts inside the centred column (${row.left})", row.left > (915f - 720f) / 2f)
    }
}
