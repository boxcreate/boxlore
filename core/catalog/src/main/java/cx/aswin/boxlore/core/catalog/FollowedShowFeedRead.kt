package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort

/**
 * Reads one followed show's own feed now, even inside the local catalog's six-hour wait
 * ([LocalEpisodeCatalogPort.RefreshReason.MANUAL]), and saves its newest episode as the show's
 * latest. A show without an HTTPS feed address, or whose feed fails, asks `/sync` for its newest
 * episode instead; `rss:` shows and shows no longer followed are skipped.
 *
 * Home's show chip uses it. Each call reads the publisher's feed, so the caller throttles it.
 */
object FollowedShowFeedRead {
    suspend fun readNow(
        podcastRepository: PodcastRepository,
        subscriptionRepository: SubscriptionRepository,
        podcastId: String,
    ) {
        val catalog = podcastRepository.localEpisodeCatalog
        SubscriptionForegroundSyncIngest.catalogIngestAction(
            podcastRepository = podcastRepository,
            subscriptionRepository = subscriptionRepository,
            localEpisodeCatalog = catalog,
            onFeedRefreshed = {},
            refreshCatalog = chipRefresh(catalog),
        )(podcastId)
        // A show not yet served from the local catalog lists from cached server pages.
        podcastRepository.invalidateEpisodesCache(podcastId)
    }

    /** The feed read [readNow] runs: always past the six-hour wait. */
    internal fun chipRefresh(catalog: LocalEpisodeCatalogPort?) =
        SubscriptionForegroundSyncIngest.catalogRefresh(catalog, pastWait = true)

    /** One feed read that skips the six-hour wait and the unchanged-feed shortcut. */
    internal suspend fun readPastWait(
        podcastId: String,
        meta: DirectFeedTipMeta,
        catalog: LocalEpisodeCatalogPort,
    ): DirectFeedResolveResult {
        val request =
            LocalEpisodeCatalogPort.RefreshRequest(
                podcastIndexId = podcastId,
                feedUrl = meta.feedUrl.orEmpty(),
                meta =
                LocalEpisodeCatalogPort.PodcastMeta(
                    title = meta.title,
                    imageUrl = meta.imageUrl,
                    genre = meta.genre,
                    artist = meta.artist,
                ),
                reason = LocalEpisodeCatalogPort.RefreshReason.MANUAL,
            )
        return when (val outcome = catalog.refresh(request)) {
            is LocalEpisodeCatalogPort.RefreshOutcome.Success -> DirectFeedResolveResult(tip = outcome.newest, persisted = true)
            is LocalEpisodeCatalogPort.RefreshOutcome.Unchanged -> DirectFeedResolveResult(tip = outcome.newest, persisted = false)
            is LocalEpisodeCatalogPort.RefreshOutcome.Failure ->
                DirectFeedResolveResult(
                    tip = null,
                    persisted = false,
                    failed = true,
                    addressGone = outcome.addressGone,
                    reportStatus = outcome.reportStatus,
                )
        }
    }
}
