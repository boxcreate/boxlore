package cx.aswin.boxlore.feature.settings

internal enum class ProfileSettingsDestination(
    val title: String,
) {
    Hub("Settings"),
    Account("Account"),
    SyncAndBackups("Cloud Sync & Backups"),
    Library("Library"),
    Appearance("Appearance"),
    Theme("Theme & colors"),
    CustomTheme("Custom theme"),
    AppearanceHome("Home"),
    AppearancePlayer("Player"),
    AppearanceNavigation("Navigation"),
    AppearanceText("Text"),
    Playback("Playback"),
    Downloads("Downloads"),
    Privacy("Privacy"),
    About("About boxlore"),
    Support("Support boxlore"),
    ;

    /** The page Back returns to: Appearance's pages return to Appearance, the custom theme editor to Theme & colors. */
    val parent: ProfileSettingsDestination?
        get() = when (this) {
            Theme, AppearanceHome, AppearancePlayer, AppearanceNavigation, AppearanceText -> Appearance
            CustomTheme -> Theme
            else -> null
        }
}
