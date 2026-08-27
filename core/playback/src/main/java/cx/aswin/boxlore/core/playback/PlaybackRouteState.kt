package cx.aswin.boxlore.core.playback

data class PlaybackRouteState(
    val isRemote: Boolean = false,
    val deviceName: String? = null,
    val volume: Int = 0,
    val minimumVolume: Int = 0,
    val maximumVolume: Int = 0,
    val isMuted: Boolean = false,
) {
    val canControlVolume: Boolean
        get() = isRemote && maximumVolume > minimumVolume

    val displayName: String
        get() = deviceName?.takeIf { it.isNotBlank() } ?: "Cast device"
}
