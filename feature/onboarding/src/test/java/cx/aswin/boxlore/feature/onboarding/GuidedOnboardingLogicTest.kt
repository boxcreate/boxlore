package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.GuidedOnboardingAction
import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingNextTurnResponse
import cx.aswin.boxlore.core.network.model.OnboardingPart
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class GuidedOnboardingLogicTest {
    private val question = GuidedOnboardingTurn(1, "question", "Short science stories, told solo.", "What tone do you enjoy?", listOf("Wonder", "Calm", "Humour", "Analysis"), intent = "preferences")

    @Test fun `build nudge counts four submitted answers rather than the question number`() {
        val history = (1..4).flatMap { turn -> listOf(OnboardingHistoryEntry("user", listOf(OnboardingPart("Answer $turn"))), OnboardingHistoryEntry("model", listOf(OnboardingPart("Next question")))) }
        assertFalse(GuidedOnboardingLogic.showBuildNudge(OnboardingUiState(aiCurrentTurn = 4, aiHistory = history.take(6))))
        assertTrue(GuidedOnboardingLogic.showBuildNudge(OnboardingUiState(aiCurrentTurn = 5, aiHistory = history)))
        assertTrue(GuidedOnboardingLogic.showBuildNudge(OnboardingUiState(aiCurrentTurn = 6, aiHistory = history)))
        assertEquals(4, GuidedOnboardingLogic.answeredTurns(OnboardingUiState(aiHistory = history)))
    }

    @Test fun `legacy responses still ask and finish with their original contract`() {
        assertFalse(GuidedOnboardingLogic.isReady(OnboardingNextTurnResponse("Question", listOf("Choice"))))
        assertTrue(GuidedOnboardingLogic.isReady(OnboardingNextTurnResponse("Ready")))
    }

    @Test fun `guided completion is explicit and malformed choices never finish`() {
        assertFalse(GuidedOnboardingLogic.isReady(OnboardingNextTurnResponse("Question", guided = question)))
        val ready = GuidedOnboardingTurn(1, "ready", question = null, intent = "explicit_finish")
        assertTrue(GuidedOnboardingLogic.isReady(OnboardingNextTurnResponse("Ready", guided = ready)))
        for (invalid in listOf(question.copy(choices = emptyList()), question.copy(choices = listOf("a", "a", "b", "c")), question.copy(question = ""), question.copy(version = 2), question.copy(response = "a".repeat(121)), question.copy(kind = "ready"))) {
            assertThrows(IllegalArgumentException::class.java) { GuidedOnboardingLogic.isReady(OnboardingNextTurnResponse("Invalid", guided = invalid)) }
        }
    }

    @Test fun `recovery commands are not answers or completion`() {
        val recovery = GuidedOnboardingTurn(1, "recovery", question = "Find shows now, or tell me more?", actions = listOf(GuidedOnboardingAction("build_feed"), GuidedOnboardingAction("continue_preferences")), intent = "repeated_answer")
        assertFalse(GuidedOnboardingLogic.isReady(OnboardingNextTurnResponse("Recovery", guided = recovery)))
        assertEquals(emptyList<String>(), GuidedOnboardingLogic.suggestedAnswers(OnboardingUiState(aiGuidedTurn = recovery)))
        assertFalse(GuidedOnboardingLogic.shouldBuildBeforeSending(OnboardingUiState(aiOptions = emptyList(), aiGuidedTurn = recovery)))
        assertFalse(GuidedOnboardingLogic.shouldBuildBeforeSending(OnboardingUiState(aiOptions = emptyList(), aiGuidedTurn = question)))
        assertTrue(GuidedOnboardingLogic.shouldBuildBeforeSending(OnboardingUiState(aiOptions = emptyList())))
        assertThrows(IllegalArgumentException::class.java) { GuidedOnboardingLogic.validate(recovery.copy(actions = listOf(GuidedOnboardingAction("unknown")))) }
    }

    @Test fun `only the active answer mode can enable continuation`() {
        val state = OnboardingUiState(aiSelectedOptions = setOf("Stories"), aiCustomInputText = " ")
        assertTrue(GuidedOnboardingLogic.hasAnswer(state, manual = false))
        assertFalse(GuidedOnboardingLogic.hasAnswer(state, manual = true))
        assertTrue(GuidedOnboardingLogic.hasAnswer(state.copy(aiCustomInputText = "Something else"), manual = true))
    }

    @Test fun `guided labels replace legacy answers without splitting the message`() {
        assertEquals(question.choices, GuidedOnboardingLogic.suggestedAnswers(OnboardingUiState(aiGuidedTurn = question)))
        GuidedOnboardingLogic.validate(question.copy(response = null, question = "どんな雰囲気が好きですか？"))
    }
}
