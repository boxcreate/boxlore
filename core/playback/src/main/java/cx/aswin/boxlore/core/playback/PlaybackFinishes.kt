package cx.aswin.boxlore.core.playback

/**
 * When the player on this phone last played an episode to the end, in this run of boxlore. Replays
 * count each time; marking an episode played, a sync from another device or a backup never does.
 * Home asks the NPS survey and the review prompt only shortly after one.
 */
object PlaybackFinishes {
    @Volatile
    var lastAtMs: Long? = null
        private set

    internal fun record(atMs: Long) {
        lastAtMs = atMs
    }

    /** Test hook: no finish in this run. */
    fun resetForTest() {
        lastAtMs = null
    }
}
