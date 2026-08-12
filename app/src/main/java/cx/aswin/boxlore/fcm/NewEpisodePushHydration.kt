package cx.aswin.boxlore.fcm

import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import kotlin.coroutines.cancellation.CancellationException

/**
 * On a new-episode push, refresh the publisher feed for opted-in PI shows so the
 * notification and auto-download use a local episode id (PI or negative supplement).
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
    ): Episode? {
        val port = episodeSupplementPort ?: return null
        if (!port.hasDirectFeedOptIn(podcastId)) return null
        val entity = subscriptionRepository.getPodcastEntity(podcastId)
        val feedUrl =
            payloadFeedUrl?.trim().orEmpty().ifEmpty { entity?.feedUrl.orEmpty() }
        val guid = payloadGuid?.trim().orEmpty()
        val enclosure = payloadEnclosureUrl?.trim().orEmpty()
        val tip =
            try {
                port.resolveNewestTipFromFeed(
                    EpisodeSupplementPort.NewestTipRequest(
                        podcastIndexId = podcastId,
                        feedUrl = feedUrl,
                        knownEpisodes = listOfNotNull(entity?.latestEpisode),
                        podcastTitle = entity?.title,
                        podcastImageUrl = entity?.imageUrl,
                        podcastGenre = entity?.genre,
                        podcastArtist = entity?.author,
                        match = EpisodeSupplementPort.FeedItemMatch(
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
        if (tip != null) {
            subscriptionRepository.updateLatestEpisode(podcastId, tip, markAsNew = true)
            return tip
        }
        if (enclosure.isEmpty()) return null
        val cached =
            port
                .getEpisodesForPodcast(
                    podcastIndexId = podcastId,
                    podcastTitle = entity?.title,
                    podcastImageUrl = entity?.imageUrl,
                    podcastGenre = entity?.genre,
                    podcastArtist = entity?.author,
                ).find { it.audioUrl.trim() == enclosure } ?: return null
        subscriptionRepository.updateLatestEpisode(podcastId, cached, markAsNew = true)
        return cached
    }
}
