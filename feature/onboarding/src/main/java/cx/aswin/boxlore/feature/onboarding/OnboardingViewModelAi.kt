package cx.aswin.boxlore.feature.onboarding

import android.util.Log
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRequest
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingNextTurnRequest
import cx.aswin.boxlore.core.network.model.OnboardingPart
import cx.aswin.boxlore.core.network.model.toPodcast
import cx.aswin.boxlore.core.network.session.ApiErrorCodes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun OnboardingViewModel.updateAiCustomInput(text: String) {
    _uiState.update { state ->
        state.copy(
            aiCustomInputText = text,
            aiSelectedOptions = if (text.isNotEmpty()) emptySet() else state.aiSelectedOptions,
        )
    }
}

internal fun OnboardingViewModel.toggleAiOption(option: String) {
    _uiState.update { state ->
        if (state.aiCustomInputText.isNotEmpty()) {
            return@update state
        }
        val currentSelected = state.aiSelectedOptions
        val newSelected =
            if (option in currentSelected) {
                currentSelected - option
            } else {
                currentSelected + option
            }
        state.copy(
            aiSelectedOptions = newSelected,
        )
    }
}

internal fun OnboardingViewModel.switchToLegacyOnboarding() {
    cancelEchoRequests()
    didSwitchFromAi = true
    _uiState.update { it.copy(currentStep = OnboardingStep.GENRES, onboardingError = null) }
    currentStepStartMs = System.currentTimeMillis()
}

internal fun OnboardingViewModel.cancelEchoRequests() {
    val pendingQuestion = echoRequests.cancel()
    lastFailedAction = null
    _uiState.update { EchoRequestPolicy.rollback(it, pendingQuestion) }
    dropStaleShowDeck()
}

internal fun OnboardingViewModel.retryLastAction() {
    val action = lastFailedAction
    if (action != null) {
        _uiState.update { it.copy(onboardingError = null) }
        action()
    }
}

internal fun OnboardingViewModel.sendAiTurnInput() {
    val currentState = _uiState.value
    if (currentState.isAiLoading || currentState.isSynthesizing || echoRequests.isRunning) return
    val turnInput = GuidedOnboardingLogic.submittedAnswer(currentState)

    if (turnInput.isBlank()) return
    if (EchoDeckLogic.shouldShow(currentState.aiDeck)) {
        // The answer waits on screen while Echo asks about a few shows; it goes out after the last one.
        _uiState.update { it.copy(aiDeck = EchoDeckLogic.open(it.aiDeck, EchoDeckNext.Send)) }
        return
    }
    if (waitForShowDeck { sendAiTurnInput() }) return

    val turnTime = (System.currentTimeMillis() - turnStartMs) / 1000f
    AnalyticsHelper.trackOnboardingAiTurnSubmitted(
        turnNumber = currentState.aiCurrentTurn,
        selectedOptions = currentState.aiSelectedOptions,
        customInputText = currentState.aiCustomInputText,
        timeSpentSeconds = turnTime,
    )

    val userEntry =
        OnboardingHistoryEntry(
            role = "user",
            parts = listOf(OnboardingPart(text = turnInput)),
        )
    val newHistory = currentState.aiHistory + userEntry
    val reactions = EchoDeckLogic.requestReactions(currentState.aiDeck)

    echoRequests.pendingQuestion = EchoQuestionSnapshot.from(currentState)
    _uiState.update {
        it.copy(
            aiHistory = newHistory,
            aiSelectedOptions = emptySet(),
            aiCustomInputText = "",
            aiSearchSuggestion = null,
            isAiLoading = true,
            aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE,
            onboardingError = null,
        )
    }

    val finalAction: () -> Unit = action@{
        if (echoRequests.isRunning) return@action
        _uiState.update {
            it.copy(
                isAiLoading = true,
                aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE,
                onboardingError = null,
            )
        }
        echoRequests.turn = viewModelScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                val testInput = turnInput.trim().lowercase()
                if (testInput == "test_fail" || testInput == "test_error") {
                    throw java.io.IOException("Simulated network failure")
                } else if (testInput == "test_timeout") {
                    delay(50000)
                }

                val locale = discoveryLocaleForRegion(currentState.currentRegion)
                val response =
                    kotlinx.coroutines.withTimeout(45000) {
                        withContext(Dispatchers.IO) {
                            podcastRepository.api
                                .getOnboardingNextTurn(
                                    publicKey = podcastRepository.publicKey,
                                    request = OnboardingNextTurnRequest(
                                        history = newHistory,
                                        responseFormat = "guided_v1",
                                        features = listOf(GuidedOnboardingLogic.FINISH_HINT_FEATURE, EchoDeckLogic.FEATURE),
                                        country = locale.country,
                                        languages = locale.languages,
                                        probeReactions = reactions,
                                    ),
                                ).execute()
                        }
                    }

                if (testInput == "test_delay" || testInput == "test_slow") {
                    val elapsed = System.currentTimeMillis() - startTime
                    val remainingDelay = 20000 - elapsed
                    if (remainingDelay > 0) {
                        delay(remainingDelay)
                    }
                }

                EchoRequestPolicy.ensureActive()
                if (response.isSuccessful && response.body() != null) {
                    val body = response.body()!!
                    val guided = body.guided?.also(GuidedOnboardingLogic::validate)

                    val durationSec = (System.currentTimeMillis() - startTime) / 1000f
                    val detectedIntent =
                        when {
                            body.searchSuggestion != null -> "named_show"
                            body.assistantMessage.startsWith("I can only help you discover podcasts") -> "guardrail_refusal"
                            body.assistantMessage.startsWith("Got it — you've been clear") -> "repetition_finish"
                            else -> guided?.intent
                        }
                    AnalyticsHelper.trackOnboardingAiResponseReceived(
                        turnNumber = currentState.aiCurrentTurn,
                        assistantMessage = body.assistantMessage,
                        optionsCount = body.options.size,
                        optionsList = body.options,
                        durationSeconds = durationSec,
                        detectedIntent = detectedIntent,
                    )

                    val assistantEntry =
                        OnboardingHistoryEntry(
                            role = "model",
                            parts = listOf(OnboardingPart(text = body.assistantMessage)),
                        )
                    // Back history represents accepted turns, never a failed or cancelled submission.
                    turnHistoryState.add(
                        OnboardingViewModel.TurnState(
                            assistantMessage = currentState.aiAssistantMessage,
                            options = currentState.aiOptions,
                            history = currentState.aiHistory,
                            guided = currentState.aiGuidedTurn,
                            searchSuggestion = currentState.aiSearchSuggestion,
                            turnNumber = currentState.aiCurrentTurn,
                        ),
                    )
                    echoRequests.pendingQuestion = null
                    if (GuidedOnboardingLogic.isReady(body)) {
                        _uiState.update { state ->
                            state.copy(
                                aiHistory = state.aiHistory + assistantEntry,
                                aiAssistantMessage = body.assistantMessage,
                                aiOptions = emptyList(),
                                aiGuidedTurn = guided,
                                aiCurrentTurn = state.aiCurrentTurn + 1,
                                isAiLoading = false,
                                aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                                onboardingError = null,
                            )
                        }
                        synthesizeAndBuildCurriculum(force = true)
                    } else {
                        _uiState.update { state ->
                            state.copy(
                                aiHistory = state.aiHistory + assistantEntry,
                                aiAssistantMessage = body.assistantMessage,
                                aiOptions = if (guided?.kind == "question") guided.choices else body.options,
                                aiGuidedTurn = guided,
                                aiSearchSuggestion = body.searchSuggestion,
                                aiCurrentTurn = state.aiCurrentTurn + 1,
                                isAiLoading = false,
                                aiLoadingStage = AiLoadingStage.IDLE,
                                onboardingError = null,
                            )
                        }
                        turnStartMs = System.currentTimeMillis()
                    }
                } else {
                    if (EchoRequestPolicy.picksTopicsAfter(ApiErrorCodes.of(response))) throw EchoAiUnavailableException()
                    throw Exception("Server returned error ${response.code()}")
                }
            } catch (e: Exception) {
                EchoRequestPolicy.ensureActive(e)
                if (e is EchoAiUnavailableException) {
                    // Echo is off for this install right now (no verification, or today's AI allowance
                    // is used). Retrying can't help, so the listener carries on with the topic picker.
                    Log.w("OnboardingViewModel", "Echo unavailable for this session; switching to topics")
                    switchToLegacyOnboarding()
                    return@launch
                }
                Log.e("OnboardingViewModel", "Error in sendAiTurnInput", e)
                val userFriendlyMsg =
                    when (e) {
                        is kotlinx.coroutines.TimeoutCancellationException -> "Our AI is taking a moment to catch its breath. Let's try that again."
                        else -> "We encountered a temporary hiccup. Let's try that again."
                    }
                _uiState.update { state ->
                    state.copy(
                        isAiLoading = false,
                        aiLoadingStage = AiLoadingStage.IDLE,
                        onboardingError = userFriendlyMsg,
                    )
                }
            }
        }
    }
    lastFailedAction = finalAction
    finalAction()
    requestShowDeckIfDue(newHistory)
}

internal fun OnboardingViewModel.synthesizeAndBuildCurriculum(force: Boolean = false) {
    val currentState = _uiState.value
    val alreadyWorking = currentState.isAiLoading || currentState.isSynthesizing || echoRequests.isRunning
    if (echoRequests.isSynthesizing || (alreadyWorking && !force)) return
    if (EchoDeckLogic.shouldShow(currentState.aiDeck)) {
        // A ready deck comes first; the build starts after its last card, reactions included.
        _uiState.update { it.copy(aiDeck = EchoDeckLogic.open(it.aiDeck, if (force) EchoDeckNext.BuildForced else EchoDeckNext.Build)) }
        return
    }
    if (waitForShowDeck { synthesizeAndBuildCurriculum(force) }) return
    val turnInput = GuidedOnboardingLogic.submittedAnswer(currentState)

    if (!force) {
        val turnTime = (System.currentTimeMillis() - turnStartMs) / 1000f
        AnalyticsHelper.trackOnboardingAiTurnSubmitted(
            turnNumber = currentState.aiCurrentTurn,
            selectedOptions = currentState.aiSelectedOptions,
            customInputText = currentState.aiCustomInputText,
            timeSpentSeconds = turnTime,
        )
    }

    val newHistoryList = currentState.aiHistory.toMutableList()
    if (turnInput.isNotBlank()) {
        newHistoryList.add(
            OnboardingHistoryEntry(
                role = "user",
                parts = listOf(OnboardingPart(text = turnInput)),
            ),
        )
    }
    if (newHistoryList.isEmpty()) {
        newHistoryList.add(
            OnboardingHistoryEntry(
                role = "user",
                parts = listOf(OnboardingPart(text = "Recommend some top topics like True Crime or Tech")),
            ),
        )
    }

    // Back from the loading suggestions restores this question, answer included.
    echoRequests.buildQuestion = EchoQuestionSnapshot.from(currentState).takeIf { currentState.aiGuidedTurn?.kind != "ready" }
    _uiState.update {
        OnboardingAiStart.buildingState(it).copy(
            aiHistory = newHistoryList,
            aiSelectedOptions = emptySet(),
            aiCustomInputText = "",
            isAiLoading = true,
            isSynthesizing = true,
            aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
            onboardingError = null,
        )
    }

    val finalAction: () -> Unit = action@{
        if (echoRequests.isSynthesizing || (!force && echoRequests.isRunning)) return@action
        synthesisStartMs = System.currentTimeMillis()
        _uiState.update {
            it.copy(
                isAiLoading = true,
                isSynthesizing = true,
                aiLoadingStage = AiLoadingStage.SYNTHESIZING_PREFERENCES,
                onboardingError = null,
            )
        }
        echoRequests.synthesis = viewModelScope.launch {
            val locale = discoveryLocaleForRegion(currentState.currentRegion)
            try {
                val startTime = System.currentTimeMillis()
                val hasSlow =
                    newHistoryList.any { h ->
                        h.role == "user" &&
                            h.parts.any { p ->
                                p.text
                                    .trim()
                                    .lowercase()
                                    .let { it == "test_slow" || it == "test_delay" }
                            }
                    }
                val hasFail =
                    newHistoryList.any { h ->
                        h.role == "user" &&
                            h.parts.any { p ->
                                p.text
                                    .trim()
                                    .lowercase()
                                    .let { it == "test_fail" || it == "test_error" }
                            }
                    }
                val hasTimeout =
                    newHistoryList.any { h ->
                        h.role == "user" &&
                            h.parts.any { p ->
                                p.text
                                    .trim()
                                    .lowercase()
                                    .let { it == "test_timeout" }
                            }
                    }

                if (hasFail) {
                    throw java.io.IOException("Simulated network failure")
                } else if (hasTimeout) {
                    delay(50000)
                }

                // Call Synthesize to get queries
                val queries =
                    kotlinx.coroutines.withTimeout(45000) {
                        val synthResponse =
                            withContext(Dispatchers.IO) {
                                podcastRepository.api
                                    .onboardingSynthesize(
                                        publicKey = podcastRepository.publicKey,
                                        request = OnboardingNextTurnRequest(
                                            history = newHistoryList,
                                            recommendationFormat = "discovery_v2",
                                            probeReactions = EchoDeckLogic.requestReactions(currentState.aiDeck),
                                        ),
                                    ).execute()
                            }
                        if (synthResponse.isSuccessful && synthResponse.body() != null) {
                            synthResponse.body()!!
                        } else {
                            throw Exception("Failed to synthesize preferences")
                        }
                    }

                EchoRequestPolicy.ensureActive()
                _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.FETCHING_CATALOGS) }

                // Call Curriculum with synthesized queries
                val rows =
                    kotlinx.coroutines.withTimeout(45000) {
                        val curriculumResponse =
                            withContext(Dispatchers.IO) {
                                podcastRepository.api
                                    .getOnboardingCurriculum(
                                        publicKey = podcastRepository.publicKey,
                                        request =
                                        OnboardingCurriculumRequest(
                                            queries = queries,
                                            country = locale.country,
                                            languages = locale.languages,
                                            recommendationFormat = "discovery_v2",
                                        ),
                                    ).execute()
                            }
                        if (curriculumResponse.isSuccessful && curriculumResponse.body() != null) {
                            curriculumResponse.body()!!.map {
                                it.copy(episodes = emptyList())
                            }
                        } else {
                            throw Exception("Failed to load curriculum from synthesis")
                        }
                    }

                EchoRequestPolicy.ensureActive()
                check(EchoRequestPolicy.hasRecommendations(rows)) { "No onboarding recommendations returned" }
                _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.ASSEMBLING_FEED) }

                if (hasSlow) {
                    val elapsed = System.currentTimeMillis() - startTime
                    val remainingDelay = 20000 - elapsed
                    if (remainingDelay > 0) {
                        delay(remainingDelay)
                    }
                }

                EchoRequestPolicy.ensureActive()

                val duration = (System.currentTimeMillis() - synthesisStartMs) / 1000f
                val uniquePodcastsCount = rows.flatMap { it.podcasts }.distinctBy { it.id }.size
                AnalyticsHelper.trackOnboardingAiSynthesisCompleted(
                    rowsCount = rows.size,
                    podcastsCount = uniquePodcastsCount,
                    durationSeconds = duration,
                )

                // Nothing is pre-selected; the shows the listener picked during the chat lead, selected.
                _uiState.update { state ->
                    state.copy(
                        aiHistory = newHistoryList,
                        aiCurriculumRows = rows,
                        aiPicks = state.selectedPodcasts.values.toList(),
                        aiCurrentTurn = 4,
                        isAiLoading = false,
                        isSynthesizing = false,
                        aiLoadingStage = AiLoadingStage.IDLE,
                        onboardingError = null,
                    )
                }
            } catch (e: Exception) {
                EchoRequestPolicy.ensureActive(e)
                Log.e("OnboardingViewModel", "Error in synthesizeAndBuildCurriculum", e)
                AnalyticsHelper.trackOnboardingAiSynthesisFailed(e.message ?: "Unknown error")
                try {
                    _uiState.update { it.copy(aiLoadingStage = AiLoadingStage.FETCHING_CATALOGS) }
                    val trending =
                        withContext(Dispatchers.IO) {
                            podcastRepository.getTrendingPodcasts(
                                country = locale.country,
                                limit = 10,
                            )
                        }
                    val dummyPodcastDtos =
                        trending.map { pod ->
                            OnboardingCurriculumPodcastDto(
                                id = pod.id.toLongOrNull() ?: 0L,
                                title = pod.title,
                                author = pod.artist,
                                image = pod.imageUrl,
                                artwork = pod.imageUrl,
                                categories = mapOf("1" to pod.genre),
                                description = pod.description,
                            )
                        }

                    val fallbackRow =
                        OnboardingCurriculumRowDto(
                            rowTitle = "Trending Hits",
                            podcasts = dummyPodcastDtos,
                            episodes = emptyList(),
                        )
                    EchoRequestPolicy.ensureActive()
                    check(EchoRequestPolicy.hasRecommendations(listOf(fallbackRow))) { "No fallback recommendations returned" }
                    _uiState.update { state ->
                        state.copy(
                            aiHistory = newHistoryList,
                            aiCurriculumRows = listOf(fallbackRow),
                            aiPicks = state.selectedPodcasts.values.toList(),
                            aiCurrentTurn = 4,
                            isAiLoading = false,
                            isSynthesizing = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            onboardingError = null,
                        )
                    }
                } catch (ex: Exception) {
                    EchoRequestPolicy.ensureActive(ex)
                    Log.e("OnboardingViewModel", "Error in fallback synthesis", ex)
                    val userFriendlyMsg =
                        when (ex) {
                            is kotlinx.coroutines.TimeoutCancellationException -> "Our AI is taking a moment to build your feed. Let's try that again."
                            else -> "We encountered a temporary hiccup. Let's try that again."
                        }
                    _uiState.update { state ->
                        state.copy(
                            isAiLoading = false,
                            isSynthesizing = false,
                            aiLoadingStage = AiLoadingStage.IDLE,
                            onboardingError = userFriendlyMsg,
                        )
                    }
                }
            }
        }
    }
    lastFailedAction = finalAction
    finalAction()
}

internal fun OnboardingViewModel.navigateToSuggestions() {
    _uiState.update {
        it.copy(
            currentStep = OnboardingStep.AI_SUGGESTIONS,
            reachedSuggestionsViaAiFlow = true,
            reachedSuggestionsViaSearchFlow = false,
            reachedSuggestionsViaOpmlFlow = false,
        )
    }
}

/**
 * Echo's Back from suggestions. While loading or after a failed build, the build is cancelled
 * and the question it was built from returns. With results, Echo shows them as ready.
 */
internal fun OnboardingViewModel.returnToEchoFromSuggestions() {
    val state = _uiState.value
    if (state.aiCurriculumRows.isEmpty()) {
        val question = echoRequests.buildQuestion
        cancelEchoRequests()
        echoRequests.buildQuestion = null
        _uiState.update { live ->
            (question?.restoreOnto(live) ?: live).copy(currentStep = OnboardingStep.AI_ONBOARDING, onboardingError = null)
        }
    } else {
        _uiState.update { it.copy(currentStep = OnboardingStep.AI_ONBOARDING) }
    }
    turnStartMs = System.currentTimeMillis()
}

/** Discards built suggestions and reopens the question they were built from. */
internal fun OnboardingViewModel.changeAiAnswers() {
    val question = echoRequests.buildQuestion
    cancelEchoRequests()
    echoRequests.buildQuestion = null
    _uiState.update { live -> OnboardingAiStart.withoutResults(question?.restoreOnto(live) ?: live) }
    if (question == null) {
        // Echo itself finished the conversation: step back to the last question it asked.
        navigateBackInAiOnboarding()
    } else {
        turnStartMs = System.currentTimeMillis()
    }
}

internal fun OnboardingViewModel.navigateBackFromSuggestions() {
    val state = _uiState.value
    if (state.reachedSuggestionsViaAiFlow) {
        returnToEchoFromSuggestions()
        return
    }
    val nextStep =
        when {
            state.reachedSuggestionsViaAiFlow -> OnboardingStep.AI_ONBOARDING
            state.reachedSuggestionsViaSearchFlow -> OnboardingStep.SEARCH
            state.reachedSuggestionsViaOpmlFlow -> OnboardingStep.WELCOME
            else -> OnboardingStep.LENGTH_PICKER
        }
    val clearSuggestionPayload =
        OnboardingSuggestionsPresentation.shouldClearSuggestionPayloadOnBack(nextStep)
    _uiState.update {
        if (clearSuggestionPayload) {
            OnboardingSuggestionsPresentation.withClearedSuggestionPayload(it, nextStep)
        } else {
            it.copy(currentStep = nextStep)
        }
    }
    if (nextStep == OnboardingStep.AI_ONBOARDING) {
        turnStartMs = System.currentTimeMillis()
    } else if (nextStep == OnboardingStep.LENGTH_PICKER) {
        currentStepStartMs = System.currentTimeMillis()
    }
}

internal fun OnboardingViewModel.navigateBackInAiOnboarding() {
    cancelEchoRequests()
    val currentState = _uiState.value
    if (currentState.aiCurrentTurn <= 1) {
        _uiState.update { it.copy(currentStep = OnboardingStep.WELCOME) }
    } else {
        if (turnHistoryState.isNotEmpty()) {
            val previousState = turnHistoryState.removeAt(turnHistoryState.size - 1)
            _uiState.update {
                it.copy(
                    aiAssistantMessage = previousState.assistantMessage,
                    aiOptions = previousState.options,
                    aiGuidedTurn = previousState.guided,
                    aiHistory = previousState.history,
                    aiCurrentTurn = previousState.turnNumber,
                    aiSelectedOptions = emptySet(),
                    aiCustomInputText = "",
                    aiSearchSuggestion = previousState.searchSuggestion,
                    aiLoadingStage = AiLoadingStage.IDLE,
                    isAiLoading = false,
                    isSynthesizing = false,
                    aiCurriculumRows = emptyList(),
                )
            }
        } else {
            val initial = OnboardingUiState()
            _uiState.update {
                it.copy(
                    aiAssistantMessage = initial.aiAssistantMessage,
                    aiOptions = initial.aiOptions,
                    aiCurrentTurn = 1,
                    aiHistory = emptyList(),
                    aiGuidedTurn = null,
                    aiLoadingStage = AiLoadingStage.IDLE,
                    aiSelectedOptions = emptySet(),
                    aiCustomInputText = "",
                    aiSearchSuggestion = null,
                    isAiLoading = false,
                    isSynthesizing = false,
                    aiCurriculumRows = emptyList(),
                )
            }
        }
        dropStaleShowDeck()
        turnStartMs = System.currentTimeMillis()
    }
}

internal fun OnboardingViewModel.finishAiOnboarding(onDone: () -> Unit) {
    if (_uiState.value.isCompleting) return
    _uiState.update { it.copy(isCompleting = true) }
    viewModelScope.launch {
        try {
            val state = _uiState.value
            val selectedIds = state.subscribedPodcastIds
            val rows = state.aiCurriculumRows
            val allCurriculumPodcasts = rows.flatMap { it.podcasts }.map { it.toPodcast() }.distinctBy { it.id }
            // Include shows picked during a search side-trip from the AI chat
            // (they live in selectedPodcasts but not in the curriculum rows).
            val podcastsToSubscribe =
                (allCurriculumPodcasts + state.selectedPodcasts.values)
                    .distinctBy { it.id }
                    .filter { it.id in selectedIds }
            for (podcast in podcastsToSubscribe) {
                subscribeAndIngest(podcast)
            }

            userPrefs.setRegion(state.currentRegion)
            boxcastPrefs.setOnboardingCompleted(true)

            val userGenres =
                state.aiHistory
                    .filter { it.role == "user" }
                    .flatMap { it.parts }
                    .map { it.text }
                    .toSet()
            boxcastPrefs.setUserGenres(userGenres)

            val subscribedTitles = selectedIds.map { seenPodcasts[it]?.title ?: it }
            // Analytics: Track onboarding completion depending on the flow that reached suggestions
            if (state.reachedSuggestionsViaAiFlow) {
                AnalyticsHelper.trackOnboardingAiDone(
                    totalSubscribedCount = selectedIds.size,
                    subscribedPodcastsList = subscribedTitles,
                    didScrollSuggestions = didScrollSuggestions,
                    totalOnboardingTimeSeconds = getTotalOnboardingTime(),
                    favoriteGenres = userGenres.toList(),
                    entryPoint = onboardingEntryPoint,
                )
            } else if (state.reachedSuggestionsViaSearchFlow) {
                AnalyticsHelper.trackOnboardingSearchDone(
                    entryPoint = searchEntryPoint,
                    totalSubscribedCount = selectedIds.size,
                    subscribedPodcastsList = subscribedTitles,
                    searchesPerformed = searchesPerformedCount,
                    timeSpentOnSearchSeconds = getSearchScreenTimeSpent(),
                    totalOnboardingTimeSeconds = getTotalOnboardingTime(),
                )
            } else if (state.reachedSuggestionsViaOpmlFlow) {
                // For OPML, onboarding_completed with method "import" is already tracked in MainActivity.kt
                // But we refresh/set the user properties to ensure intent and subscriptions are up to date.
                AnalyticsHelper.setOnboardingImportCompletedUserProperties(
                    initialPodcastsSubscribed = selectedIds.size,
                )
            } else {
                // Manual/Genre flow
                AnalyticsHelper.trackOnboardingManualDone(
                    totalSubscribedCount = selectedIds.size,
                    subscribedPodcastsList = subscribedTitles,
                    totalOnboardingTimeSeconds = getTotalOnboardingTime(),
                    didSwitchFromAi = didSwitchFromAi,
                    favoriteGenres = state.selectedGenres,
                )
            }

            onDone()
        } catch (e: Exception) {
            Log.e("OnboardingViewModel", "Error in finishAiOnboarding", e)
            _uiState.update { state ->
                state.copy(
                    isCompleting = false,
                    onboardingError = "We couldn't finish setup. Please try again.",
                )
            }
        }
    }
}

fun OnboardingViewModel.markOnboardingCompletedSilent(onDone: () -> Unit) {
    boxcastPrefs.setOnboardingCompleted(true)
    onDone()
}

/** The API refused Echo for this session even after any step-up (see [ApiErrorCodes.AI_UNAVAILABLE]). */
internal class EchoAiUnavailableException : Exception("Echo is unavailable for this session")
