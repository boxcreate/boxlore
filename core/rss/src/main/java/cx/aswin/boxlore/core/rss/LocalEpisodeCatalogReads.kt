package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeCatalogDao
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.PodcastMeta
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import cx.aswin.boxlore.core.model.Episode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-side [LocalEpisodeCatalogPort] so the repository class stays under Detekt’s function cap. */
internal class LocalEpisodeCatalogReads(
    private val dao: LocalEpisodeCatalogDao,
    private val isFeedUnchanged: suspend (
        url: String,
        etag: String?,
        lastModified: String?,
    ) -> Boolean,
) : LocalEpisodeCatalogPort {
    override suspend fun isReady(podcastId: String): Boolean =
        withContext(Dispatchers.IO) {
            LocalCatalogReadyLogic.isReady(dao.getFeed(podcastId))
        }

    override suspend fun getPage(
        podcastId: String,
        limit: Int,
        offset: Int,
        sort: String,
        meta: PodcastMeta,
    ): List<Episode> =
        withContext(Dispatchers.IO) {
            val rows =
                if (sort == "oldest") {
                    dao.getOldestPage(podcastId, limit, offset)
                } else {
                    dao.getNewestPage(podcastId, limit, offset)
                }
            rows.map { it.toCatalogEpisode(meta) }
        }

    override suspend fun getWindow(
        podcastId: String,
        sort: String,
        bound: Int,
        aroundEpisodeId: String?,
        meta: PodcastMeta,
    ): List<Episode> =
        withContext(Dispatchers.IO) {
            val limit = bound.coerceAtLeast(1)
            val around = aroundEpisodeId?.let { dao.getEpisode(it) }
            val rows =
                if (around != null && around.podcastId == podcastId) {
                    continuationRows(dao, podcastId, sort, around, limit)
                } else if (sort == "oldest") {
                    dao.getOldestPage(podcastId, limit, 0)
                } else {
                    dao.getNewestPage(podcastId, limit, 0)
                }
            rows.map { it.toCatalogEpisode(meta) }
        }

    override suspend fun getEpisode(
        episodeId: String,
        meta: PodcastMeta,
    ): Episode? =
        withContext(Dispatchers.IO) {
            dao.getEpisode(episodeId)?.toCatalogEpisode(meta)
        }

    override suspend fun findByCatalogKey(
        podcastId: String,
        guid: String?,
        enclosureUrl: String?,
        meta: PodcastMeta,
    ): Episode? =
        withContext(Dispatchers.IO) {
            val wantedGuid = guid?.trim().orEmpty()
            if (wantedGuid.isNotEmpty()) {
                dao.getByGuid(podcastId, wantedGuid)?.let { return@withContext it.toCatalogEpisode(meta) }
            }
            val enclosure = enclosureUrl?.trim().orEmpty()
            if (enclosure.isEmpty()) return@withContext null
            dao.getByAudioUrl(podcastId, enclosure)?.toCatalogEpisode(meta)
        }

    override suspend fun search(
        podcastId: String,
        query: String,
        meta: PodcastMeta,
    ): List<Episode> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return@withContext emptyList()
            dao.search(podcastId, trimmed.escapeForSqlLike()).map { it.toCatalogEpisode(meta) }
        }

    override suspend fun newest(
        podcastId: String,
        meta: PodcastMeta,
    ): Episode? =
        withContext(Dispatchers.IO) {
            dao.getNewest(podcastId)?.toCatalogEpisode(meta)
        }

    override suspend fun count(podcastId: String): Int =
        withContext(Dispatchers.IO) {
            dao.count(podcastId)
        }

    override suspend fun isPublisherFeedUnchanged(
        podcastId: String,
        feedUrl: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val existing = dao.getFeed(podcastId) ?: return@withContext false
            if (existing.feedEtag.isNullOrBlank() && existing.feedLastModified.isNullOrBlank()) {
                return@withContext false
            }
            val url =
                LocalEpisodeCatalogRepository.resolveHttps(feedUrl, existing.feedUrl)
                    ?: return@withContext false
            if (url != existing.feedUrl.trim()) {
                return@withContext false
            }
            try {
                isFeedUnchanged(url, existing.feedEtag, existing.feedLastModified)
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun refresh(request: RefreshRequest): RefreshOutcome =
        RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)

    override suspend fun markFeedUrlLookup(
        podcastId: String,
        atMillis: Long,
    ) = withContext(Dispatchers.IO) {
        if (dao.getFeed(podcastId) != null) {
            dao.setFeedUrlLookupAt(podcastId, atMillis)
            return@withContext
        }
        dao.upsertFeed(
            LocalEpisodeCatalogRepository.stubFeed(podcastId, feedUrlLookupAt = atMillis),
        )
    }

    override suspend fun lastFeedUrlLookupAt(podcastId: String): Long =
        withContext(Dispatchers.IO) {
            dao.getFeed(podcastId)?.feedUrlLookupAt ?: 0L
        }

    override suspend fun setUnsubscribedTtl(
        podcastId: String,
        ttlExpiresAt: Long?,
    ) = withContext(Dispatchers.IO) {
        if (dao.getFeed(podcastId) == null) return@withContext
        dao.setTtl(podcastId, ttlExpiresAt)
    }

    override suspend fun sweepExpired(nowMillis: Long) =
        withContext(Dispatchers.IO) {
            for (id in dao.listExpiredFeedIds(nowMillis)) {
                dao.deleteCatalogIfExpired(id, nowMillis)
            }
        }
}

internal fun LocalEpisodeEntity.toCatalogEpisode(meta: PodcastMeta): Episode =
    toEpisode(
        podcastTitle = meta.title,
        podcastImageUrl = meta.imageUrl,
        podcastGenre = meta.genre,
        podcastArtist = meta.artist,
    )

internal suspend fun continuationRows(
    dao: LocalEpisodeCatalogDao,
    podcastId: String,
    sort: String,
    around: LocalEpisodeEntity,
    limit: Int,
): List<LocalEpisodeEntity> {
    val restLimit = (limit - 1).coerceAtLeast(0)
    if (restLimit == 0) return listOf(around)
    val rest =
        if (sort == "oldest") {
            dao.getOlderThan(podcastId, around.publishedDate, around.episodeId, restLimit)
        } else {
            dao.getNewerThan(podcastId, around.publishedDate, around.episodeId, restLimit)
        }
    return listOf(around) + rest
}
