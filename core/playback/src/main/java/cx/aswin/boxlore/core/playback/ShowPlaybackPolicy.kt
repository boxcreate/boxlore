package cx.aswin.boxlore.core.playback

/**
 * The speeds boxlore offers, and how a show's own speed and trim silence combine with the app's.
 * The player and the show page read the same list, so a show's speed is always one the player shows.
 */
object ShowPlaybackPolicy {
    val SPEED_OPTIONS: List<Float> = listOf(0.5f, 0.8f, 0.9f, 1f, 1.1f, 1.2f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f)

    /** Where a speed or trim silence change from the player is saved. */
    enum class ChangeTarget { SHOW, APP }

    /** "1×", "1.25×". */
    fun speedLabel(speed: Float): String = if (speed == speed.toInt().toFloat()) "${speed.toInt()}×" else "$speed×"

    /** The show's own speed when it has one, else the app speed; an unusable stored speed falls back to the app speed. */
    fun effectiveSpeed(appSpeed: Float, showSpeed: Float?): Float {
        val app = PlaybackControlSync.sanitizePlaybackSpeed(appSpeed)
        return showSpeed?.let { PlaybackControlSync.sanitizePlaybackSpeed(it, fallback = app) } ?: app
    }

    /** The show's own trim silence setting when it has one, else the app's. */
    fun effectiveTrimSilence(appTrimSilence: Boolean, showTrimSilence: Boolean?): Boolean = showTrimSilence ?: appTrimSilence

    /** A player change saves to the playing show only while that show has its own setting. */
    fun changeTarget(podcastId: String?, showValue: Any?): ChangeTarget = if (!podcastId.isNullOrBlank() && showValue != null) ChangeTarget.SHOW else ChangeTarget.APP
}

/**
 * The playing show's own speed and trim silence, and the app's trim silence setting, as the player shows them.
 * [podcastId] is null while nothing is playing.
 */
data class PlaybackAudioState(
    val podcastId: String? = null,
    val showSpeed: Float? = null,
    val showTrimSilence: Boolean? = null,
    val appTrimSilence: Boolean = false,
) {
    /** Whether silence is trimmed for what's playing. */
    val trimSilence: Boolean
        get() = ShowPlaybackPolicy.effectiveTrimSilence(appTrimSilence, showTrimSilence)

    /** Whether the playing show keeps its own speed, so a speed change is saved for that show. */
    val hasShowSpeed: Boolean
        get() = ShowPlaybackPolicy.changeTarget(podcastId, showSpeed) == ShowPlaybackPolicy.ChangeTarget.SHOW

    /** Whether the playing show keeps its own trim silence setting. */
    val hasShowTrimSilence: Boolean
        get() = ShowPlaybackPolicy.changeTarget(podcastId, showTrimSilence) == ShowPlaybackPolicy.ChangeTarget.SHOW
}
