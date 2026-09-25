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

    @Test
    fun `toggle action when notifications off and app notifications allowed`() {
        val action = resolveNotificationToggleAction(
            podcastNotificationsEnabled = false,
            areAppNotificationsEnabled = true,
            hasPostNotificationPermission = true,
        )
        assertEquals(NotificationToggleAction.TOGGLE_NOTIFICATIONS, action)
    }

    @Test
    fun `toggle action when notifications off and permission not granted`() {
        val action = resolveNotificationToggleAction(
            podcastNotificationsEnabled = false,
            areAppNotificationsEnabled = false,
            hasPostNotificationPermission = false,
        )
        assertEquals(NotificationToggleAction.REQUEST_PERMISSION, action)
    }

    @Test
    fun `toggle action when notifications off and system blocked with permission granted`() {
        val action = resolveNotificationToggleAction(
            podcastNotificationsEnabled = false,
            areAppNotificationsEnabled = false,
            hasPostNotificationPermission = true,
        )
        assertEquals(NotificationToggleAction.SHOW_PERMISSION_BLOCKED_WARNING, action)
    }

    @Test
    fun `toggle action when notifications on and system notifications allowed`() {
        val action = resolveNotificationToggleAction(
            podcastNotificationsEnabled = true,
            areAppNotificationsEnabled = true,
            hasPostNotificationPermission = true,
        )
        assertEquals(NotificationToggleAction.TOGGLE_NOTIFICATIONS, action)
    }

    @Test
    fun `toggle action when notifications on and system blocked shows warning if not visible`() {
        val action = resolveNotificationToggleAction(
            podcastNotificationsEnabled = true,
            areAppNotificationsEnabled = false,
            hasPostNotificationPermission = true,
            isWarningVisible = false,
        )
        assertEquals(NotificationToggleAction.SHOW_PERMISSION_BLOCKED_WARNING, action)
    }

    @Test
    fun `toggle action when notifications on and system blocked toggles off if warning already visible`() {
        val action = resolveNotificationToggleAction(
            podcastNotificationsEnabled = true,
            areAppNotificationsEnabled = false,
            hasPostNotificationPermission = true,
            isWarningVisible = true,
        )
        assertEquals(NotificationToggleAction.TOGGLE_NOTIFICATIONS, action)
    }
}
