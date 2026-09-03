package cx.aswin.boxlore.core.playback

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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

fun PlaybackRepository.setOutputVolume(volume: Int) {
    val route = playerStateFlow.value.playbackRoute
    val controller = mediaHandle.controller ?: return
    val targetVolume =
        PlaybackOutputVolumePolicy.targetVolume(
            requestedVolume = volume,
            route = route,
            commandAvailable = controller.isCommandAvailable(androidx.media3.common.Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS),
        ) ?: return
    controller.setDeviceVolume(
        targetVolume,
        androidx.media3.common.C.VOLUME_FLAG_SHOW_UI,
    )
}
