package cx.aswin.boxlore.core.playback

import android.util.Log
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Saves a timed play to the local listening record (Listening history and Insights) and adds its
 * show to the played shows. Moved out of [PlaybackTelemetrySession] unchanged.
 */
internal class ListeningRecordWriter(private val scope: CoroutineScope, private val database: BoxLoreDatabase,) {
    /** One timed play as [PlaybackTelemetrySession] ends it. */
    data class Play(
        val episodeId: String,
        val podcastId: String?,
        val podcastName: String?,
        val podcastGenre: String?,
        val startedAt: Long,
        val consumedMs: Long,
        val wallMs: Long,
        val completed: Boolean,
        val source: String?,
        val output: String?,
    )

    fun save(play: Play, deviceId: () -> String?,) {
        scope.launch {
            try {
                val installationId = runCatching(deviceId).getOrNull()
                // Older untagged listening belongs to this installation; claim it before adding to it.
                installationId?.let { database.listeningInsightsMaintenance().claimLegacyListening(it) }
                val entity =
                    ListeningSessionRecordLogic.buildSession(
                        ListeningSessionRecordLogic.BuildSessionInput(
                            sessionId =
                                java.util.UUID
                                    .randomUUID()
                                    .toString(),
                            episodeId = play.episodeId,
                            podcastId = play.podcastId.orEmpty(),
                            startedAt = play.startedAt,
                            endedAt = System.currentTimeMillis(),
                            consumedMs = play.consumedMs,
                            completed = play.completed,
                            wallMs = play.wallMs,
                            source = play.source,
                            output = play.output,
                            episodePublishedAt = episodeReleaseDateMs(play.episodeId),
                            deviceId = installationId,
                        ),
                    ) ?: return@launch
                ListeningSessionRecordLogic.persistSessionAndRollUp(
                    sessions = database.listeningSessionDao(),
                    maintenance = database.listeningInsightsMaintenance(),
                    session = entity,
                )
                recordPlayedShow(entity, play.podcastName, play.podcastGenre)
            } catch (e: Exception) {
                Log.e("BoxLorePlaybackService", "Failed to persist listening session", e)
            }
        }
    }

    /** The playing episode's release date from the queue, else the stored catalog (epoch ms). */
    private suspend fun episodeReleaseDateMs(episodeId: String): Long? = runCatching {
        val stored =
            database.queueDao().getQueueItemByEpisodeId(episodeId)?.pubDate?.takeIf { it > 0L }
                ?: database.localEpisodeCatalogDao().getEpisode(episodeId)?.publishedDate?.takeIf { it > 0L }
                ?: database.rssEpisodeDao().getEpisode(episodeId)?.publishedDate
        ListeningSessionRecordLogic.releaseDateMs(stored)
    }.getOrNull()

    /** Adds the show of a recorded play to the played shows, with the art its history row holds. */
    private suspend fun recordPlayedShow(session: ListeningSessionEntity, podcastName: String?, genre: String?,) {
        if (session.podcastId.isBlank()) return
        val history = database.listeningHistoryDao().getHistoryItem(database.episodeListenerStore().historyId(session.episodeId))
        database.playedShowDao().recordPlay(
            podcastId = session.podcastId,
            podcastName = podcastName ?: history?.podcastName.orEmpty(),
            imageUrl = history?.podcastImageUrl ?: history?.episodeImageUrl,
            genre = genre,
            playedAt = session.startedAt,
        )
    }
}
