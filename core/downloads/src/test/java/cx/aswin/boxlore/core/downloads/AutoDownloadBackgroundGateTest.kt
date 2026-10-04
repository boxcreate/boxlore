package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoDownloadBackgroundGateTest {
    private val healthy = BackgroundCheckDeviceState(true, true, true, true)
    private val enabled = AutoDownloadBackgroundSettings(enabled = true)

    @Test fun allRequiredConditionsFailClosed() {
        assertFalse(AutoDownloadBackgroundGate.permits(AutoDownloadBackgroundSettings(), healthy))
        assertFalse(AutoDownloadBackgroundGate.permits(enabled, BackgroundCheckDeviceState()))
        assertFalse(AutoDownloadBackgroundGate.permits(enabled, healthy.copy(connected = false)))
        assertFalse(AutoDownloadBackgroundGate.permits(enabled, healthy.copy(unmetered = false)))
        assertFalse(AutoDownloadBackgroundGate.permits(enabled, healthy.copy(batteryNotLow = false)))
        assertFalse(AutoDownloadBackgroundGate.permits(enabled.copy(chargingOnly = true), healthy.copy(charging = false)))
        assertTrue(AutoDownloadBackgroundGate.permits(enabled, healthy.copy(charging = false)))
        assertTrue(AutoDownloadBackgroundGate.permits(enabled.copy(wifiOnly = false), healthy.copy(unmetered = false)))
    }

    @Test fun disabledAndUnknownStateNeverInvokeWork() = runTest {
        for (policy in listOf(enabled, AutoDownloadBackgroundSettings())) {
            val gate = AutoDownloadBackgroundGate(MutableStateFlow(policy), { BackgroundCheckDeviceState() }, MutableStateFlow(healthy))
            assertNull(gate.runGuarded { error("must not execute") })
        }
    }

    @Test fun consentOrRestrictionChangesCancelInFlightWork() = runTest {
        for (change in listOf(enabled.copy(enabled = false), enabled.copy(chargingOnly = true), enabled.copy(wifiOnly = false))) {
            val settings = MutableStateFlow(enabled)
            val device = MutableStateFlow(healthy)
            val gate = AutoDownloadBackgroundGate(settings, { device.value }, device)
            var cancelled = false
            val run = async {
                gate.runGuarded {
                    try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
                }
            }
            runCurrent()
            settings.value = change
            runCurrent()
            assertNull(run.await())
            assertTrue(cancelled)
        }
    }

    @Test fun LossOfEachDeviceConditionCancelsInFlightWork() = runTest {
        for (lost in listOf(healthy.copy(connected = false), healthy.copy(unmetered = false), healthy.copy(batteryNotLow = false), healthy.copy(charging = false))) {
            val settings = MutableStateFlow(enabled.copy(chargingOnly = true))
            val device = MutableStateFlow(healthy)
            val gate = AutoDownloadBackgroundGate(settings, { device.value }, device)
            var cancelled = false
            val run = async {
                gate.runGuarded {
                    try {
                awaitCancellation()
            } finally {
                cancelled = true
            }
                }
            }
            runCurrent()
            device.value = lost
            runCurrent()
            assertNull(run.await())
            assertTrue(cancelled)
        }
    }
}
