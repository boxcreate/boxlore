package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.GuidedOnboardingAction
import cx.aswin.boxlore.core.network.model.GuidedOnboardingTurn
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumPodcastDto
import cx.aswin.boxlore.core.network.model.OnboardingCurriculumRowDto
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingPart
import cx.aswin.boxlore.core.network.model.OnboardingProbeCard
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EchoScreenModelTest {
    private val choices = listOf("Optimistic wonder", "Calm contemplation", "Humorous lightness", "Serious analysis")
    private val rows = listOf(OnboardingCurriculumRowDto("Cold cases", listOf(OnboardingCurriculumPodcastDto(1, title = "Serial"))))

    private fun answers(count: Int) = (1..count).flatMap {
        listOf(OnboardingHistoryEntry("user", listOf(OnboardingPart("answer $it"))), OnboardingHistoryEntry("model", listOf(OnboardingPart("question"))))
    }

    private fun question(finish: String? = null, kind: String = "question", actions: List<String> = emptyList()) = OnboardingUiState(
        aiCurrentTurn = 3,
        aiHistory = answers(2),
        aiOptions = if (kind == "question") choices else emptyList(),
        aiGuidedTurn = GuidedOnboardingTurn(
            1,
            kind,
            "Bold choice.",
            "What tone do you enjoy?",
            if (kind == "question") choices else emptyList(),
            actions.map { GuidedOnboardingAction(it) },
            if (kind == "question") "preferences" else "frustration",
            finish,
        ),
    )

    @Test
    fun `requests win, then errors, then results, then the answer mode`() {
        val base = question()
        assertEquals(EchoScreenKind.Waiting, EchoScreenModel.kind(base.copy(isAiLoading = true, onboardingError = "x"), writing = true))
        assertEquals(EchoScreenKind.Waiting, EchoScreenModel.kind(base.copy(isSynthesizing = true), writing = false))
        assertEquals(EchoScreenKind.Error, EchoScreenModel.kind(base.copy(onboardingError = "x", aiCurriculumRows = rows), writing = false))
        assertEquals(EchoScreenKind.Ready, EchoScreenModel.kind(base.copy(aiCurriculumRows = rows), writing = true))
        assertEquals(EchoScreenKind.Ready, EchoScreenModel.kind(question(kind = "ready"), writing = false))
        assertEquals(EchoScreenKind.Writing, EchoScreenModel.kind(base, writing = true))
        assertEquals(EchoScreenKind.Recovery, EchoScreenModel.kind(question(kind = "recovery", actions = listOf("build_feed")), writing = false))
        assertEquals(EchoScreenKind.Writing, EchoScreenModel.kind(question(kind = "recovery", actions = listOf("continue_preferences")), writing = true))
        assertEquals(EchoScreenKind.Asking, EchoScreenModel.kind(base, writing = false))
    }

    @Test
    fun `continue needs an answer and the finish hint decides where Find my shows goes`() {
        val hidden = EchoScreenModel.footer(question("hidden"), EchoScreenKind.Asking)
        assertEquals(EchoFooter(EchoAction.Continue, primaryEnabled = false), hidden)
        val answered = question("hidden").copy(aiSelectedOptions = setOf(choices[0]))
        assertTrue(EchoScreenModel.footer(answered, EchoScreenKind.Asking).primaryEnabled)

        val available = EchoScreenModel.footer(question("available"), EchoScreenKind.Asking)
        assertEquals(EchoAction.Continue, available.primary)
        assertEquals(EchoAction.FindShowsNow, available.secondary)
        assertEquals(EchoFooterNote.QuestionsLeft, available.note)
        // Two answers so far: up to five more, counting the one on screen.
        assertEquals(5, available.questionsLeft)

        // Even when the API says Echo has enough, Continue leads and finishing stays the quiet pill.
        assertEquals(available, EchoScreenModel.footer(question("prominent"), EchoScreenKind.Asking))

        val written = question("hidden").copy(aiCustomInputText = "Long walks and space")
        assertTrue(EchoScreenModel.footer(written, EchoScreenKind.Writing).primaryEnabled)
        assertFalse(EchoScreenModel.footer(written.copy(aiCustomInputText = "  "), EchoScreenKind.Writing).primaryEnabled)
    }

    @Test
    fun `questions left is a ceiling that counts down and never drops below one`() {
        assertEquals(7, GuidedOnboardingLogic.questionsLeftAtMost(OnboardingUiState()))
        assertEquals(3, GuidedOnboardingLogic.questionsLeftAtMost(question().copy(aiHistory = answers(4))))
        assertEquals(1, GuidedOnboardingLogic.questionsLeftAtMost(question().copy(aiHistory = answers(6))))
        assertEquals(1, GuidedOnboardingLogic.questionsLeftAtMost(question().copy(aiHistory = answers(9))))
    }

    @Test
    fun `an older Worker without the hint keeps the four answer rule`() {
        val three = question().copy(aiHistory = answers(3))
        val four = question().copy(aiHistory = answers(4))
        assertEquals(EchoFinishLevel.Hidden, EchoScreenModel.finishLevel(three))
        assertEquals(EchoFinishLevel.Available, EchoScreenModel.finishLevel(four))
        assertEquals(EchoFinishLevel.Hidden, EchoScreenModel.finishLevel(question("hidden").copy(aiHistory = answers(4))))
    }

    @Test
    fun `errors, recovery, results and waiting have their own buttons`() {
        assertEquals(EchoFooter(EchoAction.TryAgain, secondary = EchoAction.PickTopics), EchoScreenModel.footer(question(), EchoScreenKind.Error))
        assertEquals(
            EchoFooter(EchoAction.FindShows, secondary = EchoAction.TellMore),
            EchoScreenModel.footer(question(kind = "recovery", actions = listOf("build_feed", "continue_preferences")), EchoScreenKind.Recovery),
        )
        assertEquals(EchoFooter(EchoAction.FindShows), EchoScreenModel.footer(question(kind = "recovery", actions = listOf("build_feed")), EchoScreenKind.Recovery))
        assertEquals(EchoFooter(EchoAction.TellMore), EchoScreenModel.footer(question(kind = "recovery", actions = listOf("continue_preferences")), EchoScreenKind.Recovery))
        assertEquals(EchoFooter(EchoAction.SeeShows, secondary = EchoAction.ChangeAnswers), EchoScreenModel.footer(question().copy(aiCurriculumRows = rows), EchoScreenKind.Ready))
        assertEquals(EchoFooter(EchoAction.FindShows, secondary = EchoAction.ChangeAnswers), EchoScreenModel.footer(question(kind = "ready"), EchoScreenKind.Ready))
        val waiting = EchoScreenModel.footer(question(), EchoScreenKind.Waiting)
        assertTrue(waiting.visible && waiting.busy)
        assertFalse(waiting.primaryEnabled)
        // Waiting freezes the footer the listener last saw instead of hiding it.
        val pill = EchoScreenModel.footer(question("available"), EchoScreenKind.Asking)
        assertEquals(pill.copy(primaryEnabled = false, secondaryEnabled = false, busy = true), EchoScreenModel.busyFooter(pill))
        assertFalse(EchoScreenModel.footer(question(), EchoScreenKind.Deck).visible)
    }

    @Test
    fun `progress moves with each answer and fills only when the shows are ready`() {
        assertEquals(0.06f, EchoScreenModel.progress(OnboardingUiState(), EchoScreenKind.Asking))
        assertEquals(0.4f, EchoScreenModel.progress(question(), EchoScreenKind.Asking), 0.001f)
        assertEquals(0.8f, EchoScreenModel.progress(question().copy(aiHistory = answers(6)), EchoScreenKind.Asking))
        assertEquals(0.4f, EchoScreenModel.progress(question("prominent"), EchoScreenKind.Asking), 0.001f)
        assertEquals(1f, EchoScreenModel.progress(question().copy(aiCurriculumRows = rows), EchoScreenKind.Ready))
    }

    @Test
    fun `the opening turn is app owned and later questions come from the API`() {
        assertTrue(EchoScreenModel.isOpeningTurn(OnboardingUiState()))
        assertNull(EchoScreenModel.question(OnboardingUiState()))
        assertEquals("What tone do you enjoy?", EchoScreenModel.question(question()))
        val legacy = OnboardingUiState(aiCurrentTurn = 2, aiHistory = answers(1), aiAssistantMessage = "Legacy question?", aiOptions = choices)
        assertEquals("Legacy question?", EchoScreenModel.question(legacy))
        assertEquals(choices, EchoScreenModel.choices(legacy))
    }

    @Test
    fun `Echo's pose follows the screen`() {
        assertEquals(EchoOnboardingPhase.Welcome, EchoScreenModel.avatarPhase(OnboardingUiState(), EchoScreenKind.Asking))
        assertEquals(EchoOnboardingPhase.Question, EchoScreenModel.avatarPhase(question(), EchoScreenKind.Writing))
        assertEquals(EchoOnboardingPhase.Thinking, EchoScreenModel.avatarPhase(question().copy(isAiLoading = true), EchoScreenKind.Waiting))
        assertEquals(EchoOnboardingPhase.Rest, EchoScreenModel.avatarPhase(question(), EchoScreenKind.Error))
        assertEquals(EchoOnboardingPhase.Presenting, EchoScreenModel.avatarPhase(question(), EchoScreenKind.Ready))
        assertEquals(EchoOnboardingPhase.Presenting, EchoScreenModel.avatarPhase(question(), EchoScreenKind.Deck))
    }

    @Test
    fun `an open show deck replaces the answers and the footer, but a request still wins`() {
        val cards = listOf(1L, 2L).map { OnboardingProbeCard(it, "Show $it", hook = "Hook", question = "Q$it?") }
        val deck = question().copy(aiDeck = EchoDeck(EchoDeckStatus.Ready, cards, showing = true))
        assertEquals(EchoScreenKind.Deck, EchoScreenModel.kind(deck, writing = true))
        assertEquals(EchoScreenKind.Asking, EchoScreenModel.kind(deck.copy(aiDeck = deck.aiDeck.copy(showing = false)), writing = false))
        assertEquals(EchoScreenKind.Waiting, EchoScreenModel.kind(deck.copy(isAiLoading = true), writing = false))
        // Held before the build, the deck shows even after Echo says it has enough.
        assertEquals(EchoScreenKind.Deck, EchoScreenModel.kind(question(kind = "ready").copy(aiDeck = deck.aiDeck), writing = false))
        assertFalse(EchoScreenModel.footer(deck, EchoScreenKind.Deck).visible)
    }
}
