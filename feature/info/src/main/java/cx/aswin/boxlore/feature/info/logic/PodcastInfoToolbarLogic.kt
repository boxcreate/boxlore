package cx.aswin.boxlore.feature.info.logic

enum class ToolbarWarning {
    NONE,
    NOTIFICATIONS_REQUIRED,
    SYSTEM_PERMISSION_BLOCKED,
}

fun toolbarWarningTitle(warning: ToolbarWarning): String = when (warning) {
    ToolbarWarning.NOTIFICATIONS_REQUIRED -> "Action Required"
    ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> "Notifications Disabled"
    else -> "Notice"
}

fun toolbarWarningMessage(warning: ToolbarWarning): String = when (warning) {
    ToolbarWarning.NOTIFICATIONS_REQUIRED -> "In order for us to download the latest episode of this show when it arrives, you need to toggle notifications on as well."
    ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> "Notification permissions are disabled in system settings. Please allow notifications and try again. We promise we will never spam."
    else -> ""
}

fun toolbarWarningActionText(warning: ToolbarWarning): String = when (warning) {
    ToolbarWarning.NOTIFICATIONS_REQUIRED -> "Enable Both"
    ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> "Turn On"
    else -> ""
}

enum class NotificationToggleAction {
    REQUEST_PERMISSION,
    SHOW_PERMISSION_BLOCKED_WARNING,
    TOGGLE_NOTIFICATIONS,
}

fun resolveNotificationToggleAction(
    podcastNotificationsEnabled: Boolean,
    areAppNotificationsEnabled: Boolean,
    hasPostNotificationPermission: Boolean,
    isWarningVisible: Boolean = false,
): NotificationToggleAction = when {
    !podcastNotificationsEnabled && !areAppNotificationsEnabled && !hasPostNotificationPermission ->
        NotificationToggleAction.REQUEST_PERMISSION
    !podcastNotificationsEnabled && !areAppNotificationsEnabled ->
        NotificationToggleAction.SHOW_PERMISSION_BLOCKED_WARNING
    !podcastNotificationsEnabled ->
        NotificationToggleAction.TOGGLE_NOTIFICATIONS
    !areAppNotificationsEnabled && !isWarningVisible ->
        NotificationToggleAction.SHOW_PERMISSION_BLOCKED_WARNING
    else ->
        NotificationToggleAction.TOGGLE_NOTIFICATIONS
}

enum class SystemBlockedResolutionAction {
    PROMPT_SYSTEM_PERMISSION,
    OPEN_SETTINGS,
}

fun canPromptNotificationPermission(
    sdkInt: Int,
    isPostNotificationsGranted: Boolean,
    hasPromptedBefore: Boolean,
    shouldShowRationale: Boolean,
): Boolean {
    if (sdkInt < 33) return false
    if (isPostNotificationsGranted) return false
    return if (hasPromptedBefore) {
        shouldShowRationale
    } else {
        true
    }
}

fun resolveSystemBlockedAction(
    canPrompt: Boolean,
): SystemBlockedResolutionAction = if (canPrompt) {
    SystemBlockedResolutionAction.PROMPT_SYSTEM_PERMISSION
} else {
    SystemBlockedResolutionAction.OPEN_SETTINGS
}
