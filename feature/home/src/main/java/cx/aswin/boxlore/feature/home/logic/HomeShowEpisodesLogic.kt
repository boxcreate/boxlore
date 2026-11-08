package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.isTrailer
import cx.aswin.boxlore.feature.home.HomeListeningHistoryItem

/** What Home's list for one selected show is built from. */
internal data class ShowEpisodesPool(
    val podcastId: String,
    /** True for a show listened to in order (oldest first); false for newest first. */
    val inOrder: Boolean,
    /** In order: the show from around the listener's place, in listening order. Otherwise its newest episodes, newest first. */
    val episodes: List<Episode>,
    /** In order: the listener's place as an ID in [episodes]; null when they haven't started the show. */
    val placeId: String? = null,
    /** The show's newest episodes, newest first. */
    val newest: List<Episode> = episodes,
)

/** The rows Home shows for a selected show. */
internal data class ShowEpisodesList(
    val episodes: List<Episode>,
    /** Nothing is left to hear, so [episodes] are the show's latest episodes. */
    val caughtUp: Boolean = false,
    /** The show's two newest episodes, the only ones that can carry the NEW tag. */
    val newestIds: Set<String> = emptySet(),
)

/**
 * Which episodes Home lists for a selected show, and which one Daily mix suggests next for a show
 * listened to in order. Both read the listener's place the same way, so they never disagree.
 *
 * - Newest first: unfinished episodes among the newest [NEWEST_POOL_SIZE], newest at the top.
 * - In order: the episode the listener is in the middle of, then the next unfinished episodes after
 *   the furthest one they finished (replaying an early episode doesn't move the list back). Trailers
 *   are skipped.
 * - When nothing is left, the show's latest episodes, marked as caught up, instead of an empty list.
 */
internal object HomeShowEpisodesLogic {
    const val VISIBLE_ROWS = 8
    const val NEWEST_POOL_SIZE = 50
    const val NEWEST_FOR_IN_ORDER = 10
    private const val CONTEXT_BEFORE = 2
    private const val NEW_TAG_COUNT = 2

    /**
     * Listening order: publish date, with season then episode number deciding between episodes
     * published at the same moment (whole seasons released at once). Numbers only break ties, so a
     * feed that numbers some episodes and not others can't scramble the order.
     */
    private val listeningOrderComparator: Comparator<Episode> =
        compareBy<Episode> { it.publishedDate }
            .thenBy(nullsLast<Int>()) { it.seasonNumber }
            .thenBy(nullsLast<Int>()) { it.episodeNumber }

    fun listeningOrder(episodes: List<Episode>): List<Episode> = episodes.distinctBy { it.id }.sortedWith(listeningOrderComparator)

    /** The listener's place in a show listened to in order: the episode they're in the middle of, else the one they finished last. */
    fun placeHistoryId(
        history: List<HomeListeningHistoryItem>,
        showIds: Set<String>,
    ): String? = HomeSerialLogic.ongoingEpisodeId(history, showIds) ?: HomeSerialLogic.lastCompletedEpisodeId(history, showIds)

    /**
     * Where the next episodes start in [episodes] (listening order): the place, moved on past the
     * furthest finished episode in the list.
     */
    fun startIndex(
        episodes: List<Episode>,
        placeId: String?,
        completedIds: Set<String>,
    ): Int {
        val placeIndex = placeId?.let { id -> episodes.indexOfFirst { it.id == id } } ?: -1
        val furthestFinished = episodes.indexOfLast { it.id in completedIds }
        return maxOf(placeIndex, furthestFinished + 1, 0)
    }

    /** The unfinished, non-trailer episodes from the start of the next episodes on. */
    fun ahead(
        episodes: List<Episode>,
        placeId: String?,
        completedIds: Set<String>,
    ): List<Episode> = episodes
        .drop(startIndex(episodes, placeId, completedIds))
        .filter { !it.isTrailer && it.id !in completedIds }

    /** The next episode Daily mix suggests in a show listened to in order: the first one ahead that isn't started. */
    fun nextUp(
        episodes: List<Episode>,
        placeId: String?,
        completedIds: Set<String>,
        inProgressIds: Set<String>,
    ): Episode? = ahead(episodes, placeId, completedIds).firstOrNull { it.id !in inProgressIds }

    fun build(
        pool: ShowEpisodesPool,
        completedIds: Set<String>,
        hideCompleted: Boolean,
    ): ShowEpisodesList {
        val newestIds = pool.newest.take(NEW_TAG_COUNT).mapTo(HashSet()) { it.id }
        return if (pool.inOrder) {
            buildInOrder(pool, completedIds, hideCompleted, newestIds)
        } else {
            buildNewestFirst(pool.episodes, completedIds, hideCompleted, newestIds)
        }
    }

    private fun buildNewestFirst(
        episodes: List<Episode>,
        completedIds: Set<String>,
        hideCompleted: Boolean,
        newestIds: Set<String>,
    ): ShowEpisodesList {
        val newest = episodes.distinctBy { it.id }
        val unfinished = newest.filter { it.id !in completedIds }
        if (unfinished.isEmpty()) return caughtUp(newest, newestIds)
        val shown = if (hideCompleted) unfinished else newest
        return ShowEpisodesList(shown.take(VISIBLE_ROWS), caughtUp = false, newestIds = newestIds)
    }

    private fun buildInOrder(
        pool: ShowEpisodesPool,
        completedIds: Set<String>,
        hideCompleted: Boolean,
        newestIds: Set<String>,
    ): ShowEpisodesList {
        val episodes = pool.episodes
        val ahead = ahead(episodes, pool.placeId, completedIds)
        if (ahead.isEmpty()) {
            return caughtUp(pool.newest.ifEmpty { episodes.asReversed() }, newestIds)
        }
        val start = startIndex(episodes, pool.placeId, completedIds)
        val placeIndex = pool.placeId?.let { id -> episodes.indexOfFirst { it.id == id } } ?: -1
        val shown =
            if (hideCompleted) {
                // A replayed episode left behind the furthest point still leads the list while it's unfinished.
                val resumed = episodes.getOrNull(placeIndex)?.takeIf { placeIndex < start && it.id !in completedIds }
                listOfNotNull(resumed) + ahead
            } else {
                val from = (start - CONTEXT_BEFORE).coerceAtLeast(0)
                val resumed = episodes.getOrNull(placeIndex)?.takeIf { placeIndex < from && it.id !in completedIds }
                listOfNotNull(resumed) + episodes.drop(from).filter { !it.isTrailer }
            }
        return ShowEpisodesList(shown.take(VISIBLE_ROWS), caughtUp = false, newestIds = newestIds)
    }

    private fun caughtUp(
        newestFirst: List<Episode>,
        newestIds: Set<String>,
    ): ShowEpisodesList {
        val latest = newestFirst.distinctBy { it.id }.take(VISIBLE_ROWS)
        return ShowEpisodesList(latest, caughtUp = latest.isNotEmpty(), newestIds = newestIds)
    }
}
