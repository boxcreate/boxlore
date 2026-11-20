package cx.aswin.boxlore.feature.library.downloads

import android.app.Application
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.text.UiText
import cx.aswin.boxlore.core.model.text.resolve
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * A failed download stays listed in Downloads but is never counted, sized or played as one on the phone.
 * Runs on Robolectric so the lines read from the module's English resources.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class DownloadTotalsTest {
    private fun UiText.en(): String = resolve(RuntimeEnvironment.getApplication())

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
        assertEquals("7 episodes • 7.0 MB used • 2 couldn’t download", rows.downloadSummary().countLine(DownloadSizeWording.Used, Locale.US).en())
    }

    @Test
    fun countLineReadsAsBeforeWithoutFailuresAndShowsOnlyFailuresWhenNothingElseIsHere() {
        assertEquals("1 episode • 1.0 MB", listOf(row("ok", DownloadedEpisodeEntity.STATUS_COMPLETED)).downloadSummary().countLine(locale = Locale.US).en())
        assertEquals("1 couldn’t download", listOf(row("failed", DownloadedEpisodeEntity.STATUS_FAILED)).downloadSummary().countLine(DownloadSizeWording.Saved, Locale.US).en())
        assertEquals(emptyList<DownloadedEpisodeEntity>(), listOf(row("failed", DownloadedEpisodeEntity.STATUS_FAILED)).playableDownloads())
    }

    @Test
    fun savedWordingNamesTheSizeAsSaved() {
        val rows = listOf(row("a", DownloadedEpisodeEntity.STATUS_COMPLETED), row("b", DownloadedEpisodeEntity.STATUS_COMPLETED))
        assertEquals("2 episodes • 2.0 MB saved", rows.downloadSummary().countLine(DownloadSizeWording.Saved, Locale.US).en())
    }

    @Test
    fun latestDownloadAgeIsShortAndRelative() {
        val nowMs = System.currentTimeMillis()
        val nowSeconds = nowMs / 1000
        assertEquals("Just now", formatRelativeDate(nowSeconds + 100, Locale.US, nowMs)!!.en())
        // ICU writes the short units ("2m ago" or "2 min. ago", by version); each names its amount and reads as past.
        listOf(120L to "2", 7_200L to "2", 172_800L to "2", 1_209_600L to "2", 5_184_000L to "2", 63_072_000L to "2").forEach { (ago, amount) ->
            val text = formatRelativeDate(nowSeconds - ago, Locale.US, nowMs)!!.en()
            assertTrue(text, text.startsWith(amount) && text.endsWith("ago"))
        }
        assertTrue(formatRelativeDate(nowSeconds - 172_800, Locale.GERMANY, nowMs)!!.en().startsWith("vor"))
    }
}
