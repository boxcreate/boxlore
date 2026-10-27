package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ShowPageFeedRead
import cx.aswin.boxlore.core.catalog.ShowPageFeedState
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.logic.ShowFeedLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/**
 * The feed-first part of [PodcastInfoViewModel]: which show reads its feed, the reads (with the
 * status line) and the page reloads after them. The rows keep their IDs, so a reload grows or
 * switches the list (stored copy → feed → server's list) without moving the scroll position or
 * dropping a selection.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 * COMPAT(own-ids): show pages read the show's own feed. Permanent: never remove (MIGRATION.md §5).
 */
internal class PodcastInfoFeedFirst(
    private val repository: PodcastRepository,
    private val ui: Ui,
) {
    /** How the helper reads and writes the page it serves. */
    interface Ui {
        fun state(): PodcastInfoUiState.Success?

        fun set(state: PodcastInfoUiState.Success)

        fun currentPodcastId(): String

        fun setOffset(offset: Int)

        suspend fun remount(state: PodcastInfoUiState.Success, episodes: List<Episode>, hasMore: Boolean): PodcastInfoUiState.Success
    }

    /** The show whose page reads its feed; null when the page reads another source. */
    @Volatile
    var podcastId: String? = null
        private set

    @Volatile
    var archiveComplete: Boolean = false
        private set

    fun isActive(id: String?): Boolean = id != null && id == podcastId

    /** Whether [podcastId]'s page reads the feed, and what it holds; null for any other source. */
    suspend fun open(podcastId: String, podcast: Podcast?): ShowPageFeedState? {
        val opened = repository.openFeedShowPage(podcastId, podcast?.let(ShowFeedLogic::metaOf) ?: PodcastMeta())
        this.podcastId = podcastId.takeIf { opened != null }
        if (opened == null) return null
        archiveComplete = opened.archiveComplete
        return opened
    }

    /** The status line and the read a page starts with once its first page shows. */
    suspend fun begin(podcast: Podcast, sort: EpisodeSort, opened: ShowPageFeedState) {
        val scope = ShowFeedLogic.openingScope(sort, opened)
        if (!ShowFeedLogic.readsOnOpen(opened, scope)) {
            setStatus(podcast.id, ShowFeedLogic.openingStatus(opened))
            return
        }
        read(podcast, scope)
    }

    /**
     * Reads [podcast]'s feed [scope] deep, then reloads as many episodes as were showing plus
     * [extra]. [showStatus] false keeps the status line out of a pull-to-refresh, which has its own
     * indicator. Null when the page moved to another show meanwhile.
     */
    suspend fun read(podcast: Podcast, scope: Scope, showStatus: Boolean = true, extra: Int = 0): ShowPageFeedRead? {
        if (showStatus) setStatus(podcast.id, ShowFeedLogic.readingStatus(scope))
        val read = repository.readShowPageFeed(podcast.id, ShowFeedLogic.metaOf(podcast), scope, podcast.feedUrl)
        if (ui.currentPodcastId() != podcast.id) return null
        archiveComplete = read.archiveComplete
        reload(podcast, extra)
        setStatus(podcast.id, ShowFeedLogic.statusAfter(read.result))
        return read
    }

    /**
     * Reads the rest of the feed before an action that needs every episode, when the copy holds
     * only the newest. The read runs in [scope], so a search the listener keeps typing into waits
     * on the same read instead of starting it again.
     */
    suspend fun completeArchive(scope: CoroutineScope, podcast: Podcast, extra: Int = 0): ShowPageFeedRead? {
        if (archiveComplete) return null
        val running = archiveRead?.takeIf { it.isActive } ?: scope.async { read(podcast, Scope.ARCHIVE, extra = extra) }.also { archiveRead = it }
        return running.await()
    }

    @Volatile
    private var archiveRead: Deferred<ShowPageFeedRead?>? = null

    /** Pages from the start until the list holds as many episodes as it showed, plus [extra]. */
    suspend fun reload(podcast: Podcast, extra: Int = 0) {
        val state = ui.state()?.takeIf { it.podcast.id == podcast.id } ?: return
        val oldest = state.currentSort == EpisodeSort.OLDEST
        val pageSize = if (oldest) OLDEST_PAGE_SIZE else PAGE_SIZE
        val wanted = ShowFeedLogic.reloadCount(state.piEpisodes.size, pageSize, extra)
        val episodes = mutableListOf<Episode>()
        var offset = 0
        var hasMore = true
        while (episodes.size < wanted && hasMore) {
            val page = repository.getEpisodesPaginated(podcast.id, minOf(MAX_PAGE, wanted - episodes.size), offset, if (oldest) "oldest" else "newest")
            episodes += page.episodes
            offset += page.sourceCount
            hasMore = page.hasMore && page.sourceCount > 0
        }
        val latest = ui.state()?.takeIf { it.podcast.id == podcast.id && it.currentSort == state.currentSort } ?: return
        ui.setOffset(offset)
        ui.set(ui.remount(latest, ShowFeedLogic.withShow(episodes, latest.podcast), hasMore).copy(isLoadingMore = false))
    }

    private fun setStatus(podcastId: String, status: ShowFeedStatus) {
        val state = ui.state()?.takeIf { it.podcast.id == podcastId } ?: return
        if (state.feedStatus != status) ui.set(state.copy(feedStatus = status))
    }

    companion object {
        const val PAGE_SIZE = 20
        const val OLDEST_PAGE_SIZE = 50
        private const val MAX_PAGE = 100
    }
}
