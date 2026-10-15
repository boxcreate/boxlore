package cx.aswin.boxlore.feature.onboarding

import cx.aswin.boxlore.core.network.model.OnboardingProbeCard
import cx.aswin.boxlore.core.network.model.OnboardingProbeReaction

/** How the listener answered a show card; [wire] is the API's `reaction`. */
internal enum class EchoDeckReaction(val wire: String) {
    Dislike("dislike"),
    Like("like"),
    Listen("listen"),
    Unknown("unknown"),
}

enum class EchoDeckStatus { Idle, Loading, Ready, Done }

/** What the held-back step is once the last card is answered: send the answer, or build the feed. */
enum class EchoDeckNext { Send, Build, BuildForced }

/**
 * Echo's show deck: two to four well-known shows, asked about one at a time with big buttons. It
 * is requested in the background once two answers are in, and shown the next time the listener
 * continues, before that answer goes out, so the reactions travel with it. One deck per conversation.
 */
data class EchoDeck(
    val status: EchoDeckStatus = EchoDeckStatus.Idle,
    val cards: List<OnboardingProbeCard> = emptyList(),
    val reactions: List<OnboardingProbeReaction> = emptyList(),
    val showing: Boolean = false,
    /** How many times the deck was asked for, and at how many answers the last time. */
    val attempts: Int = 0,
    val requestedAt: Int = 0,
    val next: EchoDeckNext = EchoDeckNext.Send,
    /** A wait for this deck already ran out; later steps no longer wait for it. */
    val waitedOut: Boolean = false,
) {
    /** The card on screen; its index is the number of reactions so far. */
    val current: OnboardingProbeCard? get() = if (showing) cards.getOrNull(reactions.size) else null
}

internal object EchoDeckLogic {
    /** Opt-in for the deck and for reactions on later turns. */
    const val FEATURE = "show_probe"
    const val MIN_ANSWERS = 2
    private const val MAX_CARDS = 4
    private const val MIN_CARDS = 2
    private const val MAX_REACTIONS = 12
    private const val MAX_ATTEMPTS = 2

    /**
     * First asked once two answers are in. An empty or failed deck (often because one of those
     * answers was off-topic, so the API counted fewer than two) is asked for once more after the
     * next answer; a deck the listener has seen is never asked for again.
     */
    fun shouldRequest(deck: EchoDeck, answers: Int): Boolean = when (deck.status) {
        EchoDeckStatus.Idle -> answers >= MIN_ANSWERS
        EchoDeckStatus.Done -> deck.cards.isEmpty() && deck.reactions.isEmpty() && deck.attempts < MAX_ATTEMPTS && answers > deck.requestedAt
        else -> false
    }

    fun requested(deck: EchoDeck, answers: Int): EchoDeck = EchoDeck(EchoDeckStatus.Loading, attempts = deck.attempts + 1, requestedAt = answers)

    /** A deck answered after a restart or rewind is stale and ignored. Fewer than two usable cards is no deck. */
    fun loaded(deck: EchoDeck, cards: List<OnboardingProbeCard>): EchoDeck {
        if (deck.status != EchoDeckStatus.Loading) return deck
        val usable = cards.filter { it.title.isNotBlank() && it.question.isNotBlank() && it.hook.isNotBlank() }.distinctBy { it.id }.take(MAX_CARDS)
        return deck.copy(status = if (usable.size < MIN_CARDS) EchoDeckStatus.Done else EchoDeckStatus.Ready, cards = if (usable.size < MIN_CARDS) emptyList() else usable)
    }

    fun failed(deck: EchoDeck): EchoDeck = if (deck.status == EchoDeckStatus.Loading) deck.copy(status = EchoDeckStatus.Done) else deck

    /**
     * A waiting deck opens instead of the next step: Continue's answer, or the feed build (Find my
     * shows, or Echo deciding it has enough). The deck takes 10 s or so, so it is often ready only
     * by the build, which is also where reactions count most.
     */
    fun shouldShow(deck: EchoDeck): Boolean = deck.status == EchoDeckStatus.Ready && !deck.showing && deck.reactions.size < deck.cards.size

    fun open(deck: EchoDeck, next: EchoDeckNext): EchoDeck = deck.copy(showing = true, next = next)

    /** A deck still on its way is worth a short wait, once; empty or late decks never hold the chat up again. */
    fun shouldWait(deck: EchoDeck): Boolean = deck.status == EchoDeckStatus.Loading && !deck.waitedOut

    fun react(deck: EchoDeck, reaction: EchoDeckReaction): EchoDeck {
        val card = deck.current ?: return deck
        val reactions = deck.reactions + OnboardingProbeReaction(card.id, card.title, reaction.wire)
        val finished = reactions.size >= deck.cards.size
        return deck.copy(reactions = reactions, showing = !finished, status = if (finished) EchoDeckStatus.Done else deck.status)
    }

    /** Back returns to the previous card, undoing its answer; from the first card it closes the deck. */
    fun back(deck: EchoDeck): EchoDeck =
        if (deck.reactions.isEmpty()) deck.copy(showing = false) else deck.copy(reactions = deck.reactions.dropLast(1))

    /** "Sounds good" and "I already listen" pick the show. */
    fun picks(reaction: String): Boolean = reaction == EchoDeckReaction.Like.wire || reaction == EchoDeckReaction.Listen.wire

    fun requestReactions(deck: EchoDeck): List<OnboardingProbeReaction>? = deck.reactions.takeLast(MAX_REACTIONS).takeIf { it.isNotEmpty() }

    /** Rewinding to before the second answer drops a deck built on it, unless the listener already answered some. */
    fun afterRewind(deck: EchoDeck, answers: Int): EchoDeck =
        if (answers < MIN_ANSWERS && deck.reactions.isEmpty() && !deck.showing) EchoDeck() else deck

    /** Where the clip starts; null when the card has no usable episode. */
    fun clip(card: OnboardingProbeCard): EchoClip? {
        val url = card.previewUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return null
        return EchoClip(card.id, url, (card.previewStartSeconds ?: 0).coerceAtLeast(0) * 1000L)
    }
}
