package cx.aswin.boxlore.feature.info

import android.util.Log
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.TrackedPodcastRtdbLogic
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.ShowPageFeedPort
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.TAG
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.pageSizeFor
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.sortParamFor
import cx.aswin.boxlore.feature.info.logic.PodcastInfoEnrichLogic
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPullRefreshLogic
import cx.aswin.boxlore.feature.info.logic.PostSubscribeRefreshes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pull to refresh on the show page. [PodcastInfoPullRefreshLogic.target] picks what a pull reads:
 * a feed-first page's feed, a true RSS show's catalog, a followed show's direct feed, an opted-in
 * show's feed extras or the Podcast Index list. Each path then reloads the first page, unless the
 * page moved to another show meanwhile.
 */
internal class PodcastInfoPullRefresh(
    private val pageState: PodcastInfoPageState,
    private val repository: PodcastRepository,
    private val rssRepository: RssPodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val localCatalog: LocalCatalogPort,
    private val supplementSupport: PodcastInfoSupplementSupport,
    private val feedFirst: PodcastInfoFeedFirst,
    private val postSubscribeRefreshes: PostSubscribeRefreshes,
    private val scope: CoroutineScope,
) {
    private val uiState = pageState.uiState

    fun refresh() {
        val currentState = uiState.value as? PodcastInfoUiState.Success ?: return
        if (currentState.isRssRefreshing) return
        when (
            PodcastInfoPullRefreshLogic.target(
                isRss = currentState.podcast.isRss,
                isSubscribed = currentState.isSubscribed,
                chip = currentState.directFeedChip,
                // Once followed, the show's own catalog refresh (with auto-download) takes over.
                feedFirst = feedFirst.isActive(currentState.podcast.id) && !currentState.isSubscribed,
                postSubscribeRunning = postSubscribeRefreshes.isRunning(currentState.podcast.id),
            )
        ) {
            PodcastInfoPullRefreshLogic.Target.FEED_FIRST -> refreshFeedFirstFromPull(currentState)
            PodcastInfoPullRefreshLogic.Target.RSS_CATALOG -> refreshTrueRssCatalog(currentState)
            PodcastInfoPullRefreshLogic.Target.SUBSCRIBED_DIRECT_FEED -> refreshSubscribedDirectFeedCatalogFromPull(currentState)
            PodcastInfoPullRefreshLogic.Target.DIRECT_FEED -> refreshDirectFeedFromPull(currentState)
            PodcastInfoPullRefreshLogic.Target.PI_CATALOG -> refreshPiCatalogFromPull(currentState)
            PodcastInfoPullRefreshLogic.Target.NONE -> Unit
        }
    }

    /**
     * Pull-to-refresh of a feed-first page: reads the newest episodes at the registry's address
     * into the stored copy (whether it is partial or the whole archive), then reloads the pages.
     */
    private fun refreshFeedFirstFromPull(currentState: PodcastInfoUiState.Success) {
        val podcast = currentState.podcast
        uiState.value = currentState.copy(isRssRefreshing = true)
        scope.launch {
            try {
                feedFirst.read(podcast, ShowPageFeedPort.Scope.NEWEST, showStatus = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Pull-to-refresh feed read failed", e)
            }
            pageState.clearPullRefreshing(podcast.id)
        }
    }

    private fun refreshTrueRssCatalog(currentState: PodcastInfoUiState.Success) {
        val targetPodcastId = currentState.podcast.id
        uiState.value = currentState.copy(isLoadingMore = true, isRssRefreshing = true)
        scope.launch {
            try {
                rssRepository.refreshCatalog(targetPodcastId)
                if (!PodcastInfoPullRefreshLogic.shouldApply(pageState.currentPodcastId, targetPodcastId)) {
                    return@launch
                }
                val latestState = uiState.value as? PodcastInfoUiState.Success ?: return@launch
                applyFirstPageRefresh(latestState, targetPodcastId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                AnalyticsHelper.trackRssRefreshFailed(
                    targetPodcastId,
                    e::class.simpleName,
                )
                pageState.clearPullRefreshing(targetPodcastId)
            }
        }
    }

    private fun refreshSubscribedDirectFeedCatalogFromPull(currentState: PodcastInfoUiState.Success) {
        val targetPodcastId = currentState.podcast.id
        uiState.value = currentState.copy(isLoadingMore = true, isRssRefreshing = true)
        scope.launch {
            try {
                executeSubscribedDirectFeedRefresh(currentState, targetPodcastId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Pull-to-refresh subscribed direct-feed failed", e)
                pageState.clearPullRefreshing(targetPodcastId)
            }
        }
    }

    private suspend fun executeSubscribedDirectFeedRefresh(
        currentState: PodcastInfoUiState.Success,
        targetPodcastId: String,
    ) {
        val catalog = repository.localEpisodeCatalog
        val entity = subscriptionRepository.getPodcastEntity(targetPodcastId)
        val feedUrl =
            TrackedPodcastRtdbLogic.httpsFeedUrl(
                entity?.feedUrl ?: currentState.podcast.feedUrl,
            )
        if (catalog == null || feedUrl == null) {
            repository.invalidateEpisodesCache(targetPodcastId)
            if (PodcastInfoPullRefreshLogic.shouldApply(pageState.currentPodcastId, targetPodcastId)) {
                val latestState = uiState.value as? PodcastInfoUiState.Success ?: return
                applyFirstPageRefresh(latestState, targetPodcastId)
            }
            return
        }
        performDirectCatalogRefresh(catalog, currentState.podcast, targetPodcastId, feedUrl)
    }

    private suspend fun performDirectCatalogRefresh(
        catalog: LocalEpisodeCatalogPort,
        podcast: Podcast,
        targetPodcastId: String,
        feedUrl: String,
    ) {
        repository.invalidateEpisodesCache(targetPodcastId)
        val meta =
            LocalEpisodeCatalogPort.PodcastMeta(
                title = podcast.title,
                imageUrl = podcast.imageUrl,
                genre = podcast.genre,
                artist = podcast.artist,
            )
        val outcome =
            catalog.refresh(
                LocalEpisodeCatalogPort.RefreshRequest(
                    podcastIndexId = targetPodcastId,
                    feedUrl = feedUrl,
                    meta = meta,
                    reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL,
                ),
            )
        if (!PodcastInfoPullRefreshLogic.shouldApply(pageState.currentPodcastId, targetPodcastId)) {
            return
        }
        val tip = PodcastInfoPullRefreshLogic.extractTip(outcome)
        if (outcome is LocalEpisodeCatalogPort.RefreshOutcome.Failure) {
            Log.w(TAG, "Subscribed direct-feed catalog refresh failed: ${outcome.message}")
        }
        tip?.let { episode ->
            subscriptionRepository.updateLatestEpisode(
                podcastId = targetPodcastId,
                episode = episode,
                markAsNew = false,
                publisherFeedAuthoritative = true,
            )
        }
        val latestState = uiState.value as? PodcastInfoUiState.Success ?: return
        applyFirstPageRefresh(latestState, targetPodcastId)
    }

    private fun refreshPiCatalogFromPull(currentState: PodcastInfoUiState.Success) {
        val targetPodcastId = currentState.podcast.id
        uiState.value = currentState.copy(isLoadingMore = true, isRssRefreshing = true)
        scope.launch {
            try {
                repository.invalidateEpisodesCache(targetPodcastId)
                if (!PodcastInfoPullRefreshLogic.shouldApply(pageState.currentPodcastId, targetPodcastId)) {
                    return@launch
                }
                val latestState = uiState.value as? PodcastInfoUiState.Success ?: return@launch
                applyFirstPageRefresh(latestState, targetPodcastId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Pull-to-refresh PI catalog failed", e)
                pageState.clearPullRefreshing(targetPodcastId)
            }
        }
    }

    private suspend fun applyFirstPageRefresh(
        latestState: PodcastInfoUiState.Success,
        targetPodcastId: String,
    ) {
        val activeInitialState =
            (uiState.value as? PodcastInfoUiState.Success)
                ?.takeIf { it.podcast.id == targetPodcastId }
                ?: latestState
        val limit = pageSizeFor(activeInitialState.currentSort)
        val sortParam = sortParamFor(activeInitialState.currentSort)
        val podcast = repository.getPodcastDetails(targetPodcastId) ?: activeInitialState.podcast
        val page = repository.getShowPageEpisodes(targetPodcastId, limit, 0, sortParam)
        if (!PodcastInfoPullRefreshLogic.shouldApply(pageState.currentPodcastId, targetPodcastId)) {
            return
        }
        val activeState =
            (uiState.value as? PodcastInfoUiState.Success)
                ?.takeIf { it.podcast.id == targetPodcastId }
                ?: activeInitialState
        if (!PodcastInfoPullRefreshLogic.shouldAcceptPageSort(activeInitialState.currentSort, activeState.currentSort)) {
            uiState.value = activeState.copy(isLoadingMore = false, isRssRefreshing = false)
            return
        }
        val localPodcast = localCatalog.getLocalPodcast(targetPodcastId)
        val preservedPodcast =
            PodcastInfoEnrichLogic.preserveSubscriptionProperties(
                refreshedPodcast = podcast,
                latestPodcast = activeState.podcast,
                localPodcast = localPodcast,
                isSubscribed = activeState.isSubscribed,
            ).let { p ->
                if (p.latestEpisode == null) {
                    val newestPageEpisode =
                        PodcastInfoEnrichLogic
                            .resolveLatestFromPage(page.episodes, sortParam)
                    p.copy(latestEpisode = newestPageEpisode)
                } else {
                    p
                }
            }
        pageState.currentOffset = page.sourceCount
        uiState.value =
            supplementSupport
                .remountWithSupplements(
                    state = activeState.copy(podcast = preservedPodcast),
                    piEpisodes = page.episodes,
                    hasMoreEpisodes = page.hasMore,
                    isLoadingMore = false,
                ).copy(isRssRefreshing = false)
    }

    private fun refreshDirectFeedFromPull(currentState: PodcastInfoUiState.Success) {
        val targetPodcastId = currentState.podcast.id
        uiState.value =
            currentState.copy(
                isRssRefreshing = true,
                directFeedChip = DirectFeedChipState.Fetching,
            )
        scope.launch {
            try {
                val refreshed =
                    supplementSupport.refreshMissingEpisodes(
                        state =
                        currentState.copy(
                            isRssRefreshing = true,
                            directFeedChip = DirectFeedChipState.Fetching,
                        ),
                        announceResult = false,
                    )
                if (!PodcastInfoPullRefreshLogic.shouldApply(pageState.currentPodcastId, targetPodcastId)) {
                    return@launch
                }
                val appliedState =
                    pageState.applySupplementRefreshState(
                        targetPodcastId,
                        refreshed.state.copy(isRssRefreshing = false),
                    ) ?: return@launch
                refreshed.pageSourceCount?.let { pageState.currentOffset = it }
                if (PodcastInfoPullRefreshLogic.shouldPersistLibraryTip(
                        isSubscribed = appliedState.isSubscribed,
                        hasTip = refreshed.libraryTip != null,
                    )
                ) {
                    refreshed.libraryTip?.let { tip ->
                        subscriptionRepository.updateLatestEpisode(
                            podcastId = targetPodcastId,
                            episode = tip,
                            markAsNew = true,
                            publisherFeedAuthoritative = true,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Pull-to-refresh direct-feed failed", e)
                if (!PodcastInfoPullRefreshLogic.shouldApply(pageState.currentPodcastId, targetPodcastId)) {
                    return@launch
                }
                val failed = uiState.value as? PodcastInfoUiState.Success ?: return@launch
                uiState.value =
                    failed.copy(
                        isRssRefreshing = false,
                        directFeedChip = DirectFeedChipState.Updated,
                    )
            }
        }
    }
}
