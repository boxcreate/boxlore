package cx.aswin.boxlore.feature.settings.pages

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.text.TextLayoutResult
import cx.aswin.boxlore.core.catalog.sync.CloudSyncUiStatus
import cx.aswin.boxlore.core.designsystem.theme.BoxLoreTheme
import cx.aswin.boxlore.feature.settings.R
import cx.aswin.boxlore.feature.settings.text
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SyncAndBackupsPageTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun `the Cloud sync card's title says the state, on the Account page's solid colours`() {
        assertEquals("Sign in to sync", text(resolveCloudSyncTitle(null)))
        assertEquals("Sync is on", text(resolveCloudSyncTitle("listener@boxlore.cx")))
        val seen = mutableMapOf<String, Pair<Color, Color>>()
        val expected = mutableMapOf<String, Pair<Color, Color>>()
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                val colors = MaterialTheme.colorScheme
                expected["signedOut"] = colors.primaryContainer to colors.onPrimaryContainer
                expected["syncing"] = colors.primaryContainer to colors.onPrimaryContainer
                expected["error"] = colors.errorContainer to colors.onErrorContainer
                expected["synced"] = colors.tertiaryContainer to colors.onTertiaryContainer
                resolveSyncVisual(false, CloudSyncUiStatus.Idle).let { seen["signedOut"] = it.iconBackground to it.iconTint }
                resolveSyncVisual(true, CloudSyncUiStatus.Syncing).let { seen["syncing"] = it.iconBackground to it.iconTint }
                resolveSyncVisual(true, CloudSyncUiStatus.Error("x")).let { seen["error"] = it.iconBackground to it.iconTint }
                resolveSyncVisual(true, CloudSyncUiStatus.Success(1L)).let { seen["synced"] = it.iconBackground to it.iconTint }
            }
        }
        compose.waitForIdle()
        assertEquals(expected, seen)
        seen.values.forEach { assertEquals(1f, it.first.alpha) }
    }

    @Config(qualifiers = "w360dp-h900dp")
    @Test
    fun `a long email stays on one line, and Import explains the Missing episodes refresh`() {
        compose.setContent {
            BoxLoreTheme(dynamicColor = false) {
                SyncAndBackupsPage("a.listener.with.a.very.long.address.for.testing@example.test", CloudSyncUiStatus.Success(1L), LibraryBackupActions({}, {}, {}, {}), {}, {})
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithTag(CLOUD_SYNC_SUBTITLE_TAG, useUnmergedTree = true).fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        assertEquals(1, layouts.last().lineCount)
        compose.onNodeWithText(text(R.string.settings_sync_import_footer)).performScrollTo().assertExists()
        compose.onNodeWithText("Cloud Sync", substring = false).assertDoesNotExist()
    }

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
        assertEquals("Sign in to back up and sync across devices", resolveCloudSyncSubtitle(null).text())
        // The page card's title already asks you to sign in, so its second line says what you get.
        assertEquals("Back up and sync your library across devices", resolveCloudSyncCardSubtitle(null).text())
        assertEquals("Signed in as listener@boxlore.cx", resolveCloudSyncCardSubtitle("listener@boxlore.cx").text())
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
