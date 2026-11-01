package cx.aswin.boxlore.core.playback

/**
 * Lets each cause of a playback error through once per episode until the next listen starts, so a
 * retry loop sends one `playback_error` instead of hundreds. A later failure after a good listen
 * counts again.
 */
internal class PlaybackErrorOnce {
    private var episodeId: String? = null
    private val causes = mutableSetOf<String>()

    /** True the first time [cause] fails for [episodeId] since the last [reset]. */
    @Synchronized
    fun firstTime(episodeId: String?, cause: String,): Boolean {
        if (episodeId != this.episodeId) {
            this.episodeId = episodeId
            causes.clear()
        }
        return causes.add(cause)
    }

    /** A listen started: errors after it are news again. */
    @Synchronized
    fun reset() {
        episodeId = null
        causes.clear()
    }
}
