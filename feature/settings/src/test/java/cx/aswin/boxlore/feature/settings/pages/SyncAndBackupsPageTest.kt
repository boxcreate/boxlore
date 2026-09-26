package cx.aswin.boxlore.feature.settings.pages

import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncAndBackupsPageTest {

    @Test
    fun resolveSyncBadgeText_returnsExpectedStrings() {
        assertEquals("Sync issue", resolveSyncBadgeText(CloudSyncUiStatus.Error("Network failure")))
        assertEquals("Syncing...", resolveSyncBadgeText(CloudSyncUiStatus.Syncing))
        assertEquals("Cloud sync active", resolveSyncBadgeText(CloudSyncUiStatus.Idle))
        assertEquals("Cloud sync active", resolveSyncBadgeText(CloudSyncUiStatus.Success(12345678L)))
    }

    @Test
    fun resolveCloudSyncSubtitle_returnsExpectedStrings() {
        assertEquals("Signed in as listener@boxlore.cx", resolveCloudSyncSubtitle("listener@boxlore.cx"))
        assertEquals("Sign in to backup and sync across devices", resolveCloudSyncSubtitle(null))
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
