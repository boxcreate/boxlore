package cx.aswin.boxlore.core.downloads

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SmartDownloadScheduleLogicTest {
    @Test
    fun `restored work turns the toggle back on`() {
        assertEquals(
            SmartDownloadScheduleLogic.ReconcileAction.ENABLE_AND_SCHEDULE,
            SmartDownloadScheduleLogic.reconcile(
                enabledInPrefs = false,
                hasActiveScheduledWork = true,
            ),
        )
    }

    @Test
    fun `enabled prefs reschedule even when work is missing`() {
        assertEquals(
            SmartDownloadScheduleLogic.ReconcileAction.SCHEDULE,
            SmartDownloadScheduleLogic.reconcile(
                enabledInPrefs = true,
                hasActiveScheduledWork = false,
            ),
        )
        assertEquals(
            SmartDownloadScheduleLogic.ReconcileAction.SCHEDULE,
            SmartDownloadScheduleLogic.reconcile(
                enabledInPrefs = true,
                hasActiveScheduledWork = true,
            ),
        )
    }

    @Test
    fun `disabled prefs cancel leftover work`() {
        assertEquals(
            SmartDownloadScheduleLogic.ReconcileAction.CANCEL,
            SmartDownloadScheduleLogic.reconcile(
                enabledInPrefs = false,
                hasActiveScheduledWork = false,
            ),
        )
    }

    @Test
    fun `automatic sync waits a full day after success`() {
        val lastSync = 10_000L

        assertEquals(
            false,
            SmartDownloadScheduleLogic.shouldRunSync(
                isManual = false,
                lastSuccessfulSyncMs = lastSync,
                nowMs = lastSync + SmartDownloadScheduleLogic.AUTOMATIC_SYNC_INTERVAL_MS - 1L,
            ),
        )
        assertEquals(
            true,
            SmartDownloadScheduleLogic.shouldRunSync(
                isManual = false,
                lastSuccessfulSyncMs = lastSync,
                nowMs = lastSync + SmartDownloadScheduleLogic.AUTOMATIC_SYNC_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun `first and manual syncs bypass automatic cadence`() {
        assertEquals(
            true,
            SmartDownloadScheduleLogic.shouldRunSync(
                isManual = false,
                lastSuccessfulSyncMs = 0L,
                nowMs = 1L,
            ),
        )
        assertEquals(
            true,
            SmartDownloadScheduleLogic.shouldRunSync(
                isManual = true,
                lastSuccessfulSyncMs = 10_000L,
                nowMs = 10_001L,
            ),
        )
    }
}
