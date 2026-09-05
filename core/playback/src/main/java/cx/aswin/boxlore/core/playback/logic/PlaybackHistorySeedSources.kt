package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeSupplementItemEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.entities.QueueItem

internal fun QueueItem.toPlaybackHistorySeedSource() = PlaybackHistorySeedSource(
    podcastId = podcastId,
    episodeTitle = title,
    episodeImageUrl = imageUrl,
    podcastImageUrl = podcastImageUrl,
    episodeAudioUrl = audioUrl,
    podcastName = podcastTitle,
    durationMs = duration.toLong() * 1_000L,
    enclosureType = enclosureType,
    episodeDescription = description,
)

internal fun LocalEpisodeEntity.toPlaybackHistorySeedSource(
    podcastName: String? = null,
    podcastImageUrl: String? = null,
) = PlaybackHistorySeedSource(
    podcastId = podcastId,
    episodeTitle = title,
    episodeImageUrl = imageUrl ?: podcastImageUrl,
    podcastImageUrl = podcastImageUrl,
    episodeAudioUrl = audioUrl,
    podcastName = podcastName,
    durationMs = duration.toLong() * 1_000L,
    enclosureType = enclosureType,
    episodeDescription = description,
)

internal fun RssEpisodeEntity.toPlaybackHistorySeedSource(
    podcastName: String? = null,
    podcastImageUrl: String? = null,
) = PlaybackHistorySeedSource(
    podcastId = podcastId,
    episodeTitle = title,
    episodeImageUrl = imageUrl ?: podcastImageUrl,
    podcastImageUrl = podcastImageUrl,
    episodeAudioUrl = audioUrl,
    podcastName = podcastName,
    durationMs = duration.toLong() * 1_000L,
    enclosureType = enclosureType,
    episodeDescription = description,
)

internal fun EpisodeSupplementItemEntity.toPlaybackHistorySeedSource(
    podcastName: String? = null,
    podcastImageUrl: String? = null,
) = PlaybackHistorySeedSource(
    podcastId = podcastId,
    episodeTitle = title,
    episodeImageUrl = imageUrl ?: podcastImageUrl,
    podcastImageUrl = podcastImageUrl,
    episodeAudioUrl = audioUrl,
    podcastName = podcastName,
    durationMs = duration.toLong() * 1_000L,
    enclosureType = enclosureType,
    episodeDescription = description,
)

internal fun cx.aswin.boxlore.core.model.Episode.toPlaybackHistorySeedSource() = PlaybackHistorySeedSource(
    podcastId = podcastId,
    episodeTitle = title,
    episodeImageUrl = imageUrl ?: podcastImageUrl,
    podcastImageUrl = podcastImageUrl,
    episodeAudioUrl = audioUrl,
    podcastName = podcastTitle ?: podcastArtist,
    durationMs = duration.toLong() * 1_000L,
    enclosureType = enclosureType,
    episodeDescription = description,
)

internal fun DownloadedEpisodeEntity.toPlaybackHistorySeedSource() = PlaybackHistorySeedSource(
    podcastId = podcastId,
    episodeTitle = episodeTitle,
    episodeImageUrl = episodeImageUrl,
    podcastImageUrl = podcastImageUrl,
    podcastName = podcastName,
    episodeAudioUrl = localFilePath.takeIf { it.isNotBlank() && it != "CACHED" },
    durationMs = durationMs,
    episodeDescription = episodeDescription,
)
