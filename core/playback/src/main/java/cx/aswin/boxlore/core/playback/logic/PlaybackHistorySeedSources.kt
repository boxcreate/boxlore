package cx.aswin.boxlore.core.playback

import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.EpisodeSupplementItemEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.entities.QueueItem

internal fun QueueItem.toPlaybackHistorySeedSource() =
    PlaybackHistorySeedSource(
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

internal fun LocalEpisodeEntity.toPlaybackHistorySeedSource() =
    PlaybackHistorySeedSource(
        podcastId = podcastId,
        episodeTitle = title,
        episodeImageUrl = imageUrl,
        episodeAudioUrl = audioUrl,
        durationMs = duration.toLong() * 1_000L,
        enclosureType = enclosureType,
        episodeDescription = description,
    )

internal fun RssEpisodeEntity.toPlaybackHistorySeedSource() =
    PlaybackHistorySeedSource(
        podcastId = podcastId,
        episodeTitle = title,
        episodeImageUrl = imageUrl,
        episodeAudioUrl = audioUrl,
        durationMs = duration.toLong() * 1_000L,
        enclosureType = enclosureType,
        episodeDescription = description,
    )

internal fun EpisodeSupplementItemEntity.toPlaybackHistorySeedSource() =
    PlaybackHistorySeedSource(
        podcastId = podcastId,
        episodeTitle = title,
        episodeImageUrl = imageUrl,
        episodeAudioUrl = audioUrl,
        durationMs = duration.toLong() * 1_000L,
        enclosureType = enclosureType,
        episodeDescription = description,
    )

internal fun DownloadedEpisodeEntity.toPlaybackHistorySeedSource() =
    PlaybackHistorySeedSource(
        podcastId = podcastId,
        episodeTitle = episodeTitle,
        episodeImageUrl = episodeImageUrl,
        podcastImageUrl = podcastImageUrl,
        podcastName = podcastName,
        durationMs = durationMs,
        episodeDescription = episodeDescription,
    )
