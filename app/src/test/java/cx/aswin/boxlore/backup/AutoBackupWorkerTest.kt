package cx.aswin.boxlore.backup

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import cx.aswin.boxlore.core.prefs.AutoBackupProblem
import cx.aswin.boxlore.core.prefs.AutoBackupStore
import cx.aswin.boxlore.ui.libraryimport.LibraryBackupAnalyticsErrors
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoBackupWorkerTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val store = AutoBackupStore(context)
    private val reports = mutableListOf<AutoBackupOutcome>()
    private val folder = FakeAutoBackupFolder()
    private var exports = 0

    @Before fun chooseFolder() = runBlocking {
        store.setFolder(FOLDER, "Sync")
        store.setKeepCount(3)
    }

    @After fun clear() = runBlocking { store.turnOff() }

    @Test fun `a saved backup records its time and file and reports once`() = runBlocking {
        val result = worker(attempt = 0).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        val settings = store.current()
        assertEquals(1_000L, settings.lastSuccessAt)
        assertEquals(1_000L, settings.lastAttemptAt)
        assertEquals(settings.lastFileName, folder.files.keys.single())
        assertNull(settings.lastError)
        assertEquals(listOf(AutoBackupOutcome.Saved(settings.lastFileName!!, 2, 0)), reports)
    }

    @Test fun `a lost grant succeeds without retrying and tells Settings`() = runBlocking {
        folder.reachError = SecurityException("revoked")
        assertEquals(ListenableWorker.Result.success(), worker(attempt = 0).doWork())
        assertEquals(AutoBackupProblem.PERMISSION_LOST, store.current().lastError)
        assertEquals(listOf(AutoBackupOutcome.Failed(AutoBackupProblem.PERMISSION_LOST, false)), reports)
        assertEquals(0, exports)
    }

    @Test fun `a missing folder succeeds without retrying and tells Settings`() = runBlocking {
        folder.reachError = FileNotFoundException("gone")
        assertEquals(ListenableWorker.Result.success(), worker(attempt = 0).doWork())
        assertEquals(AutoBackupProblem.FOLDER_MISSING, store.current().lastError)
    }

    @Test fun `a write failure retries quietly, then records the problem on the last try`() = runBlocking {
        folder.writeError = IOException("busy")
        assertEquals(ListenableWorker.Result.retry(), worker(attempt = 0).doWork())
        assertEquals(ListenableWorker.Result.retry(), worker(attempt = 1).doWork())
        assertNull(store.current().lastError)
        assertTrue(reports.isEmpty())
        assertEquals(ListenableWorker.Result.failure(), worker(attempt = AutoBackupScheduling.MAX_ATTEMPTS - 1).doWork())
        assertEquals(AutoBackupProblem.IO_ERROR, store.current().lastError)
        assertEquals(listOf(AutoBackupOutcome.Failed(AutoBackupProblem.IO_ERROR, true)), reports)
        // The next good run clears it.
        folder.writeError = null
        assertEquals(ListenableWorker.Result.success(), worker(attempt = 0).doWork())
        assertNull(store.current().lastError)
    }

    @Test fun `an export failure retries like a write failure`() = runBlocking {
        val failing = worker(attempt = 0).apply { export = { throw IllegalStateException("db") } }
        assertEquals(ListenableWorker.Result.retry(), failing.doWork())
        val last = worker(attempt = 2).apply { export = { throw IllegalStateException("db") } }
        assertEquals(ListenableWorker.Result.failure(), last.doWork())
        assertEquals(AutoBackupProblem.EXPORT_FAILED, store.current().lastError)
    }

    @Test fun `a run after turning off or before a folder does nothing`() = runBlocking {
        store.turnOff()
        val attemptBefore = store.current().lastAttemptAt
        assertEquals(ListenableWorker.Result.success(), worker(attempt = 0).doWork())
        assertEquals(0, exports)
        assertEquals(attemptBefore, store.current().lastAttemptAt)
        assertTrue(folder.writes.isEmpty())
    }

    @Test fun `a run for a folder replaced mid-run doesn't stamp the new folder's last backup`() = runBlocking {
        val replaced = worker(attempt = 0).apply {
            export = {
                // The listener picks another folder while this run builds its backup.
                store.setFolder("content://tree/new", "New")
                store.recordProblem(AutoBackupProblem.IO_ERROR, "content://tree/new")
                LibraryJsonBackup.Export("{}".toByteArray(), 2)
            }
        }
        assertEquals(ListenableWorker.Result.success(), replaced.doWork())
        val settings = store.current()
        assertNull(settings.lastSuccessAt)
        assertNull(settings.lastFileName)
        assertEquals(AutoBackupProblem.IO_ERROR, settings.lastError)
    }

    @Test fun `every problem code is an allowed analytics error code`() {
        val allowed = setOf(
            LibraryBackupAnalyticsErrors.PERMISSION_LOST,
            LibraryBackupAnalyticsErrors.FOLDER_MISSING,
            LibraryBackupAnalyticsErrors.IO_ERROR,
            LibraryBackupAnalyticsErrors.EXPORT_FAILED,
        )
        assertEquals(
            allowed,
            setOf(AutoBackupProblem.PERMISSION_LOST, AutoBackupProblem.FOLDER_MISSING, AutoBackupProblem.IO_ERROR, AutoBackupProblem.EXPORT_FAILED),
        )
    }

    private fun worker(attempt: Int) = TestListenableWorkerBuilder<AutoBackupWorker>(context)
        .setRunAttemptCount(attempt)
        .build()
        .apply {
            store = this@AutoBackupWorkerTest.store
            folderFor = { uri ->
                assertEquals(FOLDER, uri)
                folder
            }
            export = {
                exports++
                LibraryJsonBackup.Export("{}".toByteArray(), 2)
            }
            nowMs = { 1_000L }
            report = { reports += it }
        }

    private companion object {
        const val FOLDER = "content://com.android.externalstorage.documents/tree/primary%3ASync"
    }
}
