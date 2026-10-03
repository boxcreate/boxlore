package cx.aswin.boxlore.lifecycle

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import cx.aswin.boxlore.core.downloads.AutoDownloadScheduling
import cx.aswin.boxlore.core.downloads.AutoDownloadWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
        .apply { if (automatic) addTag(AutoDownloadScheduling.TRANSFER_TAG) }
        .build()
}
