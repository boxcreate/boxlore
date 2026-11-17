package cx.aswin.boxlore.feature.library.downloads

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity

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

/**
 * The count line, such as "6 episodes • 420.0 MB used • 2 couldn’t download"; [sizeSuffix] follows
 * the size (" used", " saved"). Only the failed part shows when nothing else is on the phone.
 */
internal fun DownloadSummary.countLine(sizeSuffix: String = ""): String {
    val failed = if (failedCount > 0) "$failedCount couldn’t download" else ""
    if (episodeCount == 0 && failed.isNotEmpty()) return failed
    val episodes = "$episodeCount ${if (episodeCount == 1) "episode" else "episodes"} • ${formatSize(sizeBytes)}$sizeSuffix"
    return if (failed.isEmpty()) episodes else "$episodes • $failed"
}
