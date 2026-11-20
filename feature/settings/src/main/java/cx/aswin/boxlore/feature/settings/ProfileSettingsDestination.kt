package cx.aswin.boxlore.feature.settings

import cx.aswin.boxlore.feature.settings.pages.AppearanceSection

internal enum class ProfileSettingsDestination(
    val title: String,
) {
    Hub("Settings"),
    Account("Account"),
    SyncAndBackups("Cloud Sync & Backups"),
    Library("Library"),
    Recommendations("LeaveMeAlone"),
    Appearance("Appearance"),
    Theme("Theme & colors"),
    CustomTheme("Custom theme"),
    AppearanceHome("Home"),
    AppearanceShowPages("Show pages"),
    AppearancePlayer("Player"),
    AppearanceNavigation("Navigation"),
    AppearanceText("Text"),
    AppearanceWidgets("Widgets"),
    Playback("Playback"),
    Downloads("Downloads"),
    WearOs("Wear OS"),
    Privacy("Privacy"),
    About("About boxlore"),
    Support("Support boxlore"),
    ;

    /**
     * The page Back returns to: Appearance's pages return to Appearance, the custom theme editor to
     * Theme & colors and Recommendations to Library (unless Appearance > Home opened it).
     */
    val parent: ProfileSettingsDestination?
        get() = when (this) {
            Theme, AppearanceHome, AppearanceShowPages, AppearancePlayer, AppearanceNavigation, AppearanceText, AppearanceWidgets -> Appearance
            CustomTheme -> Theme
            Recommendations -> Library
            else -> null
        }
}

/** The page each Appearance overview row opens. */
internal fun appearanceSectionDestination(section: AppearanceSection): ProfileSettingsDestination = when (section) {
    AppearanceSection.Theme -> ProfileSettingsDestination.Theme
    AppearanceSection.Home -> ProfileSettingsDestination.AppearanceHome
    AppearanceSection.ShowPages -> ProfileSettingsDestination.AppearanceShowPages
    AppearanceSection.Player -> ProfileSettingsDestination.AppearancePlayer
    AppearanceSection.Navigation -> ProfileSettingsDestination.AppearanceNavigation
    AppearanceSection.Text -> ProfileSettingsDestination.AppearanceText
    AppearanceSection.Widgets -> ProfileSettingsDestination.AppearanceWidgets
}
