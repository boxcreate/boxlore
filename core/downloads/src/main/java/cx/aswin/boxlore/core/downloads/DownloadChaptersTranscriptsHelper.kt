package cx.aswin.boxlore.core.downloads

import android.content.Context
import android.util.Log
import cx.aswin.boxlore.core.catalog.ChapterOfflineStorage
import cx.aswin.boxlore.core.catalog.ChapterRepository
import cx.aswin.boxlore.core.catalog.TranscriptOfflineStorage
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import java.io.File
import java.net.URI

internal object DownloadChaptersTranscriptsHelper {

    private const val TAG = "DownloadChaptersHelper"

    fun downloadTextUrl(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return try {
            if (url.startsWith("/") || url.startsWith("file:")) {
                val cleanPath = url.removePrefix("file://").removePrefix("file:")
                val file = File(cleanPath)
                if (file.exists()) file.readText() else null
            } else {
                val cleanUrl = if (url.startsWith("//")) "https:$url" else url
                URI.create(cleanUrl).toURL().readText()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download text from $url", e)
            null
        }
    }

    suspend fun persistOfflineChaptersAndTranscripts(
        context: Context,
        database: BoxLoreDatabase,
        episode: Episode,
    ) {
        var localChaptersPath: String? = null
        var localTranscriptPath: String? = null

        // 1. Chapters: download remote chapters JSON or parse from description
        val chaptersUrl = episode.chaptersUrl
        if (!chaptersUrl.isNullOrBlank()) {
            val json = downloadTextUrl(chaptersUrl)
            if (!json.isNullOrBlank()) {
                localChaptersPath = ChapterOfflineStorage.saveOfflineChapters(context, episode.id, json)
            }
        } else {
            val descriptionChapters = ChapterRepository.parseChaptersFromDescription(episode.description)
            if (descriptionChapters.isNotEmpty()) {
                val json = ChapterRepository.chaptersToJson(descriptionChapters)
                localChaptersPath = ChapterOfflineStorage.saveOfflineChapters(context, episode.id, json)
            }
        }

        // 2. Transcripts: download remote transcript text (SRT/VTT)
        val transcriptUrl = episode.transcriptUrl
            ?: episode.transcripts?.firstOrNull()?.url
        if (!transcriptUrl.isNullOrBlank()) {
            val content = downloadTextUrl(transcriptUrl)
            if (!content.isNullOrBlank()) {
                localTranscriptPath = TranscriptOfflineStorage.saveOfflineTranscript(context, episode.id, content)
            }
        }

        // 3. Update database row if either path was created
        if (localChaptersPath != null || localTranscriptPath != null) {
            try {
                database.downloadedEpisodeDao().updateOfflineText(episode.id, localChaptersPath, localTranscriptPath)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update downloaded episode with chapters/transcript paths for ${episode.id}", e)
            }
        }
    }

    fun cleanupChaptersAndTranscripts(context: Context, episodeId: String) {
        ChapterOfflineStorage.deleteOfflineChapters(context, episodeId)
        TranscriptOfflineStorage.deleteOfflineTranscript(context, episodeId)
    }

    fun deleteLocalFileIfValid(path: String?) {
        if (path.isNullOrBlank()) return
        val prefix = "file://"
        if (path.startsWith("/") || path.startsWith(prefix)) {
            val cleanPath = path.removePrefix(prefix)
            try {
                val file = File(cleanPath)
                if (file.exists()) {
                    file.delete()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to delete file: $cleanPath", e)
            }
        }
    }

    suspend fun insertOptimisticDownload(
        context: Context,
        database: BoxLoreDatabase,
        rankingFeedbackRepository: RankingFeedbackRepository,
        episode: Episode,
        podcast: Podcast,
        isSmartDownloaded: Boolean,
        onPrepared: () -> Unit = {},
    ) {
        val existing = try {
            database.downloadedEpisodeDao().getDownload(episode.id)
        } catch (e: Exception) {
            null
        }
        val effectiveIsSmart = isSmartDownloaded && (existing == null || existing.isSmartDownloaded)

        if (existing?.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
            handleAlreadyCompletedOptimistic(database, rankingFeedbackRepository, existing, episode, podcast, effectiveIsSmart)
            onPrepared()
            return
        }

        val epArt = resolveArtworkUrl(episode.imageUrl, podcast.imageUrl)
        val podArt = resolveArtworkUrl(podcast.imageUrl, episode.imageUrl)
        val entity = buildImmediateOptimisticEntity(episode, podcast, existing, effectiveIsSmart, epArt, podArt)

        try {
            database.downloadedEpisodeDao().insert(entity)
        } catch (e: Exception) {
            Log.e(TAG, "Optimistic insert failed for ${episode.id}", e)
        }

        onPrepared()
        if (!effectiveIsSmart) {
            recordDownloadRankingFeedback(rankingFeedbackRepository, episode, podcast)
        }

        fetchAndPersistArtwork(context, database, episode.id, podcast.id, epArt, podArt)
        persistOfflineChaptersAndTranscripts(context, database, episode)
    }

    private suspend fun handleAlreadyCompletedOptimistic(
        database: BoxLoreDatabase,
        rankingFeedbackRepository: RankingFeedbackRepository,
        existing: DownloadedEpisodeEntity,
        episode: Episode,
        podcast: Podcast,
        effectiveIsSmartDownloaded: Boolean,
    ) {
        if (!effectiveIsSmartDownloaded) {
            database.downloadedEpisodeDao().promoteToManual(existing.episodeId)
        }
        if (!effectiveIsSmartDownloaded) {
            recordDownloadRankingFeedback(rankingFeedbackRepository, episode, podcast)
        }
    }

    private fun buildImmediateOptimisticEntity(
        episode: Episode,
        podcast: Podcast,
        existing: DownloadedEpisodeEntity?,
        effectiveIsSmartDownloaded: Boolean,
        episodeArtSource: String?,
        podcastArtSource: String?,
    ): DownloadedEpisodeEntity = DownloadedEpisodeEntity(
        episodeId = episode.id,
        podcastId = podcast.id,
        episodeTitle = episode.title,
        episodeDescription = episode.description,
        episodeImageUrl = existing?.episodeImageUrl ?: episodeArtSource,
        podcastName = podcast.title,
        podcastImageUrl = existing?.podcastImageUrl ?: podcastArtSource,
        durationMs = episode.duration * 1000L,
        publishedDate = episode.publishedDate,
        localFilePath = existing?.localFilePath ?: "",
        downloadId = existing?.downloadId ?: 0,
        downloadedAt = existing?.downloadedAt ?: System.currentTimeMillis(),
        sizeBytes = existing?.sizeBytes ?: 0,
        status = DownloadedEpisodeEntity.STATUS_DOWNLOADING,
        isSmartDownloaded = effectiveIsSmartDownloaded,
        downloadOrigin = if (effectiveIsSmartDownloaded) DownloadedEpisodeEntity.ORIGIN_SMART else DownloadedEpisodeEntity.ORIGIN_MANUAL,
        chaptersUrl = existing?.chaptersUrl ?: episode.chaptersUrl,
        transcriptUrl = existing?.transcriptUrl ?: (episode.transcriptUrl ?: episode.transcripts?.firstOrNull()?.url),
    )

    suspend fun enrichAutoDownload(context: Context, database: BoxLoreDatabase, episode: Episode, podcast: Podcast) {
        fetchAndPersistArtwork(
            context,
            database,
            episode.id,
            podcast.id,
            resolveArtworkUrl(episode.imageUrl, podcast.imageUrl),
            resolveArtworkUrl(podcast.imageUrl, episode.imageUrl)
        )
        persistOfflineChaptersAndTranscripts(context, database, episode)
    }

    private suspend fun fetchAndPersistArtwork(
        context: Context,
        database: BoxLoreDatabase,
        episodeId: String,
        podcastId: String,
        episodeArtSource: String?,
        podcastArtSource: String?,
    ) {
        val localEp = downloadArtworkLocally(context, episodeArtSource, "downloaded_artworks", "episode_$episodeId.png")
        val localPod = downloadArtworkLocally(context, podcastArtSource, "downloaded_artworks", "podcast_$podcastId.png")
        if (localEp == null && localPod == null) return

        try {
            database.downloadedEpisodeDao().updateArtwork(episodeId, localEp, localPod)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update artwork paths for $episodeId", e)
        }
    }

    private fun downloadArtworkLocally(
        context: Context,
        imageUrl: String?,
        subDir: String,
        fileName: String,
    ): String? {
        if (imageUrl.isNullOrBlank()) return null
        return try {
            val cleanUrlStr = if (imageUrl.startsWith("//")) "https:$imageUrl" else imageUrl
            val url = URI.create(cleanUrlStr).toURL()
            val dir = File(context.filesDir, subDir).apply { mkdirs() }
            val file = File(dir, fileName)
            url.openStream().use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download artwork: $imageUrl", e)
            null
        }
    }

    private fun resolveArtworkUrl(primary: String?, fallback: String?): String? =
        DownloadArtworkUrls.remoteUrl(primary) ?: DownloadArtworkUrls.remoteUrl(fallback)

    private suspend fun recordDownloadRankingFeedback(
        rankingFeedbackRepository: RankingFeedbackRepository,
        episode: Episode,
        podcast: Podcast,
    ) {
        rankingFeedbackRepository.recordAction(
            target = FeedbackTarget(
                episodeId = episode.id,
                podcastId = podcast.id,
                genre = episode.podcastGenre ?: podcast.genre,
            ),
            action = RankingAction.MANUAL_DOWNLOAD,
        )
    }
}
