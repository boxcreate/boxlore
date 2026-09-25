package cx.aswin.boxlore.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import cx.aswin.boxlore.feature.settings.AppearanceSettings
import cx.aswin.boxlore.feature.settings.DownloadsNavigation
import cx.aswin.boxlore.feature.settings.LibraryBackupWriters
import cx.aswin.boxlore.feature.settings.PlaybackSettings
import cx.aswin.boxlore.feature.settings.RegionSettings
import cx.aswin.boxlore.feature.settings.SettingsRepositories
import cx.aswin.boxlore.feature.settings.SettingsScreen
import cx.aswin.boxlore.feature.settings.SettingsScreenConfig
import cx.aswin.boxlore.feature.settings.downloads.AutoDownloadSettingsScreen
import cx.aswin.boxlore.feature.settings.downloads.SmartDownloadsSettingsScreen
import cx.aswin.boxlore.feature.settings.pages.AppearanceActions
import cx.aswin.boxlore.feature.settings.pages.AppearanceUiState
import cx.aswin.boxlore.feature.settings.pages.PlaybackActions
import cx.aswin.boxlore.feature.settings.pages.PlaybackUiState
import cx.aswin.boxlore.ui.libraryimport.OpmlImportState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

internal fun androidx.navigation.NavGraphBuilder.addSettingsDestination(w: NavGraphWiring) {
    addMainSettingsRoute(w)
    addDownloadSettingsRoutes(w)
}

private fun androidx.navigation.NavGraphBuilder.addMainSettingsRoute(w: NavGraphWiring) {
    val navController = w.navController
    val container = w.container
    val scope = w.scope
    val settingsState = w.settingsState
    val appInstanceId = w.session.appInstanceId

    composable(
        route = "settings?page={page}",
        arguments = listOf(
            navArgument("page") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            },
        ),
    ) { backStackEntry ->
        val settingsPage = backStackEntry.arguments?.getString("page")

        SettingsScreen(
            repositories = SettingsRepositories(
                rssPodcastRepository = container.rssPodcastRepository,
                rankingFeedbackRepository = container.rankingFeedbackRepository,
                authRepository = container.authRepository,
                syncStatusFlow = container.cloudSyncTriggerCoordinator.syncStatusFlow,
                onSyncNow = {
                    scope.launch {
                        container.cloudSyncTriggerCoordinator.triggerManualSync()
                    }
                },
            ),
            config = SettingsScreenConfig(
                onBack = { navController.popBackStack() },
                onResetAnalytics = {
                    try {
                        cx.aswin.boxlore.core.analytics.AnalyticsHelper.resetIdentity()
                    } catch (e: Exception) {
                        android.util.Log.e("Settings", "Failed to reset analytics", e)
                    }
                },
                appInstanceId = appInstanceId,
                initialPage = settingsPage,
            ),
            regionSettings = RegionSettings(
                currentRegion = settingsState.currentRegion,
                contentLanguages = settingsState.contentLanguages,
                onSetRegion = { region -> scope.launch { w.userPrefs.setRegion(region) } },
                onSetContentLanguages = { languages ->
                    scope.launch { w.userPrefs.setContentLanguages(languages) }
                },
            ),
            appearanceSettings = buildAppearanceSettings(w),
            playbackSettings = buildPlaybackSettings(w),
            libraryBackupWriters = buildLibraryBackupWriters(w),
            downloadsNavigation = DownloadsNavigation(
                onNavigateToSmartDownloads = { navController.navigate(NavRoutes.LIBRARY_DOWNLOADS_SETTINGS) },
                onNavigateToAutoDownloads = { navController.navigate("library/auto_downloads/settings") },
            ),
        )
    }
}

private fun buildAppearanceSettings(w: NavGraphWiring): AppearanceSettings {
    val scope = w.scope
    val userPrefs = w.userPrefs
    val settingsState = w.settingsState

    return AppearanceSettings(
        state = AppearanceUiState(
            currentThemeConfig = settingsState.themeConfig,
            isDynamicColorEnabled = settingsState.useDynamicColor,
            currentThemeBrand = settingsState.themeBrand,
            currentSurfaceStyle = settingsState.surfaceStyle,
            currentFontRoundness = settingsState.fontRoundness,
            currentNavigationStyle = settingsState.navigationStyle,
            currentOpenAppTo = settingsState.openAppTo,
            homeShortcutsInLibrary = settingsState.homeShortcutsInLibrary,
            currentWidgetAppearance = settingsState.widgetAppearance,
            currentExploreDefaultTab = settingsState.exploreDefaultTab,
            currentSubscriptionsDefaultTab = settingsState.subscriptionsDefaultTab,
            currentSubscriptionsTabStyle = settingsState.subscriptionsTabStyle,
        ),
        actions = AppearanceActions(
            onSetThemeConfig = { config -> scope.launch { userPrefs.setThemeConfig(config) } },
            onToggleDynamicColor = { enabled -> scope.launch { userPrefs.setUseDynamicColor(enabled) } },
            onSetThemeBrand = { brand -> scope.launch { userPrefs.setThemeBrand(brand) } },
            onSetSurfaceStyle = { style -> scope.launch { userPrefs.setSurfaceStyle(style) } },
            onSetFontRoundness = { roundness -> scope.launch { userPrefs.setFontRoundness(roundness) } },
            onSetNavigationStyle = { style -> scope.launch { userPrefs.setNavigationStyle(style) } },
            onSetOpenAppTo = { openAppTo -> scope.launch { userPrefs.setOpenAppTo(openAppTo) } },
            onSetHomeShortcutsInLibrary = { enabled ->
                scope.launch { userPrefs.setHomeShortcutsInLibrary(enabled) }
            },
            onSetWidgetAppearance = { appearance ->
                scope.launch { userPrefs.setWidgetAppearance(appearance) }
            },
            onSetExploreDefaultTab = { tab ->
                scope.launch { userPrefs.setExploreDefaultTab(tab) }
            },
            onSetSubscriptionsDefaultTab = { tab ->
                scope.launch { userPrefs.setSubscriptionsDefaultTab(tab) }
            },
            onSetSubscriptionsTabStyle = { style ->
                scope.launch { userPrefs.setSubscriptionsTabStyle(style) }
            },
        ),
    )
}

private fun buildPlaybackSettings(w: NavGraphWiring): PlaybackSettings {
    val scope = w.scope
    val userPrefs = w.userPrefs
    val settingsState = w.settingsState

    fun trackAndPersistPlaybackDuration(eventName: String, value: Long, persist: suspend (Long) -> Unit) {
        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .trackSettingsInteraction(eventName, value.toString())
        scope.launch { persist(value) }
    }

    return PlaybackSettings(
        state = PlaybackUiState(
            skipBehavior = settingsState.skipBehavior,
            skipBeginningMs = settingsState.skipBeginningMs,
            skipEndingMs = settingsState.skipEndingMs,
            seekBackwardMs = settingsState.seekBackwardMs,
            seekForwardMs = settingsState.seekForwardMs,
            hideCompletedInHome = settingsState.hideCompletedInHome,
            hideCompletedInSubs = settingsState.hideCompletedInSubs,
            hideCompletedInShowDetails = settingsState.hideCompletedInShowDetails,
            restartForgottenEpisodes = settingsState.restartForgottenEpisodes,
            sameShowQueueOnly = settingsState.sameShowQueueOnly,
        ),
        actions = PlaybackActions(
            onSetSkipBehavior = { behavior -> scope.launch { userPrefs.setSkipBehavior(behavior) } },
            onSetSkipBeginningMs = { value ->
                trackAndPersistPlaybackDuration("skip_beginning_changed", value, userPrefs::setSkipBeginningMs)
            },
            onSetSkipEndingMs = { value ->
                trackAndPersistPlaybackDuration("skip_ending_changed", value, userPrefs::setSkipEndingMs)
            },
            onSetSeekBackwardMs = { value ->
                trackAndPersistPlaybackDuration("seek_backward_changed", value, userPrefs::setSeekBackwardMs)
            },
            onSetSeekForwardMs = { value ->
                trackAndPersistPlaybackDuration("seek_forward_changed", value, userPrefs::setSeekForwardMs)
            },
            onSetHideCompletedInHome = { hide -> scope.launch { userPrefs.setHideCompletedInHome(hide) } },
            onSetHideCompletedInSubs = { hide -> scope.launch { userPrefs.setHideCompletedInSubs(hide) } },
            onSetHideCompletedInShowDetails = { hide -> scope.launch { userPrefs.setHideCompletedInShowDetails(hide) } },
            onSetRestartForgottenEpisodes = { enabled ->
                scope.launch { userPrefs.setRestartForgottenEpisodes(enabled) }
            },
            onSetSameShowQueueOnly = { enabled ->
                scope.launch { userPrefs.setSameShowQueueOnly(enabled) }
            },
        ),
    )
}

private fun buildLibraryBackupWriters(w: NavGraphWiring): LibraryBackupWriters {
    val scope = w.scope
    val application = w.application
    val podcastRepository = w.podcastRepository
    val playbackRepository = w.playbackRepository
    val subscriptionRepository = w.subscriptionRepository
    val userPrefs = w.userPrefs
    val opmlCallbacks = w.opmlCallbacks

    return LibraryBackupWriters(
        onExportJson = { uri ->
            scope.launch(Dispatchers.IO) {
                runLibraryExport(
                    application = application,
                    uri = uri,
                    format = "json",
                    successToast = "Library Exported Successfully",
                    failureToastPrefix = "Failed to export",
                ) {
                    cx.aswin.boxlore.core.catalog.backup.LibraryBackupManager(
                        subscriptionRepository,
                        playbackRepository,
                        podcastRepository,
                        userPrefs,
                        application,
                    ).exportLibraryAsJson()
                        .toByteArray()
                }
            }
        },
        onExportOpml = { uri ->
            scope.launch(Dispatchers.IO) {
                runLibraryExport(
                    application = application,
                    uri = uri,
                    format = "opml",
                    successToast = "Subscriptions Exported as OPML",
                    failureToastPrefix = "Failed to export OPML",
                ) {
                    cx.aswin.boxlore.core.catalog.backup.LibraryBackupManager(
                        subscriptionRepository,
                        playbackRepository,
                        podcastRepository,
                        context = application,
                    ).exportLibraryAsOpml()
                        .toByteArray()
                }
            }
        },
        onImportJson = { uri -> opmlCallbacks.performJsonImport(uri) },
        onImportOpml = { uri ->
            opmlCallbacks.onImportStateChange(OpmlImportState.Parsing(uri))
            opmlCallbacks.onTriggerKeyChange(System.currentTimeMillis())
        },
    )
}

private fun androidx.navigation.NavGraphBuilder.addDownloadSettingsRoutes(w: NavGraphWiring) {
    val userPrefs = w.userPrefs
    val navController = w.navController

    composable(NavRoutes.LIBRARY_DOWNLOADS_SETTINGS) {
        SmartDownloadsSettingsScreen(
            userPrefs = userPrefs,
            onBack = { navController.popBackStack() },
        )
    }
    composable("smart_downloads_settings") {
        SmartDownloadsSettingsScreen(
            userPrefs = userPrefs,
            onBack = { navController.popBackStack() },
        )
    }
    composable("library/auto_downloads/settings") {
        AutoDownloadSettingsScreen(
            userPrefs = userPrefs,
            onBack = { navController.popBackStack() },
        )
    }
    composable("auto_download_settings") {
        AutoDownloadSettingsScreen(
            userPrefs = userPrefs,
            onBack = { navController.popBackStack() },
        )
    }
}

private suspend fun runLibraryExport(
    application: android.app.Application,
    uri: android.net.Uri,
    format: String,
    successToast: String,
    failureToastPrefix: String,
    exportBytes: suspend () -> ByteArray,
) {
    try {
        val bytes = exportBytes()
        (
            application.contentResolver.openOutputStream(uri)
                ?: error("Unable to open export destination")
            ).use { it.write(bytes) }
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackBackupRestoreResult(
            action = "export",
            success = true,
            format = format,
        )
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            android.widget.Toast
                .makeText(application, successToast, android.widget.Toast.LENGTH_SHORT)
                .show()
        }
    } catch (e: Exception) {
        if (e is CancellationException) throw e
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackBackupRestoreResult(
            action = "export",
            success = false,
            format = format,
            errorMessage =
            cx.aswin.boxlore.ui.libraryimport.LibraryBackupAnalyticsErrors
                .fromThrowable(
                    e,
                    cx.aswin.boxlore.ui.libraryimport.LibraryBackupAnalyticsErrors.EXPORT_FAILED,
                ),
        )
        kotlinx.coroutines.withContext(Dispatchers.Main) {
            android.widget.Toast
                .makeText(
                    application,
                    "$failureToastPrefix: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT,
                ).show()
        }
    }
}
