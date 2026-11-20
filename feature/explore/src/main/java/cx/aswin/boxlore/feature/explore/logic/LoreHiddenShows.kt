package cx.aswin.boxlore.feature.explore.logic

import cx.aswin.boxlore.core.model.HiddenShows
import cx.aswin.boxlore.feature.explore.LearnCuriosityCard

/** The Lore deck without cards from shows the listener hid. */
internal object LoreHiddenShows {
    /** What the deck shows once [hiddenIds] change. */
    sealed interface Change {
        /** No shown card is from a hidden show. */
        data object None : Change

        /** The deck without them; [loadMore] when too few are left, as after a swipe. */
        data class Deck(val cards: List<LearnCuriosityCard>, val loadMore: Boolean) : Change

        /** Nothing is left and the deck has ended. */
        data object CaughtUp : Change
    }

    fun withoutHiddenShows(cards: List<LearnCuriosityCard>, hiddenIds: Set<String>): List<LearnCuriosityCard> =
        if (hiddenIds.isEmpty()) cards else cards.filterNot { isHidden(it, hiddenIds) }

    fun isHidden(card: LearnCuriosityCard, hiddenIds: Set<String>): Boolean =
        hiddenIds.isNotEmpty() && HiddenShows.hidesShow(hiddenIds, card.podcastId)

    fun change(cards: List<LearnCuriosityCard>, hiddenIds: Set<String>, endOfContent: Boolean): Change {
        val kept = withoutHiddenShows(cards, hiddenIds)
        return when {
            kept.size == cards.size -> Change.None
            kept.isEmpty() && endOfContent -> Change.CaughtUp
            else -> Change.Deck(kept, loadMore = kept.size < MIN_CARDS)
        }
    }

    /** Fewer cards than this and the deck loads its next page. */
    private const val MIN_CARDS = 3
}
