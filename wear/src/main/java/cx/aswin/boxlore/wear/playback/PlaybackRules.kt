package cx.aswin.boxlore.wear.playback

import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.model.PlaybackPlace
import cx.aswin.boxlore.wear.model.SleepTimerChoice
import cx.aswin.boxlore.wear.model.WatchEpisode

/** What the player reports at one moment, in the player's own units. */
internal data class PlayerSnapshot(
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val speed: Float,
    /** The player gave up on this episode with an error. */
    val failed: Boolean = false,
)

/** Pure rules of watch playback, kept apart from the player so they can be tested on their own. */
internal object PlaybackRules {
    private const val MINUTE_MS = 60_000L

    /** What the Home card and player show for [episode] at [snapshot]. */
    fun nowPlaying(episode: WatchEpisode, snapshot: PlayerSnapshot, sleepTimer: SleepTimerChoice, upNextCount: Int): NowPlaying {
        val durationMs = if (snapshot.durationMs > 0) snapshot.durationMs else episode.durationMinutes * MINUTE_MS
        val position = snapshot.positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L))
        val progress = if (durationMs > 0) position.toFloat() / durationMs else 0f
        val leftMs = (durationMs - position).coerceAtLeast(0L)
        return NowPlaying(
            episode = episode,
            isPlaying = snapshot.isPlaying && !snapshot.failed,
            place = PlaybackPlace.Watch,
            progress = progress,
            minutesLeft = if (leftMs == 0L) 0 else ((leftMs + MINUTE_MS - 1) / MINUTE_MS).toInt(),
            speed = snapshot.speed,
            sleepTimer = sleepTimer,
            upNextCount = upNextCount,
            failed = snapshot.failed,
        )
    }

    /** The paused card for the episode last played, shown before anything is loaded in the player. */
    fun resting(episode: WatchEpisode, positionMs: Long, upNextCount: Int): NowPlaying =
        nowPlaying(
            episode = episode,
            snapshot = PlayerSnapshot(positionMs, episode.durationMinutes * MINUTE_MS, isPlaying = false, speed = 1f),
            sleepTimer = SleepTimerChoice.Off,
            upNextCount = upNextCount,
        )

    /** How long a timed sleep choice runs, or null for Off and End of episode. */
    fun sleepDelayMs(choice: SleepTimerChoice): Long? = choice.minutes?.times(MINUTE_MS)

    /** Whether the next queued episode should start when one ends. */
    fun continuesAfterEnd(choice: SleepTimerChoice): Boolean = choice != SleepTimerChoice.EndOfEpisode
}
