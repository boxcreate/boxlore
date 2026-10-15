package cx.aswin.boxlore.feature.onboarding

import android.util.Log
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.network.model.OnboardingHistoryEntry
import cx.aswin.boxlore.core.network.model.OnboardingNextTurnRequest
import cx.aswin.boxlore.core.network.model.toPodcast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

/** The deck usually takes 4-10 s; past this the conversation simply carries on without it. */
private const val DeckTimeoutMillis = 25_000L

/** The longest a step waits for a deck still loading, behind the thinking state, before moving on without it. */
private const val DeckWaitMillis = 8_000L

/**
 * Continue or a feed build while the deck is still on its way waits for it, behind the usual
 * thinking placeholder, then carries on with [proceed]: into the deck if it came, straight on if
 * it came back empty or is still loading. Back cancels the wait like any request.
 */
internal fun OnboardingViewModel.waitForShowDeck(proceed: () -> Unit): Boolean {
    if (!EchoDeckLogic.shouldWait(_uiState.value.aiDeck)) return false
    _uiState.update { it.copy(isAiLoading = true, aiLoadingStage = AiLoadingStage.GENERATING_RESPONSE, onboardingError = null) }
    echoRequests.turn = viewModelScope.launch {
        withTimeoutOrNull(DeckWaitMillis) { _uiState.first { it.aiDeck.status != EchoDeckStatus.Loading } }
        echoRequests.turn = null
        _uiState.update { state ->
            val deck = if (state.aiDeck.status == EchoDeckStatus.Loading) state.aiDeck.copy(waitedOut = true) else state.aiDeck
            state.copy(aiDeck = deck, isAiLoading = false, aiLoadingStage = AiLoadingStage.IDLE)
        }
        proceed()
    }
    return true
}

/** Asks for the show deck in the background once [history] holds the second answer (and once more if that came back empty). */
internal fun OnboardingViewModel.requestShowDeckIfDue(history: List<OnboardingHistoryEntry>) {
    val state = _uiState.value
    val answers = history.count { it.role == "user" }
    if (!EchoDeckLogic.shouldRequest(state.aiDeck, answers)) return
    _uiState.update { it.copy(aiDeck = EchoDeckLogic.requested(it.aiDeck, answers)) }
    val locale = discoveryLocaleForRegion(state.currentRegion)
    echoRequests.deck = viewModelScope.launch {
        val cards = try {
            val response = withTimeout(DeckTimeoutMillis) {
                withContext(Dispatchers.IO) {
                    podcastRepository.api
                        .getOnboardingProbeDeck(
                            publicKey = podcastRepository.publicKey,
                            request = OnboardingNextTurnRequest(
                                history = history,
                                features = listOf(EchoDeckLogic.FEATURE),
                                country = locale.country,
                                languages = locale.languages,
                            ),
                        ).execute()
                }
            }
            response.body()?.cards.takeIf { response.isSuccessful }
        } catch (e: Exception) {
            EchoRequestPolicy.ensureActive(e)
            Log.w("OnboardingViewModel", "Show deck unavailable", e)
            null
        }
        _uiState.update { it.copy(aiDeck = if (cards == null) EchoDeckLogic.failed(it.aiDeck) else EchoDeckLogic.loaded(it.aiDeck, cards)) }
        // The deck is ready the moment its cards are; clips buffer behind it.
        val deck = _uiState.value.aiDeck
        if (deck.status == EchoDeckStatus.Ready) deckClips.prefetch(deck.cards.mapNotNull(EchoDeckLogic::clip))
    }
}

/**
 * Records the listener's answer to the card on screen. "Sounds good" and "I already listen" also
 * pick the show: it leads the suggestions, selected, and the listener can still untick it. After the last card, the step the deck held back goes ahead: the
 * answer goes out, or the feed is built.
 */
internal fun OnboardingViewModel.reactToShow(reaction: EchoDeckReaction) {
    val card = _uiState.value.aiDeck.current ?: return
    _uiState.update { state ->
        val deck = EchoDeckLogic.react(state.aiDeck, reaction)
        if (EchoDeckLogic.picks(reaction.wire)) {
            val id = card.id.toString()
            state.copy(aiDeck = deck, selectedPodcasts = state.selectedPodcasts + (id to card.toPodcast()), subscribedPodcastIds = state.subscribedPodcastIds + id)
        } else {
            state.copy(aiDeck = deck)
        }
    }
    val deck = _uiState.value.aiDeck
    if (!deck.showing) {
        deckClips.release()
        when (deck.next) {
            EchoDeckNext.Send -> sendAiTurnInput()
            EchoDeckNext.Build -> synthesizeAndBuildCurriculum()
            EchoDeckNext.BuildForced -> synthesizeAndBuildCurriculum(force = true)
        }
    }
}

/** Back on a card: the previous card returns with its answer undone, or the deck closes. */
internal fun OnboardingViewModel.stepBackInDeck() {
    _uiState.update { state ->
        val undone = state.aiDeck.reactions.lastOrNull()
        val deck = EchoDeckLogic.back(state.aiDeck)
        if (undone != null && EchoDeckLogic.picks(undone.reaction)) {
            val id = undone.id.toString()
            state.copy(aiDeck = deck, selectedPodcasts = state.selectedPodcasts - id, subscribedPodcastIds = state.subscribedPodcastIds - id)
        } else {
            state.copy(aiDeck = deck)
        }
    }
}

/** After a rewind, a deck built on answers that are gone is dropped (and its request stopped). */
internal fun OnboardingViewModel.dropStaleShowDeck() {
    val state = _uiState.value
    val deck = EchoDeckLogic.afterRewind(state.aiDeck, GuidedOnboardingLogic.answeredTurns(state))
    if (deck == state.aiDeck) return
    echoRequests.cancelDeck()
    deckClips.release()
    _uiState.update { it.copy(aiDeck = deck) }
}
