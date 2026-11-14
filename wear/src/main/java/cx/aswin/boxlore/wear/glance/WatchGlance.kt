package cx.aswin.boxlore.wear.glance

import cx.aswin.boxlore.wear.data.WatchLibraryState

/** What the tile and the watch-face shortcut show: the episode to jump back into. */
data class WatchGlance(
    val episodeId: String,
    val title: String,
    val showTitle: String,
    val minutesLeft: Int,
    /** Played share of the episode, from 0 to 1. */
    val progress: Float,
    /** The episode's picture; null when it has none. */
    val artworkUrl: String? = null,
)

/** What a tap on Resume, in the tile or on the watch face, leads to. */
enum class ResumeTap { ShowPlayer, PlayHere, AskWhere }

/** How the saved library becomes a [WatchGlance], and what its short texts say. */
internal object GlanceRules {
    private const val MINUTE_MS = 60_000L

    /**
     * The episode the listener was last in, or null when nothing is in progress. It reads the saved
     * library only, so the tile and the watch face work with the app closed and no phone in reach.
     */
    fun from(state: WatchLibraryState): WatchGlance? {
        val episode = state.jumpBackIn.firstOrNull() ?: return null
        val left = episode.minutesLeft ?: return null
        val total = if (episode.durationMs > 0L) episode.durationMs else episode.durationMinutes * MINUTE_MS
        val position = state.positions[episode.id] ?: 0L
        val progress = if (total > 0L) (position.toFloat() / total).coerceIn(0f, 1f) else 0f
        return WatchGlance(episode.id, episode.title, episode.showTitle, left, progress, episode.artworkUrl)
    }

    /** Changes exactly when the tile and the watch face have something new to draw. */
    fun key(glance: WatchGlance?): String = glance?.let { "${it.episodeId}|${it.minutesLeft}" }.orEmpty()

    /** The show's name before the time left; the time alone when the show is not known. */
    fun detail(showTitle: String, timeLeft: String, joined: (String, String) -> String): String =
        if (showTitle.isBlank()) timeLeft else joined(showTitle, timeLeft)

    /**
     * Resume carries on with the episode the tile named. One already playing here needs nothing
     * but its player; while the phone plays, the listener picks the device, as anywhere in the app.
     */
    fun onResume(playingHereId: String?, episodeId: String, phoneIsPlaying: Boolean): ResumeTap =
        when {
            playingHereId == episodeId -> ResumeTap.ShowPlayer
            phoneIsPlaying -> ResumeTap.AskWhere
            else -> ResumeTap.PlayHere
        }

    /** The short slot on a watch face has room for a few characters: "26m". */
    fun shortTime(minutesLeft: Int): String = "${minutesLeft}m"
}
