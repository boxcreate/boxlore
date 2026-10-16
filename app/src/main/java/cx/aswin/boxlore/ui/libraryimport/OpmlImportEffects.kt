package cx.aswin.boxlore.ui.libraryimport

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.backup.LibraryBackupManager
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.PlaybackRepository
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Runs OPML / JSON library import work when [importTriggerKey] changes. */
@Composable
fun OpmlImportEffects(
    importTriggerKey: Long,
    opmlImportState: OpmlImportState,
    onStateChange: (OpmlImportState) -> Unit,
    context: Context,
    subscriptionRepository: SubscriptionRepository,
    playbackRepository: PlaybackRepository,
    podcastRepository: PodcastRepository,
) {
    LaunchedEffect(importTriggerKey) {
        if (importTriggerKey == 0L) return@LaunchedEffect
        val state = opmlImportState
        if (state is OpmlImportState.Parsing) {
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

                    val importedList = mutableListOf<Podcast>()
                    for (index in feeds.indices) {
                        val feed = feeds[index]
                        onStateChange(
                            OpmlImportState.Importing(
                                currentFeedTitle = feed.title,
                                progress = index.toFloat() / feeds.size,
                                currentCount = index,
                                totalCount = feeds.size,
                                importedPodcasts = importedList.toList(),
                            ),
                        )
                        val imported = backupManager.importSingleOpmlFeed(feed)
                        if (imported != null) importedList.add(imported)
                    }

                    if (importedList.isEmpty()) {
                        AnalyticsHelper.trackOnboardingImportFailed(
                            "opml",
                            LibraryBackupAnalyticsErrors.NO_PODCASTS_RESOLVED,
                        )
                        AnalyticsHelper.trackBackupRestoreResult(
                            action = "import",
                            success = false,
                            format = "opml",
                            errorMessage = LibraryBackupAnalyticsErrors.NO_PODCASTS_RESOLVED,
                        )
                        onStateChange(
                            OpmlImportState.Error(ImportFailure.NothingReachable),
                        )
                    } else {
                        AnalyticsHelper.trackBackupRestoreResult(
                            action = "import",
                            success = true,
                            itemCount = importedList.size,
                            format = "opml",
                        )
                        onStateChange(
                            OpmlImportState.AskCompleted(
                                importedPodcasts = importedList.toList(),
                                selectedIds = importedList.map { it.id }.toSet(),
                            ),
                        )
                    }
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
        } else if (state is OpmlImportState.Completing) {
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
                    for (index in state.podcastsToMark.indices) {
                        val podcast = state.podcastsToMark[index]
                        onStateChange(
                            OpmlImportState.Completing(
                                progress = index.toFloat() / total,
                                currentShowTitle = podcast.title,
                                podcastsToMark = state.podcastsToMark,
                                totalImportedCount = state.totalImportedCount,
                                importedPodcasts = state.importedPodcasts,
                            ),
                        )
                        backupManager.markAllEpisodesCompleted(podcast)
                    }
                    onStateChange(
                        OpmlImportState.Success(
                            importedCount = state.totalImportedCount,
                            completedCount = total,
                            isJson = false,
                            importedPodcasts = state.importedPodcasts,
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
