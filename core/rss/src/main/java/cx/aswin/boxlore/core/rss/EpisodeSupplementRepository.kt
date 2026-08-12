package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeSupplementEntity
import cx.aswin.boxlore.core.database.EpisodeSupplementItemEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fetches a publisher feed and caches feed-only episodes under a Podcast Index podcast id.
 *
 * Never creates `rss:` library rows, never migrates subscriptions, never touches FCM.
 * Callers may promote [EpisodeSupplementOutcome.Success.newestFeedEpisode] into Room
 * `podcasts.latestEpisode` for Home filter chips.
 */
class EpisodeSupplementRepository(
    private val database: BoxLoreDatabase,
    private val feedClient: RssFeedClient = RssFeedClient(),
) : EpisodeSupplementPort {
    private val dao = database.episodeSupplementDao()

    override suspend fun refreshFromFeed(
        podcastIndexId: String,
        feedUrl: String,
        baselineEpisodes: List<Episode>,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): EpisodeSupplementOutcome = withContext(Dispatchers.IO) {
        try {
            val parsed = fetchParsedFeed(podcastIndexId, feedUrl)
            persistParsedSupplement(
                podcastIndexId = podcastIndexId,
                parsed = parsed,
                baselineEpisodes = baselineEpisodes,
                podcastTitle = podcastTitle,
                podcastImageUrl = podcastImageUrl,
                podcastGenre = podcastGenre,
                podcastArtist = podcastArtist,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            EpisodeSupplementOutcome.Failure(
                message = error.message?.takeIf(String::isNotBlank)
                    ?: "Couldn't load feed",
            )
        }
    }

    override suspend fun optInFromFeedIfDisconnected(
        podcastIndexId: String,
        feedUrl: String,
        baselineEpisodes: List<Episode>,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): EpisodeSupplementOutcome = withContext(Dispatchers.IO) {
        try {
            val parsed = fetchParsedFeed(podcastIndexId, feedUrl)
            val feedOnly = parsed.episodes.filterNot { rss ->
                EpisodeSupplementMatcher.isPresentInBaseline(rss, baselineEpisodes)
            }
            val newestFeedPublished = parsed.episodes.firstOrNull()?.publishedDate ?: 0L
            val newestBaselinePublished =
                baselineEpisodes.maxOfOrNull { it.publishedDate } ?: 0L
            if (!EpisodeSupplementDisconnectLogic.shouldOptIn(
                    feedOnlyCount = feedOnly.size,
                    newestFeedPublishedDate = newestFeedPublished,
                    newestBaselinePublishedDate = newestBaselinePublished,
                )
            ) {
                return@withContext EpisodeSupplementOutcome.NoDisconnect
            }
            persistParsedSupplement(
                podcastIndexId = podcastIndexId,
                parsed = parsed,
                baselineEpisodes = baselineEpisodes,
                podcastTitle = podcastTitle,
                podcastImageUrl = podcastImageUrl,
                podcastGenre = podcastGenre,
                podcastArtist = podcastArtist,
                feedOnlyOverride = feedOnly,
            )
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            EpisodeSupplementOutcome.Failure(
                message = error.message?.takeIf(String::isNotBlank)
                    ?: "Couldn't load feed",
            )
        }
    }

    override suspend fun hasDirectFeedOptIn(podcastIndexId: String): Boolean =
        withContext(Dispatchers.IO) {
            dao.getSupplement(podcastIndexId) != null
        }

    override suspend fun listOptedInPodcastIds(): Set<String> =
        withContext(Dispatchers.IO) {
            dao.listOptedInPodcastIds().toSet()
        }

    override suspend fun resolveNewestTipFromFeed(
        podcastIndexId: String,
        feedUrl: String,
        knownEpisodes: List<Episode>,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): Episode? = withContext(Dispatchers.IO) {
        require(podcastIndexId.isNotBlank()) { "Missing podcast id" }
        require(!podcastIndexId.startsWith("rss:")) {
            "Supplement is only for Podcast Index shows"
        }
        val existing = dao.getSupplement(podcastIndexId) ?: return@withContext null
        val resolvedUrl =
            feedUrl.trim().ifEmpty { existing.feedUrl.trim() }
        if (!resolvedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext null
        }
        val parsed = fetchParsedFeed(podcastIndexId, resolvedUrl)
        val newest = parsed.episodes.firstOrNull() ?: return@withContext null
        val tip =
            EpisodeSupplementTipLogic.resolveNewestTip(
                newestFeedEpisode = newest,
                podcastIndexId = podcastIndexId,
                knownEpisodes = knownEpisodes,
                podcastTitle = podcastTitle,
                podcastImageUrl = podcastImageUrl,
                podcastGenre = podcastGenre,
                podcastArtist = podcastArtist,
            )
        dao.upsertSupplement(
            existing.copy(
                feedUrl = parsed.finalUrl,
                rssNamespaceId = parsed.rssNamespaceId,
                feedEtag = parsed.etag,
                feedLastModified = parsed.lastModified,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        val matched = EpisodeSupplementMatcher.findMatchingBaseline(newest, knownEpisodes)
        if (matched == null) {
            dao.upsertItems(listOf(newest.toSupplementItem(podcastIndexId)))
        }
        tip
    }

    override suspend fun getEpisodesForPodcast(
        podcastIndexId: String,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): List<Episode> = withContext(Dispatchers.IO) {
        dao.getAllNewest(podcastIndexId).map {
            it.toEpisode(podcastTitle, podcastImageUrl, podcastGenre, podcastArtist)
        }
    }

    override suspend fun getEpisode(
        episodeId: String,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): Episode? = withContext(Dispatchers.IO) {
        dao.getEpisode(episodeId)?.toEpisode(
            podcastTitle,
            podcastImageUrl,
            podcastGenre,
            podcastArtist,
        )
    }

    override suspend fun search(
        podcastIndexId: String,
        query: String,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
    ): List<Episode> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        dao.search(podcastIndexId, trimmed.escapeForSqlLike()).map {
            it.toEpisode(podcastTitle, podcastImageUrl, podcastGenre, podcastArtist)
        }
    }

    private suspend fun fetchParsedFeed(
        podcastIndexId: String,
        feedUrl: String,
    ): ParsedSupplementFeed {
        require(podcastIndexId.isNotBlank()) { "Missing podcast id" }
        require(!podcastIndexId.startsWith("rss:")) {
            "Supplement is only for Podcast Index shows"
        }
        val fetched = feedClient.fetch(feedUrl)
        val rssNamespaceId = RssIdGenerator.podcastId(fetched.finalUrl)
        val parsed =
            feedClient.parse(
                feedUrl = fetched.finalUrl,
                bytes = fetched.body,
                podcastId = rssNamespaceId,
            )
        return ParsedSupplementFeed(
            finalUrl = fetched.finalUrl,
            rssNamespaceId = rssNamespaceId,
            etag = fetched.etag,
            lastModified = fetched.lastModified,
            episodes = parsed.episodes,
        )
    }

    private suspend fun persistParsedSupplement(
        podcastIndexId: String,
        parsed: ParsedSupplementFeed,
        baselineEpisodes: List<Episode>,
        podcastTitle: String?,
        podcastImageUrl: String?,
        podcastGenre: String?,
        podcastArtist: String?,
        feedOnlyOverride: List<RssEpisodeEntity>? = null,
    ): EpisodeSupplementOutcome.Success {
        val feedOnly =
            feedOnlyOverride ?: parsed.episodes.filterNot { rss ->
                EpisodeSupplementMatcher.isPresentInBaseline(rss, baselineEpisodes)
            }
        val previousIds = dao.getAllNewest(podcastIndexId).map { it.episodeId }.toSet()
        val items = feedOnly.map { it.toSupplementItem(podcastIndexId) }
        dao.replaceAll(
            podcastId = podcastIndexId,
            supplement = EpisodeSupplementEntity(
                podcastId = podcastIndexId,
                feedUrl = parsed.finalUrl,
                rssNamespaceId = parsed.rssNamespaceId,
                feedEtag = parsed.etag,
                feedLastModified = parsed.lastModified,
                fetchedAt = System.currentTimeMillis(),
            ),
            items = items,
        )
        val tip =
            parsed.episodes.firstOrNull()?.let { newest ->
                EpisodeSupplementTipLogic.resolveNewestTip(
                    newestFeedEpisode = newest,
                    podcastIndexId = podcastIndexId,
                    knownEpisodes = baselineEpisodes,
                    podcastTitle = podcastTitle,
                    podcastImageUrl = podcastImageUrl,
                    podcastGenre = podcastGenre,
                    podcastArtist = podcastArtist,
                )
            }
        return EpisodeSupplementOutcome.Success(
            addedCount = items.count { it.episodeId !in previousIds },
            totalSupplementCount = items.size,
            newestFeedEpisode = tip,
        )
    }

    companion object {
        fun create(
            database: BoxLoreDatabase,
            feedClient: RssFeedClient = RssFeedClient(),
        ): EpisodeSupplementRepository = EpisodeSupplementRepository(database, feedClient)
    }
}

private data class ParsedSupplementFeed(
    val finalUrl: String,
    val rssNamespaceId: String,
    val etag: String?,
    val lastModified: String?,
    val episodes: List<RssEpisodeEntity>,
)

private fun RssEpisodeEntity.toSupplementItem(podcastIndexId: String) =
    EpisodeSupplementItemEntity(
        episodeId = episodeId,
        podcastId = podcastIndexId,
        guid = guid,
        title = title,
        description = description,
        audioUrl = audioUrl,
        imageUrl = imageUrl,
        duration = duration,
        publishedDate = publishedDate,
        chaptersUrl = chaptersUrl,
        transcriptUrl = transcriptUrl,
        transcripts = transcripts,
        persons = persons,
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        episodeType = episodeType,
        enclosureType = enclosureType,
    )
