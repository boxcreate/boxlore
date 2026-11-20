package cx.aswin.boxlore.feature.info

import androidx.compose.runtime.saveable.Saver
import kotlinx.coroutines.CancellationException

/** Saves selected episode IDs as a bundle-safe list. */
internal val EpisodeIdListSaver = Saver<List<String>, ArrayList<String>>(
    save = { ArrayList(it) },
    restore = { it },
)

/**
 * The selection window, in [sort], for a range selection or to look selected episodes up again
 * after the page is recreated, or the failure when it cannot be loaded: a range selection then
 * leaves the selection as it was and says why, and the restore keeps every ID and tries again.
 */
@Suppress("TooGenericExceptionCaught") // Any load failure is handled by the caller.
internal suspend fun PodcastInfoViewModel.selectionWindowResult(sort: EpisodeSort): Result<PodcastEpisodeSelectionWindow> = try {
    Result.success(loadEpisodeSelectionWindow(sort))
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (failure: Exception) {
    android.util.Log.w("PodcastInfoScreen", "Could not load the show's episodes for selection", failure)
    Result.failure(failure)
}
