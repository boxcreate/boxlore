package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.designsystem.theme.GoogleSansWeight

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.components.BoxLoreLogo
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberCondensedGoogleSansFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Genre data matching GenreSelector.kt
data class GenreItem(
    val label: String,
    val value: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

val ONBOARDING_GENRES =
    listOf(
        GenreItem("News", "News", Icons.Rounded.Newspaper),
        GenreItem("Tech", "Technology", Icons.Rounded.Computer),
        GenreItem("Business", "Business", Icons.Rounded.Work),
        GenreItem("Comedy", "Comedy", Icons.Rounded.SentimentVerySatisfied),
        GenreItem("True Crime", "True Crime", Icons.Rounded.Fingerprint),
        GenreItem("Sports", "Sports", Icons.Rounded.EmojiEvents),
        GenreItem("Health", "Health", Icons.Rounded.MonitorHeart),
        GenreItem("History", "History", Icons.Rounded.AccountBalance),
        GenreItem("Arts", "Arts", Icons.Rounded.Palette),
        GenreItem("Society", "Society & Culture", Icons.Rounded.Groups),
        GenreItem("Education", "Education", Icons.Rounded.School),
        GenreItem("Science", "Science", Icons.Rounded.Science),
        GenreItem("TV & Film", "TV & Film", Icons.Rounded.Movie),
        GenreItem("Fiction", "Fiction", Icons.Rounded.AutoStories),
        GenreItem("Music", "Music", Icons.Rounded.MusicNote),
        GenreItem("Religion", "Religion & Spirituality", Icons.Rounded.SelfImprovement),
        GenreItem("Family", "Kids & Family", Icons.Rounded.ChildCare),
        GenreItem("Leisure", "Leisure", Icons.Rounded.Weekend),
        GenreItem("Govt", "Government", Icons.Rounded.Gavel),
    )

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    onImportClick: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOnboardingCompleted = remember { viewModel.isOnboardingCompleted() }

    val handleComplete = {
        if (uiState.selectedPodcasts.isNotEmpty()) {
            viewModel.generateRecommendationsFromSearch()
        } else {
            viewModel.completeOnboarding(onComplete)
        }
    }

    val isRootStep =
        uiState.currentStep == OnboardingStep.WELCOME ||
            uiState.currentStep == OnboardingStep.AI_ONBOARDING

    BackHandler(
        enabled =
            if (isOnboardingCompleted) {
                true
            } else {
                !isRootStep
            },
    ) {
        if (isOnboardingCompleted &&
            (
                uiState.currentStep == OnboardingStep.WELCOME ||
                    uiState.currentStep == OnboardingStep.AI_ONBOARDING ||
                    uiState.currentStep == OnboardingStep.GENRES
            )
        ) {
            onBack()
        } else {
            when (uiState.currentStep) {
                OnboardingStep.WELCOME -> {
                    // Handled above
                }
                OnboardingStep.GENRES -> {
                    viewModel.navigateBackToWelcome()
                }
                OnboardingStep.SUB_GENRES -> {
                    viewModel.navigateBackFromSubGenres()
                }
                OnboardingStep.ACTIVITY_PICKER -> {
                    viewModel.navigateBackFromActivityPicker()
                }
                OnboardingStep.LENGTH_PICKER -> {
                    viewModel.navigateBackFromLengthPicker()
                }
                OnboardingStep.SEARCH -> {
                    if (uiState.searchQuery.isNotEmpty()) {
                        viewModel.updateSearchQuery("")
                    } else {
                        viewModel.navigateBackFromSearch()
                    }
                }
                OnboardingStep.AI_ONBOARDING -> {
                    // Handled above
                }
                OnboardingStep.AI_SUGGESTIONS -> {
                    viewModel.navigateBackFromSuggestions()
                }
            }
        }
    }

    // Main content with animated transitions
    AnimatedContent(
        targetState = uiState.currentStep,
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            (slideInHorizontally { it } + fadeIn()) togetherWith
                (slideOutHorizontally { -it } + fadeOut())
        },
        label = "onboarding_step",
    ) { step ->
        LaunchedEffect(step) {
            val (stepName, stepIndex, flowType) =
                when (step) {
                    OnboardingStep.WELCOME -> Triple("welcome", 0, "welcome")
                    OnboardingStep.GENRES -> Triple("genres", 1, "manual_genre")
                    OnboardingStep.SUB_GENRES -> Triple("sub_genres", 2, "manual_genre")
                    OnboardingStep.ACTIVITY_PICKER -> Triple("activities", 3, "manual_genre")
                    OnboardingStep.LENGTH_PICKER -> Triple("lengths", 4, "manual_genre")
                    OnboardingStep.SEARCH -> Triple("search", 5, "search")
                    OnboardingStep.AI_ONBOARDING -> Triple("ai_chat", 1, "ai_chat")
                    OnboardingStep.AI_SUGGESTIONS -> Triple("ai_suggestions", 6, "ai_chat")
                }
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackOnboardingStepViewed(
                stepName = stepName,
                flowType = flowType,
                stepIndex = stepIndex,
            )
        }
        when (step) {
            OnboardingStep.WELCOME -> {
                LaunchedEffect(Unit) {
                    viewModel.onWelcomeScreenViewed()
                }
                WelcomeScreen(
                    onHelpMeFind = viewModel::startOnboarding,
                    onSearch = viewModel::navigateToSearch,
                    onSkip = { viewModel.skipOnboarding(onComplete) },
                    onImportClick = onImportClick,
                )
            }
            OnboardingStep.GENRES -> {
                LaunchedEffect(Unit) { viewModel.onGenreScreenViewed() }
                GenrePickerScreen(
                    selectedGenres = uiState.selectedGenres,
                    onToggleGenre = viewModel::toggleGenre,
                    onContinue = viewModel::continueToRecommendations,
                    onBack = {
                        if (isOnboardingCompleted) {
                            onBack()
                        } else {
                            viewModel.navigateBackToWelcome()
                        }
                    },
                )
            }
            OnboardingStep.SUB_GENRES -> {
                SubGenrePickerScreen(
                    selectedGenres = uiState.selectedGenres,
                    selectedSubGenres = uiState.selectedSubGenres,
                    onToggleSubGenre = viewModel::toggleSubGenre,
                    onBack = viewModel::navigateBackFromSubGenres,
                    onContinue = viewModel::continueToActivityPicker,
                )
            }
            OnboardingStep.ACTIVITY_PICKER -> {
                ActivityPickerScreen(
                    selectedActivities = uiState.listeningActivities,
                    activityGenreMap = uiState.activityGenreMap,
                    allSelectedGenres = uiState.selectedGenres,
                    onToggleActivity = viewModel::toggleListeningActivity,
                    onSetGenresForActivity = viewModel::setGenresForActivity,
                    onBack = viewModel::navigateBackFromActivityPicker,
                    onContinue = viewModel::continueToLengthPicker,
                )
            }
            OnboardingStep.LENGTH_PICKER -> {
                LengthPickerScreen(
                    selectedLengths = uiState.preferredLengths,
                    lengthGenreMap = uiState.lengthGenreMap,
                    allSelectedGenres = uiState.selectedGenres,
                    onToggleLength = viewModel::togglePreferredLength,
                    onSetGenresForLength = viewModel::setGenresForLength,
                    onBack = viewModel::navigateBackFromLengthPicker,
                    onContinue = viewModel::synthesizeGenreOnboarding,
                )
            }
            OnboardingStep.SEARCH -> {
                val isAiSideTrip = viewModel.isSearchFromAiChat()
                OnboardingSearchScreen(
                    query = uiState.searchQuery,
                    results = uiState.searchResults,
                    isSearching = uiState.isSearching,
                    subscribedIds = uiState.subscribedPodcastIds,
                    selectedPodcasts = uiState.selectedPodcasts,
                    onQueryChange = viewModel::updateSearchQuery,
                    onSubscribe = viewModel::toggleSubscriptionFromSearch,
                    onBack = viewModel::navigateBackFromSearch,
                    onDone = {
                        if (isAiSideTrip) {
                            viewModel.returnToAiChatFromSearch()
                        } else {
                            handleComplete()
                        }
                    },
                    popularPodcasts = uiState.popularPodcasts,
                    isPopularLoading = uiState.isPopularLoading,
                    selectedSearchGenre = uiState.selectedSearchGenre,
                    onGenreSelect = viewModel::selectSearchGenre,
                    isAiSideTrip = isAiSideTrip,
                )
            }
            OnboardingStep.AI_ONBOARDING -> {
                AiOnboardingScreen(
                    uiState = uiState,
                    onBack = {
                        if (isOnboardingCompleted && uiState.aiCurrentTurn <= 1) {
                            onBack()
                        } else {
                            viewModel.navigateBackInAiOnboarding()
                        }
                    },
                    onOptionToggle = viewModel::toggleAiOption,
                    onCustomInputChange = viewModel::updateAiCustomInput,
                    onContinue = {
                        if (uiState.aiOptions.isEmpty() || uiState.aiCurrentTurn >= 7) {
                            viewModel.synthesizeAndBuildCurriculum()
                        } else {
                            viewModel.sendAiTurnInput()
                        }
                    },
                    onRevealSuggestions = viewModel::navigateToSuggestions,
                    onRetryCuration = viewModel::retryLastAction,
                    onSwitchToManual = viewModel::switchToLegacyOnboarding,
                    onBuildFeedNow = viewModel::synthesizeAndBuildCurriculum,
                    onSearchInstead = viewModel::switchToSearchFromAi,
                )
            }
            OnboardingStep.AI_SUGGESTIONS -> {
                AiSuggestionsScreen(
                    uiState = uiState,
                    onBack = viewModel::navigateBackFromSuggestions,
                    onToggleSubscription = viewModel::togglePodcastSubscription,
                    onToggleRowSubscriptions = viewModel::toggleAllPodcastsInRow,
                    onRegionChange = viewModel::setRegion,
                    onRetry = viewModel::retryLastAction,
                    onFinish = {
                        viewModel.finishAiOnboarding(onComplete)
                    },
                )
            }
        }
    }
}

/** One marquee loop: 6 cards; start-to-start distance includes the gap after each card. */
private const val CoverLoopCount = 6
private const val CoverCardDp = 110
private const val CoverGapDp = 12
/** Distance from card[i] to card[i+CoverLoopCount] under spacedBy (6 cards + 6 gaps). */
private const val CoverLoopPeriodDp = CoverLoopCount * (CoverCardDp + CoverGapDp) // 732
/** Enough tiled loops for offsets without decoding a wall of Images. */
private const val CoverLoopRepeats = 7
/** Keep row translation ≤ this so the left edge never exposes empty space. */
private const val CoverMaxTranslationDp = -48f
/** Subtle entrance glide — large travel reads as janky “loading”. */
private const val WelcomeCarouselTravelDp = 120f

/**
 * Welcome first-impression clock.
 * Covers ease into a continuous drift (no mid-sequence pause). Chrome rises on draw-phase
 * layers only so the tree doesn’t recompose every frame.
 */
private const val WelcomeEntranceMs = 2400
/** Drift begins during cover settle so velocity never hits zero. */
private const val WelcomeDriftDelayMs = 700L
private const val WelcomeDriftPeriodMs = 40000

@Composable
private fun WelcomeScreen(
    onHelpMeFind: () -> Unit,
    onSearch: () -> Unit,
    onSkip: () -> Unit,
    onImportClick: () -> Unit,
) {
    val condensedFamily = rememberCondensedGoogleSansFamily()
    val entranceProgress = remember { Animatable(0f) }
    val driftProgress = remember { Animatable(0f) }
    // Buttons stay inert until entrance completes — avoids per-frame enabled recomposition.
    var chromeInteractive by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Decode/layout a couple frames before motion — kills the cold-start hitch.
        withFrameNanos { }
        withFrameNanos { }
        withFrameNanos { }
        launch {
            entranceProgress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = WelcomeEntranceMs,
                        easing = LinearEasing,
                    ),
            )
            chromeInteractive = true
        }
        launch {
            delay(WelcomeDriftDelayMs)
            driftProgress.animateTo(
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
                                durationMillis = WelcomeDriftPeriodMs,
                                easing = LinearEasing,
                            ),
                        repeatMode = RepeatMode.Restart,
                    ),
            )
        }
    }

    // Grid is a sibling of inset-padded chrome so status-bar/nav inset settle doesn't jitter covers,
    // and so foreground recomposition doesn't rebuild hundreds of Images each frame.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
    ) {
        CinematicBackgroundGrid(
            entranceProgress = entranceProgress,
            driftProgress = driftProgress,
        )

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = Color.Transparent,
        ) { innerPadding ->
            WelcomeForeground(
                entranceProgress = entranceProgress,
                chromeInteractive = chromeInteractive,
                condensedFamily = condensedFamily,
                actions =
                    WelcomeActions(
                        onHelpMeFind = onHelpMeFind,
                        onSearch = onSearch,
                        onSkip = onSkip,
                        onImportClick = onImportClick,
                    ),
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            )
        }
    }
}

private data class WelcomeActions(
    val onHelpMeFind: () -> Unit,
    val onSearch: () -> Unit,
    val onSkip: () -> Unit,
    val onImportClick: () -> Unit,
)

@Composable
@Suppress("LongMethod")
private fun WelcomeForeground(
    entranceProgress: Animatable<Float, AnimationVector1D>,
    chromeInteractive: Boolean,
    condensedFamily: FontFamily,
    actions: WelcomeActions,
    modifier: Modifier = Modifier,
) {
    // IMPORTANT: never read entranceProgress.value in composition — that recomposes this whole
    // tree every frame and feels like a janky loading page. All motion is graphicsLayer-only.
    Box(modifier = modifier) {
        val scrimColor = MaterialTheme.colorScheme.surface
        // Static scrim — becomes solid over the last row’s bottom edge (covers stay
        // natural height; we hide the “end of carousel” with opacity, not stretch).
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to scrimColor.copy(alpha = 0.0f),
                                    0.20f to scrimColor.copy(alpha = 0.0f),
                                    0.34f to scrimColor.copy(alpha = 0.55f),
                                    0.46f to scrimColor.copy(alpha = 0.88f),
                                    0.56f to scrimColor.copy(alpha = 0.98f),
                                    0.64f to scrimColor,
                                    1.0f to scrimColor,
                                ),
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier.graphicsLayer {
                        applyWelcomeReveal(
                            progress = entranceProgress.value,
                            start = 0.28f,
                            end = 0.58f,
                            riseDp = 12f,
                        )
                    },
            ) {
                BoxLoreLogo(
                    textColor = MaterialTheme.colorScheme.primary,
                    height = 38.dp,
                )
                Text(
                    text = "Podcasts, done right.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = GoogleSansWeight.medium,
                    fontFamily = condensedFamily,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            // Extra air between brand lockup and CTAs — less crowded.
            Spacer(modifier = Modifier.height(32.dp))

            // Primary CTA — soft card, AI cue integrated (no floating badge).
            val primaryShape = RoundedCornerShape(28.dp)
            Surface(
                color = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = primaryShape,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            applyWelcomeReveal(
                                progress = entranceProgress.value,
                                start = 0.42f,
                                end = 0.72f,
                                riseDp = 10f,
                            )
                        }.expressiveClickable(
                            enabled = chromeInteractive,
                            shape = primaryShape,
                            onClick = actions.onHelpMeFind,
                        ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 22.dp, end = 16.dp, top = 18.dp, bottom = 18.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "Personalized with AI",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = GoogleSansWeight.medium,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                letterSpacing = 0.2.sp,
                            )
                        }
                        Text(
                            text = "Build my personalized feed",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = GoogleSansWeight.bold,
                        )
                        Text(
                            text = "We'll find shows that match what you love",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.72f),
                        )
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.16f),
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(percent = 50),
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Secondary row — quieter tonal pills
            val secondaryShape = RoundedCornerShape(22.dp)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .graphicsLayer {
                            applyWelcomeReveal(
                                progress = entranceProgress.value,
                                start = 0.52f,
                                end = 0.80f,
                                riseDp = 8f,
                            )
                        },
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = secondaryShape,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 54.dp)
                            .expressiveClickable(
                                enabled = chromeInteractive,
                                shape = secondaryShape,
                                onClick = actions.onSearch,
                            ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "I know my shows",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = GoogleSansWeight.semiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    shape = secondaryShape,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .heightIn(min = 54.dp)
                            .expressiveClickable(
                                enabled = chromeInteractive,
                                shape = secondaryShape,
                                onClick = actions.onImportClick,
                            ),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Upload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Import library",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = GoogleSansWeight.semiBold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Skip — quiet text action
            val skipMuted = MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier =
                    Modifier
                        .graphicsLayer {
                            applyWelcomeReveal(
                                progress = entranceProgress.value,
                                start = 0.62f,
                                end = 0.90f,
                                riseDp = 6f,
                            )
                        }.expressiveClickable(
                            enabled = chromeInteractive,
                            shape = RoundedCornerShape(percent = 50),
                            onClick = actions.onSkip,
                        ).padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "Skip setup",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = GoogleSansWeight.medium,
                        color = skipMuted,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = skipMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

/** Maps global entrance progress into a 0→1 eased segment. */
private fun welcomeSegmentEase(
    progress: Float,
    start: Float,
    end: Float,
): Float {
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return WelcomeChromeDecelerate.transform(t)
}

/** Fade + short rise for chrome — draw-phase only via [applyWelcomeReveal]. */
private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyWelcomeReveal(
    progress: Float,
    start: Float,
    end: Float,
    riseDp: Float,
) {
    val ease = welcomeSegmentEase(progress, start, end)
    alpha = ease
    translationY = (1f - ease) * riseDp * density
}

/** Soft decelerate for chrome reveals. */
private val WelcomeChromeDecelerate = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f)

/** Carousel entrance — gentle ease-out glide. */
private val WelcomeCarouselSettle = CubicBezierEasing(0.33f, 0.0f, 0.2f, 1.0f)

@Composable
private fun CinematicBackgroundGrid(
    entranceProgress: Animatable<Float, AnimationVector1D>,
    driftProgress: Animatable<Float, AnimationVector1D>,
) {
    val context = LocalContext.current
    val allCovers =
        remember {
            (0..99)
                .map { index ->
                    context.resources.getIdentifier("pod_cover_$index", "drawable", context.packageName)
                }.filter { it != 0 }
                .shuffled()
        }

    if (allCovers.isEmpty()) return

    val row1Covers = remember(allCovers) { allCovers.filterIndexed { idx, _ -> idx % 4 == 0 } }
    val row2Covers = remember(allCovers) { allCovers.filterIndexed { idx, _ -> idx % 4 == 1 } }
    val row3Covers = remember(allCovers) { allCovers.filterIndexed { idx, _ -> idx % 4 == 2 } }
    val row4Covers = remember(allCovers) { allCovers.filterIndexed { idx, _ -> idx % 4 == 3 } }

    val coverFadeStart = 0f
    val coverFadeEnd = 0.40f

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val local =
                        ((entranceProgress.value - coverFadeStart) / (coverFadeEnd - coverFadeStart))
                            .coerceIn(0f, 1f)
                    val coverFade = WelcomeChromeDecelerate.transform(local)
                    alpha = 0.55f + coverFade * 0.45f
                },
        verticalArrangement = Arrangement.spacedBy(CoverGapDp.dp, Alignment.Top),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        ScrollingRow(
            covers = row1Covers,
            entranceProgress = entranceProgress,
            driftProgress = driftProgress,
            baseOffsetDp = -(CoverLoopPeriodDp + WelcomeCarouselTravelDp + 400f),
            direction = 1f,
            rowDelay = 0f,
        )
        ScrollingRow(
            covers = row2Covers,
            entranceProgress = entranceProgress,
            driftProgress = driftProgress,
            baseOffsetDp = -(CoverLoopPeriodDp * 0.55f),
            direction = -1f,
            rowDelay = 0.04f,
        )
        ScrollingRow(
            covers = row3Covers,
            entranceProgress = entranceProgress,
            driftProgress = driftProgress,
            baseOffsetDp = -(CoverLoopPeriodDp + WelcomeCarouselTravelDp + 480f),
            direction = 1f,
            rowDelay = 0.08f,
        )
        ScrollingRow(
            covers = row4Covers,
            entranceProgress = entranceProgress,
            driftProgress = driftProgress,
            baseOffsetDp = -(CoverLoopPeriodDp * 0.7f),
            direction = -1f,
            rowDelay = 0.12f,
        )
    }
}

@Composable
private fun ScrollingRow(
    covers: List<Int>,
    entranceProgress: Animatable<Float, AnimationVector1D>,
    driftProgress: Animatable<Float, AnimationVector1D>,
    baseOffsetDp: Float,
    direction: Float,
    rowDelay: Float,
) {
    val loopCovers =
        remember(covers) {
            if (covers.isEmpty()) {
                emptyList()
            } else {
                List(CoverLoopCount) { index -> covers[index % covers.size] }
            }
        }
    val tiledCovers =
        remember(loopCovers) {
            List(CoverLoopRepeats) { loopCovers }.flatten()
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .wrapContentWidth(unbounded = true, align = Alignment.Start)
                .graphicsLayer {
                    // Ease into rest while drift is already running — continuous marquee, no pause.
                    val local =
                        ((entranceProgress.value - rowDelay) / 0.45f).coerceIn(0f, 1f)
                    val scrollEase = WelcomeCarouselSettle.transform(local)
                    val period = CoverLoopPeriodDp.toFloat()
                    val translationDp =
                        (
                            baseOffsetDp +
                                direction * (WelcomeCarouselTravelDp * scrollEase) +
                                direction * (period * driftProgress.value)
                        ).coerceAtMost(CoverMaxTranslationDp)
                    translationX = translationDp.dp.toPx()
                },
        horizontalArrangement = Arrangement.spacedBy(CoverGapDp.dp),
    ) {
        tiledCovers.forEach { drawableResId ->
            val cardShape = RoundedCornerShape(16.dp)
            Box(
                modifier =
                    Modifier
                        .size(CoverCardDp.dp)
                        .clip(cardShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Image(
                    painter = painterResource(id = drawableResId),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}
