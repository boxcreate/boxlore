package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.Episode

/**
 * State for the queue-sheet banner that appears when Tier 0 same-show continuation
 * was skipped because the playing episode arrived from a discovery/recommendation source.
 *
 * The banner lets the user override the skip and insert the next few episodes
 * from the same show into the queue.
 */
data class SameShowContinuationState(
    /** Whether the banner should be shown. */
    val visible: Boolean = false,
    /** The podcast title for the show whose continuation was skipped. */
    val podcastTitle: String = "",
    /** Number of available next episodes (up to 5). Zero means no banner. */
    val availableCount: Int = 0,
    /** Preloaded next episodes ready to insert (up to 5). */
    val nextEpisodes: List<Episode> = emptyList(),
) {
    companion object {
        val HIDDEN = SameShowContinuationState()

        /** Maximum number of same-show continuation episodes to offer. */
        const val MAX_CONTINUATION_OFFER = 5
    }
}
