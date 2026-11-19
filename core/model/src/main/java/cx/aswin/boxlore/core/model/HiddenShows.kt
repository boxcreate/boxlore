package cx.aswin.boxlore.core.model

/**
 * A show the listener hid everywhere boxlore suggests shows: recommendations, charts, curated rows,
 * Lore, similar episodes, Smart Queue and Android Auto. Explore's Find a show search still finds it, so it can be unhidden.
 *
 * [id] is the show it was hidden from. [aliasIds] are the other shows its listening counts under
 * ([Podcast.listenerShowIds]), so the Podcast Index copy of a hidden RSS show stays hidden too.
 * [title] and [imageUrl] name the show in the hidden list without a request; either may be blank.
 */
data class HiddenShow(
    val id: String,
    val title: String = "",
    val imageUrl: String = "",
    val aliasIds: Set<String> = emptySet(),
) {
    /** Every ID that names this show. */
    val matchIds: Set<String>
        get() = aliasIds + id

    companion object {
        fun of(podcast: Podcast): HiddenShow = HiddenShow(
            id = podcast.id,
            title = podcast.title,
            imageUrl = podcast.imageUrl,
            aliasIds = podcast.listenerShowIds - podcast.id,
        )
    }
}

/** Matching shows and episodes against hidden shows by every ID that names them. */
object HiddenShows {
    /** Every ID that hides a show: each hidden show's own ID and its aliases. */
    fun matchIds(shows: Collection<HiddenShow>): Set<String> = shows.flatMapTo(HashSet()) { it.matchIds }

    /** True when any ID [podcast]'s listening counts under is hidden. */
    fun hides(hiddenIds: Set<String>, podcast: Podcast): Boolean = hiddenIds.isNotEmpty() && podcast.listenerShowIds.any { it in hiddenIds }

    /** True when the show [showId] (an episode's `podcastId`) is hidden. */
    fun hidesShow(hiddenIds: Set<String>, showId: String?): Boolean = showId != null && showId in hiddenIds
}

/** These shows without the hidden ones ([HiddenShows.hides]). */
fun List<Podcast>.withoutHiddenShows(hiddenIds: Set<String>): List<Podcast> = if (hiddenIds.isEmpty()) this else filterNot { HiddenShows.hides(hiddenIds, it) }

/** These episodes without the ones from hidden shows ([HiddenShows.hidesShow]). */
fun List<Episode>.withoutHiddenShowEpisodes(hiddenIds: Set<String>): List<Episode> = if (hiddenIds.isEmpty()) this else filterNot { HiddenShows.hidesShow(hiddenIds, it.podcastId) }
