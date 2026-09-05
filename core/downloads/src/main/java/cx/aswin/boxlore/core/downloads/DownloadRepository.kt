@file:Suppress("TooManyFunctions")

package cx.aswin.boxlore.core.downloads

import android.content.Context
import android.util.Log
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeMediaCacheKey
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.ranking.FeedbackTarget
import cx.aswin.boxlore.core.ranking.RankingAction
import cx.aswin.boxlore.core.ranking.RankingFeedbackRepository
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull

open class DownloadRepository(
    private val context: Context,
    private val database: BoxLoreDatabase,
    private val rankingFeedbackRepository: RankingFeedbackRepository,
) {
    private val downloadManager: DownloadManager = getDownloadManager(context)

    init {
        downloadManager.addListener(
            object : DownloadManager.Listener {
                override fun onDownloadChanged(
                    downloadManager: DownloadManager,
                    download: androidx.media3.exoplayer.offline.Download,
                    finalException: Exception?,
                ) {
                    // Sync status with DB
                    val state = download.state
                    val episodeId = download.request.id
                    val dataParts = String(download.request.data, Charsets.UTF_8).split("|")
                    val podcastIdFromRequest = dataParts.getOrNull(0)?.takeIf { it.isNotBlank() }
                    if (state == androidx.media3.exoplayer.offline.Download.STATE_COMPLETED) {
                        val fileSizeMb = DownloadAnalyticsMapping.fileSizeMb(download.contentLength)

                        CoroutineScope(Dispatchers.IO).launch {
                            val existing = database.downloadedEpisodeDao().getDownload(episodeId)
                            val podcastId = existing?.podcastId ?: podcastIdFromRequest ?: "unknown"
                            val source = DownloadAnalyticsMapping.source(existing?.isSmartDownloaded)
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDownloadCompleted(
                                episodeId,
                                podcastId,
                                source,
                                fileSizeMb,
                            )
                            if (existing != null) {
                                val updated =
                                    existing.copy(
                                        sizeBytes = if (download.contentLength > 0) download.contentLength else existing.sizeBytes,
                                        localFilePath = "CACHED", // Marker that it is in Media3 Cache
                                        status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                                    )
                                database.downloadedEpisodeDao().insert(updated)
                            } else {
                                val fallback = DownloadedEpisodeEntity(
                                    episodeId = episodeId,
                                    podcastId = podcastId,
                                    episodeTitle = dataParts.getOrNull(2) ?: "Downloaded Episode",
                                    episodeDescription = null,
                                    episodeImageUrl = null,
                                    podcastName = dataParts.getOrNull(1) ?: "Podcast",
                                    podcastImageUrl = null,
                                    durationMs = 0L,
                                    publishedDate = 0L,
                                    localFilePath = "CACHED",
                                    downloadId = 0L,
                                    downloadedAt = System.currentTimeMillis(),
                                    sizeBytes = if (download.contentLength > 0) download.contentLength else 0L,
                                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                                    isSmartDownloaded = false,
                                )
                                database.downloadedEpisodeDao().insert(fallback)
                            }
                        }
                    } else if (state == androidx.media3.exoplayer.offline.Download.STATE_FAILED) {
                        val errorReason = DownloadAnalyticsMapping.failureReason(finalException)

                        CoroutineScope(Dispatchers.IO).launch {
                            val existing = database.downloadedEpisodeDao().getDownload(episodeId)
                            val podcastId = existing?.podcastId ?: podcastIdFromRequest
                            val source = DownloadAnalyticsMapping.source(existing?.isSmartDownloaded)
                            cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDownloadFailed(
                                errorReason,
                                episodeId,
                                podcastId,
                                source,
                            )
                            // Optional: Allow user to retry or just delete
                            database.downloadedEpisodeDao().delete(episodeId)
                        }
                    } else if (state == androidx.media3.exoplayer.offline.Download.STATE_REMOVING) {
                        CoroutineScope(Dispatchers.IO).launch {
                            database.downloadedEpisodeDao().delete(episodeId)
                        }
                    }
                }
            },
        )
    }

    open fun addDownload(
        episode: Episode,
        podcast: Podcast,
        isSmartDownloaded: Boolean = false,
        isForeground: Boolean = true,
    ) {
        val source = if (isSmartDownloaded) "smart" else "manual"
        cx.aswin.boxlore.core.analytics.AnalyticsHelper.trackDownloadRequested(
            episode.id,
            podcast.id,
            source,
        )
        val downloadRequest =
            DownloadRequest
                .Builder(episode.id, android.net.Uri.parse(episode.audioUrl))
                .setCustomCacheKey(EpisodeMediaCacheKey.of(episode.id, episode.audioUrl))
                .setData(
                    // Serialize needed metadata to restore if app killed
                    // Ideally use Proto or JSON. For now, we trust DB has details.
                    "${podcast.id}|${podcast.title}|${episode.title}".toByteArray(),
                ).build()

        dispatchAddDownload(context, mediaDownloadServiceClass(), downloadRequest, isForeground)

        android.util.Log.d("DownloadRepo", "Optimistically adding download: ${episode.id}")
        // Optimistically insert into DB as "Downloading"
        CoroutineScope(Dispatchers.IO).launch {
            insertOptimisticDownload(context, database, rankingFeedbackRepository, episode, podcast, isSmartDownloaded)
        }
    }

    open fun removeDownload(episodeId: String, isForeground: Boolean = true): Job {
        // Capture artwork paths BEFORE triggering removal to avoid a race with
        // the DownloadManager listener (which deletes the DB row on STATE_REMOVING).
        return CoroutineScope(Dispatchers.IO).launch {
            val existing = try {
                database.downloadedEpisodeDao().getDownload(episodeId)
            } catch (e: Exception) {
                Log.e("DownloadRepo", "Failed to read artwork paths for $episodeId", e)
                null
            }

            val customCacheKey = try {
                getDownloadManager(context).downloadIndex.getDownload(episodeId)?.request?.customCacheKey
            } catch (_: Exception) {
                null
            } ?: try {
                database.localEpisodeCatalogDao().getEpisode(episodeId)?.audioUrl?.let {
                    EpisodeMediaCacheKey.of(episodeId, it)
                }
            } catch (_: Exception) {
                null
            }

            notifyServiceRemoveDownload(context, episodeId, isForeground)
            evictFromCaches(context, episodeId, customCacheKey)
            cleanupArtwork(
                database = database,
                episodeId = episodeId,
                podcastId = existing?.podcastId,
                episodeImgPath = existing?.episodeImageUrl,
                podcastImgPath = existing?.podcastImageUrl,
            )

            database.downloadedEpisodeDao().delete(episodeId)
        }
    }

    open suspend fun removeDownloadSuspend(episodeId: String, isForeground: Boolean = true) {
        removeDownload(episodeId, isForeground).join()
    }

    open suspend fun awaitDownloadCompletion(episodeId: String, timeoutMs: Long = 150_000L): Boolean {
        val initialExisting = database.downloadedEpisodeDao().getDownload(episodeId)
        if (initialExisting?.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
            return true
        }

        val initialMedia3 = getMedia3DownloadCompletionStatus(downloadManager, episodeId) { length ->
            markCompletedInDb(episodeId, length)
        }
        if (initialMedia3 != null) {
            return initialMedia3
        }

        return withTimeoutOrNull(timeoutMs) {
            var observer: DownloadCompletionObserver? = null
            try {
                suspendCancellableCoroutine { cont ->
                    val obs = DownloadCompletionObserver(episodeId, cont) { length ->
                        markCompletedInDb(episodeId, length)
                    }
                    observer = obs
                    downloadManager.addListener(obs)
                    cont.invokeOnCancellation {
                        downloadManager.removeListener(obs)
                    }

                    checkObserverAsyncDb(database, episodeId, obs)
                    checkObserverAsyncMedia3(downloadManager, episodeId, obs)
                }
            } finally {
                observer?.let { downloadManager.removeListener(it) }
            }
        } ?: false
    }

    private suspend fun markCompletedInDb(episodeId: String, contentLength: Long) {
        val existing = database.downloadedEpisodeDao().getDownload(episodeId)
        if (existing != null) {
            if (existing.status != DownloadedEpisodeEntity.STATUS_COMPLETED) {
                val updated = existing.copy(
                    sizeBytes = if (contentLength > 0) contentLength else existing.sizeBytes,
                    localFilePath = "CACHED",
                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                )
                database.downloadedEpisodeDao().insert(updated)
            }
        } else {
            val media3Download = runCatching { downloadManager.downloadIndex.getDownload(episodeId) }.getOrNull()
            val dataParts = media3Download?.request?.data?.let { String(it, Charsets.UTF_8).split("|") }
            val fallback = DownloadedEpisodeEntity(
                episodeId = episodeId,
                podcastId = dataParts?.getOrNull(0)?.takeIf { it.isNotBlank() } ?: "unknown",
                episodeTitle = dataParts?.getOrNull(2) ?: "Downloaded Episode",
                episodeDescription = null,
                episodeImageUrl = null,
                podcastName = dataParts?.getOrNull(1) ?: "Podcast",
                podcastImageUrl = null,
                durationMs = 0L,
                publishedDate = 0L,
                localFilePath = "CACHED",
                downloadId = 0L,
                downloadedAt = System.currentTimeMillis(),
                sizeBytes = if (contentLength > 0) contentLength else 0L,
                status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                isSmartDownloaded = false,
            )
            database.downloadedEpisodeDao().insert(fallback)
        }
        val verified = database.downloadedEpisodeDao().getDownload(episodeId)
        check(verified?.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
            "Failed to persist completed download row in Room for $episodeId"
        }
    }

    suspend fun reconcileDownloadStatus(episodeId: String): DownloadedEpisodeEntity? {
        val existing = database.downloadedEpisodeDao().getDownload(episodeId) ?: return null
        if (existing.status != DownloadedEpisodeEntity.STATUS_DOWNLOADING &&
            existing.status != DownloadedEpisodeEntity.STATUS_QUEUED
        ) {
            return existing
        }

        val media3Download = try {
            downloadManager.downloadIndex.getDownload(episodeId)
        } catch (e: Exception) {
            Log.w("DownloadRepo", "Failed to query downloadIndex for $episodeId", e)
            null
        }

        if (media3Download == null) {
            Log.w(
                "DownloadRepo",
                "Reconciling orphaned download: $episodeId not found in Media3 index. Deleting stale Room row.",
            )
            database.downloadedEpisodeDao().delete(episodeId)
            return null
        }

        return when (media3Download.state) {
            Download.STATE_COMPLETED -> {
                Log.i("DownloadRepo", "Reconciling download $episodeId: Media3 reported COMPLETED. Updating Room.")
                val updated = existing.copy(
                    sizeBytes = if (media3Download.contentLength > 0) media3Download.contentLength else existing.sizeBytes,
                    localFilePath = "CACHED",
                    status = DownloadedEpisodeEntity.STATUS_COMPLETED,
                )
                database.downloadedEpisodeDao().insert(updated)
                updated
            }
            Download.STATE_FAILED,
            Download.STATE_REMOVING -> {
                Log.w("DownloadRepo", "Reconciling download $episodeId: Media3 reported state ${media3Download.state}. Deleting Room row.")
                database.downloadedEpisodeDao().delete(episodeId)
                null
            }
            Download.STATE_DOWNLOADING,
            Download.STATE_QUEUED,
            Download.STATE_RESTARTING,
            Download.STATE_STOPPED -> reconcileActiveOrStoppedDownload(media3Download, existing, episodeId)
            else -> existing
        }
    }

    private suspend fun reconcileActiveOrStoppedDownload(
        media3Download: Download,
        existing: DownloadedEpisodeEntity,
        episodeId: String,
    ): DownloadedEpisodeEntity? {
        val lastActivityTimeMs = if (media3Download.updateTimeMs > 0) {
            media3Download.updateTimeMs
        } else {
            existing.downloadedAt
        }
        val ageMs = System.currentTimeMillis() - lastActivityTimeMs
        return if (ageMs > STALE_DOWNLOADING_THRESHOLD_MS) {
            Log.w(
                "DownloadRepo",
                "Reconciling download $episodeId: Stuck in state ${media3Download.state} for ${ageMs / 1000}s. Removing stale download.",
            )
            try {
                downloadManager.removeDownload(episodeId)
            } catch (e: Exception) {
                Log.e("DownloadRepo", "Failed to remove stale download $episodeId from DownloadManager", e)
            }
            database.downloadedEpisodeDao().delete(episodeId)
            null
        } else {
            existing
        }
    }

    suspend fun reconcileStaleDownloads() {
        val downloading = try {
            database.downloadedEpisodeDao().getAllDownloadsSync()
                .filter {
                    it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING ||
                        it.status == DownloadedEpisodeEntity.STATUS_QUEUED
                }
        } catch (e: Exception) {
            Log.e("DownloadRepo", "Failed to get downloads for reconciliation", e)
            emptyList()
        }
        for (entity in downloading) {
            reconcileDownloadStatus(entity.episodeId)
        }
    }

    val downloads: Flow<List<DownloadedEpisodeEntity>> =
        database.downloadedEpisodeDao().getAllDownloads().transform { rows ->
            emit(withUsableArtwork(rows))
        }

    /** Completed downloads mapped for listener-facing offline playback, newest release first. */
    val completedDownloadItems: Flow<List<CompletedDownloadItem>> =
        downloads.map(CompletedDownloadItems::from)

    /** Completed download episode ids — prefer over filtering [downloads] by Room status in features. */
    val completedDownloadIds: Flow<Set<String>> =
        downloads.map { list ->
            list
                .filter { it.status == DownloadedEpisodeEntity.STATUS_COMPLETED }
                .map { it.episodeId }
                .toSet()
        }

    /** Actively downloading episode ids — prefer over filtering [downloads] by Room status in features. */
    val downloadingEpisodeIds: Flow<Set<String>> =
        downloads.map { list ->
            list
                .filter { it.status == DownloadedEpisodeEntity.STATUS_DOWNLOADING }
                .map { it.episodeId }
                .toSet()
        }

    fun isDownloaded(episodeId: String): Flow<Boolean> = database.downloadedEpisodeDao().isDownloadedFlow(episodeId).map { it > 0 }

    fun isDownloading(episodeId: String): Flow<Boolean> = database.downloadedEpisodeDao().isDownloadingFlow(episodeId).map { it > 0 }

    private suspend fun withUsableArtwork(rows: List<DownloadedEpisodeEntity>): List<DownloadedEpisodeEntity> {
        if (rows.isEmpty()) return rows
        val fallbacks =
            database
                .podcastDao()
                .getPodcastsByIds(rows.map { it.podcastId }.distinct())
                .associate { podcast -> podcast.podcastId to podcast.imageUrl }
        return rows.map { row ->
            val episodeArt =
                DownloadArtworkUrls.resolve(
                    stored = row.episodeImageUrl,
                    fallback = row.podcastImageUrl ?: fallbacks[row.podcastId],
                ) ?: fallbacks[row.podcastId]
            val podcastArt =
                DownloadArtworkUrls.resolve(
                    stored = row.podcastImageUrl,
                    fallback = fallbacks[row.podcastId],
                ) ?: fallbacks[row.podcastId]
            if (episodeArt == row.episodeImageUrl && podcastArt == row.podcastImageUrl) {
                row
            } else {
                row.copy(episodeImageUrl = episodeArt, podcastImageUrl = podcastArt)
            }
        }
    }

    companion object {
        @Volatile
        private var downloadManager: DownloadManager? = null
        private var cache: Cache? = null
        private var streamCache: Cache? = null
        private var databaseProvider: DatabaseProvider? = null
        private var streamDatabaseProvider: DatabaseProvider? = null

        private const val STREAM_CACHE_MAX_BYTES = 250L * 1024 * 1024 // 250 MB
        const val STALE_DOWNLOADING_THRESHOLD_MS = 30 * 60 * 1000L // 30 minutes

        @androidx.annotation.VisibleForTesting
        @Synchronized
        fun resetForTesting() {
            try {
                downloadManager?.release()
            } catch (ignored: Exception) {
                Log.d("DownloadRepo", "Release failed", ignored)
            }
            try {
                cache?.release()
            } catch (ignored: Exception) {
                Log.d("DownloadRepo", "Release failed", ignored)
            }
            try {
                streamCache?.release()
            } catch (ignored: Exception) {
                Log.d("DownloadRepo", "Release failed", ignored)
            }
            downloadManager = null
            cache = null
            streamCache = null
            databaseProvider = null
            streamDatabaseProvider = null
        }

        fun mediaDownloadServiceClass(): Class<out DownloadService> =
            cx.aswin.boxlore.core.downloads.ports.DownloadServiceLauncherHolder.require().mediaDownloadServiceClass()

        fun getDownloadManager(context: Context): DownloadManager = downloadManager ?: synchronized(this) {
            downloadManager ?: createDownloadManager(context).also { downloadManager = it }
        }

        private fun createDownloadManager(context: Context): DownloadManager {
            val databaseProvider = getDatabaseProvider(context)
            val cache = getDownloadCache(context)
            val httpDataSourceFactory =
                androidx.media3.datasource.DefaultHttpDataSource
                    .Factory()
                    .setUserAgent(
                        androidx.media3.common.util.Util
                            .getUserAgent(context, "BoxLore"),
                    ).setAllowCrossProtocolRedirects(true)

            val dataSourceFactory =
                androidx.media3.datasource.DataSource.Factory {
                    ThrottlingDataSource(httpDataSourceFactory.createDataSource())
                }

            return DownloadManager(
                context,
                databaseProvider,
                cache,
                dataSourceFactory,
                Executors.newFixedThreadPool(6),
            )
        }

        private fun getDatabaseProvider(context: Context): DatabaseProvider = databaseProvider ?: StandaloneDatabaseProvider(context).also {
            databaseProvider = it
        }

        /** Permanent cache for user-downloaded episodes. No eviction. */
        @Synchronized
        fun getDownloadCache(context: Context): Cache = cache ?: run {
            val cacheDir = File(context.filesDir, "downloads")
            val evictor = NoOpCacheEvictor()
            val provider = getDatabaseProvider(context)
            SimpleCache(cacheDir, evictor, provider).also { cache = it }
        }

        // Keep old name for backward compat
        @Synchronized
        fun getCache(context: Context): Cache = getDownloadCache(context)

        /** LRU-evicted cache for streaming playback. Auto-cleans when exceeding 250 MB. */
        @Synchronized
        fun getStreamCache(context: Context): Cache = streamCache ?: run {
            val cacheDir = File(context.cacheDir, "stream_cache")
            val dbProvider = streamDatabaseProvider ?: StandaloneDatabaseProvider(context).also { streamDatabaseProvider = it }
            val evictor =
                androidx.media3.datasource.cache
                    .LeastRecentlyUsedCacheEvictor(STREAM_CACHE_MAX_BYTES)
            SimpleCache(cacheDir, evictor, dbProvider).also { streamCache = it }
        }

        /**
         * Media3's [Cache] has no key-rename API, so linking a Podcast Index download to its
         * RSS counterpart (a different episodeId) would otherwise orphan the already-downloaded
         * bytes under the old cache key. This copies the cached spans and content-length
         * metadata over to [newEpisodeId], frees the old resource, and best-effort re-registers
         * the download under the new id in Media3's own index so playback and the offline
         * library keep serving the cached asset instead of silently falling back to network.
         *
         * Returns true only when the destination has cached spans. It is idempotent after a
         * process death between this move and the caller's Room transaction.
         */
        fun relinkDownloadCache(context: Context, oldEpisodeId: String, newEpisodeId: String,): Boolean {
            if (oldEpisodeId == newEpisodeId) return true
            val cache = getDownloadCache(context)
            // Tracked separately from content length: ContentMetadata.getContentLength() can
            // legitimately be LENGTH_UNSET even after the spans were copied successfully.
            val movedSuccessfully =
                runCatching {
                    val spans = cache.getCachedSpans(oldEpisodeId)
                    if (spans.isEmpty()) {
                        // A process may have died after moving cache/index state but before the
                        // surrounding Room transaction committed. Treat an already-populated
                        // destination as a successful idempotent retry so the DB repair can finish.
                        return@runCatching cache
                            .getCachedSpans(newEpisodeId)
                            .any { it.isCached }
                    }
                    spans.filter { it.isCached }.forEach { span ->
                        copyCachedSpanToNewKey(cache, span, newEpisodeId)
                    }
                    val contentLength =
                        androidx.media3.datasource.cache.ContentMetadata
                            .getContentLength(cache.getContentMetadata(oldEpisodeId))
                    if (contentLength > 0) {
                        cache.applyContentMetadataMutations(
                            newEpisodeId,
                            androidx.media3.datasource.cache.ContentMetadataMutations.setContentLength(
                                androidx.media3.datasource.cache
                                    .ContentMetadataMutations(),
                                contentLength,
                            ),
                        )
                    }
                    true
                }.onFailure {
                    Log.w("DownloadRepo", "Failed to move cached bytes from $oldEpisodeId to $newEpisodeId", it)
                }.getOrDefault(false)

            if (!movedSuccessfully) return false
            // Re-key the Media3 download index first, and only drop the old cache resource once
            // that succeeds, so a failure here never leaves the new key without an index entry.
            relinkDownloadIndexEntry(context, oldEpisodeId, newEpisodeId)
            runCatching { cache.removeResource(oldEpisodeId) }
                .onFailure { Log.w("DownloadRepo", "Failed to release old cache resource $oldEpisodeId", it) }
            return true
        }

        /**
         * Copies one cached [span] to [newEpisodeId], acquiring the write hole via
         * [Cache.startReadWrite] first as required by [Cache]'s locking contract — writing
         * through [Cache.startFile] without that lock can race with another active writer for
         * the same key/offset/length.
         */
        private fun copyCachedSpanToNewKey(cache: Cache, span: androidx.media3.datasource.cache.CacheSpan, newEpisodeId: String,) {
            val destSpan = cache.startReadWrite(newEpisodeId, span.position, span.length)
            if (destSpan.isCached) return // already present at the destination key/offset
            try {
                val newFile = cache.startFile(newEpisodeId, span.position, span.length)
                span.file?.copyTo(newFile, overwrite = true)
                cache.commitFile(newFile, span.length)
            } finally {
                cache.releaseHoleSpan(destSpan)
            }
        }

        /** Best-effort: re-registers the Media3 download index entry under the new id. */
        private fun relinkDownloadIndexEntry(context: Context, oldEpisodeId: String, newEpisodeId: String,) {
            runCatching {
                val manager = getDownloadManager(context)
                val existing = manager.downloadIndex.getDownload(oldEpisodeId) ?: return@runCatching
                val newRequest =
                    DownloadRequest
                        .Builder(newEpisodeId, existing.request.uri)
                        .setCustomCacheKey(newEpisodeId)
                        .apply { existing.request.mimeType?.let(::setMimeType) }
                        .setData(existing.request.data)
                        .build()
                manager.addDownload(newRequest)
                manager.removeDownload(oldEpisodeId)
            }.onFailure {
                Log.w(
                    "DownloadRepo",
                    "Failed to re-key Media3 download index from $oldEpisodeId to $newEpisodeId",
                    it,
                )
            }
        }
    }
}

private fun dispatchAddDownload(
    context: Context,
    serviceClass: Class<out DownloadService>,
    downloadRequest: DownloadRequest,
    isForeground: Boolean,
) {
    if (isForeground) {
        try {
            DownloadService.sendAddDownload(
                context,
                serviceClass,
                downloadRequest,
                true,
            )
        } catch (e: Exception) {
            android.util.Log.w(
                "DownloadRepo",
                "Background service start blocked by OS. Adding directly to DownloadManager instance.",
                e,
            )
            enqueueDirectlyToManager(context, downloadRequest)
        }
    } else {
        enqueueDirectlyToManager(context, downloadRequest)
    }
}

private fun enqueueDirectlyToManager(context: Context, downloadRequest: DownloadRequest) {
    try {
        val manager = DownloadRepository.getDownloadManager(context)
        manager.resumeDownloads()
        manager.addDownload(downloadRequest)
    } catch (e: Exception) {
        android.util.Log.e("DownloadRepo", "Failed to add download directly to DownloadManager", e)
    }
}

private fun notifyServiceRemoveDownload(
    context: Context,
    episodeId: String,
    isForeground: Boolean,
) {
    if (isForeground) {
        try {
            DownloadService.sendRemoveDownload(
                context,
                DownloadRepository.mediaDownloadServiceClass(),
                episodeId,
                false,
            )
        } catch (e: Exception) {
            Log.w("DownloadRepo", "sendRemoveDownload failed for $episodeId", e)
        }
    }
}

private fun evictFromCaches(
    context: Context,
    episodeId: String,
    customCacheKey: String?,
) {
    try {
        DownloadRepository.getDownloadManager(context).removeDownload(episodeId)
    } catch (e: Exception) {
        Log.e("DownloadRepo", "Direct removeDownload on DownloadManager failed for $episodeId", e)
    }

    try {
        val cache = DownloadRepository.getDownloadCache(context)
        val cacheKey = customCacheKey?.takeIf { it.isNotBlank() } ?: episodeId
        cache.removeResource(cacheKey)
        if (cacheKey != episodeId) {
            try {
                cache.removeResource(episodeId)
            } catch (e: Exception) {
                Log.d("DownloadRepo", "Cache removal failed", e)
            }
        }
        if (episodeId.startsWith("briefing_")) {
            evictMatchingBriefingResources(cache, episodeId)
        }
    } catch (e: Exception) {
        Log.w("DownloadRepo", "Direct SimpleCache removal fallback for $episodeId failed or unneeded", e)
    }
}

private fun evictMatchingBriefingResources(cache: Cache, episodeId: String) {
    try {
        cache.keys
            .filter { it.startsWith("${episodeId}_") }
            .forEach { versionedKey -> runCatching { cache.removeResource(versionedKey) } }
    } catch (e: Exception) {
        Log.d("DownloadRepo", "Briefing evict failed", e)
    }
}

private suspend fun cleanupArtwork(
    database: BoxLoreDatabase,
    episodeId: String,
    podcastId: String?,
    episodeImgPath: String?,
    podcastImgPath: String?,
) {
    try {
        deleteLocalFileIfValid(episodeImgPath)
        if (podcastId != null && podcastImgPath != null) {
            val othersCount = database.downloadedEpisodeDao().countOthersByPodcastId(podcastId, episodeId)
            if (othersCount == 0) {
                deleteLocalFileIfValid(podcastImgPath)
            }
        }
    } catch (e: Exception) {
        Log.e("DownloadRepo", "Failed to clean up artwork files for $episodeId", e)
    }
}

private fun downloadArtworkLocally(
    context: Context,
    imageUrl: String?,
    subDir: String,
    fileName: String,
): String? {
    if (imageUrl.isNullOrBlank()) return null
    try {
        val cleanUrlStr = if (imageUrl.startsWith("//")) "https:$imageUrl" else imageUrl
        val url = java.net.URI.create(cleanUrlStr).toURL()
        val dir = File(context.filesDir, subDir).apply { mkdirs() }
        val file = File(dir, fileName)
        url.openStream().use { input ->
            file.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return file.absolutePath
    } catch (e: Exception) {
        android.util.Log.e("DownloadRepo", "Failed to download artwork: $imageUrl", e)
        return null
    }
}

private fun deleteLocalFileIfValid(path: String?) {
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
            android.util.Log.w("DownloadRepo", "Failed to delete file: $cleanPath", e)
        }
    }
}

private fun resolveArtworkUrl(primary: String?, fallback: String?): String? =
    DownloadArtworkUrls.remoteUrl(primary) ?: DownloadArtworkUrls.remoteUrl(fallback)

private suspend fun handleAlreadyCompletedOptimistic(
    database: BoxLoreDatabase,
    rankingFeedbackRepository: RankingFeedbackRepository,
    existing: DownloadedEpisodeEntity,
    episode: Episode,
    podcast: Podcast,
    effectiveIsSmartDownloaded: Boolean,
) {
    if (existing.isSmartDownloaded && !effectiveIsSmartDownloaded) {
        database.downloadedEpisodeDao().insert(existing.copy(isSmartDownloaded = false))
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
)

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
        val current = database.downloadedEpisodeDao().getDownload(episodeId) ?: return
        database.downloadedEpisodeDao().insert(
            current.copy(
                episodeImageUrl = localEp ?: current.episodeImageUrl,
                podcastImageUrl = localPod ?: current.podcastImageUrl,
            ),
        )
    } catch (e: Exception) {
        android.util.Log.e("DownloadRepo", "Failed to update artwork paths for $episodeId", e)
    }
}

private suspend fun insertOptimisticDownload(
    context: Context,
    database: BoxLoreDatabase,
    rankingFeedbackRepository: RankingFeedbackRepository,
    episode: Episode,
    podcast: Podcast,
    isSmartDownloaded: Boolean,
) {
    val existing = try {
        database.downloadedEpisodeDao().getDownload(episode.id)
    } catch (e: Exception) {
        null
    }
    val effectiveIsSmart = isSmartDownloaded && (existing == null || existing.isSmartDownloaded)

    if (existing?.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
        handleAlreadyCompletedOptimistic(database, rankingFeedbackRepository, existing, episode, podcast, effectiveIsSmart)
        return
    }

    val epArt = resolveArtworkUrl(episode.imageUrl, podcast.imageUrl)
    val podArt = resolveArtworkUrl(podcast.imageUrl, episode.imageUrl)
    val entity = buildImmediateOptimisticEntity(episode, podcast, existing, effectiveIsSmart, epArt, podArt)

    try {
        database.downloadedEpisodeDao().insert(entity)
    } catch (e: Exception) {
        android.util.Log.e("DownloadRepo", "Optimistic insert failed for ${episode.id}", e)
    }

    if (!effectiveIsSmart) {
        recordDownloadRankingFeedback(rankingFeedbackRepository, episode, podcast)
    }

    fetchAndPersistArtwork(context, database, episode.id, podcast.id, epArt, podArt)
}

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

@androidx.annotation.VisibleForTesting
internal suspend fun getMedia3DownloadCompletionStatus(
    downloadManager: DownloadManager,
    episodeId: String,
    onCompleted: suspend (Long) -> Unit,
): Boolean? {
    val download = try {
        downloadManager.downloadIndex.getDownload(episodeId)
    } catch (e: Exception) {
        null
    } ?: return null

    return when (download.state) {
        androidx.media3.exoplayer.offline.Download.STATE_COMPLETED -> {
            try {
                onCompleted(download.contentLength)
                true
            } catch (e: Exception) {
                android.util.Log.e("DownloadRepo", "Failed to persist Room row for completed download $episodeId", e)
                false
            }
        }
        androidx.media3.exoplayer.offline.Download.STATE_FAILED,
        androidx.media3.exoplayer.offline.Download.STATE_REMOVING -> false
        else -> null
    }
}

@androidx.annotation.VisibleForTesting
internal class DownloadCompletionObserver(
    private val episodeId: String,
    private val cont: CancellableContinuation<Boolean>,
    private val onCompleted: suspend (Long) -> Unit,
) : DownloadManager.Listener {
    private val resumed = java.util.concurrent.atomic.AtomicBoolean(false)

    fun safeResume(success: Boolean) {
        if (resumed.compareAndSet(false, true) && cont.isActive) {
            cont.resume(success)
        }
    }

    fun handleCompletion(contentLength: Long) {
        if (resumed.compareAndSet(false, true)) {
            CoroutineScope(Dispatchers.IO).launch {
                var success = false
                try {
                    onCompleted(contentLength)
                    success = true
                } catch (e: Exception) {
                    android.util.Log.e("DownloadRepo", "Error marking download completed in DB", e)
                } finally {
                    if (cont.isActive) {
                        cont.resume(success)
                    }
                }
            }
        }
    }

    override fun onDownloadChanged(manager: DownloadManager, download: Download, finalException: Exception?) {
        if (download.request.id == episodeId) {
            when (download.state) {
                Download.STATE_COMPLETED -> handleCompletion(download.contentLength)
                Download.STATE_FAILED,
                Download.STATE_REMOVING -> safeResume(false)
            }
        }
    }

    override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
        if (download.request.id == episodeId) safeResume(false)
    }
}

private fun checkObserverAsyncDb(database: BoxLoreDatabase, episodeId: String, observer: DownloadCompletionObserver) {
    CoroutineScope(Dispatchers.IO).launch {
        if (database.downloadedEpisodeDao().getDownload(episodeId)?.status == DownloadedEpisodeEntity.STATUS_COMPLETED) {
            observer.safeResume(true)
        }
    }
}

private fun checkObserverAsyncMedia3(
    downloadManager: DownloadManager,
    episodeId: String,
    observer: DownloadCompletionObserver,
) {
    CoroutineScope(Dispatchers.IO).launch {
        val recheckMedia3 = runCatching { downloadManager.downloadIndex.getDownload(episodeId) }.getOrNull()
        when (recheckMedia3?.state) {
            Download.STATE_COMPLETED -> observer.handleCompletion(recheckMedia3.contentLength)
            Download.STATE_FAILED,
            Download.STATE_REMOVING -> observer.safeResume(false)
        }
    }
}
