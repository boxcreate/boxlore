package cx.aswin.boxlore.feature.settings.pages

import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.feature.settings.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncAndBackupsPageTest {

    @Test
    fun resolveSyncBadgeText_returnsExpectedStrings() {
        assertEquals("Sync issue", text(resolveSyncBadgeText(CloudSyncUiStatus.Error("Network failure"))))
        assertEquals("Syncing...", text(resolveSyncBadgeText(CloudSyncUiStatus.Syncing)))
        assertEquals("Cloud sync active", text(resolveSyncBadgeText(CloudSyncUiStatus.Idle)))
        assertEquals("Cloud sync active", text(resolveSyncBadgeText(CloudSyncUiStatus.Success(12345678L))))
    }

    @Test
    fun resolveCloudSyncSubtitle_returnsExpectedStrings() {
        assertEquals("Signed in as listener@boxlore.cx", resolveCloudSyncSubtitle("listener@boxlore.cx").text())
        assertEquals("Sign in to backup and sync across devices", resolveCloudSyncSubtitle(null).text())
    }

    @Test
    fun fullLibraryExportSummary_namesOnlyWhatTheJsonExportWrites() {
        // LibraryBackupExportTest pins the exported sections; Missing episodes? opt-ins are not written.
        assertEquals("Subscriptions, folders, playlists, history, likes, and settings (JSON)", text(FULL_LIBRARY_EXPORT_SUMMARY))
    }

    @Test
    fun libraryBackupActions_triggersCallbacksCorrectly() {
        var exportJsonCalled = false
        var exportOpmlCalled = false
        var importJsonCalled = false
        var importOpmlCalled = false

        val actions = LibraryBackupActions(
            onExportJson = { exportJsonCalled = true },
            onExportOpml = { exportOpmlCalled = true },
            onImportJson = { importJsonCalled = true },
            onImportOpml = { importOpmlCalled = true },
        )

        actions.onExportJson()
        assertTrue(exportJsonCalled)

        actions.onExportOpml()
        assertTrue(exportOpmlCalled)

        actions.onImportJson()
        assertTrue(importJsonCalled)

        actions.onImportOpml()
        assertTrue(importOpmlCalled)
    }
}
