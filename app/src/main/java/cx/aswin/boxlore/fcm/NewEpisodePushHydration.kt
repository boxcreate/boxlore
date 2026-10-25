package cx.aswin.boxlore.fcm

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
        /**
         * The Podcast Index list the feed-only extras are compared with (only without a local
         * catalog). Catalog refreshes never take one: new episodes get recipe IDs.
         */
        val loadPiList: (suspend (String) -> List<Episode>)? = null,
        /**
         * The phone's row of the episode of a show whose trusted aliases carry a recipe ID, for a
         * push that names one. Used only after GUID and enclosure matching.
         *
         * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, notifications).
         */
        val findByRecipeId: (suspend (podcastId: String, recipeId: Long) -> Episode?)? = null,
    )

    /**
     * What a push says about its episode: GUID and enclosure (matched first, as before), then the
     * catalog key and recipe ID newer pushes add.
     */
    private data class PushMatch(val guid: String?, val enclosureUrl: String?, val catalogKey: String?, val recipeId: Long?,)

    suspend fun resolveLocalEpisode(
        podcastId: String,
        payloadFeedUrl: String?,
        payloadEnclosureUrl: String?,
        payloadGuid: String? = null,
        sources: Sources,
        payloadCatalogKey: String? = null,
        payloadRecipeId: Long? = null,
    ): Episode? {
        if (sources.localEpisodeCatalog != null) {
            return resolveFromLocalCatalog(
                podcastId = podcastId,
                payloadFeedUrl = payloadFeedUrl,
                match = PushMatch(payloadGuid, payloadEnclosureUrl, payloadCatalogKey, payloadRecipeId),
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

        val outcome = runFullRefresh(port, podcastId, meta, sources.loadPiList)
        if (outcome is EpisodeSupplementOutcome.Success) {
            outcome.newestFeedEpisode?.let { tip ->
                sources.subscriptionRepository.updateLatestEpisode(
                    podcastId = podcastId,
                    episode = tip,
                    markAsNew = false,
                    publisherFeedAuthoritative = true,
                )
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
            sources.subscriptionRepository.updateLatestEpisode(
                podcastId = podcastId,
                episode = matched,
                markAsNew = false,
            )
            return matched
        }
        return null
    }

    private suspend fun resolveFromLocalCatalog(
        podcastId: String,
        payloadFeedUrl: String?,
        match: PushMatch,
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
        findStored(podcastId, match, meta, catalog, sources)?.let { return it }
        try {
            catalog.refresh(
                LocalEpisodeCatalogPort.RefreshRequest(
                    podcastIndexId = podcastId,
                    feedUrl = feedUrl,
                    meta = meta,
                    reason = LocalEpisodeCatalogPort.RefreshReason.NEW_RELEASE,
                ),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Match extras path: a failed refresh still tries the last-good catalog row.
        }
        val matched = findStored(podcastId, match, meta, catalog, sources) ?: return null
        sources.subscriptionRepository.updateLatestEpisode(
            podcastId = podcastId,
            episode = matched,
            markAsNew = false,
        )
        return matched
    }

    /**
     * The stored episode a push names: by GUID or enclosure first, as before; then by the catalog key
     * and, through the show's aliases, by (show, recipe ID) (P4 PR 13).
     */
    private suspend fun findStored(
        podcastId: String,
        match: PushMatch,
        meta: LocalEpisodeCatalogPort.PodcastMeta,
        catalog: LocalEpisodeCatalogPort,
        sources: Sources,
    ): Episode? {
        catalog.findByCatalogKey(podcastId, match.guid, match.enclosureUrl, meta)?.let { return it }
        // The key is the episode's GUID, or its enclosure address when it has none.
        match.catalogKey?.let { key -> catalog.findByCatalogKey(podcastId, key, key, meta)?.let { return it } }
        val recipeId = match.recipeId ?: return null
        return try {
            sources.findByRecipeId?.invoke(podcastId, recipeId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun runFullRefresh(
        port: EpisodeSupplementPort,
        podcastId: String,
        meta: HydrationMeta,
        loadPiList: (suspend (String) -> List<Episode>)?,
    ): EpisodeSupplementOutcome? = try {
        port.refreshFromFeed(
            EpisodeSupplementPort.RefreshFromFeedRequest(
                podcastIndexId = podcastId,
                feedUrl = meta.feedUrl,
                loadBaseline = loadPiList?.let { loader -> { loader(podcastId) } },
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

    private suspend fun cachedExtras(port: EpisodeSupplementPort, podcastId: String, meta: HydrationMeta,): List<Episode> = try {
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
    ): Episode? = try {
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

    private data class HydrationMeta(
        val feedUrl: String,
        val title: String?,
        val imageUrl: String?,
        val genre: String?,
        val artist: String?,
        val knownTip: Episode?,
    )
}
