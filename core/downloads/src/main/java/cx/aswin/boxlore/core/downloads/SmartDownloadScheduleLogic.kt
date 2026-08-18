package cx.aswin.boxlore.core.downloads

/**
 * Keeps the Smart Downloads toggle and the periodic WorkManager job aligned after
 * Google Backup / device-transfer restores, where those two stores can diverge.
 */
internal object SmartDownloadScheduleLogic {
    const val UNIQUE_WORK_NAME = "SmartDownloadSync"

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
}
