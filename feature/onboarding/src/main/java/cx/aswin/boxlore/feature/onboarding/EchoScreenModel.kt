package cx.aswin.boxlore.feature.onboarding

/**
 * What Echo's screen shows. Exactly one kind at a time; the layout areas never move. [Deck] asks
 * about one show at a time: its question rides in Echo's bubble and big buttons replace the footer.
 */
internal enum class EchoScreenKind { Asking, Writing, Waiting, Error, Recovery, Ready, Deck }

/** [FindShowsNow] is the optional, confirmed form of [FindShows]. */
internal enum class EchoAction { Continue, FindShows, FindShowsNow, SeeShows, TryAgain, PickTopics, TellMore, ChangeAnswers }

/** A quiet sentence beside the optional action, saying why it is there. */
internal enum class EchoFooterNote { QuestionsLeft }

/** Whether the quiet early finish is offered. The API's `prominent` counts as available. */
internal enum class EchoFinishLevel { Hidden, Available }

/**
 * [primary] is the one full-width filled button. [secondary] is a quiet text action above it,
 * optionally beside a [note]. While Echo works the footer stays where it was ([busy]): its actions
 * pause and the main button says Echo is thinking. Only the show deck hides it.
 */
internal data class EchoFooter(
    val primary: EchoAction,
    val primaryEnabled: Boolean = true,
    val secondary: EchoAction? = null,
    val secondaryEnabled: Boolean = true,
    val note: EchoFooterNote? = null,
    /** For [EchoFooterNote.QuestionsLeft]: the most questions Echo may still ask. */
    val questionsLeft: Int = 0,
    val visible: Boolean = true,
    val busy: Boolean = false,
)

internal object EchoScreenModel {
    const val BUILD_FEED_ACTION = "build_feed"
    const val CONTINUE_ACTION = "continue_preferences"

    /** Requests win over everything; an error always offers recovery before stale results. */
    fun kind(state: OnboardingUiState, writing: Boolean): EchoScreenKind = when {
        state.isAiLoading || state.isSynthesizing -> EchoScreenKind.Waiting
        state.onboardingError != null -> EchoScreenKind.Error
        // A deck held before the build shows even when Echo has said it has enough.
        state.aiDeck.current != null -> EchoScreenKind.Deck
        state.aiCurriculumRows.isNotEmpty() || state.aiGuidedTurn?.kind == "ready" -> EchoScreenKind.Ready
        writing -> EchoScreenKind.Writing
        state.aiGuidedTurn?.kind == "recovery" -> EchoScreenKind.Recovery
        else -> EchoScreenKind.Asking
    }

    /**
     * The API's hint wins; an older Worker without it falls back to four submitted answers. Even
     * when the API says Echo has enough (`prominent`), Continue stays the main button: finishing is
     * always the listener's quiet choice, and more answers only make the suggestions better.
     */
    fun finishLevel(state: OnboardingUiState): EchoFinishLevel = when (state.aiGuidedTurn?.finish) {
        "prominent", "available" -> EchoFinishLevel.Available
        "hidden" -> EchoFinishLevel.Hidden
        else -> if (GuidedOnboardingLogic.showBuildNudge(state)) EchoFinishLevel.Available else EchoFinishLevel.Hidden
    }

    fun footer(state: OnboardingUiState, kind: EchoScreenKind): EchoFooter = when (kind) {
        EchoScreenKind.Asking, EchoScreenKind.Writing -> {
            val answered = GuidedOnboardingLogic.hasAnswer(state, manual = kind == EchoScreenKind.Writing)
            when (finishLevel(state)) {
                EchoFinishLevel.Available ->
                    EchoFooter(
                        EchoAction.Continue,
                        primaryEnabled = answered,
                        secondary = EchoAction.FindShowsNow,
                        note = EchoFooterNote.QuestionsLeft,
                        questionsLeft = GuidedOnboardingLogic.questionsLeftAtMost(state),
                    )
                EchoFinishLevel.Hidden -> EchoFooter(EchoAction.Continue, primaryEnabled = answered)
            }
        }
        EchoScreenKind.Waiting -> busyFooter(null)
        // The deck's own buttons stand in for the footer.
        EchoScreenKind.Deck -> EchoFooter(EchoAction.Continue, primaryEnabled = false, visible = false)
        EchoScreenKind.Error -> EchoFooter(EchoAction.TryAgain, secondary = EchoAction.PickTopics)
        EchoScreenKind.Recovery -> {
            val actions = state.aiGuidedTurn?.actions.orEmpty().map { it.id }
            if (BUILD_FEED_ACTION in actions) {
                EchoFooter(EchoAction.FindShows, secondary = EchoAction.TellMore.takeIf { CONTINUE_ACTION in actions })
            } else {
                EchoFooter(EchoAction.TellMore)
            }
        }
        EchoScreenKind.Ready ->
            EchoFooter(if (state.aiCurriculumRows.isNotEmpty()) EchoAction.SeeShows else EchoAction.FindShows, secondary = EchoAction.ChangeAnswers)
    }

    /** The footer the listener last saw, frozen while Echo works, so nothing appears or vanishes. */
    fun busyFooter(previous: EchoFooter?): EchoFooter =
        (previous?.takeIf { it.visible } ?: EchoFooter(EchoAction.Continue)).copy(primaryEnabled = false, secondaryEnabled = false, busy = true)

    /**
     * Progress toward "enough to find your shows": each answer moves it, it never claims
     * completion early, and it fills once Echo says it has enough.
     */
    fun progress(state: OnboardingUiState, kind: EchoScreenKind): Float = when {
        kind == EchoScreenKind.Ready -> 1f
        else -> (GuidedOnboardingLogic.answeredTurns(state) / 5f).coerceIn(0.06f, 0.8f)
    }

    fun avatarPhase(state: OnboardingUiState, kind: EchoScreenKind): EchoOnboardingPhase = when (kind) {
        EchoScreenKind.Waiting -> if (state.isSynthesizing) EchoOnboardingPhase.Preparing else EchoOnboardingPhase.Thinking
        EchoScreenKind.Error -> EchoOnboardingPhase.Rest
        EchoScreenKind.Ready -> EchoOnboardingPhase.Ready
        EchoScreenKind.Deck -> EchoOnboardingPhase.Presenting
        else -> if (state.aiCurrentTurn <= 1 && state.aiHistory.isEmpty()) EchoOnboardingPhase.Welcome else EchoOnboardingPhase.Question
    }

    fun isOpeningTurn(state: OnboardingUiState): Boolean = state.aiCurrentTurn <= 1 && state.aiHistory.isEmpty()

    fun question(state: OnboardingUiState): String? = when {
        isOpeningTurn(state) -> null
        else -> state.aiGuidedTurn?.question ?: state.aiAssistantMessage.takeIf { state.aiGuidedTurn == null }
    }

    fun choices(state: OnboardingUiState): List<String> = GuidedOnboardingLogic.suggestedAnswers(state)
}
