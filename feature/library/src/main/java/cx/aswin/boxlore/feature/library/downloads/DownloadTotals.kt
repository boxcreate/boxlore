package cx.aswin.boxlore.feature.library.downloads

import androidx.annotation.PluralsRes
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.feature.library.R
import java.util.Locale

/** Rows Downloads counts and plays: every row except a download that failed, which has no audio on the phone. */
internal fun List<DownloadedEpisodeEntity>.playableDownloads(): List<DownloadedEpisodeEntity> =
    filter { it.status != DownloadedEpisodeEntity.STATUS_FAILED }

/** What a Downloads header or show card counts: episodes on the phone, their size, and failed downloads. */
internal data class DownloadSummary(val episodeCount: Int, val sizeBytes: Long, val failedCount: Int)

internal fun List<DownloadedEpisodeEntity>.downloadSummary(): DownloadSummary {
    val playable = playableDownloads()
    return DownloadSummary(
        episodeCount = playable.size,
        sizeBytes = playable.sumOf { it.sizeBytes },
        failedCount = size - playable.size,
    )
}

/** How the count line names the size: plain, "used" (all Downloads) or "saved" (one show). */
internal enum class DownloadSizeWording(@PluralsRes val plural: Int) {
    Plain(R.plurals.library_downloads_count_line),
    Used(R.plurals.library_downloads_count_line_used),
    Saved(R.plurals.library_downloads_count_line_saved),
}

/**
 * The count line, such as "6 episodes • 420.0 MB used • 2 couldn’t download"; [wording] says how the
 * size reads. Only the failed part shows when nothing else is on the phone.
 */
internal fun DownloadSummary.countLine(wording: DownloadSizeWording = DownloadSizeWording.Plain, locale: Locale): UiText {
    val failed = if (failedCount > 0) UiText.plural(R.plurals.library_downloads_failed_count, failedCount, failedCount) else null
    if (episodeCount == 0 && failed != null) return failed
    val episodes = UiText.plural(wording.plural, episodeCount, episodeCount, formatSize(sizeBytes, locale))
    return if (failed == null) episodes else UiText.res(R.string.library_downloads_count_and_failed, episodes, failed)
}
