package cx.aswin.boxlore.core.playback

import android.content.Context
import android.media.MediaRouter2
import androidx.media3.cast.Cast
import androidx.media3.common.DeviceInfo

internal object PlaybackRouteResolver {
    fun resolve(context: Context, deviceInfo: DeviceInfo, volume: Int, isMuted: Boolean,): PlaybackRouteState {
        val isRemote = deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE
        val deviceName =
            if (isRemote) {
                resolveDeviceName(context, deviceInfo.routingControllerId)
            } else {
                null
            }
        return resolveState(
            isRemote = isRemote,
            deviceName = deviceName,
            volume = volume,
            minimumVolume = deviceInfo.minVolume,
            maximumVolume = deviceInfo.maxVolume,
            isMuted = isMuted,
        )
    }

    internal fun resolveState(
        isRemote: Boolean,
        deviceName: String?,
        volume: Int,
        minimumVolume: Int,
        maximumVolume: Int,
        isMuted: Boolean,
    ): PlaybackRouteState {
        val normalizedMaximum = maximumVolume.coerceAtLeast(minimumVolume)
        return PlaybackRouteState(
            isRemote = isRemote,
            deviceName = deviceName,
            volume = volume.coerceIn(minimumVolume, normalizedMaximum),
            minimumVolume = minimumVolume,
            maximumVolume = normalizedMaximum,
            isMuted = isMuted,
        )
    }

    private fun resolveDeviceName(context: Context, routingControllerId: String?,): String? = runCatching {
        Cast
            .getSingletonInstance(context)
            .currentCastSession
            ?.castDevice
            ?.friendlyName
            ?: routingControllerId?.let { controllerId ->
                MediaRouter2
                    .getInstance(context)
                    .getController(controllerId)
                    ?.selectedRoutes
                    ?.firstOrNull()
                    ?.name
                    ?.toString()
            }
    }.getOrNull()
}
