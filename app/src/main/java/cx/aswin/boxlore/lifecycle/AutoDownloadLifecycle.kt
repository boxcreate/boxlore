package cx.aswin.boxlore.lifecycle

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.NetworkType
import androidx.work.WorkManager
import androidx.work.await
import cx.aswin.boxlore.AppContainer
import cx.aswin.boxlore.core.downloads.AutoDownloadScheduling
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Restored/cloud settings and process starts rebuild jobs independently of notification permission. */
internal class AutoDownloadLifecycle(
    private val context: Context,
    private val container: AppContainer,
    private val scope: CoroutineScope
) : DefaultLifecycleObserver {
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            var previousIds = emptySet<String>()
            var previousWifi: Boolean? = null
            combine(
                container.database.podcastDao().getSubscribedPodcasts(),
                container.userPreferencesRepository.autoDownloadWifiOnlyStream
            ) { shows, wifi ->
                shows.filter(cx.aswin.boxlore.core.downloads.AutoDownloadCoordinator::eligible).map { it.podcastId }.toSet() to wifi
            }.distinctUntilChanged().collect { (ids, wifi) ->
                safely {
                    val manager = WorkManager.getInstance(context)
                    for (disabled in previousIds - ids) manager.cancelAllWorkByTag(AutoDownloadScheduling.showTag(disabled)).await()
                    if (previousWifi != wifi) reconcileAutoDownloadWifiPolicy(manager, wifi)
                    container.autoDownloadCoordinator.synchronizeSubscriptions()
                    AutoDownloadScheduling.reconcile(context, ids.isNotEmpty())
                    if (ids.isNotEmpty()) AutoDownloadScheduling.catchUp(context)
                    previousIds = ids
                    previousWifi = wifi
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        scope.launch {
            safely {
                if (container.autoDownloadCoordinator.synchronizeSubscriptions().isNotEmpty()) AutoDownloadScheduling.catchUp(context)
            }
        }
    }

    private suspend fun safely(block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w("AutoDownloadLifecycle", "Unable to schedule catch-up", e)
        }
    }
}

/** Preserve correctly constrained cold-start work; rebuild only requests with stale policy. */
internal suspend fun reconcileAutoDownloadWifiPolicy(manager: WorkManager, wifiOnly: Boolean) {
    val network = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
    for (work in manager.getWorkInfosByTagFlow(AutoDownloadScheduling.TRANSFER_TAG).first()) {
        if (!work.state.isFinished && work.constraints.requiredNetworkType != network) {
            manager.cancelWorkById(work.id).await()
        }
    }
}
