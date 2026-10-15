package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingNextTurnResponse

/** A malformed guided answer must never accidentally start feed construction. */
internal object GuidedOnboardingLogic {
    /** Opt-in that makes the API send `guided.finish` without enabling the show deck. */
    const val FINISH_HINT_FEATURE = "finish_hint"

    /** The API's cap on listener answers; the last one goes straight to building. */
    const val MAX_ANSWERS = 7

    fun validate(turn: GuidedOnboardingTurn) {
        require(turn.version == 1)
        val response = turn.response
        require(response == null || (response.isNotBlank() && response.codePointCount(0, response.length) <= 120))
        require(turn.choices.all { it.isNotBlank() && it.codePointCount(0, it.length) <= 80 })
        require(turn.choices.map { it.trim().lowercase() }.distinct().size == turn.choices.size)
        when (turn.kind) {
            "question" -> {
                requireQuestion(turn)
                require(turn.choices.size == 4 && turn.actions.isEmpty())
                require(turn.intent in setOf("preferences", "named_show", "guardrail_refusal", "fallback"))
            }
            "ready" -> {
                require(turn.question == null && turn.choices.isEmpty() && turn.actions.isEmpty())
                require(turn.intent in setOf("enough_context", "explicit_finish", "max_turns"))
            }
            "recovery" -> {
                requireQuestion(turn)
                require(turn.choices.isEmpty() && turn.actions.isNotEmpty())
                require(turn.actions.map { it.id }.distinct().size == turn.actions.size)
                require(turn.actions.all { it.id in setOf("build_feed", "continue_preferences") })
                require(turn.intent in setOf("repeated_answer", "frustration"))
            }
            else -> error("Unsupported guided onboarding state")
        }
    }

    fun isReady(response: OnboardingNextTurnResponse): Boolean =
        response.guided?.let {
            validate(it)
            it.kind == "ready"
        } ?: response.options.isEmpty()

    fun suggestedAnswers(state: OnboardingUiState): List<String> = state.aiGuidedTurn?.choices ?: state.aiOptions

    fun submittedAnswer(state: OnboardingUiState): String =
        (
            state.aiSelectedOptions.toList() +
                if (state.aiCustomInputText.isNotBlank()) listOf(state.aiCustomInputText) else emptyList()
            ).joinToString(". ")

    fun answeredTurns(state: OnboardingUiState): Int = state.aiHistory.count { it.role == "user" }

    fun showBuildNudge(state: OnboardingUiState): Boolean = answeredTurns(state) >= 4

    /** Questions Echo can still ask, counting the one on screen; Echo often stops sooner. */
    fun questionsLeftAtMost(state: OnboardingUiState): Int = (MAX_ANSWERS - answeredTurns(state)).coerceAtLeast(1)

    fun hasAnswer(state: OnboardingUiState, manual: Boolean): Boolean =
        if (manual) state.aiCustomInputText.isNotBlank() else state.aiSelectedOptions.isNotEmpty()

    fun shouldBuildBeforeSending(state: OnboardingUiState): Boolean =
        state.aiCurrentTurn >= MAX_ANSWERS || (state.aiGuidedTurn?.let { it.kind == "ready" } ?: state.aiOptions.isEmpty())

    private fun requireQuestion(turn: GuidedOnboardingTurn) {
        val question = requireNotNull(turn.question)
        require(question.isNotBlank() && question.codePointCount(0, question.length) <= 180)
    }
}
