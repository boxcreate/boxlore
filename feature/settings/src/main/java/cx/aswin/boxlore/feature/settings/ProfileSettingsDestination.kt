package cx.aswin.boxlore.feature.settings

import androidx.annotation.StringRes
import cx.aswin.boxlore.feature.settings.pages.AppearanceSection

internal enum class ProfileSettingsDestination(
    @StringRes val title: Int,
) {
    Hub(R.string.settings_page_hub),
    Account(R.string.settings_page_account),
    SyncAndBackups(R.string.settings_page_sync),
    Library(R.string.settings_page_library),
    Recommendations(R.string.settings_page_recommendations),
    Appearance(R.string.settings_page_appearance),
    Theme(R.string.settings_page_theme),
    CustomTheme(R.string.theme_editor_title),
    AppearanceHome(R.string.settings_page_home),
    AppearanceShowPages(R.string.settings_page_show_pages),
    AppearancePlayer(R.string.settings_page_player),
    AppearanceNavigation(R.string.settings_page_navigation),
    AppearanceText(R.string.settings_page_text),
    AppearanceWidgets(R.string.settings_page_widgets),
    AppearanceLanguage(R.string.settings_app_language_title),
    Playback(R.string.settings_page_playback),
    Downloads(R.string.settings_page_downloads),
    WearOs(R.string.settings_page_wear_os),
    Privacy(R.string.settings_privacy_title),
    About(R.string.settings_page_about),
    Support(R.string.settings_page_support),
    ;

    /**
     * The page Back returns to: Appearance's pages return to Appearance, the custom theme editor to
     * Theme & colors and Recommendations to Library (unless Appearance > Home opened it).
     */
    val parent: ProfileSettingsDestination?
        get() = when (this) {
            Theme, AppearanceHome, AppearanceShowPages, AppearancePlayer, AppearanceNavigation, AppearanceText, AppearanceWidgets, AppearanceLanguage -> Appearance
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
    AppearanceSection.Language -> ProfileSettingsDestination.AppearanceLanguage
}
