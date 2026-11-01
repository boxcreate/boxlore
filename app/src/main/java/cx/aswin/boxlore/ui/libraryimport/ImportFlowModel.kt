package cx.aswin.boxlore.ui.libraryimport

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cx.aswin.boxlore.core.catalog.backup.JsonBackupPhase
import kotlin.math.roundToInt

/**
 * Why an import stopped. [libraryUnchanged] is true only when nothing was written before the failure,
 * so the error screen can promise that the library is as it was. [retry] picks the way back to the
 * import options, and [fileHint] adds which files work.
 */
enum class ImportFailure(val libraryUnchanged: Boolean, val retry: ImportRetry, val fileHint: Boolean = false) {
    /** The picked file could not be opened or parsed. */
    FileUnreadable(libraryUnchanged = true, retry = ImportRetry.AnotherFile, fileHint = true),

    /** The OPML file lists no podcast feeds. */
    NoFeeds(libraryUnchanged = true, retry = ImportRetry.AnotherFile, fileHint = true),

    /**
     * None of the OPML feeds came back as imported. A feed can be subscribed before its episode refresh
     * fails, so this does not promise an unchanged library.
     */
    NothingReachable(libraryUnchanged = false, retry = ImportRetry.TryAgain),

    /** The OPML import failed partway; shows subscribed before that stay. */
    ImportStopped(libraryUnchanged = false, retry = ImportRetry.TryAgain),

    /** Shows are subscribed, but marking old episodes played failed partway. */
    MarkingStopped(libraryUnchanged = false, retry = ImportRetry.None),

    /** A JSON backup failed to restore; settings or shows may already be partly restored. */
    BackupFailed(libraryUnchanged = false, retry = ImportRetry.AnotherFile),
}

/** The action that takes the listener back to the import options after a failure. */
enum class ImportRetry { AnotherFile, TryAgain, None }

/** Which import screen a state shows. Working and done share one screen so Echo carries through. */
internal enum class ImportScreen { Pick, Progress, Ask, Failed }

internal fun importScreenFor(state: OpmlImportState): ImportScreen? = when (state) {
    OpmlImportState.Idle -> null
    OpmlImportState.ShowSelector -> ImportScreen.Pick
    is OpmlImportState.Parsing,
    is OpmlImportState.ActiveImportState,
    is OpmlImportState.Completing,
    is OpmlImportState.Success,
    -> ImportScreen.Progress
    is OpmlImportState.AskCompleted -> ImportScreen.Ask
    is OpmlImportState.Error -> ImportScreen.Failed
}

internal fun contentKeyFor(state: OpmlImportState): String = when (state) {
    OpmlImportState.Idle -> "idle"
    OpmlImportState.ShowSelector -> "selector"
    is OpmlImportState.ImportingJson -> "importing_json"
    is OpmlImportState.Parsing -> "parsing"
    is OpmlImportState.Importing -> "importing"
    is OpmlImportState.AskCompleted -> "ask"
    is OpmlImportState.Completing -> "completing"
    is OpmlImportState.Success -> "success"
    is OpmlImportState.Error -> "error"
}

/** What Echo does on each import screen; the Start fresh list has no Echo. */
internal enum class ImportEchoMoment(val sequence: String) {
    Pick("import-pick"),
    Working("import-working"),
    Done("import-done"),
    Failed("import-failed"),
}

internal fun importEchoMomentFor(state: OpmlImportState): ImportEchoMoment? = when (importScreenFor(state)) {
    ImportScreen.Pick -> ImportEchoMoment.Pick
    ImportScreen.Progress -> if (state is OpmlImportState.Success) ImportEchoMoment.Done else ImportEchoMoment.Working
    ImportScreen.Failed -> ImportEchoMoment.Failed
    ImportScreen.Ask, null -> null
}

/** Echo's size on an import screen: about a quarter of the height, within readable bounds. */
internal fun importEchoSize(availableHeight: Dp): Dp =
    (availableHeight * ImportEchoHeightShare).coerceIn(ImportEchoMinSize, ImportEchoMaxSize)

internal const val ImportEchoHeightShare = 0.26f
internal val ImportEchoMinSize = 96.dp
internal val ImportEchoMaxSize = 196.dp

/** Show [current] of [total] (the one being worked on), and how far along the bar is. */
internal data class ImportProgress(val current: Int, val total: Int, val fraction: Float)

internal fun importProgressFor(state: OpmlImportState): ImportProgress? = when (state) {
    is OpmlImportState.Importing -> countedProgress(state.currentCount, state.totalCount, state.progress)
    is OpmlImportState.ImportingJson -> countedProgress(state.currentCount, state.totalCount, state.progress)
    is OpmlImportState.Completing -> {
        val total = state.podcastsToMark.size
        val fraction = state.progress.coerceIn(0f, 1f)
        countedProgress((fraction * total).roundToInt(), total, fraction)
    }
    else -> null
}

private fun countedProgress(index: Int, total: Int, progress: Float): ImportProgress? =
    if (total <= 0) null else ImportProgress((index + 1).coerceIn(1, total), total, progress.coerceIn(0f, 1f))

internal enum class ImportStepKind { ReadFile, Subscribe, ChooseMarkPlayed, MarkPlayed, RestoreBackup, LatestEpisodes }

internal enum class ImportStepStatus { Done, Active, Upcoming }

/** One line of the import checklist. [count] is shown on finished steps that found or added shows. */
internal data class ImportStep(val kind: ImportStepKind, val status: ImportStepStatus, val count: Int? = null)

/**
 * The checklist under the progress: an OPML file is read, its shows subscribed, then the listener picks
 * what to mark played. A backup restores shows and history, then fetches the latest episodes (the only
 * stages the restore reports).
 */
internal fun importStepsFor(state: OpmlImportState): List<ImportStep> = when (state) {
    is OpmlImportState.Parsing -> listOf(
        ImportStep(ImportStepKind.ReadFile, ImportStepStatus.Active),
        ImportStep(ImportStepKind.Subscribe, ImportStepStatus.Upcoming),
        ImportStep(ImportStepKind.ChooseMarkPlayed, ImportStepStatus.Upcoming),
    )
    is OpmlImportState.Importing -> listOf(
        ImportStep(ImportStepKind.ReadFile, ImportStepStatus.Done, state.totalCount),
        ImportStep(ImportStepKind.Subscribe, ImportStepStatus.Active),
        ImportStep(ImportStepKind.ChooseMarkPlayed, ImportStepStatus.Upcoming),
    )
    is OpmlImportState.Completing -> listOf(
        ImportStep(ImportStepKind.ReadFile, ImportStepStatus.Done),
        ImportStep(ImportStepKind.Subscribe, ImportStepStatus.Done, state.totalImportedCount),
        ImportStep(ImportStepKind.MarkPlayed, ImportStepStatus.Active),
    )
    is OpmlImportState.ImportingJson -> when (state.phase) {
        JsonBackupPhase.REFRESHING_FEEDS -> listOf(
            ImportStep(ImportStepKind.RestoreBackup, ImportStepStatus.Done),
            ImportStep(ImportStepKind.LatestEpisodes, ImportStepStatus.Active),
        )
        JsonBackupPhase.COMPLETED -> listOf(
            ImportStep(ImportStepKind.RestoreBackup, ImportStepStatus.Done),
            ImportStep(ImportStepKind.LatestEpisodes, ImportStepStatus.Done),
        )
        else -> listOf(
            ImportStep(ImportStepKind.RestoreBackup, ImportStepStatus.Active),
            ImportStep(ImportStepKind.LatestEpisodes, ImportStepStatus.Upcoming),
        )
    }
    else -> emptyList()
}

/**
 * The result of restoring a JSON backup. The backup manager reports a failed restore as a negative
 * count instead of throwing, which must not read as a finished import.
 */
internal fun jsonImportOutcome(count: Int, hasNotificationsEnabled: Boolean): OpmlImportState =
    if (count < 0) {
        OpmlImportState.Error(ImportFailure.BackupFailed)
    } else {
        OpmlImportState.Success(importedCount = count, completedCount = 0, isJson = true, hasNotificationsEnabled = hasNotificationsEnabled)
    }

/** The notifications nudge after a backup restore: only when restored shows use alerts or downloads and notifications are off. */
internal fun showsImportNotificationPrompt(state: OpmlImportState.Success, notificationsAllowed: Boolean): Boolean =
    state.isJson && state.hasNotificationsEnabled && !notificationsAllowed

/** Where an import is, for the review prompt's quiet days: null when none is under way or done. */
internal fun libraryImportPhase(state: OpmlImportState): String? = when (state) {
    is OpmlImportState.Parsing, is OpmlImportState.ActiveImportState, is OpmlImportState.Completing -> "running"
    is OpmlImportState.Success -> "done"
    else -> null
}
