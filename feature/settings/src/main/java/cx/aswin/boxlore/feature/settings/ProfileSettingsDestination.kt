package cx.aswin.boxlore.feature.settings

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
            Theme, AppearanceHome, AppearancePlayer, AppearanceNavigation, AppearanceText, AppearanceWidgets -> Appearance
            CustomTheme -> Theme
            Recommendations -> Library
            else -> null
        }
}
