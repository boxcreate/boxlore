package cx.aswin.boxlore.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cx.aswin.boxlore.core.designsystem.analytics.TrackScreenView
import cx.aswin.boxlore.core.prefs.AppLanguage

/** The onboarding flow. [onAppLanguageChosen] applies a language picked in the first question and may recreate the activity. */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onComplete: () -> Unit,
    onBack: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onAppLanguageChosen: (AppLanguage) -> Unit = {},
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
            uiState.currentStep == OnboardingStep.AI_ONBOARDING ||
            uiState.currentStep == OnboardingStep.APP_LANGUAGE

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
                OnboardingStep.APP_LANGUAGE -> {
                    // A root step: Back leaves the app, as on Welcome.
                }
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

    OnboardingStepTransition(uiState.currentStep, Modifier.fillMaxSize()) { step ->
        val (stepName, stepIndex, flowType) =
            remember(step) { onboardingStepAnalytics(step) }
        TrackScreenView("onboarding_$stepName") {
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackOnboardingStepViewed(
                stepName = stepName,
                flowType = flowType,
                stepIndex = stepIndex,
            )
        }
        when (step) {
            OnboardingStep.APP_LANGUAGE -> {
                LaunchedEffect(Unit) {
                    viewModel.onAppLanguageQuestionViewed()
                }
                // The question is gone once answered; the step changes with it.
                uiState.appLanguageQuestion?.let { question ->
                    AppLanguageQuestionContent(
                        question = question,
                        onChoose = { language, handoff ->
                            viewModel.chooseAppLanguage(language, handoff)
                            onAppLanguageChosen(language)
                        },
                    )
                }
            }
            OnboardingStep.WELCOME -> {
                LaunchedEffect(Unit) {
                    viewModel.onWelcomeScreenViewed()
                }
                WelcomeScreen(
                    onHelpMeFind = viewModel::startOnboarding,
                    onSearch = viewModel::navigateToSearch,
                    onSkip = { viewModel.skipOnboarding(onComplete) },
                    onImportClick = onImportClick,
                    handoff = WelcomeHandoff(uiState.openingHandoff, viewModel::finishOpeningHandoff),
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
                    searchFailed = uiState.searchFailed,
                    onRetrySearch = viewModel::retrySearch,
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

/**
 * One step at a time: the next step slides in from the end while the last one leaves. From the
 * language question to Welcome nothing slides: the two share their panel, so Welcome takes over from
 * the question's in place ([OpeningHandoff]) underneath, while the question's choices fade away on
 * top. A screen on its way out is hidden from accessibility services.
 */
@Composable
internal fun OnboardingStepTransition(step: OnboardingStep, modifier: Modifier = Modifier, content: @Composable (OnboardingStep) -> Unit) {
    AnimatedContent(
        targetState = step,
        modifier = modifier,
        transitionSpec = {
            if (initialState == OnboardingStep.APP_LANGUAGE && targetState == OnboardingStep.WELCOME) {
                (EnterTransition.None togetherWith fadeOut(tween(LanguageChoicesFadeMillis))).apply { targetContentZIndex = -1f }
            } else {
                (slideInHorizontally { it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -it } + fadeOut())
            }
        },
        label = "onboarding_step",
    ) { target ->
        val leaving = transition.targetState == EnterExitState.PostExit
        Box(if (leaving) Modifier.clearAndSetSemantics {} else Modifier, propagateMinConstraints = true) {
            content(target)
        }
    }
}

/** The language question's choices fade this fast as Welcome takes over underneath. */
private const val LanguageChoicesFadeMillis = 120

/** The name, index and flow type each onboarding step reports to analytics. */
internal fun onboardingStepAnalytics(step: OnboardingStep): Triple<String, Int, String> = when (step) {
    // Before Welcome, with its own index so funnels keyed on Welcome's 0 count Welcome only.
    OnboardingStep.APP_LANGUAGE -> Triple(OnboardingAppLanguage.STEP_NAME, -1, "welcome")
    OnboardingStep.WELCOME -> Triple("welcome", 0, "welcome")
    OnboardingStep.GENRES -> Triple("genres", 1, "manual_genre")
    OnboardingStep.SUB_GENRES -> Triple("sub_genres", 2, "manual_genre")
    OnboardingStep.ACTIVITY_PICKER -> Triple("activities", 3, "manual_genre")
    OnboardingStep.LENGTH_PICKER -> Triple("lengths", 4, "manual_genre")
    OnboardingStep.SEARCH -> Triple("search", 5, "search")
    OnboardingStep.AI_ONBOARDING -> Triple("ai_chat", 1, "ai_chat")
    OnboardingStep.AI_SUGGESTIONS -> Triple("ai_suggestions", 6, "ai_chat")
}
