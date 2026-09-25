package cx.aswin.boxlore.feature.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.feature.settings.dialogs.AddRssFeedDialog
import cx.aswin.boxlore.feature.settings.dialogs.ResetAnalyticsDialog
import cx.aswin.boxlore.feature.settings.dialogs.RssMatchConfirmationDialog
import cx.aswin.boxlore.feature.settings.pages.AboutSettingsPage
import cx.aswin.boxlore.feature.settings.pages.AccountSettingsPage
import cx.aswin.boxlore.feature.settings.pages.AppInfo
import cx.aswin.boxlore.feature.settings.pages.AppearanceActions
import cx.aswin.boxlore.feature.settings.pages.AppearanceSettingsPage
import cx.aswin.boxlore.feature.settings.pages.AppearanceUiState
import cx.aswin.boxlore.feature.settings.pages.DownloadsSettingsPage
import cx.aswin.boxlore.feature.settings.pages.LibraryBackupActions
import cx.aswin.boxlore.feature.settings.pages.LibraryDiscoveryPreferences
import cx.aswin.boxlore.feature.settings.pages.LibrarySettingsPage
import cx.aswin.boxlore.feature.settings.pages.PlaybackActions
import cx.aswin.boxlore.feature.settings.pages.PlaybackSettingsPage
import cx.aswin.boxlore.feature.settings.pages.PlaybackUiState
import cx.aswin.boxlore.feature.settings.pages.PrivacySettingsActions
import cx.aswin.boxlore.feature.settings.pages.PrivacySettingsPage
import cx.aswin.boxlore.feature.settings.pages.SettingsHub
import kotlinx.coroutines.flow.StateFlow

/** Where to send the user for the two Downloads settings sub-screens. */
data class DownloadsNavigation(
    val onNavigateToSmartDownloads: () -> Unit = {},
    val onNavigateToAutoDownloads: () -> Unit = {},
)

/**
 * Uri-based read/write callbacks for library export/import. [SettingsScreen] wires these to the
 * platform file pickers and hands [LibrarySettingsPage] the resulting no-arg "open picker"
 * triggers (see [trackedLibraryBackupActions]).
 */
data class LibraryBackupWriters(
    val onExportJson: (Uri) -> Unit = {},
    val onExportOpml: (Uri) -> Unit = {},
    val onImportJson: (Uri) -> Unit = {},
    val onImportOpml: (Uri) -> Unit = {},
)

/** Content-region + language prefs, surfaced from the Library settings sub-page. */
data class RegionSettings(
    val currentRegion: String = "us",
    val contentLanguages: List<String> = listOf("en"),
    val onSetRegion: (String) -> Unit = {},
    val onSetContentLanguages: (List<String>) -> Unit = {},
)

/** RSS + ranking ports needed by [SettingsViewModel] (keeps [SettingsScreen] ≤7 params). */
data class SettingsRepositories(
    val rssPodcastRepository: cx.aswin.boxlore.core.rss.RssPodcastRepository,
    val rankingFeedbackRepository: cx.aswin.boxlore.core.ranking.RankingFeedbackRepository,
    val authRepository: cx.aswin.boxlore.core.network.AuthRepository? = null,
    val syncStatusFlow: StateFlow<CloudSyncUiStatus>? = null,
    val onSyncNow: (() -> Unit)? = null,
)

/** [SettingsScreen]'s top-level identifiers/callbacks that aren't tied to a specific sub-page. */
data class SettingsScreenConfig(
    val onBack: () -> Unit,
    val onResetAnalytics: () -> Unit,
    val appInstanceId: String? = null,
    /** Optional deep-link page: "library", "appearance", etc. */
    val initialPage: String? = null,
)

/** Appearance sub-page state paired with its actions, so [SettingsScreen] can pass both as one. */
data class AppearanceSettings(
    val state: AppearanceUiState =
        AppearanceUiState(
            currentThemeConfig = "system",
            isDynamicColorEnabled = true,
            currentThemeBrand = "violet",
            currentSurfaceStyle = "standard",
            currentFontRoundness = "round",
        ),
    val actions: AppearanceActions = AppearanceActions({}, {}, {}, {}, {}),
)

/** Playback sub-page state paired with its actions, so [SettingsScreen] can pass both as one. */
data class PlaybackSettings(
    val state: PlaybackUiState =
        PlaybackUiState(
            skipBehavior = "just_skip",
            skipBeginningMs = 0L,
            skipEndingMs = 0L,
            seekBackwardMs = 10_000L,
            seekForwardMs = 30_000L,
            hideCompletedInHome = true,
            hideCompletedInSubs = true,
            hideCompletedInShowDetails = false,
            restartForgottenEpisodes = true,
            sameShowQueueOnly = false,
        ),
    val actions: PlaybackActions = PlaybackActions({}, {}, {}, {}, {}, {}, {}, {}, {}),
)

@Composable
fun SettingsScreen(
    config: SettingsScreenConfig,
    repositories: SettingsRepositories,
    regionSettings: RegionSettings = RegionSettings(),
    appearanceSettings: AppearanceSettings = AppearanceSettings(),
    playbackSettings: PlaybackSettings = PlaybackSettings(),
    libraryBackupWriters: LibraryBackupWriters = LibraryBackupWriters(),
    downloadsNavigation: DownloadsNavigation = DownloadsNavigation(),
) {
    val onResetAnalytics = config.onResetAnalytics
    val appInstanceId = config.appInstanceId
    val initialPage = config.initialPage
    val context = LocalContext.current
    val settingsViewModel: SettingsViewModel =
        viewModel(
            factory =
            SettingsViewModelAssembler.factory(
                rssSubscriptionPort = repositories.rssPodcastRepository,
                rankingResetPort = repositories.rankingFeedbackRepository,
            ),
        )
    val rssState by settingsViewModel.uiState.collectAsStateWithLifecycle()

    var destination by rememberSaveable {
        mutableStateOf(initialPage.toSettingsDestination())
    }
    var previousDestination by rememberSaveable {
        mutableStateOf<ProfileSettingsDestination?>(null)
    }
    val currentUser by (
        repositories.authRepository?.currentUser?.collectAsStateWithLifecycle()
            ?: remember { mutableStateOf(null) }
    )
    val accountStatus = resolveAccountStatus(currentUser)
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var isDeletionExpanded by rememberSaveable { mutableStateOf(false) }
    var analyticsIdVersion by remember { mutableIntStateOf(0) }

    val deletionId =
        remember(appInstanceId, analyticsIdVersion) {
            AnalyticsHelper.getDistinctId().ifBlank { appInstanceId.orEmpty() }
        }
    val appInfo = rememberAppInfo(context)

    val exportJsonLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("application/json"),
            onResult = { uri -> uri?.let(libraryBackupWriters.onExportJson) },
        )
    val exportOpmlLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.CreateDocument("text/x-opml"),
            onResult = { uri -> uri?.let(libraryBackupWriters.onExportOpml) },
        )
    val importJsonLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri -> uri?.let(libraryBackupWriters.onImportJson) },
        )
    val importOpmlLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.OpenDocument(),
            onResult = { uri -> uri?.let(libraryBackupWriters.onImportOpml) },
        )

    LaunchedEffect(Unit) {
        AnalyticsHelper.trackSettingsScreenViewed("home_top_bar")
    }

    LaunchedEffect(settingsViewModel) {
        settingsViewModel.events.collect { event ->
            when (event) {
                is SettingsEvent.ShowToast ->
                    Toast.makeText(context, event.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler(enabled = destination != ProfileSettingsDestination.Hub) {
        val prev = previousDestination
        previousDestination = null
        destination = prev ?: ProfileSettingsDestination.Hub
    }

    val returnToHub = {
        val prev = previousDestination
        previousDestination = null
        destination = prev ?: ProfileSettingsDestination.Hub
    }

    val contentBundle = SettingsPagesContentBundle(
        regionSettings = regionSettings,
        appearanceSettings = appearanceSettings,
        playbackSettings = playbackSettings,
        downloadsNavigation = downloadsNavigation,
        settingsViewModel = settingsViewModel,
        context = context,
    )

    val uiData = SettingsPagesUiData(
        accountStatus = accountStatus,
        deletionId = deletionId,
        isDeletionExpanded = isDeletionExpanded,
        appInfo = appInfo,
    )

    val actions = SettingsPagesActions(
        onNavigate = { destination = it },
        onReturnToHub = returnToHub,
        onNavigateToAccountFromLibrary = {
            previousDestination = ProfileSettingsDestination.Library
            destination = ProfileSettingsDestination.Account
        },
        onDeletionExpandedChange = { isDeletionExpanded = it },
        onShowResetDialog = { showResetDialog = true },
        backupActions = trackedLibraryBackupActions(
            exportJsonLauncher,
            exportOpmlLauncher,
            importJsonLauncher,
            importOpmlLauncher,
        ),
    )

    SettingsAnimatedPages(
        destination = destination,
        config = config,
        repositories = repositories,
        contentBundle = contentBundle,
        uiData = uiData,
        actions = actions,
    )

    SettingsDialogs(
        rssState = rssState,
        settingsViewModel = settingsViewModel,
        showResetDialog = showResetDialog,
        onDismissResetDialog = { showResetDialog = false },
        onConfirmResetDialog = {
            AnalyticsHelper.trackSettingsInteraction("analytics_reset")
            onResetAnalytics()
            analyticsIdVersion++
            showResetDialog = false
        },
    )
}

private fun resolveAccountStatus(user: cx.aswin.boxlore.core.model.BoxLoreUser?): String? =
    user?.let { it.email ?: it.displayName ?: "Connected" }

internal data class SettingsPagesContentBundle(
    val regionSettings: RegionSettings,
    val appearanceSettings: AppearanceSettings,
    val playbackSettings: PlaybackSettings,
    val downloadsNavigation: DownloadsNavigation,
    val settingsViewModel: SettingsViewModel,
    val context: Context,
)

internal data class SettingsPagesUiData(
    val accountStatus: String?,
    val deletionId: String,
    val isDeletionExpanded: Boolean,
    val appInfo: AppInfo,
)

internal data class SettingsPagesActions(
    val onNavigate: (ProfileSettingsDestination) -> Unit,
    val onReturnToHub: () -> Unit,
    val onNavigateToAccountFromLibrary: () -> Unit,
    val onDeletionExpandedChange: (Boolean) -> Unit,
    val onShowResetDialog: () -> Unit,
    val backupActions: LibraryBackupActions,
)

@Composable
private fun SettingsAnimatedPages(
    destination: ProfileSettingsDestination,
    config: SettingsScreenConfig,
    repositories: SettingsRepositories,
    contentBundle: SettingsPagesContentBundle,
    uiData: SettingsPagesUiData,
    actions: SettingsPagesActions,
) {
    AnimatedContent(
        targetState = destination,
        transitionSpec = { settingsDestinationTransitionSpec() },
        label = "settings_destination",
    ) { currentDestination ->
        when (currentDestination) {
            ProfileSettingsDestination.Hub ->
                SettingsHub(
                    onBack = config.onBack,
                    onNavigate = actions.onNavigate,
                )

            ProfileSettingsDestination.Account -> {
                val syncStatus by (
                    repositories.syncStatusFlow?.collectAsStateWithLifecycle()
                        ?: remember { mutableStateOf(CloudSyncUiStatus.Idle) }
                )
                AccountSettingsPage(
                    authRepository = repositories.authRepository,
                    onBack = actions.onReturnToHub,
                    syncStatus = syncStatus,
                    onSyncNow = repositories.onSyncNow ?: {},
                )
            }

            ProfileSettingsDestination.Library ->
                LibrarySettingsPage(
                    discoveryPreferences = LibraryDiscoveryPreferences(
                        currentRegion = contentBundle.regionSettings.currentRegion,
                        contentLanguages = contentBundle.regionSettings.contentLanguages,
                        onSetRegion = {
                            AnalyticsHelper.trackSettingsInteraction("content_region_changed", it)
                            contentBundle.regionSettings.onSetRegion(it)
                        },
                        onSetContentLanguages = {
                            AnalyticsHelper.trackSettingsInteraction(
                                "content_languages_changed",
                                it.joinToString(","),
                            )
                            contentBundle.regionSettings.onSetContentLanguages(it)
                        },
                    ),
                    onAddRssClick = { contentBundle.settingsViewModel.openAddRssDialog() },
                    backupActions = actions.backupActions,
                    onBack = actions.onReturnToHub,
                    onAccountClick = actions.onNavigateToAccountFromLibrary,
                    accountStatus = uiData.accountStatus,
                )

            ProfileSettingsDestination.Appearance ->
                AppearanceSettingsPage(
                    state = contentBundle.appearanceSettings.state,
                    actions = contentBundle.appearanceSettings.actions.trackedForAnalytics(),
                    onBack = actions.onReturnToHub,
                )

            ProfileSettingsDestination.Playback ->
                PlaybackSettingsPage(
                    state = contentBundle.playbackSettings.state,
                    actions = contentBundle.playbackSettings.actions,
                    onBack = actions.onReturnToHub,
                )

            ProfileSettingsDestination.Downloads ->
                DownloadsSettingsPage(
                    onSmartDownloadsClick = contentBundle.downloadsNavigation.onNavigateToSmartDownloads,
                    onAutoDownloadsClick = contentBundle.downloadsNavigation.onNavigateToAutoDownloads,
                    onBack = actions.onReturnToHub,
                )

            ProfileSettingsDestination.Privacy ->
                PrivacySettingsPage(
                    deletionId = uiData.deletionId,
                    isDeletionExpanded = uiData.isDeletionExpanded,
                    actions =
                    PrivacySettingsActions(
                        onDeletionExpandedChange = actions.onDeletionExpandedChange,
                        onResetIdentityClick = actions.onShowResetDialog,
                        onResetRecommendationsClick = contentBundle.settingsViewModel::resetRecommendations,
                        onCopyDeletionId = { copyDeletionId(contentBundle.context, uiData.deletionId) },
                        onEmailDeletionRequest = {
                            requestAnalyticsDeletionByEmail(contentBundle.context, uiData.deletionId)
                        },
                    ),
                    onBack = actions.onReturnToHub,
                )

            ProfileSettingsDestination.About ->
                AboutSettingsPage(
                    appInfo = uiData.appInfo,
                    onVisitPodcastIndex = { visitPodcastIndexHomepage(contentBundle.context) },
                    onOpenChangelog = { openChangelog(contentBundle.context) },
                    onBack = actions.onReturnToHub,
                )
        }
    }
}

@Composable
private fun SettingsDialogs(
    rssState: SettingsRssUiState,
    settingsViewModel: SettingsViewModel,
    showResetDialog: Boolean,
    onDismissResetDialog: () -> Unit,
    onConfirmResetDialog: () -> Unit,
) {
    if (rssState.showAddRssDialog) {
        AddRssFeedDialog(
            url = rssState.rssUrl,
            error = rssState.rssError,
            isAdding = rssState.isAddingRss,
            onUrlChange = settingsViewModel::onRssUrlChange,
            onDismiss = settingsViewModel::dismissAddRssDialog,
            onConfirm = settingsViewModel::addSubscription,
        )
    }

    rssState.pendingRssMatch?.let { subscription ->
        val podcastIndexMatch = subscription.potentialPodcastIndexMatch ?: return@let
        RssMatchConfirmationDialog(
            rssTitle = subscription.podcast.title,
            podcastIndexTitle = podcastIndexMatch.title,
            isLinking = rssState.isLinkingRssMatch,
            onUseRssSource = settingsViewModel::confirmPodcastIndexLink,
            onKeepSeparate = settingsViewModel::keepRssMatchSeparate,
        )
    }

    if (showResetDialog) {
        ResetAnalyticsDialog(
            onDismiss = onDismissResetDialog,
            onConfirm = onConfirmResetDialog,
        )
    }
}

private fun AnimatedContentTransitionScope<ProfileSettingsDestination>.settingsDestinationTransitionSpec(): ContentTransform {
    val enterFromRight = targetState != ProfileSettingsDestination.Hub
    val motionSpec =
        spring<IntOffset>(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        )
    val enter =
        slideInHorizontally(
            animationSpec = motionSpec,
            initialOffsetX = { width -> if (enterFromRight) width / 3 else -width / 3 },
        ) + fadeIn()
    val exit =
        slideOutHorizontally(
            animationSpec = motionSpec,
            targetOffsetX = { width -> if (enterFromRight) -width / 4 else width / 4 },
        ) + fadeOut()
    return (enter togetherWith exit).using(SizeTransform(clip = false))
}

/** Builds the no-arg "open picker" triggers [LibrarySettingsPage] shows, with analytics tracking. */
private fun trackedLibraryBackupActions(
    exportJsonLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    exportOpmlLauncher: androidx.activity.result.ActivityResultLauncher<String>,
    importJsonLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
    importOpmlLauncher: androidx.activity.result.ActivityResultLauncher<Array<String>>,
): LibraryBackupActions = LibraryBackupActions(
    onExportJson = {
        AnalyticsHelper.trackSettingsInteraction("library_export")
        exportJsonLauncher.launch("boxlore_backup_${System.currentTimeMillis()}.json")
    },
    onExportOpml = {
        AnalyticsHelper.trackSettingsInteraction("library_export_opml")
        exportOpmlLauncher.launch("boxlore_subscriptions_${System.currentTimeMillis()}.opml")
    },
    onImportJson = {
        AnalyticsHelper.trackSettingsInteraction("library_import_json")
        importJsonLauncher.launch(arrayOf("application/json"))
    },
    onImportOpml = {
        AnalyticsHelper.trackSettingsInteraction("library_import_opml")
        importOpmlLauncher.launch(arrayOf("*/*"))
    },
)

/** Wraps the appearance callbacks with their analytics tracking, without changing behavior. */
internal fun AppearanceActions.trackedForAnalytics(): AppearanceActions = AppearanceActions(
    onSetThemeConfig = {
        AnalyticsHelper.trackSettingsInteraction("theme_mode_changed", it)
        onSetThemeConfig(it)
    },
    onToggleDynamicColor = {
        AnalyticsHelper.trackSettingsInteraction("dynamic_color_toggled", it.toString())
        onToggleDynamicColor(it)
    },
    onSetThemeBrand = {
        AnalyticsHelper.trackSettingsInteraction("theme_brand_changed", it)
        onSetThemeBrand(it)
    },
    onSetSurfaceStyle = {
        AnalyticsHelper.trackSettingsInteraction("surface_style_changed", it)
        onSetSurfaceStyle(it)
    },
    onSetFontRoundness = {
        AnalyticsHelper.trackSettingsInteraction("font_roundness_changed", it)
        onSetFontRoundness(it)
    },
    onSetNavigationStyle = {
        AnalyticsHelper.trackSettingsInteraction("navigation_style_changed", it)
        onSetNavigationStyle(it)
    },
    onSetOpenAppTo = {
        AnalyticsHelper.trackSettingsInteraction("open_app_to_changed", it)
        onSetOpenAppTo(it)
    },
    onSetHomeShortcutsInLibrary = {
        AnalyticsHelper.trackSettingsInteraction("home_shortcuts_in_library_toggled", it.toString())
        onSetHomeShortcutsInLibrary(it)
    },
    onSetWidgetAppearance = {
        AnalyticsHelper.trackSettingsInteraction("widget_appearance_changed", it)
        onSetWidgetAppearance(it)
    },
    onSetExploreDefaultTab = {
        AnalyticsHelper.trackSettingsInteraction("explore_default_tab_changed", it)
        onSetExploreDefaultTab(it)
    },
    onSetSubscriptionsDefaultTab = {
        AnalyticsHelper.trackSettingsInteraction("subscriptions_default_tab_changed", it)
        onSetSubscriptionsDefaultTab(it)
    },
    onSetSubscriptionsTabStyle = {
        AnalyticsHelper.trackSettingsInteraction("subscriptions_tab_style_changed", it)
        onSetSubscriptionsTabStyle(it)
    },
)

@Composable
private fun rememberAppInfo(context: Context): AppInfo {
    val versionName =
        remember {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName
            }.getOrNull().orEmpty().ifBlank { "Not available" }
        }
    val versionCode =
        remember {
            runCatching {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    info.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    info.versionCode.toLong()
                }
            }.getOrDefault(0L)
        }
    return remember(versionName, versionCode) {
        AppInfo(
            versionName = versionName,
            versionCode = versionCode,
            packageName = context.packageName,
            androidRelease =
            android.os.Build.VERSION.RELEASE
                .orEmpty()
                .ifBlank { "?" },
            sdkInt = android.os.Build.VERSION.SDK_INT,
        )
    }
}

private fun copyDeletionId(
    context: Context,
    deletionId: String,
) {
    AnalyticsHelper.trackSettingsInteraction("delete_id_copied")
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("Anonymous analytics ID", deletionId))
    Toast.makeText(context, "Analytics ID copied", Toast.LENGTH_SHORT).show()
}

private fun requestAnalyticsDeletionByEmail(
    context: Context,
    deletionId: String,
) {
    AnalyticsHelper.trackSettingsInteraction("delete_email_clicked")
    val intent =
        Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@aswin.cx"))
            putExtra(Intent.EXTRA_SUBJECT, "Analytics data deletion request")
            putExtra(
                Intent.EXTRA_TEXT,
                "Please delete PostHog analytics data associated with this distinct ID: $deletionId",
            )
        }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Toast.makeText(context, "No email app is available", Toast.LENGTH_SHORT).show()
        }
}

private fun visitPodcastIndexHomepage(context: Context) {
    AnalyticsHelper.trackSettingsInteraction("podcast_index_homepage_clicked")
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://podcastindex.org")))
    }
}

private fun openChangelog(context: Context) {
    AnalyticsHelper.trackSettingsInteraction("changelog_clicked")
    runCatching {
        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://github.com/boxcreate/boxlore/blob/master/CHANGELOG.md"),
            ),
        )
    }
}

internal fun String?.toSettingsDestination(): ProfileSettingsDestination = when (this?.trim()?.lowercase()) {
    "account" -> ProfileSettingsDestination.Account
    "library" -> ProfileSettingsDestination.Library
    "appearance" -> ProfileSettingsDestination.Appearance
    "playback" -> ProfileSettingsDestination.Playback
    "downloads" -> ProfileSettingsDestination.Downloads
    "privacy" -> ProfileSettingsDestination.Privacy
    "about" -> ProfileSettingsDestination.About
    else -> ProfileSettingsDestination.Hub
}
