package cx.aswin.boxlore.feature.info.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PodcastInfoToolbarLogicTest {
    @Test
    fun `toolbar warning copy for notifications required`() {
        assertEquals("Action Required", toolbarWarningTitle(ToolbarWarning.NOTIFICATIONS_REQUIRED))
        assertEquals("Enable Both", toolbarWarningActionText(ToolbarWarning.NOTIFICATIONS_REQUIRED))
        assertTrue(toolbarWarningMessage(ToolbarWarning.NOTIFICATIONS_REQUIRED).contains("download"))
    }

    @Test
    fun `toolbar warning copy for system permission blocked`() {
        assertEquals("Notifications Disabled", toolbarWarningTitle(ToolbarWarning.SYSTEM_PERMISSION_BLOCKED))
        assertEquals("Go to Settings", toolbarWarningActionText(ToolbarWarning.SYSTEM_PERMISSION_BLOCKED))
        assertTrue(toolbarWarningMessage(ToolbarWarning.SYSTEM_PERMISSION_BLOCKED).contains("system settings"))
    }

    @Test
    fun `toolbar warning copy for none uses defaults`() {
        assertEquals("Notice", toolbarWarningTitle(ToolbarWarning.NONE))
        assertEquals("", toolbarWarningMessage(ToolbarWarning.NONE))
        assertEquals("", toolbarWarningActionText(ToolbarWarning.NONE))
    }
}
