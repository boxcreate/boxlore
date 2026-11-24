package cx.aswin.boxlore.feature.settings.downloads

import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** Smart Downloads controls: on starts at once, off only after the question, and each change reports once. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SmartDownloadsActionsTest {
    private val calls = mutableListOf<String>()
    private val tracked = mutableListOf<String>()
    private var state = SmartDownloadsState(enabled = false, wifiOnly = true, chargingOnly = false, maxEpisodes = 10, storageBudget = 1000L, cleanupRule = "after_24h")
    private var asking = false
    private var slowWrites = false

    private val prefs = object : SmartDownloadsPrefs {
        override suspend fun setEnabled(enabled: Boolean) {
            calls += "enabled=$enabled"
        }
        override suspend fun setWifiOnly(wifiOnly: Boolean) {
            if (slowWrites) kotlinx.coroutines.delay(100)
            calls += "wifi=$wifiOnly"
        }
        override suspend fun setChargingOnly(chargingOnly: Boolean) {
            if (slowWrites) kotlinx.coroutines.delay(100)
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
            calls += "schedule($wifiOnly,$chargingOnly)"
        }
        override fun cancel() {
            calls += "cancel"
        }
        override fun purgeAll() {
            calls += "purge"
        }
    }

    private fun kotlinx.coroutines.test.TestScope.actions() = SmartDownloadsActions(
        scope = this,
        prefs = prefs,
        scheduler = scheduler,
        state = { state },
        onConfirmOffChange = { asking = it },
        track = { key, value, previous -> if (value != previous) tracked += "$key=$value" },
    )

    @Test
    fun `turning on saves, schedules once and reports once`() = runTest {
        actions().setEnabled(true)
        runCurrent()
        assertEquals(listOf("enabled=true", "schedule(true,false)"), calls)
        assertEquals(listOf("smart_downloads_toggled=true"), tracked)
        assertFalse(asking)
    }

    // Regression: turning Smart Downloads off deleted every smart download at once, with no question.
    @Test
    fun `turning off only asks, and nothing is removed, saved or reported until the answer`() = runTest {
        state = state.copy(enabled = true)
        val actions = actions()
        actions.setEnabled(false)
        runCurrent()
        assertTrue(asking)
        assertEquals(emptyList<String>(), calls)
        assertEquals(emptyList<String>(), tracked)

        actions.dismissDisable()
        runCurrent()
        assertFalse(asking)
        assertEquals(emptyList<String>(), calls)
        assertEquals(emptyList<String>(), tracked)

        actions.setEnabled(false)
        actions.confirmDisable()
        runCurrent()
        assertFalse(asking)
        assertEquals(listOf("enabled=false", "cancel", "purge"), calls)
        assertEquals(listOf("smart_downloads_toggled=false"), tracked)
    }

    @Test
    fun `network and charging limits reschedule only while Smart Downloads is on`() = runTest {
        actions().setWifiOnly(false)
        runCurrent()
        assertEquals(listOf("wifi=false"), calls)
        calls.clear()
        state = state.copy(enabled = true)
        actions().setChargingOnly(true)
        runCurrent()
        assertEquals(listOf("charging=true", "schedule(true,true)"), calls)
    }

    @Test
    fun `two limits changed before either is saved end on the schedule that matches what was saved`() = runTest {
        state = state.copy(enabled = true)
        slowWrites = true
        val actions = actions()
        actions.setWifiOnly(false)
        actions.setChargingOnly(true)
        advanceUntilIdle()
        assertEquals("schedule(false,true)", calls.last { it.startsWith("schedule") })
        assertEquals(listOf("wifi=false", "charging=true"), calls.filterNot { it.startsWith("schedule") })
    }

    @Test
    fun `picking the current amount, size or clean-up writes it again but reports nothing`() = runTest {
        val actions = actions()
        actions.setMaxEpisodes(10)
        actions.setStorageBudget(1000L)
        actions.setCleanupRule("after_24h")
        actions.setMaxEpisodes(25)
        runCurrent()
        assertEquals(listOf("max=10", "budget=1000", "rule=after_24h", "max=25"), calls)
        assertEquals(listOf("smart_downloads_max_episodes_changed=25"), tracked)
    }
}
