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
}
