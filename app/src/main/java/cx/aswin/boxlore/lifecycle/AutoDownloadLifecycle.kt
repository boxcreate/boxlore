package cx.aswin.boxlore.lifecycle

import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import androidx.work.await
import cx.aswin.boxlore.AppContainer
import cx.aswin.boxlore.core.downloads.AutoDownloadScheduling
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
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
        container.subscriptionForegroundSync.setForeground(ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        scope.launch {
            var previousIds = emptySet<String>()
            var previousWifi: Boolean? = null
            var previousBackground: AutoDownloadBackgroundSettings? = null
            combine(
                container.database.podcastDao().getSubscribedPodcasts(),
                container.userPreferencesRepository.autoDownloadWifiOnlyStream,
                container.userPreferencesRepository.autoDownloadBackgroundSettingsStream
            ) { shows, wifi, background ->
                Triple(shows.filter(cx.aswin.boxlore.core.downloads.AutoDownloadCoordinator::eligible).map { it.podcastId }.toSet(), wifi, background)
            }.distinctUntilChanged().collectLatest { (ids, wifi, background) ->
                safely {
                    val manager = WorkManager.getInstance(context)
                    for (disabled in previousIds - ids) manager.cancelAllWorkByTag(AutoDownloadScheduling.showTag(disabled)).await()
                    if (previousWifi != wifi || previousBackground != background) reconcileAutoDownloadWifiPolicy(manager, wifi, background)
                    container.autoDownloadCoordinator.synchronizeSubscriptions()
                    AutoDownloadScheduling.reconcile(context, ids.isNotEmpty(), background)
                    scanCachedInForeground(ids)
                    previousIds = ids
                    previousWifi = wifi
                    previousBackground = background
                }
            }
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        container.subscriptionForegroundSync.setForeground(true)
        scope.launch {
            safely {
                for (id in container.autoDownloadCoordinator.synchronizeSubscriptions()) container.autoDownloadCoordinator.scanCached(id)
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        container.subscriptionForegroundSync.setForeground(false)
    }

    private suspend fun scanCachedInForeground(ids: Set<String>) {
        if (!ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        for (id in ids) container.autoDownloadCoordinator.scanCached(id)
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
internal suspend fun reconcileAutoDownloadWifiPolicy(manager: WorkManager, wifiOnly: Boolean, background: AutoDownloadBackgroundSettings = AutoDownloadBackgroundSettings()) {
    for (work in manager.getWorkInfosByTagFlow(AutoDownloadScheduling.TRANSFER_TAG).first()) {
        val fromBackground = AutoDownloadScheduling.BACKGROUND_TRANSFER_TAG in work.tags
        val expected = AutoDownloadScheduling.transferConstraints(wifiOnly, if (fromBackground) background else null)
        val legacy = AutoDownloadScheduling.POLICY_TRANSFER_TAG !in work.tags
        val backgroundDisallowed = fromBackground && !background.enabled
        if (work.state.isFinished) continue
        if (legacy || backgroundDisallowed || work.constraints != expected) {
            manager.cancelWorkById(work.id).await()
        }
    }
}
