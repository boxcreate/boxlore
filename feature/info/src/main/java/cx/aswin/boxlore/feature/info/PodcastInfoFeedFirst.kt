package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ShowPageFeedRead
import cx.aswin.boxlore.core.catalog.ShowPageFeedResult
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
 * After a read falls back to the server's list, this page stops reading the feed for search,
 * sorting and scrolling until it is opened again ([readsFeed]); pull to refresh still tries it.
 * Following the show hands the page to the show's own catalog refresh ([stop]).
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 * COMPAT(own-ids): show pages read the show's own feed. Permanent: never remove (MIGRATION.md §5).
 */
internal class PodcastInfoFeedFirst(
    private val source: Source,
    private val ui: Ui,
) {
    /** What the page reads through, so tests can script it ([sourceOf] in the app). */
    interface Source {
        suspend fun open(podcastId: String, meta: PodcastMeta): ShowPageFeedState?

        suspend fun read(podcastId: String, meta: PodcastMeta, scope: Scope, savedFeedUrl: String?): ShowPageFeedRead

        suspend fun page(podcastId: String, limit: Int, offset: Int, sort: String): PodcastRepository.EpisodePage

        fun online(): Boolean
    }

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

    /** The last read of this page fell back to the server's list. */
    @Volatile
    private var fellBack: Boolean = false

    /** Scrolling reached the end of the copy while offline: it waits for a connection. */
    @Volatile
    private var waitingAtCopyEnd: Boolean = false

    @Volatile
    private var archiveRead: Deferred<ShowPageFeedRead?>? = null

    fun isActive(id: String?): Boolean = id != null && id == podcastId

    /** True while [id]'s page reads its feed: it is feed-first and its last read didn't fall back. */
    fun readsFeed(id: String?): Boolean = isActive(id) && !fellBack

    /** Whether [podcastId]'s page reads the feed, and what it holds; null for any other source. */
    suspend fun open(podcastId: String, podcast: Podcast?): ShowPageFeedState? {
        val opened = source.open(podcastId, podcast?.let(ShowFeedLogic::metaOf) ?: PodcastMeta())
        this.podcastId = podcastId.takeIf { opened != null }
        fellBack = false
        waitingAtCopyEnd = false
        archiveRead = null
        if (opened == null) return null
        archiveComplete = opened.archiveComplete
        return opened
    }

    /** The listener followed the show: its own catalog refresh takes over, and this page reads no more. */
    fun stop() {
        podcastId = null
        archiveRead = null
    }

    /** The status line and the read a page starts with once its first page shows. */
    suspend fun begin(podcast: Podcast, sort: EpisodeSort, opened: ShowPageFeedState) {
        val scope = ShowFeedLogic.openingScope(sort, opened, podcast.type)
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
        val read = source.read(podcast.id, ShowFeedLogic.metaOf(podcast), scope, podcast.feedUrl)
        if (ui.currentPodcastId() != podcast.id) return null
        archiveComplete = read.archiveComplete
        when (read.result) {
            ShowPageFeedResult.FALLBACK -> fellBack = true
            ShowPageFeedResult.UPDATED, ShowPageFeedResult.UNCHANGED -> {
                fellBack = false
                waitingAtCopyEnd = false
            }
            ShowPageFeedResult.OFFLINE -> Unit
        }
        reload(podcast, extra)
        setStatus(podcast.id, ShowFeedLogic.statusAfter(read))
        return read
    }

    /**
     * Reads the rest of the feed before an action that needs every episode, when the copy holds
     * only the newest. The read runs in [scope], so a search the listener keeps typing into waits
     * on the same read instead of starting it again. Nothing is read once the page fell back: the
     * action uses the server's list until the page opens again. [atCopyEnd]: scrolling reached the
     * end of the copy, which then waits for a connection when the read finds the phone offline.
     */
    suspend fun completeArchive(scope: CoroutineScope, podcast: Podcast, extra: Int = 0, atCopyEnd: Boolean = false): ShowPageFeedRead? {
        if (archiveComplete || fellBack) return null
        val running = archiveRead?.takeIf { it.isActive } ?: scope.async { read(podcast, Scope.ARCHIVE, extra = extra) }.also { archiveRead = it }
        val read = running.await()
        if (atCopyEnd && read?.result == ShowPageFeedResult.OFFLINE) waitingAtCopyEnd = true
        return read
    }

    /**
     * True while scrolling past the copy waits for a connection: loading more does nothing until
     * the phone is online again, so the end of the list doesn't keep retrying.
     */
    fun holdsAtCopyEnd(): Boolean = waitingAtCopyEnd && !source.online()

    /**
     * Pages from the start until the list holds as many episodes as it showed, plus [extra], and
     * down to the episode that was last ([ShowFeedLogic.keepsLoading]), so newer episodes added
     * above never push the rows the listener was on out of the list.
     */
    suspend fun reload(podcast: Podcast, extra: Int = 0) {
        val state = ui.state()?.takeIf { it.podcast.id == podcast.id } ?: return
        val oldest = state.currentSort == EpisodeSort.OLDEST
        val pageSize = if (oldest) OLDEST_PAGE_SIZE else PAGE_SIZE
        val wanted = ShowFeedLogic.reloadCount(state.piEpisodes.size, pageSize, extra)
        val anchorId = state.piEpisodes.lastOrNull()?.id
        val episodes = mutableListOf<Episode>()
        var offset = 0
        var more = true
        var hasMore = true
        while (more && ShowFeedLogic.keepsLoading(episodes, wanted, anchorId)) {
            val page = source.page(podcast.id, minOf(MAX_PAGE, wanted - episodes.size).coerceAtLeast(MIN_PAGE), offset, if (oldest) "oldest" else "newest")
            episodes += page.episodes
            offset += page.sourceCount
            more = page.hasMore && page.sourceCount > 0
            hasMore = ShowFeedLogic.hasMoreAfterPage(page.hasMore, page.sourceCount, archivePending = readsFeed(podcast.id) && !archiveComplete)
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
        private const val MIN_PAGE = 20

        /** The app's [Source]: the repository's feed-first calls and the show page's episode list. */
        fun sourceOf(repository: PodcastRepository): Source = object : Source {
            override suspend fun open(podcastId: String, meta: PodcastMeta): ShowPageFeedState? = repository.openFeedShowPage(podcastId, meta)

            override suspend fun read(podcastId: String, meta: PodcastMeta, scope: Scope, savedFeedUrl: String?): ShowPageFeedRead =
                repository.readShowPageFeed(podcastId, meta, scope, savedFeedUrl)

            override suspend fun page(podcastId: String, limit: Int, offset: Int, sort: String): PodcastRepository.EpisodePage =
                repository.getShowPageEpisodes(podcastId, limit, offset, sort)

            override fun online(): Boolean = repository.feedFirst.online()
        }
    }
}
