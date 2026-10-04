package cx.aswin.boxlore.feature.home

import kotlinx.coroutines.CompletableDeferred

/** Publish the first Home snapshot and start refreshes after its local caches finish restoring. */
internal class HomeCacheRestoreGate {
    private val restored = CompletableDeferred<Unit>()

    suspend fun restore(readCache: suspend () -> Unit) {
        try {
            readCache()
        } finally {
            restored.complete(Unit)
        }
    }

    suspend fun awaitRestore() {
        restored.await()
    }
}
