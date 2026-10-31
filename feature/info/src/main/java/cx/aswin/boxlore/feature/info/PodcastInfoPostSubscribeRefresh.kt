package cx.aswin.boxlore.feature.info

import android.util.Log
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.TrackedPodcastRtdbLogic
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.TAG
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.pageSizeFor
import cx.aswin.boxlore.feature.info.PodcastInfoPageState.Companion.sortParamFor
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPostSubscribeLogic
import cx.aswin.boxlore.feature.info.logic.PodcastInfoPullRefreshLogic
import cx.aswin.boxlore.feature.info.logic.PostSubscribeRefreshes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

/**
 * The show's catalog refresh after following it from the show page. The application-scoped
 * [SubscriptionForegroundSync] reads the publisher feed, so leaving the page doesn't cancel it;
 * while it runs the page body shows its status line ([PodcastInfoPostSubscribeLogic]), and the
 * page remounts its first page when it ends, if the page still shows that followed show.
 */
internal class PodcastInfoPostSubscribeRefresh(
    private val pageState: PodcastInfoPageState,
    private val repository: PodcastRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val subscriptionForegroundSync: SubscriptionForegroundSync?,
    private val supplementSupport: PodcastInfoSupplementSupport,
    private val postSubscribeRefreshes: PostSubscribeRefreshes,
) {
    private val uiState = pageState.uiState

    suspend fun refreshTipAfterSubscribe() {
        try {
            val latest = uiState.value as? PodcastInfoUiState.Success ?: return
            if (latest.podcast.isRss) return
            val targetPodcastId = latest.podcast.id
            // The body's status line shows the refresh; the top-bar pill no longer spins for it.
            PodcastInfoPostSubscribeLogic.withStatusLine(uiState, targetPodcastId, { pageState.currentPodcastId }, postSubscribeRefreshes) {
                val sync = subscriptionForegroundSync
                if (sync != null) {
                    coroutineScope {
                        val finished =
                            async {
                                sync.catalogIngestFinished.first { it == targetPodcastId }
                            }
                        sync.requestCatalogIngest(targetPodcastId)
                        finished.await()
                    }
                    applyPostSubscribeCatalogUi(targetPodcastId)
                } else {
                    refreshLocalCatalogAfterSubscribeFallback(latest, targetPodcastId)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Post-subscribe catalog refresh failed", e)
        }
    }

    private suspend fun refreshLocalCatalogAfterSubscribeFallback(
        latest: PodcastInfoUiState.Success,
        targetPodcastId: String,
    ) {
        val catalog = repository.localEpisodeCatalog
        val entity = subscriptionRepository.getPodcastEntity(targetPodcastId)
        val feedUrl =
            TrackedPodcastRtdbLogic.httpsFeedUrl(
                entity?.feedUrl ?: latest.podcast.feedUrl,
            )
        if (catalog == null || feedUrl == null) {
            syncPiTipAfterSubscribe(targetPodcastId)
            applyPostSubscribeCatalogUi(targetPodcastId)
            return
        }
        val meta =
            LocalEpisodeCatalogPort.PodcastMeta(
                title = latest.podcast.title,
                imageUrl = latest.podcast.imageUrl,
                genre = latest.podcast.genre,
                artist = latest.podcast.artist,
            )
        val outcome =
            catalog.refresh(
                LocalEpisodeCatalogPort.RefreshRequest(
                    podcastIndexId = targetPodcastId,
                    feedUrl = feedUrl,
                    meta = meta,
                ),
            )
        val tip = PodcastInfoPullRefreshLogic.extractTip(outcome)
        tip?.let { episode ->
            subscriptionRepository.updateLatestEpisode(
                podcastId = targetPodcastId,
                episode = episode,
                markAsNew = false,
                publisherFeedAuthoritative = true,
            )
        }
        applyPostSubscribeCatalogUi(targetPodcastId)
        if (tip == null) {
            syncPiTipAfterSubscribe(targetPodcastId)
        }
    }

    private suspend fun applyPostSubscribeCatalogUi(targetPodcastId: String) {
        val shownState = uiState.value as? PodcastInfoUiState.Success ?: return
        if (!PodcastInfoPullRefreshLogic.shouldApply(pageState.currentPodcastId, targetPodcastId) ||
            shownState.podcast.id != targetPodcastId ||
            !shownState.isSubscribed
        ) {
            return
        }
        val page =
            repository.getShowPageEpisodes(
                feedId = targetPodcastId,
                limit = pageSizeFor(shownState.currentSort),
                offset = 0,
                sort = sortParamFor(shownState.currentSort),
            )
        if (!PodcastInfoPullRefreshLogic.shouldApply(pageState.currentPodcastId, targetPodcastId)) {
            return
        }
        pageState.currentOffset = page.sourceCount
        uiState.value =
            supplementSupport.remountWithSupplements(
                state = shownState,
                piEpisodes = page.episodes,
                hasMoreEpisodes = page.hasMore,
                isFetchingFromFeed = false,
            )
    }

    private suspend fun syncPiTipAfterSubscribe(podcastId: String) {
        val synced = repository.syncSubscriptions(listOf(podcastId))
        synced[podcastId]?.let { episode ->
            subscriptionRepository.updateLatestEpisode(podcastId, episode)
        }
    }
}
