package cx.aswin.boxlore.feature.library.downloads

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** A failed download stays listed in Downloads but is never counted, sized or played as one on the phone. */
class DownloadTotalsTest {
    private fun row(id: String, status: Int, sizeBytes: Long = 1_048_576L, origin: String = DownloadedEpisodeEntity.ORIGIN_MANUAL) =
        DownloadedEpisodeEntity(
            episodeId = id,
            podcastId = "pod-1",
            episodeTitle = "Episode $id",
            episodeDescription = null,
            episodeImageUrl = null,
            podcastName = "Podcast",
            podcastImageUrl = null,
            durationMs = 60_000L,
            publishedDate = 0L,
            localFilePath = "",
            downloadId = 0L,
            downloadedAt = 0L,
            sizeBytes = sizeBytes,
            status = status,
            downloadOrigin = origin,
        )

    @Test
    fun failedDownloadsAreLeftOutOfPlayAllAndTheTotals() {
        // Reported: 6 of 8 downloads finished, Downloads said 8, and Play all queued the 2 that failed.
        val rows = (1..6).map { row("ok-$it", DownloadedEpisodeEntity.STATUS_COMPLETED) } +
            row("running", DownloadedEpisodeEntity.STATUS_DOWNLOADING) +
            row("failed-1", DownloadedEpisodeEntity.STATUS_FAILED, sizeBytes = 50_000_000L) +
            row("failed-auto", DownloadedEpisodeEntity.STATUS_FAILED, origin = DownloadedEpisodeEntity.ORIGIN_AUTO)

        assertEquals((1..6).map { "ok-$it" } + "running", rows.playableDownloads().map { it.episodeId })
        assertEquals(DownloadSummary(episodeCount = 7, sizeBytes = 7 * 1_048_576L, failedCount = 2), rows.downloadSummary())
        assertEquals("7 episodes • 7.0 MB used • 2 couldn’t download", rows.downloadSummary().countLine(sizeSuffix = " used"))
    }

    @Test
    fun countLineReadsAsBeforeWithoutFailuresAndShowsOnlyFailuresWhenNothingElseIsHere() {
        assertEquals("1 episode • 1.0 MB", listOf(row("ok", DownloadedEpisodeEntity.STATUS_COMPLETED)).downloadSummary().countLine())
        assertEquals("1 couldn’t download", listOf(row("failed", DownloadedEpisodeEntity.STATUS_FAILED)).downloadSummary().countLine(" saved"))
        assertEquals(emptyList<DownloadedEpisodeEntity>(), listOf(row("failed", DownloadedEpisodeEntity.STATUS_FAILED)).playableDownloads())
    }
}
