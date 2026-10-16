package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.session.ApiErrorCodes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** The turn-to-synthesis handoff may briefly own both jobs; exits cancel both. */
internal class EchoRequestLifecycle {
    var turn: Job? = null
    var synthesis: Job? = null
    var pendingQuestion: EchoQuestionSnapshot? = null

    /** The question a feed build started from; Back from loading suggestions restores it. */
    var buildQuestion: EchoQuestionSnapshot? = null

    /** The show deck request runs beside the turns; undoing a later answer keeps it. */
    var deck: Job? = null

    fun cancelDeck() {
        deck?.cancel()
        deck = null
    }

    val isRunning: Boolean get() = turn?.isActive == true || isSynthesizing
    val isSynthesizing: Boolean get() = synthesis?.isActive == true

    fun cancel(): EchoQuestionSnapshot? {
        val question = pendingQuestion
        turn?.cancel()
        synthesis?.cancel()
        turn = null
        synthesis = null
        pendingQuestion = null
        return question
    }
}

internal object EchoRequestPolicy {
    suspend fun ensureActive(cause: Exception? = null) {
        currentCoroutineContext().ensureActive()
        if (cause is CancellationException && cause !is TimeoutCancellationException) throw cause
    }

    fun clearPending(state: OnboardingUiState): OnboardingUiState =
        state.copy(isAiLoading = false, isSynthesizing = false, aiLoadingStage = AiLoadingStage.IDLE, onboardingError = null)

    fun rollback(state: OnboardingUiState, question: EchoQuestionSnapshot?): OnboardingUiState =
        clearPending(question?.restoreOnto(state) ?: state)

    fun hasRecommendations(rows: List<OnboardingCurriculumRowDto>): Boolean = rows.any { it.podcasts.isNotEmpty() }

    /**
     * Whether a failed Echo turn should hand over to the topic picker instead of offering Try again:
     * the API refused AI for this session ([ApiErrorCodes.AI_UNAVAILABLE]: the step-up failed or is
     * off, or today's AI allowance is used), so a retry can't succeed.
     */
    fun picksTopicsAfter(apiErrorCode: String?): Boolean = apiErrorCode in ApiErrorCodes.AI_UNAVAILABLE
}

/** Transient question fields only; catalog results and account preferences stay live. */
internal data class EchoQuestionSnapshot(
    val message: String,
    val turn: Int,
    val options: List<String>,
    val selected: Set<String>,
    val draft: String,
    val search: String?,
    val guided: GuidedOnboardingTurn?,
    val history: List<OnboardingHistoryEntry>,
) {
    fun restoreOnto(live: OnboardingUiState): OnboardingUiState =
        live.copy(
            aiAssistantMessage = message,
            aiCurrentTurn = turn,
            aiOptions = options,
            aiSelectedOptions = selected,
            aiCustomInputText = draft,
            aiSearchSuggestion = search,
            aiGuidedTurn = guided,
            aiHistory = history,
        )

    companion object {
        fun from(state: OnboardingUiState): EchoQuestionSnapshot =
            EchoQuestionSnapshot(
                message = state.aiAssistantMessage,
                turn = state.aiCurrentTurn,
                options = state.aiOptions,
                selected = state.aiSelectedOptions,
                draft = state.aiCustomInputText,
                search = state.aiSearchSuggestion,
                guided = state.aiGuidedTurn,
                history = state.aiHistory,
            )
    }
}
