package cx.aswin.boxlore.feature.info

import android.app.Application
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.EpisodeLinkResult
import cx.aswin.boxlore.core.catalog.resolveEpisodeLink
import cx.aswin.boxlore.core.domain.ports.EpisodeOfflineLookupPort
import cx.aswin.boxlore.core.domain.ports.LocalCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.addToQueueNext
import cx.aswin.boxlore.core.playback.completedEpisodeIds
import cx.aswin.boxlore.core.playback.getSession
import cx.aswin.boxlore.core.playback.likedEpisodeIds
import cx.aswin.boxlore.core.playback.removeFromQueue
import cx.aswin.boxlore.core.playback.savePlaybackState
import cx.aswin.boxlore.core.playback.toggleCompletion
import cx.aswin.boxlore.core.playback.toggleLike
import cx.aswin.boxlore.core.playback.togglePlayPause
import cx.aswin.boxlore.core.playback.withEpisodeAliases
import cx.aswin.boxlore.feature.info.logic.EpisodeOfflineMergeLogic
import cx.aswin.boxlore.feature.info.logic.collectSimilarEpisodes
import cx.aswin.boxlore.feature.info.logic.sharedLookup
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Immutable
sealed interface EpisodeInfoUiState {
    data object Loading : EpisodeInfoUiState

    data class Success(
        val episode: Episode,
        val podcastId: String,
        val podcastTitle: String,
        val podcastGenre: String = "",
        val resumePositionMs: Long = 0L,
        val durationMs: Long = 0L,
        val relatedEpisodes: List<Episode> = emptyList(),
        val relatedEpisodesLoading: Boolean = true,
        val similarEpisodes: List<Episode> = emptyList(),
        val similarEpisodesLoading: Boolean = true,
        val isPlaying: Boolean = false, // Sync with global player
        val isPlaybackLoading: Boolean = false,
        val location: String? = null,
        val license: String? = null,
        val crossPromotion: cx.aswin.boxlore.core.model.ResolvedCrossPromotion? = null,
        val crossPromoLoading: Boolean = false,
        val showNotes: cx.aswin.boxlore.core.model.ShowNotes? = null,
        val chapters: List<cx.aswin.boxlore.core.model.Chapter> = emptyList(),
        /**
         * The ID the page was opened with. [episode] keeps the phone's own ID when a link named the
         * episode by another of its IDs (P4 PR 13), so callers match the page by this one.
         */
        val requestedEpisodeId: String = episode.id,
    ) : EpisodeInfoUiState

    data object Error : EpisodeInfoUiState

    /**
     * The episode is only in its show's feed, so the page is reading that feed once to find it.
     * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, episode page).
     */
    data object ReadingFeed : EpisodeInfoUiState

    /**
     * Nothing lists the episode the page was opened for: no row on the phone, the API doesn't know
     * it, or its show's feed no longer has it. [podcastId] is its show when the link named one, else
     * empty. Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, episode page).
     */
    data class NotFound(val podcastId: String) : EpisodeInfoUiState
}

@Suppress("LargeClass")
class EpisodeInfoViewModel(
    application: Application,
    private val podcastRepository: cx.aswin.boxlore.core.catalog.PodcastRepository,
    private val playbackRepository: cx.aswin.boxlore.core.playback.PlaybackRepository,
    private val downloadRepository: cx.aswin.boxlore.core.downloads.DownloadRepository,
    private val queueManager: cx.aswin.boxlore.core.playback.QueueManager,
    private val userPrefs: cx.aswin.boxlore.core.prefs.UserPreferencesRepository,
    private val localCatalog: LocalCatalogPort,
    private val episodeOfflineLookup: EpisodeOfflineLookupPort,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow<EpisodeInfoUiState>(EpisodeInfoUiState.Loading)
    val uiState: StateFlow<EpisodeInfoUiState> = _uiState.asStateFlow()

    /** Null until read. More like this shows and loads only while the listener keeps it on. */
    val moreLikeThisOn: StateFlow<Boolean?> =
        userPrefs.discoverySectionsStream
            .map { it.moreLikeThis }
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    private var similarEpisodesJob: Job? = null

    /** The running page load and what it was asked for, so the route and the screen don't load the page twice. */
    private var loadJob: Job? = null
    private var loadRequest: EpisodeLoadRequests.Request? = null
    private val crossPromotionResolver = cx.aswin.boxlore.core.catalog.crosspromo.CrossPromotionResolver(podcastRepository)
    private val notesLoader = cx.aswin.boxlore.feature.info.logic.EpisodeInfoNotesLoader(
        scope = viewModelScope,
        chapters = cx.aswin.boxlore.core.catalog.ChapterRepository::getChapters,
        promotion = { episode, hostId, hostTitle, notes ->
            val result = cx.aswin.boxlore.core.catalog.crosspromo.CrossPromotionDetector().detect(episode, hostTitle, notes)
            val name = result.extractedShowName
            if (result.isCrossPromotion && name != null) {
                crossPromotionResolver.resolve(name, hostId, notes.links)?.let { target ->
                    cx.aswin.boxlore.core.model.ResolvedCrossPromotion(name, result.confidence, target, result.matchedIndicators)
                }
            } else {
                null
            }
        },
    )

    // Observe liked episodes: every stored ID of a liked episode, so this page reads the episode's like whichever ID it shows
    val likedEpisodeIds =
        playbackRepository.likedEpisodeIds
            .stateIn(
                scope = viewModelScope,
                started =
                kotlinx.coroutines.flow.SharingStarted
                    .WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    val completedEpisodeIds =
        playbackRepository.completedEpisodeIds
            .stateIn(
                scope = viewModelScope,
                started =
                kotlinx.coroutines.flow.SharingStarted
                    .WhileSubscribed(5_000),
                initialValue = emptySet(),
            )

    private val _showRemoveDownloadDialog = MutableStateFlow(false)
    val showRemoveDownloadDialog: StateFlow<Boolean> = _showRemoveDownloadDialog.asStateFlow()

    fun confirmDownloadRemoval() {
        val currentState = _uiState.value as? EpisodeInfoUiState.Success ?: return
        viewModelScope.launch {
            downloadRepository.removeDownload(currentState.episode.id)
            _showRemoveDownloadDialog.value = false
        }
    }

    fun dismissDownloadRemoval() {
        _showRemoveDownloadDialog.value = false
    }

    // --- Tracking State ---
    private var sessionStartTime = System.currentTimeMillis()
    private var didPlay = false
    private var didLike = false
    private var didDownload = false
    private var didAddToQueue = false
    private var didMarkPlayed = false
    private var didViewPodcast = false
    private var didViewRelatedEpisode = false
    private var didScrollRelatedEpisodes = false
    private var relatedEpisodesShownCount = 0
    private var sourceEntryPoint: String? = null
    private var hasTrackedExit = false

    fun onToggleCompletion() {
        didMarkPlayed = true
        val currentState = uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            viewModelScope.launch {
                playbackRepository.toggleCompletion(
                    episode = currentState.episode,
                    podcastId = currentState.podcastId,
                    podcastTitle = currentState.podcastTitle,
                    podcastImageUrl = currentState.episode.podcastImageUrl,
                )
            }
        }
    }

    init {
        // Observe global player state to sync button (Play/Pause)
        viewModelScope.launch {
            playbackRepository.playerState.collect { playerState ->
                val currentState = _uiState.value
                if (currentState is EpisodeInfoUiState.Success) {
                    val isSameEpisode = playerState.currentEpisode?.id == currentState.episode.id
                    val isPlaying = isSameEpisode && playerState.isPlaying
                    val isPlaybackLoading = isSameEpisode && playerState.isLoading

                    // If playing this episode, we can also sync the progress in real-time
                    val resumePos = if (isSameEpisode) playerState.position else currentState.resumePositionMs

                    if (
                        currentState.isPlaying != isPlaying ||
                        currentState.isPlaybackLoading != isPlaybackLoading ||
                        (isSameEpisode && currentState.resumePositionMs != resumePos)
                    ) {
                        _uiState.value =
                            currentState.copy(
                                isPlaying = isPlaying,
                                isPlaybackLoading = isPlaybackLoading,
                                resumePositionMs = resumePos,
                            )
                    }
                }
            }
        }
    }

    fun onToggleLike(episode: Episode) {
        didLike = true
        val currentState = uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            val wasLiked = likedEpisodeIds.value.contains(episode.id)
            viewModelScope.launch {
                playbackRepository.toggleLike(
                    episode = episode,
                    podcastId = currentState.podcastId,
                    podcastTitle = currentState.podcastTitle,
                    podcastImageUrl = currentState.episode.podcastImageUrl,
                )
            }
        }
    }

    fun loadEpisode(
        episodeId: String,
        episodeTitle: String = "",
        episodeDescription: String = "",
        episodeImageUrl: String = "",
        episodeAudioUrl: String = "",
        episodeDuration: Int = 0,
        podcastId: String = "",
        podcastTitle: String = "",
        entryPointContext: android.os.Bundle? = null,
    ) {
        // If we already have this episode loaded (or are loading it with at least as much to go on), don't reload.
        if (EpisodeLoadRequests.isCovered(loadRequest, loadJob?.isActive == true, _uiState.value, episodeId, podcastId)) return
        loadJob?.cancel()
        loadRequest = EpisodeLoadRequests.Request(episodeId, podcastId)
        // A new episode stops the previous one's Try these episodes request straight away.
        similarEpisodesJob?.cancel()

        notesLoader.cancel()
        loadJob = viewModelScope.launch {
            _uiState.value = EpisodeInfoUiState.Loading
            try {
                var seed =
                    EpisodeOfflineMergeLogic.EpisodeFieldSeed(
                        podcastId = podcastId,
                        podcastTitle = podcastTitle,
                        episodeTitle = episodeTitle,
                        episodeImageUrl = episodeImageUrl,
                        episodeDescription = episodeDescription,
                        episodeAudioUrl = episodeAudioUrl,
                        episodeDurationSec = episodeDuration,
                    )

                var currentEpisode: Episode? = null

                // If essential parameters are missing, resolve them from local DB first, then network
                seed =
                    EpisodeOfflineMergeLogic.merge(
                        seed,
                        episodeOfflineLookup.fromDownload(episodeId),
                    )
                seed =
                    EpisodeOfflineMergeLogic.merge(
                        seed,
                        episodeOfflineLookup.fromHistory(episodeId),
                    )

                var finalPodcastId = seed.podcastId
                var finalPodcastTitle = seed.podcastTitle
                var finalEpisodeTitle = seed.episodeTitle
                var finalEpisodeImageUrl = seed.episodeImageUrl
                var finalEpisodeDescription = seed.episodeDescription
                var finalEpisodeAudioUrl = seed.episodeAudioUrl
                var finalEpisodeDuration = seed.episodeDurationSec

                // A link, push or Android Auto item with only an ID: resolve it by show (P4 PR 13).
                var resolved: Episode? = null
                if (finalPodcastId.isEmpty() || finalEpisodeTitle.isEmpty() || finalEpisodeAudioUrl.isEmpty()) {
                    val link = podcastRepository.resolveEpisodeLink(episodeId, finalPodcastId.takeIf(String::isNotEmpty)) {
                        _uiState.value = EpisodeInfoUiState.ReadingFeed
                    }
                    resolved = (link as? EpisodeLinkResult.Found)?.episode
                    EpisodeLoadRequests.unresolvedState(link, finalPodcastId, finalEpisodeTitle, finalEpisodeAudioUrl)?.let { unresolved ->
                        _uiState.value = unresolved
                        return@launch
                    }
                    val fullEpisode = resolved
                    if (fullEpisode != null) {
                        currentEpisode = fullEpisode
                        if (finalPodcastId.isEmpty()) finalPodcastId = fullEpisode.podcastId ?: "unknown"
                        if (finalPodcastTitle.isEmpty() ||
                            finalPodcastTitle == "Podcast"
                        ) {
                            finalPodcastTitle = fullEpisode.podcastTitle ?: "Podcast"
                        }
                        if (finalEpisodeTitle.isEmpty()) finalEpisodeTitle = fullEpisode.title
                        if (finalEpisodeImageUrl.isEmpty()) finalEpisodeImageUrl = fullEpisode.imageUrl ?: ""
                        if (finalEpisodeDescription.isEmpty()) finalEpisodeDescription = fullEpisode.description
                        if (finalEpisodeAudioUrl.isEmpty()) finalEpisodeAudioUrl = fullEpisode.audioUrl
                        if (finalEpisodeDuration == 0) finalEpisodeDuration = fullEpisode.duration
                    }
                }

                if (currentEpisode == null) {
                    currentEpisode =
                        Episode(
                            id = episodeId,
                            title = finalEpisodeTitle,
                            description = finalEpisodeDescription,
                            imageUrl = finalEpisodeImageUrl,
                            audioUrl = finalEpisodeAudioUrl,
                            duration = finalEpisodeDuration,
                            publishedDate = 0L,
                            podcastId = EpisodeLoadRequests.knownShow(finalPodcastId),
                        )
                }

                // The page's episode keeps the phone's own ID when the link named it by another one.
                val pageEpisodeId = currentEpisode.id
                // Check for resume position immediately
                val resumeSession = playbackRepository.getSession(pageEpisodeId)
                val resumeMs = resumeSession?.positionMs ?: 0L
                val durationMs = resumeSession?.durationMs ?: (finalEpisodeDuration * 1000L)

                // Fetch local podcast if exists for immediate metadata
                val localPodcast =
                    if (finalPodcastId.isNotEmpty()) {
                        localCatalog.getLocalPodcast(finalPodcastId)
                    } else {
                        null
                    }
                val initialLocation = localPodcast?.location
                val initialLicense = localPodcast?.license

                if (localPodcast != null && (finalPodcastTitle.isEmpty() || finalPodcastTitle == "Podcast")) {
                    finalPodcastTitle = localPodcast.title
                }

                // If finalPodcastTitle is empty/generic, try to fetch podcast details from network
                if (finalPodcastId.isNotEmpty() && (finalPodcastTitle.isEmpty() || finalPodcastTitle == "Podcast")) {
                    val podcast = podcastRepository.getPodcastDetails(finalPodcastId)
                    if (podcast != null) {
                        finalPodcastTitle = podcast.title
                    }
                }

                _uiState.value =
                    EpisodeInfoUiState.Success(
                        episode = currentEpisode,
                        podcastId = finalPodcastId,
                        podcastTitle = finalPodcastTitle,
                        resumePositionMs = resumeMs,
                        durationMs = durationMs,
                        location = initialLocation,
                        license = initialLicense,
                        requestedEpisodeId = episodeId,
                    )

                // Detect immediately on partial title (triggers search request concurrently)
                detectCrossPromotion(currentEpisode, finalPodcastTitle)

                // Track Screen View
                val props =
                    mutableMapOf<String, Any>().apply {
                        put("podcast_id", finalPodcastId)
                        put("podcast_name", finalPodcastTitle)
                        put("episode_id", pageEpisodeId)
                        put("episode_name", finalEpisodeTitle)
                        put("is_partially_played", resumeMs > 0)
                        if (entryPointContext != null) {
                            entryPointContext.getString("entry_point")?.let {
                                sourceEntryPoint = it
                                put("source_entry_point", it)
                            }
                        }
                    }
                cx.aswin.boxlore.core.analytics.AnalyticsHelper
                    .trackEpisodeInfoScreenViewed(props)

                // 2. Fetch full details if we haven't already
                if (finalEpisodeDescription.isEmpty()) {
                    val fullEpisode = resolved ?: podcastRepository.getEpisode(pageEpisodeId, finalPodcastId.takeIf(String::isNotEmpty))
                    if (fullEpisode != null) {
                        val netImage = fullEpisode.imageUrl
                        currentEpisode =
                            fullEpisode.copy(
                                imageUrl = if (!netImage.isNullOrEmpty()) netImage else finalEpisodeImageUrl,
                            )

                        val existingState = _uiState.value as? EpisodeInfoUiState.Success
                        _uiState.value = existingState?.copy(
                            episode = currentEpisode,
                            podcastId = finalPodcastId,
                            podcastTitle = finalPodcastTitle,
                        ) ?: EpisodeInfoUiState.Success(
                            episode = currentEpisode,
                            podcastId = finalPodcastId,
                            podcastTitle = finalPodcastTitle,
                            resumePositionMs = resumeMs,
                            durationMs = durationMs,
                            location = initialLocation,
                            license = initialLicense,
                            requestedEpisodeId = episodeId,
                        )
                    }
                } else {
                    // Fetch network episode anyway to ensure we have any extra metadata
                    val fullEpisode = resolved ?: podcastRepository.getEpisode(pageEpisodeId, finalPodcastId.takeIf(String::isNotEmpty))
                    if (fullEpisode != null) {
                        val netImage = fullEpisode.imageUrl
                        currentEpisode =
                            fullEpisode.copy(
                                imageUrl = if (!netImage.isNullOrEmpty()) netImage else finalEpisodeImageUrl,
                            )
                        val existingState = _uiState.value as? EpisodeInfoUiState.Success
                        _uiState.value = existingState?.copy(episode = currentEpisode) ?: EpisodeInfoUiState.Success(
                            episode = currentEpisode,
                            podcastId = finalPodcastId,
                            podcastTitle = finalPodcastTitle,
                            resumePositionMs = resumeMs,
                            durationMs = durationMs,
                            location = existingState?.location ?: initialLocation,
                            license = existingState?.license ?: initialLicense,
                            requestedEpisodeId = episodeId,
                        )
                    }
                }

                detectCrossPromotion(currentEpisode, finalPodcastTitle)
                loadRelatedAndSimilar(pageEpisodeId, finalPodcastId, finalEpisodeTitle, finalPodcastTitle, finalEpisodeDescription)
            } catch (e: kotlinx.coroutines.CancellationException) {
                // A newer load of this page replaced this one; it owns the page state now.
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                // Keep showing partial data if success was already emitted?
                if (_uiState.value is EpisodeInfoUiState.Loading || _uiState.value is EpisodeInfoUiState.ReadingFeed) {
                    _uiState.value = EpisodeInfoUiState.Error
                }
            }
        }
    }

    private fun loadRelatedAndSimilar(
        episodeId: String,
        podcastId: String,
        episodeTitle: String,
        podcastTitle: String,
        episodeDescription: String,
    ) {
        if (podcastId.isEmpty() || podcastId == "unknown") return

        // One /podcast request shared by the related-episodes and similar-episodes loaders.
        val podcastDetails =
            viewModelScope.sharedLookup { podcastRepository.getPodcastDetails(podcastId) }

        // 3. Fetch related episodes AND podcast genre INDEPENDENTLY (non-blocking)
        viewModelScope.launch {
            try {
                android.util.Log.d("EpisodeInfo", "Fetching related episodes for podcastId: $podcastId")

                // Fetch podcast to get genre
                val podcast = podcastDetails.await()
                val genre = podcast?.genre ?: ""

                // Use getEpisodesPaginated which is the correct method used elsewhere
                val page = podcastRepository.getEpisodesPaginated(podcastId, 15, 0, "newest")
                android.util.Log.d("EpisodeInfo", "Fetched ${page.episodes.size} episodes, genre: $genre")
                val relatedEps = cx.aswin.boxlore.feature.info.logic.selectMoreFromEpisodes(page.episodes, episodeId)

                val currentSuccess = _uiState.value as? EpisodeInfoUiState.Success
                if (currentSuccess != null && currentSuccess.episode.id == episodeId) {
                    relatedEpisodesShownCount = relatedEps.size
                    _uiState.value =
                        currentSuccess.copy(
                            relatedEpisodes = relatedEps,
                            relatedEpisodesLoading = false,
                            podcastGenre = genre.ifEmpty { currentSuccess.podcastGenre },
                            location = podcast?.location ?: currentSuccess.location,
                            license = podcast?.license ?: currentSuccess.license,
                        )
                }
            } catch (e: Exception) {
                android.util.Log.e("EpisodeInfo", "Error fetching related episodes", e)
                // Mark loading as done even on failure
                val currentSuccess = _uiState.value as? EpisodeInfoUiState.Success
                if (currentSuccess != null) {
                    _uiState.value = currentSuccess.copy(relatedEpisodesLoading = false)
                }
                e.printStackTrace()
            }
        }

        // 4. Fetch similar episodes (contextual "More like this") from Qdrant Cloud via proxy, only
        // while the listener keeps the section on.
        similarEpisodesJob?.cancel()
        similarEpisodesJob = viewModelScope.launch {
            collectSimilarEpisodes(
                episodeId = episodeId,
                on = userPrefs.discoverySectionsStream.map { it.moreLikeThis },
                state = _uiState,
                fetch = {
                    android.util.Log.d("EpisodeInfo", "Fetching similar episodes for episodeId: $episodeId, title: $episodeTitle")
                    // Fetch the podcast details to get categories and author
                    val podcast = podcastDetails.await()
                    val region = userPrefs.regionStream.first().takeIf { it.isNotBlank() } ?: "us"
                    podcastRepository.getSimilarEpisodes(
                        cx.aswin.boxlore.core.catalog.SimilarEpisodesQuery(
                            episodeId = episodeId,
                            podcastId = podcastId,
                            title = episodeTitle,
                            description = episodeDescription,
                            podcastTitle = podcastTitle,
                            categories = podcast?.genre ?: "",
                            author = podcast?.artist ?: "",
                            limit = 10,
                            country = region,
                            languages = userPrefs.contentLanguagesStream.first(),
                        ),
                    ).also { android.util.Log.d("EpisodeInfo", "Fetched ${it.size} similar episodes") }
                },
                onError = { android.util.Log.e("EpisodeInfo", "Error fetching similar episodes", it) },
            )
        }
    }

    fun toggleDownload(episode: Episode) {
        val currentState = _uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            viewModelScope.launch {
                val isDownloaded = downloadRepository.isDownloaded(episode.id).first()
                val isDownloading = downloadRepository.isDownloading(episode.id).first()
                when (cx.aswin.boxlore.core.downloads.DownloadTogglePolicy.resolveAction(isDownloaded, isDownloading)) {
                    cx.aswin.boxlore.core.downloads.DownloadToggleAction.CONFIRM_REMOVAL -> {
                        _showRemoveDownloadDialog.value = true
                    }
                    cx.aswin.boxlore.core.downloads.DownloadToggleAction.CANCEL_DOWNLOAD -> {
                        downloadRepository.removeDownload(episode.id)
                    }
                    cx.aswin.boxlore.core.downloads.DownloadToggleAction.START_DOWNLOAD -> {
                        didDownload = true
                        val podcast =
                            cx.aswin.boxlore.core.model.Podcast(
                                id = currentState.podcastId,
                                title = currentState.podcastTitle,
                                artist = "",
                                imageUrl = currentState.episode.podcastImageUrl ?: "",
                                description = "",
                                genre = currentState.podcastGenre,
                            )
                        downloadRepository.addDownload(episode, podcast)
                    }
                }
            }
        }
    }

    fun isDownloaded(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> = downloadRepository.isDownloaded(episodeId)

    fun isDownloading(episodeId: String): kotlinx.coroutines.flow.Flow<Boolean> = downloadRepository.isDownloading(episodeId)

    fun onMainActionClick(entryPointContext: android.os.Bundle? = null) {
        didPlay = true
        val currentState = _uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            val globalState = playbackRepository.playerState.value

            // Rewrite the bundle so that the immediate entry point is the episode screen
            val finalBundle =
                android.os.Bundle().apply {
                    if (entryPointContext != null) {
                        putAll(entryPointContext)
                        val originalEntryPoint = entryPointContext.getString("entry_point")
                        if (originalEntryPoint != null) {
                            putString("source_entry_point", originalEntryPoint)
                        }
                    }
                    putString("entry_point", "episode_info_screen")
                }

            if (globalState.currentEpisode?.id == currentState.episode.id) {
                // Same episode: Toggle Play/Pause
                if (!globalState.isPlaying) {
                    val map = mutableMapOf<String, Any>()
                    finalBundle.keySet().forEach { key ->
                        bundleValueOrNull(finalBundle, key)?.let { map[key] = it }
                    }
                    if (map.isNotEmpty()) {
                        cx.aswin.boxlore.core.analytics.PendingEntryPoint.noteRequest()
                        cx.aswin.boxlore.core.analytics.PendingEntryPoint
                            .set(cx.aswin.boxlore.core.analytics.PendingEntryPoint.withScreens(map))
                    }
                }
                playbackRepository.togglePlayPause()
            } else {
                // Different episode: Start Playback

                viewModelScope.launch {
                    val pod =
                        cx.aswin.boxlore.core.model.Podcast(
                            id = currentState.podcastId,
                            title = currentState.podcastTitle,
                            artist = "",
                            imageUrl = currentState.episode.podcastImageUrl ?: "",
                            description = "",
                            genre = currentState.podcastGenre,
                        )
                    queueManager.playEpisode(currentState.episode, pod, entryPointContext = finalBundle)
                }
            }
        }
    }

    fun seekToPosition(positionMs: Long) {
        val currentState = uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            val globalState = playbackRepository.playerState.value
            if (globalState.currentEpisode?.id == currentState.episode.id) {
                playbackRepository.seekTo(positionMs, play = true)
            } else {
                viewModelScope.launch {
                    playbackRepository.savePlaybackState(
                        cx.aswin.boxlore.feature.info.logic.EpisodeInfoSeekLogic.progressSaveInputForSeek(
                            podcastId = currentState.podcastId,
                            podcastTitle = currentState.podcastTitle,
                            episode = currentState.episode,
                            positionMs = positionMs,
                            durationMs = currentState.durationMs,
                            isLiked = likedEpisodeIds.value.contains(currentState.episode.id),
                            lastPlayedAt = System.currentTimeMillis(),
                        ),
                    )
                    val pod =
                        cx.aswin.boxlore.core.model.Podcast(
                            id = currentState.podcastId,
                            title = currentState.podcastTitle,
                            artist = "",
                            imageUrl = currentState.episode.podcastImageUrl ?: "",
                            description = "",
                            genre = currentState.podcastGenre,
                        )
                    queueManager.playEpisode(currentState.episode, pod)
                }
            }
        }
    }

    fun toggleQueue() {
        didAddToQueue = true
        val currentState = _uiState.value
        if (currentState is EpisodeInfoUiState.Success) {
            viewModelScope.launch {
                val isQueued = queuedEpisodeIds.value.contains(currentState.episode.id)
                if (isQueued) {
                    playbackRepository.removeFromQueue(currentState.episode.id)
                } else {
                    val pod =
                        cx.aswin.boxlore.core.model.Podcast(
                            id = currentState.podcastId,
                            title = currentState.podcastTitle,
                            artist = "",
                            imageUrl = currentState.episode.podcastImageUrl ?: "",
                            description = "",
                            genre = currentState.podcastGenre,
                        )
                    // User requested "Add to Queue" -> Insert as NEXT item
                    playbackRepository.addToQueueNext(currentState.episode, pod)
                }
            }
        }
    }

    // Track queued episodes, under every stored ID of each queued episode: a show switched to its
    // feed lists an episode under its feed ID while the queue may hold it under the old one.
    val queuedEpisodeIds: StateFlow<Set<String>> =
        playbackRepository
            .withEpisodeAliases(
                playbackRepository.playerState
                    .map { state -> state.queue.map { it.id }.toSet() }
                    .distinctUntilChanged(),
            ).stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = emptySet(),
            )

    fun onPodcastLinkClicked() {
        didViewPodcast = true
    }

    fun onRelatedEpisodeClicked() {
        didViewRelatedEpisode = true
    }

    fun onRelatedEpisodesScrolled() {
        didScrollRelatedEpisodes = true
    }

    fun onScreenResume() {
        if (hasTrackedExit) {
            // User came back from background or backstack. Restart the session timer.
            sessionStartTime = System.currentTimeMillis()
            hasTrackedExit = false
        }
    }

    fun trackScreenExit() {
        if (hasTrackedExit) return
        hasTrackedExit = true

        val currentState = _uiState.value as? EpisodeInfoUiState.Success ?: return
        val timeSpentSeconds = (System.currentTimeMillis() - sessionStartTime) / 1000f

        val props =
            mutableMapOf<String, Any>().apply {
                put("podcast_id", currentState.podcastId)
                put("podcast_name", currentState.podcastTitle)
                put("episode_id", currentState.episode.id)
                put("episode_name", currentState.episode.title)
                put("time_spent_seconds", timeSpentSeconds)
                put("did_play", didPlay)
                put("did_like", didLike)
                put("did_download", didDownload)
                put("did_add_to_queue", didAddToQueue)
                put("did_mark_played", didMarkPlayed)
                put("did_view_podcast", didViewPodcast)
                put("did_view_related_episode", didViewRelatedEpisode)
                put("related_episodes_shown_count", relatedEpisodesShownCount)
                put("did_scroll_related_episodes", didScrollRelatedEpisodes)

                if (sourceEntryPoint != null) {
                    put("source_entry_point", sourceEntryPoint!!)
                }
            }
        cx.aswin.boxlore.core.analytics.AnalyticsHelper
            .trackEpisodeInfoScreenSession(props)
    }

    private fun detectCrossPromotion(episode: Episode, hostPodcastTitle: String) {
        val current = _uiState.value as? EpisodeInfoUiState.Success ?: return
        if (current.episode.id != episode.id) return
        notesLoader.load(
            episode,
            current.podcastId,
            hostPodcastTitle,
            onNotes = { notes -> updateEpisodeExtras(episode.id) { it.copy(showNotes = notes) } },
            onChapters = { chapters -> updateEpisodeExtras(episode.id) { it.copy(chapters = chapters) } },
            onPromotion = { promotion -> updateEpisodeExtras(episode.id) { it.copy(crossPromotion = promotion, crossPromoLoading = false) } },
            onStarted = { updateEpisodeExtras(episode.id) { it.copy(crossPromoLoading = true) } },
        )
    }

    private fun updateEpisodeExtras(episodeId: String, update: (EpisodeInfoUiState.Success) -> EpisodeInfoUiState.Success) {
        val current = _uiState.value as? EpisodeInfoUiState.Success ?: return
        if (current.episode.id == episodeId) _uiState.value = update(current)
    }
}

/** Typed Bundle lookup for analytics maps (avoids deprecated [android.os.Bundle.get]). */
private fun bundleValueOrNull(
    bundle: android.os.Bundle,
    key: String,
): Any? {
    if (!bundle.containsKey(key)) return null
    bundle.getString(key)?.let { return it }
    // Non-string analytics values used by entry-point bundles.
    return when (key) {
        "ep_carousel_position" -> bundle.getInt(key)
        "ep_is_subscribed" -> bundle.getBoolean(key)
        else ->
            bundle.getString(key)
                ?: bundle.getCharSequence(key)?.toString()
    }
}
