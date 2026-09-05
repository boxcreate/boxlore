package cx.aswin.boxlore.core.downloads

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SharedAppDependencies
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.toPodcast
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.downloads.DownloadsDependenciesHolder
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.flow.first

open class AutoDownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID) ?: run {
            Log.e("BoxLore_BackgroundTrace", "[Worker] Failed: KEY_EPISODE_ID missing")
            return Result.failure()
        }
        val podcastId = inputData.getString(KEY_PODCAST_ID) ?: run {
            Log.e("BoxLore_BackgroundTrace", "[Worker] Failed: KEY_PODCAST_ID missing")
            return Result.failure()
        }

        Log.i("BoxLore_BackgroundTrace", "[Worker] AutoDownloadWorker execution started for podcastId: $podcastId, episodeId: $episodeId")

        val deps = SharedAppDependenciesHolder.require()
        val database = deps.database

        // 1. Verify that auto-download is enabled for this podcast
        val podcastEntity = database.podcastDao().getPodcast(podcastId)
        if (!isPodcastEligibleForAutoDownload(podcastEntity, podcastId)) {
            return Result.success()
        }

        val downloadRepository = DownloadsDependenciesHolder.require().downloadRepository

        // 2. Check if the episode is already downloaded or active
        val existingDownload = downloadRepository.reconcileDownloadStatus(episodeId)
        val maxAllowed = deps.userPreferencesRepository.autoDownloadMaxEpisodesStream.first()
        val existingResult = handleExistingDownload(existingDownload, database, downloadRepository, podcastId, maxAllowed)
        if (existingResult != null) {
            return existingResult
        }

        return executeDownload(deps, database, downloadRepository, podcastEntity!!, podcastId, episodeId, maxAllowed)
    }

    private fun isPodcastEligibleForAutoDownload(podcastEntity: PodcastEntity?, podcastId: String): Boolean {
        if (podcastEntity == null) {
            Log.w("BoxLore_BackgroundTrace", "[Worker] Podcast $podcastId not found in local database. Skipping auto-download.")
            return false
        }
        if (podcastEntity.isRss) {
            Log.i("BoxLore_BackgroundTrace", "[Worker] RSS podcasts do not support release-triggered auto-downloads.")
            return false
        }

        Log.i(
            "BoxLore_BackgroundTrace",
            "[Worker] Local podcast check: autoDownloadEnabled=${podcastEntity.autoDownloadEnabled}, notificationsEnabled=${podcastEntity.notificationsEnabled}, title='${podcastEntity.title}'"
        )

        if (!podcastEntity.autoDownloadEnabled) {
            Log.w(
                "BoxLore_BackgroundTrace",
                "[Worker] Auto-download is NOT enabled for podcast '${podcastEntity.title}' ($podcastId). Skipping."
            )
            return false
        }
        return true
    }

    private suspend fun handleExistingDownload(
        existingDownload: DownloadedEpisodeEntity?,
        database: BoxLoreDatabase,
        downloadRepository: DownloadRepository,
        podcastId: String,
        maxAllowed: Int,
    ): Result? {
        if (existingDownload == null) return null
        if (existingDownload.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
            Log.i("BoxLore_BackgroundTrace", "[Worker] Episode ${existingDownload.episodeId} is already completed. Skipping download.")
            if (existingDownload.isSmartDownloaded) {
                Log.i("BoxLore_BackgroundTrace", "[Worker] Promoting smart-downloaded episode ${existingDownload.episodeId} to standard auto-download.")
                database.downloadedEpisodeDao().insert(existingDownload.copy(isSmartDownloaded = false))
                enforceMaxDownloadsQuota(database, downloadRepository, podcastId, maxAllowed)
            }
            return Result.success()
        }
        if (existingDownload.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING ||
            existingDownload.status == DownloadedEpisodeEntity.STATUS_QUEUED
        ) {
            Log.i("BoxLore_BackgroundTrace", "[Worker] Episode ${existingDownload.episodeId} is already downloading. Promoting and awaiting completion.")
            if (existingDownload.isSmartDownloaded) {
                database.downloadedEpisodeDao().insert(existingDownload.copy(isSmartDownloaded = false))
            }
            val completed = downloadRepository.awaitDownloadCompletion(existingDownload.episodeId)
            if (!completed) {
                Log.w("BoxLore_BackgroundTrace", "[Worker] In-progress download failed or timed out for episode: ${existingDownload.episodeId}")
                return Result.retry()
            }
            Log.i("BoxLore_BackgroundTrace", "[Worker] SUCCESS! Completed in-progress auto-download for episode: ${existingDownload.episodeId}")
            enforceMaxDownloadsQuota(database, downloadRepository, podcastId, maxAllowed)
            return Result.success()
        }
        return null
    }

    private suspend fun executeDownload(
        deps: SharedAppDependencies,
        database: BoxLoreDatabase,
        downloadRepository: DownloadRepository,
        podcastEntity: PodcastEntity,
        podcastId: String,
        episodeId: String,
        maxAllowed: Int,
    ): Result {
        return try {
            val podcastRepository = deps.podcastRepository
            Log.i("BoxLore_BackgroundTrace", "[Worker] Fetching episode metadata from repository for episodeId: $episodeId...")
            val episode = fetchEpisodeMetadata(podcastRepository, podcastEntity, podcastId, episodeId)

            if (episode.audioUrl.isBlank()) {
                Log.e(
                    "BoxLore_BackgroundTrace",
                    "[Worker] Cannot enqueue download for '$episodeId': audioUrl is blank. All metadata sources failed."
                )
                return Result.failure()
            }

            Log.i("BoxLore_BackgroundTrace", "[Worker] Fetched episode metadata successfully: '${episode.title}' (${episode.audioUrl})")
            val podcast = podcastEntity.toPodcast()

            Log.i("BoxLore_BackgroundTrace", "[Worker] Enqueueing background download via DownloadRepository for '${episode.title}'...")
            downloadRepository.addDownload(episode, podcast, isSmartDownloaded = false, isForeground = false)

            val completed = downloadRepository.awaitDownloadCompletion(episodeId)
            if (!completed) {
                Log.w("BoxLore_BackgroundTrace", "[Worker] Auto-download failed or timed out for episode: ${episode.title} ($episodeId)")
                return Result.retry()
            }

            Log.i("BoxLore_BackgroundTrace", "[Worker] SUCCESS! Completed auto-download for episode: ${episode.title} ($episodeId)")
            enforceMaxDownloadsQuota(database, downloadRepository, podcastId, maxAllowed)

            Result.success()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: java.io.IOException) {
            Log.e("BoxLore_BackgroundTrace", "[Worker] Network error during auto-download (will retry)", e)
            Result.retry()
        } catch (e: Exception) {
            Log.e("BoxLore_BackgroundTrace", "[Worker] Non-retryable error during auto-download", e)
            Result.failure()
        }
    }

    private suspend fun fetchEpisodeMetadata(
        podcastRepository: PodcastRepository,
        podcastEntity: PodcastEntity,
        podcastId: String,
        episodeId: String
    ): Episode {
        var episode = podcastRepository.getEpisode(episodeId)
        if (episode == null) {
            Log.w(
                "BoxLore_BackgroundTrace",
                "[Worker] Direct getEpisode failed for $episodeId. Attempting paginated list fallback for podcast $podcastId..."
            )
            val page = podcastRepository.getEpisodesPaginated(podcastId, limit = 50)
            episode = page.episodes.find { it.id == episodeId }
        }
        if (episode == null) {
            val latest = podcastEntity.latestEpisode
            if (latest != null) {
                Log.w("BoxLore_BackgroundTrace", "[Worker] Using local podcastEntity.latestEpisode fallback for $episodeId.")
                episode = latest.copy(id = episodeId)
            } else {
                Log.w("BoxLore_BackgroundTrace", "[Worker] Constructing emergency fallback episode for $episodeId.")
                episode = Episode(
                    id = episodeId,
                    title = "New Episode",
                    description = podcastEntity.description ?: "",
                    audioUrl = "",
                    imageUrl = podcastEntity.imageUrl,
                    podcastImageUrl = podcastEntity.imageUrl,
                    podcastTitle = podcastEntity.title,
                    podcastId = podcastId,
                    duration = 0,
                    publishedDate = System.currentTimeMillis() / 1000L
                )
            }
        }
        return episode
    }

    private suspend fun enforceMaxDownloadsQuota(
        database: BoxLoreDatabase,
        downloadRepository: DownloadRepository,
        podcastId: String,
        maxAllowed: Int
    ) {
        if (maxAllowed <= 0) return
        val allDownloads = database.downloadedEpisodeDao().getAllDownloadsSync()
        val podcastAutoDownloads = allDownloads.filter {
            it.podcastId == podcastId &&
                !it.isSmartDownloaded &&
                it.status in listOf(
                    DownloadedEpisodeEntity.STATUS_COMPLETED,
                    DownloadedEpisodeEntity.STATUS_DOWNLOADING,
                    DownloadedEpisodeEntity.STATUS_QUEUED
                )
        }

        Log.i(
            "BoxLore_BackgroundTrace",
            "[Worker] Quota Check: currently retain ${podcastAutoDownloads.size} auto-downloads (Max allowed: $maxAllowed)"
        )

        val excessCount = podcastAutoDownloads.size - maxAllowed
        if (excessCount > 0) {
            val toRemove = podcastAutoDownloads
                .sortedBy { it.downloadedAt }
                .take(excessCount)
            for (oldDownload in toRemove) {
                Log.i(
                    "BoxLore_BackgroundTrace",
                    "[Worker] Quota exceeded ($maxAllowed). Deleting oldest download '${oldDownload.episodeTitle}' (${oldDownload.episodeId})"
                )
                downloadRepository.removeDownload(oldDownload.episodeId, isForeground = false).join()
            }
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_PODCAST_ID = "podcast_id"
    }
}
