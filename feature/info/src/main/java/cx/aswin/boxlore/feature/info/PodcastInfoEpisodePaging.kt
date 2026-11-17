package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ShowPageSource
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.pageSizeFor
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.sortParamFor
import cx.aswin.boxlore.feature.info.logic.ShowFeedLogic
import cx.aswin.boxlore.feature.info.logic.podcastSearchResponseIsCurrent
import java.io.IOException
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
    private var loadMoreJob: Job? = null

    fun loadMore() {
        val currentState = uiState.value
        if (currentState !is PodcastInfoUiState.Success || !canLoadMore(currentState)) return

        uiState.value = currentState.copy(isLoadingMore = true)

        loadMoreJob = scope.launch {
            try {
                val limit = pageSizeFor(currentState.currentSort)
                val sortParam = sortParamFor(currentState.currentSort)
                val page = loadPageRetrying { repository.getShowPageEpisodes(pageState.currentPodcastId, limit, pageState.currentOffset, sortParam, list = pageState.listSchemes) }
                if (needsArchiveToLoadMore(limit, page.episodes.size)) {
                    // Past the stored newest episodes: read the whole feed, then show one more page.
                    feedFirst.completeArchive(scope, currentState.podcast, extra = limit, atCopyEnd = true)
                    pageState.stopLoadingMore()
                    return@launch
                }
                val latest = uiState.value as? PodcastInfoUiState.Success ?: return@launch
                // The sort changed while this page was retrying: its own first page replaces the list.
                if (latest.currentSort != currentState.currentSort) return@launch
                pageState.currentOffset += page.sourceCount
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
                val latest = uiState.value as? PodcastInfoUiState.Success ?: return@launch
                if (latest.currentSort != currentState.currentSort) return@launch
                // Not the end of the list: it says the page couldn't load and offers Try again.
                uiState.value = latest.copy(isLoadingMore = false, hasMoreEpisodes = true, pageLoadFailed = true)
            }
        }
    }

    /**
     * Try again after a page couldn't load ([PodcastInfoUiState.Success.pageLoadFailed]): the next
     * page, or the current sort's first page when the list has none yet.
     */
    fun retryFailedPage() {
        val state = uiState.value as? PodcastInfoUiState.Success ?: return
        if (!state.pageLoadFailed) return
        if (pageState.currentOffset == 0) {
            uiState.value = state.copy(pageLoadFailed = false, isLoadingMore = true, hasMoreEpisodes = true)
            loadMoreJob = scope.launch { loadFirstSortedPage(state, state.currentSort) }
        } else {
            uiState.value = state.copy(pageLoadFailed = false)
            loadMore()
        }
    }

    /**
     * Whether another page may load: not already loading, more to come, not searching, and (for a
     * feed-first page, P4 PR 16) not past the stored copy while offline, which waits for a connection.
     */
    private fun canLoadMore(state: PodcastInfoUiState.Success): Boolean =
        !state.isLoadingMore &&
            !state.pageLoadFailed &&
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
        // A page of the old order still loading or retrying has nothing to add to the new one.
        loadMoreJob?.cancel()

        uiState.value =
            currentState.copy(
                currentSort = newSort,
                episodes = emptyList(),
                piEpisodes = emptyList(),
                isLoadingMore = true,
                hasMoreEpisodes = true,
                pageLoadFailed = false,
                searchQuery = "",
                searchResults = null,
                searchFailed = false,
                searchedNewest = null,
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
            val page = loadPageRetrying { repository.getShowPageEpisodes(pageState.currentPodcastId, pageSizeFor(sort), 0, sortParamFor(sort), list = pageState.listSchemes) }
            val latestState = uiState.value as? PodcastInfoUiState.Success ?: return
            if (latestState.currentSort != sort) return
            pageState.currentOffset = page.sourceCount
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
            if (latestState.currentSort != sort) return
            // The list in the new order is empty, not finished: it says so and offers Try again.
            uiState.value = latestState.copy(isLoadingMore = false, hasMoreEpisodes = true, pageLoadFailed = true)
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
                    searchFailed = false,
                    searchedNewest = null,
                )
            return
        }

        // Debounce search
        searchJob =
            scope.launch {
                uiState.value = (uiState.value as? PodcastInfoUiState.Success)?.copy(isSearching = true, searchFailed = false) ?: return@launch
                delay(SEARCH_DEBOUNCE_MS)

                try {
                    // Correctly search for episodes within this feed using the repository
                    val podcastContext = (uiState.value as? PodcastInfoUiState.Success)
                    val feedId = podcastContext?.podcast?.id ?: return@launch
                    val podcast = podcastContext.podcast

                    val readsFeed = prepareFeedFirstSearch(feedId, podcast)
                    val search = repository.searchShowEpisodes(feedId, query)
                    val networkResults = search.episodes
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
                            searchFailed = false,
                            searchedNewest = search.searchedNewest,
                        )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    val latestState = uiState.value as? PodcastInfoUiState.Success ?: return@launch
                    if (!podcastSearchResponseIsCurrent(query, currentState.podcast.id, latestState.searchQuery, latestState.podcast.id)) return@launch
                    // Not "no matches": the episodes couldn't be read, so the list offers Try again.
                    uiState.value = latestState.copy(isSearching = false, searchResults = emptyList(), searchFailed = true, searchedNewest = null)
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
            // Offline, or the read failed: the copy holds only the newest items, so searching it would
            // pass off part of the show as all of it. Fail, so the page offers Try again.
            if (!ShowFeedLogic.selectionReadsWholeShow(feedFirst.readsFeed(feedId), feedFirst.archiveComplete)) {
                throw IOException("The show's feed could not be read in full")
            }
        }
        return feedFirst.readsFeed(feedId) && repository.showPageSource(feedId) == ShowPageSource.FEED
    }

    /**
     * Every episode of the page's show for range selection and Mark all
     * ([loadPodcastEpisodeSelectionWindow]), in [sort] or the page's sort, read with the repository's
     * whole-show read ([PodcastRepository.getWholeShowPage]): a page that can't be read throws rather
     * than ending the list early. A feed-first page whose copy holds only the newest items reads the
     * rest of the feed first, as search does, so the selection reaches the show's first episode;
     * that read runs in [archiveScope] (Mark all passes its own, so leaving the page doesn't stop it).
     */
    suspend fun selectionWindow(sort: EpisodeSort?, archiveScope: CoroutineScope = scope): PodcastEpisodeSelectionWindow {
        val feedId = pageState.currentPodcastId
        val state = uiState.value as? PodcastInfoUiState.Success
        val needsArchive = ShowFeedLogic.needsArchive(feedFirst.archiveComplete, ShowFeedLogic.ArchiveAction.SELECT_RANGE)
        if (state != null && needsArchive && feedFirst.readsFeed(feedId)) {
            feedFirst.completeArchive(archiveScope, state.podcast)
            // Offline, or the read failed: the copy still holds only the newest items, so the window
            // would be part of the show. Fail instead; a page that fell back pages the server's list.
            if (!ShowFeedLogic.selectionReadsWholeShow(feedFirst.readsFeed(feedId), feedFirst.archiveComplete)) {
                throw IOException("The show's feed could not be read in full")
            }
        }
        // The whole-show read sizes its own pages: the most each source answers in one request.
        return loadPodcastEpisodeSelectionWindow(uiState.value as? PodcastInfoUiState.Success, sort) { _, offset, sortParam ->
            repository.getWholeShowPage(feedId = feedId, offset = offset, sort = sortParam, list = pageState.listSchemes)
        }
    }

    /** [load] until a page comes back; a failed page waits and asks again a few times while the loading cards show ([ShowPageRetry]), then throws. */
    private suspend fun loadPageRetrying(load: suspend () -> PodcastRepository.EpisodePage): PodcastRepository.EpisodePage = ShowPageRetry.load(
        load = load,
        waitMs = { repository.routeBudget.waitMs(cx.aswin.boxlore.core.catalog.ApiRouteBudget.V2_EPISODES) },
        sleep = { delay(it) },
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 500L
    }
}
