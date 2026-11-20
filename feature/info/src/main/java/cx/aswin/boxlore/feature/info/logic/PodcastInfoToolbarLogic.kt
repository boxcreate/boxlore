package cx.aswin.boxlore.feature.info.logic

import androidx.annotation.StringRes
import cx.aswin.boxlore.feature.info.R

enum class ToolbarWarning {
    NONE,
    AUTO_DOWNLOAD_APP_OPEN_ONLY,
    SYSTEM_PERMISSION_BLOCKED,
}

fun resolveAutoDownloadToggleWarning(
    currentWarning: ToolbarWarning,
    autoDownloadEnabled: Boolean,
    notificationsEnabled: Boolean,
    backgroundChecksEnabled: Boolean,
): ToolbarWarning = when {
    currentWarning == ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> currentWarning
    !autoDownloadEnabled && !notificationsEnabled && !backgroundChecksEnabled -> ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY
    else -> ToolbarWarning.NONE
}

@StringRes
fun toolbarWarningTitle(warning: ToolbarWarning): Int = when (warning) {
    ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY -> R.string.podcast_info_warning_auto_download_title
    ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> R.string.podcast_info_warning_blocked_title
    else -> R.string.podcast_info_warning_notice_title
}

/** The banner's message, or null when the warning has none. */
@StringRes
fun toolbarWarningMessage(warning: ToolbarWarning): Int? = when (warning) {
    ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY -> R.string.podcast_info_warning_auto_download_message
    ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> R.string.podcast_info_warning_blocked_message
    else -> null
}

/** The banner's button, or null when the warning has none. */
@StringRes
fun toolbarWarningActionText(warning: ToolbarWarning): Int? = when (warning) {
    ToolbarWarning.AUTO_DOWNLOAD_APP_OPEN_ONLY -> R.string.podcast_info_warning_auto_download_action
    ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> R.string.podcast_info_warning_blocked_action
    else -> null
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
