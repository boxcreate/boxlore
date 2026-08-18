package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.model.Podcast
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap

data class LegacyRssFeedSnapshot(
    val sourceFeedUrl: String,
    val finalFeedUrl: String,
    val podcastGuid: String?,
    val etag: String?,
    val lastModified: String?,
    val parsed: ParsedRssFeed,
)

enum class LegacyRssUpgradeOutcome {
    MIGRATED,
    ALREADY_MIGRATED,
    SOURCE_NOT_ELIGIBLE,
    TARGET_ALREADY_IN_USE,
    IDENTITY_MISMATCH,
    INCOMPLETE_CATALOG,
}

internal object LegacyRssUpgradeLogic {
    fun isExactTarget(
        snapshot: LegacyRssFeedSnapshot,
        target: Podcast,
    ): Boolean {
        val sourceKeys =
            listOf(snapshot.sourceFeedUrl, snapshot.finalFeedUrl)
                .mapNotNull(::canonicalFeedKey)
                .toSet()
        val targetKey = canonicalFeedKey(target.feedUrl)
        val sameUrl = targetKey != null && targetKey in sourceKeys
        val sameGuid =
            !snapshot.podcastGuid.isNullOrBlank() &&
                snapshot.podcastGuid.equals(target.podcastGuid, ignoreCase = true)
        return sameUrl || sameGuid
    }

    fun listenerIdsResolve(
        rows: Collection<String>,
        listenerIds: Collection<String>,
    ): Boolean = listenerIds.all { it in rows || it.toLongOrNull()?.let { value -> value > 0L } == true }

    fun targetEntity(
        source: PodcastEntity,
        target: Podcast,
        snapshot: LegacyRssFeedSnapshot,
        latestEpisode: cx.aswin.boxlore.core.model.Episode?,
        nowMillis: Long,
    ): PodcastEntity =
        PodcastEntity(
            podcastId = target.id,
            title = target.title,
            author = target.artist,
            imageUrl = target.imageUrl.ifBlank { source.imageUrl },
            description = target.description ?: source.description,
            isSubscribed = true,
            subscribedAt = source.subscribedAt,
            genre = target.genre,
            type = target.type,
            lastRefreshed = nowMillis,
            latestEpisode = latestEpisode,
            podcastGuid = target.podcastGuid ?: snapshot.podcastGuid,
            fundingUrl = target.fundingUrl,
            fundingMessage = target.fundingMessage,
            medium = target.medium,
            hasValue = target.hasValue,
            updateFrequency = target.updateFrequency,
            location = target.location,
            license = target.license,
            isLocked = target.isLocked,
            preferredSort = source.preferredSort,
            notificationsEnabled = source.notificationsEnabled,
            autoDownloadEnabled = source.autoDownloadEnabled,
            skipBeginningOverrideMs = source.skipBeginningOverrideMs,
            skipEndingOverrideMs = source.skipEndingOverrideMs,
            sourceType = PodcastEntity.SOURCE_PODCAST_INDEX,
            feedUrl = snapshot.finalFeedUrl,
            rssHasNewEpisodes = source.rssHasNewEpisodes,
        )

    private fun canonicalFeedKey(raw: String?): String? {
        val url = raw?.trim()?.toHttpUrlOrNull() ?: return null
        val host = url.host.lowercase().removePrefix("www.")
        val path = url.encodedPath.trimEnd('/').ifEmpty { "/" }
        val query = url.encodedQuery?.let { "?$it" }.orEmpty()
        return "$host$path$query"
    }
}

class LegacyRssSubscriptionRepair internal constructor(
    private val database: BoxLoreDatabase,
    private val feedClient: RssFeedClient,
    private val refreshLocks: ConcurrentHashMap<String, Mutex>,
) {
    private val upgrader = LegacyRssSubscriptionUpgrader(database)

    /**
     * Loads the current publisher feed without mutating Room. The snapshot verifies exact
     * URL/GUID identity and seeds the PI-owned local catalog atomically.
     */
    suspend fun inspect(podcastId: String): Result<LegacyRssFeedSnapshot> =
        withContext(Dispatchers.IO) {
            val lock = refreshLocks.getOrPut(podcastId) { Mutex() }
            lock.withLock {
                try {
                    val source =
                        database.podcastDao().getPodcast(podcastId)
                            ?: error("RSS subscription not found")
                    require(source.isSubscribed && source.isRss) {
                        "Podcast is not a subscribed RSS source"
                    }
                    val sourceFeedUrl = source.feedUrl ?: error("RSS feed URL is missing")
                    val fetched = feedClient.fetch(sourceFeedUrl)
                    val parsed =
                        feedClient.parse(
                            feedUrl = fetched.finalUrl,
                            bytes = fetched.body,
                            podcastId = podcastId,
                        )
                    Result.success(
                        LegacyRssFeedSnapshot(
                            sourceFeedUrl = sourceFeedUrl,
                            finalFeedUrl = fetched.finalUrl,
                            podcastGuid = parsed.podcastGuid ?: source.podcastGuid,
                            etag = fetched.etag,
                            lastModified = fetched.lastModified,
                            parsed = parsed,
                        ),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Result.failure(error)
                }
            }
        }

    suspend fun upgrade(
        sourcePodcastId: String,
        target: Podcast,
        snapshot: LegacyRssFeedSnapshot,
    ): LegacyRssUpgradeOutcome =
        withContext(Dispatchers.IO) {
            val lock = refreshLocks.getOrPut(sourcePodcastId) { Mutex() }
            lock.withLock {
                upgrader.upgrade(
                    sourcePodcastId = sourcePodcastId,
                    target = target,
                    snapshot = snapshot,
                )
            }
        }
}

internal class LegacyRssSubscriptionUpgrader(
    private val database: BoxLoreDatabase,
) {
    suspend fun upgrade(
        sourcePodcastId: String,
        target: Podcast,
        snapshot: LegacyRssFeedSnapshot,
    ): LegacyRssUpgradeOutcome =
        database.withTransaction {
            val source =
                eligibleSource(sourcePodcastId)
                    ?: return@withTransaction ineligibleSourceOutcome(target.id)
            if (!LegacyRssUpgradeLogic.isExactTarget(snapshot, target)) {
                return@withTransaction LegacyRssUpgradeOutcome.IDENTITY_MISMATCH
            }
            if (targetIsInUse(target.id)) {
                return@withTransaction LegacyRssUpgradeOutcome.TARGET_ALREADY_IN_USE
            }
            val catalog =
                copiedCatalog(source, target, snapshot)
                    ?: return@withTransaction LegacyRssUpgradeOutcome.INCOMPLETE_CATALOG
            persistTargetCatalog(target, snapshot, catalog)
            reassignListenerReferences(sourcePodcastId, catalog.targetEntity)
            retireSource(source, target.id)
            LegacyRssUpgradeOutcome.MIGRATED
        }

    private suspend fun eligibleSource(sourcePodcastId: String): PodcastEntity? {
        val source = database.podcastDao().getPodcast(sourcePodcastId)
        return source?.takeIf { it.isSubscribed && it.isRss }
    }

    private suspend fun ineligibleSourceOutcome(targetId: String): LegacyRssUpgradeOutcome {
        val migrated = database.podcastDao().getPodcast(targetId)
        return if (migrated?.isSubscribed == true && !migrated.isRss) {
            LegacyRssUpgradeOutcome.ALREADY_MIGRATED
        } else {
            LegacyRssUpgradeOutcome.SOURCE_NOT_ELIGIBLE
        }
    }

    private suspend fun copiedCatalog(
        source: PodcastEntity,
        target: Podcast,
        snapshot: LegacyRssFeedSnapshot,
    ): CopiedRssCatalog? {
        val rssDao = database.rssEpisodeDao()
        val stored = rssDao.getAllNewest(source.podcastId)
        val refreshed =
            StickyRssEpisodeRemap.remap(
                parsed = snapshot.parsed.episodes,
                existing = rssDao.listIdentities(source.podcastId),
            )
        val combined = (refreshed + stored).distinctBy(RssEpisodeEntity::episodeId)
        val identities =
            combined.map { row ->
                LocalEpisodeIdentity(
                    episodeId = row.episodeId,
                    guid = row.guid.orEmpty(),
                    audioUrl = row.audioUrl,
                )
            }
        val localRows =
            LocalEpisodeCatalogPersist.toLocalEpisodes(
                podcastIndexId = target.id,
                rssNamespaceId = source.podcastId,
                parsed = combined,
                existing = identities,
                piBaseline = null,
                channelImageUrl = snapshot.parsed.imageUrl,
                showImageUrl = target.imageUrl.ifBlank { source.imageUrl },
            )
        if (localRows.isEmpty() || !snapshot.finalFeedUrl.startsWith("https://", ignoreCase = true)) {
            return null
        }
        val listenerIds =
            (
                database.listeningHistoryDao().getHistoryForPodcast(source.podcastId).map { it.episodeId } +
                    database.downloadedEpisodeDao().getDownloadsForPodcast(source.podcastId).map { it.episodeId } +
                    database.queueDao().getEpisodeIdsForPodcast(source.podcastId)
            ).toSet()
        val localIds = localRows.mapTo(mutableSetOf()) { it.episodeId }
        if (!LegacyRssUpgradeLogic.listenerIdsResolve(localIds, listenerIds)) {
            return null
        }
        val newest = localRows.maxByOrNull { it.publishedDate }
        val now = System.currentTimeMillis()
        val latestEpisode =
            newest?.toEpisode(
                podcastTitle = target.title,
                podcastImageUrl = target.imageUrl.ifBlank { source.imageUrl },
                podcastGenre = target.genre,
                podcastArtist = target.artist,
            )
        return CopiedRssCatalog(
            localRows = localRows,
            targetEntity =
                LegacyRssUpgradeLogic.targetEntity(
                    source = source,
                    target = target,
                    snapshot = snapshot,
                    latestEpisode = latestEpisode,
                    nowMillis = now,
                ),
            nowMillis = now,
        )
    }

    private suspend fun persistTargetCatalog(
        target: Podcast,
        snapshot: LegacyRssFeedSnapshot,
        catalog: CopiedRssCatalog,
    ) {
        val localDao = database.localEpisodeCatalogDao()
        localDao.deleteCatalog(target.id)
        database.episodeSupplementDao().deleteItemsForPodcast(target.id)
        database.episodeSupplementDao().deleteSupplement(target.id)
        database.podcastDao().upsert(catalog.targetEntity)
        localDao.upsertEpisodes(catalog.localRows)
        localDao.upsertFeed(
            LocalEpisodeFeedEntity(
                podcastId = target.id,
                feedUrl = snapshot.finalFeedUrl,
                feedEtag = snapshot.etag,
                feedLastModified = snapshot.lastModified,
                fetchedAt = catalog.nowMillis,
                itemCount = catalog.localRows.size,
                feedOrder = FeedOrderLogic.classify(snapshot.parsed.episodes.map { it.publishedDate }),
                ttlExpiresAt = null,
                needsFullBackfill = false,
                copiedExtrasCount = 0,
                ready = true,
                feedUrlLookupAt = catalog.nowMillis,
            ),
        )
    }

    private suspend fun reassignListenerReferences(
        sourcePodcastId: String,
        targetEntity: PodcastEntity,
    ) {
        val historyDao = database.listeningHistoryDao()
        val downloadDao = database.downloadedEpisodeDao()
        val queueDao = database.queueDao()
        historyDao.getHistoryForPodcast(sourcePodcastId).forEach { row ->
            historyDao.upsert(
                row.copy(
                    podcastId = targetEntity.podcastId,
                    podcastImageUrl = targetEntity.imageUrl,
                    podcastName = targetEntity.title,
                ),
            )
        }
        downloadDao.getDownloadsForPodcast(sourcePodcastId).forEach { row ->
            downloadDao.insert(
                row.copy(
                    podcastId = targetEntity.podcastId,
                    podcastName = targetEntity.title,
                    podcastImageUrl = targetEntity.imageUrl,
                ),
            )
        }
        queueDao
            .getAllQueueItemsSync()
            .filter { it.podcastId == sourcePodcastId }
            .forEach { row ->
                queueDao.updateQueueItem(
                    row.copy(
                        podcastId = targetEntity.podcastId,
                        podcastTitle = targetEntity.title,
                        podcastGenre = targetEntity.genre.orEmpty(),
                        podcastArtist = targetEntity.author,
                        podcastImageUrl = targetEntity.imageUrl,
                    ),
                )
            }
        database.listeningSessionDao().reassignPodcastId(sourcePodcastId, targetEntity.podcastId)
        database.listeningRollupDao().reassignPodcastId(sourcePodcastId, targetEntity.podcastId)
    }

    private suspend fun retireSource(
        source: PodcastEntity,
        targetId: String,
    ) {
        database.podcastDao().upsert(
            source.copy(
                isSubscribed = false,
                subscribedAt = 0L,
                notificationsEnabled = false,
                autoDownloadEnabled = false,
                linkedPodcastIndexId = targetId,
            ),
        )
    }

    private suspend fun targetIsInUse(podcastId: String): Boolean {
        if (database.podcastDao().getPodcast(podcastId)?.isSubscribed == true) return true
        if (database.listeningHistoryDao().getHistoryForPodcast(podcastId).isNotEmpty()) return true
        if (database.downloadedEpisodeDao().getDownloadsForPodcast(podcastId).isNotEmpty()) return true
        return database.queueDao().getEpisodeIdsForPodcast(podcastId).isNotEmpty()
    }
}

private data class CopiedRssCatalog(
    val localRows: List<LocalEpisodeEntity>,
    val targetEntity: PodcastEntity,
    val nowMillis: Long,
)
