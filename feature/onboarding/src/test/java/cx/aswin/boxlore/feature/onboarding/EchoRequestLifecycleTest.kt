package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingPart
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoRequestLifecycleTest {
    @Test fun `late provider result after exit cannot replace restored question or trigger fallback`() = runBlocking {
        val lifecycle = EchoRequestLifecycle()
        val provider = CompletableDeferred<Unit>()
        var question = "Restored question"
        var fallback = false
        val pending = launch(start = CoroutineStart.UNDISPATCHED) {
            try {
                // Simulate a blocking provider that finishes after navigation has cancelled its job.
                withContext(NonCancellable) { provider.await() }
                EchoRequestPolicy.ensureActive()
                question = "Late response"
            } catch (error: Exception) {
                EchoRequestPolicy.ensureActive(error)
                fallback = true
            }
        }
        lifecycle.turn = pending
        assertTrue(lifecycle.isRunning)
        lifecycle.cancel()
        provider.complete(Unit)
        pending.join()
        assertTrue(pending.isCancelled)
        assertEquals("Restored question", question)
        assertFalse(fallback)
        assertFalse(lifecycle.isRunning)
    }

    @Test fun `exiting cancels both turn and synthesis during handoff`() = runBlocking {
        val lifecycle = EchoRequestLifecycle()
        val turn = launch(start = CoroutineStart.UNDISPATCHED) { delay(Long.MAX_VALUE) }
        val synthesis = launch(start = CoroutineStart.UNDISPATCHED) { delay(Long.MAX_VALUE) }
        lifecycle.turn = turn
        lifecycle.synthesis = synthesis
        assertTrue(lifecycle.isRunning)
        assertTrue(lifecycle.isSynthesizing)
        lifecycle.cancel()
        turn.join()
        synthesis.join()
        assertTrue(turn.isCancelled && synthesis.isCancelled)
        assertFalse(lifecycle.isRunning)
    }

    @Test fun `ordinary cancellation bypasses recovery but genuine timeout remains recoverable`() = runBlocking {
        var cancelled = false
        try {
            EchoRequestPolicy.ensureActive(CancellationException("Exit"))
        } catch (_: CancellationException) {
            cancelled = true
        }
        assertTrue(cancelled)
        var timeoutRecovered = false
        try {
            withTimeout(1) { delay(Long.MAX_VALUE) }
        } catch (error: Exception) {
            EchoRequestPolicy.ensureActive(error)
            timeoutRecovered = true
        }
        assertTrue(timeoutRecovered)
    }

    @Test fun `back or search exit clears error and pending flags without resetting preferences`() {
        val state = OnboardingUiState(currentRegion = "in", aiCurrentTurn = 3, aiSelectedOptions = setOf("Calm"), isAiLoading = true, isSynthesizing = true, aiLoadingStage = AiLoadingStage.FETCHING_CATALOGS, onboardingError = "Offline")
        val restored = EchoRequestPolicy.clearPending(state)
        assertEquals(state.copy(isAiLoading = false, isSynthesizing = false, aiLoadingStage = AiLoadingStage.IDLE, onboardingError = null), restored)
    }

    @Test fun `empty primary and fallback catalogs cannot become a ready result`() {
        assertFalse(EchoRequestPolicy.hasRecommendations(emptyList()))
        assertFalse(EchoRequestPolicy.hasRecommendations(listOf(OnboardingCurriculumRowDto("Empty"))))
        assertTrue(EchoRequestPolicy.hasRecommendations(listOf(OnboardingCurriculumRowDto("Shows", podcasts = listOf(OnboardingCurriculumPodcastDto(42, title = "Science"))))))
    }

    @Test fun `pending first turn cancellation restores draft and initial identity for Search round trip`() {
        val initial = OnboardingUiState(aiCustomInputText = "Calm science stories")
        val pending = initial.copy(
            aiHistory = listOf(OnboardingHistoryEntry("user", listOf(OnboardingPart(initial.aiCustomInputText)))),
            aiCustomInputText = "",
            isAiLoading = true,
            onboardingError = "Offline",
            currentRegion = "in",
        )
        val lifecycle = EchoRequestLifecycle()
        lifecycle.pendingQuestion = EchoQuestionSnapshot.from(initial)
        val restored = EchoRequestPolicy.rollback(pending, lifecycle.cancel())
        assertTrue(restored.aiHistory.isEmpty())
        assertEquals(initial.aiCustomInputText, restored.aiCustomInputText)
        assertEquals(initial.aiOptions, restored.aiOptions)
        assertEquals(1, restored.aiCurrentTurn)
        assertEquals("in", restored.currentRegion)
        assertFalse(restored.isAiLoading)
        assertNull(restored.onboardingError)
        assertNull(lifecycle.pendingQuestion)
    }

    @Test fun `accepted turn has no rollback snapshot so synthesis cancellation preserves accepted history`() {
        val lifecycle = EchoRequestLifecycle()
        val accepted = OnboardingUiState(aiCurrentTurn = 3, aiHistory = listOf(OnboardingHistoryEntry("model", listOf(OnboardingPart("Ready")))), isSynthesizing = true)
        val restored = EchoRequestPolicy.rollback(accepted, lifecycle.cancel())
        assertEquals(accepted.aiHistory, restored.aiHistory)
        assertEquals(3, restored.aiCurrentTurn)
        assertFalse(restored.isSynthesizing)
    }

    @Test fun `a turn refused for this session hands over to the topic picker instead of Try again`() {
        // Step-up failed or off (401) and the daily AI allowance used up (429): retrying can't help.
        assertTrue(EchoRequestPolicy.picksTopicsAfter("verification_required"))
        assertTrue(EchoRequestPolicy.picksTopicsAfter("daily_ai_limit"))
        // Ordinary failures keep Try again; refused sessions were already replaced by the network layer.
        assertFalse(EchoRequestPolicy.picksTopicsAfter(null))
        assertFalse(EchoRequestPolicy.picksTopicsAfter("session_expired"))
        assertFalse(EchoRequestPolicy.picksTopicsAfter("Unauthorized"))
    }
}
