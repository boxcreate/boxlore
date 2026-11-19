package cx.aswin.boxlore.feature.library.logic

import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.EpisodeStatus
import cx.aswin.boxlore.core.model.Podcast

/** The played state of each show's latest episode in Subscriptions and New Episodes. */
internal object LibraryEpisodeStatusLogic {
    /**
     * [podcasts] with their latest episode's status: completed when its ID is in [completedIds]
     * (every completed episode, so a long or freshly marked history can't hide one), in progress
     * with its share played when [inProgressById] holds it (every started episode), else unplayed.
     */
    fun withLatestEpisodeStatus(
        podcasts: List<Podcast>,
        inProgressById: Map<String, ListeningHistoryEntity>,
        completedIds: Set<String>,
    ): List<Podcast> = podcasts.map { podcast ->
        val episode = podcast.latestEpisode ?: return@map podcast
        val started = inProgressById[episode.id]?.takeIf { !it.isCompleted && it.progressMs > 0L }
        when {
            episode.id in completedIds -> podcast.copy(resumeProgress = 1f, episodeStatus = EpisodeStatus.COMPLETED)
            started != null -> {
                val progress = if (started.durationMs > 0) (started.progressMs.toFloat() / started.durationMs).coerceIn(0f, 1f) else 0f
                podcast.copy(resumeProgress = progress, episodeStatus = EpisodeStatus.IN_PROGRESS)
            }
            else -> podcast.copy(episodeStatus = EpisodeStatus.UNPLAYED)
        }
    }
}
