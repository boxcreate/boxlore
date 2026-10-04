package cx.aswin.boxlore.core.downloads

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.toPodcast
import cx.aswin.boxlore.core.database.AutoDownloadReleaseEntity
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Existing FQCN and input keys are retained for work scheduled before this fix. */
open class AutoDownloadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    private val fromBackgroundCheck: Boolean
        get() = inputData.getBoolean(KEY_BACKGROUND_CHECK, false) || AutoDownloadScheduling.BACKGROUND_TRANSFER_TAG in tags

    override suspend fun doWork(): Result {
        val episodeId = inputData.getString(KEY_EPISODE_ID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        val podcastId = inputData.getString(KEY_PODCAST_ID)?.takeIf { it.isNotBlank() } ?: return Result.failure()
        // Old polling-derived requests cannot bypass consent before startup reconciliation runs.
        if (AutoDownloadScheduling.TRANSFER_TAG in tags && AutoDownloadScheduling.POLICY_TRANSFER_TAG !in tags) return Result.success()
        return AutoDownloadTransfers.lock(episodeId).withLock {
            if (fromBackgroundCheck) {
                val preferences = SharedAppDependenciesHolder.require().userPreferencesRepository
                AutoDownloadBackgroundGate.create(applicationContext, preferences, forTransfer = true).runGuarded {
                    execute(podcastId, episodeId)
                } ?: if (preferences.autoDownloadBackgroundSettingsStream.first().enabled) Result.retry() else Result.success()
            } else {
                execute(podcastId, episodeId)
            }
        }
    }

    private suspend fun execute(podcastId: String, episodeId: String): Result {
        val deps = SharedAppDependenciesHolder.require()
        val database = deps.database
        val ledger = database.autoDownloadDao()
        val show = database.podcastDao().getPodcast(podcastId)
        if (show == null || !AutoDownloadCoordinator.eligible(show)) return Result.success()
        if (ledger.getRelease(episodeId)?.state in listOf(AutoDownloadReleaseEntity.HANDLED, AutoDownloadReleaseEntity.REMOVED)) return Result.success()
        ledger.insertRelease(AutoDownloadReleaseEntity(episodeId, podcastId))
        val repository = DownloadsDependenciesHolder.require().downloadRepository
        var ownsTransfer = false
        return try {
            withTimeoutOrNull(WORK_SLICE_MS) {
                downloadEpisode(deps, show, episodeId, repository) {
                    ownsTransfer = true
                    AutoDownloadTransfers.acquire(episodeId)
                }
            } ?: Result.retry()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("AutoDownloadWorker", "Auto-download will resume on retry", e)
            Result.retry()
        } finally {
            if (ownsTransfer) {
                withContext(NonCancellable) {
                try {
                    repository.pauseAutoDownload(episodeId)
                } finally {
                    AutoDownloadTransfers.release(episodeId)
                }
            }
            }
        }
    }

    private suspend fun downloadEpisode(
        deps: cx.aswin.boxlore.core.catalog.SharedAppDependencies,
        show: cx.aswin.boxlore.core.database.PodcastEntity,
        episodeId: String,
        repository: DownloadRepository,
        onTransferStarted: () -> Unit,
    ): Result {
        val maximum = deps.userPreferencesRepository.autoDownloadMaxEpisodesStream.first()
        handleExisting(repository.reconcileDownloadStatus(episodeId), show.podcastId, maximum, repository)?.let { return it }
        val episode = resolveEpisode(deps, show, episodeId) ?: return Result.retry()
        if (episode.audioUrl.isBlank()) return Result.retry()
        preflight(deps, show.podcastId, episodeId)?.let { return it }
        onTransferStarted()
        if (!repository.addAutoDownload(episode, show.toPodcast())) {
            deps.database.autoDownloadDao().finish(episodeId)
            return Result.success()
        }
        if (!repository.awaitAutoDownloadCompletion(episodeId)) return Result.retry()
        deps.database.autoDownloadDao().finish(episodeId)
        enforceQuota(show.podcastId, maximum, repository)
        return Result.success()
    }

    private suspend fun handleExisting(
        existing: DownloadedEpisodeEntity?,
        podcastId: String,
        maximum: Int,
        repository: DownloadRepository,
    ): Result? {
        if (existing == null) return null
        val database = SharedAppDependenciesHolder.require().database
        if (existing.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
            if (existing.isSmartDownloaded) {
                database.downloadedEpisodeDao().insert(existing.copy(isSmartDownloaded = false, downloadOrigin = DownloadedEpisodeEntity.ORIGIN_AUTO))
            }
            database.autoDownloadDao().finish(existing.episodeId)
            enforceQuota(podcastId, maximum, repository)
            return Result.success()
        }
        // A manual request owns this episode. Auto retention must never adopt it.
        if (!existing.isSmartDownloaded && existing.downloadOrigin != DownloadedEpisodeEntity.ORIGIN_AUTO) {
            database.autoDownloadDao().finish(existing.episodeId)
            return Result.success()
        }
        return null
    }

    private suspend fun resolveEpisode(
        deps: cx.aswin.boxlore.core.catalog.SharedAppDependencies,
        show: cx.aswin.boxlore.core.database.PodcastEntity,
        episodeId: String,
    ): cx.aswin.boxlore.core.model.Episode? {
        val stored = deps.database.localEpisodeCatalogDao().getEpisode(episodeId)
            ?.takeIf { it.podcastId == show.podcastId }?.toEpisode(show.title, show.imageUrl, show.genre, show.author)
            ?: show.latestEpisode?.takeIf { it.id == episodeId }
        if (stored != null) return stored
        val catalog = deps.podcastRepository.localEpisodeCatalog ?: return null
        val feed = show.feedUrl?.takeIf { it.startsWith("https://") } ?: return null
        val background = fromBackgroundCheck
        val backgroundGate = AutoDownloadBackgroundGate.create(applicationContext, deps.userPreferencesRepository, forTransfer = true)
        catalog.refresh(
            LocalEpisodeCatalogPort.RefreshRequest(
                show.podcastId,
                feed,
                AutoDownloadCoordinator.meta(show),
                reason = LocalEpisodeCatalogPort.RefreshReason.NEW_RELEASE,
                canProceed = { !background || backgroundGate.allowed() },
                runPostPersistCallback = !background,
            )
        )
        return catalog.getEpisode(episodeId)?.takeIf { it.podcastId == show.podcastId }
    }

    private suspend fun preflight(
        deps: cx.aswin.boxlore.core.catalog.SharedAppDependencies,
        podcastId: String,
        episodeId: String,
    ): Result? {
        val current = deps.database.podcastDao().getPodcast(podcastId)
        if (current == null || !AutoDownloadCoordinator.eligible(current)) return Result.success()
        if (deps.database.autoDownloadDao().getRelease(episodeId)?.state != AutoDownloadReleaseEntity.PENDING) return Result.success()
        val connectivity = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        if (deps.userPreferencesRepository.autoDownloadWifiOnlyStream.first() && connectivity.activeNetwork != null && connectivity.isActiveNetworkMetered) return Result.retry()
        return null
    }

    private suspend fun enforceQuota(podcastId: String, maximum: Int, repository: DownloadRepository) {
        val downloads = SharedAppDependenciesHolder.require().database.downloadedEpisodeDao().getDownloadsForPodcast(podcastId)
        for (row in AutoDownloadRetention.excess(downloads, maximum)) {
            repository.removeDownload(row.episodeId, isForeground = false).join()
        }
    }

    companion object {
        const val KEY_EPISODE_ID = "episode_id"
        const val KEY_PODCAST_ID = "podcast_id"
        const val KEY_BACKGROUND_CHECK = "background_check"
        const val WORK_SLICE_MS = 8 * 60 * 1000L
    }
}

internal object AutoDownloadRetention {
    fun excess(downloads: List<DownloadedEpisodeEntity>, maximum: Int): List<DownloadedEpisodeEntity> {
        if (maximum <= 0) return emptyList()
        val owned = downloads.filter { it.downloadOrigin == DownloadedEpisodeEntity.ORIGIN_AUTO && it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
            .sortedWith(compareByDescending<DownloadedEpisodeEntity> { it.publishedDate }.thenByDescending { it.downloadedAt }.thenBy { it.episodeId })
        return owned.drop(maximum)
    }
}
