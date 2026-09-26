package cx.aswin.boxlore.feature.settings

internal fun String?.toSettingsDestination(): ProfileSettingsDestination = when (this?.trim()?.lowercase()) {
    "account" -> ProfileSettingsDestination.Account
    "sync", "sync_and_backups", "sync-and-backups", "backups" -> ProfileSettingsDestination.SyncAndBackups
    "library" -> ProfileSettingsDestination.Library
    "appearance" -> ProfileSettingsDestination.Appearance
    "playback" -> ProfileSettingsDestination.Playback
    "downloads" -> ProfileSettingsDestination.Downloads
    "privacy" -> ProfileSettingsDestination.Privacy
    "about" -> ProfileSettingsDestination.About
    "support", "support_us", "support-us", "donate", "tips" -> ProfileSettingsDestination.Support
    else -> ProfileSettingsDestination.Hub
}

internal sealed interface SettingsBackAction {
    data object NavigateBack : SettingsBackAction
    data class NavigateTo(val destination: ProfileSettingsDestination) : SettingsBackAction
}

internal fun resolveSettingsBackAction(
    isOnboarding: Boolean,
    previousDestination: ProfileSettingsDestination?,
    initialPage: String?,
): SettingsBackAction =
    when {
        isOnboarding -> SettingsBackAction.NavigateBack
        previousDestination != null -> SettingsBackAction.NavigateTo(previousDestination)
        initialPage != null && initialPage != "hub" -> SettingsBackAction.NavigateBack
        else -> SettingsBackAction.NavigateTo(ProfileSettingsDestination.Hub)
    }
