package cx.aswin.boxlore.core.playback

import android.util.Log
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import cx.aswin.boxlore.core.model.PlaybackEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

/** Transport / seek / speed [PlaybackRepository] API. */
fun PlaybackRepository.isTransportReady(): Boolean = controller?.isConnected == true

fun PlaybackRepository.resume(entryPointContext: android.os.Bundle? = null) = transportHelper.resume(entryPointContext)

fun PlaybackRepository.skipToEpisode(
    index: Int,
    entryPoint: PlaybackEntryPoint = PlaybackEntryPoint.GENERIC,
    sourceContext: android.os.Bundle? = null,
) = transportHelper.skipToEpisode(index, entryPoint, sourceContext)

fun PlaybackRepository.skipToNextEpisode() = transportHelper.skipToNextEpisode()

fun PlaybackRepository.skipToPreviousEpisode() = transportHelper.skipToPreviousEpisode()

fun PlaybackRepository.isShuffleEnabled(): Boolean = controller?.takeIf { it.isConnected }?.shuffleModeEnabled == true

fun PlaybackRepository.currentRepeatMode(): Int = controller?.takeIf { it.isConnected }?.repeatMode ?: Player.REPEAT_MODE_OFF

fun PlaybackRepository.toggleShuffle(): Boolean {
    val mediaController = controller?.takeIf { it.isConnected } ?: return false
    val enabled = !mediaController.shuffleModeEnabled
    mediaController.shuffleModeEnabled = enabled
    return enabled
}

fun PlaybackRepository.cycleRepeatMode(): Int {
    val mediaController = controller?.takeIf { it.isConnected } ?: return Player.REPEAT_MODE_OFF
    val nextMode = PlaybackRepeatModePolicy.next(mediaController.repeatMode)
    mediaController.repeatMode = nextMode
    return nextMode
}

fun PlaybackRepository.togglePlayPause(entryPointContext: android.os.Bundle? = null) {
    val mediaController = controller?.takeIf { it.isConnected } ?: return
    if (mediaController.isPlaying) {
        mediaController.pause()
    } else {
        resume(entryPointContext)
    }
}

internal object PlaybackRepeatModePolicy {
    fun next(mode: Int): Int =
        when (mode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
}

fun PlaybackRepository.pause() {
    controller?.takeIf { it.isConnected }?.pause()
}

/**
 * Stops remote playback before ending the Cast application. Ending the session by itself can
 * detach the sender while an Android TV audio pipeline keeps playing without a visible receiver.
 */
fun PlaybackRepository.stopCasting() {
    val plan =
        CastStopPolicy.plan(
            isRemote = playerState.value.playbackRoute.isRemote || hasActiveCastSession == true,
        )
    if (!plan.endReceiverSession) return

    if (plan.stopRemotePlayback) {
        controller?.takeIf { it.isConnected }?.stop()
    }
    synchronizeCastSession(isActive = false)
    repositoryScope.launch {
        if (plan.stopRemotePlayback) delay(CastStopPolicy.REMOTE_STOP_GRACE_MS)
        endCurrentCastSession(stopReceiverApplication = true)
    }
}

/**
 * Reconciles Cast framework session ownership with Media3's device route. Media3 can retain a
 * remote [androidx.media3.common.DeviceInfo] briefly after a failed wake/reconnect; an explicit
 * inactive session must win so the phone does not remain in a disabled Cast UI.
 */
fun PlaybackRepository.synchronizeCastSession(isActive: Boolean?) {
    hasActiveCastSession = isActive
    if (isActive == false) {
        playerStateFlow.value =
            playerStateFlow.value.copy(
                playbackRoute = PlaybackRouteState(),
            )
        return
    }
    if (isActive == null) return

    repositoryScope.launch {
        delay(CastSessionSyncPolicy.ROUTE_REFRESH_DELAY_MS)
        controllerBridge?.syncPlaybackRoute()
    }
}

internal object CastSessionSyncPolicy {
    const val ROUTE_REFRESH_DELAY_MS = 250L

    fun shouldAcceptRemoteRoute(hasActiveSession: Boolean?): Boolean = hasActiveSession != false
}

internal data class CastStopPlan(
    val stopRemotePlayback: Boolean,
    val endReceiverSession: Boolean,
)

internal object CastStopPolicy {
    const val REMOTE_STOP_GRACE_MS = 250L

    fun plan(isRemote: Boolean): CastStopPlan =
        CastStopPlan(
            stopRemotePlayback = isRemote,
            endReceiverSession = isRemote,
        )
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
    controller?.takeIf { it.isConnected }?.playbackParameters = PlaybackParameters(sanitized)
    playerStateFlow.value = playerStateFlow.value.copy(playbackSpeed = sanitized)
    repositoryScope.launch {
        try {
            userPreferencesRepository.setPlaybackSpeed(sanitized)
        } catch (exception: IOException) {
            Log.w("PlaybackRepo", "Unable to persist playback speed", exception)
        }
    }
}
