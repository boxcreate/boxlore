package cx.aswin.boxlore.core.catalog.sync

/**
 * UI representation of cloud synchronization state, exposed to settings and status indicators.
 */
sealed interface CloudSyncUiStatus {
    data object Idle : CloudSyncUiStatus
    data object Syncing : CloudSyncUiStatus
    data class Success(val syncedAt: Long) : CloudSyncUiStatus
    data class Error(val message: String) : CloudSyncUiStatus
}
