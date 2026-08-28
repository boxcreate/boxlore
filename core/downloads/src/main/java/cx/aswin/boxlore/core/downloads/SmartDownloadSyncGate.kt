package cx.aswin.boxlore.core.downloads

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Serializes automatic and manual Smart Download runs so cadence admission and download
 * reconciliation are one process-local critical section.
 */
internal class SmartDownloadSyncGate(
    private val currentTimeMs: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun run(
        isManual: Boolean,
        lastSuccessfulSyncMs: suspend () -> Long,
        onAutomaticCadenceSatisfied: suspend () -> Unit,
        sync: suspend (nowMs: Long) -> Boolean,
    ): Boolean =
        mutex.withLock {
            val nowMs = currentTimeMs()
            if (
                !SmartDownloadScheduleLogic.shouldRunSync(
                    isManual = isManual,
                    lastSuccessfulSyncMs = lastSuccessfulSyncMs(),
                    nowMs = nowMs,
                )
            ) {
                onAutomaticCadenceSatisfied()
                true
            } else {
                sync(nowMs)
            }
        }
}
