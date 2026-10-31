package cx.aswin.boxlore.feature.info

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.autoOrganizeFolderLook
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.completedEpisodeIds
import cx.aswin.boxlore.core.playback.historyForEpisodeLookup
import cx.aswin.boxlore.core.playback.likedEpisodeIds
import cx.aswin.boxlore.core.prefs.HomePinnedShows
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.TAG
import cx.aswin.boxlore.feature.info.logic.PodcastEpisodePlaybackStateLogic
import cx.aswin.boxlore.feature.info.logic.PodcastInfoFolderSyncLogic
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPageShow
import cx.aswin.boxlore.feature.info.logic.PostSubscribeRefreshes
import cx.aswin.boxlore.feature.info.logic.enablePodcastNotifications
import cx.aswin.boxlore.feature.info.logic.togglePodcastAutoDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The show page. Opening a show ([PodcastInfoShowLoader]), the episode list
 * ([PodcastInfoEpisodePaging]), pull to refresh ([PodcastInfoPullRefresh]), the refresh after
 * subscribing ([PodcastInfoPostSubscribeRefresh]), episode actions ([PodcastInfoEpisodeActions])
 * and screen analytics ([PodcastInfoSessionTracker]) live in helpers that share the page's live
 * state ([PodcastInfoPageState]); the screen only talks to this ViewModel.
 */
@Suppress("kotlin:S6310")
class PodcastInfoViewModel(
    application: Application,
    deps: InfoSharedDeps,
    private val subscriptionRepository: cx.aswin.boxlore.core.catalog.SubscriptionRepository,
    private val rssRepository: cx.aswin.boxlore.core.rss.RssPodcastRepository,
    episodeSupplementPort: EpisodeSupplementPort,
    private val userPreferencesRepository: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    routeArgs: PodcastInfoRouteArgs,
) : AndroidViewModel(application) {
    private val repository = deps.podcastRepository
    private val playbackRepository = deps.playbackRepository
    private val downloadRepository = deps.downloadRepository
    private val localCatalog = deps.localCatalog
    private val folderRepository = deps.folderRepository
    private val entryPoint = routeArgs.entryPoint
    private val supplementSupport = PodcastInfoSupplementSupport(repository, episodeSupplementPort)
    private val pageState = PodcastInfoPageState()
    private val _uiState = pageState.uiState

    /** The feed-first show page (P4 PR 16): reads, the status line and reloads as the list grows. */
    private val feedFirst =
        PodcastInfoFeedFirst(
            PodcastInfoFeedFirst.sourceOf(repository),
            object : PodcastInfoFeedFirst.Ui {
                override fun state(): PodcastInfoUiState.Success? = _uiState.value as? PodcastInfoUiState.Success

                override fun set(state: PodcastInfoUiState.Success) {
                    _uiState.value = state
                }

                override fun currentPodcastId(): String = pageState.currentPodcastId

                override fun setOffset(offset: Int) {
                    pageState.currentOffset = offset
                }

                override suspend fun remount(state: PodcastInfoUiState.Success, episodes: List<Episode>, hasMore: Boolean): PodcastInfoUiState.Success =
                    supplementSupport.remountWithSupplements(state = state, piEpisodes = episodes, hasMoreEpisodes = hasMore, isLoadingMore = false)
            },
        )
    private val customGenreMutex = Mutex()
    private val postSubscribeRefreshes = PostSubscribeRefreshes()
    private val sessionTracker = PodcastInfoSessionTracker(routeArgs)
    private val showLoader =
        PodcastInfoShowLoader(
            pageState = pageState,
            repository = repository,
            localCatalog = localCatalog,
            subscriptionRepository = subscriptionRepository,
            rssRepository = rssRepository,
            userPrefs = userPreferencesRepository,
            supplementSupport = supplementSupport,
            feedFirst = feedFirst,
            sessionTracker = sessionTracker,
        )
    private val episodePaging = PodcastInfoEpisodePaging(pageState, repository, subscriptionRepository, supplementSupport, feedFirst, viewModelScope)
    private val pullRefresh =
        PodcastInfoPullRefresh(
            pageState = pageState,
            repository = repository,
            rssRepository = rssRepository,
            subscriptionRepository = subscriptionRepository,
            localCatalog = localCatalog,
            supplementSupport = supplementSupport,
            feedFirst = feedFirst,
            postSubscribeRefreshes = postSubscribeRefreshes,
            scope = viewModelScope,
        )
    private val postSubscribeRefresh =
        PodcastInfoPostSubscribeRefresh(
            pageState = pageState,
            repository = repository,
            subscriptionRepository = subscriptionRepository,
            subscriptionForegroundSync = deps.subscriptionForegroundSync,
            supplementSupport = supplementSupport,
            postSubscribeRefreshes = postSubscribeRefreshes,
        )
    private val episodeActions =
        PodcastInfoEpisodeActions(
            pageState = pageState,
            repository = repository,
            playbackRepository = playbackRepository,
            downloadRepository = downloadRepository,
            queueManager = deps.queueManager,
            sessionTracker = sessionTracker,
            entryPoint = entryPoint,
            scope = viewModelScope,
        )

    val folderNames: StateFlow<List<String>> =
        folderRepository?.folderNames
            ?.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptyList(),
            ) ?: MutableStateFlow(emptyList())

    /**
     * The route this page was opened for and the show it resolved to (a followed `rss:` feed linked
     * to it, or the show Podcast Index renumbered it to), so the screen themes the page with that
     * show's artwork and never with a previous page's ([cx.aswin.boxlore.feature.info.logic.RenumberedShowPageLogic.artworkPodcast]).
     */
    val pageShow: StateFlow<PodcastInfoPageShow> = pageState.pageShow.asStateFlow()

    // Observe liked episodes: every stored ID of a liked episode, so a row reads the episode's like whichever ID it lists
    private val likedEpisodeIds =
        playbackRepository.likedEpisodeIds
            .stateIn(
                scope = viewModelScope,
                started =
                kotlinx.coroutines.flow.SharingStarted
                    .WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    fun onToggleLike(episode: Episode) = episodeActions.onToggleLike(episode)

    // Check if an episode is liked (helper for UI)
    fun isEpisodeLiked(episodeId: String): Boolean = likedEpisodeIds.value.contains(episodeId)

    // Expose flow for UI to collect
    val likedEpisodesState = likedEpisodeIds

    // Observe completed episodes
    private val completedEpisodeIds =
        playbackRepository.completedEpisodeIds
            .stateIn(
                scope = viewModelScope,
                started =
                kotlinx.coroutines.flow.SharingStarted
                    .WhileSubscribed(5_000),
                initialValue = emptySet(),
            )
    val completedEpisodesState: StateFlow<Set<String>> = completedEpisodeIds

    private val userPrefs = userPreferencesRepository

    val autoDownloadBackgroundChecksEnabled: StateFlow<Boolean> =
        userPrefs.autoDownloadBackgroundSettingsStream
            .map { it.enabled }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val globalSkipBeginningMs: StateFlow<Long> =
        userPrefs.skipBeginningMsStream
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                cx.aswin.boxlore.core.playback.PlaybackSkipPolicy.DEFAULT_SKIP_BEGINNING_MS,
            )
    val globalSkipEndingMs: StateFlow<Long> =
        userPrefs.skipEndingMsStream
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                cx.aswin.boxlore.core.playback.PlaybackSkipPolicy.DEFAULT_SKIP_ENDING_MS,
            )

    val hideCompletedInShowDetails: StateFlow<Boolean> =
        userPrefs.hideCompletedInShowDetailsStream
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = false,
            )

    val isPinnedToHome: StateFlow<Boolean> =
        combine(pageState.currentPodcastIdFlow, userPrefs.homePinnedPodcastIdsStream) { id, pins ->
            id.isNotEmpty() && id in pins
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false,
        )

    fun setUseAppPlaybackDefaults(useDefaults: Boolean) {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (!state.isSubscribed) return
        val beginning = if (useDefaults) null else globalSkipBeginningMs.value
        val ending = if (useDefaults) null else globalSkipEndingMs.value
        updatePodcastPlaybackOverrides(beginning, ending)
    }

    fun setSkipBeginningOverride(valueMs: Long) {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (!state.isSubscribed) return
        updatePodcastPlaybackOverrides(
            beginningMs =
            cx.aswin.boxlore.core.playback.PlaybackSkipPolicy
                .sanitizeTrim(valueMs),
            endingMs = state.podcast.skipEndingOverrideMs ?: globalSkipEndingMs.value,
        )
    }

    fun setSkipEndingOverride(valueMs: Long) {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (!state.isSubscribed) return
        updatePodcastPlaybackOverrides(
            beginningMs = state.podcast.skipBeginningOverrideMs ?: globalSkipBeginningMs.value,
            endingMs =
            cx.aswin.boxlore.core.playback.PlaybackSkipPolicy
                .sanitizeTrim(valueMs),
        )
    }

    private fun updatePodcastPlaybackOverrides(
        beginningMs: Long?,
        endingMs: Long?,
    ) {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        viewModelScope.launch {
            subscriptionRepository.setPlaybackSkipOverrides(
                state.podcast.id,
                beginningMs,
                endingMs,
            )
            val latest = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
            if (latest.podcast.id == state.podcast.id) {
                _uiState.value =
                    latest.copy(
                        podcast =
                        latest.podcast.copy(
                            skipBeginningOverrideMs = beginningMs,
                            skipEndingOverrideMs = endingMs,
                        ),
                    )
            }
            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackSettingsInteraction(
                "podcast_playback_override_changed",
                "${state.podcast.id}:${beginningMs ?: "default"}:${endingMs ?: "default"}",
            )
        }
    }

    fun toggleHideCompleted() {
        viewModelScope.launch {
            userPrefs.setHideCompletedInShowDetails(!hideCompletedInShowDetails.value)
        }
    }

    val uiState: StateFlow<PodcastInfoUiState> = _uiState.asStateFlow()

    // Observe downloaded episode IDs
    val downloadedEpisodeIds: StateFlow<Set<String>> = episodeActions.downloadedEpisodeIds

    // Observe downloading episode IDs
    val downloadingEpisodeIds: StateFlow<Set<String>> = episodeActions.downloadingEpisodeIds

    val episodePendingDownloadRemoval: StateFlow<Episode?> = episodeActions.episodePendingDownloadRemoval

    fun confirmDownloadRemoval() = episodeActions.confirmDownloadRemoval()

    fun dismissDownloadRemoval() = episodeActions.dismissDownloadRemoval()

    fun isEpisodeCompleted(episodeId: String): Boolean = completedEpisodeIds.value.contains(episodeId)

    fun onToggleCompletion(episode: Episode) = episodeActions.onToggleCompletion(episode)

    fun markAllAsCompleted() = episodeActions.markAllAsCompleted()

    fun markAllAsUncompleted() = episodeActions.markAllAsUncompleted()

    fun toggleDownload(episode: Episode) = episodeActions.toggleDownload(episode)

    fun downloadEpisodes(episodes: List<Episode>) = episodeActions.downloadEpisodes(episodes)

    fun markEpisodesCompleted(episodes: List<Episode>) = episodeActions.markEpisodesCompleted(episodes)

    fun markEpisodesUncompleted(episodes: List<Episode>) = episodeActions.markEpisodesUncompleted(episodes)

    fun playEpisodes(episodes: List<Episode>) = episodeActions.playEpisodes(episodes)

    fun addEpisodesToQueue(episodes: List<Episode>) = episodeActions.addEpisodesToQueue(episodes)

    /**
     * Loads up to [MAX_SELECTION_EPISODES] episodes for range selection and for looking a saved
     * selection up again after recreation. [sort] is the order the selection was made in; null
     * uses the page's current sort. A recreated page passes the saved sort, because the reloaded
     * page may open in a different order than the one the listener selected in.
     */
    internal suspend fun loadEpisodeSelectionWindow(sort: EpisodeSort? = null): PodcastEpisodeSelectionWindow = episodePaging.selectionWindow(sort)

    fun isDownloaded(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> = downloadRepository.isDownloaded(episodeId)

    fun isDownloading(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> = downloadRepository
        .isDownloading(episodeId)
        .map { isDownloading ->
            android.util.Log.d("PodcastInfoVM", "isDownloading($episodeId): $isDownloading")
            isDownloading
        }

    fun consumeUserMessage() {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (state.userMessage == null) return
        _uiState.value = state.copy(userMessage = null)
    }

    fun toggleHomePin() {
        val podcastId = pageState.currentPodcastId
        if (podcastId.isEmpty()) return
        viewModelScope.launch {
            val result = userPrefs.toggleHomePinnedPodcastId(podcastId)
            if (result == HomePinnedShows.ToggleResult.AtCapacity) {
                val state = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                _uiState.value = state.copy(userMessage = HomePinnedShows.capacityUserMessage())
            }
        }
    }

    /**
     * One-shot RSS feed supplement for PI-owned shows. Never calls [cx.aswin.boxlore.core.domain.ports.RssSubscriptionPort].
     */
    fun loadMissingEpisodes() {
        val currentState = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (currentState.directFeedChip != DirectFeedChipState.Offer) return

        _uiState.value =
            currentState.copy(
                directFeedChip = DirectFeedChipState.Fetching,
                userMessage = null,
            )
        viewModelScope.launch {
            try {
                val latest = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                val targetPodcastId = latest.podcast.id
                val refresh = supplementSupport.refreshMissingEpisodes(latest, announceResult = true)
                val appliedState =
                    pageState.applySupplementRefreshState(targetPodcastId, refresh.state)
                        ?: return@launch
                refresh.pageSourceCount?.let { pageState.currentOffset = it }
                if (appliedState.isSubscribed) {
                    refresh.libraryTip?.let { tip ->
                        subscriptionRepository.updateLatestEpisode(
                            podcastId = targetPodcastId,
                            episode = tip,
                            markAsNew = true,
                            publisherFeedAuthoritative = true,
                        )
                    }
                }
                subscriptionRepository.syncTrackedPodcastFeedUrl(latest.podcast)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load missing episodes", e)
                val latest = _uiState.value as? PodcastInfoUiState.Success ?: return@launch
                _uiState.value =
                    latest.copy(
                        directFeedChip = DirectFeedChipState.Offer,
                        userMessage = "Couldn't update episodes from the feed",
                    )
            }
        }
    }

    /** Reloads after the error state (no local row and show details failed). */
    fun retryLoad() {
        val podcastId = pageState.requestedPodcastId ?: return
        if (_uiState.value !is PodcastInfoUiState.Error) return
        pageState.currentPodcastId = ""
        pageState.requestedPodcastId = null
        loadPodcast(podcastId)
    }

    fun loadPodcast(podcastId: String) {
        if (
            cx.aswin.boxlore.feature.info.logic.PodcastInfoLoadGuard.shouldSkipLoad(
                routePodcastId = podcastId,
                requestedPodcastId = pageState.requestedPodcastId,
                currentPodcastId = pageState.currentPodcastId,
                state = _uiState.value,
            )
        ) {
            return
        }

        pageState.requestedPodcastId = podcastId
        pageState.currentPodcastId = podcastId
        pageState.currentPodcastIdFlow.value = podcastId
        pageState.pageShow.value = PodcastInfoPageShow(routePodcastId = podcastId, shownPodcastId = podcastId)
        pageState.currentOffset = 0
        viewModelScope.launch { showLoader.open(podcastId) }
    }

    fun loadMoreEpisodes() = episodePaging.loadMore()

    fun refreshRssFeed() = pullRefresh.refresh()

    fun toggleSort() {
        sessionTracker.sorted()
        episodePaging.toggleSort()
    }

    fun searchEpisodes(query: String) {
        if (query.isNotBlank()) sessionTracker.searched()
        episodePaging.search(query)
    }

    fun toggleSubscription() {
        val currentState = _uiState.value
        if (currentState !is PodcastInfoUiState.Success) return
        viewModelScope.launch {
            val wasSubscribed = currentState.isSubscribed
            sessionTracker.subscriptionToggled(wasSubscribed)
            // The page's own follow state decides the direction: it counts a follow under a retired number.
            subscriptionRepository.toggleSubscription(currentState.podcast, shownFollowed = wasSubscribed)
            val isSubscribed = showLoader.isPageFollowed(pageState.requestedPodcastId ?: currentState.podcast.id, currentState.podcast.id)
            applyToggledSubscriptionUi(currentState, isSubscribed)
            if (isSubscribed && !wasSubscribed) {
                launch { postSubscribeRefresh.refreshTipAfterSubscribe() }
            } else if (!isSubscribed && wasSubscribed) {
                userPrefs.removeLastSeenEpisodeId(currentState.podcast.id)
                userPrefs.removePodcastIdFromManualOrderAndPins(currentState.podcast.id)
            }
        }
    }

    private fun applyToggledSubscriptionUi(
        currentState: PodcastInfoUiState.Success,
        isSubscribed: Boolean,
    ) {
        val latestState =
            (_uiState.value as? PodcastInfoUiState.Success)
                ?.takeIf { it.podcast.id == currentState.podcast.id }
                ?: currentState
        val updatedPodcast =
            latestState.podcast.copy(
                subscribedAt = if (isSubscribed) System.currentTimeMillis() else 0L,
                notificationsEnabled = isSubscribed && latestState.podcast.notificationsEnabled,
                autoDownloadEnabled = isSubscribed && latestState.podcast.autoDownloadEnabled,
                customGenre = if (isSubscribed) latestState.podcast.customGenre else null,
                customGenreIcon = if (isSubscribed) latestState.podcast.customGenreIcon else null,
            )
        _uiState.value =
            latestState.copy(
                podcast = updatedPodcast,
                isSubscribed = isSubscribed,
            )
        // Feed-first (P4 PR 16): once followed, the show's own catalog refresh (with auto-download)
        // reads the feed; this page's reads would store its validators first.
        if (isSubscribed && feedFirst.isActive(latestState.podcast.id)) feedFirst.stop()
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackPodcastSubscriptionToggled(
            podcastId = latestState.podcast.id,
            podcastName = latestState.podcast.title,
            isSubscribed = isSubscribed,
            entryPoint = entryPoint ?: "unknown",
        )
    }

    fun updateCustomGenre(customGenre: String?, customGenreIcon: String?) {
        val state = _uiState.value as? PodcastInfoUiState.Success ?: return
        if (!state.isSubscribed) return
        val trimmedGenre = customGenre?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedIcon = customGenreIcon?.trim()?.takeIf { it.isNotEmpty() }
        viewModelScope.launch {
            customGenreMutex.withLock {
                subscriptionRepository.updateCustomGenre(
                    state.podcast.id,
                    trimmedGenre,
                    trimmedIcon,
                )
                val latest = _uiState.value as? PodcastInfoUiState.Success ?: return@withLock
                if (latest.podcast.id == state.podcast.id && latest.isSubscribed) {
                    _uiState.value =
                        latest.copy(
                            podcast =
                            latest.podcast.copy(
                                customGenre = trimmedGenre,
                                customGenreIcon = trimmedIcon,
                            ),
                        )
                }
            }
            PodcastInfoFolderSyncLogic.syncFoldersAfterGenreUpdate(
                folderRepository = folderRepository,
                autoOrganizeLook = if (userPrefs.autoOrganizeFoldersStream.first()) userPrefs.autoOrganizeFolderLook() else null,
            )
        }
    }

    fun toggleNotifications(rssDisclosureAccepted: Boolean = false, disclosedFeedUrl: String? = null) {
        val currentState = _uiState.value
        if (currentState is PodcastInfoUiState.Success) {
            viewModelScope.launch {
                val updatedPodcast = cx.aswin.boxlore.feature.info.logic.togglePodcastNotifications(
                    currentState.podcast,
                    rssDisclosureAccepted,
                    disclosedFeedUrl,
                    subscriptionRepository::setNotificationsEnabled,
                )
                val newEnabled = updatedPodcast.notificationsEnabled
                _uiState.update { latest ->
                    if (latest is PodcastInfoUiState.Success && latest.podcast.id == currentState.podcast.id) {
                        latest.copy(podcast = latest.podcast.copy(notificationsEnabled = newEnabled))
                    } else {
                        latest
                    }
                }

                cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackShowNotificationToggled(
                    currentState.podcast.id,
                    newEnabled,
                )

                android.util.Log.d("PodcastInfoViewModel", "Notifications toggled for ${currentState.podcast.title}: $newEnabled")
            }
        }
    }

    fun toggleAutoDownload() {
        viewModelScope.launch {
            togglePodcastAutoDownload(_uiState, subscriptionRepository::setAutoDownloadEnabled)
        }
    }

    fun enableShowNotifications(rssDisclosureAccepted: Boolean = false, disclosedFeedUrl: String? = null) {
        viewModelScope.launch {
            enablePodcastNotifications(
                uiState = _uiState,
                setNotificationsEnabled = { podcast, enabled ->
                    subscriptionRepository.setNotificationsEnabled(podcast, enabled, rssDisclosureAccepted, disclosedFeedUrl)
                },
                trackShowNotificationToggled = cx.aswin.boxlore.core.analytics.AnalyticsHelper::trackShowNotificationToggled,
            )
        }
    }

    fun toggleQueue(episode: Episode) = episodeActions.toggleQueue(episode)

    // Playback State Logic
    data class EpisodePlaybackState(
        val isPlaying: Boolean = false,
        val isResume: Boolean = false,
        val progress: Float = 0f,
        val timeLeft: String? = null,
    )

    // Combine player state and history to provide per-episode state
    val episodePlaybackState: StateFlow<Map<String, EpisodePlaybackState>> =
        kotlinx.coroutines.flow
            .combine(
                playbackRepository.playerState,
                // Looked up by the IDs this page lists: an episode stored under several IDs shows its progress under each.
                playbackRepository.historyForEpisodeLookup.map { history ->
                    history.map { it.toInfoListeningProgressItem() }
                },
                pageState.currentPodcastIdFlow,
            ) { player: cx.aswin.boxlore.core.playback.PlayerState, historyList: List<InfoListeningProgressItem>, loadedId: String? ->
                PodcastEpisodePlaybackStateLogic.episodeStates(player, historyList, loadedId)
            }.flowOn(kotlinx.coroutines.Dispatchers.Default)
            .distinctUntilChanged()
            .stateIn(
                scope = viewModelScope,
                started =
                kotlinx.coroutines.flow.SharingStarted
                    .WhileSubscribed(5_000),
                initialValue = emptyMap(),
            )

    fun onPlayClick(episode: Episode) = episodeActions.onPlayClick(episode)

    // Track queued episodes, under every stored ID of each queued episode: a show switched to its
    // feed lists an episode under its feed ID while the queue may hold it under the old one.
    val queuedEpisodeIds: StateFlow<Set<String>> = episodeActions.queuedEpisodeIds

    fun recordEpisodeClick(episodeId: String) {
        sessionTracker.clicked(episodeId)
    }

    fun onScreenResume() {
        sessionTracker.screenResumed()
    }

    fun trackScreenExit() {
        sessionTracker.screenExited(pageState.currentPodcastId) {
            (_uiState.value as? PodcastInfoUiState.Success)?.podcast?.title ?: "Unknown"
        }
    }

    override fun onCleared() {
        super.onCleared()
        trackScreenExit()
    }
}
