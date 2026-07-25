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
import cx.aswin.boxlore.core.designsystem.theme.expressiveClickable
import cx.aswin.boxlore.core.designsystem.theme.rememberCondensedGoogleSansFamily
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

/** One marquee loop: 6×(110dp card + 12dp gap) including the gap before the next loop. */
private const val CoverLoopCount = 6
private const val CoverCardDp = 110
private const val CoverGapDp = 12
private const val CoverLoopPeriodDp = CoverLoopCount * (CoverCardDp + CoverGapDp) // 732
/** Enough tiled loops for entrance offsets (~2400dp) without ~480 Image nodes. */
private const val CoverLoopRepeats = 8

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

    LaunchedEffect(Unit) {
        // Let the first cover frames compose/decode before motion — avoids start hitch.
        withFrameNanos { }
        withFrameNanos { }
        launch {
            entranceProgress.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = 4800,
                        easing = LinearEasing,
                    ),
            )
        }
        launch {
            driftProgress.animateTo(
                targetValue = 1f,
                animationSpec =
                    infiniteRepeatable(
                        animation =
                            tween(
                                durationMillis = 25000, // 732dp loop ≈ 29dp/s
                                easing = LinearEasing,
                            ),
                        repeatMode = RepeatMode.Restart,
                    ),
            )
        }
    }

    // Grid is a sibling of inset-padded chrome so status-bar/nav inset settle doesn't jitter covers,
    // and so foreground recomposition (logo/CTAs) doesn't rebuild hundreds of Images each frame.
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
                condensedFamily = condensedFamily,
                onHelpMeFind = onHelpMeFind,
                onSearch = onSearch,
                onSkip = onSkip,
                onImportClick = onImportClick,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            )
        }
    }
}

@Composable
@Suppress("LongMethod")
private fun WelcomeForeground(
    entranceProgress: Animatable<Float, AnimationVector1D>,
    condensedFamily: FontFamily,
    onHelpMeFind: () -> Unit,
    onSearch: () -> Unit,
    onSkip: () -> Unit,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        val logoProgress = ((entranceProgress.value - 0.20f) / 0.55f).coerceIn(0f, 1f)
        val logoEase = FastOutSlowInEasing.transform(logoProgress)

        val scrimColor = MaterialTheme.colorScheme.surface
        val scrimEdge = 0.68f - (logoEase * 0.23f)
        val scrimMid = 0.76f - (logoEase * 0.24f)
        val scrimFull = 0.81f - (logoEase * 0.24f)
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops =
                                arrayOf(
                                    0.0f to scrimColor.copy(alpha = 0.0f),
                                    (scrimEdge - 0.15f).coerceAtLeast(0f) to scrimColor.copy(alpha = 0.0f),
                                    scrimEdge to scrimColor.copy(alpha = 0.5f),
                                    scrimMid to scrimColor.copy(alpha = 0.9f),
                                    scrimFull to scrimColor,
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

            val logoScale = 1.3f - (logoEase * 0.3f)
            val logoOffsetY = (1f - logoEase) * 150f
            Column(
                modifier =
                    Modifier
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            translationY = logoOffsetY * density
                        },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Welcome to",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = GoogleSansWeight.bold,
                    fontFamily = condensedFamily,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                cx.aswin.boxlore.core.designsystem.components.BoxLoreLogo(
                    textColor = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            val btn1RawProgress = ((entranceProgress.value - 0.45f) / 0.30f).coerceIn(0f, 1f)
            val btn2RawProgress = ((entranceProgress.value - 0.53f) / 0.30f).coerceIn(0f, 1f)
            val btn3RawProgress = ((entranceProgress.value - 0.61f) / 0.30f).coerceIn(0f, 1f)

            val btn1Alpha = FastOutSlowInEasing.transform(btn1RawProgress)
            val btn2Alpha = FastOutSlowInEasing.transform(btn2RawProgress)
            val btn3Alpha = FastOutSlowInEasing.transform(btn3RawProgress)

                // Primary CTA
                Box(
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        shape = RoundedCornerShape(percent = 50),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(76.dp)
                                .graphicsLayer {
                                    alpha = btn1Alpha
                                    translationY = (1f - btn1Alpha) * 20.dp.toPx()
                                }.expressiveClickable(
                                    enabled = btn1Alpha > 0.95f,
                                    shape = RoundedCornerShape(percent = 50),
                                    onClick = onHelpMeFind,
                                ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 10.dp),
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = "Build my personalized feed.",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = GoogleSansWeight.bold,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "We'll find you perfect shows based on what you love",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    // Floating AI Badge sitting on the button border
                    Box(
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-24).dp, y = (-6).dp)
                                .graphicsLayer {
                                    alpha = btn1Alpha
                                    translationY = (1f - btn1Alpha) * 20.dp.toPx()
                                }.background(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = RoundedCornerShape(percent = 50),
                                ).border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(percent = 50),
                                ).padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(12.dp),
                            )
                            Text(
                                text = "AI",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = GoogleSansWeight.extraBold,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 9.sp,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Secondary row
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .graphicsLayer {
                                alpha = btn2Alpha
                                translationY = (1f - btn2Alpha) * 20.dp.toPx()
                            },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(percent = 50),
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 50.dp)
                                .expressiveClickable(
                                    enabled = btn2Alpha > 0.95f,
                                    shape = RoundedCornerShape(percent = 50),
                                    onClick = onSearch,
                                ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "I know my shows",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = GoogleSansWeight.bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        shape = RoundedCornerShape(percent = 50),
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .heightIn(min = 50.dp)
                                .expressiveClickable(
                                    enabled = btn2Alpha > 0.95f,
                                    shape = RoundedCornerShape(percent = 50),
                                    onClick = onImportClick,
                                ),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Upload,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Import library",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = GoogleSansWeight.bold,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Skip
                Box(
                    modifier =
                        Modifier
                            .graphicsLayer {
                                alpha = btn3Alpha
                            }.expressiveClickable(
                                enabled = btn3Alpha > 0.95f,
                                shape = RoundedCornerShape(percent = 50),
                                onClick = onSkip,
                            ).padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = "Skip Setup",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = GoogleSansWeight.bold,
                            color = Color(0xFF888888),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF888888),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(36.dp))
            }
        }
}

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

    val smoothBurstEasing = remember { CubicBezierEasing(0.25f, 0.1f, 0.25f, 1.0f) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(CoverGapDp.dp, Alignment.Top),
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        ScrollingRow(
            covers = row1Covers,
            entranceProgress = entranceProgress,
            driftProgress = driftProgress,
            baseOffsetDp = -2200f,
            direction = 1f,
            entranceEasing = smoothBurstEasing,
        )
        ScrollingRow(
            covers = row2Covers,
            entranceProgress = entranceProgress,
            driftProgress = driftProgress,
            baseOffsetDp = -100f,
            direction = -1f,
            entranceEasing = smoothBurstEasing,
        )
        ScrollingRow(
            covers = row3Covers,
            entranceProgress = entranceProgress,
            driftProgress = driftProgress,
            baseOffsetDp = -2400f,
            direction = 1f,
            entranceEasing = smoothBurstEasing,
        )
        ScrollingRow(
            covers = row4Covers,
            entranceProgress = entranceProgress,
            driftProgress = driftProgress,
            baseOffsetDp = -300f,
            direction = -1f,
            entranceEasing = smoothBurstEasing,
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
    entranceEasing: Easing,
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
                    val scrollProgress = (entranceProgress.value / 0.75f).coerceIn(0f, 1f)
                    val scrollEase = entranceEasing.transform(scrollProgress)
                    val translationDp =
                        baseOffsetDp +
                            direction * (600f * scrollEase) +
                            direction * (CoverLoopPeriodDp.toFloat() * driftProgress.value)
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
