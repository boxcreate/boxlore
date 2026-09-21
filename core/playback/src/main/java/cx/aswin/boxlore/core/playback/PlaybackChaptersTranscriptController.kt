package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.catalog.BuildConfig
import cx.aswin.boxlore.core.catalog.ChapterOfflineStorage
import cx.aswin.boxlore.core.catalog.ChapterRepository
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.TranscriptOfflineStorage
import cx.aswin.boxlore.core.catalog.TranscriptRepository
import cx.aswin.boxlore.core.catalog.mapRegionForBriefing
import cx.aswin.boxlore.core.model.AutoTranscriptState
import cx.aswin.boxlore.core.playback.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Chapters / transcript monitoring for [cx.aswin.boxlore.core.playback.PlaybackRepository].
 */
internal class PlaybackChaptersTranscriptController(
    private val context: android.content.Context,
    private val scope: CoroutineScope,
    private val playerState: StateFlow<PlayerState>,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val podcastRepository: PodcastRepository,
    @Suppress("UNUSED_PARAMETER") deviceUuid: (() -> String)? = null,
) {
    private var chaptersAndTranscriptFetchJob: Job? = null
    private var lastLoadedEpisodeId: String? = null

    fun monitorChaptersAndTranscripts() {
        scope.launch {
            playerState
                .map { it.currentEpisode?.id }
                .distinctUntilChanged()
                .collect { episodeId ->
                    android.util.Log.d("PlaybackRepo", "monitorChaptersAndTranscripts: collected episodeId=$episodeId")
                    if (episodeId == null) {
                        lastLoadedEpisodeId = null
                        chaptersAndTranscriptFetchJob?.cancel()
                        playerStateFlow.value =
                            playerStateFlow.value.copy(
                                currentChapters = emptyList(),
                                isChaptersLoading = false,
                                currentTranscript = emptyList(),
                            )
                    } else {
                        if (episodeId == lastLoadedEpisodeId) {
                            android.util.Log.d(
                                "PlaybackRepo",
                                "monitorChaptersAndTranscripts: episodeId matches lastLoadedEpisodeId, ignoring",
                            )
                            return@collect
                        }
                        android.util.Log.d("PlaybackRepo", "monitorChaptersAndTranscripts: starting fetch job for episodeId=$episodeId")
                        lastLoadedEpisodeId = episodeId
                        chaptersAndTranscriptFetchJob?.cancel()
                        chaptersAndTranscriptFetchJob =
                            launch {
                                var episode = playerStateFlow.value.currentEpisode
                                android.util.Log.d(
                                    "PlaybackRepo",
                                    "monitorChaptersAndTranscripts: currentEpisode title=${episode?.title}, audioUrl=${episode?.audioUrl}, chaptersUrl=${episode?.chaptersUrl}, transcriptUrl=${episode?.transcriptUrl}",
                                )

                                // If missing metadata, try to enrich from PodcastRepository.
                                // Skip when URLs are already present (they may carry server signatures).
                                if (episodeId.startsWith("briefing_") && (episode?.chaptersUrl == null || episode.transcriptUrl == null)) {
                                    try {
                                        android.util.Log.d(
                                            "PlaybackRepo",
                                            "monitorChaptersAndTranscripts: enriching briefing episode $episodeId",
                                        )
                                        val parts = episodeId.split("_")
                                        android.util.Log.d(
                                            "PlaybackRepo",
                                            "monitorChaptersAndTranscripts: parts=$parts, size=${parts.size}",
                                        )
                                        if (parts.size >= 3) {
                                            val region = parts[1]
                                            val date = parts[2]
                                            val audioUri = android.net.Uri.parse(episode?.audioUrl ?: "")
                                            val version = audioUri.getQueryParameter("v")
                                            val versionParam = if (version != null) "&v=$version" else ""
                                            val mappedRegion = mapRegionForBriefing(region)
                                            android.util.Log.d(
                                                "PlaybackRepo",
                                                "monitorChaptersAndTranscripts: region=$region, mappedRegion=$mappedRegion, date=$date, version=$version",
                                            )

                                            val updatedEpisode =
                                                episode?.copy(
                                                    chaptersUrl = "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/chapters/$mappedRegion?d=$date$versionParam",
                                                    transcriptUrl = "${BuildConfig.BOXLORE_API_BASE_URL}/briefings/transcript/$mappedRegion?d=$date$versionParam",
                                                )
                                            android.util.Log.d(
                                                "PlaybackRepo",
                                                "monitorChaptersAndTranscripts: updatedEpisode chaptersUrl=${updatedEpisode?.chaptersUrl}, transcriptUrl=${updatedEpisode?.transcriptUrl}",
                                            )
                                            if (updatedEpisode != null) {
                                                val updatedQueue =
                                                    playerStateFlow.value.queue.map {
                                                        if (it.id == episodeId) updatedEpisode else it
                                                    }
                                                playerStateFlow.value =
                                                    playerStateFlow.value.copy(
                                                        currentEpisode = updatedEpisode,
                                                        queue = updatedQueue,
                                                    )
                                                episode = updatedEpisode
                                                android.util.Log.d(
                                                    "PlaybackRepo",
                                                    "monitorChaptersAndTranscripts: playerState updated with enriched briefing episode",
                                                )
                                            }
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("PlaybackRepo", "Failed to enrich briefing episode $episodeId", e)
                                    }
                                } else if (episode != null && (episode.chaptersUrl == null || episode.transcriptUrl == null)) {
                                    try {
                                        val enriched = podcastRepository.getEpisode(episodeId)
                                        if (enriched != null && playerStateFlow.value.currentEpisode?.id == episodeId) {
                                            val updatedEpisode =
                                                episode.copy(
                                                    description = if (episode.description.isBlank()) enriched.description else episode.description,
                                                    chaptersUrl = enriched.chaptersUrl,
                                                    transcriptUrl = enriched.transcriptUrl,
                                                    transcripts = enriched.transcripts,
                                                    persons = enriched.persons,
                                                    seasonNumber = enriched.seasonNumber,
                                                    episodeNumber = enriched.episodeNumber,
                                                    episodeType = enriched.episodeType,
                                                    podcastImageUrl = episode.podcastImageUrl ?: enriched.podcastImageUrl,
                                                    podcastTitle = episode.podcastTitle ?: enriched.podcastTitle,
                                                    podcastArtist = episode.podcastArtist ?: enriched.podcastArtist,
                                                )
                                            val updatedQueue =
                                                playerStateFlow.value.queue.map {
                                                    if (it.id == episodeId) updatedEpisode else it
                                                }
                                            playerStateFlow.value =
                                                playerStateFlow.value.copy(
                                                    currentEpisode = updatedEpisode,
                                                    queue = updatedQueue,
                                                )
                                            episode = updatedEpisode
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("PlaybackRepo", "Failed to enrich episode $episodeId", e)
                                    }
                                }

                                val chaptersUrl = episode?.chaptersUrl
                                val transcriptUrl = episode?.transcriptUrl

                                // Fetch chapters
                                if (chaptersUrl != null) {
                                    playerStateFlow.value =
                                        playerStateFlow.value.copy(
                                            isChaptersLoading = true,
                                            currentChapters = emptyList(),
                                            isChaptersNative = true,
                                            autoChaptersState = AutoTranscriptState.NONE,
                                        )
                                    launch {
                                        var chapters = ChapterRepository.getChapters(chaptersUrl)
                                        if (chapters.isEmpty()) {
                                            chapters = ChapterOfflineStorage.getOfflineChapters(context, episodeId)
                                        }
                                        if (chapters.isEmpty()) {
                                            chapters = ChapterRepository.parseChaptersFromDescription(episode.description)
                                        }
                                        if (playerStateFlow.value.currentEpisode?.id == episodeId) {
                                            playerStateFlow.value =
                                                playerStateFlow.value.copy(
                                                    currentChapters = chapters,
                                                    isChaptersLoading = false,
                                                )
                                        }
                                    }
                                } else {
                                    val offlineChapters = ChapterOfflineStorage.getOfflineChapters(context, episodeId)
                                    val parsedChapters = if (offlineChapters.isNotEmpty()) {
                                        offlineChapters
                                    } else {
                                        ChapterRepository.parseChaptersFromDescription(episode?.description)
                                    }
                                    if (parsedChapters.isNotEmpty()) {
                                        playerStateFlow.value =
                                            playerStateFlow.value.copy(
                                                currentChapters = parsedChapters,
                                                isChaptersLoading = false,
                                                isChaptersNative = true,
                                                autoChaptersState = AutoTranscriptState.NONE,
                                            )
                                    } else {
                                        val autoChapters = ChapterRepository.getCachedChapters("auto_${episode?.id}") ?: emptyList()
                                        playerStateFlow.value =
                                            playerStateFlow.value.copy(
                                                currentChapters = autoChapters,
                                                isChaptersLoading = false,
                                                isChaptersNative = false,
                                                autoChaptersState = if (autoChapters.isNotEmpty()) AutoTranscriptState.COMPLETED else playerStateFlow.value.autoChaptersState,
                                            )
                                    }
                                }

                                // Fetch transcript
                                if (transcriptUrl != null) {
                                    // RSS transcript available — fetch normally, no AI state
                                    playerStateFlow.value = playerStateFlow.value.copy(autoTranscriptState = AutoTranscriptState.NONE)
                                    launch {
                                        var transcript = TranscriptRepository.getTranscript(transcriptUrl)
                                        if (transcript.isEmpty()) {
                                            transcript = TranscriptOfflineStorage.getOfflineTranscript(context, episodeId)
                                        }
                                        if (playerStateFlow.value.currentEpisode?.id == episodeId) {
                                            playerStateFlow.value = playerStateFlow.value.copy(currentTranscript = transcript)
                                        }
                                    }
                                } else if (episode != null && episode.audioUrl.isNotEmpty()) {
                                    val offlineTranscript = TranscriptOfflineStorage.getOfflineTranscript(context, episodeId)
                                    playerStateFlow.value =
                                        playerStateFlow.value.copy(
                                            autoTranscriptState = AutoTranscriptState.NONE,
                                            currentTranscript = offlineTranscript,
                                        )
                                } else {
                                    playerStateFlow.value =
                                        playerStateFlow.value.copy(
                                            currentTranscript = emptyList(),
                                            autoTranscriptState = AutoTranscriptState.NONE,
                                        )
                                }
                            }
                    }
                }
        }
    }
}
