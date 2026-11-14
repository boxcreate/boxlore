package cx.aswin.boxlore.wear.data

import cx.aswin.boxlore.wear.model.NowPlaying
import cx.aswin.boxlore.wear.model.SleepTimerChoice
import cx.aswin.boxlore.wear.model.WatchEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

private val NoPhoneQueue: StateFlow<List<WatchEpisode>?> = MutableStateFlow(null)

/** What the Home card and the player read and control, wherever the audio is playing. */
interface WatchPlayback {
    /** Null when nothing is playing and no earlier state is known. */
    val nowPlaying: StateFlow<NowPlaying?>

    /**
     * The phone's queue while the phone's player is the one shown, for Up next to list instead of
     * the watch's own. Null whenever the watch's own queue is the one that applies.
     */
    val phoneUpNext: StateFlow<List<WatchEpisode>?>
        get() = NoPhoneQueue

    /** Whether the phone is in reach and playing right now, so starting something here needs a choice. */
    val phoneIsPlaying: Boolean
        get() = false

    /** Starts [episode] on the phone instead of here. */
    fun playOnPhone(episode: WatchEpisode) = Unit

    /** Whether [playDailyMix] can start anything on this watch. */
    val hasDailyMix: Boolean

    /** Whether [switchPlace] can move playback between the watch and the phone. */
    val canSwitchPlace: Boolean

    fun play(episodeId: String)

    /** Starts the listener's Daily Mix; does nothing where there is none. */
    fun playDailyMix() = Unit

    fun togglePlayPause()

    /** Stops playback; does nothing when it is already stopped. */
    fun pause()

    fun seekBack()

    fun seekForward()

    fun setSpeed(speed: Float)

    fun setSleepTimer(choice: SleepTimerChoice)

    /** Moves the current episode to the phone when it is on the watch, and the other way round. Needs the phone link. */
    fun switchPlace() = Unit
}

/** Playback with nothing loaded and no way to start anything. */
class IdleWatchPlayback : WatchPlayback {
    override val nowPlaying: StateFlow<NowPlaying?> = MutableStateFlow(null)
    override val hasDailyMix: Boolean = false
    override val canSwitchPlace: Boolean = false

    override fun play(episodeId: String) = Unit

    override fun togglePlayPause() = Unit

    override fun pause() = Unit

    override fun seekBack() = Unit

    override fun seekForward() = Unit

    override fun setSpeed(speed: Float) = Unit

    override fun setSleepTimer(choice: SleepTimerChoice) = Unit
}
