package cx.aswin.boxlore.core.playback

/**
 * When the player on this phone last played an episode to the end, in this run of boxlore. Replays
 * count each time; marking an episode played, a sync from another device or a backup never does.
 * Home asks the NPS survey and the review prompt only shortly after one. [onRecorded] (set by the app)
 * also keeps each finish on disk, so the moment outlives a restart.
 */
object PlaybackFinishes {
    @Volatile
    var lastAtMs: Long? = null
        private set

    /** Called with each finish's time, as it is recorded; must not block (the app saves it in the background). */
    @Volatile
    var onRecorded: ((Long) -> Unit)? = null

    internal fun record(atMs: Long) {
        lastAtMs = atMs
        onRecorded?.invoke(atMs)
    }

    /** Test hook: no finish in this run. */
    fun resetForTest() {
        lastAtMs = null
    }
}
