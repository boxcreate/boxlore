package cx.aswin.boxlore.backup

import android.app.Application
import cx.aswin.boxlore.core.prefs.AutoBackupProblem
import java.io.FileNotFoundException
import java.io.IOException
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AutoBackupRunnerTest {
    private val now = LocalDateTime.of(2026, 12, 15, 9, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
    private val bytes = "{\"version\":8}".toByteArray()

    private fun runner(folder: AutoBackupFolder, export: suspend () -> LibraryJsonBackup.Export = { LibraryJsonBackup.Export(bytes, 4) }) =
        AutoBackupRunner(folder, export, { now }, ZoneOffset.UTC)

    @Test fun `saves through a temporary name and prunes only older automatic backups`() = runBlocking {
        val folder = FakeAutoBackupFolder(
            "boxlore_auto_backup_2026-11-24_0900.json",
            "boxlore_auto_backup_2026-12-01_0900.json",
            "boxlore_auto_backup_2026-12-08_0900.json",
            "boxlore_auto_backup_2026-12-08_0900.json.tmp",
            "boxlore_backup_1765000000000.json",
            "keep-me.txt",
        )
        val outcome = runner(folder).run(keep = 2)
        assertEquals(AutoBackupOutcome.Saved("boxlore_auto_backup_2026-12-15_0905.json", 4, pruned = 3), outcome)
        assertEquals(listOf("boxlore_auto_backup_2026-12-15_0905.json" to "boxlore_auto_backup_2026-12-15_0905.tmp.json"), folder.writes)
        assertArrayEquals(bytes, folder.files.getValue("boxlore_auto_backup_2026-12-15_0905.json"))
        assertEquals(
            setOf("boxlore_auto_backup_2026-12-08_0900.json", "boxlore_auto_backup_2026-12-15_0905.json", "boxlore_backup_1765000000000.json", "keep-me.txt"),
            folder.files.keys,
        )
    }

    @Test fun `a clock behind the folder's newest backups still keeps the file it just saved`() = runBlocking {
        // The phone's clock reads 2026-12-15 while the folder already holds later backups, from a synced device or a reset clock.
        val folder = FakeAutoBackupFolder(
            "boxlore_auto_backup_2027-01-05_0900.json",
            "boxlore_auto_backup_2027-01-12_0900.json",
            "boxlore_auto_backup_2027-01-19_0900.json",
        )
        val outcome = runner(folder).run(keep = 3)
        assertEquals(AutoBackupOutcome.Saved("boxlore_auto_backup_2026-12-15_0905.json", 4, pruned = 1), outcome)
        assertEquals(
            setOf("boxlore_auto_backup_2026-12-15_0905.json", "boxlore_auto_backup_2027-01-12_0900.json", "boxlore_auto_backup_2027-01-19_0900.json"),
            folder.files.keys,
        )
    }

    @Test fun `a lost grant or a missing folder is not retryable and writes nothing`() = runBlocking {
        val lost = FakeAutoBackupFolder().apply { reachError = SecurityException("revoked") }
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.PERMISSION_LOST, retryable = false), runner(lost).run(5))
        val missing = FakeAutoBackupFolder().apply { reachError = FileNotFoundException("gone") }
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.FOLDER_MISSING, retryable = false), runner(missing).run(5))
        assertTrue(lost.writes.isEmpty() && missing.writes.isEmpty())
        // The folder vanishing between the check and the write reads the same way.
        val vanished = FakeAutoBackupFolder().apply {
            writeError = FileNotFoundException("gone")
            reachErrorAfterWrite = FileNotFoundException("gone")
        }
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.FOLDER_MISSING, retryable = false), runner(vanished).run(5))
    }

    @Test fun `write and export failures are retryable`() = runBlocking {
        val full = FakeAutoBackupFolder().apply { writeError = IOException("no space") }
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.IO_ERROR, retryable = true), runner(full).run(5))
        val provider = FakeAutoBackupFolder().apply { writeError = UnsupportedOperationException("provider") }
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.IO_ERROR, retryable = true), runner(provider).run(5))
        val folder = FakeAutoBackupFolder()
        assertEquals(
            AutoBackupOutcome.Failed(AutoBackupProblem.EXPORT_FAILED, retryable = true),
            runner(folder) { throw IllegalStateException("database closed") }.run(5),
        )
        assertTrue(folder.writes.isEmpty())
    }

    @Test fun `a new file that vanished while the folder is still there is retried, not reported as a missing folder`() = runBlocking {
        val folder = FakeAutoBackupFolder().apply { writeError = FileNotFoundException("document") }
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.IO_ERROR, retryable = true), runner(folder).run(keep = 3))
    }

    @Test fun `a folder that went away during the write is still reported as missing`() = runBlocking {
        val folder = FakeAutoBackupFolder().apply {
            writeError = FileNotFoundException("document")
            reachErrorAfterWrite = FileNotFoundException("folder")
        }
        assertEquals(AutoBackupOutcome.Failed(AutoBackupProblem.FOLDER_MISSING, retryable = false), runner(folder).run(keep = 3))
    }

    @Test fun `a pruning failure never fails a saved backup`() = runBlocking {
        val folder = FakeAutoBackupFolder("boxlore_auto_backup_2026-11-24_0900.json").apply { listError = IOException("list") }
        val outcome = runner(folder).run(keep = 1)
        assertEquals(AutoBackupOutcome.Saved("boxlore_auto_backup_2026-12-15_0905.json", 4, pruned = 0), outcome)
    }

    @Test fun `logs never carry the folder path from a provider's message`() = runBlocking {
        ShadowLog.clear()
        val folder = FakeAutoBackupFolder().apply { writeError = IOException("primary:Sync/boxlore is full") }
        runner(folder).run(5)
        runner(FakeAutoBackupFolder()) { throw IllegalStateException("content://tree/primary%3ASync") }.run(5)
        val logs = ShadowLog.getLogsForTag("AutoBackupRunner")
        assertEquals(2, logs.size)
        assertTrue(logs.all { it.throwable == null && !it.msg.contains("Sync") })
    }

    @Test(expected = CancellationException::class)
    fun `cancellation is not recorded as a failure`(): Unit = runBlocking {
        runner(FakeAutoBackupFolder()) { throw CancellationException("stopped") }.run(5)
    }
}
