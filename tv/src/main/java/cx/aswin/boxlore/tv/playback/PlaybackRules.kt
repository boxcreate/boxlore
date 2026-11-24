package cx.aswin.boxlore.tv.playback

import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.SleepTimerChoice
import cx.aswin.boxlore.tv.model.TvEpisode

/** What the player reports at one moment, in the player's own units. */
internal data class PlayerSnapshot(
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val speed: Float,
)

/** Pure rules of TV playback, kept apart from the player so they can be tested on their own. */
internal object PlaybackRules {
    private const val MINUTE_MS = 60_000L
    private const val SECOND_MS = 1_000L

    /** What Home's Resume card and the player show for [episode] at [snapshot]. */
    fun nowPlaying(episode: TvEpisode, snapshot: PlayerSnapshot, sleepTimer: SleepTimerChoice): NowPlaying {
        val durationMs = if (snapshot.durationMs > 0) snapshot.durationMs else episode.durationMinutes * MINUTE_MS
        val position = snapshot.positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val leftMs = (durationMs - position).coerceAtLeast(0L)
        return NowPlaying(
            episode = episode,
            isPlaying = snapshot.isPlaying,
            progress = if (durationMs > 0) position.toFloat() / durationMs else 0f,
            elapsedSeconds = (position / SECOND_MS).toInt(),
            minutesLeft = ((leftMs + MINUTE_MS - 1) / MINUTE_MS).toInt(),
            speed = snapshot.speed,
            sleepTimer = sleepTimer,
        )
    }

    /** The paused card for the episode last played, shown before anything is loaded in the player. */
    fun resting(episode: TvEpisode, positionMs: Long): NowPlaying =
        nowPlaying(episode, PlayerSnapshot(positionMs, episode.durationMinutes * MINUTE_MS, isPlaying = false, speed = 1f), SleepTimerChoice.Off).copy(loaded = false)

    /** How long a timed sleep choice runs, or null for Off and End of episode. */
    fun sleepDelayMs(choice: SleepTimerChoice): Long? = choice.minutes?.times(MINUTE_MS)

    /**
     * Whether starting [startingId] first saves where the player is. A player that stands still
     * saved its place when it stopped, and saving the same episode again could put an older
     * place back over a newer one.
     */
    fun savesPlaceBeforeStart(loadedId: String?, startingId: String, isPlaying: Boolean): Boolean = isPlaying || loadedId != startingId

    /** Whether a jump in position is saved at once: a still player saves nothing more by itself. */
    fun savesPlaceOnJump(isSeek: Boolean, isPlaying: Boolean): Boolean = isSeek && !isPlaying

    /** Whether the next episode in Up next starts when one ends. */
    fun continuesAfterEnd(choice: SleepTimerChoice): Boolean = choice != SleepTimerChoice.EndOfEpisode
}
