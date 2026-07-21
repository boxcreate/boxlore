package cx.aswin.boxlore.core.playback.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.SmartQueueSources
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseContract
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.RankingSurface
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/**
 * Prewarms Android Auto folder collage artwork for [BoxLorePlaybackService].
 */
internal class AutoCollagePrewarmer(
    private val context: Context,
    private val database: BoxLoreDatabase,
    private val queueRepository: QueueRepository,
    private val smartQueueSources: SmartQueueSources,
    private val adaptiveCandidateScorer: AdaptiveCandidateScorer,
    private val toAutoPodcast: (PodcastEntity) -> Podcast,
    private val mediaSessionProvider: () -> MediaLibrarySession?,
    private val onCollagesReady: (Map<String, Uri>) -> Unit,
) {
    private val mutex = Mutex()
    private val lastPrewarmAtMs = AtomicLong(0L)

    suspend fun prewarm(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastPrewarmAtMs.get() < MIN_REFRESH_INTERVAL_MS) {
            return
        }
        mutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!force && lockedNow - lastPrewarmAtMs.get() < MIN_REFRESH_INTERVAL_MS) {
                return
            }
            runPrewarm()
            lastPrewarmAtMs.set(System.currentTimeMillis())
        }
    }

    private suspend fun runPrewarm() {
        try {
            val history = database.listeningHistoryDao().getRecentHistoryList(300)
            val resumeItems = database.listeningHistoryDao().getResumeItemsList()
            val subscriptions = database.podcastDao().getSubscribedPodcastsList()
            val downloads = database.downloadedEpisodeDao().getCompletedDownloads(8)
            val queue = queueRepository.getQueueSnapshot()
            val historyImages =
                history.mapNotNull {
                    it.episodeImageUrl ?: it.podcastImageUrl
                }
            val resumeImages =
                resumeItems.mapNotNull {
                    it.episodeImageUrl ?: it.podcastImageUrl
                }
            val subscriptionImages = subscriptions.mapNotNull { it.imageUrl }
            val downloadImages =
                downloads.mapNotNull {
                    it.episodeImageUrl ?: it.podcastImageUrl
                }
            val queueImages = queue.mapNotNull { it.imageUrl ?: it.podcastImageUrl }
            val newEpisodeImages =
                subscriptions.mapNotNull {
                    it.latestEpisode?.imageUrl ?: it.imageUrl
                }
            var mixtape =
                MixtapeEngine.build(
                    subscriptions = subscriptions.map(toAutoPodcast),
                    history = history,
                    adaptiveRanking =
                        MixtapeEngine.AdaptiveRanking(
                            scorer = adaptiveCandidateScorer,
                            surface = RankingSurface.ANDROID_AUTO,
                        ),
                )
            if (mixtape.episodes.size < 3) {
                val recommendations =
                    runCatching {
                        withTimeout(6_000L) {
                            smartQueueSources.getPersonalizedRecommendations(
                                history = smartQueueSources.getHistoryForRecommendations(25),
                                interests = smartQueueSources.getInterests(),
                                country = smartQueueSources.getRegion(),
                                subscribedPodcastIds = subscriptions.map { it.podcastId },
                                subscribedGenres = subscriptions.mapNotNull { it.genre }.distinct(),
                            )
                        }
                    }.getOrDefault(emptyList())
                mixtape =
                    MixtapeEngine.build(
                        subscriptions = subscriptions.map(toAutoPodcast),
                        history = history,
                        recommendations = recommendations,
                        adaptiveRanking =
                            MixtapeEngine.AdaptiveRanking(
                                scorer = adaptiveCandidateScorer,
                                surface = RankingSurface.ANDROID_AUTO,
                            ),
                    )
            }
            val mixtapeImages =
                mixtape.podcasts.mapNotNull { podcast ->
                    podcast.latestEpisode?.let { episode ->
                        episode.imageUrl ?: episode.podcastImageUrl ?: podcast.imageUrl
                    }
                }
            val newEpisodeKeys =
                subscriptions.mapNotNull { podcast ->
                    podcast.latestEpisode?.id ?: podcast.podcastId.takeIf {
                        podcast.latestEpisode != null || !podcast.imageUrl.isNullOrBlank()
                    }
                }
            val uris =
                AutoCollageGenerator.generateAllCollages(
                    context = context,
                    folderImages =
                        mapOf(
                            AutoBrowseContract.HOME_ID to (historyImages + newEpisodeImages).take(4),
                            AutoBrowseContract.LIBRARY_ID to subscriptionImages.take(4),
                            AutoBrowseContract.DOWNLOADS_ID to downloadImages.take(4),
                            AutoBrowseContract.DISCOVER_ID to subscriptionImages.asReversed().take(4),
                            AutoBrowseContract.HOME_CONTINUE_ID to resumeImages.take(4),
                            AutoBrowseContract.HOME_QUEUE_ID to queueImages.take(4),
                            AutoBrowseContract.HOME_NEW_EPISODES_ID to newEpisodeImages.take(4),
                            AutoBrowseContract.HOME_DRIVE_MIX_ID to mixtapeImages.take(4),
                            AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID to
                                (queueImages + subscriptionImages).take(4),
                            AutoBrowseContract.DISCOVER_TIME_PICKS_ID to emptyList(),
                            AutoBrowseContract.DISCOVER_GENRES_ID to emptyList(),
                        ),
                    folderContentKeys =
                        mapOf(
                            AutoBrowseContract.HOME_ID to
                                history.take(4).map { it.episodeId },
                            AutoBrowseContract.LIBRARY_ID to
                                subscriptions.take(4).map { it.podcastId },
                            AutoBrowseContract.DOWNLOADS_ID to
                                downloads.map { it.episodeId },
                            AutoBrowseContract.DISCOVER_ID to
                                subscriptions.asReversed().take(4).map { it.podcastId },
                            AutoBrowseContract.HOME_CONTINUE_ID to
                                resumeItems.map { it.episodeId },
                            AutoBrowseContract.HOME_QUEUE_ID to
                                queue.map { it.id },
                            AutoBrowseContract.HOME_NEW_EPISODES_ID to
                                newEpisodeKeys.take(4),
                            AutoBrowseContract.HOME_DRIVE_MIX_ID to
                                mixtape.episodes.map { it.id },
                            AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID to
                                (
                                    queue.map { it.id } +
                                        subscriptions.map { it.podcastId }
                                ).take(4),
                            AutoBrowseContract.DISCOVER_TIME_PICKS_ID to
                                listOf(AutoBrowseContract.DISCOVER_TIME_PICKS_ID),
                            AutoBrowseContract.DISCOVER_GENRES_ID to
                                listOf(AutoBrowseContract.DISCOVER_GENRES_ID),
                        ),
                )
            onCollagesReady(uris)
            val session = mediaSessionProvider()
            session?.notifyChildrenChanged(AutoBrowseContract.ROOT_ID, 4, null)
            session?.notifyChildrenChanged(AutoBrowseContract.HOME_ID, 3, null)
            session?.notifyChildrenChanged(
                AutoBrowseContract.LIBRARY_ID,
                subscriptions.size + 2,
                null,
            )
            session?.notifyChildrenChanged(AutoBrowseContract.DISCOVER_ID, 3, null)
            session?.notifyChildrenChanged(AutoBrowseContract.HOME_CONTINUE_ID, resumeItems.size, null)
        } catch (error: Exception) {
            Log.w("AutoBrowse", "Unable to prewarm Android Auto artwork", error)
        }
    }

    companion object {
        private const val MIN_REFRESH_INTERVAL_MS = 30_000L
    }
}
