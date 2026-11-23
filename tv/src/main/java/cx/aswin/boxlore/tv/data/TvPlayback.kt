package cx.aswin.boxlore.tv.data

import cx.aswin.boxlore.tv.model.NowPlaying
import cx.aswin.boxlore.tv.model.SleepTimerChoice
import kotlinx.coroutines.flow.StateFlow

/** The TV's player as the screens see it. The remote's media keys reach it through the same calls. */
interface TvPlayback {
    /** What is loaded, or null when nothing has played yet. */
    val nowPlaying: StateFlow<NowPlaying?>

    fun play(episodeId: String)

    /** Plays the Daily Mix from its first episode. */
    fun playDailyMix()

    fun togglePlayPause()

    fun seekBack()

    fun seekForward()

    fun setSpeed(speed: Float)

    fun setSleepTimer(choice: SleepTimerChoice)

    companion object {
        /** The phone player's defaults. */
        const val SEEK_BACK_SECONDS = 10
        const val SEEK_FORWARD_SECONDS = 30
    }
}
