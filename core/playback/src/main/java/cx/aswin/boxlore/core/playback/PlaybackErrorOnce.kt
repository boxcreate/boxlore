package cx.aswin.boxlore.core.playback

/**
 * Lets each cause of a playback error through once per episode until the next listen starts, so a
 * retry loop (or trying A, B and A again offline) sends one `playback_error` per failure instead of
 * hundreds. A later failure after a good listen counts again.
 */
internal class PlaybackErrorOnce {
    private val seen = LinkedHashSet<String>()

    /** True the first time [cause] fails for [episodeId] since the last [reset]. */
    @Synchronized
    fun firstTime(episodeId: String?, cause: String,): Boolean {
        val added = seen.add("${episodeId.orEmpty()}|$cause")
        if (added && seen.size > MAX_KEYS) seen.remove(seen.first())
        return added
    }

    /** A listen started: errors after it are news again. */
    @Synchronized
    fun reset() {
        seen.clear()
    }

    private companion object {
        /** Enough for a long offline stretch of skips; the oldest go first. */
        const val MAX_KEYS = 200
    }
}
