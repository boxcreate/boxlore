package cx.aswin.boxlore.core.playback.service.auto

import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.playback.CastMediaEligibility
import cx.aswin.boxlore.core.playback.CastMediaMetadata
import cx.aswin.boxlore.core.playback.PlaybackMediaIdPolicy

/**
 * Resolves Auto browse MediaItems and domain episodes into playable URIs.
 * Extracted from [AutoBrowseLibraryCallback].
 */
internal class AutoMediaResolver(private val host: AutoBrowseLibraryHost,) {
    suspend fun resolveMediaItem(item: MediaItem): MediaItem {
        android.util.Log.d("BoxCastPlayer", "resolveMediaItem: mediaId=${item.mediaId}, initialArtworkUri=${item.mediaMetadata.artworkUri}")
        val episodeId = item.mediaId.stripEpisodePrefix()
        val uri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri

        if (uri != null) {
            val existingArtist = item.mediaMetadata.artist?.toString()?.takeIf(String::isNotBlank)
            val existingAlbum = item.mediaMetadata.albumTitle?.toString()?.takeIf(String::isNotBlank)
            val resolvedPodcast = existingAlbum ?: existingArtist
            val metadataBuilder = item.mediaMetadata.buildUpon()
            if (resolvedPodcast == null) {
                val podcastName =
                    host.database.listeningHistoryDao().getHistoryItem(episodeId)?.podcastName?.takeIf(String::isNotBlank)
                        ?: host.queueRepository.getQueueItemByEpisodeId(episodeId)?.podcastTitle?.takeIf(String::isNotBlank)
                        ?: host.database.downloadedEpisodeDao().getDownload(episodeId)?.podcastName?.takeIf(String::isNotBlank)
                        ?: runCatching { host.podcastRepository.getEpisode(episodeId) }.getOrNull()?.let {
                            it.podcastTitle?.takeIf(String::isNotBlank) ?: it.podcastArtist?.takeIf(String::isNotBlank)
                        }
                if (podcastName != null) {
                    metadataBuilder
                        .setArtist(podcastName)
                        .setAlbumTitle(podcastName)
                        .setSubtitle(podcastName)
                }
            }
            return item
                .buildUpon()
                .setUri(uri)
                .setMediaMetadata(metadataBuilder.build())
                .setCustomCacheKey(
                    PlaybackMediaIdPolicy.customCacheKey(episodeId, uri.toString()),
                ).build()
        }

        val download = host.database.downloadedEpisodeDao().getDownload(episodeId)
        val historyItem = host.database.listeningHistoryDao().getHistoryItem(episodeId)
        val queueItem = host.queueRepository.getQueueItemByEpisodeId(episodeId)
        val downloadCompleted =
            download?.status ==
                cx.aswin.boxlore.core.database.DownloadedEpisodeEntity.STATUS_COMPLETED
        val source =
            AutoMediaResolutionPolicy.resolve(
                downloadCompleted = downloadCompleted,
                downloadUri = if (downloadCompleted) resolveDownloadRequestUri(episodeId) else null,
                historyAudioUrl = historyItem?.episodeAudioUrl,
                queueAudioUrl = queueItem?.audioUrl,
                historyMimeType = historyItem?.enclosureType,
                queueMimeType = queueItem?.enclosureType,
            )
        val resolvedAudioUrl = source.playbackUri
        if (resolvedAudioUrl != null) {
            val fallbackEpisode =
                if (historyItem?.podcastName.isNullOrBlank() && queueItem?.podcastTitle.isNullOrBlank() && download?.podcastName.isNullOrBlank()) {
                    runCatching { host.podcastRepository.getEpisode(episodeId) }.getOrNull()
                } else {
                    null
                }
            val podcastName =
                historyItem?.podcastName?.takeIf(String::isNotBlank)
                    ?: queueItem?.podcastTitle?.takeIf(String::isNotBlank)
                    ?: download?.podcastName?.takeIf(String::isNotBlank)
                    ?: fallbackEpisode?.podcastTitle?.takeIf(String::isNotBlank)
                    ?: fallbackEpisode?.podcastArtist?.takeIf(String::isNotBlank)

            val episodeTitle =
                historyItem?.episodeTitle?.takeIf(String::isNotBlank)
                    ?: queueItem?.title?.takeIf(String::isNotBlank)
                    ?: download?.episodeTitle?.takeIf(String::isNotBlank)
                    ?: fallbackEpisode?.title

            val artworkUriStr =
                historyItem?.episodeImageUrl?.takeIf(String::isNotBlank)
                    ?: historyItem?.podcastImageUrl?.takeIf(String::isNotBlank)
                    ?: queueItem?.imageUrl?.takeIf(String::isNotBlank)
                    ?: queueItem?.podcastImageUrl?.takeIf(String::isNotBlank)
                    ?: download?.episodeImageUrl?.takeIf(String::isNotBlank)
                    ?: download?.podcastImageUrl?.takeIf(String::isNotBlank)
                    ?: fallbackEpisode?.imageUrl
                    ?: fallbackEpisode?.podcastImageUrl

            android.util.Log.d("BoxCastPlayer", "resolveMediaItem: resolved: '$artworkUriStr', pod: '$podcastName'")
            return MediaItem
                .Builder()
                .setMediaId(item.mediaId)
                .setUri(resolvedAudioUrl)
                .setMimeType(source.mimeType)
                .setCustomCacheKey(
                    PlaybackMediaIdPolicy.customCacheKey(episodeId, resolvedAudioUrl),
                ).setMediaMetadata(
                    item.mediaMetadata
                        .buildUpon()
                        .setTitle(episodeTitle)
                        .setArtist(podcastName.orEmpty())
                        .setSubtitle(podcastName)
                        .setAlbumTitle(podcastName)
                        .setArtworkUri(
                            AutoArtworkRepository.remoteUri(
                                host.asContext(),
                                artworkUriStr,
                            ),
                        ).setExtras(
                            CastMediaMetadata.extrasWithRemoteUri(
                                existing =
                                AutoBrowseContract.mergeExtras(
                                    item.mediaMetadata.extras,
                                    AutoBrowseContract.itemExtras(
                                        source =
                                        item.mediaMetadata.extras
                                            ?.getString(AutoBrowseContract.EXTRA_SOURCE)
                                            ?: AutoBrowseContract.SOURCE_DISCOVER,
                                        downloadStatus =
                                        if (
                                            downloadCompleted
                                        ) {
                                            androidx.media3.session.MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED
                                        } else {
                                            null
                                        },
                                    ),
                                ),
                                remoteUri = source.castRemoteUri,
                            ),
                        ).build(),
                ).build()
        }

        // Try API
        val episode = host.podcastRepository.getEpisode(episodeId)
        if (episode != null) {
            val podcastName =
                episode.podcastTitle?.takeIf(String::isNotBlank)
                    ?: episode.podcastArtist?.takeIf(String::isNotBlank)
            android.util.Log.d("BoxCastPlayer", "resolveMediaItem: resolved from API: '${episode.imageUrl}'")
            return MediaItem
                .Builder()
                .setMediaId(item.mediaId)
                .setUri(episode.audioUrl)
                .setMimeType(episode.enclosureType)
                .setCustomCacheKey(
                    PlaybackMediaIdPolicy.customCacheKey(episodeId, episode.audioUrl),
                ).setMediaMetadata(
                    item.mediaMetadata
                        .buildUpon()
                        .setTitle(episode.title)
                        .setArtist(podcastName.orEmpty())
                        .setSubtitle(podcastName)
                        .setAlbumTitle(podcastName)
                        .setArtworkUri(
                            AutoArtworkRepository.remoteUri(
                                host.asContext(),
                                episode.imageUrl ?: episode.podcastImageUrl,
                            ),
                        ).setExtras(
                            CastMediaMetadata.extrasWithRemoteUri(
                                existing = item.mediaMetadata.extras,
                                remoteUri = episode.audioUrl,
                            ),
                        ).build(),
                ).build()
        }

        android.util.Log.e("AutoBrowse", "Could not resolve episode: $episodeId")
        return item
    }

    suspend fun resolveDomainEpisode(episodeId: String): cx.aswin.boxlore.core.model.Episode? {
        host.queueRepository
            .getQueueSnapshot()
            .firstOrNull { it.id == episodeId }
            ?.let { return it }
        val history = host.database.listeningHistoryDao().getHistoryItem(episodeId)
        val historyAudioUrl = history?.episodeAudioUrl
        if (history != null && historyAudioUrl != null) {
            return cx.aswin.boxlore.core.model.Episode(
                id = history.episodeId,
                title = history.episodeTitle,
                description = history.episodeDescription.orEmpty(),
                audioUrl = historyAudioUrl,
                imageUrl = history.episodeImageUrl,
                podcastImageUrl = history.podcastImageUrl,
                podcastTitle = history.podcastName,
                podcastId = history.podcastId,
                duration = (history.durationMs / 1_000L).toInt(),
                enclosureType = history.enclosureType,
            )
        }
        val download = host.database.downloadedEpisodeDao().getDownload(episodeId)
        if (
            download?.status ==
            cx.aswin.boxlore.core.database.DownloadedEpisodeEntity.STATUS_COMPLETED
        ) {
            val audioUrl =
                resolveDownloadRequestUri(episodeId)
                    ?: download.localFilePath
                        .takeIf {
                            it.isNotBlank() && it != "CACHED" && java.io.File(it).isFile
                        }?.let {
                            android.net.Uri
                                .fromFile(java.io.File(it))
                                .toString()
                        }
            if (audioUrl != null) {
                return cx.aswin.boxlore.core.model.Episode(
                    id = download.episodeId,
                    title = download.episodeTitle,
                    description = download.episodeDescription.orEmpty(),
                    audioUrl = audioUrl,
                    imageUrl = download.episodeImageUrl,
                    podcastImageUrl = download.podcastImageUrl,
                    podcastTitle = download.podcastName,
                    podcastId = download.podcastId,
                    duration = (download.durationMs / 1_000L).toInt(),
                    publishedDate = download.publishedDate,
                )
            }
        }
        return host.podcastRepository.getEpisode(episodeId)
    }

    fun resolveDownloadRequestUri(episodeId: String): String? = runCatching {
        cx.aswin.boxlore.core.downloads.DownloadRepository
            .getDownloadManager(host.asContext())
            .downloadIndex
            .getDownload(episodeId)
            ?.request
            ?.uri
            ?.toString()
    }.onFailure {
        android.util.Log.w(
            "AutoBrowse",
            "Unable to resolve cached download URI for $episodeId",
            it,
        )
    }.getOrNull()
}

internal data class AutoMediaSource(val playbackUri: String?, val castRemoteUri: String?, val mimeType: String?,)

internal object AutoMediaResolutionPolicy {
    fun resolve(
        downloadCompleted: Boolean,
        downloadUri: String?,
        historyAudioUrl: String?,
        queueAudioUrl: String?,
        historyMimeType: String?,
        queueMimeType: String?,
    ): AutoMediaSource {
        val remoteUri =
            historyAudioUrl?.takeIf { it.isNotBlank() }
                ?: queueAudioUrl?.takeIf { it.isNotBlank() }
        val localUri = downloadUri.takeIf { downloadCompleted && !it.isNullOrBlank() }
        return AutoMediaSource(
            playbackUri = localUri ?: remoteUri,
            castRemoteUri = remoteUri?.takeIf(CastMediaEligibility::isCastable),
            mimeType = historyMimeType ?: queueMimeType,
        )
    }
}
