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
     * The listener asked to play from one of boxlore's screens (they're showing): remember which
     * screens were showing now, because the play is often published after preparing (a lookup, a
     * queue load) and the listener may have moved on by then. [withScreens] uses this for
     * [REQUEST_WINDOW_MS]. Plays from a widget, the car or the notification don't call it.
     */
    fun noteRequest() {
        requested = clock() to ScreenViews.trail
    }

    /**
     * [context] with the screen the listener asked to play from and the one before it, as `screen`
     * and `previous_screen`: the screens [noteRequest] saw within [REQUEST_WINDOW_MS], else nothing
     * added. Fields the caller set already win.
     */
    fun withScreens(context: Map<String, Any>): Map<String, Any> {
        val trail = requested?.takeIf { (at, _) -> clock() - at in 0..REQUEST_WINDOW_MS }?.second ?: return context
        return buildMap {
            putAll(context)
            trail.current?.let { if (SCREEN !in context) put(SCREEN, it) }
            trail.previous?.let { if (PREVIOUS_SCREEN !in context) put(PREVIOUS_SCREEN, it) }
        }
    }

    @Volatile
    private var requested: Pair<Long, ScreenViews.Trail>? = null

    /** Monotonic milliseconds; replaced in tests. */
    @Volatile
    internal var clock: () -> Long = { System.nanoTime() / NANOS_PER_MS }

    /** Drops the pending context only when [matches] says it's the one to drop; anything else stays. */
    @Synchronized
    fun discardIf(matches: (Map<String, Any>) -> Boolean) {
        if (pending?.let(matches) == true) pending = null
    }

    @Synchronized
    fun consume(): Map<String, Any>? {
        val result = pending
        pending = null
        return result
    }

    private const val SCREEN = "screen"
    private const val PREVIOUS_SCREEN = "previous_screen"
    private const val REQUEST_WINDOW_MS = 10_000L
    private const val NANOS_PER_MS = 1_000_000L

    /** Test hook: no request remembered. */
    internal fun resetForTest() {
        requested = null
        clock = { System.nanoTime() / NANOS_PER_MS }
    }
}
