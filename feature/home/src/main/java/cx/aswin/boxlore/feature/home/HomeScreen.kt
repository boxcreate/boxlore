package cx.aswin.boxlore.feature.home

import android.content.pm.PackageManager
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import cx.aswin.boxlore.core.designsystem.layout.LocalBoxloreWindowLayout
import cx.aswin.boxlore.core.downloads.CompletedDownloadItem
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.DiscoverySections
import cx.aswin.boxlore.core.prefs.HeroCardStyle
import cx.aswin.boxlore.feature.home.components.ChangeRecommendationPodcastSheet
import cx.aswin.boxlore.feature.home.components.LocalHeroCardStyle
import cx.aswin.boxlore.feature.home.components.LocalHeroCoverShade
import cx.aswin.boxlore.feature.home.components.LocalHeroScrollOffset
import cx.aswin.boxlore.feature.home.components.LocalHeroTopInset
import cx.aswin.boxlore.feature.home.components.LocalLastSeenEpisodes
import cx.aswin.boxlore.feature.home.components.LocalMarqueeTopTone
import cx.aswin.boxlore.feature.home.components.MarqueeDetentConnection
import cx.aswin.boxlore.feature.home.components.MarqueeSheetOverlap
import cx.aswin.boxlore.feature.home.components.MarqueeTopTone
import cx.aswin.boxlore.feature.home.components.TopControlBar
import cx.aswin.boxlore.feature.home.components.homeHeroStyle
import cx.aswin.boxlore.feature.home.components.marqueeBarOverArtwork
import cx.aswin.boxlore.feature.home.components.marqueeDetent
import cx.aswin.boxlore.feature.home.logic.HomeMixMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

@androidx.compose.runtime.Stable
data class StableHeroList(
    val list: List<SmartHeroItem>,
)

@androidx.compose.runtime.Stable
data class StablePodcastList(
    val list: List<Podcast>,
)

@androidx.compose.runtime.Stable
data class StableEpisodeList(
    val list: List<Episode>,
)

@androidx.compose.runtime.Stable
data class StableCompletedDownloadList(
    val list: List<CompletedDownloadItem>,
)

@androidx.compose.runtime.Stable
data class StableEditorialRowList(
    val list: List<HomeEditorialRow>,
)

@androidx.compose.runtime.Stable
data class StablePlaybackStateMap(
    val map: Map<String, Pair<EpisodeStatus, Float>>,
)

@androidx.compose.runtime.Stable
data class HomePlaybackUi(
    val currentPlayingPodcastId: String?,
    val currentPlayingEpisodeId: String?,
    val isPlaying: Boolean,
    val isPlayerLoading: Boolean = false,
    val downloadedEpisodeIds: Set<String> = emptySet(),
    val completedDownloads: StableCompletedDownloadList = StableCompletedDownloadList(emptyList()),
    val homeMixMode: HomeMixMode = HomeMixMode.DAILY,
)

@androidx.compose.runtime.Stable
data class HomeSheetUi(
    val showReviewPrompt: Boolean = false,
    val reviewPromptVariant: cx.aswin.boxlore.feature.home.components.ReviewPromptVariant =
        cx.aswin.boxlore.feature.home.components.ReviewPromptVariant.Milestone,
    val showPostReview: Boolean = false,
    val showFeedback: Boolean = false,
    val candidatePodcasts: List<Podcast> = emptyList(),
)

@androidx.compose.runtime.Stable
data class HomeFeedCallbacks(
    val onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    val onHeroArrowClick: (SmartHeroItem, Int) -> Unit,
    val onEpisodeClick: ((Episode, Podcast, String?) -> Unit)?,
    val onPlayClick: ((Podcast, android.os.Bundle?) -> Unit)?,
    val onNavigateToLibrary: (() -> Unit)?,
    val onNavigateToDownloads: (() -> Unit)?,
    val onNavigateToExplore: ((String?, String, String?) -> Unit)?,
    val onToggleSubscription: (String) -> Unit,
    val onTogglePlayback: (android.os.Bundle?) -> Unit,
    val onSelectCategory: (String?) -> Unit,
    val onPodcastSelected: (String?) -> Unit,
    val onPlayMix: (HomeMixMode) -> Unit,
    val onMixModeChanged: (HomeMixMode) -> Unit,
    val onPlayEpisode: (Episode, Podcast, cx.aswin.boxlore.core.model.PlaybackEntryPoint) -> Unit,
    val onImportClick: () -> Unit,
    val onAiOnboardingClick: () -> Unit,
    val onDismissImportBanner: () -> Unit,
    val onBriefingClick: (String) -> Unit,
    val onDismissBriefing: () -> Unit,
    val onDismissBriefingForever: () -> Unit,
    val onFeedbackClick: () -> Unit,
    val onToggleHomePin: (String) -> Unit = {},
    val onDismissFeaturedVideoShowcaseForever: () -> Unit = {},
)

@androidx.compose.runtime.Stable
data class HomeScreenCallbacks(
    val feed: HomeFeedCallbacks,
    val onNavigateToSettings: (() -> Unit)? = null,
    val onForceReviewPrompt: () -> Unit = {},
    val onForceSurveyNps: () -> Unit = {},
    val onDismissReviewPrompt: () -> Unit = {},
    val onDismissPostReview: () -> Unit = {},
    val onDismissFeedback: () -> Unit = {},
    val onNavigateToPlayStoreReview: () -> Unit = {},
    val onSubmitFeedback: suspend (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    val onNavigateToDebug: () -> Unit = {},
    val onOverrideRecommendationPodcast: (String?) -> Unit = {},
    val onNavigateToLatestEpisodes: (() -> Unit)? = null,
)

@Composable
@Suppress("LongParameterList", "LongMethod")
fun HomeRoute(
    podcastRepository: cx.aswin.boxlore.core.catalog.PodcastRepository,
    playbackRepository: cx.aswin.boxlore.core.playback.PlaybackRepository,
    engagementPromptCoordinator: cx.aswin.boxlore.core.catalog.EngagementPromptCoordinator,
    subscriptionRepository: cx.aswin.boxlore.core.catalog.SubscriptionRepository,
    downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository,
    rssPodcastRepository: cx.aswin.boxlore.core.rss.RssPodcastRepository,
    adaptiveCandidateScorer: cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer,
    localCatalog: cx.aswin.boxlore.core.domain.ports.LocalCatalogPort,
    userPreferencesRepository: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    onPodcastClick: (Podcast, String, String?, Int?) -> Unit,
    onHeroArrowClick: (SmartHeroItem, Int) -> Unit,
    onEpisodeClick: ((Episode, Podcast, String?) -> Unit)? = null, // Navigate to EpisodeInfo
    onPlayClick: ((Podcast, android.os.Bundle?) -> Unit)? = null, // Navigate directly to Player (Resume)
    onNavigateToLibrary: (() -> Unit)? = null,
    onNavigateToDownloads: (() -> Unit)? = null,
    onNavigateToLatestEpisodes: (() -> Unit)? = null,
    onNavigateToExplore: ((String?, String, String?) -> Unit)? = null,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToPlayStoreReview: () -> Unit = {},
    onSubmitFeedback: suspend (String, String, String, String) -> Boolean = { _, _, _, _ -> false },
    onFeedbackClick: (() -> Unit)? = null,
    onNavigateToDebug: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onAiOnboardingClick: () -> Unit = {},
    onInitialContentReady: () -> Unit = {},
    onBriefingClick: (String) -> Unit = {},
    navController: NavController? = null,
    modifier: Modifier = Modifier,
) {
    val application = LocalContext.current.applicationContext as android.app.Application
    val viewModel: HomeViewModel =
        viewModel(
            factory =
            HomeViewModelAssembler.factory(
                application = application,
                deps =
                HomeViewModelDeps(
                    podcastRepository = podcastRepository,
                    playbackRepository = playbackRepository,
                    engagementCoordinator = engagementPromptCoordinator,
                    subscriptionRepository = subscriptionRepository,
                    downloadRepository = downloadRepository,
                    rssRepository = rssPodcastRepository,
                    adaptiveScorer = adaptiveCandidateScorer,
                    localCatalog = localCatalog,
                    userPreferencesRepository = userPreferencesRepository,
                ),
            ),
        )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    viewModel.onHomeStarted()
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            viewModel.onHomeStarted()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (navController != null) {
        DisposableEffect(navController) {
            val listener =
                NavController.OnDestinationChangedListener { _, destination, _ ->
                    val route = destination.route
                    if (route != null && route != "home" && !route.startsWith("episode") && !route.startsWith("podcast")) {
                        viewModel.selectPodcast(null)
                    }
                }
            navController.addOnDestinationChangedListener(listener)
            onDispose {
                navController.removeOnDestinationChangedListener(listener)
            }
        }
    }

    val uiState by viewModel.uiState.collectAsState()
    val homeShortcutsInLibrary by userPreferencesRepository.homeShortcutsInLibraryStream.collectAsState(initial = false)
    // Null until read, so a switched-off greeting never flashes in.
    val discoverySections by userPreferencesRepository.discoverySectionsStream.collectAsState(initial = null)
    val heroCardStyle by userPreferencesRepository.heroCardStyleStream.collectAsState(initial = remember { userPreferencesRepository.cachedHeroCardStyle })
    val heroCoverShade by userPreferencesRepository.heroCoverShadeStream.collectAsState(initial = remember { userPreferencesRepository.cachedHeroCoverShade })
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.pinFeedback.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    var hasReportedInitialContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.isLoading) {
        if (!uiState.isLoading && !hasReportedInitialContentReady) {
            withFrameNanos {}
            withFrameNanos {}
            hasReportedInitialContentReady = true
            onInitialContentReady()
        }
    }
    val isPlaying by remember(viewModel) {
        viewModel.playerState.map { it.isPlaying }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val currentPlayingPodcastId by remember(viewModel) {
        viewModel.playerState.map { it.currentPodcast?.id }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val currentPlayingEpisodeId by remember(viewModel) {
        viewModel.playerState.map { it.currentEpisode?.id }.distinctUntilChanged()
    }.collectAsState(initial = null)
    val isPlayerLoading by remember(viewModel) {
        viewModel.playerState.map { it.isLoading }.distinctUntilChanged()
    }.collectAsState(initial = false)
    val showReviewPrompt by viewModel.showReviewPrompt.collectAsState()
    val reviewPromptVariant by viewModel.reviewPromptVariant.collectAsState()
    val showPostReview by viewModel.showPostReview.collectAsState()
    val showFeedback by viewModel.showFeedback.collectAsState()

    val candidatePodcasts by viewModel.candidatePodcasts.collectAsState(initial = emptyList())

    val downloadedEpisodeIds by viewModel.downloadedEpisodeIds.collectAsState(initial = emptySet())
    val completedDownloadItems by viewModel.completedDownloadItems.collectAsState(initial = emptyList())
    val homeMixMode by viewModel.homeMixMode.collectAsState()
    val lastSeenEpisodes by viewModel.lastSeenEpisodes.collectAsState()

    val callbacks =
        remember(
            viewModel,
            onPodcastClick,
            onHeroArrowClick,
            onEpisodeClick,
            onPlayClick,
            onNavigateToLibrary,
            onNavigateToDownloads,
            onNavigateToExplore,
            onNavigateToSettings,
            onNavigateToPlayStoreReview,
            onSubmitFeedback,
            onNavigateToDebug,
            onImportClick,
            onAiOnboardingClick,
            onBriefingClick,
            onFeedbackClick,
            onNavigateToLatestEpisodes,
        ) {
            HomeScreenCallbacks(
                feed =
                HomeFeedCallbacks(
                    onPodcastClick = { podcast, entryPoint, category, index ->
                        podcast.latestEpisode?.id?.let { episodeId ->
                            viewModel.markPodcastEpisodeAsSeen(podcast.id, episodeId)
                        }
                        onPodcastClick(podcast, entryPoint, category, index)
                    },
                    onHeroArrowClick = onHeroArrowClick,
                    onEpisodeClick = onEpisodeClick,
                    onPlayClick = onPlayClick,
                    onNavigateToLibrary = onNavigateToLibrary,
                    onNavigateToDownloads = onNavigateToDownloads,
                    onNavigateToExplore = onNavigateToExplore,
                    onToggleSubscription = viewModel::toggleSubscription,
                    onTogglePlayback = viewModel::togglePlayback,
                    onSelectCategory = viewModel::selectCategory,
                    onPodcastSelected = viewModel::selectPodcast,
                    onPlayMix = viewModel::playMix,
                    onMixModeChanged = viewModel::setHomeMixMode,
                    onPlayEpisode = viewModel::playEpisode,
                    onImportClick = onImportClick,
                    onAiOnboardingClick = onAiOnboardingClick,
                    onDismissImportBanner = viewModel::dismissHomeImportBanner,
                    onBriefingClick = onBriefingClick,
                    onDismissBriefing = viewModel::dismissBriefingForToday,
                    onDismissBriefingForever = viewModel::dismissBriefingForever,
                    onFeedbackClick = onFeedbackClick ?: viewModel::triggerFeedback,
                    onToggleHomePin = viewModel::toggleHomePin,
                    onDismissFeaturedVideoShowcaseForever = viewModel::dismissFeaturedVideoShowcaseForever,
                ),
                onNavigateToSettings = onNavigateToSettings,
                onForceReviewPrompt = viewModel::forceReviewPrompt,
                onForceSurveyNps = viewModel::forceSurveyNps,
                onDismissReviewPrompt = {
                    viewModel.markReviewPromptShown()
                    viewModel.dismissReviewPrompt()
                },
                onDismissPostReview = viewModel::dismissPostReview,
                onDismissFeedback = viewModel::dismissFeedback,
                onNavigateToPlayStoreReview = {
                    viewModel.markReviewed()
                    viewModel.triggerPostReview()
                    onNavigateToPlayStoreReview()
                },
                onSubmitFeedback = onSubmitFeedback,
                onNavigateToDebug = onNavigateToDebug,
                onOverrideRecommendationPodcast = viewModel::setOverriddenRecPodcast,
                onNavigateToLatestEpisodes = onNavigateToLatestEpisodes,
            )
        }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalLastSeenEpisodes provides lastSeenEpisodes,
        LocalHeroCardStyle provides homeHeroStyle(heroCardStyle, LocalBoxloreWindowLayout.current.usesTabletLayout),
        LocalHeroCoverShade provides heroCoverShade,
        LocalHomeDiscoverySections provides discoverySections,
    ) {
        HomeScreen(
            uiState = uiState,
            showTopBarShortcuts = !homeShortcutsInLibrary,
            playback =
            HomePlaybackUi(
                currentPlayingPodcastId = currentPlayingPodcastId,
                currentPlayingEpisodeId = currentPlayingEpisodeId,
                isPlaying = isPlaying,
                isPlayerLoading = isPlayerLoading,
                downloadedEpisodeIds = downloadedEpisodeIds,
                completedDownloads = StableCompletedDownloadList(completedDownloadItems),
                homeMixMode = homeMixMode,
            ),
            sheets =
            HomeSheetUi(
                showReviewPrompt = showReviewPrompt,
                reviewPromptVariant = reviewPromptVariant,
                showPostReview = showPostReview,
                showFeedback = showFeedback,
                candidatePodcasts = candidatePodcasts,
            ),
            callbacks = callbacks,
            snackbarHostState = snackbarHostState,
            modifier = modifier,
        )
    }
}

private fun homeScrollFraction(
    gridState: LazyStaggeredGridState,
    collapseThresholdPx: Float,
): Float {
    if (gridState.firstVisibleItemIndex > 0) {
        return 1f
    }
    return (gridState.firstVisibleItemScrollOffset / collapseThresholdPx).coerceIn(0f, 1f)
}

/** The listener's Home section switches; null while they load, which hides the greeting. */
internal val LocalHomeDiscoverySections = staticCompositionLocalOf<DiscoverySections?> { DiscoverySections() }

@Composable
private fun HomeScreenFeedContent(
    uiState: HomeUiState,
    gridState: LazyStaggeredGridState,
    playback: HomePlaybackUi,
    callbacks: HomeFeedCallbacks,
    onChangePodcastClick: () -> Unit,
    discoverySections: DiscoverySections?,
) {
    if (uiState.isError) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Error loading content", color = MaterialTheme.colorScheme.error)
        }
        return
    }

    PodcastFeed(
        content =
        PodcastFeedContent(
            heroItems = StableHeroList(uiState.heroItems),
            latestItems = StablePodcastList(uiState.latestEpisodes),
            subscribedItems = StablePodcastList(uiState.subscribedPodcasts),
            editorialRows = StableEditorialRowList(uiState.editorialRows),
            gridItems = StablePodcastList(uiState.discoverPodcasts),
            recommendations = StableEpisodeList(uiState.recommendations),
            selectedPodcastEpisodes = StableEpisodeList(uiState.selectedPodcastEpisodes),
        ),
        feedState =
        PodcastFeedUiState(
            discoveryGreeting = uiState.discoveryGreeting,
            selectedCategory = uiState.selectedCategory,
            selectedPodcastId = uiState.selectedPodcastId,
            briefing = uiState.briefing,
            briefingChapters = uiState.briefingChapters,
            seemsToLikePodcast = uiState.seemsToLikePodcast,
            showImportBanner = uiState.showImportBanner,
            pinnedPodcastIds = uiState.pinnedPodcastIds,
            showDiscoveryGreeting = discoverySections?.curatedRows == true,
            exploreHasForYou = discoverySections?.pickedForYou != false,
        ),
        recommendationState =
        PodcastFeedRecommendationState(
            becauseYouLikeRecommendations = StableEpisodeList(uiState.becauseYouLikeRecommendations),
            becauseYouLikePodcasts = StablePodcastList(uiState.becauseYouLikePodcasts),
            isRecommendationsLoading = uiState.isRecommendationsLoading,
            isRecommendationsFallback = uiState.isRecommendationsFallback,
            onChangePodcastClick = onChangePodcastClick,
        ),
        loadingState =
        PodcastFeedLoadingState(
            isEditorialRowsLoading = uiState.isEditorialRowsLoading,
            isFilterLoading = uiState.isFilterLoading,
            isSelectedPodcastLoading = uiState.isSelectedPodcastLoading,
            isSelectedRssRefreshing = uiState.isSelectedRssRefreshing,
            isLoading = uiState.isLoading,
        ),
        playback =
        PodcastFeedPlayback(
            player = playback,
            episodePlaybackState = StablePlaybackStateMap(uiState.episodePlaybackState),
            softExpireProgressEpisodeIds = uiState.softExpireProgressEpisodeIds,
        ),
        callbacks = callbacks,
        layout = PodcastFeedLayout(gridState = gridState),
    )
}

@Composable
private fun HomeScreenBottomSheets(
    uiState: HomeUiState,
    sheets: HomeSheetUi,
    showChangePodcastSheet: Boolean,
    callbacks: HomeScreenCallbacks,
    onDismissChangePodcastSheet: () -> Unit,
) {
    if (sheets.showReviewPrompt) {
        cx.aswin.boxlore.feature.home.components.ReviewPromptSheet(
            completedCount = uiState.completedEpisodeCount,
            variant = sheets.reviewPromptVariant,
            onDismissRequest = callbacks.onDismissReviewPrompt,
            onNavigateToReview = callbacks.onNavigateToPlayStoreReview,
            onNavigateToFeedback = {
                callbacks.onDismissReviewPrompt()
                callbacks.feed.onFeedbackClick()
            },
        )
    }

    if (sheets.showPostReview) {
        cx.aswin.boxlore.feature.home.components.PostReviewSheet(
            onDismissRequest = callbacks.onDismissPostReview,
            onNavigateToFeedback = {
                callbacks.onDismissPostReview()
                callbacks.feed.onFeedbackClick()
            },
        )
    }

    if (sheets.showFeedback) {
        val context = LocalContext.current
        val versionStr =
            remember(context) {
                try {
                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
                } catch (_: PackageManager.NameNotFoundException) {
                    "unknown"
                }
            }

        cx.aswin.boxlore.feature.home.components.FeedbackSheet(
            appVersion = versionStr,
            onSubmit = callbacks.onSubmitFeedback,
            onRateInstead = {
                callbacks.onDismissFeedback()
                val pkgName = context.packageName
                try {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=$pkgName")),
                    )
                } catch (e: Exception) {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            android.net.Uri.parse("https://play.google.com/store/apps/details?id=$pkgName"),
                        ),
                    )
                }
            },
            onDismissRequest = callbacks.onDismissFeedback,
        )
    }

    if (showChangePodcastSheet) {
        ChangeRecommendationPodcastSheet(
            candidatePodcasts = sheets.candidatePodcasts,
            onDismissRequest = onDismissChangePodcastSheet,
            onPodcastSelect = { podcast ->
                onDismissChangePodcastSheet()
                callbacks.onOverrideRecommendationPodcast(podcast?.id)
            },
        )
    }
}

/** Home's top bar, with its analytics; over Marquee's cover it starts clear ([overArtwork]). */
@Composable
@Suppress("LongParameterList")
private fun HomeTopBar(
    scrollFractionProvider: () -> Float,
    showTopBarShortcuts: Boolean,
    callbacks: HomeScreenCallbacks,
    overArtwork: (() -> Float)? = null,
    artworkTone: MarqueeTopTone? = null,
    modifier: Modifier = Modifier,
) {
    TopControlBar(
        scrollFractionProvider = scrollFractionProvider,
        modifier = modifier,
        showUtilityIcons = showTopBarShortcuts,
        overArtwork = overArtwork,
        artworkContent = artworkTone?.content ?: androidx.compose.ui.graphics.Color.Unspecified,
        onFeedbackClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackTopControlbarInteraction("feedback_clicked", "home")
            callbacks.feed.onFeedbackClick()
        },
        onFeedbackLongClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackTopControlbarInteraction("feedback_long_clicked", "home")
            callbacks.onForceSurveyNps()
        },
        onAvatarClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackTopControlbarInteraction("settings_clicked", "home")
            callbacks.onNavigateToSettings?.invoke()
        },
        onAvatarLongClick = {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper
                .trackTopControlbarInteraction("avatar_long_clicked", "home")
            callbacks.onNavigateToDebug()
        },
    )
}

/** The expanded bar below the status bar: 16dp above and below its 33dp shortcuts. */
private val HomeTopBarEstimate = 65.dp

/** The bar fills over this distance as the body's edge passes under it. */
private val MarqueeBarEdge = 24.dp

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    playback: HomePlaybackUi,
    sheets: HomeSheetUi,
    callbacks: HomeScreenCallbacks,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = SnackbarHostState(),
    showTopBarShortcuts: Boolean = true,
) {
    // Track scroll state for collapsing top bar
    val gridState = rememberLazyStaggeredGridState()
    var showChangePodcastSheet by remember { androidx.compose.runtime.mutableStateOf(false) }

    val collapseThresholdPx = with(LocalDensity.current) { 100.dp.toPx() }
    val scrollFraction by remember(gridState, collapseThresholdPx) {
        derivedStateOf { homeScrollFraction(gridState, collapseThresholdPx) }
    }
    val feed: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize()) {
            HomeScreenFeedContent(
                uiState = uiState,
                gridState = gridState,
                playback = playback,
                callbacks = callbacks.feed,
                onChangePodcastClick = { showChangePodcastSheet = true },
                discoverySections = LocalHomeDiscoverySections.current,
            )
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        if (HeroCardStyle.sanitize(LocalHeroCardStyle.current) == HeroCardStyle.MARQUEE) {
            // Marquee's hero runs under the top bar, so the feed starts at the top of the screen and the
            // bar floats over it; the hero leaves room for the bar's height (LocalHeroTopInset).
            var topBarHeight by remember { androidx.compose.runtime.mutableIntStateOf(0) }
            val estimate = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + HomeTopBarEstimate
            val topInset = if (topBarHeight > 0) with(LocalDensity.current) { topBarHeight.toDp() } else estimate
            val topTone = remember { MarqueeTopTone() }
            val overlapPx = with(LocalDensity.current) { MarqueeSheetOverlap.toPx() }
            val edgePx = with(LocalDensity.current) { MarqueeBarEdge.toPx() }
            // The hero keeps its cover still while it scrolls away.
            val heroScroll: () -> Int = remember(gridState) {
                { if (gridState.firstVisibleItemIndex == 0) gridState.firstVisibleItemScrollOffset else Int.MAX_VALUE }
            }
            val heroItem = { gridState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == "hero" } }
            // The page stops where the body's edge meets the bar; the next swipe carries it under.
            val detent = remember(gridState, overlapPx) {
                MarqueeDetentConnection(
                    detent = { heroItem()?.let { marqueeDetent(it.size.height, overlapPx, topBarHeight) } },
                    scrolled = { if (heroItem() == null) null else heroScroll() },
                )
            }
            // The bar stays clear over the cover, and fills with the body's colour as the body passes under it.
            val overArtwork: () -> Float = remember(gridState, overlapPx, edgePx) {
                { marqueeBarOverArtwork(heroItem()?.let { it.offset.y + it.size.height }, overlapPx, topBarHeight, edgePx) }
            }
            CompositionLocalProvider(
                LocalHeroTopInset provides topInset,
                LocalHeroScrollOffset provides heroScroll,
                LocalMarqueeTopTone provides topTone,
            ) { Box(Modifier.fillMaxSize().nestedScroll(detent)) { feed() } }
            HomeTopBar(
                scrollFractionProvider = { scrollFraction },
                showTopBarShortcuts = showTopBarShortcuts,
                callbacks = callbacks,
                overArtwork = overArtwork,
                artworkTone = topTone,
                // Keep the expanded height: the bar shrinks as Home scrolls, the hero must not.
                modifier = Modifier.onSizeChanged { if (scrollFraction == 0f) topBarHeight = it.height },
            )
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                HomeTopBar({ scrollFraction }, showTopBarShortcuts, callbacks)
                feed()
            }
        }
    }

    HomeScreenBottomSheets(
        uiState = uiState,
        sheets = sheets,
        showChangePodcastSheet = showChangePodcastSheet,
        callbacks = callbacks,
        onDismissChangePodcastSheet = { showChangePodcastSheet = false },
    )
}
