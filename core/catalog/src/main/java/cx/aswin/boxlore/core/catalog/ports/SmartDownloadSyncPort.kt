package cx.aswin.boxlore.core.catalog.ports

/**
 * Port that decouples `:core:data` (LibraryBackupManager) from `:core:downloads`
 * (SmartDownloadManager companion) at compile time.
 *
 * Installed by AppContainer from `:app` after creating SmartDownloadManager.
 * Calls before installation are silently dropped (backup restore would still apply prefs;
 * WorkManager periodic work will be rescheduled on next smart-download settings open).
 */
object SmartDownloadSyncPort {
    /** Schedule or update periodic smart-download sync. Parameters mirror [SmartDownloadManager.schedulePeriodicSync]. */
    @Volatile
    var schedulePeriodicSync: ((wifiOnly: Boolean, chargingOnly: Boolean) -> Unit)? = null

    /** Cancel the periodic sync work. */
    @Volatile
    var cancelPeriodicSync: (() -> Unit)? = null
}
