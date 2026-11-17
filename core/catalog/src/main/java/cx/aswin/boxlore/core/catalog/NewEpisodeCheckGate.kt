package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/**
 * Decides when [SubscriptionForegroundSync] may check followed shows for new episodes. It owns
 * that class's foreground gate ([SubscriptionForegroundSync.setForeground]); nothing else sets it.
 *
 * - **boxlore on screen** ([setAppVisible]): the usual passes, timer included.
 * - **Android Auto** ([carListsOpened]): when the car opens New episodes or Shows while boxlore's
 *   player service runs, one automatic pass. The gate stays open for [carWindowMs] and asks for the
 *   pass through [SubscriptionForegroundSync.requestRefresh], so its cooldown, in-flight guard,
 *   six-hour per-feed wait and automatic limits all apply. At most one per [carMinIntervalMs]; the
 *   window closes early when the drive ends ([carDriveEnded]), which stops a pass still running.
 * - **Nothing else.** With boxlore closed and no player service, nothing checks feeds: checking in
 *   the background needs the listener's consent, which only background auto-download asks for.
 *   Home screen widgets and the watch never start a check, to spare the battery: they show what the
 *   last pass or push saved and redraw when that changes.
 */
class NewEpisodeCheckGate(
    private val scope: CoroutineScope,
    private val setSyncForeground: (Boolean) -> Unit,
    private val requestPass: () -> Unit,
    private val carWindowMs: Long = DEFAULT_CAR_WINDOW_MS,
    private val carMinIntervalMs: Long = DEFAULT_CAR_MIN_INTERVAL_MS,
    private val nowMs: () -> Long = { System.currentTimeMillis() },
) {
    constructor(sync: SubscriptionForegroundSync, scope: CoroutineScope) :
        this(scope = scope, setSyncForeground = sync::setForeground, requestPass = sync::requestRefresh)

    private val lock = Any()
    private var appVisible = false
    private var carWindow: Job? = null
    private var lastCarCheckAtMs: Long? = null

    /** boxlore's UI came to or left the foreground (the process lifecycle owner). */
    fun setAppVisible(visible: Boolean) {
        synchronized(lock) {
            appVisible = visible
            publish()
        }
    }

    /**
     * Android Auto opened New episodes or Shows during a drive. Returns whether a check was asked
     * for; false inside [carMinIntervalMs] of the last one.
     */
    fun carListsOpened(): Boolean {
        synchronized(lock) {
            val now = nowMs()
            val last = lastCarCheckAtMs
            if (last != null && now - last in 0 until carMinIntervalMs) return false
            lastCarCheckAtMs = now
            carWindow?.cancel()
            // Started only once recorded, so a window that ends at once still finds itself and closes.
            val window =
                scope.launch(start = CoroutineStart.LAZY) {
                    delay(carWindowMs)
                    closeCarWindow(coroutineContext.job)
                }
            carWindow = window
            publish()
            window.start()
        }
        requestPass()
        return true
    }

    /** The drive ended: close the car's window now. */
    fun carDriveEnded() {
        synchronized(lock) {
            carWindow?.cancel()
            carWindow = null
            publish()
        }
    }

    private fun closeCarWindow(window: Job) {
        synchronized(lock) {
            if (carWindow !== window) return
            carWindow = null
            publish()
        }
    }

    private fun publish() {
        setSyncForeground(appVisible || carWindow != null)
    }

    companion object {
        /**
         * How long the car's window stays open. An automatic pass reads up to
         * [DirectFeedRefreshBatch.AUTOMATIC_MAX] due feeds, two at a time
         * ([SubscriptionForegroundSync.DEFAULT_FEED_CONCURRENCY]), after its `/sync` chunks; ten
         * minutes covers that with a few slow or dead hosts. A pass still running when the window
         * ends stops there, and later passes read the feeds it did not reach first. Kept below
         * [SubscriptionForegroundSync.DEFAULT_PERIODIC_INTERVAL_MS], so a window opened while boxlore
         * is closed never lets the timer start a second pass. A window opened while boxlore is on
         * screen keeps the timer boxlore already had, so leaving boxlore during it can let that timer
         * run one more pass, still under the cooldown and six-hour wait.
         */
        const val DEFAULT_CAR_WINDOW_MS = 10 * 60 * 1000L

        /** A car reconnecting at every stop asks for one check per half hour at most. */
        const val DEFAULT_CAR_MIN_INTERVAL_MS = 30 * 60 * 1000L
    }
}
