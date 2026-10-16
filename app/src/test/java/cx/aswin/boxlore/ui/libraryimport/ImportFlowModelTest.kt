package cx.aswin.boxlore.ui.libraryimport

import android.net.Uri
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.backup.JsonBackupPhase
import cx.aswin.boxlore.core.designsystem.echo.parseEchoCharacterManifest
import cx.aswin.boxlore.core.model.Podcast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class ImportFlowModelTest {
    private val shows = (1..3).map { Podcast("p$it", "Show $it", "Host $it", "") }

    @Test fun `a broken backup reads as a failed restore, not a finished import with -1 shows`() {
        // The backup manager swallows restore errors and reports -1; this used to show "You're all set".
        assertEquals(OpmlImportState.Error(ImportFailure.BackupFailed), jsonImportOutcome(-1, hasNotificationsEnabled = false))
        assertEquals(
            OpmlImportState.Success(importedCount = 5, completedCount = 0, isJson = true, hasNotificationsEnabled = true),
            jsonImportOutcome(5, hasNotificationsEnabled = true),
        )
        assertEquals(OpmlImportState.Success(importedCount = 0, completedCount = 0, isJson = true), jsonImportOutcome(0, hasNotificationsEnabled = false))
    }

    @Test fun `only failures before anything is written promise an unchanged library`() {
        val unchanged = ImportFailure.entries.filter { it.libraryUnchanged }
        assertEquals(listOf(ImportFailure.FileUnreadable, ImportFailure.NoFeeds), unchanged)
        assertEquals(ImportFailure.FileUnreadable, opmlFailureFor(parsed = false))
        assertEquals(ImportFailure.ImportStopped, opmlFailureFor(parsed = true))
        assertEquals(ImportRetry.None, ImportFailure.MarkingStopped.retry)
        assertEquals(ImportRetry.AnotherFile, ImportFailure.BackupFailed.retry)
        ImportFailure.entries.forEach { importFailureCopy(it) }
    }

    @Test fun `working and done share one screen and Echo leaves the Start fresh list`() {
        val importing = OpmlImportState.Importing("Show 1", 0.5f, 1, 2, shows)
        assertEquals(ImportScreen.Pick, importScreenFor(OpmlImportState.ShowSelector))
        assertEquals(ImportScreen.Progress, importScreenFor(OpmlImportState.Parsing(Uri.EMPTY)))
        assertEquals(ImportScreen.Progress, importScreenFor(importing))
        assertEquals(ImportScreen.Progress, importScreenFor(OpmlImportState.Success(3, 0)))
        assertEquals(ImportScreen.Ask, importScreenFor(OpmlImportState.AskCompleted(shows, emptySet())))
        assertEquals(ImportScreen.Failed, importScreenFor(OpmlImportState.Error(ImportFailure.NoFeeds)))
        assertNull(importScreenFor(OpmlImportState.Idle))
        assertEquals(ImportEchoMoment.Working, importEchoMomentFor(importing))
        assertEquals(ImportEchoMoment.Done, importEchoMomentFor(OpmlImportState.Success(3, 0)))
        assertEquals(ImportEchoMoment.Failed, importEchoMomentFor(OpmlImportState.Error(ImportFailure.NoFeeds)))
        assertNull(importEchoMomentFor(OpmlImportState.AskCompleted(shows, emptySet())))
    }

    @Test fun `progress counts the show being worked on and stays within the bar`() {
        assertEquals(ImportProgress(12, 48, 11f / 48), importProgressFor(OpmlImportState.Importing("A", 11f / 48, 11, 48, emptyList())))
        assertEquals(ImportProgress(10, 10, 1f), importProgressFor(OpmlImportState.ImportingJson(progress = 1.8f, currentCount = 12, totalCount = 10)))
        assertEquals(ImportProgress(1, 10, 0f), importProgressFor(OpmlImportState.ImportingJson(progress = -0.2f, currentCount = -3, totalCount = 10)))
        assertNull(importProgressFor(OpmlImportState.ImportingJson(totalCount = 0)))
        assertNull(importProgressFor(OpmlImportState.Parsing(Uri.EMPTY)))
        assertEquals(ImportProgress(2, 3, 1f / 3), importProgressFor(OpmlImportState.Completing(1f / 3, "Show 2", shows, 3)))
    }

    @Test fun `the checklist follows the real import stages`() {
        fun statuses(state: OpmlImportState) = importStepsFor(state).map { it.kind to it.status }
        assertEquals(
            listOf(ImportStepKind.ReadFile to ImportStepStatus.Active, ImportStepKind.Subscribe to ImportStepStatus.Upcoming, ImportStepKind.ChooseMarkPlayed to ImportStepStatus.Upcoming),
            statuses(OpmlImportState.Parsing(Uri.EMPTY)),
        )
        val importing = importStepsFor(OpmlImportState.Importing("A", 0.1f, 4, 48, emptyList()))
        assertEquals(ImportStep(ImportStepKind.ReadFile, ImportStepStatus.Done, 48), importing.first())
        assertEquals(ImportStepStatus.Active, importing[1].status)
        val completing = importStepsFor(OpmlImportState.Completing(0.5f, "Show 2", shows, 45))
        assertEquals(ImportStep(ImportStepKind.Subscribe, ImportStepStatus.Done, 45), completing[1])
        assertEquals(ImportStep(ImportStepKind.MarkPlayed, ImportStepStatus.Active), completing[2])
        assertEquals(
            listOf(ImportStepKind.RestoreBackup to ImportStepStatus.Active, ImportStepKind.LatestEpisodes to ImportStepStatus.Upcoming),
            statuses(OpmlImportState.ImportingJson(phase = JsonBackupPhase.PREPARING)),
        )
        assertEquals(
            listOf(ImportStepKind.RestoreBackup to ImportStepStatus.Done, ImportStepKind.LatestEpisodes to ImportStepStatus.Active),
            statuses(OpmlImportState.ImportingJson(phase = JsonBackupPhase.REFRESHING_FEEDS, totalCount = 4)),
        )
        assertTrue(importStepsFor(OpmlImportState.ImportingJson(phase = JsonBackupPhase.COMPLETED)).all { it.status == ImportStepStatus.Done })
        assertTrue(importStepsFor(OpmlImportState.Success(3, 0)).isEmpty())
    }

    @Test fun `every Echo pose the import uses ships in the Echo manifest with a stage reach`() {
        val spec = parseEchoCharacterManifest(java.io.File("../core/designsystem/src/main/assets/echo-character/manifest.json").readText())
        ImportEchoMoment.entries.forEach {
            assertTrue("missing ${it.sequence}", it.sequence in spec.sequences)
            assertTrue("no stage reach for ${it.sequence}", spec.reach(it.sequence) != null)
        }
    }

    @Test fun `Echo takes about a quarter of the height within readable bounds`() {
        assertEquals(ImportEchoMinSize, importEchoSize(200.dp))
        assertEquals(640.dp * ImportEchoHeightShare, importEchoSize(640.dp))
        assertEquals(ImportEchoMaxSize, importEchoSize(1200.dp))
    }

    @Test fun `the notifications nudge appears only for restored shows that need it`() {
        val backup = OpmlImportState.Success(4, 0, isJson = true, hasNotificationsEnabled = true)
        assertTrue(showsImportNotificationPrompt(backup, notificationsAllowed = false))
        assertFalse(showsImportNotificationPrompt(backup, notificationsAllowed = true))
        assertFalse(showsImportNotificationPrompt(backup.copy(hasNotificationsEnabled = false), notificationsAllowed = false))
        assertFalse(showsImportNotificationPrompt(backup.copy(isJson = false), notificationsAllowed = false))
    }
}
