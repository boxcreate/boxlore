package cx.aswin.boxlore.feature.settings

internal fun String?.toSettingsDestination(): ProfileSettingsDestination = when (this?.trim()?.lowercase()) {
    "account" -> ProfileSettingsDestination.Account
    "sync", "sync_and_backups", "sync-and-backups", "backups" -> ProfileSettingsDestination.SyncAndBackups
    "library" -> ProfileSettingsDestination.Library
    "recommendations" -> ProfileSettingsDestination.Recommendations
    "appearance" -> ProfileSettingsDestination.Appearance
    "theme" -> ProfileSettingsDestination.Theme
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

/**
 * Back from [currentDestination]: an explicit previous page first, then its parent (Appearance's
 * pages return to Appearance), unless the page was opened directly as [initialPage], which leaves
 * Settings.
 */
internal fun resolveSettingsBackAction(
    isOnboarding: Boolean,
    previousDestination: ProfileSettingsDestination?,
    initialPage: String?,
    currentDestination: ProfileSettingsDestination? = null,
): SettingsBackAction {
    val parent = currentDestination?.parent
    val openedDirectly = initialPage != null && initialPage.toSettingsDestination() == currentDestination
    return when {
        isOnboarding -> SettingsBackAction.NavigateBack
        previousDestination != null -> SettingsBackAction.NavigateTo(previousDestination)
        parent != null && !openedDirectly -> SettingsBackAction.NavigateTo(parent)
        initialPage != null && initialPage != "hub" -> SettingsBackAction.NavigateBack
        else -> SettingsBackAction.NavigateTo(ProfileSettingsDestination.Hub)
    }
}

/** Nested settings return toward their parent, matching the toolbar and system Back action. */
internal fun settingsDestinationMovesForward(from: ProfileSettingsDestination, to: ProfileSettingsDestination): Boolean = when {
    to == ProfileSettingsDestination.Hub -> false
    generateSequence(from.parent) { it.parent }.any { it == to } -> false
    from == ProfileSettingsDestination.Account && to == ProfileSettingsDestination.SyncAndBackups -> false
    from == ProfileSettingsDestination.Recommendations && to == ProfileSettingsDestination.AppearanceHome -> false
    else -> true
}
