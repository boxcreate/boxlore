package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeSupplementDao
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
class EpisodeSupplementRepository internal constructor(
    private val dao: EpisodeSupplementDao,
    private val feedClient: RssFeedClient,
    private val runInTransaction: suspend (suspend () -> Unit) -> Unit,
) : EpisodeSupplementPort {
    constructor(
        database: BoxLoreDatabase,
        feedClient: RssFeedClient = RssFeedClient(),
    ) : this(
        dao = database.episodeSupplementDao(),
        feedClient = feedClient,
        runInTransaction = { block -> database.withTransaction { block() } },
    )

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
                PersistParsedSupplementRequest(
                    podcastIndexId = podcastIndexId,
                    parsed = parsed,
                    baselineEpisodes = baselineEpisodes,
                    meta = SupplementPodcastMeta(
                        title = podcastTitle,
                        imageUrl = podcastImageUrl,
                        genre = podcastGenre,
                        artist = podcastArtist,
                    ),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            feedLoadFailure()
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
                PersistParsedSupplementRequest(
                    podcastIndexId = podcastIndexId,
                    parsed = parsed,
                    baselineEpisodes = baselineEpisodes,
                    meta = SupplementPodcastMeta(
                        title = podcastTitle,
                        imageUrl = podcastImageUrl,
                        genre = podcastGenre,
                        artist = podcastArtist,
                    ),
                    feedOnlyOverride = feedOnly,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            feedLoadFailure()
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

    /**
     * Fetches the publisher feed and returns a tip under [request.podcastIndexId].
     *
     * When [EpisodeSupplementPort.NewestTipRequest.match] is set, only that feed item
     * is promoted — not whatever happens to be newest. Throws [IllegalArgumentException]
     * for a blank or `rss:` id, and may throw from the HTTP client or parser. Callers
     * that must not fail the surrounding job should catch non-cancellation exceptions.
     */
    override suspend fun resolveNewestTipFromFeed(
        request: EpisodeSupplementPort.NewestTipRequest,
    ): Episode? = withContext(Dispatchers.IO) {
        requirePiPodcastId(request.podcastIndexId)
        val existing = dao.getSupplement(request.podcastIndexId) ?: return@withContext null
        val resolvedUrl =
            request.feedUrl.trim().ifEmpty { existing.feedUrl.trim() }
        if (!resolvedUrl.startsWith("https://", ignoreCase = true)) {
            return@withContext null
        }
        val parsed = fetchParsedFeed(request.podcastIndexId, resolvedUrl)
        val chosen =
            pickMatchingFeedEpisode(
                episodes = parsed.episodes,
                match = request.match,
            ) ?: return@withContext null
        val meta =
            SupplementPodcastMeta(
                title = request.podcastTitle,
                imageUrl = request.podcastImageUrl,
                genre = request.podcastGenre,
                artist = request.podcastArtist,
            )
        val tip =
            EpisodeSupplementTipLogic.resolveNewestTip(
                newestFeedEpisode = chosen,
                podcastIndexId = request.podcastIndexId,
                knownEpisodes = request.knownEpisodes,
                podcastTitle = meta.title,
                podcastImageUrl = meta.imageUrl,
                podcastGenre = meta.genre,
                podcastArtist = meta.artist,
            )
        val matched = EpisodeSupplementMatcher.findMatchingBaseline(chosen, request.knownEpisodes)
        val tipItem =
            if (matched == null) {
                listOf(chosen.toSupplementItem(request.podcastIndexId))
            } else {
                emptyList()
            }
        runInTransaction {
            dao.upsertSupplementAndOptionalItems(
                existing.copy(
                    feedUrl = parsed.finalUrl,
                    rssNamespaceId = parsed.rssNamespaceId,
                    feedEtag = parsed.etag,
                    feedLastModified = parsed.lastModified,
                    fetchedAt = System.currentTimeMillis(),
                ),
                tipItem,
            )
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
        requirePiPodcastId(podcastIndexId)
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
        request: PersistParsedSupplementRequest,
    ): EpisodeSupplementOutcome.Success {
        val feedOnly =
            request.feedOnlyOverride ?: request.parsed.episodes.filterNot { rss ->
                EpisodeSupplementMatcher.isPresentInBaseline(rss, request.baselineEpisodes)
            }
        val previousIds = dao.getAllNewest(request.podcastIndexId).map { it.episodeId }.toSet()
        val items = feedOnly.map { it.toSupplementItem(request.podcastIndexId) }
        dao.replaceAll(
            podcastId = request.podcastIndexId,
            supplement = EpisodeSupplementEntity(
                podcastId = request.podcastIndexId,
                feedUrl = request.parsed.finalUrl,
                rssNamespaceId = request.parsed.rssNamespaceId,
                feedEtag = request.parsed.etag,
                feedLastModified = request.parsed.lastModified,
                fetchedAt = System.currentTimeMillis(),
            ),
            items = items,
        )
        val tip =
            request.parsed.episodes.firstOrNull()?.let { newest ->
                EpisodeSupplementTipLogic.resolveNewestTip(
                    newestFeedEpisode = newest,
                    podcastIndexId = request.podcastIndexId,
                    knownEpisodes = request.baselineEpisodes,
                    podcastTitle = request.meta.title,
                    podcastImageUrl = request.meta.imageUrl,
                    podcastGenre = request.meta.genre,
                    podcastArtist = request.meta.artist,
                )
            }
        return EpisodeSupplementOutcome.Success(
            addedCount = items.count { it.episodeId !in previousIds },
            totalSupplementCount = items.size,
            newestFeedEpisode = tip,
        )
    }

    companion object {
        const val FEED_LOAD_FAILED_MESSAGE = "Couldn't update episodes from the feed"

        fun create(
            database: BoxLoreDatabase,
            feedClient: RssFeedClient = RssFeedClient(),
        ): EpisodeSupplementRepository = EpisodeSupplementRepository(database, feedClient)

        internal fun requirePiPodcastId(podcastIndexId: String) {
            require(podcastIndexId.isNotBlank()) { "Missing podcast id" }
            require(!podcastIndexId.startsWith("rss:")) {
                "Supplement is only for Podcast Index shows"
            }
        }

        internal fun pickMatchingFeedEpisode(
            episodes: List<RssEpisodeEntity>,
            match: EpisodeSupplementPort.FeedItemMatch?,
        ): RssEpisodeEntity? {
            val guid = match?.guid?.trim().orEmpty()
            val enclosure = match?.enclosureUrl?.trim().orEmpty()
            if (guid.isEmpty() && enclosure.isEmpty()) {
                return episodes.firstOrNull()
            }
            return episodes.firstOrNull { rss ->
                (guid.isNotEmpty() && rss.guid?.trim() == guid) ||
                    (enclosure.isNotEmpty() && rss.audioUrl.trim() == enclosure)
            }
        }

        private fun feedLoadFailure(): EpisodeSupplementOutcome.Failure =
            EpisodeSupplementOutcome.Failure(FEED_LOAD_FAILED_MESSAGE)
    }
}

private data class ParsedSupplementFeed(
    val finalUrl: String,
    val rssNamespaceId: String,
    val etag: String?,
    val lastModified: String?,
    val episodes: List<RssEpisodeEntity>,
)

private data class SupplementPodcastMeta(
    val title: String?,
    val imageUrl: String?,
    val genre: String?,
    val artist: String?,
)

private data class PersistParsedSupplementRequest(
    val podcastIndexId: String,
    val parsed: ParsedSupplementFeed,
    val baselineEpisodes: List<Episode>,
    val meta: SupplementPodcastMeta,
    val feedOnlyOverride: List<RssEpisodeEntity>? = null,
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
