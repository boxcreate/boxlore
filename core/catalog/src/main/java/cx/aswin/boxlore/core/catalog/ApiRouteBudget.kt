package cx.aswin.boxlore.core.catalog

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one pacer for library-wide jobs that send one API request per show (OPML import, filling in
 * shows a cloud pull left as placeholders, missing-address lookups, reading a whole show for range
 * selection). The API allows each session about 30 requests a minute per route and answers 429
 * with `Retry-After` past that; this keeps each [route] to [perMinute] requests in any [windowMs],
 * leaving room for what the listener opens meanwhile.
 *
 * A job asks before each request: [acquire] waits for room (a screen showing progress, or catch-up
 * work running on its own), [tryAcquire] answers at once (work that must not hold up a pass, which
 * leaves the rest for later). A 429 is reported with [rateLimited]: the route then waits for
 * `Retry-After` (a minute without one) before anything else is sent on it, so a 429 is never a final
 * answer. Requests sent without asking (a show page the listener opens) are not counted.
 *
 * One per process: [PodcastRepository.routeBudget].
 */
class ApiRouteBudget(
    private val perMinute: Int = DEFAULT_PER_MINUTE,
    private val windowMs: Long = WINDOW_MS,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val sleep: suspend (Long) -> Unit = { delay(it) },
) {
    private val lock = Mutex()
    private val sent = HashMap<String, ArrayDeque<Long>>()
    private val pausedUntil = HashMap<String, Long>()

    /** Waits until [route] has room, then counts one request on it. */
    suspend fun acquire(route: String) {
        while (true) {
            val waitMs = lock.withLock { reserve(route) }
            if (waitMs <= 0L) return
            sleep(waitMs)
        }
    }

    /** Counts one request on [route] when it has room now; false (nothing counted) when it would have to wait. */
    suspend fun tryAcquire(route: String): Boolean = lock.withLock { reserve(route) <= 0L }

    /** [route] answered 429: nothing more is sent on it for [retryAfterMs] (a minute when the answer didn't say). */
    suspend fun rateLimited(route: String, retryAfterMs: Long?) {
        lock.withLock {
            val until = nowMs() + (retryAfterMs?.takeIf { it > 0L } ?: windowMs)
            pausedUntil[route] = maxOf(pausedUntil[route] ?: 0L, until)
        }
    }

    /** Whether [route] is waiting out a 429 now ([rateLimited]). */
    suspend fun isPaused(route: String): Boolean = lock.withLock { (pausedUntil[route] ?: 0L) > nowMs() }

    /** How long a request on [route] would wait now; 0 when it can go. Counts nothing. */
    suspend fun waitMs(route: String): Long = lock.withLock { waitFor(route, nowMs()) }

    private fun reserve(route: String): Long {
        val now = nowMs()
        val wait = waitFor(route, now)
        if (wait <= 0L) sent.getOrPut(route) { ArrayDeque() }.addLast(now)
        return wait
    }

    private fun waitFor(route: String, now: Long): Long {
        val paused = (pausedUntil[route] ?: 0L) - now
        if (paused > 0L) return paused
        val times = sent[route]
        while (times != null && times.isNotEmpty() && now - times.first() >= windowMs) times.removeFirst()
        if ((times?.size ?: 0) < perMinute) return 0L
        val oldest = times?.firstOrNull() ?: return windowMs
        return (oldest + windowMs - now).coerceAtLeast(1L)
    }

    companion object {
        /** Below the API's 30 a minute per route, with room for requests the listener makes meanwhile. */
        const val DEFAULT_PER_MINUTE = 25
        const val WINDOW_MS = 60_000L

        /** `GET /podcast`: one show's details, by ID, feed address or GUID. */
        const val PODCAST = "podcast"

        /** `GET /search`: the OPML import's title search. */
        const val SEARCH = "search"

        /** `GET /v2/episodes`: one page of a show's episode list. */
        const val V2_EPISODES = "v2/episodes"
    }
}
