package cx.aswin.boxlore.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.FolderRepository
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.toScorable
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.FolderDisplaySize
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.SubscriptionFolder
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.addToQueue
import cx.aswin.boxlore.core.playback.addToQueueNext
import cx.aswin.boxlore.core.playback.historyForEpisodeLookup
import cx.aswin.boxlore.core.playback.likedEpisodes
import cx.aswin.boxlore.core.playback.playEpisode
import cx.aswin.boxlore.core.playback.playQueue
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.CandidateSource
import cx.aswin.boxlore.core.ranking.EpisodeRankingInput
import cx.aswin.boxlore.core.ranking.RankingObjective
import cx.aswin.boxlore.core.ranking.RankingSurface
import cx.aswin.boxlore.feature.library.logic.SubscriptionManualOrderLogic
import cx.aswin.boxlore.feature.library.logic.SubscriptionSmartOrderLogic
import cx.aswin.boxlore.feature.library.subscriptions.FolderInterSort
import cx.aswin.boxlore.feature.library.subscriptions.FolderIntraSort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SubscriptionSort { SmartRank, RecentlyUpdated, Alphabetical, MostListened, Manual }
enum class DownloadsSortOrder { RECENT, NAME, SIZE, COUNT }
enum class ShowSortOrder { NEWEST, OLDEST, LARGEST }

sealed interface LibraryUiState {
    data object Loading : LibraryUiState
    data class Success(
        val subscribedPodcasts: List<Podcast> = emptyList(),
        val likedEpisodes: List<ListeningHistoryEntity> = emptyList(),
        val downloadedEpisodes: List<cx.aswin.boxlore.core.database.DownloadedEpisodeEntity> = emptyList(),
        val recentHistory: List<ListeningHistoryEntity> = emptyList(),
        val currentSort: SubscriptionSort = SubscriptionSort.SmartRank,
        val allHistory: List<ListeningHistoryEntity> = emptyList(),
        val manualOrder: List<String> = emptyList(),
        val smartOrderIds: List<String> = emptyList(),
    ) : LibraryUiState
    data class Error(val message: String) : LibraryUiState
}

class LibraryViewModel(
    private val subscriptionRepository: SubscriptionRepository,
    private val playbackRepository: PlaybackRepository,
    private val downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository,
    private val userPreferencesRepository: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    private val adaptiveScorer: AdaptiveCandidateScorer,
    private val folderRepository: FolderRepository? = null,
) : ViewModel() {

    val lastSeenEpisodes: StateFlow<Map<String, String>> = userPreferencesRepository.lastSeenEpisodesStream
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyMap()
        )

    val homeShortcutsInLibrary: StateFlow<Boolean> =
        userPreferencesRepository.homeShortcutsInLibraryStream.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false,
        )

    val subscriptionsTabStyle: StateFlow<String> =
        userPreferencesRepository.subscriptionsTabStyleStream.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = userPreferencesRepository.cachedSubscriptionsTabStyle,
        )

    private val _downloadsSortOrder = MutableStateFlow(DownloadsSortOrder.RECENT)
    val downloadsSortOrder = _downloadsSortOrder.asStateFlow()

    private val _showSortOrder = MutableStateFlow(ShowSortOrder.NEWEST)
    val showSortOrder = _showSortOrder.asStateFlow()

    fun setDownloadsSortOrder(sortOrder: DownloadsSortOrder) {
        _downloadsSortOrder.value = sortOrder
    }

    fun setShowSortOrder(sortOrder: ShowSortOrder) {
        _showSortOrder.value = sortOrder
    }

    val folders: StateFlow<List<SubscriptionFolder>> =
        folderRepository?.folders
            ?.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptyList(),
            ) ?: MutableStateFlow<List<SubscriptionFolder>>(emptyList()).asStateFlow()

    init {
        viewModelScope.launch {
            if (userPreferencesRepository.autoOrganizeFoldersStream.first()) {
                folderRepository?.autoOrganizeSubscribedShows()
            } else {
                folderRepository?.syncLinkedGenres()
            }
        }
    }

    fun createFolder(
        name: String,
        icon: String?,
        displaySize: FolderDisplaySize,
        linkedGenre: String?,
        showPodcastGrid: Boolean = false,
        podcastIds: List<String> = emptyList(),
    ) {
        viewModelScope.launch {
            folderRepository?.createFolder(
                name = name,
                icon = icon,
                displaySize = displaySize,
                linkedGenre = linkedGenre,
                showPodcastGrid = showPodcastGrid,
                podcastIds = podcastIds,
            )
        }
    }

    fun createFolderAndMovePodcast(
        name: String,
        icon: String?,
        displaySize: FolderDisplaySize,
        linkedGenre: String?,
        showPodcastGrid: Boolean = false,
        podcastId: String,
        fromFolderId: String? = null,
    ) {
        viewModelScope.launch {
            val created = folderRepository?.createFolder(
                name = name,
                icon = icon,
                displaySize = displaySize,
                linkedGenre = linkedGenre,
                showPodcastGrid = showPodcastGrid,
                podcastIds = listOf(podcastId),
            )
            if (created != null && fromFolderId != null) {
                folderRepository.removePodcastFromFolder(podcastId, fromFolderId)
            }
        }
    }

    fun updateFolder(folder: SubscriptionFolder) {
        viewModelScope.launch {
            folderRepository?.updateFolder(folder)
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            folderRepository?.deleteFolder(folderId)
        }
    }

    fun addPodcastToFolder(podcastId: String, folderId: String) {
        viewModelScope.launch {
            folderRepository?.addPodcastToFolder(podcastId, folderId)
        }
    }

    fun removePodcastFromFolder(podcastId: String, folderId: String) {
        viewModelScope.launch {
            folderRepository?.removePodcastFromFolder(podcastId, folderId)
        }
    }

    fun setFolderShows(folderId: String, podcastIds: List<String>) {
        viewModelScope.launch {
            folderRepository?.setPodcastsForFolder(folderId, podcastIds)
        }
    }

    fun movePodcastToFolder(podcastId: String, fromFolderId: String?, toFolderId: String) {
        viewModelScope.launch {
            if (fromFolderId != null) {
                folderRepository?.removePodcastFromFolder(podcastId, fromFolderId)
            }
            folderRepository?.addPodcastToFolder(podcastId, toFolderId)
        }
    }

    fun unsubscribe(podcast: Podcast) {
        viewModelScope.launch {
            subscriptionRepository.toggleSubscription(podcast)
            if (!subscriptionRepository.isSubscribed(podcast.id)) {
                userPreferencesRepository.removePodcastIdFromManualOrderAndPins(podcast.id)
            }
        }
    }

    fun reorderFolderShows(folderId: String, orderedPodcastIds: List<String>) {
        viewModelScope.launch {
            folderRepository?.setPodcastsForFolder(folderId, orderedPodcastIds)
            val currentIntraSort = userPreferencesRepository.subscriptionIntraFolderSortStream.first()
            if (currentIntraSort != FolderIntraSort.Manual.name) {
                userPreferencesRepository.setSubscriptionIntraFolderSort(FolderIntraSort.Manual.name)
            }
        }
    }

    private val subscriptionSort = userPreferencesRepository.subscriptionSortStream
        .map { sortName ->
            try {
                SubscriptionSort.valueOf(sortName)
            } catch (e: Exception) {
                SubscriptionSort.SmartRank
            }
        }

    private val sortAndManualOrder =
        combine(
            subscriptionSort,
            userPreferencesRepository.subscriptionManualOrderStream,
        ) { sort, order -> sort to order }

    val pinnedPodcastIds: StateFlow<Set<String>> =
        userPreferencesRepository.homePinnedPodcastIdsStream
            .map { it.toSet() }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    fun setSubscriptionSort(sort: SubscriptionSort) {
        viewModelScope.launch {
            if (sort == SubscriptionSort.Manual) {
                val saved = userPreferencesRepository.subscriptionManualOrderStream.first()
                if (saved.isEmpty()) {
                    val currentIds =
                        (uiState.value as? LibraryUiState.Success)
                            ?.subscribedPodcasts
                            ?.map { it.id }
                            .orEmpty()
                    if (currentIds.isNotEmpty()) {
                        userPreferencesRepository.setSubscriptionManualOrder(currentIds)
                    }
                }
            }
            userPreferencesRepository.setSubscriptionSort(sort.name)
        }
    }

    fun reorderSubscriptions(orderedIds: List<String>) {
        if (orderedIds.isEmpty()) return
        viewModelScope.launch {
            userPreferencesRepository.setSubscriptionManualOrder(orderedIds)
            val currentName = userPreferencesRepository.subscriptionSortStream.first()
            if (currentName != SubscriptionSort.Manual.name) {
                userPreferencesRepository.setSubscriptionSort(SubscriptionSort.Manual.name)
            }
        }
    }

    val useSmartRank: StateFlow<Boolean> = userPreferencesRepository.latestEpisodesSortUseSmartStream
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    fun setUseSmartRank(useSmart: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setLatestEpisodesSortUseSmart(useSmart)
        }
    }

    suspend fun scoreLatestEpisodes(
        podcasts: List<Podcast>,
        history: List<ListeningHistoryEntity>,
    ): Map<String, Double> {
        val inputs = podcasts.mapNotNull { podcast ->
            podcast.latestEpisode?.let { episode ->
                EpisodeRankingInput(
                    episode = episode,
                    podcast = podcast,
                    priorScore = episode.publishedDate.toDouble().coerceAtLeast(0.0),
                    source = CandidateSource.SUBSCRIPTION,
                )
            }
        }
        return try {
            adaptiveScorer.scoreEpisodes(
                inputs = inputs,
                history = history,
                objective = RankingObjective.YOUR_SHOWS,
                surface = RankingSurface.LIBRARY,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            inputs.associate { it.episode.id to it.priorScore }
        }
    }

    val hideCompletedInSubs: StateFlow<Boolean> = userPreferencesRepository.hideCompletedInSubsStream
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = true
        )

    fun setHideCompletedInSubs(hide: Boolean) {
        viewModelScope.launch {
            userPreferencesRepository.setHideCompletedInSubs(hide)
        }
    }

    val autoOrganizeFolders: StateFlow<Boolean> = userPreferencesRepository.autoOrganizeFoldersStream
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = false
        )

    fun setAutoOrganizeFolders(
        enabled: Boolean,
        displaySize: FolderDisplaySize? = null,
        showPodcastGrid: Boolean = false,
    ) {
        viewModelScope.launch {
            userPreferencesRepository.setAutoOrganizeFolders(enabled)
            if (enabled) {
                folderRepository?.autoOrganizeSubscribedShows(
                    defaultDisplaySize = displaySize,
                    showPodcastGrid = showPodcastGrid,
                )
            }
        }
    }

    val folderSort: StateFlow<FolderInterSort> = userPreferencesRepository.subscriptionFolderSortStream
        .map { sortName ->
            try {
                FolderInterSort.valueOf(sortName)
            } catch (_: Exception) {
                FolderInterSort.Inherit
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FolderInterSort.Inherit,
        )

    fun setFolderSort(sort: FolderInterSort) {
        viewModelScope.launch {
            userPreferencesRepository.setSubscriptionFolderSort(sort.name)
        }
    }

    val folderManualOrder: StateFlow<List<String>> = userPreferencesRepository.subscriptionFolderManualOrderStream
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    fun reorderFolders(orderedFolderIds: List<String>) {
        if (orderedFolderIds.isEmpty()) return
        viewModelScope.launch {
            userPreferencesRepository.setSubscriptionFolderManualOrder(orderedFolderIds)
            val currentFolderSort = userPreferencesRepository.subscriptionFolderSortStream.first()
            if (currentFolderSort != FolderInterSort.Manual.name) {
                userPreferencesRepository.setSubscriptionFolderSort(FolderInterSort.Manual.name)
            }
        }
    }

    val intraFolderSort: StateFlow<FolderIntraSort> = userPreferencesRepository.subscriptionIntraFolderSortStream
        .map { sortName ->
            try {
                FolderIntraSort.valueOf(sortName)
            } catch (_: Exception) {
                FolderIntraSort.Inherit
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = FolderIntraSort.Inherit,
        )

    fun setIntraFolderSort(sort: FolderIntraSort) {
        viewModelScope.launch {
            userPreferencesRepository.setSubscriptionIntraFolderSort(sort.name)
        }
    }

    // Combine subscriptions, liked episodes, downloads, AND listening history
    // so we can enrich each podcast's latestEpisode with play status
    val uiState: StateFlow<LibraryUiState> = combine(
        subscriptionRepository.subscribedPodcasts,
        playbackRepository.likedEpisodes,
        downloadRepository.downloads,
        // Lists count an episode once; the lookup finds an episode stored under several IDs by any of them.
        combine(playbackRepository.getAllHistory(), playbackRepository.historyForEpisodeLookup) { list, lookup -> list to lookup },
        sortAndManualOrder,
    ) {
            podcasts: List<Podcast>,
            liked: List<ListeningHistoryEntity>,
            downloads: List<cx.aswin.boxlore.core.database.DownloadedEpisodeEntity>,
            histories: Pair<List<ListeningHistoryEntity>, List<ListeningHistoryEntity>>,
            sortAndOrder,
        ->
        val (allHistory, historyLookup) = histories
        val (sort, manualOrder) = sortAndOrder
        // Enrich podcasts with episode status from listening history
        val historyById = historyLookup.associateBy { it.episodeId }
        val enrichedPodcasts = podcasts.map { podcast ->
            val episode = podcast.latestEpisode ?: return@map podcast
            val history = historyById[episode.id]

            when {
                // Never touched → UNPLAYED
                history == null || (history.progressMs == 0L && !history.isCompleted) -> {
                    podcast.copy(episodeStatus = EpisodeStatus.UNPLAYED)
                }
                // Started but not finished → IN_PROGRESS
                !history.isCompleted && history.progressMs > 0L -> {
                    val progress = if (history.durationMs > 0) {
                        (history.progressMs.toFloat() / history.durationMs).coerceIn(0f, 1f)
                    } else {
                        0f
                    }
                    podcast.copy(
                        resumeProgress = progress,
                        episodeStatus = EpisodeStatus.IN_PROGRESS
                    )
                }
                // Completed
                history.isCompleted -> {
                    podcast.copy(
                        resumeProgress = 1f,
                        episodeStatus = EpisodeStatus.COMPLETED
                    )
                }
                else -> podcast
            }
        }

        val podScoresMap = try {
            adaptiveScorer.scorePodcasts(
                podcasts = enrichedPodcasts.map { it.toScorable() },
                history = allHistory,
                objective = RankingObjective.YOUR_SHOWS,
                surface = RankingSurface.LIBRARY,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyMap()
        }

        val smartRanked = SubscriptionSmartOrderLogic.sort(
            podcasts = enrichedPodcasts,
            scores = podScoresMap,
        )

        // Apply sorting
        val sortedPodcasts = when (sort) {
            SubscriptionSort.SmartRank -> smartRanked
            SubscriptionSort.RecentlyUpdated -> {
                enrichedPodcasts.sortedByDescending { it.latestEpisode?.publishedDate ?: 0L }
            }
            SubscriptionSort.Alphabetical -> {
                enrichedPodcasts.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            }
            SubscriptionSort.MostListened -> {
                val historyCounts = allHistory.groupBy { it.podcastId }.mapValues { it.value.size }
                enrichedPodcasts.sortedByDescending { historyCounts[it.id] ?: 0 }
            }
            SubscriptionSort.Manual -> {
                SubscriptionManualOrderLogic.apply(manualOrder, enrichedPodcasts)
            }
        }

        LibraryUiState.Success(
            subscribedPodcasts = sortedPodcasts,
            likedEpisodes = liked,
            downloadedEpisodes = downloads,
            recentHistory = allHistory.filter { !it.isManualCompletion && !it.isBulkCompletion }.take(3),
            currentSort = sort,
            allHistory = allHistory,
            manualOrder = manualOrder,
            smartOrderIds = smartRanked.map { it.id },
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = LibraryUiState.Loading
        )

    // ── Telemetry State & Lifecycle ──

    // Shared session timer (since distinct ViewModel instances are created per route)
    var sessionStartTime: Long = 0L
    private var hasTrackedExit = false

    // Hub State
    var hubNavigatedTo: String? = null

    // Subscriptions State
    var subTabSwitchesCount = 0
    var subDidSearch = false
    var subFinalSearchQuery: String? = null
    var subPodcastsClickedCount = 0
    var subEpisodesClickedCount = 0

    // Liked / Downloads State
    var genericEpisodesClickedCount = 0
    var genericItemsRemovedCount = 0

    fun onScreenResume() {
        SharedAppDependenciesHolder.instance?.subscriptionForegroundSync?.requestRefresh()
        viewModelScope.launch {
            folderRepository?.syncLinkedGenres()
        }
        if (sessionStartTime == 0L) {
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackHubExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryHubSession(timeSpent, hubNavigatedTo)
        hasTrackedExit = true
        sessionStartTime = 0L
    }

    fun trackSubscriptionsExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibrarySubscriptionsSession(
            timeSpentSeconds = timeSpent,
            tabSwitchesCount = subTabSwitchesCount,
            didSearch = subDidSearch,
            finalSearchQuery = subFinalSearchQuery,
            podcastsClickedCount = subPodcastsClickedCount,
            episodesClickedCount = subEpisodesClickedCount
        )
        hasTrackedExit = true
        sessionStartTime = 0L
    }

    fun trackLikedExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryLikedSession(
            timeSpentSeconds = timeSpent,
            episodesClickedCount = genericEpisodesClickedCount,
            episodesUnlikedCount = genericItemsRemovedCount
        )
        hasTrackedExit = true
        sessionStartTime = 0L
    }

    fun trackDownloadsExit() {
        if (sessionStartTime == 0L || hasTrackedExit) return
        val timeSpent = (System.currentTimeMillis() - sessionStartTime) / 1000f
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackLibraryDownloadsSession(
            timeSpentSeconds = timeSpent,
            episodesClickedCount = genericEpisodesClickedCount,
            episodesDeletedCount = genericItemsRemovedCount
        )
        hasTrackedExit = true
        sessionStartTime = 0L
    }

    fun removeDownload(episodeId: String) {
        viewModelScope.launch {
            genericItemsRemovedCount++
            downloadRepository.removeDownload(episodeId)
        }
    }

    fun removeMultipleDownloads(episodeIds: List<String>) {
        viewModelScope.launch {
            genericItemsRemovedCount += episodeIds.size
            episodeIds.forEach { id ->
                downloadRepository.removeDownload(id)
            }
        }
    }

    fun playEpisode(episode: Episode, podcast: Podcast) {
        viewModelScope.launch {
            playbackRepository.playEpisode(episode, podcast)
        }
    }

    fun addToQueue(episode: Episode, podcast: Podcast) {
        viewModelScope.launch {
            playbackRepository.addToQueue(episode, podcast)
        }
    }

    fun addToQueueNext(episode: Episode, podcast: Podcast) {
        viewModelScope.launch {
            playbackRepository.addToQueueNext(episode, podcast)
        }
    }

    fun playQueue(episodes: List<Episode>, podcast: Podcast) {
        viewModelScope.launch {
            playbackRepository.playQueue(episodes, podcast)
        }
    }
}
