package cx.aswin.boxlore.feature.info

import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackControlSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Saves a show's own speed and trim silence from "Playback for this show" and shows the change on the
 * page. The playback service follows the stored row, so a show that is playing picks it up at once.
 * A control reports only when its value changes.
 */
internal class PodcastShowPlaybackWriter(
    private val scope: CoroutineScope,
    private val subscriptionRepository: SubscriptionRepository,
    private val uiState: MutableStateFlow<PodcastInfoUiState>,
) {
    fun setSpeed(state: PodcastInfoUiState.Success, speed: Float?) {
        val value = speed?.let { PlaybackControlSync.sanitizePlaybackSpeed(it) }
        if (value == state.podcast.playbackSpeedOverride) return
        val podcastId = state.podcast.id
        scope.launch {
            subscriptionRepository.setPlaybackSpeedOverride(podcastId, value)
            updatePodcast(podcastId) { it.copy(playbackSpeedOverride = value) }
            AnalyticsHelper.trackSettingsInteraction("podcast_speed_override_changed", "$podcastId:${value ?: "default"}")
        }
    }

    fun setTrimSilence(state: PodcastInfoUiState.Success, enabled: Boolean?) {
        if (enabled == state.podcast.skipSilenceOverride) return
        val podcastId = state.podcast.id
        scope.launch {
            subscriptionRepository.setSkipSilenceOverride(podcastId, enabled)
            updatePodcast(podcastId) { it.copy(skipSilenceOverride = enabled) }
            val value = when (enabled) {
                null -> "default"
                true -> "on"
                false -> "off"
            }
            AnalyticsHelper.trackSettingsInteraction("podcast_trim_silence_override_changed", "$podcastId:$value")
        }
    }

    /**
     * [podcast] with the stored row's own speed, trim silence and skips, shown on the page too. The
     * player changes them while this page stays open, and a follow writes the page's values back.
     */
    suspend fun refresh(podcast: Podcast): Podcast {
        val row = subscriptionRepository.getPodcastEntity(podcast.id) ?: return podcast
        val stored: (Podcast) -> Podcast = {
            it.copy(
                skipBeginningOverrideMs = row.skipBeginningOverrideMs,
                skipEndingOverrideMs = row.skipEndingOverrideMs,
                playbackSpeedOverride = row.playbackSpeedOverride,
                skipSilenceOverride = row.skipSilenceOverride,
            )
        }
        updatePodcast(podcast.id, stored)
        return stored(podcast)
    }

    private fun updatePodcast(podcastId: String, change: (Podcast) -> Podcast) {
        uiState.update { current ->
            val success = current as? PodcastInfoUiState.Success
            if (success?.podcast?.id == podcastId) success.copy(podcast = change(success.podcast)) else current
        }
    }
}
