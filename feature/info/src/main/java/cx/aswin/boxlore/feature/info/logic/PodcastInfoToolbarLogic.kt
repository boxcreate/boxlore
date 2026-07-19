package cx.aswin.boxlore.feature.info.logic

enum class ToolbarWarning {
    NONE,
    NOTIFICATIONS_REQUIRED,
    SYSTEM_PERMISSION_BLOCKED,
}

fun toolbarWarningTitle(warning: ToolbarWarning): String =
    when (warning) {
        ToolbarWarning.NOTIFICATIONS_REQUIRED -> "Action Required"
        ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> "Notifications Disabled"
        else -> "Notice"
    }

fun toolbarWarningMessage(warning: ToolbarWarning): String =
    when (warning) {
        ToolbarWarning.NOTIFICATIONS_REQUIRED -> "In order for us to download the latest episode of this show when it arrives, you need to toggle notifications on as well."
        ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> "Notification permissions are disabled in system settings. Please allow notifications and try again. We promise we will never spam."
        else -> ""
    }

fun toolbarWarningActionText(warning: ToolbarWarning): String =
    when (warning) {
        ToolbarWarning.NOTIFICATIONS_REQUIRED -> "Enable Both"
        ToolbarWarning.SYSTEM_PERMISSION_BLOCKED -> "Go to Settings"
        else -> ""
    }
