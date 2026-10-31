package cx.aswin.boxlore.feature.info

import android.util.Log
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.ShowPageFeedState
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.PodrollItem
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.TAG
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.pageSizeFor
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.sortParamFor
import cx.aswin.boxlore.feature.info.logic.PodcastInfoEnrichLogic
import cx.aswin.boxlore.feature.info.logic.PodcastInfoLoadRecovery
import cx.aswin.boxlore.feature.info.logic.PodcastInfoSortLogic
import cx.aswin.boxlore.feature.info.logic.RenumberedShowPageLogic
import cx.aswin.boxlore.feature.info.logic.ShowFeedLogic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Opens a show page for [PodcastInfoViewModel.loadPodcast]: the stored row first, then the show's
 * details and first episode page, following a show Podcast Index renumbered, the feed extras
 * check on open, the feed-first read (P4 PR 16) and the show's extra details (location, podroll).
 */
internal class PodcastInfoShowLoader(
    private val pageState: PodcastInfoPageState,
    private val repository: PodcastRepository,
    private val localCatalog: LocalCatalogPort,
    private val subscriptionRepository: SubscriptionRepository,
    private val rssRepository: RssPodcastRepository,
    private val userPrefs: UserPreferencesRepository,
    private val supplementSupport: PodcastInfoSupplementSupport,
    private val feedFirst: PodcastInfoFeedFirst,
    private val sessionTracker: PodcastInfoSessionTracker,
) {
    private val uiState = pageState.uiState

    /** Loads [routePodcastId]'s page; returns once the load and the work it started have ended. */
    suspend fun open(routePodcastId: String) {
        coroutineScope {
            // A show Podcast Index renumbered opens as the show it now answers as, where its follow moved.
            val renumberedFollows = subscriptionRepository.renumberedFollows
            val renumberedTo = renumberedFollows.currentId(routePodcastId)
            if (renumberedTo != null) renumberedFollows.adoptKnown(routePodcastId)
            // Follows still under a number Podcast Index retired for this show become this show's.
            renumberedFollows.adoptFormerFollows(renumberedTo ?: routePodcastId)
            loadShow(routePodcastId, renumberedTo ?: routePodcastId, renumberingsLeft = MAX_RENUMBERINGS_PER_LOAD)
        }
    }

    /**
     * Loads the page of [routePodcastId] as [showId]: the route's show, or the show Podcast Index
     * renumbered it to. When the show's details answer as another numeric show ID, the follow moves
     * there ([cx.aswin.boxlore.core.catalog.RenumberedShowFollows.adoptAnswer]) and the page
     * loads again as that show, following a chain of renumberings up to [renumberingsLeft] times,
     * with that answer as [answered]: it shows while the page loads when the show has no stored row,
     * and stays when the next load fails.
     */
    // The show-page load is unchanged from loadPodcast apart from renumbering (kotlin:S3776);
    // Sonar's S6619 misreads the nullable stored row.
    @Suppress("LongMethod", "CyclomaticComplexMethod", "NestedBlockDepth", "ReturnCount", "kotlin:S3776", "kotlin:S6619")
    private suspend fun CoroutineScope.loadShow(routePodcastId: String, showId: String, renumberingsLeft: Int, answered: Podcast? = null) {
        run {
            val linkedRss =
                localCatalog.getSubscribedRssLinkedTo(showId)
                    ?: if (showId != routePodcastId) localCatalog.getSubscribedRssLinkedTo(routePodcastId) else null
            val effectivePodcastId = linkedRss?.id ?: showId
            if (effectivePodcastId != pageState.currentPodcastId) {
                pageState.currentPodcastId = effectivePodcastId
                pageState.currentPodcastIdFlow.value = effectivePodcastId
            }
            pageState.setShownPodcastId(routePodcastId, effectivePodcastId)
            val localPodcast = linkedRss ?: localCatalog.getLocalPodcast(effectivePodcastId)
            val isSubscribed = isPageFollowed(routePodcastId, effectivePodcastId)
            var currentPodcast = RenumberedShowPageLogic.shownBeforeDetails(localPodcast, answered, effectivePodcastId)

            if (currentPodcast != null) {
                sessionTracker.followStateAtOpen(isSubscribed)
                uiState.value =
                    PodcastInfoUiState.Success(
                        podcast = currentPodcast,
                        episodes = emptyList(),
                        isSubscribed = isSubscribed,
                        hasMoreEpisodes = true,
                        isLoadingMore = true,
                    )
                if (isSubscribed) {
                    currentPodcast.let { podcast ->
                        podcast.latestEpisode?.id?.let { episodeId ->
                            launch {
                                userPrefs.setLastSeenEpisodeId(podcast.id, episodeId)
                                subscriptionRepository.clearRssNewEpisodesFlag(podcast.id)
                            }
                        }
                    }
                }
            } else {
                uiState.value = PodcastInfoUiState.Loading
            }

            try {
                val initialType = currentPodcast?.type ?: "episodic"
                val initialSort =
                    PodcastInfoSortLogic.resolveInitialSort(
                        localPodcast?.preferredSort,
                        initialType,
                    )
                val limit = pageSizeFor(initialSort)
                val sortParam = sortParamFor(initialSort)

                if (currentPodcast?.isRss == true) {
                    val cachedPage =
                        repository.getShowPageEpisodes(effectivePodcastId, limit, 0, sortParam)
                    pageState.currentOffset = cachedPage.sourceCount
                    val successState =
                        PodcastInfoUiState.Success(
                            podcast = currentPodcast,
                            episodes = cachedPage.episodes,
                            piEpisodes = cachedPage.episodes,
                            isSubscribed = isSubscribed,
                            hasMoreEpisodes = cachedPage.hasMore,
                            currentSort = initialSort,
                            isLoadingMore = true,
                            directFeedChip = DirectFeedChipState.Hidden,
                        )
                    uiState.value = successState
                    // Conditional: cheap HEAD check for validator-capable feeds, interval-gated
                    // otherwise, so opening a show doesn't re-download unchanged feeds. Pull-to-
                    // refresh calls the forced refreshCatalog path instead.
                    rssRepository.refreshCatalogIfNeeded(effectivePodcastId)
                }

                // Feed-first show pages (P4 PR 16): without a stored copy, boxlore's list shows while the
                // feed is read; its rows carry the same IDs, so the list then grows without moving.
                val feedOpened = if (currentPodcast?.isRss == true) null else feedFirst.open(effectivePodcastId, currentPodcast)
                val (apiPodcast, page) =
                    fetchPodcastAndEpisodes(effectivePodcastId, limit, sortParam)

                if (apiPodcast != null &&
                    renumberingsLeft > 0 &&
                    RenumberedShowPageLogic.isRenumbered(effectivePodcastId, apiPodcast.id)
                ) {
                    // Podcast Index renumbered the show: one follow, under the show it now answers as
                    // (the repository moved it as the answer arrived; this makes sure before the page reads it).
                    subscriptionRepository.renumberedFollows.adoptAnswer(effectivePodcastId, apiPodcast)
                    if (pageState.requestedPodcastId == routePodcastId) {
                        loadShow(routePodcastId, apiPodcast.id, renumberingsLeft - 1, answered = apiPodcast)
                    }
                    return
                }
                if (apiPodcast != null) {
                    val apiPodcastWithFallback =
                        PodcastInfoEnrichLogic.enrichPodcastWithFallback(
                            apiPodcast = apiPodcast,
                            currentPodcast = currentPodcast,
                            localPodcast = localPodcast,
                            pageEpisodes = page.episodes,
                            sortParam = sortParam,
                        )
                    currentPodcast = apiPodcastWithFallback
                    pageState.currentPodcastId = apiPodcastWithFallback.id
                    pageState.currentPodcastIdFlow.value = apiPodcastWithFallback.id
                    pageState.setShownPodcastId(routePodcastId, apiPodcastWithFallback.id)

                    sessionTracker.screenViewed(apiPodcastWithFallback.id, apiPodcastWithFallback.title)
                    val latestIsSubscribed = isPageFollowed(routePodcastId, apiPodcastWithFallback.id)

                    sessionTracker.followStateAtOpen(isSubscribed)

                    pageState.currentOffset = page.sourceCount
                    val pageEpisodes = if (feedOpened != null) ShowFeedLogic.withShow(page.episodes, apiPodcastWithFallback) else page.episodes
                    val baseSuccess =
                        PodcastInfoUiState.Success(
                            podcast = apiPodcastWithFallback,
                            episodes = pageEpisodes,
                            piEpisodes = pageEpisodes,
                            isSubscribed = latestIsSubscribed,
                            hasMoreEpisodes = page.hasMore,
                            currentSort = initialSort,
                            isLoadingMore = false,
                        )
                    uiState.value =
                        supplementSupport.remountWithSupplements(
                            state = baseSuccess,
                            piEpisodes = pageEpisodes,
                            hasMoreEpisodes = page.hasMore,
                            isLoadingMore = false,
                        )
                    feedOpened?.let { opened -> launch { beginFeedFirst(apiPodcastWithFallback, initialSort, opened) } }

                    if (supplementSupport.shouldRefreshOnOpen(
                            apiPodcastWithFallback.id,
                            apiPodcastWithFallback.isRss,
                        )
                    ) {
                        launch {
                            val latest =
                                uiState.value as? PodcastInfoUiState.Success ?: return@launch
                            uiState.value =
                                latest.copy(directFeedChip = DirectFeedChipState.Fetching)
                            try {
                                val refreshed =
                                    supplementSupport.refreshMissingEpisodes(
                                        state = latest.copy(directFeedChip = DirectFeedChipState.Fetching),
                                        announceResult = false,
                                    )
                                val appliedState =
                                    pageState.applySupplementRefreshState(
                                        apiPodcastWithFallback.id,
                                        refreshed.state,
                                    ) ?: return@launch
                                refreshed.pageSourceCount?.let { pageState.currentOffset = it }
                                if (appliedState.isSubscribed) {
                                    refreshed.libraryTip?.let { tip ->
                                        subscriptionRepository.updateLatestEpisode(
                                            podcastId = apiPodcastWithFallback.id,
                                            episode = tip,
                                            markAsNew = true,
                                            publisherFeedAuthoritative = true,
                                        )
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                Log.e(TAG, "Silent direct-feed refresh failed", e)
                                val failed =
                                    uiState.value as? PodcastInfoUiState.Success ?: return@launch
                                uiState.value =
                                    failed.copy(directFeedChip = DirectFeedChipState.Updated)
                            }
                        }
                    }

                    if (latestIsSubscribed) {
                        apiPodcastWithFallback.latestEpisode?.id?.let { episodeId ->
                            launch {
                                userPrefs.setLastSeenEpisodeId(apiPodcastWithFallback.id, episodeId)
                                subscriptionRepository.clearRssNewEpisodesFlag(apiPodcastWithFallback.id)
                            }
                        }
                    }

                    if (!apiPodcastWithFallback.isRss) {
                        launch {
                            fetchAndApplyPodcastMeta(
                                effectivePodcastId,
                                apiPodcast.id,
                                localPodcast,
                            )
                        }
                    }
                } else {
                    if (currentPodcast == null) {
                        sessionTracker.screenViewed(effectivePodcastId, null)
                        AnalyticsHelper.trackCatalogMiss(
                            "podcast",
                            effectivePodcastId,
                        )
                        uiState.value = PodcastInfoUiState.Error
                    } else {
                        // Details failed but the local row is showing: keep it and apply the
                        // episode page that loaded alongside, instead of spinning forever.
                        val latest = uiState.value as? PodcastInfoUiState.Success
                        if (latest != null && latest.podcast.id == currentPodcast.id) {
                            val recovered =
                                PodcastInfoLoadRecovery.withoutPodcastDetails(
                                    state = latest,
                                    pageEpisodes = page.episodes,
                                    pageHasMore = page.hasMore,
                                    sort = initialSort,
                                )
                            if (page.episodes.isNotEmpty()) pageState.currentOffset = page.sourceCount
                            uiState.value =
                                if (page.episodes.isNotEmpty()) {
                                    supplementSupport.remountWithSupplements(
                                        state = recovered,
                                        piEpisodes = page.episodes,
                                        hasMoreEpisodes = page.hasMore,
                                        isLoadingMore = false,
                                    )
                                } else {
                                    recovered
                                }
                            val podcastShown = currentPodcast
                            feedOpened?.let { opened -> launch { beginFeedFirst(podcastShown, initialSort, opened) } }
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load podcast $effectivePodcastId", e)
                if (currentPodcast == null) {
                    sessionTracker.screenViewed(effectivePodcastId, null)
                    AnalyticsHelper.trackCatalogMiss(
                        "podcast",
                        effectivePodcastId,
                    )
                    uiState.value = PodcastInfoUiState.Error
                } else {
                    // We already showed a Success state (with isLoadingMore = true) further up;
                    // make sure a failed refresh doesn't leave it stuck loading forever.
                    val latestState = uiState.value as? PodcastInfoUiState.Success
                    if (latestState != null) {
                        uiState.value = latestState.copy(isLoadingMore = false, isRssRefreshing = false)
                    }
                }
            }
        }
    }

    /**
     * The page's follow: the show it shows, the route's show that show replaced, or any show
     * recorded as renumbered into it, still followed under its old number ([RenumberedShowPageLogic.isFollowed]).
     */
    suspend fun isPageFollowed(routePodcastId: String, shownPodcastId: String): Boolean =
        RenumberedShowPageLogic.isFollowed(
            shownFollowed = subscriptionRepository.isSubscribed(shownPodcastId),
            routeFollowed = routePodcastId != shownPodcastId && subscriptionRepository.isSubscribed(routePodcastId),
            formerFollowed = subscriptionRepository.renumberedFollows.isFollowedUnderFormerId(shownPodcastId),
        )

    private suspend fun fetchPodcastAndEpisodes(
        podcastId: String,
        limit: Int,
        sortParam: String,
    ): Pair<Podcast?, PodcastRepository.EpisodePage> = coroutineScope {
        val apiPodcast: Podcast?
        val page: PodcastRepository.EpisodePage

        if (podcastId.startsWith("url:") || podcastId.startsWith("guid:")) {
            apiPodcast = repository.getPodcastDetails(podcastId)
            if (apiPodcast != null) {
                val realId = apiPodcast.id
                val episodesDeferred = async { repository.getShowPageEpisodes(realId, limit, 0, sortParam) }
                page = episodesDeferred.await()
            } else {
                page = PodcastRepository.EpisodePage(emptyList(), false)
            }
        } else {
            val podcastDeferred = async { repository.getPodcastDetails(podcastId) }
            val episodesDeferred = async { repository.getShowPageEpisodes(podcastId, limit, 0, sortParam) }
            apiPodcast = podcastDeferred.await()
            page = episodesDeferred.await()
        }
        Pair(apiPodcast, page)
    }

    /**
     * Starts the feed-first page's opening read once its first page shows. A failure leaves the
     * stored copy showing and clears the status line.
     */
    private suspend fun beginFeedFirst(podcast: Podcast, sort: EpisodeSort, opened: ShowPageFeedState) {
        try {
            feedFirst.begin(podcast, sort, opened)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Feed-first read failed for ${podcast.id}", e)
            val state = uiState.value as? PodcastInfoUiState.Success ?: return
            if (state.podcast.id == podcast.id) uiState.value = state.copy(isLoadingMore = false, feedStatus = ShowFeedStatus.NONE)
        }
    }

    private suspend fun fetchAndApplyPodcastMeta(
        podcastId: String,
        apiPodcastId: String,
        localPodcast: Podcast?,
    ) {
        try {
            val meta = repository.getPodcastMeta(podcastId) ?: return
            val state = uiState.value as? PodcastInfoUiState.Success ?: return
            if (state.podcast.id != podcastId && state.podcast.id != apiPodcastId) return

            val enrichedPodcast =
                state.podcast.copy(
                    location = meta.location,
                    license = meta.license,
                    isLocked = meta.locked == 1,
                    updateFrequency = meta.updateFrequency,
                    podroll =
                    meta.podroll?.map {
                        PodrollItem(
                            title = it.title,
                            url = it.url,
                            uuid = it.uuid,
                        )
                    },
                )
            uiState.value = state.copy(podcast = enrichedPodcast)
            if (!state.isSubscribed) return

            val preferredSortVal = localPodcast?.preferredSort ?: "newest"
            val typeVal = if (preferredSortVal == "oldest") "serial" else "episodic"
            localCatalog.upsertSubscribedPodcast(
                enrichedPodcast.copy(
                    imageUrl =
                    enrichedPodcast.imageUrl.ifEmpty {
                        localPodcast?.imageUrl.orEmpty()
                    },
                    type = typeVal,
                    preferredSort = preferredSortVal,
                    notificationsEnabled = localPodcast?.notificationsEnabled ?: false,
                    autoDownloadEnabled = localPodcast?.autoDownloadEnabled ?: false,
                ),
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private companion object {
        /** Renumberings a page follows in one load (a show renumbered again); a longer chain stops there instead of looping. */
        const val MAX_RENUMBERINGS_PER_LOAD = 4
    }
}
