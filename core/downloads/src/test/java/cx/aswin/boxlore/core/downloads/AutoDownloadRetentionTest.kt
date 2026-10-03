package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AutoDownloadRetentionTest {
    @Test fun retentionProtectsManualLegacyAndActiveDownloads() {
        val rows = listOf(
            row("manual", "manual", 1),
            row("legacy", "unknown", 2),
            row("auto-old", "auto", 3),
            row("auto-new", "auto", 4),
            row("active", "auto", 0).copy(status = DownloadedEpisodeEntity.STATUS_DOWNLOADING)
        )
        assertEquals(listOf("auto-old"), AutoDownloadRetention.excess(rows, 1).map { it.episodeId })
        assertTrue(AutoDownloadRetention.excess(rows, 0).isEmpty())
    }

    @Test fun progressingDownloadCanRunLongerThanOld150SecondLimit() {
        val progress = AutoDownloadProgress(0, idleLimitMs = 90_000)
        for (second in 0..300 step 30) assertFalse(progress.stalled(second * 1000L, second.toLong()))
        assertTrue(progress.stalled(390_000, 300))
    }

    private fun row(id: String, origin: String, date: Long) = DownloadedEpisodeEntity(
        episodeId = id, podcastId = "show", episodeTitle = id, episodeDescription = null,
        episodeImageUrl = null, podcastName = "Show", podcastImageUrl = null, durationMs = 0,
        publishedDate = date, localFilePath = "CACHED", downloadId = 0, downloadedAt = date,
        sizeBytes = 100, status = DownloadedEpisodeEntity.STATUS_COMPLETED, downloadOrigin = origin,
    )
}
