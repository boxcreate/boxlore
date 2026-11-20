package cx.aswin.boxlore.core.playback.service.auto

import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaConstants
import cx.aswin.boxlore.core.catalog.R
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.EpisodeMediaCacheKey
import cx.aswin.boxlore.core.model.text.LocaleFormats
import cx.aswin.boxlore.core.playback.PlaybackQueueContext
import java.util.Locale

internal data class AutoPlayableSpec(
    val mediaId: String,
    val title: CharSequence,
    val podcastTitle: CharSequence?,
    val source: String,
    val subtitle: CharSequence? = podcastTitle,
    val artworkUri: Uri? = null,
    val uri: String? = null,
    val durationMs: Long? = null,
    val groupTitle: String? = null,
    val progress: Double? = null,
    val isCompleted: Boolean = false,
    val isDownloaded: Boolean = false,
    val customCacheKey: String? = null,
    val supportedCommands: List<String>? = null,
)

/** How an episode's row is shown, beyond the episode itself. */
internal data class AutoEpisodeRow(
    val groupTitle: String? = null,
    val mediaIdPrefix: String = "episode:",
    val isDownloaded: Boolean = false,
    val subtitle: String? = null,
    val progress: Double? = null,
    val isCompleted: Boolean = false,
)

/**
 * This item as the player shows it: under the title goes the show's name, not the list row's
 * second line ("Show · 36 min", "35 min left"), which the player's own progress bar already says.
 */
internal fun MediaItem.forPlayback(): MediaItem =
    buildUpon().setMediaMetadata(mediaMetadata.buildUpon().setSubtitle(mediaMetadata.artist).build()).build()

@OptIn(UnstableApi::class)
internal object AutoMediaItemFactory {
    private val episodeCommands =
        listOf(
            AutoBrowseContract.COMMAND_TOGGLE_LIKE,
            AutoBrowseContract.COMMAND_ADD_TO_QUEUE,
            AutoBrowseContract.COMMAND_MARK_COMPLETE,
        )

    fun browsable(
        id: String,
        title: CharSequence,
        subtitle: CharSequence? = null,
        artworkUri: Uri? = null,
        mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
        childStyleExtras: Bundle = AutoBrowseContract.listChildrenExtras(),
        singleItemStyle: Int? = null,
    ): MediaItem {
        val extras =
            if (singleItemStyle == null) {
                childStyleExtras
            } else {
                AutoBrowseContract.mergeExtras(
                    childStyleExtras,
                    Bundle().apply {
                        putInt(
                            MediaConstants.EXTRAS_KEY_CONTENT_STYLE_SINGLE_ITEM,
                            singleItemStyle,
                        )
                    },
                )
            }
        return MediaItem
            .Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    // With a display title set, the car shows the subtitle as the second line; without
                    // one it shows the artist there and the subtitle is never seen.
                    .setDisplayTitle(title)
                    .setSubtitle(subtitle)
                    .setArtist(subtitle)
                    .setArtworkUri(artworkUri)
                    .setIsPlayable(false)
                    .setIsBrowsable(true)
                    .setMediaType(mediaType)
                    .setExtras(extras)
                    .build(),
            ).build()
    }

    fun playable(spec: AutoPlayableSpec): MediaItem {
        val extras =
            AutoBrowseContract.itemExtras(
                source = spec.source,
                groupTitle = spec.groupTitle,
                progress = spec.progress,
                isCompleted = spec.isCompleted,
                downloadStatus =
                if (spec.isDownloaded) {
                    MediaConstants.EXTRAS_VALUE_STATUS_DOWNLOADED
                } else {
                    null
                },
            )
        return MediaItem
            .Builder()
            .setMediaId(spec.mediaId)
            .apply {
                if (!spec.uri.isNullOrBlank() && spec.uri != "CACHED") setUri(spec.uri)
                if (!spec.customCacheKey.isNullOrBlank()) {
                    setCustomCacheKey(spec.customCacheKey)
                }
            }.setMediaMetadata(
                MediaMetadata
                    .Builder()
                    .setTitle(spec.title)
                    // With a display title set, the car shows the subtitle ("Today · 28 min", "35 min
                    // left") as the second line; without one it shows the artist there instead.
                    .setDisplayTitle(spec.title)
                    .setSubtitle(spec.subtitle)
                    .setArtist(spec.podcastTitle)
                    .setAlbumTitle(spec.podcastTitle)
                    .setArtworkUri(spec.artworkUri)
                    .apply { spec.durationMs?.takeIf { it > 0 }?.let(::setDurationMs) }
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PODCAST_EPISODE)
                    .setSupportedCommands(spec.supportedCommands ?: episodeCommands)
                    .setExtras(extras)
                    .build(),
            ).build()
    }

    fun fromHistory(
        history: ListeningHistoryEntity,
        source: String,
        artworkUri: Uri?,
        subtitle: String,
        groupTitle: String?,
        title: CharSequence = history.episodeTitle,
        isDownloaded: Boolean = false,
    ): MediaItem {
        val progress =
            if (history.durationMs > 0) {
                history.progressMs.toDouble() / history.durationMs.toDouble()
            } else {
                0.0
            }
        return playable(
            AutoPlayableSpec(
                mediaId = "episode:${history.episodeId}",
                title = title,
                podcastTitle = history.podcastName,
                subtitle = subtitle,
                artworkUri = artworkUri,
                uri = history.episodeAudioUrl,
                durationMs = history.durationMs,
                source = source,
                groupTitle = groupTitle,
                progress = progress,
                isCompleted = history.isCompleted,
                isDownloaded = isDownloaded,
                customCacheKey =
                EpisodeMediaCacheKey.of(history.episodeId, history.episodeAudioUrl),
            ),
        )
    }

    fun fromEpisode(
        resources: Resources,
        episode: Episode,
        source: String,
        artworkUri: Uri?,
        podcastTitle: String? = episode.podcastTitle,
        row: AutoEpisodeRow = AutoEpisodeRow(),
    ): MediaItem = playable(
        AutoPlayableSpec(
            mediaId = "${row.mediaIdPrefix}${episode.id}",
            title = episode.title,
            podcastTitle = podcastTitle,
            subtitle = row.subtitle ?: buildDurationSubtitle(resources, podcastTitle, episode.duration.toLong() * 1_000L),
            artworkUri = artworkUri,
            uri = episode.audioUrl,
            durationMs = episode.duration.toLong() * 1_000L,
            source = source,
            groupTitle = row.groupTitle,
            progress = row.progress,
            isCompleted = row.isCompleted,
            isDownloaded = row.isDownloaded,
            customCacheKey = EpisodeMediaCacheKey.of(episode.id, episode.audioUrl),
        ),
    ).let { item ->
        item.buildUpon().setMediaMetadata(
            item.mediaMetadata.buildUpon()
                .setExtras(PlaybackQueueContext.extras(episode, item.mediaMetadata.extras))
                .build(),
        ).build()
    }

    fun fromDownload(resources: Resources, download: DownloadedEpisodeEntity, artworkUri: Uri?, uri: String?, groupTitle: String?,): MediaItem = playable(
        AutoPlayableSpec(
            mediaId = "episode:${download.episodeId}",
            title = download.episodeTitle,
            podcastTitle = download.podcastName,
            subtitle = buildDurationSubtitle(resources, download.podcastName, download.durationMs),
            artworkUri = artworkUri,
            uri = uri,
            durationMs = download.durationMs,
            source = AutoBrowseContract.SOURCE_DOWNLOADS,
            groupTitle = groupTitle,
            isDownloaded = true,
            customCacheKey = EpisodeMediaCacheKey.of(download.episodeId, uri),
        ),
    )

    fun buildDurationSubtitle(resources: Resources, podcastTitle: String?, durationMs: Long,): String {
        val duration = formatDuration(resources, durationMs)
        return listOfNotNull(
            podcastTitle?.takeIf(String::isNotBlank),
            duration.takeIf(String::isNotBlank),
        ).joinToString(" · ")
    }

    /** Joins the parts of a row's second line, leaving out blank ones. */
    fun joinSubtitle(vararg parts: String?): String = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

    /** "35 min left", "1 hr, 5 min left" or "Almost done" for an episode that has been started, in the car's language. */
    fun formatTimeLeft(resources: Resources, progressMs: Long, durationMs: Long): String {
        val remainingMin = (durationMs - progressMs).coerceAtLeast(0) / MS_PER_MINUTE
        if (remainingMin <= 0) return resources.getString(R.string.auto_almost_done)
        val length = LocaleFormats.duration(remainingMin * SECONDS_PER_MINUTE, resources.locale())
        return resources.getString(R.string.auto_time_left, length)
    }

    /** An episode's length: "35 min", "1 hr, 5 min", "< 1 min", or blank when unknown. */
    fun formatDuration(resources: Resources, durationMs: Long): String {
        if (durationMs <= 0) return ""
        val totalMinutes = durationMs / MS_PER_MINUTE
        if (totalMinutes <= 0) return resources.getString(R.string.auto_duration_under_minute)
        return LocaleFormats.duration(totalMinutes * SECONDS_PER_MINUTE, resources.locale())
    }

    private fun Resources.locale(): Locale = configuration.locales.get(0) ?: Locale.getDefault()

    private const val MS_PER_MINUTE = 60_000L
    private const val SECONDS_PER_MINUTE = 60L
}
