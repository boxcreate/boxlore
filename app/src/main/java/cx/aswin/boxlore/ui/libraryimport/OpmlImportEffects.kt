package cx.aswin.boxlore.ui.libraryimport

import android.content.Context
import android.net.Uri
import android.util.Log
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.backup.LibraryBackupManager
import cx.aswin.boxlore.core.catalog.backup.OpmlImportReport
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs the OPML work [opmlImportState] asks for: parsing and subscribing for [OpmlImportState.Parsing],
 * marking shows played for [OpmlImportState.Completing]. [LibraryImportViewModel] calls it, so the
 * work outlives the activity being recreated.
 */
internal suspend fun runOpmlImportStep(
    opmlImportState: OpmlImportState,
    onStateChange: (OpmlImportState) -> Unit,
    context: Context,
    subscriptionRepository: SubscriptionRepository,
    playbackRepository: PlaybackRepository,
    podcastRepository: PodcastRepository,
) {
    when (val state = opmlImportState) {
        is OpmlImportState.Parsing -> parseAndSubscribe(state, onStateChange, context, subscriptionRepository, playbackRepository, podcastRepository)
        is OpmlImportState.Completing -> markShowsPlayed(state, onStateChange, context, subscriptionRepository, playbackRepository, podcastRepository)
        else -> Unit
    }
}

/** Parses the outline in [state], subscribes to its shows, then asks which to mark played. */
private suspend fun parseAndSubscribe(
    state: OpmlImportState.Parsing,
    onStateChange: (OpmlImportState) -> Unit,
    context: Context,
    subscriptionRepository: SubscriptionRepository,
    playbackRepository: PlaybackRepository,
    podcastRepository: PodcastRepository,
) {
    val uri = state.uri
    withContext(Dispatchers.IO) {
        var parsed = false
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                AnalyticsHelper.trackOnboardingImportFailed(
                    "opml",
                    LibraryBackupAnalyticsErrors.FILE_OPEN_FAILED,
                )
                onStateChange(OpmlImportState.Error(ImportFailure.FileUnreadable))
                return@withContext
            }
            val backupManager =
                LibraryBackupManager(
                    subscriptionRepository,
                    playbackRepository,
                    podcastRepository,
                    context = context,
                )
            val feeds = backupManager.parseOpmlFeeds(inputStream)
            inputStream.close()
            parsed = true

            if (feeds.isEmpty()) {
                AnalyticsHelper.trackOnboardingImportFailed(
                    "opml",
                    LibraryBackupAnalyticsErrors.NO_FEEDS,
                )
                onStateChange(
                    OpmlImportState.Error(ImportFailure.NoFeeds),
                )
                return@withContext
            }

            onStateChange(
                OpmlImportState.Importing(
                    currentFeedTitle = feeds.first().title,
                    progress = 0f,
                    currentCount = 0,
                    totalCount = feeds.size,
                    importedPodcasts = emptyList(),
                ),
            )

            // Paced under the API's rate limits; outlines it still couldn't answer for are finished later.
            val report =
                backupManager.importOpmlFeeds(feeds) { index, feed, imported ->
                    onStateChange(
                        OpmlImportState.Importing(
                            currentFeedTitle = feed.title,
                            progress = index.toFloat() / feeds.size,
                            currentCount = index,
                            totalCount = feeds.size,
                            importedPodcasts = imported,
                        ),
                    )
                }
            trackOpmlImportOutcome(report)
            onStateChange(opmlImportOutcome(report))
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("OPML_IMPORT", "Error during parsing/importing", e)
            val code =
                LibraryBackupAnalyticsErrors.fromThrowable(
                    e,
                    LibraryBackupAnalyticsErrors.IMPORT_FAILED,
                )
            AnalyticsHelper.trackOnboardingImportFailed("opml", code)
            AnalyticsHelper.trackBackupRestoreResult(
                action = "import",
                success = false,
                format = "opml",
                errorMessage = code,
            )
            onStateChange(OpmlImportState.Error(opmlFailureFor(parsed)))
        }
    }
}

/** Marks the shows picked in [state] played, one by one. */
private suspend fun markShowsPlayed(
    state: OpmlImportState.Completing,
    onStateChange: (OpmlImportState) -> Unit,
    context: Context,
    subscriptionRepository: SubscriptionRepository,
    playbackRepository: PlaybackRepository,
    podcastRepository: PodcastRepository,
) {
    withContext(Dispatchers.IO) {
        try {
            val backupManager =
                LibraryBackupManager(
                    subscriptionRepository,
                    playbackRepository,
                    podcastRepository,
                    context = context,
                )
            val total = state.podcastsToMark.size
            // Only shows read in full count as marked: one whose pages ran out is left partly marked.
            var fullyMarked = 0
            for (index in state.podcastsToMark.indices) {
                val podcast = state.podcastsToMark[index]
                onStateChange(
                    OpmlImportState.Completing(
                        progress = index.toFloat() / total,
                        currentShowTitle = podcast.title,
                        podcastsToMark = state.podcastsToMark,
                        totalImportedCount = state.totalImportedCount,
                        importedPodcasts = state.importedPodcasts,
                        pendingCount = state.pendingCount,
                    ),
                )
                if (backupManager.markAllEpisodesCompleted(podcast)) fullyMarked++
            }
            onStateChange(
                OpmlImportState.Success(
                    importedCount = state.totalImportedCount,
                    completedCount = fullyMarked,
                    isJson = false,
                    importedPodcasts = state.importedPodcasts,
                    pendingCount = state.pendingCount,
                ),
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("OPML_IMPORT", "Error marking completed", e)
            AnalyticsHelper.trackOnboardingImportFailed(
                "opml",
                LibraryBackupAnalyticsErrors.MARK_COMPLETED_FAILED,
            )
            onStateChange(OpmlImportState.Error(ImportFailure.MarkingStopped))
        }
    }
}

/**
 * An OPML import that threw. Until the file is parsed nothing is subscribed, so the library is as it
 * was; after that some shows may already be in.
 */
internal fun opmlFailureFor(parsed: Boolean): ImportFailure =
    if (parsed) ImportFailure.ImportStopped else ImportFailure.FileUnreadable

/** Import a JSON library backup on a background dispatcher. */
suspend fun performJsonLibraryImport(
    uri: Uri,
    context: Context,
    subscriptionRepository: SubscriptionRepository,
    playbackRepository: PlaybackRepository,
    podcastRepository: PodcastRepository,
    userPrefs: UserPreferencesRepository,
    onStateChange: (OpmlImportState) -> Unit,
) {
    onStateChange(OpmlImportState.ImportingJson())
    withContext(Dispatchers.IO) {
        try {
            val jsonStr =
                context.contentResolver.openInputStream(uri)?.use {
                    it.bufferedReader().readText()
                }
            if (jsonStr == null) {
                AnalyticsHelper.trackOnboardingImportFailed(
                    "json",
                    LibraryBackupAnalyticsErrors.FILE_OPEN_FAILED,
                )
                withContext(Dispatchers.Main) {
                    onStateChange(OpmlImportState.Error(ImportFailure.FileUnreadable))
                }
                return@withContext
            }
            val (count, hasNotificationsEnabled) =
                LibraryBackupManager(
                    subscriptionRepository,
                    playbackRepository,
                    podcastRepository,
                    userPrefs,
                    context,
                ).importLibraryFromJson(jsonStr) { progress ->
                    withContext(Dispatchers.Main) {
                        onStateChange(
                            OpmlImportState.ImportingJson(
                                currentTitle = progress.currentTitle,
                                progress = progress.progressRatio,
                                currentCount = progress.current,
                                totalCount = progress.total,
                                phase = progress.phase,
                            ),
                        )
                    }
                }
            val outcome = jsonImportOutcome(count, hasNotificationsEnabled)
            if (outcome is OpmlImportState.Error) {
                AnalyticsHelper.trackOnboardingImportFailed("json", LibraryBackupAnalyticsErrors.IMPORT_FAILED)
            }
            AnalyticsHelper.trackBackupRestoreResult(
                action = "import",
                success = outcome is OpmlImportState.Success,
                itemCount = count.coerceAtLeast(0),
                format = "json",
                errorMessage = if (outcome is OpmlImportState.Error) LibraryBackupAnalyticsErrors.IMPORT_FAILED else null,
            )
            withContext(Dispatchers.Main) { onStateChange(outcome) }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val code =
                LibraryBackupAnalyticsErrors.fromThrowable(
                    e,
                    LibraryBackupAnalyticsErrors.IMPORT_FAILED,
                )
            AnalyticsHelper.trackOnboardingImportFailed("json", code)
            AnalyticsHelper.trackBackupRestoreResult(
                action = "import",
                success = false,
                format = "json",
                errorMessage = code,
            )
            withContext(Dispatchers.Main) {
                onStateChange(OpmlImportState.Error(ImportFailure.BackupFailed))
            }
        }
    }
}

/** The screen after an OPML import: the shows it followed to mark played, still adding (only held back by the rate limit), or nothing reachable. */
private fun opmlImportOutcome(report: OpmlImportReport): OpmlImportState = when {
    report.imported.isNotEmpty() ->
        OpmlImportState.AskCompleted(
            importedPodcasts = report.imported,
            selectedIds = report.imported.map { it.id }.toSet(),
            pendingCount = report.pending.size,
        )
    // Nothing yet, but the rate limit only held them back: the catch-up adds them while boxlore is open.
    report.pending.isNotEmpty() ->
        OpmlImportState.Success(
            importedCount = 0,
            completedCount = 0,
            isJson = false,
            importedPodcasts = emptyList(),
            pendingCount = report.pending.size,
        )
    else -> OpmlImportState.Error(ImportFailure.NothingReachable)
}

private fun trackOpmlImportOutcome(report: OpmlImportReport) {
    when {
        report.imported.isNotEmpty() ->
            AnalyticsHelper.trackBackupRestoreResult(
                action = "import",
                success = true,
                itemCount = report.imported.size,
                format = "opml",
            )
        report.pending.isEmpty() -> {
            AnalyticsHelper.trackOnboardingImportFailed("opml", LibraryBackupAnalyticsErrors.NO_PODCASTS_RESOLVED)
            AnalyticsHelper.trackBackupRestoreResult(
                action = "import",
                success = false,
                format = "opml",
                errorMessage = LibraryBackupAnalyticsErrors.NO_PODCASTS_RESOLVED,
            )
        }
    }
}
