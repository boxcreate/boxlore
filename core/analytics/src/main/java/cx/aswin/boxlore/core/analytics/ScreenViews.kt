package cx.aswin.boxlore.core.analytics

/**
 * The screen the listener is on and the one before it, for `screen_viewed`, the other view events,
 * and the screen a play started from. The shared Compose helper (`TrackScreenView` in
 * `:core:designsystem`) registers each screen while it shows; anything may read [trail], from any thread.
 *
 * - Each showing screen holds a [Registration]; the most recent one still showing is current.
 * - A registration that changes screen (a new folder step, another story card) replaces itself, so the
 *   screen it showed becomes the one before.
 * - A screen that goes away hands current back to the newest screen still showing, and becomes the one
 *   before. One that goes away behind another (the page under a sheet) leaves the trail alone.
 */
object ScreenViews {
    /** The current screen and the one before it; either is null until known. */
    data class Trail(val current: String?, val previous: String?,)

    /** One showing screen, as [show] and [leave] know it. */
    class Registration

    @Volatile
    var trail: Trail = Trail(null, null)
        private set

    private val showing = mutableListOf<Pair<Registration, String>>()

    /**
     * [registration] shows [screen]: a new one becomes current, with the screen it covered before it;
     * one that already showed another screen replaces it in place. Returns the screen before.
     */
    @Synchronized
    fun show(registration: Registration, screen: String,): String? {
        val index = showing.indexOfFirst { it.first === registration }
        val now = trail
        if (index < 0) {
            showing += registration to screen
            trail = Trail(current = screen, previous = now.current)
            return now.current
        }
        val shown = showing[index].second
        showing[index] = registration to screen
        if (index != showing.lastIndex) return now.previous
        if (shown != screen) trail = Trail(current = screen, previous = shown)
        return trail.previous
    }

    /** [registration] went away; when it was current, the newest screen still showing is current again. */
    @Synchronized
    fun leave(registration: Registration) {
        val index = showing.indexOfFirst { it.first === registration }
        if (index < 0) return
        val (_, screen) = showing.removeAt(index)
        if (index == showing.size) trail = Trail(current = showing.lastOrNull()?.second, previous = screen)
    }

    /** Test hook: no screens yet. */
    @androidx.annotation.VisibleForTesting
    @Synchronized
    fun resetForTest() {
        showing.clear()
        trail = Trail(null, null)
    }
}
