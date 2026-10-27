package cx.aswin.boxlore.feature.info.logic

import cx.aswin.boxlore.core.catalog.ShowPageFeedResult
import cx.aswin.boxlore.core.catalog.ShowPageFeedState
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort.Scope
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.EpisodeSort
import cx.aswin.boxlore.feature.info.ShowFeedStatus

/**
 * Decisions of the feed-first show page, apart from the ViewModel so they can be tested alone:
 * how deep a read goes, what the status line says, and how pages join as the list grows.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page).
 * COMPAT(own-ids): show pages read the show's own feed. Permanent: never remove (MIGRATION.md §5).
 */
internal object ShowFeedLogic {
    /**
     * The read a page starts as it opens: the whole archive for an oldest-first page (serial shows
     * open oldest first) and for a feed that lists its oldest items first, else the newest items.
     */
    fun openingScope(sort: EpisodeSort, opened: ShowPageFeedState): Scope = if (sort == EpisodeSort.OLDEST || opened.oldestFirst) Scope.ARCHIVE else Scope.NEWEST

    /**
     * Whether opening reads the feed: always without a stored copy or once it is an hour old, and
     * for an archive read the copy doesn't hold yet.
     */
    fun readsOnOpen(opened: ShowPageFeedState, scope: Scope): Boolean = opened.recheckDue || (scope == Scope.ARCHIVE && !opened.archiveComplete)

    /** The status line while a read of [scope] runs. */
    fun readingStatus(scope: Scope): ShowFeedStatus = if (scope == Scope.ARCHIVE) ShowFeedStatus.COMPLETING_ARCHIVE else ShowFeedStatus.READING_NEWEST

    /** The status line once a read ended with [result]. */
    fun statusAfter(result: ShowPageFeedResult): ShowFeedStatus = when (result) {
        ShowPageFeedResult.UPDATED, ShowPageFeedResult.UNCHANGED -> ShowFeedStatus.NONE
        ShowPageFeedResult.OFFLINE -> ShowFeedStatus.OFFLINE
        ShowPageFeedResult.FALLBACK -> ShowFeedStatus.FALLBACK
    }

    /** The status line as a page opens without reading: the stored copy, or nothing yet. */
    fun openingStatus(opened: ShowPageFeedState): ShowFeedStatus = if (opened.cached) ShowFeedStatus.CACHED else ShowFeedStatus.NONE

    /**
     * Whether an action must wait for the whole archive: in-show search, the oldest-first sort, and
     * a page that ran past the stored newest items ([pageSize] asked, [pageCount] came back).
     */
    fun needsArchive(archiveComplete: Boolean, action: ArchiveAction, pageSize: Int = 0, pageCount: Int = 0): Boolean {
        if (archiveComplete) return false
        return when (action) {
            ArchiveAction.SEARCH, ArchiveAction.SORT_OLDEST -> true
            ArchiveAction.LOAD_MORE -> pageCount < pageSize
        }
    }

    /**
     * How many episodes a page reloaded after a read holds: as many as were showing (so the rows,
     * the scroll position and any selection stay), plus [extra] when the listener was scrolling on.
     */
    fun reloadCount(showing: Int, pageSize: Int, extra: Int = 0): Int = maxOf(showing + extra, pageSize)

    /** Stored-copy episodes with the show's details filled in where the copy has none. */
    fun withShow(episodes: List<Episode>, podcast: Podcast): List<Episode> = episodes.map { episode ->
        if (!episode.podcastTitle.isNullOrBlank() && !episode.podcastImageUrl.isNullOrBlank()) return@map episode
        episode.copy(
            podcastTitle = episode.podcastTitle?.takeIf(String::isNotBlank) ?: podcast.title,
            podcastImageUrl = episode.podcastImageUrl?.takeIf(String::isNotBlank) ?: podcast.imageUrl.takeIf(String::isNotBlank),
            podcastId = episode.podcastId ?: podcast.id,
            podcastGenre = episode.podcastGenre ?: podcast.genre,
            podcastArtist = episode.podcastArtist ?: podcast.artist,
        )
    }

    /** The show details stored-copy episodes are listed with. */
    fun metaOf(podcast: Podcast): PodcastMeta = PodcastMeta(
        title = podcast.title.takeIf(String::isNotBlank),
        imageUrl = podcast.imageUrl.takeIf(String::isNotBlank),
        genre = podcast.genre,
        artist = podcast.artist,
    )

    enum class ArchiveAction { SEARCH, SORT_OLDEST, LOAD_MORE }
}
