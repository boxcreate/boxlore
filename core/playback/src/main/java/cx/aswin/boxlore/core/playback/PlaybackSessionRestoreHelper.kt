package cx.aswin.boxlore.core.playback

import androidx.media3.common.MediaItem
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.database.EpisodeListenerStore
import cx.aswin.boxlore.core.database.ListeningHistoryDao
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.model.persistableArtworkUrl
internal data class RestoredSessionData(
    val lastSession: ListeningHistoryEntity,
    val episode: Episode,
    val podcast: Podcast,
)

/**
 * This state showing [restored] with [queue], as app start and the idle cloud handoff put it. The
 * queue comes back without the show it was started from ([PlayerState.queueShow]), so an older
 * queue's show never labels entries of the restored one.
 */
internal fun PlayerState.showingRestoredSession(restored: RestoredSessionData, queue: List<Episode>,): PlayerState = copy(
    currentEpisode = restored.episode,
    currentPodcast = restored.podcast,
    isLiked = restored.lastSession.isLiked,
    queue = queue,
    queueShow = null,
)

internal object PlaybackSessionRestoreHelper {
    /**
     * The session to show for [targetEpisodeId] (the controller's media ID), or the most recently
     * played one without a target.
     *
     * With [listenerStore], the state is the episode's projected state, so an episode stored under
     * several IDs restores its shared progress, like and completion whichever ID is playing; the
     * restored episode keeps the media ID. A missing row is created only when no ID of the episode
     * has one, under the episode's owner ID, so a restore never adds an unplayed row beside the
     * episode's real state (own-episode-IDs P4, plan section 8). Without it (tests), the media
     * ID's own row is read as before.
     */
    @Suppress("LongParameterList")
    suspend fun resolveRestoredSession(
        targetEpisodeId: String?,
        currentItem: MediaItem?,
        listeningHistoryDao: ListeningHistoryDao,
        podcastRepository: PodcastRepository,
        savedQueue: List<Episode>,
        listenerStore: EpisodeListenerStore? = null,
    ): RestoredSessionData? {
        val resolved =
            resolveSession(
                targetEpisodeId = targetEpisodeId,
                currentItem = currentItem,
                listeningHistoryDao = listeningHistoryDao,
                podcastRepository = podcastRepository,
                savedQueue = savedQueue,
                listenerStore = listenerStore,
            ) ?: return null
        val lastSession = resolved.session

        val queueEpisode = savedQueue.find { it.id == lastSession.episodeId }
        val resolvedPodcastName =
            resolvePodcastName(
                lastSession = lastSession,
                currentItem = currentItem,
                queueEpisode = queueEpisode,
                podcastRepository = podcastRepository,
            )
        val resolvedPodcastId = resolvePodcastId(lastSession, queueEpisode, podcastRepository)
        val resolvedPodcastImageUrl = resolvePodcastImageUrl(lastSession, currentItem, queueEpisode, podcastRepository)

        enrichIfMissingMetadata(
            lastSession = lastSession.copy(episodeId = resolved.rowId),
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

    /** The session state, carrying the restored episode's ID, and [rowId], the stored row its metadata repairs go to. */
    private data class ResolvedSession(val session: ListeningHistoryEntity, val rowId: String,)

    @Suppress("LongParameterList")
    private suspend fun resolveSession(
        targetEpisodeId: String?,
        currentItem: MediaItem?,
        listeningHistoryDao: ListeningHistoryDao,
        podcastRepository: PodcastRepository,
        savedQueue: List<Episode>,
        listenerStore: EpisodeListenerStore?,
    ): ResolvedSession? {
        if (targetEpisodeId == null) {
            val latest =
                listenerStore?.lastPlayedSession(unfinishedOnly = false)
                    ?: listeningHistoryDao.getLastPlayedSessionAny()
                    ?: return null
            return ResolvedSession(latest, latest.episodeId)
        }
        val stored = listenerStore?.projectedHistory(targetEpisodeId) ?: listeningHistoryDao.getHistoryItem(targetEpisodeId)
        if (stored != null) {
            return ResolvedSession(stored.copy(episodeId = targetEpisodeId), stored.episodeId)
        }
        val historyId = listenerStore?.historyId(targetEpisodeId) ?: targetEpisodeId
        val synthetic =
            createAndInsertSyntheticSession(
                targetEpisodeId = targetEpisodeId,
                historyId = historyId,
                listenerStore = listenerStore,
                currentItem = currentItem,
                listeningHistoryDao = listeningHistoryDao,
                podcastRepository = podcastRepository,
                savedQueue = savedQueue,
            ) ?: return null
        return ResolvedSession(synthetic.copy(episodeId = targetEpisodeId), historyId)
    }

    @Suppress("LongParameterList")
    private suspend fun createAndInsertSyntheticSession(
        targetEpisodeId: String,
        historyId: String,
        listenerStore: EpisodeListenerStore?,
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
        val resolvedPodcastName = resolveSyntheticPodcastName(baseEp, livePodcast)
        val (resolvedArtwork, resolvedPodcastArtwork) = resolveSyntheticArtwork(baseEp, currentItem)
        val durationMs = (baseEp?.duration?.toLong() ?: 0L) * 1_000L

        val built =
            ListeningHistoryEntity(
                episodeId = historyId,
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
        // A seeded owner row of an episode stored under several IDs carries the owner's show.
        val syntheticSession = listenerStore?.ownerRow(built, targetEpisodeId) ?: built
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
                imageUrl = persistableArtworkUrl(lastSession.episodeImageUrl) ?: PlaybackArtworkMetadata.savedArtworkUrl(currentItem?.mediaMetadata),
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
        currentItem: MediaItem?,
        queueEpisode: Episode?,
        podcastRepository: PodcastRepository,
    ): String? =
        persistableArtworkUrl(lastSession.podcastImageUrl)
            ?: persistableArtworkUrl(queueEpisode?.podcastImageUrl)
            ?: persistableArtworkUrl(runCatching { podcastRepository.getEpisode(lastSession.episodeId) }.getOrNull()?.podcastImageUrl)
            ?: PlaybackArtworkMetadata.savedArtworkUrl(currentItem?.mediaMetadata)

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

    private fun resolveSyntheticPodcastName(baseEp: Episode?, livePodcast: String?): String =
        baseEp?.podcastTitle?.takeIf(String::isNotBlank)
            ?: baseEp?.podcastArtist?.takeIf(String::isNotBlank)
            ?: livePodcast
            ?: ""

    private fun resolveSyntheticArtwork(
        baseEp: Episode?,
        currentItem: MediaItem?,
    ): Pair<String?, String?> {
        val itemArtwork = PlaybackArtworkMetadata.savedArtworkUrl(currentItem?.mediaMetadata)
        val artwork = persistableArtworkUrl(baseEp?.imageUrl) ?: itemArtwork
        val podcastArtwork = persistableArtworkUrl(baseEp?.podcastImageUrl) ?: itemArtwork
        return artwork to podcastArtwork
    }
}
