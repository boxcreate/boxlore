package cx.aswin.boxlore.core.downloads

import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadIndex
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.room.withTransaction
import cx.aswin.boxlore.core.analytics.AnalyticsHelper
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeMediaCacheKey

/** Reports a failed download: error reason, episode ID, show ID, source. */
internal typealias DownloadFailureTracker = (String, String, String?, String?) -> Unit

private val trackFailedDownload: DownloadFailureTracker = { reason, episodeId, podcastId, source ->
    AnalyticsHelper.trackDownloadFailed(reason, episodeId, podcastId, source)
}

/**
 * What [DownloadRepository]'s Media3 listener does when Media3 gives up on [download]: reports the
 * failure, then keeps or clears the row through [keepFailedDownload].
 */
internal suspend fun onMedia3DownloadFailed(
    database: BoxLoreDatabase,
    download: Download,
    finalException: Exception?,
    trackFailed: DownloadFailureTracker = trackFailedDownload,
) {
    val episodeId = download.request.id
    val podcastIdFromRequest = String(download.request.data, Charsets.UTF_8).split("|").getOrNull(0)?.takeIf { it.isNotBlank() }
    val existing = database.downloadedEpisodeDao().getDownload(episodeId)
    trackFailed(
        DownloadAnalyticsMapping.failureReason(finalException),
        episodeId,
        existing?.podcastId ?: podcastIdFromRequest,
        DownloadAnalyticsMapping.source(existing?.isSmartDownloaded),
    )
    keepFailedDownload(database, episodeId)
}

/**
 * What [episodeId]'s download row becomes when Media3 gives up on its transfer. A manual download
 * or an auto-download stays as [DownloadedEpisodeEntity.STATUS_FAILED], so Downloads still lists it;
 * the listener can start a manual one again ([canRetryDownload]) and an auto-download retries on
 * its own. A Smart Download row goes, since Smart Downloads picks again on its next run. A
 * completed row is never changed.
 */
internal suspend fun keepFailedDownload(database: BoxLoreDatabase, episodeId: String) {
    val dao = database.downloadedEpisodeDao()
    database.withTransaction {
        val row = dao.getDownload(episodeId) ?: return@withTransaction
        when {
            row.status == DownloadedEpisodeEntity.STATUS_COMPLETED -> Unit
            isSmartRow(row) -> dao.delete(episodeId)
            else -> dao.insert(row.copy(status = DownloadedEpisodeEntity.STATUS_FAILED))
        }
    }
}

/** True for a failed download the listener can start again from Downloads (auto-downloads retry on their own). */
fun DownloadedEpisodeEntity.canRetryDownload(): Boolean =
    status == DownloadedEpisodeEntity.STATUS_FAILED && downloadOrigin != DownloadedEpisodeEntity.ORIGIN_AUTO

/**
 * True when this row is a download the listener asked for that background downloads must leave
 * alone: one queued, running or finished, or one that failed. A failed manual row gives way, so an
 * auto-download can still fetch the episode.
 */
internal fun DownloadedEpisodeEntity.isOwnedByManualRequest(): Boolean =
    status != DownloadedEpisodeEntity.STATUS_FAILED &&
        !isSmartDownloaded &&
        downloadOrigin != DownloadedEpisodeEntity.ORIGIN_AUTO

/**
 * True for a failed download the listener started, which Smart Downloads never picks up again: it
 * stays in Downloads for the listener to retry or remove.
 */
internal fun DownloadedEpisodeEntity.isListenersFailedDownload(): Boolean = canRetryDownload() && !isSmartRow(this)

/**
 * Moves a failed download back to downloading and returns the request that starts it again, built
 * from the episode's current audio address (feeds can replace an expired link), else the one
 * Media3 kept. Null, changing nothing, when the row can't be retried ([canRetryDownload]) or no
 * audio address is known. Reads Media3's index, so call it off the main thread.
 */
internal suspend fun prepareDownloadRetry(
    database: BoxLoreDatabase,
    downloadIndex: DownloadIndex,
    episodeId: String,
): Pair<DownloadedEpisodeEntity, DownloadRequest>? {
    val dao = database.downloadedEpisodeDao()
    val row = dao.getDownload(episodeId)?.takeIf { it.canRetryDownload() } ?: return null
    val stored = runCatching { downloadIndex.getDownload(episodeId)?.request }.getOrNull()
    val request = retryRequest(row, currentAudioUrl(database, episodeId), stored) ?: return null
    val started = database.withTransaction {
        val current = dao.getDownload(episodeId)?.takeIf { it.canRetryDownload() } ?: return@withTransaction false
        dao.insert(current.copy(status = DownloadedEpisodeEntity.STATUS_DOWNLOADING))
        true
    }
    return if (started) row to request else null
}

/**
 * The request that downloads [row] again: [audioUrl] when known, keeping the data of the [stored]
 * request when Media3 still has one; else [stored] as it is.
 */
private fun retryRequest(row: DownloadedEpisodeEntity, audioUrl: String?, stored: DownloadRequest?): DownloadRequest? {
    if (audioUrl == null) return stored
    if (stored != null && stored.uri.toString() == audioUrl) return stored
    val source = if (row.downloadOrigin == DownloadedEpisodeEntity.ORIGIN_SMART) "smart" else "manual"
    val data = stored?.data?.takeIf { it.isNotEmpty() }
        ?: "${row.podcastId}|${row.podcastName}|${row.episodeTitle}|$source".toByteArray()
    return DownloadRequest.Builder(row.episodeId, Uri.parse(audioUrl))
        .setMimeType(stored?.mimeType)
        .setCustomCacheKey(EpisodeMediaCacheKey.of(row.episodeId, audioUrl))
        .setData(data)
        .build()
}

/** The episode's audio address as the stored catalog or feed now has it. */
private suspend fun currentAudioUrl(database: BoxLoreDatabase, episodeId: String): String? {
    val catalog = runCatching { database.localEpisodeCatalogDao().getEpisode(episodeId)?.audioUrl }.getOrNull()
        ?.trim()?.takeIf { it.isNotEmpty() }
    return catalog
        ?: runCatching { database.rssEpisodeDao().getEpisode(episodeId)?.audioUrl }.getOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}

/** True for a Smart Download's row, which Smart Downloads replaces rather than keeping as failed. */
internal fun isSmartRow(row: DownloadedEpisodeEntity): Boolean =
    row.downloadOrigin != DownloadedEpisodeEntity.ORIGIN_AUTO &&
        (row.isSmartDownloaded || row.downloadOrigin == DownloadedEpisodeEntity.ORIGIN_SMART)
