package cx.aswin.boxlore.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import cx.aswin.boxlore.core.prefs.AutoBackupFrequency
import cx.aswin.boxlore.core.prefs.AutoBackupSettings
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoBackupSchedulingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var manager: WorkManager

    @Before fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        manager = WorkManager.getInstance(context)
    }

    private fun periodic(): List<WorkInfo> = manager.getWorkInfosForUniqueWork(AutoBackupScheduling.PERIODIC_WORK_NAME).get()

    private fun liveWork(name: String): List<WorkInfo> = manager.getWorkInfosForUniqueWork(name).get().filterNot { it.state.isFinished }

    @Test fun `the names are the permanent identities`() {
        assertEquals("boxlore_auto_backup_periodic", AutoBackupScheduling.PERIODIC_WORK_NAME)
        assertEquals("boxlore_auto_backup_now", AutoBackupScheduling.NOW_WORK_NAME)
    }

    @Test fun `on with a folder schedules weekly by default with battery and storage but no network`() = runBlocking {
        AutoBackupScheduling.reconcile(context, AutoBackupScheduling.planOf(active()))
        val work = periodic().single()
        assertEquals(WorkInfo.State.ENQUEUED, work.state)
        assertEquals(TimeUnit.DAYS.toMillis(7), work.periodicityInfo!!.repeatIntervalMillis)
        assertTrue(work.constraints.requiresBatteryNotLow())
        assertTrue(work.constraints.requiresStorageNotLow())
        assertFalse(work.constraints.requiresCharging())
        assertEquals(NetworkType.NOT_REQUIRED, work.constraints.requiredNetworkType)
    }

    @Test fun `following settings updates the same work, ignores unrelated changes and cancels when off`() {
        val settings = MutableStateFlow(AutoBackupSettings())
        // On its own thread, like the app's scope: Room's blocking calls must not share this test's event loop.
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch { AutoBackupScheduling.follow(context, settings) }
        settle()
        assertTrue(liveWork(AutoBackupScheduling.PERIODIC_WORK_NAME).isEmpty())

        // On without a folder (the picker was cancelled) schedules nothing.
        settings.value = AutoBackupSettings(enabled = true)
        settle()
        assertTrue(liveWork(AutoBackupScheduling.PERIODIC_WORK_NAME).isEmpty())

        settings.value = active()
        awaitWork { liveWork(AutoBackupScheduling.PERIODIC_WORK_NAME).size == 1 }
        val id = periodic().single().id

        settings.value = active(AutoBackupFrequency.DAILY)
        awaitWork { periodic().single().periodicityInfo!!.repeatIntervalMillis == TimeUnit.DAYS.toMillis(1) }
        assertEquals(id, periodic().single().id)

        // Keep count and run results never touch the schedule.
        settings.value = active(AutoBackupFrequency.DAILY).copy(keepCount = 10, lastSuccessAt = 5L)
        settle()
        assertEquals(id, liveWork(AutoBackupScheduling.PERIODIC_WORK_NAME).single().id)

        AutoBackupScheduling.backUpNow(context)
        settings.value = AutoBackupSettings(frequency = AutoBackupFrequency.DAILY)
        awaitWork { liveWork(AutoBackupScheduling.PERIODIC_WORK_NAME).isEmpty() && liveWork(AutoBackupScheduling.NOW_WORK_NAME).isEmpty() }
        scope.cancel()
    }

    @Test fun `back up now keeps one pending run that needs only storage, and a new folder replaces it`(): Unit = runBlocking {
        AutoBackupScheduling.backUpNow(context)
        AutoBackupScheduling.backUpNow(context)
        val pending = liveWork(AutoBackupScheduling.NOW_WORK_NAME)
        assertEquals(1, pending.size)
        assertTrue(pending.single().constraints.requiresStorageNotLow())
        assertFalse(pending.single().constraints.requiresBatteryNotLow())
        // A run for the previous folder is replaced, so the new folder still gets its first backup.
        AutoBackupScheduling.backUpNow(context, newFolder = true)
        val replaced = liveWork(AutoBackupScheduling.NOW_WORK_NAME)
        assertEquals(1, replaced.size)
        assertTrue(replaced.single().id != pending.single().id)
        // REPLACE cancels the old run and WorkManager drops its record.
        assertTrue(manager.getWorkInfoById(pending.single().id).get()?.state.let { it == null || it == WorkInfo.State.CANCELLED })
        withContext(Dispatchers.IO) {
            AutoBackupScheduling.reconcile(context, AutoBackupScheduling.Plan(active = false, frequency = AutoBackupFrequency.WEEKLY))
        }
        assertTrue(liveWork(AutoBackupScheduling.NOW_WORK_NAME).isEmpty())
    }

    @Test fun `backing up means running or retrying, not waiting for storage to start`() {
        val running = info(WorkInfo.State.RUNNING)
        val waiting = info(WorkInfo.State.ENQUEUED)
        val retrying = info(WorkInfo.State.ENQUEUED, attempts = 1)
        assertFalse(AutoBackupScheduling.isBackingUp(emptyList(), emptyList()))
        assertFalse(AutoBackupScheduling.isBackingUp(listOf(waiting), listOf(waiting)))
        assertTrue(AutoBackupScheduling.isBackingUp(emptyList(), listOf(running)))
        assertTrue(AutoBackupScheduling.isBackingUp(listOf(running), emptyList()))
        assertTrue(AutoBackupScheduling.isBackingUp(emptyList(), listOf(retrying)))
        assertFalse(AutoBackupScheduling.isBackingUp(emptyList(), listOf(info(WorkInfo.State.SUCCEEDED, attempts = 1))))
    }

    @Test fun `the flow stays idle while back up now waits for its constraints`(): Unit = runBlocking {
        AutoBackupScheduling.backUpNow(context)
        assertEquals(WorkInfo.State.ENQUEUED, liveWork(AutoBackupScheduling.NOW_WORK_NAME).single().state)
        assertFalse(withContext(Dispatchers.IO) { withTimeout(5_000) { AutoBackupScheduling.isBackingUp(context).first() } })
        withContext(Dispatchers.IO) {
            AutoBackupScheduling.reconcile(context, AutoBackupScheduling.Plan(active = false, frequency = AutoBackupFrequency.WEEKLY))
        }
    }

    private fun info(state: WorkInfo.State, attempts: Int = 0) =
        WorkInfo(id = UUID.randomUUID(), state = state, tags = emptySet(), runAttemptCount = attempts)

    private fun active(frequency: AutoBackupFrequency = AutoBackupFrequency.WEEKLY) =
        AutoBackupSettings(enabled = true, folderUri = "content://tree/sync", frequency = frequency)

    private fun settle() = Thread.sleep(200)

    private fun awaitWork(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Work didn't reach the expected state" }
            Thread.sleep(20)
        }
    }

    @Test fun `a failed schedule update is tried again, and a newer plan replaces the wait`(): Unit = runBlocking {
        val tried = mutableListOf<AutoBackupScheduling.Plan>()
        val settings = MutableStateFlow(active())
        val follow = launch {
            AutoBackupScheduling.follow(context, settings, apply = { _, plan ->
                tried += plan
                if (tried.size < 3) error("work database busy")
            }, retryDelayMs = 1L)
        }
        withTimeout(5_000) { while (tried.size < 3) delay(5) }
        assertEquals(List(3) { AutoBackupScheduling.planOf(active()) }, tried)
        follow.cancel()

        // A newer plan doesn't wait out the old plan's retry: it applies at once, and the old one stops.
        val applied = mutableListOf<AutoBackupScheduling.Plan>()
        val changing = MutableStateFlow(active())
        val waiting = launch {
            AutoBackupScheduling.follow(context, changing, apply = { _, plan ->
                applied += plan
                if (plan.active) error("work database busy")
            }, retryDelayMs = 60_000L)
        }
        withTimeout(5_000) { while (applied.isEmpty()) delay(5) }
        changing.value = AutoBackupSettings()
        withTimeout(5_000) { while (applied.size < 2) delay(5) }
        delay(100)
        assertEquals(listOf(AutoBackupScheduling.planOf(active()), AutoBackupScheduling.planOf(AutoBackupSettings())), applied)
        waiting.cancel()

        // A plan that keeps failing stops after its tries.
        val failing = mutableListOf<AutoBackupScheduling.Plan>()
        AutoBackupScheduling.follow(context, flowOf(active()), apply = { _, plan ->
            failing += plan
            error("still busy")
        }, retryDelayMs = 1L)
        assertEquals(AutoBackupScheduling.RECONCILE_TRIES, failing.size)
    }
}
