package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ShowPageSource
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.pageSizeFor
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.sortParamFor
import cx.aswin.boxlore.feature.info.logic.ShowFeedLogic
import cx.aswin.boxlore.feature.info.logic.podcastSearchResponseIsCurrent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/**
 * The show page's episode list after its first page: loading more, changing the sort, searching
 * inside the show, and the window range selection acts on. On a feed-first page (P4 PR 16) each
 * reads the feed's whole archive first when the stored copy can't answer.
 */
internal class PodcastInfoEpisodePaging(
    private val pageState: PodcastInfoPageState,
    private val repository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val supplementSupport: PodcastInfoSupplementSupport,
    private val feedFirst: PodcastInfoFeedFirst,
    private val scope: CoroutineScope,
) {
    private val uiState = pageState.uiState
    private var searchJob: Job? = null

    fun loadMore() {
        val currentState = uiState.value
        if (currentState !is PodcastInfoUiState.Success || !canLoadMore(currentState)) return

        uiState.value = currentState.copy(isLoadingMore = true)

        scope.launch {
            try {
                val limit = pageSizeFor(currentState.currentSort)
                val sortParam = sortParamFor(currentState.currentSort)
                val page = repository.getShowPageEpisodes(pageState.currentPodcastId, limit, pageState.currentOffset, sortParam)
                if (needsArchiveToLoadMore(limit, page.episodes.size)) {
                    // Past the stored newest episodes: read the whole feed, then show one more page.
                    feedFirst.completeArchive(scope, currentState.podcast, extra = limit, atCopyEnd = true)
                    pageState.stopLoadingMore()
                    return@launch
                }
                pageState.currentOffset += page.sourceCount
                val latest = uiState.value as? PodcastInfoUiState.Success ?: return@launch
                val nextPi = latest.piEpisodes + page.episodes
                uiState.value =
                    supplementSupport.remountWithSupplements(
                        state = latest,
                        piEpisodes = nextPi,
                        hasMoreEpisodes = page.hasMore,
                        isLoadingMore = false,
                    )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                uiState.value = currentState.copy(isLoadingMore = false)
            }
        }
    }

    /**
     * Whether another page may load: not already loading, more to come, not searching, and (for a
     * feed-first page, P4 PR 16) not past the stored copy while offline, which waits for a connection.
     */
    private fun canLoadMore(state: PodcastInfoUiState.Success): Boolean =
        !state.isLoadingMore &&
            state.hasMoreEpisodes &&
            state.searchResults == null &&
            !(feedFirst.readsFeed(pageState.currentPodcastId) && feedFirst.holdsAtCopyEnd())

    /** A feed-first page past its stored newest episodes reads the whole feed before the next page (P4 PR 16). */
    private fun needsArchiveToLoadMore(limit: Int, loaded: Int): Boolean =
        feedFirst.readsFeed(pageState.currentPodcastId) &&
            ShowFeedLogic.needsArchive(feedFirst.archiveComplete, ShowFeedLogic.ArchiveAction.LOAD_MORE, limit, loaded)

    fun toggleSort() {
        val currentState = uiState.value
        if (currentState !is PodcastInfoUiState.Success) return

        val newSort = if (currentState.currentSort == EpisodeSort.NEWEST) EpisodeSort.OLDEST else EpisodeSort.NEWEST
        pageState.currentOffset = 0

        uiState.value =
            currentState.copy(
                currentSort = newSort,
                episodes = emptyList(),
                piEpisodes = emptyList(),
                isLoadingMore = true,
                hasMoreEpisodes = true,
                searchQuery = "",
                searchResults = null,
            )

        scope.launch {
            // Persist sort preference for subscribed podcasts
            if (currentState.isSubscribed) {
                subscriptionRepository.updatePreferredSort(pageState.currentPodcastId, sortParamFor(newSort))
            }
            loadFirstSortedPage(currentState, newSort)
        }
    }

    /** The first page in [sort] after the sort changed; a feed-first page's oldest wait for the whole feed (P4 PR 16). */
    private suspend fun loadFirstSortedPage(currentState: PodcastInfoUiState.Success, sort: EpisodeSort) {
        try {
            if (sort == EpisodeSort.OLDEST &&
                feedFirst.readsFeed(pageState.currentPodcastId) &&
                ShowFeedLogic.needsArchive(feedFirst.archiveComplete, ShowFeedLogic.ArchiveAction.SORT_OLDEST)
            ) {
                // The oldest episodes are only known once the whole feed is read.
                feedFirst.completeArchive(scope, currentState.podcast)
                pageState.stopLoadingMore()
                return
            }
            val page = repository.getShowPageEpisodes(pageState.currentPodcastId, pageSizeFor(sort), 0, sortParamFor(sort))
            pageState.currentOffset = page.sourceCount
            val latestState = uiState.value as? PodcastInfoUiState.Success ?: return
            uiState.value =
                supplementSupport.remountWithSupplements(
                    state = latestState,
                    piEpisodes = page.episodes,
                    hasMoreEpisodes = page.hasMore,
                    isLoadingMore = false,
                )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            val latestState = uiState.value as? PodcastInfoUiState.Success ?: return
            uiState.value = latestState.copy(isLoadingMore = false)
        }
    }

    fun search(query: String) {
        val currentState = uiState.value
        if (currentState !is PodcastInfoUiState.Success) return

        searchJob?.cancel()
        searchJob = null
        uiState.value = currentState.copy(searchQuery = query)

        // Clear search
        if (query.isBlank()) {
            uiState.value =
                currentState.copy(
                    searchQuery = "",
                    searchResults = null,
                    isSearching = false,
                )
            return
        }

        // Debounce search
        searchJob =
            scope.launch {
                uiState.value = (uiState.value as? PodcastInfoUiState.Success)?.copy(isSearching = true) ?: return@launch
                delay(SEARCH_DEBOUNCE_MS)

                try {
                    // Correctly search for episodes within this feed using the repository
                    val podcastContext = (uiState.value as? PodcastInfoUiState.Success)
                    val feedId = podcastContext?.podcast?.id ?: return@launch
                    val podcast = podcastContext.podcast

                    val readsFeed = prepareFeedFirstSearch(feedId, podcast)
                    val networkResults = repository.searchEpisodes(feedId, query)
                    val results =
                        supplementSupport.unionSearch(
                            feedId = feedId,
                            query = query,
                            networkResults = ShowFeedLogic.withShow(networkResults, podcast),
                            meta =
                            PodcastListMeta(
                                title = podcast.title,
                                imageUrl = podcast.imageUrl,
                                genre = podcast.genre,
                                artist = podcast.artist,
                            ),
                            // The feed's own copy holds every episode the feed has: no extras to add.
                            // After a fallback the server's list gets them, as without the flag.
                            isRss = podcast.isRss || readsFeed,
                        )

                    // Ensure we are still in a valid state to update
                    currentCoroutineContext().ensureActive()
                    val latestState = uiState.value as? PodcastInfoUiState.Success ?: return@launch
                    if (!podcastSearchResponseIsCurrent(query, feedId, latestState.searchQuery, latestState.podcast.id)) return@launch
                    uiState.value =
                        latestState.copy(
                            searchResults = results,
                            isSearching = false,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    val latestState = uiState.value as? PodcastInfoUiState.Success ?: return@launch
                    if (!podcastSearchResponseIsCurrent(query, currentState.podcast.id, latestState.searchQuery, latestState.podcast.id)) return@launch
                    uiState.value = latestState.copy(isSearching = false, searchResults = emptyList())
                }
            }
    }

    /**
     * True when [feedId]'s search runs over the feed's own copy (P4 PR 16): the page reads its feed,
     * so a copy that holds only the newest is completed first. False once the page fell back: search
     * then runs over the server's list, with cached feed extras, and reads no feed.
     */
    private suspend fun prepareFeedFirstSearch(feedId: String, podcast: Podcast): Boolean {
        if (!feedFirst.readsFeed(feedId)) return false
        if (ShowFeedLogic.needsArchive(feedFirst.archiveComplete, ShowFeedLogic.ArchiveAction.SEARCH)) {
            feedFirst.completeArchive(scope, podcast)
        }
        return feedFirst.readsFeed(feedId) && repository.showPageSource(feedId) == ShowPageSource.FEED
    }

    /** The range-selection window ([loadPodcastEpisodeSelectionWindow]) for the page's show, in [sort] or the page's sort. */
    suspend fun selectionWindow(sort: EpisodeSort?): PodcastEpisodeSelectionWindow =
        loadPodcastEpisodeSelectionWindow(uiState.value as? PodcastInfoUiState.Success, sort) { limit, sortParam ->
            repository.getShowPageEpisodes(
                feedId = pageState.currentPodcastId,
                limit = limit,
                offset = 0,
                sort = sortParam,
            )
        }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 500L
    }
}
