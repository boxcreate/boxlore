package cx.aswin.boxlore.core.playback.service.auto

import android.net.Uri
import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.model.Episode
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
            return resolveItemWithUri(item, episodeId, uri)
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
            val localPodcastName =
                historyItem?.podcastName?.takeIf(String::isNotBlank)
                    ?: queueItem?.podcastTitle?.takeIf(String::isNotBlank)
                    ?: download?.podcastName?.takeIf(String::isNotBlank)
                    ?: item.mediaMetadata.albumTitle?.toString()?.takeIf(String::isNotBlank)
                    ?: item.mediaMetadata.artist?.toString()?.takeIf(String::isNotBlank)
                    ?: item.mediaMetadata.subtitle?.toString()?.takeIf(String::isNotBlank)

            val localTitle =
                historyItem?.episodeTitle?.takeIf(String::isNotBlank)
                    ?: queueItem?.title?.takeIf(String::isNotBlank)
                    ?: download?.episodeTitle?.takeIf(String::isNotBlank)
                    ?: item.mediaMetadata.title?.toString()?.takeIf(String::isNotBlank)

            val localArtworkUri =
                historyItem?.episodeImageUrl?.takeIf(String::isNotBlank)
                    ?: historyItem?.podcastImageUrl?.takeIf(String::isNotBlank)
                    ?: queueItem?.imageUrl?.takeIf(String::isNotBlank)
                    ?: queueItem?.podcastImageUrl?.takeIf(String::isNotBlank)
                    ?: download?.episodeImageUrl?.takeIf(String::isNotBlank)
                    ?: download?.podcastImageUrl?.takeIf(String::isNotBlank)
                    ?: item.mediaMetadata.artworkUri?.toString()?.takeIf(String::isNotBlank)

            val fallbackEpisode =
                if (AutoMediaResolutionPolicy.needsPlaybackMetadataFallback(localTitle, localPodcastName, localArtworkUri)) {
                    runCatching { host.podcastRepository.getEpisode(episodeId) }.getOrNull()
                } else {
                    null
                }

            val metadata =
                AutoMediaResolutionPolicy.resolvePlaybackMetadata(
                    localTitle = localTitle,
                    localPodcastName = localPodcastName,
                    localArtworkUri = localArtworkUri,
                    fallbackTitle = fallbackEpisode?.title,
                    fallbackPodcastName = fallbackEpisode?.podcastTitle?.takeIf(String::isNotBlank)
                        ?: fallbackEpisode?.podcastArtist?.takeIf(String::isNotBlank),
                    fallbackArtworkUri = fallbackEpisode?.imageUrl ?: fallbackEpisode?.podcastImageUrl,
                )

            android.util.Log.d("BoxCastPlayer", "resolveMediaItem: resolved: '${metadata.artworkUri}', pod: '${metadata.podcastName}'")
            return buildResolvedMediaItem(
                item = item,
                episodeId = episodeId,
                resolvedAudioUrl = resolvedAudioUrl,
                source = source,
                metadata = metadata,
                downloadCompleted = downloadCompleted,
            )
        }

        // Try API
        val episode = host.podcastRepository.getEpisode(episodeId)
        if (episode != null) {
            return buildApiFallbackMediaItem(item, episodeId, episode)
        }

        android.util.Log.e("AutoBrowse", "Could not resolve episode: $episodeId")
        return item
    }

    private suspend fun resolveItemWithUri(
        item: MediaItem,
        episodeId: String,
        uri: Uri,
    ): MediaItem {
        val existingArtist = item.mediaMetadata.artist?.toString()
        val existingAlbum = item.mediaMetadata.albumTitle?.toString()
        val existingSubtitle = item.mediaMetadata.subtitle?.toString()
        val metadataBuilder = item.mediaMetadata.buildUpon()

        if (AutoMediaResolutionPolicy.needsItemMetadataFallback(existingArtist, existingAlbum, existingSubtitle)) {
            val fallbackPodcastName = resolvePodcastNameFallback(episodeId)
            val enriched =
                AutoMediaResolutionPolicy.resolveItemMetadata(
                    existingArtist = existingArtist,
                    existingAlbumTitle = existingAlbum,
                    existingSubtitle = existingSubtitle,
                    fallbackPodcastName = fallbackPodcastName,
                )
            enriched.artist?.let { metadataBuilder.setArtist(it) }
            enriched.albumTitle?.let { metadataBuilder.setAlbumTitle(it) }
            enriched.subtitle?.let { metadataBuilder.setSubtitle(it) }
        }

        return item
            .buildUpon()
            .setUri(uri)
            .setMediaMetadata(metadataBuilder.build())
            .setCustomCacheKey(
                PlaybackMediaIdPolicy.customCacheKey(episodeId, uri.toString()),
            ).build()
    }

    private suspend fun resolvePodcastNameFallback(episodeId: String): String? =
        host.database.listeningHistoryDao().getHistoryItem(episodeId)?.podcastName?.takeIf(String::isNotBlank)
            ?: host.queueRepository.getQueueItemByEpisodeId(episodeId)?.podcastTitle?.takeIf(String::isNotBlank)
            ?: host.database.downloadedEpisodeDao().getDownload(episodeId)?.podcastName?.takeIf(String::isNotBlank)
            ?: runCatching { host.podcastRepository.getEpisode(episodeId) }.getOrNull()?.let {
                it.podcastTitle?.takeIf(String::isNotBlank) ?: it.podcastArtist?.takeIf(String::isNotBlank)
            }

    private fun buildResolvedMediaItem(
        item: MediaItem,
        episodeId: String,
        resolvedAudioUrl: String,
        source: AutoMediaSource,
        metadata: AutoPlaybackMetadata,
        downloadCompleted: Boolean,
    ): MediaItem {
        val resolvedArtworkUri =
            metadata.artworkUri?.let { uriStr ->
                if (uriStr.startsWith("content://")) {
                    Uri.parse(uriStr)
                } else {
                    AutoArtworkRepository.remoteUri(host.asContext(), uriStr)
                }
            } ?: item.mediaMetadata.artworkUri

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
                    .setTitle(metadata.title ?: item.mediaMetadata.title)
                    .setArtist(metadata.podcastName ?: item.mediaMetadata.artist?.toString().orEmpty())
                    .setSubtitle(metadata.podcastName ?: item.mediaMetadata.subtitle?.toString())
                    .setAlbumTitle(metadata.podcastName ?: item.mediaMetadata.albumTitle?.toString())
                    .setArtworkUri(resolvedArtworkUri)
                    .setExtras(
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

    private fun buildApiFallbackMediaItem(
        item: MediaItem,
        episodeId: String,
        episode: Episode,
    ): MediaItem {
        val podcastName =
            episode.podcastTitle?.takeIf(String::isNotBlank)
                ?: episode.podcastArtist?.takeIf(String::isNotBlank)
                ?: item.mediaMetadata.albumTitle?.toString()?.takeIf(String::isNotBlank)
                ?: item.mediaMetadata.artist?.toString()?.takeIf(String::isNotBlank)
                ?: item.mediaMetadata.subtitle?.toString()?.takeIf(String::isNotBlank)
        android.util.Log.d("BoxCastPlayer", "resolveMediaItem: resolved from API: '${episode.imageUrl}'")
        val resolvedArtworkUri =
            (episode.imageUrl ?: episode.podcastImageUrl)?.let { uriStr ->
                if (uriStr.startsWith("content://")) {
                    Uri.parse(uriStr)
                } else {
                    AutoArtworkRepository.remoteUri(host.asContext(), uriStr)
                }
            } ?: item.mediaMetadata.artworkUri

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
                    .setArtworkUri(resolvedArtworkUri)
                    .setExtras(
                        CastMediaMetadata.extrasWithRemoteUri(
                            existing = item.mediaMetadata.extras,
                            remoteUri = episode.audioUrl,
                        ),
                    ).build(),
            ).build()
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

internal data class AutoMediaSource(
    val playbackUri: String?,
    val castRemoteUri: String?,
    val mimeType: String?,
)

internal data class AutoItemMetadata(
    val artist: String?,
    val albumTitle: String?,
    val subtitle: String?,
)

internal data class AutoPlaybackMetadata(
    val title: String?,
    val podcastName: String?,
    val artworkUri: String?,
)

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

    fun needsItemMetadataFallback(
        existingArtist: String?,
        existingAlbumTitle: String?,
        existingSubtitle: String?,
    ): Boolean =
        existingArtist.isNullOrBlank() ||
            existingAlbumTitle.isNullOrBlank() ||
            existingSubtitle.isNullOrBlank()

    fun resolveItemMetadata(
        existingArtist: String?,
        existingAlbumTitle: String?,
        existingSubtitle: String?,
        fallbackPodcastName: String? = null,
    ): AutoItemMetadata {
        val artist = existingArtist?.takeIf(String::isNotBlank)
        val album = existingAlbumTitle?.takeIf(String::isNotBlank)
        val subtitle = existingSubtitle?.takeIf(String::isNotBlank)

        val fallback = fallbackPodcastName?.takeIf(String::isNotBlank) ?: album ?: artist ?: subtitle
        return AutoItemMetadata(
            artist = artist ?: fallback,
            albumTitle = album ?: fallback,
            subtitle = subtitle ?: fallback,
        )
    }

    fun needsPlaybackMetadataFallback(
        localTitle: String?,
        localPodcastName: String?,
        localArtworkUri: String?,
    ): Boolean =
        localTitle.isNullOrBlank() ||
            localPodcastName.isNullOrBlank() ||
            localArtworkUri.isNullOrBlank()

    fun resolvePlaybackMetadata(
        localTitle: String?,
        localPodcastName: String?,
        localArtworkUri: String?,
        fallbackTitle: String? = null,
        fallbackPodcastName: String? = null,
        fallbackArtworkUri: String? = null,
    ): AutoPlaybackMetadata {
        val title = localTitle?.takeIf(String::isNotBlank)
        val podcastName = localPodcastName?.takeIf(String::isNotBlank)
        val artworkUri = localArtworkUri?.takeIf(String::isNotBlank)

        return AutoPlaybackMetadata(
            title = title ?: fallbackTitle?.takeIf(String::isNotBlank),
            podcastName = podcastName ?: fallbackPodcastName?.takeIf(String::isNotBlank),
            artworkUri = artworkUri ?: fallbackArtworkUri?.takeIf(String::isNotBlank),
        )
    }
}
