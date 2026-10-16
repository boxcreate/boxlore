package cx.aswin.boxlore

/**
 * Holds the latest Cast session state until playback exists.
 *
 * Forwarding Cast state at startup used to construct the whole playback graph (prefs migration,
 * MediaController bind) on the main thread inside Application.onCreate, which caused startup
 * ANRs even for widget, worker and notification starts. The latest state now waits here and is
 * applied the moment playback is created; once attached, updates pass straight through.
 */
internal class DeferredCastSessionState {
    private var pending: Boolean? = null
    private var hasPending = false
    private var target: ((Boolean?) -> Unit)? = null

    @Synchronized
    fun submit(isActive: Boolean?) {
        val apply = target
        if (apply != null) {
            apply(isActive)
        } else {
            pending = isActive
            hasPending = true
        }
    }

    @Synchronized
    fun attach(apply: (Boolean?) -> Unit) {
        target = apply
        if (hasPending) {
            hasPending = false
            apply(pending)
            pending = null
        }
    }
}
