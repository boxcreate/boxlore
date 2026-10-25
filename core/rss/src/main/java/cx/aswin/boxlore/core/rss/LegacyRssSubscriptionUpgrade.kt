package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.RssEpisodeIdentity
import cx.aswin.boxlore.core.model.Podcast
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

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
    fun isExactTarget(snapshot: LegacyRssFeedSnapshot, target: Podcast,): Boolean {
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

    /** Where [copiedLocalRows] writes: the Podcast Index show, the RSS namespace its IDs were minted in, and artwork. */
    data class CopyTarget(val podcastIndexId: String, val rssNamespaceId: String, val channelImageUrl: String?, val showImageUrl: String?,)

    /**
     * The local catalog rows an upgrade copies: the feed's items under their stored RSS IDs
     * ([StickyRssEpisodeRemap.remapItems]), then the stored RSS rows the feed no longer lists, so
     * every stored ID (and the listener state on it) carries over. Feed items come first, in
     * document order, so a key a feed item and a leftover stored row share keeps the feed item.
     * Each row is known by its catalog key: a GUID-less feed item by its enclosure URL. No recipe
     * ID is minted here ([NewEpisodeIds.NONE]): every row carries the RSS ID the remap gave it, as
     * before P4 PR 9.
     *
     * Added in P4 PR 8 (own-IDs plan section 4: first in the document wins).
     * COMPAT(own-ids): the legacy upgrade keeps every RSS episode ID. Permanent: never remove (MIGRATION.md §5).
     */
    fun copiedLocalRows(
        feedItems: List<ParsedFeedItem>,
        stored: List<RssEpisodeEntity>,
        storedIdentities: List<RssEpisodeIdentity>,
        target: CopyTarget,
    ): List<LocalEpisodeEntity> = copiedPlannedRows(feedItems, stored, storedIdentities, target).map { it.entity }

    /**
     * [copiedLocalRows] with each row's plan, so the upgrade can record an alias for every row
     * whose key follows the identity contract ([PlannedEpisodeRow.keyFollowsContract]).
     *
     * Added in P4 PR 9 (own-IDs plan section 5: every catalog ID of a Podcast Index show has an alias).
     * COMPAT(own-ids): the legacy upgrade keeps every RSS episode ID. Permanent: never remove (MIGRATION.md §5).
     */
    fun copiedPlannedRows(
        feedItems: List<ParsedFeedItem>,
        stored: List<RssEpisodeEntity>,
        storedIdentities: List<RssEpisodeIdentity>,
        target: CopyTarget,
    ): List<PlannedEpisodeRow> {
        val refreshed = StickyRssEpisodeRemap.remapItems(parsed = feedItems, existing = storedIdentities)
        val refreshedIds = refreshed.mapTo(HashSet()) { it.episode.episodeId }
        val firstStoredIndex = (refreshed.maxOfOrNull(ParsedFeedItem::documentIndex) ?: -1) + 1
        val combined =
            refreshed +
                stored
                    .filterNot { it.episodeId in refreshedIds }
                    .distinctBy(RssEpisodeEntity::episodeId)
                    .asParsedFeedItems(firstIndex = firstStoredIndex)
        val identities =
            combined.map { item ->
                LocalEpisodeIdentity(
                    episodeId = item.episode.episodeId,
                    guid = item.catalogKey.orEmpty(),
                    audioUrl = item.episode.audioUrl,
                )
            }
        return LocalEpisodeCatalogPersist.plan(
            podcastIndexId = target.podcastIndexId,
            rssNamespaceId = target.rssNamespaceId,
            parsed = combined,
            existing = identities,
            newIds = NewEpisodeIds.NONE,
            channelImageUrl = target.channelImageUrl,
            showImageUrl = target.showImageUrl,
        )
    }

    fun listenerIdsResolve(rows: Collection<String>, listenerIds: Collection<String>,): Boolean = listenerIds.all { it in rows || it.toLongOrNull()?.let { value -> value > 0L } == true }

    fun targetEntity(
        source: PodcastEntity,
        target: Podcast,
        snapshot: LegacyRssFeedSnapshot,
        latestEpisode: cx.aswin.boxlore.core.model.Episode?,
        nowMillis: Long,
    ): PodcastEntity = PodcastEntity(
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
    suspend fun inspect(podcastId: String): Result<LegacyRssFeedSnapshot> = withContext(Dispatchers.IO) {
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

    suspend fun upgrade(sourcePodcastId: String, target: Podcast, snapshot: LegacyRssFeedSnapshot,): LegacyRssUpgradeOutcome = withContext(Dispatchers.IO) {
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

internal class LegacyRssSubscriptionUpgrader(private val database: BoxLoreDatabase,) {
    suspend fun upgrade(sourcePodcastId: String, target: Podcast, snapshot: LegacyRssFeedSnapshot,): LegacyRssUpgradeOutcome = database.withTransaction {
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

    private suspend fun copiedCatalog(source: PodcastEntity, target: Podcast, snapshot: LegacyRssFeedSnapshot,): CopiedRssCatalog? {
        val rssDao = database.rssEpisodeDao()
        val planned =
            LegacyRssUpgradeLogic.copiedPlannedRows(
                feedItems = snapshot.parsed.items,
                stored = rssDao.getAllNewest(source.podcastId),
                storedIdentities = rssDao.listIdentities(source.podcastId),
                target =
                LegacyRssUpgradeLogic.CopyTarget(
                    podcastIndexId = target.id,
                    rssNamespaceId = source.podcastId,
                    channelImageUrl = snapshot.parsed.imageUrl,
                    showImageUrl = target.imageUrl.ifBlank { source.imageUrl },
                ),
            )
        val localRows = planned.map { it.entity }
        if (localRows.isEmpty() || !snapshot.finalFeedUrl.startsWith("https://", ignoreCase = true)) {
            return null
        }
        // The upgrade keeps the RSS IDs (listener state rides on them), so it can't re-mint around
        // another show that already holds one (the same feed listed twice), as a catalog row or as
        // an evicted episode's alias. It stays on RSS instead.
        val catalogDao = database.localEpisodeCatalogDao()
        val identityDao = database.episodeIdentityDao()
        val takesAnotherShowsId =
            LocalEpisodeCollisionGuard.takesAnotherShowsId(
                rows = localRows,
                podcastId = target.id,
                findOwners = { ids -> catalogDao.findOwners(ids) },
                findAliases = { ids -> identityDao.getAliases(ids) },
            )
        if (takesAnotherShowsId) {
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
            planned = planned,
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

    private suspend fun persistTargetCatalog(target: Podcast, snapshot: LegacyRssFeedSnapshot, catalog: CopiedRssCatalog,) {
        val localDao = database.localEpisodeCatalogDao()
        localDao.deleteCatalog(target.id)
        database.episodeSupplementDao().deleteItemsForPodcast(target.id)
        database.episodeSupplementDao().deleteSupplement(target.id)
        database.podcastDao().upsert(catalog.targetEntity)
        localDao.upsertEpisodes(catalog.localRows)
        recordAliases(target.id, catalog)
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

    /**
     * Records a trusted alias for each copied row whose key follows the identity contract, in the
     * upgrade's transaction ([EpisodeAliasProvenance.RSS_FOLLOW_UPGRADE]), so the show's episode IDs
     * keep resolving after its catalog is evicted, like every other catalog ID of a Podcast Index
     * show. An existing alias of the ID stays as it is, and a new one owns its episode only when no
     * trusted alias owns it yet ([CatalogAliasRecords]).
     *
     * Added in P4 PR 9 (own-IDs plan section 5: every catalog ID of a Podcast Index show has an alias).
     * COMPAT(own-ids): upgraded shows keep their IDs after eviction. Permanent: never remove (MIGRATION.md §5).
     */
    private suspend fun recordAliases(targetId: String, catalog: CopiedRssCatalog,) {
        val epNs = EpisodeNamespaces.choose(targetId, catalogEpNs = null, showEpNs = database.podcastDao().getEpNs(targetId)) ?: return
        val identityDao = database.episodeIdentityDao()
        val aliasesById =
            catalog.localRows
                .map { it.episodeId }
                .distinct()
                .chunked(UPGRADE_ALIAS_LOOKUP_CHUNK)
                .flatMap { identityDao.getAliases(it) }
                .associateBy { it.aliasId }
        val writes =
            CatalogAliasRecords.of(
                podcastId = targetId,
                planned = catalog.planned,
                resolution = LocalEpisodeCollisionGuard.Resolution(catalog.localRows, repaired = emptyList(), unplaced = emptyList()),
                aliasesById = aliasesById,
                showAliases = identityDao.aliasesForShow(targetId),
                newIds = NewEpisodeIds(epNs = epNs),
                nowMillis = catalog.nowMillis,
                storedRowProvenance = EpisodeAliasProvenance.RSS_FOLLOW_UPGRADE,
            )
        if (writes.inserts.isNotEmpty()) identityDao.insertAliasesIfAbsent(writes.inserts)
        writes.updates.forEach { identityDao.upsertAlias(it) }
    }

    private suspend fun reassignListenerReferences(sourcePodcastId: String, targetEntity: PodcastEntity,) {
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

    private suspend fun retireSource(source: PodcastEntity, targetId: String,) {
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
    val planned: List<PlannedEpisodeRow>,
    val localRows: List<LocalEpisodeEntity>,
    val targetEntity: PodcastEntity,
    val nowMillis: Long,
)

/** Stays under SQLite's bound-parameter limit. */
private const val UPGRADE_ALIAS_LOOKUP_CHUNK = 500
