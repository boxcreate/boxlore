package cx.aswin.boxlore.feature.settings.downloads

import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class AutoDownloadSettingsActionsTest {
    @Test
    fun `each visible episode limit persists exactly the selected value`() = runTest {
        val limits = mutableListOf<Int>()
        val actions = AutoDownloadSettingsActions(this, { limits += it }, { error("unexpected background write") }, {})
        autoDownloadEpisodeLimits.forEach(actions::selectEpisodeLimit)
        runCurrent()
        assertEquals(listOf(1, 2, 3, 5), limits)
    }

    @Test
    fun `unsupported limits cannot change the download quota`() = runTest {
        val actions = AutoDownloadSettingsActions(this, { error("unexpected quota write") }, {}, {})
        listOf(-1, 0, 4, 6).forEach(actions::selectEpisodeLimit)
        runCurrent()
    }

    @Test
    fun `help opens and either dismissal route closes it without changing preferences`() = runTest {
        var visible = false
        val actions = AutoDownloadSettingsActions(this, { error("unexpected quota write") }, { error("unexpected background write") }, { visible = it })
        actions.showHelp()
        assertTrue(visible)
        actions.dismissHelp()
        assertFalse(visible)
        actions.showHelp()
        assertTrue(visible)
        actions.dismissHelp()
        assertFalse(visible)
        runCurrent()
    }

    @Test
    fun `disabled checks hide controls and describe remaining discovery paths`() {
        val presentation = autoDownloadBackgroundPresentation(AutoDownloadBackgroundSettings())
        assertFalse(presentation.controlsVisible)
        assertTrue(presentation.footer.contains("notifications can still start downloads"))
        assertTrue(presentation.footer.contains("open and refresh"))
    }

    @Test
    fun `enabled checks show controls with cadence and battery costs`() {
        val presentation = autoDownloadBackgroundPresentation(AutoDownloadBackgroundSettings(enabled = true))
        assertTrue(presentation.controlsVisible)
        assertTrue(presentation.footer.contains("6 hours"))
        assertTrue(presentation.footer.contains("extra battery and data"))
        assertTrue(presentation.footer.contains("Android may delay"))
    }

    @Test
    fun `hiding and showing controls retains every network and charging preference combination`() = runTest {
        val policies = listOf(true to true, true to false, false to true, false to false)
        for ((wifi, charging) in policies) {
            var saved = AutoDownloadBackgroundSettings(enabled = true, wifiOnly = wifi, chargingOnly = charging)
            val actions = AutoDownloadSettingsActions(this, { error("unexpected quota write") }, { saved = saved.copy(enabled = it) }, {})
            actions.setBackgroundEnabled(false)
            runCurrent()
            assertFalse(autoDownloadBackgroundPresentation(saved).controlsVisible)
            assertEquals(AutoDownloadBackgroundSettings(false, wifi, charging), saved)

            actions.setBackgroundEnabled(true)
            runCurrent()
            assertTrue(autoDownloadBackgroundPresentation(saved).controlsVisible)
            assertEquals(AutoDownloadBackgroundSettings(true, wifi, charging), saved)
        }
    }
}
