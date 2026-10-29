package cx.aswin.boxlore.ui

import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.posthog.PostHog
import cx.aswin.boxlore.BoxLoreApplication
import cx.aswin.boxlore.BuildConfig
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.designsystem.component.AppLoreNavigationActionSize
import cx.aswin.boxlore.core.designsystem.component.AppNavigationBarHorizontalInset
import cx.aswin.boxlore.core.designsystem.component.BoxLoreNavigationBar
import cx.aswin.boxlore.core.designsystem.component.LocalAdaptivePlayerCompactProgress
import cx.aswin.boxlore.core.designsystem.component.LocalNavigationStyle
import cx.aswin.boxlore.core.designsystem.component.NavigationStyle
import cx.aswin.boxlore.core.designsystem.component.PredictiveBackWrapper
import cx.aswin.boxlore.core.designsystem.component.appBottomChromeContentPadding
import cx.aswin.boxlore.core.designsystem.component.appMiniPlayerTopOffset
import cx.aswin.boxlore.core.designsystem.component.navigationChromeMetrics
import cx.aswin.boxlore.core.designsystem.component.navigationStyleUsesExternalSystemNavigationInset
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.core.designsystem.theme.FontRoundness
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.skipBackward
import cx.aswin.boxlore.core.playback.skipForward
import cx.aswin.boxlore.core.playback.stopAndClearQueue
import cx.aswin.boxlore.core.playback.togglePlayPause
import cx.aswin.boxlore.core.prefs.PrefsFileMigrator
import cx.aswin.boxlore.fcm.FcmTopicHelper
import cx.aswin.boxlore.fcm.isUpdaterAnnouncementAction
import cx.aswin.boxlore.fcm.shouldSuppressAnnouncement
import cx.aswin.boxlore.feature.home.ModeSwitchState
import cx.aswin.boxlore.feature.onboarding.generateRecommendationsFromOpml
import cx.aswin.boxlore.feature.onboarding.markOnboardingCompletedSilent
import cx.aswin.boxlore.feature.player.v2.PlayerSheetLayout
import cx.aswin.boxlore.feature.player.v2.PlayerSheetScaffold
import cx.aswin.boxlore.lifecycle.DownloadBandwidthEffect
import cx.aswin.boxlore.navigation.BoxLoreNavHost
import cx.aswin.boxlore.navigation.LaunchSubscriptionsBackAction
import cx.aswin.boxlore.navigation.NavHostActions
import cx.aswin.boxlore.navigation.NavHostSession
import cx.aswin.boxlore.navigation.NavOpmlCallbacks
import cx.aswin.boxlore.navigation.NavSettingsState
import cx.aswin.boxlore.navigation.dispatchPushAction
import cx.aswin.boxlore.navigation.externalPushActionIntent
import cx.aswin.boxlore.navigation.isLaunchLandingBackRoute
import cx.aswin.boxlore.navigation.navigateBottomNavTab
import cx.aswin.boxlore.navigation.navigateHomeFromLaunchSubscriptions
import cx.aswin.boxlore.navigation.resolveBottomNavTab
import cx.aswin.boxlore.navigation.resolveIsFromOnboarding
import cx.aswin.boxlore.navigation.resolveLaunchSubscriptionsBack
import cx.aswin.boxlore.navigation.shouldShowBottomNav
import cx.aswin.boxlore.navigation.shouldShowPlayerChrome
import cx.aswin.boxlore.navigation.snapshotNavBackStack
import cx.aswin.boxlore.ui.announcement.FeatureAnnouncementOverlay
import cx.aswin.boxlore.ui.announcement.InAppAnnouncementDialog
import cx.aswin.boxlore.ui.libraryimport.OpmlImportDialog
import cx.aswin.boxlore.ui.libraryimport.OpmlImportDialogActions
import cx.aswin.boxlore.ui.libraryimport.OpmlImportEffects
import cx.aswin.boxlore.ui.libraryimport.OpmlImportState
import cx.aswin.boxlore.ui.libraryimport.performJsonLibraryImport
import cx.aswin.boxlore.ui.logic.SubscriptionResumeRefreshLogic
import cx.aswin.boxlore.ui.logic.canUseAdaptivePlayer
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Application UI root hosted by [cx.aswin.boxlore.MainActivity]: theme, nav host,
 * player overlay, bottom nav, and activity-scoped dialogs.
 *
 * Repositories come from [BoxLoreApplication.container] — never constructed here.
 */
@Composable
fun BoxLoreAppRoot(
    activity: ComponentActivity,
    application: BoxLoreApplication,
    expandPlayerTrigger: Long,
    intentState: MutableState<android.content.Intent?>,
    warmStartIntent: MutableState<android.content.Intent?>,
    onPlaybackRepositoryReady: (PlaybackRepository) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val tabletNavigation = cx.aswin.boxlore.navigation.rememberTabletDetailNavigation(navController)

    val currentWarmIntent = warmStartIntent.value
    LaunchedEffect(currentWarmIntent) {
        val intent = currentWarmIntent ?: return@LaunchedEffect
        val rawTarget = intent.data?.toString() ?: intent.getStringExtra("target_route")
        runCatching {
            dispatchPushAction(
                target = rawTarget,
                handleDeepLink = { deepLinkIntent ->
                    val uri = deepLinkIntent.data
                    AnalyticsHelper.trackDeepLinkOpened(
                        linkScheme = uri?.scheme ?: "unknown",
                        isFirstOpen = false,
                        linkHost = uri?.host,
                        coldStart = false,
                    )
                    tabletNavigation.handleDeepLink(if (intent.data != null) intent else deepLinkIntent)
                },
                navigate = { tabletNavigation.navigate(navController, it) },
                openExternal = { activity.startActivity(externalPushActionIntent(it)) },
                openUpdater = { download ->
                    if (download) {
                        application.container.appUpdates.openAndDownload()
                    } else {
                        application.container.appUpdates.open(checkNow = true)
                    }
                },
            )
        }.onFailure { Log.w("BoxLoreAppRoot", "Unable to open notification target", it) }
        intent.removeExtra("target_route")
        warmStartIntent.value = null
    }

    // A sign-in link opens Account & sync, which shows the sign-in and the first sync.
    val accountPageRequest by application.container.emailLinkSignIn.accountPageRequests.collectAsState()
    var handledAccountPageRequest by rememberSaveable { mutableLongStateOf(0L) }
    LaunchedEffect(accountPageRequest) {
        val entry = navController.currentBackStackEntry
        val action = cx.aswin.boxlore.navigation.accountPageRequestAction(
            request = accountPageRequest,
            handled = handledAccountPageRequest,
            route = entry?.destination?.route,
            page = entry?.arguments?.getString("page"),
        )
        if (action == cx.aswin.boxlore.navigation.AccountPageRequestAction.Ignore) return@LaunchedEffect
        handledAccountPageRequest = accountPageRequest
        if (action == cx.aswin.boxlore.navigation.AccountPageRequestAction.Open) {
            tabletNavigation.navigate(navController, cx.aswin.boxlore.navigation.ACCOUNT_PAGE_ROUTE)
        }
    }

    val navBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = navBackStackEntry?.destination?.route ?: "home"
    val startupWorkGate = application.startupWorkGate
    LaunchedEffect(currentRoute) {
        if (currentRoute != "home") {
            withFrameNanos {}
            withFrameNanos {}
            startupWorkGate.markReady()
        }
    }

    val container = application.container
    val podcastRepository = container.podcastRepository
    val playbackRepository = container.playbackRepository
    val downloadRepository = container.downloadRepository
    val subscriptionRepository = container.subscriptionRepository
    val consentManager = container.consentManager
    val userPrefs = container.userPreferencesRepository
    val queueManager = container.queueManager
    val smartDownloadManager = container.smartDownloadManager
    val installReferrerManager = container.installReferrerManager

    val onSubmitFeedback: suspend (String, String, String, String) -> Boolean =
        remember(podcastRepository) {
            { category, message, version, email ->
                podcastRepository.submitFeedback(category, message, version, email.ifBlank { null })
            }
        }

    LaunchedEffect(Unit) {
        val prefs =
            PrefsFileMigrator.open(
                activity,
                newName = PrefsFileMigrator.Files.API_CONFIG,
                oldName = PrefsFileMigrator.LegacyFiles.API_CONFIG,
            )
        prefs
            .edit()
            .putString("base_url", BuildConfig.BOXLORE_API_BASE_URL)
            .putString("public_key", BuildConfig.BOXLORE_PUBLIC_KEY)
            .apply()
    }

    val openedToLandingOnLaunch = remember { mutableStateOf(false) }
    val canGoBack =
        navController.previousBackStackEntry != null ||
            (
                openedToLandingOnLaunch.value &&
                    isLaunchLandingBackRoute(currentRoute)
                )

    SideEffect { onPlaybackRepositoryReady(playbackRepository) }

    LaunchedEffect(Unit) {
        FcmTopicHelper.reconcileAfterRestoreIfNeeded(activity, subscriptionRepository)
    }

    val onboardingViewModel =
        remember {
            cx.aswin.boxlore.feature.onboarding.OnboardingViewModel(
                application,
                podcastRepository,
                subscriptionRepository,
                userPrefs,
            )
        }

    val currentIntent = intentState.value
    val hasDeepLink = currentIntent?.data != null
    val currentUser by container.authRepository.currentUser.collectAsStateWithLifecycle()
    var onboardingCompleted by remember {
        mutableStateOf(
            onboardingViewModel.isOnboardingCompleted() ||
                hasDeepLink ||
                (currentUser != null && currentUser?.isEmailVerified == true),
        )
    }
    val isFromOnboardingRoute =
        navBackStackEntry?.arguments?.getBoolean("fromOnboarding") == true ||
            (!onboardingCompleted && currentRoute.startsWith("settings"))
    var isSupportPageActive by remember { mutableStateOf(false) }
    var isAccountPageActive by remember { mutableStateOf(false) }
    val isSupportActive = isSupportPageActive && currentRoute.startsWith("settings")
    val isAccountActive = currentRoute.startsWith("settings") &&
        (isAccountPageActive || navBackStackEntry?.arguments?.getString("page") == "account")
    val showBottomNav =
        shouldShowBottomNav(
            onboardingCompleted = onboardingCompleted,
            currentRoute = currentRoute,
            isFromOnboarding = isFromOnboardingRoute,
        ) &&
            !isSupportActive

    LaunchedEffect(hasDeepLink) {
        if (hasDeepLink) {
            onboardingViewModel.markOnboardingCompletedSilent {
                onboardingCompleted = true
            }
        }
    }

    LaunchedEffect(currentUser) {
        val isVerified = currentUser?.isEmailVerified == true
        if (isVerified && !onboardingCompleted && currentRoute == "onboarding") {
            onboardingViewModel.markOnboardingCompletedSilent {
                onboardingCompleted = true
                navController.navigate("home") {
                    popUpTo("onboarding") { inclusive = true }
                }
            }
        }
    }

    LaunchedEffect(Unit) { installReferrerManager.checkInstallReferrer() }
    LaunchedEffect(installReferrerManager) {
        installReferrerManager.referralFlow.collect { referral ->
            Log.d("MainActivityReferrer", "Received referral: $referral")
            val path = installReferralRoute(referral)
            if (path != null) {
                onboardingViewModel.markOnboardingCompletedSilent { onboardingCompleted = true }
                tabletNavigation.navigate(navController, path) { launchSingleTop = true }
            }
        }
    }

    var loreQueueConflictEpisode by remember { mutableStateOf<Episode?>(null) }
    val queueLoreEpisode: (Episode) -> Unit = { episode ->
        val podcast =
            Podcast(
                id = episode.podcastId ?: "unknown",
                title = episode.podcastTitle ?: "Unknown Podcast",
                artist = episode.podcastTitle ?: "Unknown",
                imageUrl = episode.podcastImageUrl ?: episode.imageUrl ?: "",
            )
        queueManager.addToQueue(episode, podcast, PlaybackEntryPoint.LEARN)
    }

    val isOnline by container.connectivityObserver.isOnlineFlow.collectAsStateWithLifecycle()
    val artworkRecoveryGeneration = cx.aswin.boxlore.core.designsystem.components.rememberArtworkRecoveryGeneration(isOnline)

    LaunchedEffect(onboardingCompleted) {
        if (onboardingCompleted) {
            startupWorkGate.runWhenReady {
                withContext(Dispatchers.IO) { container.subscriptionForegroundSync.ensureStarted() }
            }
        }
    }

    LaunchedEffect(onboardingCompleted, isOnline) {
        if (onboardingCompleted) {
            startupWorkGate.runWhenReady {
                withContext(Dispatchers.IO) { container.legacyRssRepair.ensureStarted() }
            }
        }
    }

    LaunchedEffect(smartDownloadManager) {
        startupWorkGate.runWhenReady {
            try {
                val lastSyncTime = userPrefs.smartDownloadsLastSyncTimeStream.first()
                val isEnabled = userPrefs.smartDownloadsEnabledStream.first()
                if (isEnabled && System.currentTimeMillis() - lastSyncTime > 24 * 3600 * 1000L) {
                    Log.d("MainActivity", "Last smart sync > 24h ago. Triggering graceful catch-up sync.")
                    withContext(Dispatchers.IO) {
                        smartDownloadManager.performSync(isForeground = true)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to check catch-up sync status", e)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    if (onboardingCompleted) {
        DisposableEffect(lifecycleOwner) {
            var skipFirstStart = true
            val observer =
                LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_START) {
                        if (SubscriptionResumeRefreshLogic.shouldRequestRefreshOnStart(skipFirstStart)) {
                            container.subscriptionForegroundSync.requestRefresh()
                        }
                        skipFirstStart = false
                    }
                }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    @Suppress("UNUSED_VARIABLE")
    val hasUserSetConsent by consentManager.hasUserSetConsent.collectAsState(initial = true)

    @Suppress("UNUSED_VARIABLE")
    val crashlyticsConsent by consentManager.isCrashReportingConsented.collectAsState(initial = false)
    val currentRegion by userPrefs.regionStream.collectAsState(initial = "us")
    val contentLanguages by userPrefs.contentLanguagesStream.collectAsState(initial = listOf("en"))
    val contentEnglishOff by userPrefs.contentEnglishOffStream.collectAsState(initial = false)

    val themeConfig by userPrefs.themeConfigStream.collectAsState(
        initial = remember { userPrefs.cachedThemeConfig },
    )
    val skipBehavior by userPrefs.skipBehaviorStream.collectAsState(initial = "just_skip")
    val skipBeginningMs by userPrefs.skipBeginningMsStream.collectAsState(initial = 0L)
    val skipEndingMs by userPrefs.skipEndingMsStream.collectAsState(initial = 0L)
    val seekBackwardMs by userPrefs.seekBackwardMsStream.collectAsState(initial = 10_000L)
    val seekForwardMs by userPrefs.seekForwardMsStream.collectAsState(initial = 30_000L)
    val miniPlayerSeekButtonsEnabled by userPrefs.miniPlayerSeekButtonsEnabledStream.collectAsStateWithLifecycle(initialValue = false)
    val fullPlayerStyle by userPrefs.fullPlayerStyleStream.collectAsStateWithLifecycle(initialValue = userPrefs.cachedFullPlayerStyle)
    val hideCompletedInHome by userPrefs.hideCompletedInHomeStream.collectAsState(initial = true)
    val hideCompletedInSubs by userPrefs.hideCompletedInSubsStream.collectAsState(initial = true)
    val hideCompletedInShowDetails by userPrefs.hideCompletedInShowDetailsStream.collectAsState(initial = false)
    val restartForgottenEpisodes by userPrefs.restartForgottenEpisodesStream.collectAsState(initial = true)
    val sameShowQueueOnly by userPrefs.sameShowQueueOnlyStream.collectAsState(initial = false)
    val homeShortcutsInLibrary by userPrefs.homeShortcutsInLibraryStream.collectAsState(initial = false)
    val discoverySections by userPrefs.discoverySectionsStream.collectAsState(initial = cx.aswin.boxlore.core.prefs.DiscoverySections())
    val useDynamicColor by userPrefs.useDynamicColorStream.collectAsState(
        initial = remember { userPrefs.cachedUseDynamicColor },
    )
    val themeBrand by userPrefs.themeBrandStream.collectAsState(
        initial = remember { userPrefs.cachedThemeBrand },
    )
    val artworkColorsEnabled by userPrefs.artworkColorsEnabledStream.collectAsState(initial = remember { userPrefs.cachedArtworkColorsEnabled })
    val navigationBlurLevel by userPrefs.navigationBlurLevelStream.collectAsState(initial = remember { userPrefs.cachedNavigationBlurLevel })
    val heroCardStyle by userPrefs.heroCardStyleStream.collectAsState(initial = remember { userPrefs.cachedHeroCardStyle })
    val heroCoverShade by userPrefs.heroCoverShadeStream.collectAsState(initial = remember { userPrefs.cachedHeroCoverShade })
    val heroTideProgress by userPrefs.heroTideProgressStream.collectAsState(initial = remember { userPrefs.cachedHeroTideProgress })
    // Person properties follow the hero look, default included, so dashboards can count every listener's pick.
    LaunchedEffect(heroCardStyle, heroCoverShade) { AnalyticsHelper.syncHomeHeroStylePersonProperties(heroCardStyle, heroCoverShade) }
    val savedCustomTheme by userPrefs.customThemeStream.collectAsState(initial = remember { userPrefs.cachedCustomTheme })
    val surfaceStyle by userPrefs.surfaceStyleStream.collectAsState(
        initial = remember { userPrefs.cachedSurfaceStyle },
    )
    val fontRoundnessKey by userPrefs.fontRoundnessStream.collectAsStateWithLifecycle(
        initialValue = remember { userPrefs.cachedFontRoundness },
    )
    val navigationStyleKey by userPrefs.navigationStyleStream.collectAsState(
        initial = remember { userPrefs.cachedNavigationStyle },
    )
    val openAppToKey by userPrefs.openAppToStream.collectAsState(
        initial = remember { userPrefs.cachedOpenAppTo },
    )
    val widgetAppearance by userPrefs.widgetAppearanceStream.collectAsState(
        initial = remember { userPrefs.cachedWidgetAppearance },
    )
    val exploreDefaultTab by userPrefs.exploreDefaultTabStream.collectAsState(
        initial = remember { userPrefs.cachedExploreDefaultTab },
    )
    val subscriptionsDefaultTab by userPrefs.subscriptionsDefaultTabStream.collectAsState(
        initial = remember { userPrefs.cachedSubscriptionsDefaultTab },
    )
    val subscriptionsTabStyle by userPrefs.subscriptionsTabStyleStream.collectAsState(
        initial = remember { userPrefs.cachedSubscriptionsTabStyle },
    )
    val navigationStyle = remember(navigationStyleKey) { NavigationStyle.fromKey(navigationStyleKey) }
    val fontRoundness =
        remember(fontRoundnessKey) {
            FontRoundness.axisValue(fontRoundnessKey)
        }
    val hasSeenMarkPlayedTip by userPrefs.hasSeenMarkPlayedTip.collectAsState(initial = true)
    val hasLoggedFirstPlay by userPrefs.hasLoggedFirstPlay.collectAsState(initial = true)
    val activeAnnouncement by userPrefs.activeAnnouncementStream.collectAsState(initial = null)
    val dismissedFeatureVersion by userPrefs.dismissedFeatureVersion.collectAsState(initial = "")
    val legacyRssRepairInProgress by container.legacyRssRepair.inProgress.collectAsStateWithLifecycle()

    val isPlaying by remember(playbackRepository) {
        playbackRepository.playerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val showLateNightNudge by remember(playbackRepository) {
        playbackRepository.playerState.map { it.showLateNightNudge }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val currentEpisode by remember(playbackRepository) {
        playbackRepository.playerState.map { it.currentEpisode }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val isModeSwitching by ModeSwitchState.isSwitching.collectAsState()

    DownloadBandwidthEffect(isPlaying = isPlaying)

    val miniPlayerPadding =
        remember(currentEpisode, navigationStyle, isAccountActive) {
            appBottomChromeContentPadding(
                style = navigationStyle,
                isMiniPlayerVisible = currentEpisode != null && !isAccountActive,
            )
        }

    val darkTheme =
        when (themeConfig) {
            "light" -> false
            "dark" -> true
            else -> isSystemInDarkTheme()
        }

    var appInstanceId by remember { mutableStateOf<String?>(null) }
    var isInitialHomeContentReady by remember { mutableStateOf(false) }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted ->
            AnalyticsHelper.trackNotificationPermissionDecided(isGranted)
        }

    LaunchedEffect(Unit) {
        FcmTopicHelper.subscribeDefaultTopics(activity)
    }

    var opmlImportState by remember { mutableStateOf<OpmlImportState>(OpmlImportState.Idle) }
    var opmlImportSource by remember { mutableStateOf("welcome_screen") }
    var importTriggerKey by remember { mutableStateOf(0L) }

    fun performJsonImport(uri: android.net.Uri) {
        scope.launch {
            performJsonLibraryImport(
                uri = uri,
                context = activity,
                subscriptionRepository = subscriptionRepository,
                playbackRepository = playbackRepository,
                podcastRepository = podcastRepository,
                userPrefs = userPrefs,
                onStateChange = { opmlImportState = it },
            )
        }
    }

    OpmlImportEffects(
        importTriggerKey = importTriggerKey,
        opmlImportState = opmlImportState,
        onStateChange = { opmlImportState = it },
        context = activity,
        subscriptionRepository = subscriptionRepository,
        playbackRepository = playbackRepository,
        podcastRepository = podcastRepository,
    )

    LaunchedEffect(Unit) {
        if (playbackRepository.restoreLastSession()) {
            val state = playbackRepository.playerState.value
            AnalyticsHelper.trackSessionRestorePrompt(
                action = "restored",
                episodeId = state.currentEpisode?.id,
                podcastId = state.currentPodcast?.id,
                positionSeconds = state.position / 1000f,
            )
        }
    }

    LaunchedEffect(isPlaying, hasLoggedFirstPlay) {
        if (isPlaying && !hasLoggedFirstPlay) {
            AnalyticsHelper.trackFirstEpisodePlayed()
            userPrefs.markFirstPlayLogged()
        }
    }

    val featureAnnouncementId = "android_auto_1_3_6"
    var activeFeatureFlag by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(dismissedFeatureVersion) {
        if (dismissedFeatureVersion != featureAnnouncementId && dismissedFeatureVersion != "android_auto_1.3.6") {
            cx.aswin.boxlore.core.analytics.PostHogStartup.runWhenReady {
                activeFeatureFlag = PostHog.getFeatureFlag("active_feature_announcement") as? String
                PostHog.reloadFeatureFlags {
                    activeFeatureFlag = PostHog.getFeatureFlag("active_feature_announcement") as? String
                }
            }
        }
    }
    val showFeatureDialog =
        currentRoute == "home" &&
            activeFeatureFlag == featureAnnouncementId &&
            dismissedFeatureVersion != featureAnnouncementId &&
            dismissedFeatureVersion != "android_auto_1.3.6" &&
            activeAnnouncement == null

    val artworkNavigation = remember { cx.aswin.boxlore.core.designsystem.theme.ArtworkNavigationColors() }
    val windowLayout = LocalBoxloreWindowLayout.current
    BoxLoreTheme(
        darkTheme = darkTheme,
        dynamicColor = useDynamicColor,
        artworkColorsEnabled = artworkColorsEnabled,
        themeBrand = themeBrand,
        surfaceStyle = surfaceStyle,
        fontRoundness = fontRoundness,
    ) {
        cx.aswin.boxlore.updates.AppUpdatesHost(application.container.appUpdates, activity) {
        CompositionLocalProvider(LocalBoxloreWindowLayout provides windowLayout, cx.aswin.boxlore.core.designsystem.components.LocalArtworkRecoveryGeneration provides artworkRecoveryGeneration, LocalNavigationStyle provides navigationStyle, cx.aswin.boxlore.core.designsystem.theme.LocalArtworkNavigationColors provides artworkNavigation) {
            // Limited API sessions step up here before AI routes; invisible unless Turnstile asks for a tap.
            application.apiSessions?.let { sessions ->
                cx.aswin.boxlore.session.turnstile.TurnstileStepUpHost(sessions.turnstile, sessions.apiBaseUrl)
            }
            loreQueueConflictEpisode?.let { pendingLoreEpisode ->
                LoreQueueConflictDialog(
                    pendingLoreEpisode = pendingLoreEpisode,
                    onDismiss = { loreQueueConflictEpisode = null },
                    onConfirmStart = { episode ->
                        playbackRepository.stopAndClearQueue()
                        queueLoreEpisode(episode)
                    },
                )
            }

            if (onboardingCompleted && activeAnnouncement != null) {
                val announcement = activeAnnouncement!!
                val suppressWhatsNewOnPlay =
                    remember(announcement) {
                        activity.shouldSuppressAnnouncement(announcement)
                    }
                if (suppressWhatsNewOnPlay) {
                    LaunchedEffect(announcement.timestamp, announcement.category) {
                        userPrefs.clearAnnouncement()
                    }
                } else {
                    InAppAnnouncementDialog(
                        announcement = announcement,
                        onDismiss = { scope.launch { userPrefs.clearAnnouncement() } },
                        onAction = { route ->
                            if (cx.aswin.boxlore.fcm.shouldDismissAnnouncementForAction(announcement, route)) scope.launch { userPrefs.clearAnnouncement() }
                            if (isUpdaterAnnouncementAction(announcement, route)) {
                                if (route == "boxlore://updates/download") {
                                    application.container.appUpdates.openAndDownload()
                                } else {
                                    application.container.appUpdates.open(checkNow = true)
                                }
                            } else {
                                try {
                                val intent =
                                    android.content.Intent(
                                        android.content.Intent.ACTION_VIEW,
                                        android.net.Uri.parse(route),
                                    )
                                activity.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("Announcement", "Failed to open route", e)
                            }
                            }
                        },
                    )
                }
            }

            if (showFeatureDialog) {
                FeatureAnnouncementOverlay(
                    featureAnnouncementId = featureAnnouncementId,
                    userPrefs = userPrefs,
                )
            }

            val navigationContent: @Composable (androidx.navigation.NavHostController, Boolean) -> Unit = { controller, detailOnly ->
            BoxLoreNavHost(
                navController = controller,
                application = application,
                session =
                NavHostSession(
                    onboardingCompleted = onboardingCompleted,
                    onOnboardingCompleted = { onboardingCompleted = true },
                    onInitialHomeContentReady = {
                        isInitialHomeContentReady = true
                        startupWorkGate.markReady()
                    },
                    onboardingViewModel = onboardingViewModel,
                    hasDeepLink = hasDeepLink,
                    openedToLandingOnLaunch = openedToLandingOnLaunch,
                    currentEpisode = currentEpisode,
                    miniPlayerPadding = if (windowLayout.usesTabletLayout) {
                        appBottomChromeContentPadding(
                            style = navigationStyle,
                            isMiniPlayerVisible = currentEpisode != null &&
                                shouldShowPlayerChrome(isAccountActive, isFromOnboardingRoute, isSupportActive, isModeSwitching) &&
                                !cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePlayerPaneVisible.current &&
                                dockedPlayerCoversPane(
                                    cx.aswin.boxlore.core.designsystem.layout.LocalBoxlorePaneBounds.current,
                                    cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreDockedPlayerBounds.current,
                                ),
                            navigationRailVisible = true,
                        )
                    } else {
                        miniPlayerPadding
                    },
                    showFeatureDialog = showFeatureDialog,
                    hasSeenMarkPlayedTip = hasSeenMarkPlayedTip,
                    permissionLauncher = permissionLauncher,
                    appInstanceId = appInstanceId,
                ),
                opmlCallbacks =
                NavOpmlCallbacks(
                    importState = opmlImportState,
                    onImportStateChange = { opmlImportState = it },
                    triggerKey = importTriggerKey,
                    onTriggerKeyChange = { importTriggerKey = it },
                    onSourceChange = { opmlImportSource = it },
                    performJsonImport = ::performJsonImport,
                ),
                actions =
                NavHostActions(
                    onLoreQueueConflictEpisode = { loreQueueConflictEpisode = it },
                    queueLoreEpisode = queueLoreEpisode,
                    onShowFeedbackSheet = { navController.navigate("feedback") },
                    onSubmitFeedback = onSubmitFeedback,
                    onSupportPageVisibilityChanged = { isSupportPageActive = it },
                    onAccountPageVisibilityChanged = { isAccountPageActive = it },
                ),
                settingsState =
                NavSettingsState(
                    currentRegion = currentRegion,
                    contentLanguages = contentLanguages,
                    contentEnglishOff = contentEnglishOff,
                    themeConfig = themeConfig,
                    useDynamicColor = useDynamicColor,
                    themeBrand = themeBrand,
                    surfaceStyle = surfaceStyle,
                    artworkColorsEnabled = artworkColorsEnabled,
                    navigationBlurLevel = navigationBlurLevel,
                    heroCardStyle = heroCardStyle,
                    heroCoverShade = heroCoverShade,
                    heroTideProgress = heroTideProgress,
                    savedCustomTheme = savedCustomTheme,
                    fontRoundness = fontRoundnessKey,
                    navigationStyle = navigationStyleKey,
                    openAppTo = openAppToKey,
                    skipBehavior = skipBehavior,
                    skipBeginningMs = skipBeginningMs,
                    skipEndingMs = skipEndingMs,
                    seekBackwardMs = seekBackwardMs,
                    seekForwardMs = seekForwardMs,
                    miniPlayerSeekButtonsEnabled = miniPlayerSeekButtonsEnabled,
                    fullPlayerStyle = fullPlayerStyle,
                    hideCompletedInHome = hideCompletedInHome,
                    hideCompletedInSubs = hideCompletedInSubs,
                    hideCompletedInShowDetails = hideCompletedInShowDetails,
                    restartForgottenEpisodes = restartForgottenEpisodes,
                    sameShowQueueOnly = sameShowQueueOnly,
                    homeShortcutsInLibrary = homeShortcutsInLibrary,
                    discoverySections = discoverySections,
                    widgetAppearance = widgetAppearance,
                    exploreDefaultTab = exploreDefaultTab,
                    subscriptionsDefaultTab = subscriptionsDefaultTab,
                    subscriptionsTabStyle = subscriptionsTabStyle,
                ),
                detailsOnly = detailOnly,
            )
            }
            val activeTab = resolveBottomNavTab(currentRoute, remember(navBackStackEntry) { snapshotNavBackStack(navController) })
            cx.aswin.boxlore.feature.player.v2.RetainedPlayerPresentation(windowLayout.usesTabletLayout) {
            TabletAppShell(
                navigation = tabletNavigation,
                navigationVisible = showBottomNav,
                playerAllowed = shouldShowPlayerChrome(isAccountActive, isFromOnboardingRoute, isSupportActive, isModeSwitching),
                hasEpisode = currentEpisode != null,
                activeTab = activeTab,
                onNavigateTab = { navController.navigateBottomNavTab(it, activeTab) },
                content = TabletShellContent(
                    keyboardActions = PlaybackKeyboardActions(playbackRepository::togglePlayPause, playbackRepository::skipBackward, playbackRepository::skipForward),
                    details = { navigationContent(tabletNavigation.detail, true) },
                    player = { contextPane ->
                        TabletAppPlayer(
                            cx.aswin.boxlore.feature.player.v2.FullPlayerDependencies(playbackRepository, downloadRepository, userPrefs),
                            tabletNavigation,
                            contextPane,
                            playerSheetActions(navController, playbackRepository, {}, navigate = { tabletNavigation.navigate(navController, it) { launchSingleTop = true } }),
                            TabletPlayerOptions(navigationStyle, miniPlayerSeekButtonsEnabled, expandPlayerTrigger, windowLayout.usesTabletLayout),
                        )
                    },
                    primary = {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val adaptivePlayer = rememberAdaptivePlayerChrome(
                    enabled = !windowLayout.usesTabletLayout &&
                        canUseAdaptivePlayer(
                        style = navigationStyle,
                        navigationVisible = showBottomNav,
                        hasEpisode = currentEpisode != null,
                        suppressed = !shouldShowPlayerChrome(isAccountActive, isFromOnboardingRoute, isSupportActive, isModeSwitching),
                        widthDp = maxWidth.value,
                    ),
                    episodeId = currentEpisode?.id,
                    route = currentRoute,
                )
                val navigationColors = if (artworkColorsEnabled && artworkNavigation.appThemePreviewEntryId == null && cx.aswin.boxlore.navigation.isArtworkDetailRoute(currentRoute)) {
                    artworkNavigation.colorsFor(navBackStackEntry?.id)
                } else {
                    null
                }
                val navigationBackground = navigationColors?.background ?: MaterialTheme.colorScheme.surface
                val showNavigationBlur = !windowLayout.usesTabletLayout && FloatingNavigationBlur.shows(navigationStyle, showBottomNav, navigationBlurLevel)
                val navigationBlur = remember { HazeState() }
                Scaffold(
                    containerColor = navigationBackground,
                    contentColor = navigationColors?.onBackground ?: MaterialTheme.colorScheme.onSurface,
                    // Screens own their system-bar insets; keep Scaffold padding at zero.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
                ) { innerPadding ->
                    // Consume Scaffold padding for lint; with zeroed insets this is a no-op.
                    Box(
                        modifier = Modifier.padding(innerPadding).fillMaxSize()
                            .nestedScroll(adaptivePlayer.scrollConnection)
                            // The page is what blurs behind the floating bar.
                            .then(if (showNavigationBlur) Modifier.hazeSource(navigationBlur) else Modifier),
                    ) {
                        PredictiveBackWrapper(
                            enabled = canGoBack && !tabletNavigation.detailActive,
                            backgroundColor = navigationBackground,
                            onBack = {
                                when (
                                    resolveLaunchSubscriptionsBack(
                                        openedToLandingOnLaunch.value &&
                                            isLaunchLandingBackRoute(currentRoute),
                                    )
                                ) {
                                    LaunchSubscriptionsBackAction.NavigateHome -> {
                                        openedToLandingOnLaunch.value = false
                                        navController.navigateHomeFromLaunchSubscriptions()
                                    }
                                    LaunchSubscriptionsBackAction.PopBackStack -> navController.popBackStack()
                                }
                            },
                        ) {
                            CompositionLocalProvider(LocalAdaptivePlayerCompactProgress provides adaptivePlayer.progress) {
                                navigationContent(navController, false)
                            }
                        }
                    }
                }

                val screenHeightDp = maxHeight
                val systemNavBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                val chromeMetrics = navigationChromeMetrics(navigationStyle)
                val bottomChromeClearance = chromeMetrics.bottomNavigationClearance
                val containerHeight = screenHeightDp + systemNavBarHeight + 50.dp
                val collapsedTargetY =
                    with(density) {
                        appMiniPlayerTopOffset(navigationStyle, screenHeightDp, systemNavBarHeight).toPx()
                    }

                if (showNavigationBlur) {
                    FloatingNavigationBlurStrip(
                        state = navigationBlur,
                        level = navigationBlurLevel,
                        bottomChrome = FloatingNavigationBlur.barChrome(navigationStyle, systemNavigationInset = systemNavBarHeight),
                        pageColor = navigationBackground,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }

                if (showBottomNav && !windowLayout.usesTabletLayout) {
                    val backStack = remember(navBackStackEntry) { snapshotNavBackStack(navController) }
                    val activeTab =
                        resolveBottomNavTab(
                            currentRoute = currentRoute,
                            backStack = backStack,
                        )
                    cx.aswin.boxlore.core.designsystem.theme.ArtworkNavigationTheme(
                        navigationColors,
                    ) {
                    BoxLoreNavigationBar(
                        currentRoute = activeTab,
                        onNavigate = { route ->
                            tabletNavigation.revealPrimary()
                            navController.navigateBottomNavTab(route, activeTab)
                        },
                        style = navigationStyle,
                        initialContentReady = isInitialHomeContentReady,
                        compactProgress = adaptivePlayer.progress,
                        modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .let { navigationModifier ->
                                if (navigationStyleUsesExternalSystemNavigationInset(navigationStyle)) {
                                    navigationModifier.padding(bottom = systemNavBarHeight)
                                } else {
                                    navigationModifier
                                }
                            },
                    )
                    }
                }

                AppPlaybackNotices(
                    repairVisible = legacyRssRepairInProgress,
                    nudgeVisible = showLateNightNudge &&
                        currentEpisode != null &&
                        !isModeSwitching &&
                        !isSupportActive &&
                        !isAccountActive &&
                        !legacyRssRepairInProgress,
                    playbackRepository = playbackRepository,
                )

                if (!windowLayout.usesTabletLayout && !tabletNavigation.hasDetail && shouldShowPlayerChrome(isAccountActive, isFromOnboardingRoute, isSupportActive, isModeSwitching)) {
                    PlayerSheetScaffold(
                        playbackRepository = playbackRepository,
                        downloadRepository = downloadRepository,
                        userPrefs = userPrefs,
                        layout =
                        PlayerSheetLayout(
                            collapsedTargetY = collapsedTargetY,
                            containerHeight = containerHeight,
                            collapsedHorizontalPadding =
                            if (navigationStyle == NavigationStyle.Floating) {
                                AppNavigationBarHorizontalInset
                            } else {
                                12.dp
                            },
                            navigationStyle = navigationStyle,
                            miniPlayerSeekButtonsEnabled = miniPlayerSeekButtonsEnabled,
                            expandTrigger = expandPlayerTrigger,
                            compactProgress = adaptivePlayer.progress,
                            compactTargetY = with(density) {
                                (
                                    screenHeightDp - bottomChromeClearance - systemNavBarHeight +
                                        (chromeMetrics.navigationBarHeight - AppLoreNavigationActionSize) / 2
                                    ).toPx()
                            },
                        ),
                        actions =
                        playerSheetActions(navController, playbackRepository, adaptivePlayer.onSheetInteractionChanged),
                        modifier = Modifier.align(Alignment.TopStart),
                    )
                }
            }
                    },
                ),
            )
            }

            OpmlImportDialog(
                state = opmlImportState,
                actions = OpmlImportDialogActions(
                    onDismissRequest = {
                        val currentState = opmlImportState
                        if (currentState is OpmlImportState.Success) {
                            if (currentState.isJson) {
                                if (currentRoute == "onboarding") {
                                    onboardingCompleted = true
                                    navController.navigate("home") {
                                        popUpTo("onboarding") { inclusive = true }
                                    }
                                }
                                onboardingViewModel.markOnboardingCompletedSilent {
                                    activity.recreate()
                                }
                            } else {
                                if (currentRoute == "onboarding") {
                                    AnalyticsHelper.trackOnboardingImportCompleted(
                                        importType = "opml",
                                        importedPodcastCount = currentState.importedCount,
                                        importedPodcastsList = currentState.importedPodcasts.map { it.title },
                                        totalOnboardingTimeSeconds = onboardingViewModel.getTotalOnboardingTime(),
                                        entryPoint = opmlImportSource,
                                    )
                                    onboardingViewModel.generateRecommendationsFromOpml(currentState.importedPodcasts)
                                } else if (opmlImportSource == "home_import_banner") {
                                    AnalyticsHelper.trackOnboardingImportCompleted(
                                        importType = "opml",
                                        importedPodcastCount = currentState.importedCount,
                                        importedPodcastsList = currentState.importedPodcasts.map { it.title },
                                        totalOnboardingTimeSeconds = 0f,
                                        entryPoint = "home_import_banner",
                                    )
                                }
                            }
                        }
                        opmlImportState = OpmlImportState.Idle
                    },
                    onSelectionChanged = { newSelection ->
                        val currentState = opmlImportState
                        if (currentState is OpmlImportState.AskCompleted) {
                            opmlImportState = currentState.copy(selectedIds = newSelection)
                        }
                    },
                    onConfirmCompleted = {
                        val currentState = opmlImportState
                        if (currentState is OpmlImportState.AskCompleted) {
                            val selectedIds = currentState.selectedIds
                            val podcastsToMark = currentState.importedPodcasts.filter { it.id in selectedIds }
                            if (podcastsToMark.isEmpty()) {
                                opmlImportState =
                                    OpmlImportState.Success(
                                        importedCount = currentState.importedPodcasts.size,
                                        completedCount = 0,
                                        isJson = false,
                                        importedPodcasts = currentState.importedPodcasts,
                                    )
                            } else {
                                opmlImportState =
                                    OpmlImportState.Completing(
                                        progress = 0f,
                                        currentShowTitle = podcastsToMark.first().title,
                                        podcastsToMark = podcastsToMark,
                                        totalImportedCount = currentState.importedPodcasts.size,
                                        importedPodcasts = currentState.importedPodcasts,
                                    )
                                importTriggerKey = System.currentTimeMillis()
                            }
                        }
                    },
                    onSkipCompleted = {
                        val currentState = opmlImportState
                        if (currentState is OpmlImportState.AskCompleted) {
                            opmlImportState =
                                OpmlImportState.Success(
                                    importedCount = currentState.importedPodcasts.size,
                                    completedCount = 0,
                                    isJson = false,
                                    importedPodcasts = currentState.importedPodcasts,
                                )
                        }
                    },
                    onImportJsonSelected = { uri -> performJsonImport(uri) },
                    onImportOpmlSelected = { uri ->
                        opmlImportState = OpmlImportState.Parsing(uri)
                        importTriggerKey = System.currentTimeMillis()
                    },
                    onSyncAccountSelected = {
                        opmlImportState = OpmlImportState.Idle
                        val fromOnboarding = resolveIsFromOnboarding(onboardingCompleted, opmlImportSource)
                        navController.navigate("settings?page=account&fromOnboarding=$fromOnboarding&fromImport=true")
                    },
                    onChooseAnotherFile = { opmlImportState = OpmlImportState.ShowSelector },
                ),
            )
        }
        }
    }
}

/**
 * The route a Play install referrer opens: the shared show, or the shared episode with its time,
 * clip and, for newer links, its show ([cx.aswin.boxlore.core.catalog.ReferralIntent.showId]) in the
 * route's `podcastId`, so an episode only its show's feed lists is found on a fresh install too.
 * Null for an unknown type.
 */
internal fun installReferralRoute(referral: cx.aswin.boxlore.core.catalog.ReferralIntent): String? = when (referral.type) {
    "podcast" -> "podcast/${referral.id}?entryPoint=install_referrer"
    "episode" -> {
        val tQuery = if (referral.timestamp != null) "&t=${referral.timestamp}" else ""
        val startQuery = if (referral.start != null) "&start=${referral.start}" else ""
        val endQuery = if (referral.end != null) "&end=${referral.end}" else ""
        val showQuery = referral.showId?.let { "&podcastId=$it" }.orEmpty()
        "episode/${referral.id}?entryPoint=install_referrer$tQuery$startQuery$endQuery$showQuery"
    }
    else -> null
}
