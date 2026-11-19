package cx.aswin.boxlore.feature.settings.pages

import android.app.Application
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityOptionsCompat
import cx.aswin.boxlore.core.prefs.AutoBackupFrequency
import cx.aswin.boxlore.core.prefs.AutoBackupProblem
import cx.aswin.boxlore.core.prefs.AutoBackupSettings
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AutoBackupGroupTest {
    private val folder = AutoBackupSettings(enabled = true, folderUri = "content://tree/sync", folderName = "Sync")

    @Test fun `status says when the last backup saved, or that none has yet`() {
        val at = LocalDateTime.of(2026, 12, 15, 9, 5).toInstant(ZoneOffset.UTC).toEpochMilli()
        assertEquals("No backups yet", autoBackupStatusText(AutoBackupUiState(folder), ZoneOffset.UTC, Locale.US))
        assertEquals(
            "Last backup: Dec 15, 2026, 9:05 AM",
            autoBackupStatusText(AutoBackupUiState(folder.copy(lastSuccessAt = at)), ZoneOffset.UTC, Locale.US).replace(' ', ' '),
        )
        assertEquals("Backing up…", autoBackupStatusText(AutoBackupUiState(folder.copy(lastSuccessAt = at), isBackingUp = true)))
    }

    @Test fun `folder problems ask for the folder again and other problems offer a retry`() {
        assertNull(autoBackupProblemText(folder))
        assertNull(autoBackupFixLabel(folder))
        val lost = folder.copy(lastError = AutoBackupProblem.PERMISSION_LOST)
        assertEquals("boxlore can't save to this folder anymore.", autoBackupProblemText(lost))
        assertEquals("Choose folder again", autoBackupFixLabel(lost))
        val missing = folder.copy(lastError = AutoBackupProblem.FOLDER_MISSING)
        assertEquals("This folder was moved or deleted.", autoBackupProblemText(missing))
        assertEquals("Choose folder again", autoBackupFixLabel(missing))
        val io = folder.copy(lastError = AutoBackupProblem.IO_ERROR)
        assertEquals("The last backup didn't save. boxlore will try again next time.", autoBackupProblemText(io))
        assertEquals("Try again", autoBackupFixLabel(io))
    }

    @Test fun `frequency labels`() {
        assertEquals(listOf("Daily", "Weekly"), AutoBackupFrequency.entries.map(::autoBackupFrequencyLabel))
    }

    @Test fun `a device without a folder picker shows a message instead of crashing`() {
        var unavailable = 0
        launchFolderPicker(launcher { throw ActivityNotFoundException("no OPEN_DOCUMENT_TREE handler") }, null) { unavailable++ }
        assertEquals(1, unavailable)

        val launched = mutableListOf<Uri?>()
        launchFolderPicker(launcher { launched += it }, null) { unavailable++ }
        assertEquals(listOf<Uri?>(null), launched)
        assertEquals(1, unavailable)
        assertTrue(NO_FOLDER_PICKER_MESSAGE.contains("boxlore"))
    }

    private fun launcher(onLaunch: (Uri?) -> Unit) = object : ActivityResultLauncher<Uri?>() {
        override fun launch(input: Uri?, options: ActivityOptionsCompat?) = onLaunch(input)

        override fun unregister() = Unit

        override val contract: ActivityResultContract<Uri?, *> = ActivityResultContracts.OpenDocumentTree()
    }
}
