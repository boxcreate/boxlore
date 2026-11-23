package cx.aswin.boxlore.tv.model

import kotlinx.serialization.Serializable

/** A show as the TV lists it. [newEpisodes] is how many episodes the listener has not started. */
@Serializable
data class TvShow(
    val id: String,
    val title: String,
    val publisher: String,
    val artworkUrl: String?,
    val description: String = "",
    /** Genres in the order the feed lists them, e.g. "History, Culture, Society". */
    val genres: List<String> = emptyList(),
    val newEpisodes: Int = 0,
    val subscribed: Boolean = true,
)

@Serializable
data class TvEpisode(
    val id: String,
    val showId: String,
    val title: String,
    val showTitle: String,
    val artworkUrl: String?,
    val durationMinutes: Int,
    val releasedAtMillis: Long? = null,
    /** Set once the listener has started the episode. */
    val minutesLeft: Int? = null,
    val played: Boolean = false,
    val liked: Boolean = false,
    val description: String = "",
    /** Links from the show notes, shown as chips that open on the phone. */
    val links: List<String> = emptyList(),
    /** True when the feed's enclosure is a video. */
    val video: Boolean = false,
    /** Where the audio streams from; null for sample episodes, which have none. */
    val audioUrl: String? = null,
    /** The feed's own key for the episode: with the show, its identity on every boxlore device. */
    val catalogKey: String? = null,
) {
    /** Played share of the episode, from 0 to 1; 0 before it starts. */
    val progress: Float
        get() {
            val left = minutesLeft ?: return if (played) 1f else 0f
            if (durationMinutes <= 0) return 0f
            return ((durationMinutes - left).toFloat() / durationMinutes).coerceIn(0f, 1f)
        }
}

/** What the Resume card and the player show. */
data class NowPlaying(
    val episode: TvEpisode,
    val isPlaying: Boolean,
    /** Played share of the episode, from 0 to 1. */
    val progress: Float,
    val elapsedSeconds: Int,
    val minutesLeft: Int,
    val speed: Float = 1f,
    val sleepTimer: SleepTimerChoice = SleepTimerChoice.Off,
)

/** The sleep timer choices the phone player offers, in the same order. */
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

    /** "1" for whole speeds, "1.25" otherwise. */
    fun label(speed: Float): String =
        if (speed % 1f == 0f) speed.toInt().toString() else speed.toString().trimEnd('0').trimEnd('.')
}

/** The phone's Your shows sort choices, in the phone's order. Manual keeps the order set on the phone. */
enum class ShowSort { Smart, RecentlyUpdated, AToZ, MostListened, Manual }

/** What a search found, or why it found nothing. */
sealed interface TvSearchResult {
    val query: String

    data class Found(override val query: String, val shows: List<TvShow>, val episodes: List<TvEpisode>) : TvSearchResult

    data class NoConnection(override val query: String) : TvSearchResult
}
