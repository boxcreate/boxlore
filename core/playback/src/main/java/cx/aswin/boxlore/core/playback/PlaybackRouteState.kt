package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.model.text.UiText

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

    /** The device's own name, or "Cast device" in English; show [displayNameText] to listeners. */
    val displayName: String
        get() = deviceName?.takeIf { it.isNotBlank() } ?: "Cast device"

    /** The device's own name, or a localized "Cast device" when it reported none. */
    val displayNameText: UiText
        get() = deviceName?.takeIf { it.isNotBlank() }?.let(UiText::raw) ?: UiText.res(R.string.playback_cast_device_fallback)
}
