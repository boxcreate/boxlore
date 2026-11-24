package cx.aswin.boxlore.feature.settings.downloads

import android.content.Context
import cx.aswin.boxlore.core.downloads.SmartDownloadManager
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** The background work Smart Downloads starts and stops; tests replace it. */
internal interface SmartDownloadScheduler {
    fun schedule(wifiOnly: Boolean, chargingOnly: Boolean)

    fun cancel()

    /** Removes every episode Smart Downloads saved; other downloads stay. */
    fun purgeAll()
}

/** The app's scheduler: WorkManager jobs through [SmartDownloadManager]. */
internal class WorkManagerSmartDownloadScheduler(private val context: Context) : SmartDownloadScheduler {
    override fun schedule(wifiOnly: Boolean, chargingOnly: Boolean) = SmartDownloadManager.schedulePeriodicSync(context, wifiOnly, chargingOnly)

    override fun cancel() = SmartDownloadManager.cancelPeriodicSync(context)

    override fun purgeAll() = SmartDownloadManager.purgeAllSmartDownloads(context)
}

/** The Smart Downloads preferences the page writes; tests replace them. */
internal interface SmartDownloadsPrefs {
    suspend fun setEnabled(enabled: Boolean)

    suspend fun setWifiOnly(wifiOnly: Boolean)

    suspend fun setChargingOnly(chargingOnly: Boolean)

    suspend fun setMaxEpisodes(count: Int)

    suspend fun setStorageBudget(megabytes: Long)

    suspend fun setCleanupRule(rule: String)
}

/** The stored preferences behind [SmartDownloadsPrefs]. */
internal fun UserPreferencesRepository.smartDownloadsPrefs(): SmartDownloadsPrefs = object : SmartDownloadsPrefs {
    override suspend fun setEnabled(enabled: Boolean) = setSmartDownloadsEnabled(enabled)

    override suspend fun setWifiOnly(wifiOnly: Boolean) = setSmartDownloadsWifiOnly(wifiOnly)

    override suspend fun setChargingOnly(chargingOnly: Boolean) = setSmartDownloadsChargingOnly(chargingOnly)

    override suspend fun setMaxEpisodes(count: Int) = setSmartDownloadsMaxEpisodes(count)

    override suspend fun setStorageBudget(megabytes: Long) = setSmartDownloadsStorageBudget(megabytes)

    override suspend fun setCleanupRule(rule: String) = setSmartDownloadsCleanupRule(rule)
}

/** What the Smart Downloads page shows now; the actions read it to report changes and reschedule. */
internal data class SmartDownloadsState(
    val enabled: Boolean,
    val wifiOnly: Boolean,
    val chargingOnly: Boolean,
    val maxEpisodes: Int,
    val storageBudget: Long,
    val cleanupRule: String,
)

/**
 * Every Smart Downloads control. Turning on schedules the sync at once; turning off only asks
 * ([onConfirmOffChange]) and changes nothing until [confirmDisable], which stops the sync and
 * removes the episodes Smart Downloads saved. Each change reports one `settings_interaction`.
 */
internal class SmartDownloadsActions(
    private val scope: CoroutineScope,
    private val prefs: SmartDownloadsPrefs,
    private val scheduler: SmartDownloadScheduler,
    private val state: () -> SmartDownloadsState,
    private val onConfirmOffChange: (Boolean) -> Unit,
    private val track: (key: String, value: Any, previous: Any) -> Unit = ::trackDownloadSetting,
) {
    /** The Enable switch: on starts right away, off asks first. */
    fun setEnabled(enabled: Boolean) {
        val current = state()
        if (enabled == current.enabled) return
        if (!enabled) {
            onConfirmOffChange(true)
            return
        }
        scope.launch {
            track("smart_downloads_toggled", true, current.enabled)
            prefs.setEnabled(true)
            scheduler.schedule(current.wifiOnly, current.chargingOnly)
        }
    }

    /** Turn off from the question: the same steps as before the question existed, in the same order. */
    fun confirmDisable() {
        onConfirmOffChange(false)
        val current = state()
        scope.launch {
            track("smart_downloads_toggled", false, current.enabled)
            prefs.setEnabled(false)
            scheduler.cancel()
            scheduler.purgeAll()
        }
    }

    /** Keep Smart Downloads on: nothing changes and nothing is reported. */
    fun dismissDisable() = onConfirmOffChange(false)

    /**
     * The latest Wi-Fi and charging choices, set when tapped. Their writes run one at a time, and each
     * schedule after a write uses both latest choices, so the last schedule matches what was saved.
     */
    private var latestWifiOnly: Boolean? = null
    private var latestChargingOnly: Boolean? = null
    private val constraintWrites = Mutex()

    fun setWifiOnly(wifiOnly: Boolean) {
        val current = state()
        latestWifiOnly = wifiOnly
        scope.launch {
            track("smart_downloads_wifi_only_toggled", wifiOnly, current.wifiOnly)
            constraintWrites.withLock {
                prefs.setWifiOnly(wifiOnly)
                if (current.enabled) scheduleLatest(current)
            }
        }
    }

    fun setChargingOnly(chargingOnly: Boolean) {
        val current = state()
        latestChargingOnly = chargingOnly
        scope.launch {
            track("smart_downloads_charging_only_toggled", chargingOnly, current.chargingOnly)
            constraintWrites.withLock {
                prefs.setChargingOnly(chargingOnly)
                if (current.enabled) scheduleLatest(current)
            }
        }
    }

    private fun scheduleLatest(current: SmartDownloadsState) =
        scheduler.schedule(wifiOnly = latestWifiOnly ?: current.wifiOnly, chargingOnly = latestChargingOnly ?: current.chargingOnly)

    fun setMaxEpisodes(count: Int) {
        val current = state()
        track("smart_downloads_max_episodes_changed", count, current.maxEpisodes)
        scope.launch { prefs.setMaxEpisodes(count) }
    }

    fun setStorageBudget(megabytes: Long) {
        val current = state()
        track("smart_downloads_storage_budget_changed", megabytes, current.storageBudget)
        scope.launch { prefs.setStorageBudget(megabytes) }
    }

    fun setCleanupRule(rule: String) {
        val current = state()
        track("smart_downloads_cleanup_rule_changed", rule, current.cleanupRule)
        scope.launch { prefs.setCleanupRule(rule) }
    }
}
