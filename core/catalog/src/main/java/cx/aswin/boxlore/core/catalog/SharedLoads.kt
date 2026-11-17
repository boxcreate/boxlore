package cx.aswin.boxlore.core.catalog

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

/**
 * Runs one load per key at a time: callers asking for a key that is already loading wait for that
 * load instead of starting another. A caller that is cancelled stops waiting; the load itself runs
 * on [scope], so the other callers still get its answer. [load] must not throw (return an empty
 * answer instead), because every waiting caller would receive the failure.
 */
internal class SharedLoads<K : Any, V>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private val running = ConcurrentHashMap<K, Deferred<V>>()

    suspend fun load(key: K, load: suspend () -> V): V {
        val deferred = running.computeIfAbsent(key) {
            scope.async(start = CoroutineStart.LAZY) { load() }
        }
        deferred.invokeOnCompletion { running.remove(key, deferred) }
        deferred.start()
        return deferred.await()
    }

    /** Loads still running, for tests. */
    fun runningCount(): Int = running.size
}
