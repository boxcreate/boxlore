package cx.aswin.boxlore.core.playback

import android.util.Log
import androidx.media3.common.PlaybackParameters
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import kotlinx.coroutines.launch
import java.io.IOException

/** Transport / seek / speed [PlaybackRepository] API. */
fun PlaybackRepository.resume(entryPointContext: android.os.Bundle? = null) = transportHelper.resume(entryPointContext)

fun PlaybackRepository.skipToEpisode(
    index: Int,
    entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
    sourceContext: android.os.Bundle? = null,
) = transportHelper.skipToEpisode(index, entryPoint, sourceContext)

fun PlaybackRepository.skipToNextEpisode() = transportHelper.skipToNextEpisode()

fun PlaybackRepository.skipToPreviousEpisode() = transportHelper.skipToPreviousEpisode()

fun PlaybackRepository.togglePlayPause(entryPointContext: android.os.Bundle? = null) {
    val mediaController = controller ?: return
    if (mediaController.isPlaying) {
        mediaController.pause()
    } else {
        resume(entryPointContext)
    }
}

fun PlaybackRepository.pause() {
    controller?.pause()
}

fun PlaybackRepository.skipForward() {
    cx.aswin.boxlore.core.analytics.AnalyticsHelper
        .setSeekSource("seek_forward")
    val state = playerState.value
    val incrementMs = PlaybackSkipPolicy.sanitizeSeekForward(state.seekForwardMs)
    seekTo((state.position + incrementMs).coerceAtMost(state.duration))
}

fun PlaybackRepository.skipBackward() {
    cx.aswin.boxlore.core.analytics.AnalyticsHelper
        .setSeekSource("seek_backward")
    val state = playerState.value
    val incrementMs = PlaybackSkipPolicy.sanitizeSeekBackward(state.seekBackwardMs)
    seekTo((state.position - incrementMs).coerceAtLeast(0))
}

fun PlaybackRepository.setPlaybackSpeed(speed: Float) {
    val sanitized = PlaybackControlSync.sanitizePlaybackSpeed(speed)
    controller?.playbackParameters = PlaybackParameters(sanitized)
    playerStateFlow.value = playerStateFlow.value.copy(playbackSpeed = sanitized)
    repositoryScope.launch {
        try {
            userPreferencesRepository.setPlaybackSpeed(sanitized)
        } catch (exception: IOException) {
            Log.w("PlaybackRepo", "Unable to persist playback speed", exception)
        }
    }
}
