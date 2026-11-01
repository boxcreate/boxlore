package cx.aswin.boxlore.core.analytics

/**
 * Static holder for entry-point context that needs to cross the
 * MediaController → MediaSession IPC boundary.
 *
 * Bundle extras set via MediaMetadata.Builder().setExtras() are not
 * reliably preserved across Binder serialization. This singleton
 * provides a same-process shortcut: PlaybackRepository writes the
 * context before calling controller.play(), and
 * BoxLorePlaybackService consumes it in startPlaybackSession().
 *
 * Thread-safe via @Synchronized.
 */
object PendingEntryPoint {
    private var pending: Map<String, Any>? = null

    @Synchronized
    fun set(context: Map<String, Any>) {
        pending = context
    }

    /**
     * Sets the pending context only if nothing is already queued. Lets an explicit
     * source (e.g. "episode_info_screen") set by a screen win over a generic default
     * (e.g. "resume_player") applied later on the same play() call.
     */
    @Synchronized
    fun setIfAbsent(context: Map<String, Any>) {
        if (pending == null) pending = context
    }

    /**
     * [context] with the screen the listener asked to play from and the one before it
     * ([ScreenViews]), as `screen` and `previous_screen`, when known. Only for plays asked from
     * boxlore's own screens; the car and voice requests leave them out.
     */
    fun withScreens(context: Map<String, Any>): Map<String, Any> {
        val trail = ScreenViews.trail
        return buildMap {
            putAll(context)
            trail.current?.let { if (SCREEN !in context) put(SCREEN, it) }
            trail.previous?.let { if (PREVIOUS_SCREEN !in context) put(PREVIOUS_SCREEN, it) }
        }
    }

    @Synchronized
    fun consume(): Map<String, Any>? {
        val result = pending
        pending = null
        return result
    }

    private const val SCREEN = "screen"
    private const val PREVIOUS_SCREEN = "previous_screen"
}
