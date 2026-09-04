package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

internal class SameShowContinuationCoordinator(
    private val scope: CoroutineScope,
    private val playerState: StateFlow<PlayerState>,
    private val playerStateFlow: MutableStateFlow<PlayerState>,
    private val podcastRepository: PodcastRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val queueCoordinator: PlaybackQueueCoordinator,
) {
    @Volatile
    private var dismissedEpisodeId: String? = null

    @Volatile
    private var addedEpisodeId: String? = null

    @Volatile
    private var lastTrackId: String? = null

    fun startMonitoring() {
        scope.launch {
            combine(
                playerState.map { it.currentEpisode }.distinctUntilChanged(),
                userPreferencesRepository.sameShowQueueOnlyStream.distinctUntilChanged(),
            ) { currentEpisode, sameShowQueueOnly ->
                Pair(currentEpisode, sameShowQueueOnly)
            }.collectLatest { (currentEpisode, sameShowQueueOnly) ->
                evaluate(currentEpisode, sameShowQueueOnly)
            }
        }
    }

    internal suspend fun evaluate(
        currentEpisode: Episode?,
        sameShowQueueOnly: Boolean,
    ) {
        if (currentEpisode == null) {
            updateState(SameShowContinuationState.HIDDEN)
            return
        }

        if (currentEpisode.id != lastTrackId) {
            lastTrackId = currentEpisode.id
            dismissedEpisodeId = null
            addedEpisodeId = null
        }

        if (dismissedEpisodeId == currentEpisode.id || addedEpisodeId == currentEpisode.id) {
            updateState(SameShowContinuationState.HIDDEN)
            return
        }

        if (!SameShowContinuationLogic.canShowBanner(currentEpisode, sameShowQueueOnly)) {
            updateState(SameShowContinuationState.HIDDEN)
            return
        }

        val podcast =
            playerState.value.currentPodcast
                ?: currentEpisode.podcastId?.let { podId ->
                    runCatching { podcastRepository.getPodcastDetails(podId) }.getOrNull()
                        ?: Podcast(
                            id = podId,
                            title = currentEpisode.podcastTitle.orEmpty(),
                            artist = currentEpisode.podcastArtist.orEmpty(),
                            imageUrl = currentEpisode.podcastImageUrl.orEmpty(),
                        )
                }

        if (podcast == null) {
            updateState(SameShowContinuationState.HIDDEN)
            return
        }

        val sort = SameShowContinuationLogic.effectiveSort(podcast)
        val allEpisodes =
            runCatching {
                podcastRepository.getEpisodeWindow(podcast.id, currentEpisode.id, sort)
            }.getOrDefault(emptyList())

        val excludeIds =
            playerState.value.queue
                .map { it.id }
                .toSet()
        val candidates =
            SameShowContinuationLogic.computeCandidates(
                allEpisodes = allEpisodes,
                currentEpisode = currentEpisode,
                podcast = podcast,
                excludeEpisodeIds = excludeIds,
            )

        if (candidates.isEmpty()) {
            updateState(SameShowContinuationState.HIDDEN)
        } else {
            val title = podcast.title.ifBlank { currentEpisode.podcastTitle.orEmpty() }
            updateState(
                SameShowContinuationState(
                    visible = true,
                    podcastTitle = title,
                    availableCount = candidates.size,
                    nextEpisodes = candidates,
                ),
            )
        }
    }

    fun dismissBanner() {
        val currentId = playerState.value.currentEpisode?.id
        dismissedEpisodeId = currentId
        updateState(SameShowContinuationState.HIDDEN)
    }

    suspend fun addContinuationEpisodes(): Boolean {
        val currentState = playerState.value.sameShowContinuation
        val currentEpisode = playerState.value.currentEpisode
        if (!currentState.visible || currentState.nextEpisodes.isEmpty() || currentEpisode == null) return false

        val podcast =
            playerState.value.currentPodcast
                ?: currentEpisode.podcastId?.let {
                    runCatching { podcastRepository.getPodcastDetails(it) }.getOrNull()
                }
                ?: Podcast(
                    id = currentState.nextEpisodes.first().podcastId ?: currentEpisode.podcastId ?: "",
                    title = currentState.podcastTitle,
                    artist = currentEpisode.podcastArtist.orEmpty(),
                    imageUrl = currentEpisode.podcastImageUrl.orEmpty(),
                )

        val success = queueCoordinator.addEpisodesAfterCurrent(currentState.nextEpisodes, podcast)
        if (success) {
            addedEpisodeId = currentEpisode.id
            updateState(SameShowContinuationState.HIDDEN)
        }
        return success
    }

    private fun updateState(newState: SameShowContinuationState) {
        if (playerStateFlow.value.sameShowContinuation != newState) {
            playerStateFlow.value = playerStateFlow.value.copy(sameShowContinuation = newState)
        }
    }
}
