package cx.aswin.boxlore.core.catalog.sync

/**
 * Result representation for cloud sync executions.
 */
sealed class SyncResult {
    data class Success(
        val pushedSubscriptions: Int,
        val pushedHistory: Int,
        val pushedQueue: Boolean,
        val pulledSubscriptions: Int,
        val pulledHistory: Int,
        val pulledQueue: Boolean,
        val syncedAt: Long,
    ) : SyncResult()

    data class Failure(
        val error: Throwable,
        val partialSyncedAt: Long? = null,
    ) : SyncResult()

    data object SkippedNoAuth : SyncResult()
    data object SkippedOffline : SyncResult()
}
