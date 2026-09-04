package cx.aswin.boxlore.core.downloads

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast

data class CompletedDownloadItem(val episode: Episode, val podcast: Podcast, val downloadedAt: Long,)

internal object CompletedDownloadItems {
    fun from(rows: List<DownloadedEpisodeEntity>): List<CompletedDownloadItem> = rows
        .asSequence()
        .filter { it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
        .sortedWith(
            compareByDescending<DownloadedEpisodeEntity>(::releaseOrderKeyMillis)
                .thenByDescending(DownloadedEpisodeEntity::downloadedAt)
                .thenBy(DownloadedEpisodeEntity::episodeId),
        ).map { row -> row.toCompletedDownloadItem() }
        .toList()

    private fun releaseOrderKeyMillis(row: DownloadedEpisodeEntity): Long = row.publishedDate
        .takeIf { it > 0L }
        ?.let { seconds -> seconds * MILLIS_PER_SECOND }
        ?: row.downloadedAt

    private fun DownloadedEpisodeEntity.toCompletedDownloadItem(): CompletedDownloadItem {
        val podcast =
            Podcast(
                id = podcastId,
                title = podcastName,
                artist = "",
                imageUrl = podcastImageUrl.orEmpty(),
                fallbackImageUrl = podcastImageUrl,
            )
        val episode =
            Episode(
                id = episodeId,
                title = episodeTitle,
                description = episodeDescription.orEmpty(),
                audioUrl = localFilePath,
                imageUrl = episodeImageUrl,
                podcastImageUrl = podcastImageUrl,
                podcastTitle = podcastName,
                podcastId = podcastId,
                duration = (durationMs / MILLIS_PER_SECOND).toInt(),
                publishedDate = publishedDate,
            )
        return CompletedDownloadItem(
            episode = episode,
            podcast = podcast,
            downloadedAt = downloadedAt,
        )
    }

    private const val MILLIS_PER_SECOND = 1_000L
}
