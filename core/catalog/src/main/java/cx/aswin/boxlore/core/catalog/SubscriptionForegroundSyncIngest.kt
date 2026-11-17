package cx.aswin.boxlore.core.catalog

import android.util.Log
import cx.aswin.boxlore.core.catalog.logic.SubscriptionForegroundSyncLogic
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.rss.LocalEpisodeCatalogRepository
import kotlinx.coroutines.CancellationException

/**
 * Reads one followed show's feed and saves its newest episode: the first save after Subscribe, on
 * the application scope, and Home's show-chip read ([FollowedShowFeedRead]), on Home's ViewModel
 * scope, so that one may be cancelled when Home closes.
 */
internal object SubscriptionForegroundSyncIngest {
    /** [refreshCatalog] reads the feed; [FollowedShowFeedRead.chipRefresh] reads it past its six-hour wait. */
    fun catalogIngestAction(
        podcastRepository: PodcastRepository,
        subscriptionRepository: SubscriptionRepository,
        localEpisodeCatalog: LocalEpisodeCatalogPort?,
        onFeedRefreshed: (String) -> Unit,
        refreshCatalog: suspend (String, DirectFeedTipMeta) -> DirectFeedResolveResult =
            catalogRefresh(localEpisodeCatalog, pastWait = false),
    ): suspend (String) -> Unit = { podcastId ->
        ingestSubscribedLocalCatalog(
            podcastId = podcastId,
            isSubscribed = { subscriptionRepository.isSubscribed(it) },
            loadMeta = { id ->
                subscriptionRepository.getPodcastEntity(id)?.let { entity ->
                    DirectFeedTipMeta(
                        feedUrl = entity.feedUrl,
                        title = entity.title,
                        imageUrl = entity.imageUrl,
                        genre = entity.genre,
                        artist = entity.author,
                        knownTip = entity.latestEpisode,
                    )
                }
            },
            refreshCatalog = refreshCatalog,
            saveLatest = { id, episode ->
                subscriptionRepository.updateLatestEpisode(
                    podcastId = id,
                    episode = episode,
                    markAsNew = false,
                    publisherFeedAuthoritative = true,
                )
            },
            markUnsubscribedTtl = { id ->
                localEpisodeCatalog?.setUnsubscribedTtl(
                    id,
                    System.currentTimeMillis() +
                        LocalEpisodeCatalogRepository.UNSUBSCRIBE_TTL_MS,
                )
            },
            syncPiTip = { id ->
                val synced = podcastRepository.syncSubscriptions(listOf(id))
                synced[id]?.let { episode ->
                    subscriptionRepository.updateLatestEpisode(id, episode)
                }
            },
            onFeedRefreshed = onFeedRefreshed,
        )
    }

    /**
     * The feed read an ingest runs. [pastWait] reads it even inside its six-hour wait
     * ([FollowedShowFeedRead.readPastWait]); otherwise the automatic read decides whether to read.
     */
    fun catalogRefresh(
        localEpisodeCatalog: LocalEpisodeCatalogPort?,
        pastWait: Boolean,
    ): suspend (String, DirectFeedTipMeta) -> DirectFeedResolveResult = { id, meta ->
        when {
            localEpisodeCatalog == null -> DirectFeedResolveResult(tip = null, persisted = false)
            pastWait -> FollowedShowFeedRead.readPastWait(id, meta, localEpisodeCatalog)
            else ->
                SubscriptionForegroundSync.resolveCatalogRefresh(
                    podcastId = id,
                    meta = meta,
                    catalog = localEpisodeCatalog,
                )
        }
    }

    @Suppress("LongParameterList", "kotlin:S107")
    suspend fun ingestSubscribedLocalCatalog(
        podcastId: String,
        isSubscribed: suspend (String) -> Boolean,
        loadMeta: suspend (String) -> DirectFeedTipMeta?,
        refreshCatalog: suspend (String, DirectFeedTipMeta) -> DirectFeedResolveResult,
        saveLatest: suspend (String, Episode) -> Unit,
        markUnsubscribedTtl: suspend (String) -> Unit,
        syncPiTip: suspend (String) -> Unit,
        onFeedRefreshed: (String) -> Unit,
    ): Boolean {
        if (!SubscriptionForegroundSyncLogic.shouldRequestCatalogIngest(podcastId)) return false
        if (!isSubscribed(podcastId)) return false
        val meta = loadMeta(podcastId)
        val feedUrl = TrackedPodcastRtdbLogic.httpsFeedUrl(meta?.feedUrl)
        if (meta == null || feedUrl == null) {
            syncPiTip(podcastId)
            return false
        }
        val resolved = refreshCatalog(podcastId, meta.copy(feedUrl = feedUrl))
        if (!isSubscribed(podcastId)) {
            markUnsubscribedTtl(podcastId)
            return false
        }
        if (resolved.persisted) {
            onFeedRefreshed(podcastId)
        }
        resolved.tip?.let { episode -> saveLatest(podcastId, episode) }
            ?: syncPiTip(podcastId)
        return resolved.persisted
    }

    /**
     * Drop Room catalogs whose unsubscribe TTL has elapsed. Isolated so a sweep
     * failure cannot abort `/sync` / feed refresh.
     */
    suspend fun sweepExpiredLocalCatalogs(catalog: LocalEpisodeCatalogPort?, nowMs: Long = System.currentTimeMillis(),) {
        try {
            catalog?.sweepExpired(nowMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Expired local catalog sweep failed", e)
        }
    }

    private const val TAG = "SubscriptionForegroundSync"
}
