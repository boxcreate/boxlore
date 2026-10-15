package cx.aswin.boxlore.feature.settings

import android.app.Application
import androidx.compose.runtime.Composable
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import cx.aswin.boxlore.core.prefs.BoxcastPrefs
import cx.aswin.boxlore.core.prefs.DiscoverySections
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.testing.PhoneGoldenConfiguration
import cx.aswin.boxlore.core.testing.PhoneGoldenRule
import cx.aswin.boxlore.feature.settings.downloads.AutoDownloadSettingsScreen
import cx.aswin.boxlore.feature.settings.downloads.SmartDownloadsSettingsScreen
import cx.aswin.boxlore.feature.settings.feedback.FeedbackScreen
import cx.aswin.boxlore.feature.settings.pages.AboutSettingsPage
import cx.aswin.boxlore.feature.settings.pages.AccountSettingsPage
import cx.aswin.boxlore.feature.settings.pages.AppInfo
import cx.aswin.boxlore.feature.settings.pages.AppearanceHomePage
import cx.aswin.boxlore.feature.settings.pages.AppearanceNavigationPage
import cx.aswin.boxlore.feature.settings.pages.AppearancePlayerPage
import cx.aswin.boxlore.feature.settings.pages.AppearanceSettingsPage
import cx.aswin.boxlore.feature.settings.pages.AppearanceTextPage
import cx.aswin.boxlore.feature.settings.pages.CustomThemeEditor
import cx.aswin.boxlore.feature.settings.pages.DownloadsSettingsPage
import cx.aswin.boxlore.feature.settings.pages.LibraryBackupActions
import cx.aswin.boxlore.feature.settings.pages.LibraryDiscoveryPreferences
import cx.aswin.boxlore.feature.settings.pages.LibrarySettingsPage
import cx.aswin.boxlore.feature.settings.pages.PlaybackSettingsPage
import cx.aswin.boxlore.feature.settings.pages.PrivacySettingsActions
import cx.aswin.boxlore.feature.settings.pages.PrivacySettingsPage
import cx.aswin.boxlore.feature.settings.pages.RecommendationsSettingsPage
import cx.aswin.boxlore.feature.settings.pages.SupportDevelopmentPage
import cx.aswin.boxlore.feature.settings.pages.SyncAndBackupsPage
import kotlinx.coroutines.flow.flowOf
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Local offline page review; never writes phone baselines or submits feedback. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TabletSettingsCaptureTest(private val scene: String, configuration: PhoneGoldenConfiguration) {
    @get:Rule val fixture = PhoneGoldenRule(configuration)

    @Test fun capture() {
        assumeTrue(System.getProperty("boxlore.tabletCaptures") == "after")
        val prefs = capturePreferences(scene.endsWith("enabled"))
        fixture.prepare()
        fixture.setContent(foldSafeViewport = true) {
            BoxLoreTheme(darkTheme = fixture.configuration.dark, dynamicColor = false) {
                when {
                    scene.startsWith("automatic") -> AutoDownloadSettingsScreen(prefs, {})
                    scene.startsWith("smart") -> SmartDownloadsSettingsScreen(prefs, {})
                    scene == "feedback" -> FeedbackScreen(mock(PodcastRepository::class.java), mock(BoxcastPrefs::class.java), {})
                    else -> {
                        val destination = captureDestination(scene)
                        AdaptiveSettingsLayout(destination, SettingsScreenConfig({}, {}), {}) { current -> CaptureSettingsPage(current) }
                    }
                }
            }
        }
        fixture.capture("settings-$scene")
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun configurations(): List<Array<Any>> = listOf("sync", "library", "appearance", "appearance-home", "custom-theme", "playback", "downloads", "privacy", "about", "account", "support", "automatic", "automatic-enabled", "smart", "smart-enabled", "feedback").flatMap { scene ->
            listOf(1280 to 800, 840 to 1280).flatMap { (width, height) ->
                listOf(false, true).map { dark -> arrayOf<Any>(scene, PhoneGoldenConfiguration(width, height, dark)) }
            }
        }
    }
}

private fun captureDestination(scene: String): ProfileSettingsDestination = when (scene) {
    "sync" -> ProfileSettingsDestination.SyncAndBackups
    "library" -> ProfileSettingsDestination.Library
    "appearance" -> ProfileSettingsDestination.Appearance
    "appearance-home" -> ProfileSettingsDestination.AppearanceHome
    "custom-theme" -> ProfileSettingsDestination.CustomTheme
    "playback" -> ProfileSettingsDestination.Playback
    "downloads" -> ProfileSettingsDestination.Downloads
    "privacy" -> ProfileSettingsDestination.Privacy
    "about" -> ProfileSettingsDestination.About
    "account" -> ProfileSettingsDestination.Account
    "support" -> ProfileSettingsDestination.Support
    else -> error(scene)
}

@Composable
private fun CaptureSettingsPage(destination: ProfileSettingsDestination) {
    val appearance = AppearanceSettings()
    val playback = PlaybackSettings()
    when (destination) {
        ProfileSettingsDestination.SyncAndBackups -> SyncAndBackupsPage(null, CloudSyncUiStatus.Idle, LibraryBackupActions({}, {}, {}, {}), {}, {})
        ProfileSettingsDestination.Library -> LibrarySettingsPage(LibraryDiscoveryPreferences("us", listOf("en"), {}, {}), {}, {})
        ProfileSettingsDestination.Recommendations -> RecommendationsSettingsPage(DiscoverySections(), { _, _ -> }, {})
        ProfileSettingsDestination.Appearance, ProfileSettingsDestination.Theme -> AppearanceSettingsPage(appearance.state, appearance.actions, {}, {})
        ProfileSettingsDestination.AppearanceHome,
        ProfileSettingsDestination.AppearancePlayer,
        ProfileSettingsDestination.AppearanceNavigation,
        ProfileSettingsDestination.AppearanceText,
        -> CaptureAppearanceSubpage(destination, appearance)
        ProfileSettingsDestination.CustomTheme -> CustomThemeEditor(appearance.state, {}, {})
        ProfileSettingsDestination.Playback -> PlaybackSettingsPage(playback.state, playback.actions, {})
        ProfileSettingsDestination.Downloads -> DownloadsSettingsPage({}, {}, {})
        ProfileSettingsDestination.Privacy -> PrivacySettingsPage("test-deletion-id", false, PrivacySettingsActions({}, {}, {}, {}, {}), {})
        ProfileSettingsDestination.About -> AboutSettingsPage(AppInfo("1.0", 1L, "cx.aswin.boxlore", "13", 33), {}, {}, onBack = {})
        ProfileSettingsDestination.Account -> AccountSettingsPage(authRepository = null, onBack = {})
        ProfileSettingsDestination.Support -> SupportDevelopmentPage({}, 42L)
        ProfileSettingsDestination.Hub -> error("Capture requires a category")
    }
}

@Composable
private fun CaptureAppearanceSubpage(destination: ProfileSettingsDestination, appearance: AppearanceSettings) {
    when (destination) {
        ProfileSettingsDestination.AppearanceHome -> AppearanceHomePage(appearance.state, appearance.actions, null, {})
        ProfileSettingsDestination.AppearancePlayer -> AppearancePlayerPage(appearance.state, appearance.actions, {})
        ProfileSettingsDestination.AppearanceNavigation -> AppearanceNavigationPage(appearance.state, appearance.actions, {})
        else -> AppearanceTextPage(appearance.state, appearance.actions, {})
    }
}

private fun capturePreferences(enabled: Boolean): UserPreferencesRepository = mock(UserPreferencesRepository::class.java).also { prefs ->
    `when`(prefs.autoDownloadWifiOnlyStream).thenReturn(flowOf(true))
    `when`(prefs.autoDownloadMaxEpisodesStream).thenReturn(flowOf(2))
    `when`(prefs.autoDownloadDeleteCompletedStream).thenReturn(flowOf(true))
    `when`(prefs.autoDownloadBackgroundSettingsStream).thenReturn(flowOf(AutoDownloadBackgroundSettings(enabled = enabled)))
    `when`(prefs.smartDownloadsEnabledStream).thenReturn(flowOf(enabled))
    `when`(prefs.smartDownloadsMaxEpisodesStream).thenReturn(flowOf(10))
    `when`(prefs.smartDownloadsStorageBudgetStream).thenReturn(flowOf(1000L))
    `when`(prefs.smartDownloadsWifiOnlyStream).thenReturn(flowOf(true))
    `when`(prefs.smartDownloadsChargingOnlyStream).thenReturn(flowOf(false))
    `when`(prefs.smartDownloadsCleanupRuleStream).thenReturn(flowOf("after_24h"))
}
