package cx.aswin.boxlore.core.playback

import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
internal data class RestoredSessionData(
    val lastSession: ListeningHistoryEntity,
    val episode: Episode,
    val podcast: Podcast,
)

internal object PlaybackSessionRestoreHelper {
    suspend fun resolveRestoredSession(
        targetEpisodeId: String?,
        currentItem: MediaItem?,
        listeningHistoryDao: ListeningHistoryDao,
        podcastRepository: PodcastRepository,
        savedQueue: List<Episode>,
    ): RestoredSessionData? {
        val lastSession =
            resolveSession(
                targetEpisodeId = targetEpisodeId,
                currentItem = currentItem,
                listeningHistoryDao = listeningHistoryDao,
                podcastRepository = podcastRepository,
                savedQueue = savedQueue,
            ) ?: return null

        val queueEpisode = savedQueue.find { it.id == lastSession.episodeId }
        val resolvedPodcastName =
            resolvePodcastName(
                lastSession = lastSession,
                currentItem = currentItem,
                queueEpisode = queueEpisode,
                podcastRepository = podcastRepository,
            )
        val resolvedPodcastId = resolvePodcastId(lastSession, queueEpisode, podcastRepository)
        val resolvedPodcastImageUrl = resolvePodcastImageUrl(lastSession, queueEpisode, podcastRepository)

        enrichIfMissingMetadata(
            lastSession = lastSession,
            resolvedPodcastId = resolvedPodcastId,
            resolvedPodcastImageUrl = resolvedPodcastImageUrl,
            resolvedPodcastName = resolvedPodcastName,
            listeningHistoryDao = listeningHistoryDao,
        )

        val episode =
            buildRestoredEpisode(
                lastSession = lastSession,
                currentItem = currentItem,
                queueEpisode = queueEpisode,
                resolvedPodcastId = resolvedPodcastId,
                resolvedPodcastName = resolvedPodcastName,
                resolvedPodcastImageUrl = resolvedPodcastImageUrl,
            ) ?: return null

        val podcast =
            Podcast(
                id = resolvedPodcastId,
                title = resolvedPodcastName,
                artist = "",
                imageUrl = resolvedPodcastImageUrl ?: "",
                description = null,
                genre = "Podcast",
            )

        val updatedLastSession =
            lastSession.copy(
                podcastName = resolvedPodcastName.takeIf(String::isNotBlank) ?: lastSession.podcastName,
                podcastId = resolvedPodcastId.takeIf(String::isNotBlank) ?: lastSession.podcastId,
                podcastImageUrl = resolvedPodcastImageUrl ?: lastSession.podcastImageUrl,
            )

        return RestoredSessionData(
            lastSession = updatedLastSession,
            episode = episode,
            podcast = podcast,
        )
    }

    private suspend fun resolveSession(
        targetEpisodeId: String?,
        currentItem: MediaItem?,
        listeningHistoryDao: ListeningHistoryDao,
        podcastRepository: PodcastRepository,
        savedQueue: List<Episode>,
    ): ListeningHistoryEntity? {
        if (targetEpisodeId == null) {
            return listeningHistoryDao.getLastPlayedSessionAny()
        }
        val stored = listeningHistoryDao.getHistoryItem(targetEpisodeId)
        if (stored != null) {
            return stored
        }
        return createAndInsertSyntheticSession(
            targetEpisodeId = targetEpisodeId,
            currentItem = currentItem,
            listeningHistoryDao = listeningHistoryDao,
            podcastRepository = podcastRepository,
            savedQueue = savedQueue,
        )
    }

    private suspend fun createAndInsertSyntheticSession(
        targetEpisodeId: String,
        currentItem: MediaItem?,
        listeningHistoryDao: ListeningHistoryDao,
        podcastRepository: PodcastRepository,
        savedQueue: List<Episode>,
    ): ListeningHistoryEntity? {
        val queueEp = savedQueue.find { it.id == targetEpisodeId }
        val baseEp = queueEp ?: runCatching { podcastRepository.getEpisode(targetEpisodeId) }.getOrNull()

        val liveTitle = CastMediaMetadata.queueTitle(currentItem?.mediaMetadata?.title)
        val livePodcast =
            CastMediaMetadata.queueTitle(currentItem?.mediaMetadata?.albumTitle)
                ?: CastMediaMetadata.queueTitle(currentItem?.mediaMetadata?.artist)
                ?: CastMediaMetadata.queueTitle(currentItem?.mediaMetadata?.subtitle)
        val liveAudioUrl = currentItem?.localConfiguration?.uri?.toString()

        val resolvedAudioUrl = baseEp?.audioUrl ?: liveAudioUrl ?: return null
        val resolvedEpisodeTitle = baseEp?.title ?: liveTitle ?: ""
        val resolvedPodcastId = baseEp?.podcastId.orEmpty()
        val resolvedPodcastName =
            baseEp?.podcastTitle?.takeIf(String::isNotBlank)
                ?: baseEp?.podcastArtist?.takeIf(String::isNotBlank)
                ?: livePodcast
                ?: ""
        val resolvedArtwork = baseEp?.imageUrl ?: currentItem?.mediaMetadata?.artworkUri?.toString()
        val resolvedPodcastArtwork = baseEp?.podcastImageUrl
        val durationMs = (baseEp?.duration?.toLong() ?: 0L) * 1_000L

        val syntheticSession =
            ListeningHistoryEntity(
                episodeId = targetEpisodeId,
                podcastId = resolvedPodcastId,
                episodeTitle = resolvedEpisodeTitle,
                episodeImageUrl = resolvedArtwork,
                podcastImageUrl = resolvedPodcastArtwork,
                episodeAudioUrl = resolvedAudioUrl,
                podcastName = resolvedPodcastName,
                progressMs = 0L,
                durationMs = durationMs,
                isCompleted = false,
                isLiked = false,
                lastPlayedAt = System.currentTimeMillis(),
                isDirty = true,
                enclosureType = baseEp?.enclosureType,
                episodeDescription = baseEp?.description ?: "",
            )
        listeningHistoryDao.insertIfAbsent(syntheticSession)
        return syntheticSession
    }

    private suspend fun enrichIfMissingMetadata(
        lastSession: ListeningHistoryEntity,
        resolvedPodcastId: String,
        resolvedPodcastImageUrl: String?,
        resolvedPodcastName: String,
        listeningHistoryDao: ListeningHistoryDao,
    ) {
        if (
            lastSession.podcastName.isBlank() ||
            lastSession.podcastId.isBlank() ||
            lastSession.podcastImageUrl.isNullOrBlank()
        ) {
            listeningHistoryDao.enrichMetadataIfMissing(
                episodeId = lastSession.episodeId,
                podcastId = resolvedPodcastId,
                episodeTitle = lastSession.episodeTitle,
                episodeImageUrl = lastSession.episodeImageUrl,
                podcastImageUrl = resolvedPodcastImageUrl,
                episodeAudioUrl = lastSession.episodeAudioUrl,
                podcastName = resolvedPodcastName,
                durationMs = lastSession.durationMs,
                enclosureType = lastSession.enclosureType,
                episodeDescription = lastSession.episodeDescription,
            )
        }
    }

    private fun buildRestoredEpisode(
        lastSession: ListeningHistoryEntity,
        currentItem: MediaItem?,
        queueEpisode: Episode?,
        resolvedPodcastId: String,
        resolvedPodcastName: String,
        resolvedPodcastImageUrl: String?,
    ): Episode? {
        val baseAudioUrl =
            lastSession.episodeAudioUrl
                ?: currentItem?.localConfiguration?.uri?.toString()
                ?: return null

        val episode =
            Episode(
                id = lastSession.episodeId,
                title = lastSession.episodeTitle,
                description = lastSession.episodeDescription ?: "",
                audioUrl = baseAudioUrl,
                imageUrl = lastSession.episodeImageUrl ?: currentItem?.mediaMetadata?.artworkUri?.toString(),
                podcastImageUrl = resolvedPodcastImageUrl,
                podcastTitle = resolvedPodcastName,
                podcastId = resolvedPodcastId,
                podcastGenre = "Podcast",
                podcastArtist = "",
                duration = (lastSession.durationMs / 1000).toInt(),
                publishedDate = 0L,
                enclosureType = lastSession.enclosureType,
            )

        return if (queueEpisode != null) {
            episode.copy(
                chaptersUrl = queueEpisode.chaptersUrl,
                transcriptUrl = queueEpisode.transcriptUrl,
                persons = queueEpisode.persons,
                transcripts = queueEpisode.transcripts,
                seasonNumber = queueEpisode.seasonNumber,
                episodeNumber = queueEpisode.episodeNumber,
                episodeType = queueEpisode.episodeType,
            )
        } else {
            episode
        }
    }

    private suspend fun resolvePodcastId(
        lastSession: ListeningHistoryEntity,
        queueEpisode: Episode?,
        podcastRepository: PodcastRepository,
    ): String =
        lastSession.podcastId.takeIf(String::isNotBlank)
            ?: queueEpisode?.podcastId?.takeIf(String::isNotBlank)
            ?: runCatching { podcastRepository.getEpisode(lastSession.episodeId) }.getOrNull()?.podcastId
            ?: ""

    private suspend fun resolvePodcastImageUrl(
        lastSession: ListeningHistoryEntity,
        queueEpisode: Episode?,
        podcastRepository: PodcastRepository,
    ): String? =
        lastSession.podcastImageUrl?.takeIf(String::isNotBlank)
            ?: queueEpisode?.podcastImageUrl?.takeIf(String::isNotBlank)
            ?: runCatching { podcastRepository.getEpisode(lastSession.episodeId) }.getOrNull()?.podcastImageUrl

    private suspend fun resolvePodcastName(
        lastSession: ListeningHistoryEntity,
        currentItem: MediaItem?,
        queueEpisode: Episode?,
        podcastRepository: PodcastRepository,
    ): String {
        val existingName = lastSession.podcastName.takeIf(String::isNotBlank)
        if (existingName != null) return existingName

        val liveName =
            CastMediaMetadata.queueTitle(currentItem?.mediaMetadata?.albumTitle)
                ?: CastMediaMetadata.queueTitle(currentItem?.mediaMetadata?.artist)
                ?: CastMediaMetadata.queueTitle(currentItem?.mediaMetadata?.subtitle)
        if (!liveName.isNullOrBlank()) return liveName

        val queueTitle = queueEpisode?.podcastTitle?.takeIf(String::isNotBlank)
        if (queueTitle != null) return queueTitle

        val apiName =
            runCatching { podcastRepository.getEpisode(lastSession.episodeId) }
                .getOrNull()
                ?.let { it.podcastTitle?.takeIf(String::isNotBlank) ?: it.podcastArtist?.takeIf(String::isNotBlank) }

        if (!apiName.isNullOrBlank()) {
            return apiName
        }

        return ""
    }
}
