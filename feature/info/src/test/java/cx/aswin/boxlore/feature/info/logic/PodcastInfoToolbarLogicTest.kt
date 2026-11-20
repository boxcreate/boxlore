package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.feature.info.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PodcastInfoToolbarLogicTest {
    @Test
    fun `auto download notice explains foreground discovery and optional show notifications`() {
        assertEquals(R.string.podcast_info_warning_auto_download_title, toolbarWarningTitle(ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY))
        assertEquals(R.string.podcast_info_warning_auto_download_action, toolbarWarningActionText(ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY))
        assertEquals(R.string.podcast_info_warning_auto_download_message, toolbarWarningMessage(ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY))
    }

    @Test
    fun `toolbar warning copy for system permission blocked`() {
        assertEquals(R.string.podcast_info_warning_blocked_title, toolbarWarningTitle(ToolbarWarning.SYSTEM_PERMISSION_BLOCKED))
        assertEquals(R.string.podcast_info_warning_blocked_action, toolbarWarningActionText(ToolbarWarning.SYSTEM_PERMISSION_BLOCKED))
        assertEquals(R.string.podcast_info_warning_blocked_message, toolbarWarningMessage(ToolbarWarning.SYSTEM_PERMISSION_BLOCKED))
    }

    @Test
    fun `toolbar warning copy for none uses defaults`() {
        assertEquals(R.string.podcast_info_warning_notice_title, toolbarWarningTitle(ToolbarWarning.NONE))
        assertNull(toolbarWarningMessage(ToolbarWarning.NONE))
        assertNull(toolbarWarningActionText(ToolbarWarning.NONE))
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

    @Test
    fun `canPromptNotificationPermission on API less than 33 returns false`() {
        val canPrompt = canPromptNotificationPermission(
            sdkInt = 32,
            isPostNotificationsGranted = false,
            hasPromptedBefore = false,
            shouldShowRationale = false,
        )
        assertEquals(false, canPrompt)
    }

    @Test
    fun `canPromptNotificationPermission when permission already granted returns false`() {
        val canPrompt = canPromptNotificationPermission(
            sdkInt = 34,
            isPostNotificationsGranted = true,
            hasPromptedBefore = true,
            shouldShowRationale = false,
        )
        assertEquals(false, canPrompt)
    }

    @Test
    fun `canPromptNotificationPermission on API 33 when not prompted before returns true`() {
        val canPrompt = canPromptNotificationPermission(
            sdkInt = 33,
            isPostNotificationsGranted = false,
            hasPromptedBefore = false,
            shouldShowRationale = false,
        )
        assertEquals(true, canPrompt)
    }

    @Test
    fun `canPromptNotificationPermission on API 33 when prompted before and rationale true returns true`() {
        val canPrompt = canPromptNotificationPermission(
            sdkInt = 33,
            isPostNotificationsGranted = false,
            hasPromptedBefore = true,
            shouldShowRationale = true,
        )
        assertEquals(true, canPrompt)
    }

    @Test
    fun `canPromptNotificationPermission on API 33 when prompted before and rationale false returns false`() {
        val canPrompt = canPromptNotificationPermission(
            sdkInt = 33,
            isPostNotificationsGranted = false,
            hasPromptedBefore = true,
            shouldShowRationale = false,
        )
        assertEquals(false, canPrompt)
    }

    @Test
    fun `resolveSystemBlockedAction returns prompt when can prompt`() {
        assertEquals(
            SystemBlockedResolutionAction.PROMPT_SYSTEM_PERMISSION,
            resolveSystemBlockedAction(canPrompt = true),
        )
    }

    @Test
    fun `resolveSystemBlockedAction returns open settings when cannot prompt`() {
        assertEquals(
            SystemBlockedResolutionAction.OPEN_SETTINGS,
            resolveSystemBlockedAction(canPrompt = false),
        )
    }
}
