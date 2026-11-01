package cx.aswin.boxlore.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView

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
        val (stepName, stepIndex, flowType) =
            remember(step) {
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
            }
        TrackScreenView("onboarding_$stepName") {
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
                    alsoFoundResults = uiState.alsoFoundResults,
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
                    callbacks = EchoCallbacks(
                        onBack = {
                            if (isOnboardingCompleted && uiState.aiCurrentTurn <= 1) {
                                viewModel.cancelEchoRequests()
                                onBack()
                            } else {
                                viewModel.navigateBackInAiOnboarding()
                            }
                        },
                        onCancelRequest = viewModel::cancelEchoRequests,
                        onOptionToggle = viewModel::toggleAiOption,
                        onCustomInputChange = viewModel::updateAiCustomInput,
                        onContinue = {
                            if (GuidedOnboardingLogic.shouldBuildBeforeSending(uiState)) {
                                viewModel.synthesizeAndBuildCurriculum()
                            } else {
                                viewModel.sendAiTurnInput()
                            }
                        },
                        onRevealSuggestions = viewModel::navigateToSuggestions,
                        onRetry = viewModel::retryLastAction,
                        onPickTopics = viewModel::switchToLegacyOnboarding,
                        onFindShows = viewModel::synthesizeAndBuildCurriculum,
                        onChangeAnswers = viewModel::changeAiAnswers,
                        onSearch = viewModel::switchToSearchFromAi,
                        onDeckReaction = viewModel::reactToShow,
                        onDeckBack = viewModel::stepBackInDeck,
                    ),
                    deckClips = viewModel.deckClips,
                )
            }
            OnboardingStep.AI_SUGGESTIONS -> {
                AiSuggestionsScreen(
                    uiState = uiState,
                    callbacks = SuggestionsCallbacks(
                        onBack = viewModel::navigateBackFromSuggestions,
                        onToggle = viewModel::togglePodcastSubscription,
                        onSetShowsSelected = viewModel::setShowsSelected,
                        onRegionChange = viewModel::setRegion,
                        onRetry = viewModel::retryLastAction,
                        onFinish = { viewModel.finishAiOnboarding(onComplete) },
                        onScrolled = viewModel::onPodcastScreenScrolled,
                        onSeeSimilar = viewModel::chooseSimilarShows,
                        onGoHome = { viewModel.goHomeFromSimilar(onComplete) },
                    ),
                )
            }
        }
    }
}
