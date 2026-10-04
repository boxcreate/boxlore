package cx.aswin.boxlore.core.downloads

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import cx.aswin.boxlore.core.prefs.AutoDownloadBackgroundSettings
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoDownloadSchedulingTest {
    private lateinit var context: Context
    private lateinit var manager: WorkManager
    private val background = AutoDownloadBackgroundSettings(enabled = true, chargingOnly = true)

    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        manager = WorkManager.getInstance(context)
    }

    @Test fun pushSupersedesUnfinishedBackgroundWorkAndSurvivesBackgroundCancellation() = runBlocking {
        enqueue(background)
        val gated = activeWork()
        assertTrue(gated.constraints.requiresCharging())
        enqueue(null)
        val push = activeWork()
        assertFalse(push.id == gated.id)
        assertNull(manager.getWorkInfoById(gated.id).get())
        assertFalse(AutoDownloadScheduling.BACKGROUND_TRANSFER_TAG in push.tags)
        assertFalse(push.constraints.requiresBatteryNotLow())
        assertFalse(push.constraints.requiresCharging())
        manager.cancelAllWorkByTag(AutoDownloadScheduling.BACKGROUND_TRANSFER_TAG).result.get()
        assertEquals(push.id, activeWork().id)
    }

    @Test fun repeatedBackgroundAdmissionKeepsTheExistingGatedRequest() = runBlocking {
        enqueue(background)
        val first = activeWork()
        enqueue(background)
        assertEquals(first.id, activeWork().id)
    }

    @Test fun foregroundDuplicatesAndLaterBackgroundAdmissionKeepTheOrdinaryRequest() = runBlocking {
        enqueue(null)
        val first = activeWork()
        enqueue(null)
        enqueue(background)
        assertEquals(first.id, activeWork().id)
        assertFalse(AutoDownloadScheduling.BACKGROUND_TRANSFER_TAG in activeWork().tags)
    }

    @Test fun pushAlsoSupersedesLegacyWorkWithUnknownDiscoverySource() = runBlocking {
        val legacy = OneTimeWorkRequestBuilder<AutoDownloadWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .addTag(AutoDownloadScheduling.TRANSFER_TAG).build()
        manager.enqueueUniqueWork(AutoDownloadScheduling.episodeWorkName("episode"), ExistingWorkPolicy.KEEP, legacy).result.get()
        enqueue(null)
        assertNull(manager.getWorkInfoById(legacy.id).get())
        assertTrue(AutoDownloadScheduling.POLICY_TRANSFER_TAG in activeWork().tags)
    }

    private suspend fun enqueue(background: AutoDownloadBackgroundSettings?) {
        AutoDownloadScheduling.enqueueEpisode(context, "show", "episode", wifiOnly = true, background = background)
    }

    private fun activeWork(): WorkInfo = manager.getWorkInfosForUniqueWork(AutoDownloadScheduling.episodeWorkName("episode"))
        .get().single { !it.state.isFinished }
}
