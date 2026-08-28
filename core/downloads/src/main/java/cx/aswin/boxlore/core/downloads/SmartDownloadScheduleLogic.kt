package cx.aswin.boxlore.core.downloads

/**
 * Keeps the Smart Downloads toggle and the periodic WorkManager job aligned after
 * Google Backup / device-transfer restores, where those two stores can diverge.
 */
internal object SmartDownloadScheduleLogic {
    const val UNIQUE_WORK_NAME = "SmartDownloadSync"
    const val AUTOMATIC_SYNC_INTERVAL_MS = 24 * 60 * 60 * 1_000L

    enum class ReconcileAction {
        ENABLE_AND_SCHEDULE,
        SCHEDULE,
        CANCEL,
    }

    fun reconcile(
        enabledInPrefs: Boolean,
        hasActiveScheduledWork: Boolean,
    ): ReconcileAction =
        when {
            hasActiveScheduledWork && !enabledInPrefs -> ReconcileAction.ENABLE_AND_SCHEDULE
            enabledInPrefs -> ReconcileAction.SCHEDULE
            else -> ReconcileAction.CANCEL
        }

    fun shouldRunSync(
        isManual: Boolean,
        lastSuccessfulSyncMs: Long,
        nowMs: Long,
    ): Boolean {
        if (isManual || lastSuccessfulSyncMs <= 0L) return true
        if (lastSuccessfulSyncMs > nowMs) return true
        return nowMs - lastSuccessfulSyncMs >= AUTOMATIC_SYNC_INTERVAL_MS
    }
}
