package cx.aswin.boxlore.core.analytics

/**
 * The screen the listener is on and the one before it, for `screen_viewed`, the other view events,
 * and the screen a play started from. The shared Compose helper (`TrackScreenView` in
 * `:core:designsystem`) moves it once per visit; anything may read it, from any thread.
 */
object ScreenViews {
    /** The current screen and the one before it; either is null until known. */
    data class Trail(val current: String?, val previous: String?,)

    @Volatile
    var trail: Trail = Trail(null, null)
        private set

    /**
     * [screen] is showing: it becomes current, and the screen it replaced becomes the one before.
     * Returns the screen before it. Showing the current screen again (a rotation, a return from a
     * sheet) keeps the trail as it is.
     */
    @Synchronized
    fun enter(screen: String): String? {
        val now = trail
        if (now.current == screen) return now.previous
        trail = Trail(current = screen, previous = now.current)
        return now.current
    }

    /** [screen] went away: when it was current, the screen before it is current again. */
    @Synchronized
    fun leave(screen: String) {
        val now = trail
        if (now.current == screen) trail = Trail(current = now.previous, previous = screen)
    }

    /** Test hook: no screens yet. */
    @androidx.annotation.VisibleForTesting
    fun resetForTest() {
        trail = Trail(null, null)
    }
}
