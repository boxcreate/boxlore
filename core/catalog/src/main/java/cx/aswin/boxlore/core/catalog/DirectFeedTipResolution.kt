package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort

/** One followed show's own feed read for [SubscriptionForegroundSync]: its local catalog when there is one, else the legacy feed extras. */
@Suppress("LongParameterList")
internal suspend fun resolveLocalCatalogTip(
    podcastId: String,
    meta: DirectFeedTipMeta,
    localEpisodeCatalog: LocalEpisodeCatalogPort?,
    episodeSupplementPort: EpisodeSupplementPort,
    podcastRepository: PodcastRepository,
    followMoves: Boolean,
    adoptMovedFeed: suspend (readFrom: String, movedTo: String) -> Unit,
): DirectFeedResolveResult {
    val catalog = localEpisodeCatalog
    if (catalog != null) {
        return SubscriptionForegroundSync.resolveCatalogRefresh(podcastId, meta, catalog, followMoves, adoptMovedFeed)
    }
    return resolveLegacySupplementTip(podcastId, meta, episodeSupplementPort, podcastRepository)
}

private suspend fun resolveLegacySupplementTip(
    podcastId: String,
    meta: DirectFeedTipMeta,
    episodeSupplementPort: EpisodeSupplementPort,
    podcastRepository: PodcastRepository,
): DirectFeedResolveResult {
    if (episodeSupplementPort.isPublisherFeedUnchanged(podcastId, meta.feedUrl.orEmpty())) {
        return DirectFeedResolveResult(
            tip =
            episodeSupplementPort
                .getEpisodesForPodcast(podcastId)
                .maxByOrNull { it.publishedDate },
            persisted = false,
        )
    }
    return when (
        val outcome =
            episodeSupplementPort.refreshFromFeed(
                EpisodeSupplementPort.RefreshFromFeedRequest(
                    podcastIndexId = podcastId,
                    feedUrl = meta.feedUrl.orEmpty(),
                    loadBaseline = { podcastRepository.loadPiEpisodesForSupplement(podcastId) },
                    podcastTitle = meta.title,
                    podcastImageUrl = meta.imageUrl,
                    podcastGenre = meta.genre,
                    podcastArtist = meta.artist,
                ),
            )
    ) {
        is EpisodeSupplementOutcome.Success ->
            DirectFeedResolveResult(tip = outcome.newestFeedEpisode, persisted = true)
        is EpisodeSupplementOutcome.Failure ->
            DirectFeedResolveResult(tip = null, persisted = false, failed = true)
        EpisodeSupplementOutcome.NoDisconnect ->
            DirectFeedResolveResult(tip = null, persisted = false)
    }
}

/** The catalog's show fields for a read. */
internal fun catalogMeta(meta: DirectFeedTipMeta) = LocalEpisodeCatalogPort.PodcastMeta(
    title = meta.title,
    imageUrl = meta.imageUrl,
    genre = meta.genre,
    artist = meta.artist,
)
