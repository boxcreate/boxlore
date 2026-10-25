package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeSupplementDao
import cx.aswin.boxlore.core.database.EpisodeSupplementEntity
import cx.aswin.boxlore.core.database.EpisodeSupplementItemEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeIdentity
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementOutcome
import cx.aswin.boxlore.core.domain.ports.EpisodeSupplementPort
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    private val isFeedUnchanged: suspend (
        url: String,
        etag: String?,
        lastModified: String?,
    ) -> Boolean = { url, etag, lastModified ->
        feedClient.confirmHeadValidators(url, etag, lastModified)
    },
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
    ): EpisodeSupplementOutcome = refreshFromFeed(
        EpisodeSupplementPort.RefreshFromFeedRequest(
            podcastIndexId = podcastIndexId,
            feedUrl = feedUrl,
            baselineEpisodes = baselineEpisodes,
            podcastTitle = podcastTitle,
            podcastImageUrl = podcastImageUrl,
            podcastGenre = podcastGenre,
            podcastArtist = podcastArtist,
        ),
    )

    override suspend fun refreshFromFeed(request: EpisodeSupplementPort.RefreshFromFeedRequest): EpisodeSupplementOutcome = withContext(Dispatchers.IO) {
        try {
            val url = resolvedRefreshFeedUrl(request.podcastIndexId, request.feedUrl)
            val loader = request.loadBaseline
            val (parsed, baseline) =
                if (loader != null) {
                    coroutineScope {
                        val parsedDeferred = async { fetchParsedFeed(request.podcastIndexId, url) }
                        val baselineDeferred = async { loader() }
                        parsedDeferred.await() to baselineDeferred.await()
                    }
                } else {
                    fetchParsedFeed(request.podcastIndexId, url) to request.baselineEpisodes
                }
            persistParsedSupplement(
                PersistParsedSupplementRequest(
                    podcastIndexId = request.podcastIndexId,
                    parsed = parsed,
                    baselineEpisodes = baseline,
                    meta =
                    SupplementPodcastMeta(
                        title = request.podcastTitle,
                        imageUrl = request.podcastImageUrl,
                        genre = request.podcastGenre,
                        artist = request.podcastArtist,
                    ),
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            feedLoadFailure()
        }
    }

    override suspend fun isPublisherFeedUnchanged(podcastIndexId: String, feedUrl: String,): Boolean = withContext(Dispatchers.IO) {
        try {
            requirePiPodcastId(podcastIndexId)
            val existing = dao.getSupplement(podcastIndexId) ?: return@withContext false
            if (existing.feedEtag.isNullOrBlank() && existing.feedLastModified.isNullOrBlank()) {
                return@withContext false
            }
            val url =
                resolveHttpsFeedUrl(feedUrl, existing.feedUrl) ?: return@withContext false
            isFeedUnchanged(url, existing.feedEtag, existing.feedLastModified)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            false
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
            val parsed = fetchParsedFeed(podcastIndexId, resolvedRefreshFeedUrl(podcastIndexId, feedUrl))
            val feedOnly =
                parsed.items.filterNot { item ->
                    EpisodeSupplementMatcher.isPresentInBaseline(item.episode, baselineEpisodes)
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
                    meta =
                    SupplementPodcastMeta(
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

    override suspend fun hasDirectFeedOptIn(podcastIndexId: String): Boolean = withContext(Dispatchers.IO) {
        dao.getSupplement(podcastIndexId) != null
    }

    override suspend fun listOptedInPodcastIds(): Set<String> = withContext(Dispatchers.IO) {
        dao.listOptedInPodcastIds().toSet()
    }

    override suspend fun listDirectFeedOptIns(): List<EpisodeSupplementPort.DirectFeedOptIn> = withContext(Dispatchers.IO) {
        dao.listSupplements().mapNotNull { row ->
            val url = resolveHttpsFeedUrl(row.feedUrl, "") ?: return@mapNotNull null
            EpisodeSupplementPort.DirectFeedOptIn(
                podcastIndexId = row.podcastId,
                feedUrl = url,
            )
        }
    }

    override suspend fun restoreDirectFeedOptIn(podcastIndexId: String, feedUrl: String,) = withContext(Dispatchers.IO) {
        try {
            requirePiPodcastId(podcastIndexId)
            val https = resolveHttpsFeedUrl(feedUrl, "") ?: return@withContext
            if (dao.getSupplement(podcastIndexId) != null) return@withContext
            val namespace =
                runCatching { RssIdGenerator.podcastId(https) }.getOrElse { return@withContext }
            dao.upsertSupplement(
                EpisodeSupplementEntity(
                    podcastId = podcastIndexId,
                    feedUrl = https,
                    rssNamespaceId = namespace,
                    feedEtag = null,
                    feedLastModified = null,
                    fetchedAt = 0L,
                ),
            )
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            // Invalid backup ids must not fail the surrounding library import.
        }
    }

    /**
     * Fetches the publisher feed and returns a tip under [request.podcastIndexId].
     *
     * When [EpisodeSupplementPort.NewestTipRequest.match] is set, only that feed item
     * is promoted — not whatever happens to be newest. Throws [IllegalArgumentException]
     * for a blank or `rss:` id, and may throw from the HTTP client or parser. Callers
     * that must not fail the surrounding job should catch non-cancellation exceptions.
     */
    override suspend fun resolveNewestTipFromFeed(request: EpisodeSupplementPort.NewestTipRequest): Episode? = withContext(Dispatchers.IO) {
        requirePiPodcastId(request.podcastIndexId)
        val existing = dao.getSupplement(request.podcastIndexId) ?: return@withContext null
        val resolvedUrl =
            resolveHttpsFeedUrl(request.feedUrl, existing.feedUrl) ?: return@withContext null
        val parsed = fetchParsedFeed(request.podcastIndexId, resolvedUrl)
        val picked =
            pickMatchingFeedEpisode(
                episodes = parsed.episodes,
                match = request.match,
            ) ?: return@withContext null
        // The tip keeps the ID of the supplement row already stored for it (P4 PR 8).
        val chosen =
            parsed.items.firstOrNull { it.episode === picked }?.let { item ->
                SupplementIdentity.remap(listOf(item), dao.getAllNewest(request.podcastIndexId)).items.firstOrNull()?.episode
            } ?: picked
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
                listOf(
                    chosen.toSupplementItem(
                        podcastIndexId = request.podcastIndexId,
                        imageUrl =
                        EpisodeSupplementArtworkLogic.resolvedImageUrl(
                            itemImageUrl = chosen.imageUrl,
                            channelImageUrl = parsed.channelImageUrl,
                            showImageUrl = request.podcastImageUrl,
                        ),
                    ),
                )
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

    private suspend fun resolvedRefreshFeedUrl(podcastIndexId: String, feedUrl: String,): String {
        requirePiPodcastId(podcastIndexId)
        val stored = dao.getSupplement(podcastIndexId)?.feedUrl.orEmpty()
        return resolveHttpsFeedUrl(feedUrl, stored)
            ?: error("No HTTPS feed URL")
    }

    private suspend fun fetchParsedFeed(podcastIndexId: String, feedUrl: String,): ParsedSupplementFeed {
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
            channelImageUrl = parsed.imageUrl,
            episodes = parsed.episodes,
            items = parsed.items,
        )
    }

    private suspend fun persistParsedSupplement(request: PersistParsedSupplementRequest): EpisodeSupplementOutcome.Success {
        val feedOnly =
            request.feedOnlyOverride ?: request.parsed.items.filterNot { item ->
                EpisodeSupplementMatcher.isPresentInBaseline(item.episode, request.baselineEpisodes)
            }
        val storedRows = dao.getAllNewest(request.podcastIndexId)
        val previousIds = storedRows.map { it.episodeId }.toSet()
        val sticky = SupplementIdentity.remap(feedOnly, storedRows)
        val items =
            sticky.items.map(ParsedFeedItem::episode).map { rss ->
                rss.toSupplementItem(
                    podcastIndexId = request.podcastIndexId,
                    imageUrl =
                    EpisodeSupplementArtworkLogic.resolvedImageUrl(
                        itemImageUrl = rss.imageUrl,
                        channelImageUrl = request.parsed.channelImageUrl,
                        showImageUrl = request.meta.imageUrl,
                    ),
                )
            }
        dao.replaceAll(
            podcastId = request.podcastIndexId,
            supplement =
            EpisodeSupplementEntity(
                podcastId = request.podcastIndexId,
                feedUrl = request.parsed.finalUrl,
                rssNamespaceId = request.parsed.rssNamespaceId,
                feedEtag = request.parsed.etag,
                feedLastModified = request.parsed.lastModified,
                fetchedAt = System.currentTimeMillis(),
            ),
            items = items + sticky.keptRows,
        )
        // The newest item under the ID its supplement row keeps, when it is feed-only.
        val stickyByPosition = sticky.items.associateBy(ParsedFeedItem::documentIndex)
        val tip =
            request.parsed.items.firstOrNull()?.let { newestItem ->
                val newest = stickyByPosition[newestItem.documentIndex]?.episode ?: newestItem.episode
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

        fun create(database: BoxLoreDatabase, feedClient: RssFeedClient = RssFeedClient(),): EpisodeSupplementRepository = EpisodeSupplementRepository(database, feedClient)

        internal fun requirePiPodcastId(podcastIndexId: String) {
            require(podcastIndexId.isNotBlank()) { "Missing podcast id" }
            require(!podcastIndexId.startsWith("rss:")) {
                "Supplement is only for Podcast Index shows"
            }
        }

        /** Prefer a request HTTPS URL; otherwise the stored supplement HTTPS URL. */
        internal fun resolveHttpsFeedUrl(requestUrl: String, storedUrl: String,): String? {
            val requested = requestUrl.trim()
            if (requested.startsWith("https://", ignoreCase = true)) return requested
            val stored = storedUrl.trim()
            return stored.takeIf { it.startsWith("https://", ignoreCase = true) }
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

        private fun feedLoadFailure(): EpisodeSupplementOutcome.Failure = EpisodeSupplementOutcome.Failure(FEED_LOAD_FAILED_MESSAGE)
    }
}

private data class ParsedSupplementFeed(
    val finalUrl: String,
    val rssNamespaceId: String,
    val etag: String?,
    val lastModified: String?,
    val channelImageUrl: String?,
    val episodes: List<RssEpisodeEntity>,
    /** [episodes] with their feed positions and identity keys, in the same order. */
    val items: List<ParsedFeedItem>,
)

private data class SupplementPodcastMeta(val title: String?, val imageUrl: String?, val genre: String?, val artist: String?,)

private data class PersistParsedSupplementRequest(
    val podcastIndexId: String,
    val parsed: ParsedSupplementFeed,
    val baselineEpisodes: List<Episode>,
    val meta: SupplementPodcastMeta,
    val feedOnlyOverride: List<ParsedFeedItem>? = null,
)

private fun RssEpisodeEntity.toSupplementItem(podcastIndexId: String, imageUrl: String? = this.imageUrl,) = EpisodeSupplementItemEntity(
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

/**
 * Keeps supplement episode IDs stable across refreshes, like the local catalog does.
 *
 * Supplement rows are replaced on every refresh. Without this, an item whose minted ID depends on
 * which parser read the feed (a GUID-less item with several media addresses, read by both parsers
 * under 4 MiB and by the streaming parser alone above it), or an older build's second row for one
 * item, would be deleted and re-added under another ID, leaving history, queue and downloads on
 * the old ID.
 *
 * Added in P4 PR 8 (own-IDs plan section 4: stored rows keep their IDs).
 * COMPAT(own-ids): keeps every stored supplement episode ID when keys or duplicate handling change. Permanent: never remove (MIGRATION.md §5).
 */
internal object SupplementIdentity {
    /**
     * @property items the feed-only items, each under the ID of the stored row it matches
     *   ([StickyRssEpisodeRemap.remapItems]: catalog key, own minted ID, then older keys).
     * @property keptRows stored rows no item matched that hold one of an item's keys (its catalog
     *   key or [ParsedFeedItem.legacyKeys]): an older build's second row for the same episode. They
     *   are written back unchanged. Rows of episodes that left the feed or reached the Podcast Index
     *   baseline are dropped, as before.
     */
    data class Result(val items: List<ParsedFeedItem>, val keptRows: List<EpisodeSupplementItemEntity>,)

    fun remap(feedOnly: List<ParsedFeedItem>, stored: List<EpisodeSupplementItemEntity>,): Result {
        val items = StickyRssEpisodeRemap.remapItems(feedOnly, stored.map { RssEpisodeIdentity(it.episodeId, it.guid, it.audioUrl) })
        val ids = items.mapTo(HashSet()) { it.episode.episodeId }
        val episodeKeys = feedOnly.flatMapTo(HashSet()) { listOfNotNull(it.catalogKey) + it.legacyKeys }
        val keptRows =
            stored.filter { row ->
                row.episodeId !in ids && StickyEpisodeIdentity.catalogKey(row.guid, row.audioUrl) in episodeKeys
            }
        return Result(items, keptRows)
    }
}
