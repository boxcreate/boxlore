package cx.aswin.boxlore.fcm

import cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.model.Episode
import kotlin.coroutines.cancellation.CancellationException

/**
 * On a new-episode push, persist the full publisher-feed extras for opted-in PI
 * shows (same [EpisodeSupplementPort.refreshFromFeed] path as launch sync) so Home
 * chips and Library New Episodes share the extras, then resolve the payload item
 * for the notification / auto-download.
 *
 * Does not opt the show in from FCM.
 */
internal object NewEpisodePushHydration {
    data class Sources(
        val subscriptionRepository: SubscriptionRepository,
        val episodeSupplementPort: EpisodeSupplementPort? = null,
        val localEpisodeCatalog: LocalEpisodeCatalogPort? = null,
        val loadPiBaseline: (suspend (String) -> List<Episode>)? = null,
    )

    suspend fun resolveLocalEpisode(
        podcastId: String,
        payloadFeedUrl: String?,
        payloadEnclosureUrl: String?,
        payloadGuid: String? = null,
        sources: Sources,
    ): Episode? {
        if (sources.localEpisodeCatalog != null) {
            return resolveFromLocalCatalog(
                podcastId = podcastId,
                payloadFeedUrl = payloadFeedUrl,
                payloadEnclosureUrl = payloadEnclosureUrl,
                payloadGuid = payloadGuid,
                sources = sources,
                catalog = sources.localEpisodeCatalog,
            )
        }
        val port = sources.episodeSupplementPort ?: return null
        if (!port.hasDirectFeedOptIn(podcastId)) return null
        val entity = sources.subscriptionRepository.getPodcastEntity(podcastId)
        val meta =
            HydrationMeta(
                feedUrl = payloadFeedUrl?.trim().orEmpty().ifEmpty { entity?.feedUrl.orEmpty() },
                title = entity?.title,
                imageUrl = entity?.imageUrl,
                genre = entity?.genre,
                artist = entity?.author,
                knownTip = entity?.latestEpisode,
            )
        val guid = payloadGuid?.trim().orEmpty()
        val enclosure = payloadEnclosureUrl?.trim().orEmpty()

        val outcome = runFullRefresh(port, podcastId, meta, sources.loadPiBaseline)
        if (outcome is EpisodeSupplementOutcome.Success) {
            outcome.newestFeedEpisode?.let { tip ->
                sources.subscriptionRepository.updateLatestEpisode(podcastId, tip, markAsNew = false)
            }
        }

        val extras = cachedExtras(port, podcastId, meta)
        val newest = (outcome as? EpisodeSupplementOutcome.Success)?.newestFeedEpisode
        val picked = NewEpisodeFcmLogic.pickHydratedEpisode(extras, newest, enclosure)
        if (enclosure.isNotEmpty() && picked?.audioUrl?.trim() == enclosure) {
            return picked
        }
        if (guid.isEmpty() && enclosure.isEmpty()) {
            return null
        }

        val matched = resolveMatchedTip(port, podcastId, meta, guid, enclosure)
        if (matched != null) {
            sources.subscriptionRepository.updateLatestEpisode(podcastId, matched, markAsNew = false)
            return matched
        }
        return null
    }

    private suspend fun resolveFromLocalCatalog(
        podcastId: String,
        payloadFeedUrl: String?,
        payloadEnclosureUrl: String?,
        payloadGuid: String?,
        sources: Sources,
        catalog: LocalEpisodeCatalogPort,
    ): Episode? {
        val entity = sources.subscriptionRepository.getPodcastEntity(podcastId)
        val meta =
            LocalEpisodeCatalogPort.PodcastMeta(
                title = entity?.title,
                imageUrl = entity?.imageUrl,
                genre = entity?.genre,
                artist = entity?.author,
            )
        val feedUrl = payloadFeedUrl?.trim().orEmpty().ifEmpty { entity?.feedUrl.orEmpty() }
        val needsBaseline = !catalog.isReady(podcastId)
        try {
            catalog.refresh(
                LocalEpisodeCatalogPort.RefreshRequest(
                    podcastIndexId = podcastId,
                    feedUrl = feedUrl,
                    meta = meta,
                    loadPiBaseline =
                        if (needsBaseline) {
                            sources.loadPiBaseline?.let { loader -> { loader(podcastId) } }
                        } else {
                            null
                        },
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Match extras path: a failed refresh still tries the last-good catalog row.
        }
        val matched =
            catalog.findByCatalogKey(
                podcastId = podcastId,
                guid = payloadGuid,
                enclosureUrl = payloadEnclosureUrl,
                meta = meta,
            ) ?: return null
        sources.subscriptionRepository.updateLatestEpisode(podcastId, matched, markAsNew = false)
        return matched
    }

    private suspend fun runFullRefresh(
        port: EpisodeSupplementPort,
        podcastId: String,
        meta: HydrationMeta,
        loadPiBaseline: (suspend (String) -> List<Episode>)?,
    ): EpisodeSupplementOutcome? =
        try {
            port.refreshFromFeed(
                EpisodeSupplementPort.RefreshFromFeedRequest(
                    podcastIndexId = podcastId,
                    feedUrl = meta.feedUrl,
                    loadBaseline = loadPiBaseline?.let { loader -> { loader(podcastId) } },
                    podcastTitle = meta.title,
                    podcastImageUrl = meta.imageUrl,
                    podcastGenre = meta.genre,
                    podcastArtist = meta.artist,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    private suspend fun cachedExtras(
        port: EpisodeSupplementPort,
        podcastId: String,
        meta: HydrationMeta,
    ): List<Episode> =
        try {
            port.getEpisodesForPodcast(
                podcastIndexId = podcastId,
                podcastTitle = meta.title,
                podcastImageUrl = meta.imageUrl,
                podcastGenre = meta.genre,
                podcastArtist = meta.artist,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

    private suspend fun resolveMatchedTip(
        port: EpisodeSupplementPort,
        podcastId: String,
        meta: HydrationMeta,
        guid: String,
        enclosure: String,
    ): Episode? =
        try {
            port.resolveNewestTipFromFeed(
                EpisodeSupplementPort.NewestTipRequest(
                    podcastIndexId = podcastId,
                    feedUrl = meta.feedUrl,
                    knownEpisodes = listOfNotNull(meta.knownTip),
                    podcastTitle = meta.title,
                    podcastImageUrl = meta.imageUrl,
                    podcastGenre = meta.genre,
                    podcastArtist = meta.artist,
                    match =
                        EpisodeSupplementPort
                            .FeedItemMatch(
                                guid = guid.takeIf { it.isNotEmpty() },
                                enclosureUrl = enclosure.takeIf { it.isNotEmpty() },
                            ).takeIf { it.guid != null || it.enclosureUrl != null },
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    internal fun piBaselineLoader(loadPage: suspend (feedId: String, limit: Int) -> List<Episode>): suspend (String) -> List<Episode> =
        { podcastId ->
            loadPage(podcastId, SubscriptionForegroundSync.DIRECT_FEED_BASELINE_LIMIT)
        }

    private data class HydrationMeta(
        val feedUrl: String,
        val title: String?,
        val imageUrl: String?,
        val genre: String?,
        val artist: String?,
        val knownTip: Episode?,
    )
}
