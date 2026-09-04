package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CompletedDownloadItemsTest {
    @Test
    fun `keeps completed downloads from every provenance and drops active rows`() {
        val rows =
            listOf(
                row(id = "manual-or-auto", publishedDate = 100L, isSmartDownloaded = false),
                row(id = "smart", publishedDate = 90L, isSmartDownloaded = true),
                row(
                    id = "downloading",
                    publishedDate = 110L,
                    status = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                ),
            )

        val items = CompletedDownloadItems.from(rows)

        assertEquals(listOf("manual-or-auto", "smart"), items.map { it.episode.id })
    }

    @Test
    fun `orders by release time and uses download time when release is missing`() {
        val items =
            CompletedDownloadItems.from(
                listOf(
                    row(id = "older-release", publishedDate = 100L, downloadedAt = 500_000L),
                    row(id = "newer-release", publishedDate = 300L, downloadedAt = 100_000L),
                    row(id = "missing-release", publishedDate = 0L, downloadedAt = 200_000L),
                ),
            )

        assertEquals(
            listOf("newer-release", "missing-release", "older-release"),
            items.map { it.episode.id },
        )
    }

    @Test
    fun `download time and id make release ties deterministic`() {
        val items =
            CompletedDownloadItems.from(
                listOf(
                    row(id = "b", publishedDate = 100L, downloadedAt = 10L),
                    row(id = "a", publishedDate = 100L, downloadedAt = 10L),
                    row(id = "latest-download", publishedDate = 100L, downloadedAt = 20L),
                ),
            )

        assertEquals(
            listOf("latest-download", "a", "b"),
            items.map { it.episode.id },
        )
    }

    @Test
    fun `maps local playback and podcast metadata`() {
        val item =
            CompletedDownloadItems
                .from(
                    listOf(
                        row(
                            id = "ep",
                            podcastId = "pod",
                            publishedDate = 42L,
                            durationMs = 180_000L,
                        ),
                    ),
                ).single()

        assertEquals("/downloads/ep.mp3", item.episode.audioUrl)
        assertEquals(180, item.episode.duration)
        assertEquals("pod", item.episode.podcastId)
        assertEquals("Podcast pod", item.episode.podcastTitle)
        assertEquals("pod", item.podcast.id)
        assertEquals(item.podcast.imageUrl, item.episode.podcastImageUrl)
    }

    private fun row(
        id: String,
        podcastId: String = "pod-$id",
        publishedDate: Long,
        downloadedAt: Long = publishedDate * 1_000L,
        durationMs: Long = 60_000L,
        status: Int = DownloadedEpisodeEntity.STATUS_COMPLETED,
        isSmartDownloaded: Boolean = false,
    ): DownloadedEpisodeEntity = DownloadedEpisodeEntity(
        episodeId = id,
        podcastId = podcastId,
        episodeTitle = "Episode $id",
        episodeDescription = "Description $id",
        episodeImageUrl = "https://example.com/$id.jpg",
        podcastName = "Podcast $podcastId",
        podcastImageUrl = "https://example.com/$podcastId.jpg",
        durationMs = durationMs,
        publishedDate = publishedDate,
        localFilePath = "/downloads/$id.mp3",
        downloadId = id.hashCode().toLong(),
        downloadedAt = downloadedAt,
        sizeBytes = 1_024L,
        status = status,
        isSmartDownloaded = isSmartDownloaded,
    )
}
