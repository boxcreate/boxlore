package cx.aswin.boxlore.lifecycle

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import cx.aswin.boxlore.core.downloads.AutoDownloadDiscoveryWorker
import cx.aswin.boxlore.core.downloads.AutoDownloadScheduling
import cx.aswin.boxlore.core.downloads.AutoDownloadWorker
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoDownloadLifecycleTest {
    @Test
    fun coldStartKeepsMatchingTransferConstraints() = runBlocking {
        val manager = manager()
        val work = transfer(NetworkType.UNMETERED)
        manager.enqueue(work).result.get()
        reconcileAutoDownloadWifiPolicy(manager, wifiOnly = true)
        reconcileAutoDownloadWifiPolicy(manager, wifiOnly = true)
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(work.id).get().state)
    }

    @Test
    fun changedOrRestoredPolicyCancelsOnlyMismatchedAutomaticTransfers() = runBlocking {
        for (wifiOnly in listOf(false, true)) {
            val manager = manager()
            val desired = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val old = if (wifiOnly) NetworkType.CONNECTED else NetworkType.UNMETERED
            val matching = transfer(desired)
            val stale = transfer(old)
            val unrelated = transfer(old, automatic = false)
            manager.enqueue(listOf(matching, stale, unrelated)).result.get()
            reconcileAutoDownloadWifiPolicy(manager, wifiOnly)
            assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(matching.id).get().state)
            assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(stale.id).get().state)
            assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(unrelated.id).get().state)
        }
    }

    private fun manager(): WorkManager {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        return WorkManager.getInstance(context)
    }

    private fun transfer(network: NetworkType, automatic: Boolean = true) = OneTimeWorkRequestBuilder<AutoDownloadWorker>()
        .setInitialDelay(1, TimeUnit.DAYS)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(network).build())
        .apply {
            if (automatic) {
            addTag(AutoDownloadScheduling.TRANSFER_TAG)
            addTag(AutoDownloadScheduling.POLICY_TRANSFER_TAG)
        }
        }
        .build()

    @Test fun disablingCancelsBackgroundWorkAndKeepsForegroundOrPushTransfers() = runBlocking {
        val manager = manager()
        val foreground = transfer(NetworkType.UNMETERED)
        val background = AutoDownloadScheduling.transferRequest("show", "episode", true, AutoDownloadBackgroundSettings(enabled = true))
        manager.enqueue(listOf(foreground, background)).result.get()
        reconcileAutoDownloadWifiPolicy(manager, true)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(background.id).get().state)
        assertEquals(WorkInfo.State.ENQUEUED, manager.getWorkInfoById(foreground.id).get().state)
    }

    @Test fun legacyTransferWithUnknownDiscoverySourceIsCancelled() = runBlocking {
        val manager = manager()
        val legacy = OneTimeWorkRequestBuilder<AutoDownloadWorker>().setInitialDelay(1, TimeUnit.DAYS)
            .addTag(AutoDownloadScheduling.TRANSFER_TAG).build()
        manager.enqueue(legacy).result.get()
        reconcileAutoDownloadWifiPolicy(manager, true)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(legacy.id).get().state)
    }

    @Test fun backgroundConstraintsIncludeBatteryChargingAndBothNetworkPolicies() {
        val policy = AutoDownloadBackgroundSettings(enabled = true, wifiOnly = false, chargingOnly = true)
        val discovery = AutoDownloadScheduling.backgroundConstraints(policy)
        assertEquals(NetworkType.CONNECTED, discovery.requiredNetworkType)
        assertTrue(discovery.requiresBatteryNotLow())
        assertTrue(discovery.requiresCharging())
        val audio = AutoDownloadScheduling.transferConstraints(true, policy)
        assertEquals(NetworkType.UNMETERED, audio.requiredNetworkType)
        assertTrue(audio.requiresBatteryNotLow())
        assertTrue(audio.requiresCharging())
    }

    @Test fun defaultReconciliationDoesNotSchedulePollingForEnabledShows() = runBlocking {
        val manager = manager()
        AutoDownloadScheduling.reconcile(ApplicationProvider.getApplicationContext(), active = true)
        assertTrue(manager.getWorkInfosForUniqueWork(AutoDownloadScheduling.DISCOVERY_NAME).get().isEmpty())
    }

    @Test fun upgradingWithConsentOffCancelsOldImplicitCatchUpAndPeriodicPolling() = runBlocking {
        val manager = manager()
        val periodic = PeriodicWorkRequestBuilder<AutoDownloadDiscoveryWorker>(1, TimeUnit.HOURS).setInitialDelay(1, TimeUnit.DAYS).build()
        val catchUp = OneTimeWorkRequestBuilder<AutoDownloadDiscoveryWorker>().setInitialDelay(1, TimeUnit.DAYS).build()
        manager.enqueueUniquePeriodicWork(AutoDownloadScheduling.DISCOVERY_NAME, ExistingPeriodicWorkPolicy.KEEP, periodic).result.get()
        manager.enqueueUniqueWork("${AutoDownloadScheduling.DISCOVERY_NAME}-now", ExistingWorkPolicy.KEEP, catchUp).result.get()
        AutoDownloadScheduling.reconcile(ApplicationProvider.getApplicationContext(), active = true)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(periodic.id).get().state)
        assertEquals(WorkInfo.State.CANCELLED, manager.getWorkInfoById(catchUp.id).get().state)
    }
}
