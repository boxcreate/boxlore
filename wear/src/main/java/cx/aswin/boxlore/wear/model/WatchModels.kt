package cx.aswin.boxlore.wear.model

import kotlinx.serialization.Serializable

/** A show as the watch lists it. [newEpisodes] is how many episodes the listener has not started. */
@Serializable
data class WatchShow(
    val id: String,
    val title: String,
    val publisher: String,
    val artworkUrl: String?,
    val newEpisodes: Int = 0,
    val subscribed: Boolean = true,
)

/** Whether an episode's audio is stored on this watch. */
@Serializable
sealed interface WatchDownload {
    @Serializable
    data object None : WatchDownload

    /** [fraction] runs from 0 to 1. */
    @Serializable
    data class InProgress(val fraction: Float) : WatchDownload

    @Serializable
    data object Done : WatchDownload
}

@Serializable
data class WatchEpisode(
    val id: String,
    val showId: String,
    val title: String,
    val showTitle: String,
    val artworkUrl: String?,
    val durationMinutes: Int,
    val releasedAtMillis: Long? = null,
    /** Set once the listener has started the episode. */
    val minutesLeft: Int? = null,
    val download: WatchDownload = WatchDownload.None,
    /** Where the audio streams from; null when the episode has none. */
    val audioUrl: String? = null,
    /** Size of the audio stored on this watch, in bytes; 0 when none is stored. */
    val storedBytes: Long = 0L,
    /** When the audio was stored, and when it was last played, as epoch milliseconds. */
    val storedAtMillis: Long = 0L,
    val lastPlayedAtMillis: Long = 0L,
    /** The exact length once a player has reported it, in milliseconds; 0 until then. */
    val durationMs: Long = 0L,
    /** The feed's own key for this episode, which lets the phone find its copy; null when unknown. */
    val catalogKey: String? = null,
)

/**
 * A follow or unfollow made on the watch that the phone has not confirmed yet. [revision] is the
 * library's revision when it was made.
 */
@Serializable
data class PendingShowChange(
    val revision: Long,
    val subscribed: Boolean,
    val atMillis: Long,
    val show: WatchShow,
)

/** Where the audio for [NowPlaying] is coming out. */
enum class PlaybackPlace { Watch, Phone }

/**
 * What the Home card and the player show. It mirrors the phone's mini player while the phone is
 * the one playing, and keeps the last state the watch heard about when the phone is out of reach.
 */
data class NowPlaying(
    val episode: WatchEpisode,
    val isPlaying: Boolean,
    val place: PlaybackPlace,
    /** Played share of the episode, from 0 to 1. */
    val progress: Float,
    val minutesLeft: Int,
    val speed: Float = 1f,
    val seekBackSeconds: Int = DEFAULT_SEEK_BACK_SECONDS,
    val seekForwardSeconds: Int = DEFAULT_SEEK_FORWARD_SECONDS,
    val sleepTimer: SleepTimerChoice = SleepTimerChoice.Off,
    val upNextCount: Int = 0,
    /** The player could not play this episode, usually for lack of a connection. */
    val failed: Boolean = false,
) {
    companion object {
        const val DEFAULT_SEEK_BACK_SECONDS = 10
        const val DEFAULT_SEEK_FORWARD_SECONDS = 30
    }
}

/** The sleep timer choices the phone player offers. */
enum class SleepTimerChoice(val minutes: Int?) {
    Off(null),
    Minutes15(15),
    Minutes30(30),
    Minutes45(45),
    Hour1(60),
    Hours2(120),
    EndOfEpisode(null),
}

/** The playback speeds the phone player offers. */
object PlaybackSpeeds {
    val all: List<Float> = listOf(0.5f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

    /** The next listed speed above or below [current]; stays at the ends. */
    fun step(current: Float, up: Boolean): Float =
        if (up) {
            all.firstOrNull { it > current + TOLERANCE } ?: all.last()
        } else {
            all.lastOrNull { it < current - TOLERANCE } ?: all.first()
        }

    private const val TOLERANCE = 0.001f

    /** "1" for whole speeds, "1.25" otherwise. */
    fun label(speed: Float): String =
        if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString().trimEnd('0')
}

/** How Your shows is laid out; the listener picks one in Settings. */
enum class ShowsLayout { Honeycomb, List }

sealed interface WatchSearchResult {
    data class Shows(val query: String, val shows: List<WatchShow>) : WatchSearchResult

    /** The watch could not reach the search service. */
    data class NoConnection(val query: String) : WatchSearchResult
}
