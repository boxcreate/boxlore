package cx.aswin.boxlore.core.playback.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.DownloadedEpisodeEntity
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Podcast
import cx.aswin.boxlore.core.playback.AutoCollageFolderLogic
import cx.aswin.boxlore.core.playback.AutoCollagePrewarmPolicy
import cx.aswin.boxlore.core.playback.MixtapeEngine
import cx.aswin.boxlore.core.playback.QueueRepository
import cx.aswin.boxlore.core.playback.SmartQueueSources
import cx.aswin.boxlore.core.playback.service.auto.AutoBrowseContract
import cx.aswin.boxlore.core.ranking.AdaptiveCandidateScorer
import cx.aswin.boxlore.core.ranking.RankingSurface
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

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
    private val announced = CollageAnnouncements<Uri>()
    private val lastPrewarmAtMs = AtomicLong(0L)

    suspend fun prewarm(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!AutoCollagePrewarmPolicy.shouldRun(force, lastPrewarmAtMs.get(), now)) {
            return
        }
        mutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (!AutoCollagePrewarmPolicy.shouldRun(force, lastPrewarmAtMs.get(), lockedNow)) {
                return
            }
            runPrewarm()
            lastPrewarmAtMs.set(System.currentTimeMillis())
        }
    }

    private suspend fun runPrewarm() {
        try {
            val snapshot = loadSnapshot()
            val mixtape = resolveMixtape(snapshot)
            val folders = snapshot.buildFolderInputs(mixtape)
            val uris =
                AutoCollageGenerator.generateAllCollages(
                    context = context,
                    folderImages = folders.mapValues { AutoCollageFolderLogic.imagesOf(it.value) },
                    folderContentKeys = folders.mapValues { AutoCollageFolderLogic.keysOf(it.value) },
                )
            onCollagesReady(uris)
            if (announced.isNews(uris)) notifyBrowseTree()
        } catch (error: Exception) {
            Log.w("AutoBrowse", "Unable to prewarm Android Auto artwork", error)
        }
    }

    private suspend fun loadSnapshot(): PrewarmSnapshot {
        // Each episode once, with its projected state (own-episode-IDs P4, plan section 8).
        val listenerStore = database.episodeListenerStore()
        val history = listenerStore.projectRows(database.listeningHistoryDao().getRecentHistoryList(300))
        val resumeItems = listenerStore.projectRows(database.listeningHistoryDao().getResumeItemsList()) { !it.isCompleted && it.progressMs > 0 }
        val subscriptions = database.podcastDao().getSubscribedPodcastsList()
        val liked = listenerStore.projectRows(database.listeningHistoryDao().getLikedEpisodesList(8)) { it.isLiked }
        val downloads = database.downloadedEpisodeDao().getCompletedDownloads(8)
        val queue = queueRepository.getQueueEpisodeSnapshot()
        return PrewarmSnapshot(
            history = history,
            resumeItems = resumeItems,
            liked = liked,
            subscriptions = subscriptions,
            downloads = downloads,
            queue = queue,
        )
    }

    private suspend fun resolveMixtape(snapshot: PrewarmSnapshot): MixtapeEngine.Result {
        var mixtape =
            MixtapeEngine.build(
                subscriptions = snapshot.subscriptions.map(toAutoPodcast),
                history = snapshot.history,
                adaptiveRanking =
                MixtapeEngine.AdaptiveRanking(
                    scorer = adaptiveCandidateScorer,
                    surface = RankingSurface.ANDROID_AUTO,
                ),
            )
        if (mixtape.episodes.size >= 3) return mixtape
        val recommendations =
            runCatching {
                withTimeout(6_000L) {
                    smartQueueSources.getPersonalizedRecommendations(
                        history = smartQueueSources.getHistoryForRecommendations(25),
                        interests = smartQueueSources.getInterests(),
                        country = smartQueueSources.getRegion(),
                        subscribedPodcastIds = snapshot.subscriptions.map { it.podcastId },
                        subscribedGenres =
                        snapshot.subscriptions.mapNotNull { it.genre }.distinct(),
                    )
                }
            }.getOrDefault(emptyList())
        return MixtapeEngine.build(
            subscriptions = snapshot.subscriptions.map(toAutoPodcast),
            history = snapshot.history,
            recommendations = recommendations,
            adaptiveRanking =
            MixtapeEngine.AdaptiveRanking(
                scorer = adaptiveCandidateScorer,
                surface = RankingSurface.ANDROID_AUTO,
            ),
        )
    }

    /**
     * Collages are the artwork of Home's blocks, so Home is the only screen to redraw, and only
     * when one of them changed: a reload sends the car's Home back to the top.
     */
    private fun notifyBrowseTree() {
        mediaSessionProvider()?.notifyChildrenChanged(AutoBrowseContract.HOME_ID, HOME_BLOCK_COUNT, null)
    }

    /** What the collages are drawn from; [buildFolderInputs] picks each folder's covers. */
    @androidx.annotation.VisibleForTesting
    internal data class PrewarmSnapshot(
        val history: List<ListeningHistoryEntity>,
        val resumeItems: List<ListeningHistoryEntity>,
        val liked: List<ListeningHistoryEntity>,
        val subscriptions: List<PodcastEntity>,
        val downloads: List<DownloadedEpisodeEntity>,
        val queue: List<Episode>,
    ) {
        fun buildFolderInputs(mixtape: MixtapeEngine.Result): Map<String, List<AutoCollageFolderLogic.ArtPair>> {
            val historyPairs =
                history.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(
                        it.episodeId,
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    )
                }
            val resumePairs =
                resumeItems.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(
                        it.episodeId,
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    )
                }
            val likedPairs =
                liked.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(
                        it.episodeId,
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    )
                }
            val subscriptionPairs =
                subscriptions.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(it.podcastId, it.imageUrl)
                }
            val downloadPairs =
                downloads.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(
                        it.episodeId,
                        it.episodeImageUrl ?: it.podcastImageUrl,
                    )
                }
            val queuePairs =
                queue.mapNotNull {
                    AutoCollageFolderLogic.pairOrNull(it.id, it.imageUrl ?: it.podcastImageUrl)
                }
            val newEpisodePairs =
                subscriptions.mapNotNull { podcast ->
                    val episode = podcast.latestEpisode
                    AutoCollageFolderLogic.pairOrNull(
                        episode?.id ?: podcast.podcastId,
                        episode?.imageUrl ?: podcast.imageUrl,
                    )
                }
            val mixtapePairs =
                mixtape.podcasts.mapNotNull { podcast ->
                    val episode = podcast.latestEpisode ?: return@mapNotNull null
                    AutoCollageFolderLogic.pairOrNull(
                        episode.id,
                        episode.imageUrl ?: episode.podcastImageUrl ?: podcast.imageUrl,
                    )
                }
            val drivePairs =
                AutoCollageFolderLogic.takeAligned(queuePairs + subscriptionPairs)
            return mapOf(
                AutoBrowseContract.HOME_ID to
                    AutoCollageFolderLogic.takeAligned(historyPairs + newEpisodePairs),
                AutoBrowseContract.LIBRARY_ID to AutoCollageFolderLogic.takeAligned(subscriptionPairs),
                AutoBrowseContract.DOWNLOADS_ID to AutoCollageFolderLogic.takeAligned(downloadPairs),
                AutoBrowseContract.DISCOVER_ID to
                    AutoCollageFolderLogic.takeAligned(subscriptionPairs.asReversed()),
                AutoBrowseContract.HOME_CONTINUE_ID to AutoCollageFolderLogic.takeAligned(resumePairs),
                AutoBrowseContract.LIBRARY_LIKED_ID to AutoCollageFolderLogic.takeAligned(likedPairs),
                AutoBrowseContract.LIBRARY_HISTORY_ID to AutoCollageFolderLogic.takeAligned(historyPairs),
                AutoBrowseContract.HOME_QUEUE_ID to AutoCollageFolderLogic.takeAligned((resumePairs + queuePairs).distinctBy { it.contentKey }),
                AutoBrowseContract.HOME_NEW_EPISODES_ID to
                    AutoCollageFolderLogic.takeAligned(newEpisodePairs),
                AutoBrowseContract.HOME_DRIVE_MIX_ID to AutoCollageFolderLogic.takeAligned(mixtapePairs),
                AutoBrowseContract.DISCOVER_DRIVE_PICKS_ID to drivePairs,
                AutoBrowseContract.DISCOVER_TIME_PICKS_ID to emptyList(),
                AutoBrowseContract.DISCOVER_GENRES_ID to emptyList(),
            )
        }
    }

    private companion object {
        /** Media3 wants an item count with a change notice; the car reloads Home whatever the number. */
        const val HOME_BLOCK_COUNT = 10
    }
}

/**
 * Remembers the collage links the car was last told about. A link carries its collage's content
 * signature, so the same links mean the same artwork and nothing worth reloading Home for.
 */
internal class CollageAnnouncements<T> {
    private var last: Map<String, T>? = null

    /** True the first time, and whenever [links] differ from the ones last announced. */
    @Synchronized
    fun isNews(links: Map<String, T>): Boolean {
        if (links == last) return false
        last = links
        return true
    }
}
