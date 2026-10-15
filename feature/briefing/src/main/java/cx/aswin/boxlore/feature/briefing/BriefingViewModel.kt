package cx.aswin.boxlore.feature.briefing

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.catalog.ChapterRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.designsystem.R
import cx.aswin.boxlore.core.designsystem.briefing.briefingShowPodcast
import cx.aswin.boxlore.core.model.Briefing
import cx.aswin.boxlore.core.model.Chapter
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.playback.getSession
import cx.aswin.boxlore.core.playback.pause
import cx.aswin.boxlore.core.playback.resume
import cx.aswin.boxlore.core.prefs.BriefingEdition
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BriefingViewModel(
    application: Application,
    private val podcastRepository: PodcastRepository,
    private val playbackRepository: PlaybackRepository,
    private val queueManager: cx.aswin.boxlore.core.playback.QueueManager,
    initialRegion: String? = null,
    private val userPreferences: UserPreferencesRepository,
) : AndroidViewModel(application) {

    private val _selectedRegion = MutableStateFlow("global")
    val selectedRegion: StateFlow<String> = _selectedRegion.asStateFlow()

    val defaultEdition = userPreferences.briefingDefaultEdition

    private val _briefingState = MutableStateFlow<Briefing?>(null)
    private val _briefingChapters = MutableStateFlow<List<Chapter>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _error = MutableStateFlow<String?>(null)
    private val _savedSession = MutableStateFlow<cx.aswin.boxlore.core.playback.PlaybackSession?>(null)

    private val _uiState = MutableStateFlow<BriefingUiState>(BriefingUiState.Loading())
    val uiState: StateFlow<BriefingUiState> = _uiState.asStateFlow()

    private val loader = BriefingLoader(
        scope = viewModelScope,
        fetchMetadata = podcastRepository::getBriefingMetadata,
        fetchChapters = { briefing ->
            val version = android.net.Uri.parse(briefing.audioUrl).getQueryParameter("v")
            val url = briefing.chaptersUrl
                ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/${briefing.region}?d=${briefing.date}${version?.let { "&v=$it" }.orEmpty()}"
            ChapterRepository.getChapters(url)
        },
        fetchSession = { playbackRepository.getSession(getBriefingEpisodeId(it)) },
        publish = { state ->
            if (state.isLoading) _isLoading.value = true
            _selectedRegion.value = state.region
            _briefingState.value = state.briefing
            _briefingChapters.value = state.chapters
            _savedSession.value = state.session
            _error.value = if (state.failed) application.getString(R.string.briefing_load_error) else null
            if (!state.isLoading) _isLoading.value = false
        },
    )

    init {
        loader.initialize(defaultEdition, userPreferences.regionStream, initialRegion)

        // Keep _savedSession updated in real-time when the current briefing is playing
        viewModelScope.launch {
            playbackRepository.playerState.collect { playerState ->
                val briefing = _briefingState.value
                if (briefing != null) {
                    val briefingEpisodeId = getBriefingEpisodeId(briefing)
                    if (playerState.currentEpisode?.id == briefingEpisodeId) {
                        _savedSession.value = cx.aswin.boxlore.core.playback.PlaybackSession(
                            podcastId = "briefing_${briefing.region}",
                            episodeId = briefingEpisodeId,
                            positionMs = playerState.position,
                            durationMs = playerState.duration,
                            timestamp = System.currentTimeMillis(),
                            episodeTitle = briefing.title,
                            podcastTitle = application.getString(R.string.briefing_brand_name),
                            imageUrl = getBriefingCoverUrl(briefing.region),
                            podcastImageUrl = getBriefingCoverUrl(briefing.region),
                            audioUrl = briefing.audioUrl,
                            enclosureType = "audio/mpeg"
                        )
                    }
                }
            }
        }

        // Combine network briefing state with local media playback state
        val briefingSubState = combine(
            _briefingState,
            _isLoading,
            _error,
            _selectedRegion,
            _briefingChapters
        ) { briefing, isLoading, error, region, chapters ->
            BriefingSubState(briefing, isLoading, error, region, chapters)
        }

        viewModelScope.launch {
            combine(
                briefingSubState,
                playbackRepository.playerState,
                _savedSession
            ) { subState, playerState, savedSession ->
                val briefing = subState.briefing
                val isLoading = subState.isLoading
                val error = subState.error
                val region = subState.region
                val chapters = subState.chapters

                when {
                    isLoading -> BriefingUiState.Loading(region)
                    error != null -> BriefingUiState.Error(error, region)
                    briefing != null -> {
                        val briefingEpisodeId = getBriefingEpisodeId(briefing)
                        val isCurrentBriefing = playerState.currentEpisode?.id == briefingEpisodeId

                        val currentPos = if (isCurrentBriefing) playerState.position else (savedSession?.positionMs ?: 0L)
                        val dur = if (isCurrentBriefing) playerState.duration else (savedSession?.durationMs ?: 0L)

                        BriefingUiState.Success(
                            briefing = briefing,
                            selectedRegion = region,
                            isPlaying = isCurrentBriefing && playerState.isPlaying,
                            currentPosition = currentPos,
                            duration = dur,
                            isBuffering = isCurrentBriefing && playerState.isLoading,
                            chapters = chapters
                        )
                    }
                    else -> BriefingUiState.Error(application.getString(R.string.briefing_unavailable), region)
                }
            }.collect {
                _uiState.value = it
            }
        }
    }

    fun selectRegion(region: String) {
        loadBriefing(region)
    }

    fun setDefaultEdition(edition: String) {
        viewModelScope.launch {
            userPreferences.setBriefingDefaultEdition(edition)
            selectRegion(BriefingEdition.resolve(edition, userPreferences.regionStream.first()))
        }
    }

    fun loadBriefing(region: String) {
        loader.load(region)
    }

    fun playBriefing(briefing: Briefing, initialPositionMs: Long? = null) {
        viewModelScope.launch {
            val dummyPodcast = briefingShowPodcast(getApplication<Application>(), briefing.region)

            val publishedDate = try {
                java.time.LocalDate.parse(briefing.date)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toEpochSecond()
            } catch (e: Exception) {
                System.currentTimeMillis() / 1000
            }

            val audioUri = android.net.Uri.parse(briefing.audioUrl)
            val version = audioUri.getQueryParameter("v")
            val versionParam = if (version != null) "&v=$version" else ""

            val descriptionHtml = buildString {
                append("<p>Your daily AI-generated news briefing for ${briefing.region.uppercase()}.</p>")
                if (briefing.script.isNotEmpty()) {
                    append("<br/>")
                    briefing.script.split("\n\n").filter { it.isNotBlank() }.forEach { paragraph ->
                        append("<p>").append(paragraph.replace("\n", "<br/>")).append("</p>")
                    }
                }
                if (briefing.sources.isNotEmpty()) {
                    append("<br/><br/><h3>References & Sources</h3><ul>")
                    briefing.sources.forEach { source ->
                        append("<li><a href=\"").append(source.url).append("\">")
                            .append(source.title).append("</a></li>")
                    }
                    append("</ul>")
                }
            }

            val dummyEpisode = Episode(
                id = getBriefingEpisodeId(briefing),
                title = briefing.title,
                description = descriptionHtml,
                audioUrl = briefing.audioUrl,
                imageUrl = getBriefingCoverUrl(briefing.region),
                podcastId = dummyPodcast.id,
                podcastTitle = dummyPodcast.title,
                podcastImageUrl = dummyPodcast.imageUrl,
                podcastArtist = dummyPodcast.artist,
                duration = 0,
                publishedDate = publishedDate,
                transcriptUrl = briefing.transcriptUrl
                    ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/transcript/${briefing.region}?d=${briefing.date}$versionParam",
                chaptersUrl = briefing.chaptersUrl
                    ?: "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/${briefing.region}?d=${briefing.date}$versionParam"
            )

            queueManager.playEpisode(
                dummyEpisode,
                dummyPodcast,
                initialPositionMs = initialPositionMs,
                // Glossary canonical entry_point — keeps playback_* attributable without
                // renaming daily_briefing_action (dashboards already use that event).
                entryPointContext =
                android.os.Bundle().apply {
                    putString("entry_point", "briefing")
                },
            )
        }
    }

    fun togglePlayPause(briefing: Briefing, initialPositionMs: Long? = null) {
        val state = _uiState.value
        if (state is BriefingUiState.Success) {
            val briefingEpisodeId = getBriefingEpisodeId(briefing)
            val playerState = playbackRepository.playerState.value
            when (
                val action = resolveBriefingPlaybackAction(
                    isCurrentBriefing = playerState.currentEpisode?.id == briefingEpisodeId,
                    isPlaying = playerState.isPlaying,
                    requestedPositionMs = initialPositionMs,
                )
            ) {
                is BriefingPlaybackAction.StartBriefing ->
                    playBriefing(briefing, action.initialPositionMs)

                BriefingPlaybackAction.Pause -> playbackRepository.pause()
                BriefingPlaybackAction.Resume -> playbackRepository.resume()
                is BriefingPlaybackAction.SeekToStory -> {
                    playbackRepository.seekTo(action.positionMs)
                    if (action.resumeAfterSeek) playbackRepository.resume()
                }
            }
        }
    }

    fun seekTo(positionMs: Long) {
        playbackRepository.seekTo(positionMs)
    }

    private fun getBriefingEpisodeId(briefing: Briefing): String = BriefingIdentity.episodeId(briefing)

    private fun getBriefingCoverUrl(region: String): String {
        val packageName = getApplication<Application>().packageName
        return cx.aswin.boxlore.core.designsystem.briefing.briefingPlayerArtworkUri(packageName, region)
    }
}

private data class BriefingSubState(
    val briefing: Briefing?,
    val isLoading: Boolean,
    val error: String?,
    val region: String,
    val chapters: List<Chapter>
)
