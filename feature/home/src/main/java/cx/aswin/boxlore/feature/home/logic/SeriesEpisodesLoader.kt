package cx.aswin.boxlore.feature.home.logic

import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException

/**
 * Loads a show listened to in order from the listener's place onwards, in listening order, for
 * Home's selected-show list and Daily mix's next episode.
 *
 * A followed show's saved copy answers with the episodes from the place on, however deep in the show
 * it is. When that can't find the place (no saved copy yet, so only the server's newest episodes; the
 * place stored under another ID; or episodes released at the same moment, which the copy can cut
 * through), the show is read oldest first a page at a time until the place turns up, keeping only
 * the page it's on and the next. If every loaded episode after the place is finished, loading moves
 * on from the last one, so a long run of finished episodes can't end the list early.
 */
internal class SeriesEpisodesLoader(
    /** The show from [aroundId] on (the saved copy), or its newest episodes when it can't. */
    private val window: suspend (aroundId: String) -> List<Episode>,
    /** The show oldest first, from [offset]. */
    private val oldestPage: suspend (offset: Int) -> Page,
    /** One token per episode, shared by every ID it's stored under. */
    private val groupTokens: suspend (ids: Collection<String>) -> Map<String, String>,
    private val maxPages: Int = MAX_PAGES,
) {
    data class Page(
        val episodes: List<Episode>,
        val hasMore: Boolean,
        /** How far the next page's offset moves; feed extras merged into a page don't count. */
        val advance: Int = episodes.size,
    )

    data class Loaded(
        /** Listening order. */
        val episodes: List<Episode>,
        /** The place as an ID in [episodes]; null when there's no place or it wasn't found. */
        val placeId: String?,
    )

    suspend fun load(
        placeHistoryId: String?,
        completedIds: Set<String>,
    ): Loaded {
        var loaded = around(placeHistoryId)
        var moves = 0
        while (moves < MAX_MOVES && endsBeforeNext(loaded, completedIds)) {
            val last = loaded.episodes.last()
            val further = around(last.id)
            if (further.episodes.none { it.id != last.id }) break
            loaded = further
            moves++
        }
        return loaded
    }

    private fun endsBeforeNext(
        loaded: Loaded,
        completedIds: Set<String>,
    ): Boolean = loaded.episodes.size >= PAGE_SIZE &&
        HomeShowEpisodesLogic.ahead(loaded.episodes, loaded.placeId, completedIds).isEmpty()

    private suspend fun around(placeHistoryId: String?): Loaded {
        if (placeHistoryId == null) return fromStart()
        val fromWindow = HomeShowEpisodesLogic.listeningOrder(readWindow(placeHistoryId))
        val tokens = groupTokens(fromWindow.map { it.id } + placeHistoryId)
        val placeInWindow = idIn(placeHistoryId, fromWindow, tokens)
        if (placeInWindow != null && !sharesMomentWithPlace(fromWindow, placeInWindow)) {
            return Loaded(fromWindow, placeInWindow)
        }
        return scan(placeHistoryId)
            ?: placeInWindow?.let { Loaded(fromWindow, it) }
            ?: fromStart()
    }

    private suspend fun readWindow(aroundId: String): List<Episode> = try {
        window(aroundId)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        // The pages below can still find the place.
        emptyList()
    }

    private suspend fun fromStart(): Loaded = Loaded(HomeShowEpisodesLogic.listeningOrder(oldestPage(0).episodes), placeId = null)

    /** Reads the show oldest first until [placeHistoryId] turns up; null when it doesn't within [maxPages]. */
    private suspend fun scan(placeHistoryId: String): Loaded? {
        var offset = 0
        var previous: List<Episode> = emptyList()
        repeat(maxPages) {
            val page = oldestPage(offset)
            if (page.episodes.isEmpty()) return null
            val tokens = groupTokens(page.episodes.map { it.id } + placeHistoryId)
            val found = idIn(placeHistoryId, page.episodes, tokens)
            if (found != null) {
                val next = if (page.hasMore) oldestPage(offset + page.advance).episodes else emptyList()
                val episodes = HomeShowEpisodesLogic.listeningOrder(previous.takeLast(PREVIOUS_KEPT) + page.episodes + next)
                return Loaded(episodes, found)
            }
            if (!page.hasMore || page.advance <= 0) return null
            previous = page.episodes
            offset += page.advance
        }
        return null
    }

    /** True when another episode was published at the same moment as the place; the saved copy may have cut that group. */
    private fun sharesMomentWithPlace(
        episodes: List<Episode>,
        placeId: String,
    ): Boolean {
        val moment = episodes.firstOrNull { it.id == placeId }?.publishedDate ?: return false
        return episodes.count { it.publishedDate == moment } > 1
    }

    private fun idIn(
        id: String,
        episodes: List<Episode>,
        tokens: Map<String, String>,
    ): String? {
        val token = tokens[id] ?: id
        return episodes.firstOrNull { (tokens[it.id] ?: it.id) == token }?.id
    }

    companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 20
        private const val MAX_MOVES = 5
        private const val PREVIOUS_KEPT = 10
    }
}
