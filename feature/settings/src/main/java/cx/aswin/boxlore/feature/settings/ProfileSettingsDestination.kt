package cx.aswin.boxlore.feature.settings

internal enum class ProfileSettingsDestination(
    val title: String,
) {
    Hub("Settings"),
    Account("Account"),
    SyncAndBackups("Cloud Sync & Backups"),
    Library("Library"),
    Appearance("Appearance"),
    Theme("Theme"),
    CustomTheme("Custom theme"),
    Playback("Playback"),
    Downloads("Downloads"),
    Privacy("Privacy"),
    About("About boxlore"),
    Support("Support boxlore"),
}
