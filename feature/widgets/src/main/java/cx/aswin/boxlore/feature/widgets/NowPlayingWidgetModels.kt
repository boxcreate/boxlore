package cx.aswin.boxlore.feature.widgets

import kotlinx.serialization.Serializable

/** Playback facts exposed to the widget by `:app` — no [PlaybackRepository] in this module. */
data class WidgetPlaybackState(
    val episodeId: String? = null,
    val episodeTitle: String? = null,
    val podcastTitle: String? = null,
    val artworkUrl: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val seekForwardMs: Long = DEFAULT_SEEK_MS,
    val seekBackwardMs: Long = DEFAULT_SEEK_MS,
) {
    val hasEpisode: Boolean get() = !episodeId.isNullOrBlank()

    companion object {
        const val DEFAULT_SEEK_MS = 15_000L
    }
}

/** Persisted + rendered widget snapshot. */
@Serializable
data class NowPlayingWidgetSnapshot(
    val episodeId: String? = null,
    val episodeTitle: String = "",
    val podcastTitle: String = "",
    val artworkUrl: String? = null,
    val artworkCachePath: String? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val seekForwardMs: Long = WidgetPlaybackState.DEFAULT_SEEK_MS,
    val seekBackwardMs: Long = WidgetPlaybackState.DEFAULT_SEEK_MS,
    val updatedAtMs: Long = 0L,
) {
    val hasEpisode: Boolean get() = !episodeId.isNullOrBlank()
}

/** Picker-visible widget families. Kept independent from launcher size heuristics. */
enum class WidgetVariant {
    NOW_PLAYING,
    BAR,
    CONTROLS,
    CONTROLS_NEXT,
}

enum class WidgetControl {
    TOGGLE,
    PREVIOUS,
    NEXT,
    SKIP_BACK,
    SKIP_FORWARD,
    OPEN_APP,
}
