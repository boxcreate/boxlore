package cx.aswin.boxlore.fcm

import cx.aswin.boxlore.core.catalog.SubscriptionForegroundSync
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
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
    suspend fun resolveLocalEpisode(
        podcastId: String,
        payloadFeedUrl: String?,
        payloadEnclosureUrl: String?,
        subscriptionRepository: SubscriptionRepository,
        episodeSupplementPort: EpisodeSupplementPort?,
        payloadGuid: String? = null,
        loadPiBaseline: (suspend (String) -> List<Episode>)? = null,
    ): Episode? {
        val port = episodeSupplementPort ?: return null
        if (!port.hasDirectFeedOptIn(podcastId)) return null
        val entity = subscriptionRepository.getPodcastEntity(podcastId)
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

        val outcome = runFullRefresh(port, podcastId, meta, loadPiBaseline)
        if (outcome is EpisodeSupplementOutcome.Success) {
            outcome.newestFeedEpisode?.let { tip ->
                subscriptionRepository.updateLatestEpisode(podcastId, tip, markAsNew = true)
            }
        }

        val extras = cachedExtras(port, podcastId, meta)
        val newest = (outcome as? EpisodeSupplementOutcome.Success)?.newestFeedEpisode
        val picked = NewEpisodeFcmLogic.pickHydratedEpisode(extras, newest, enclosure)
        if (enclosure.isNotEmpty() && picked?.audioUrl?.trim() == enclosure) {
            return picked
        }
        if (guid.isEmpty() && enclosure.isEmpty()) {
            return picked
        }

        val matched = resolveMatchedTip(port, podcastId, meta, guid, enclosure)
        if (matched != null) {
            subscriptionRepository.updateLatestEpisode(podcastId, matched, markAsNew = true)
            return matched
        }
        return null
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
                        EpisodeSupplementPort.FeedItemMatch(
                            guid = guid.takeIf { it.isNotEmpty() },
                            enclosureUrl = enclosure.takeIf { it.isNotEmpty() },
                        )
                            .takeIf { it.guid != null || it.enclosureUrl != null },
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }

    internal fun piBaselineLoader(
        loadPage: suspend (feedId: String, limit: Int) -> List<Episode>,
    ): suspend (String) -> List<Episode> = { podcastId ->
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
