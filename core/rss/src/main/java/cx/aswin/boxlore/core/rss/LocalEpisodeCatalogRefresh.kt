package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.LocalEpisodeCatalogDao
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeFeedEntity
import cx.aswin.boxlore.core.database.LocalFeedOrder
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshOutcome
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshReason
import cx.aswin.boxlore.core.domain.ports.LocalEpisodeCatalogPort.RefreshRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * @property identity aliases and namespaces (Room v39), P4 PR 9
 * @property recipeHash the recipe new IDs are minted with; tests force clashes through it (P4 PR 9)
 */
internal data class LocalCatalogRefreshDeps(
    val dao: LocalEpisodeCatalogDao,
    val feedClient: RssFeedClient,
    val runInTransaction: suspend (suspend () -> Unit) -> Unit,
    val isFeedUnchanged: suspend (url: String, etag: String?, lastModified: String?) -> Boolean,
    val reconcileListenerState: suspend (String, List<LocalEpisodeEntity>) -> Unit,
    val megaGetGate: Semaphore,
    val identity: CatalogIdentityStore,
    val recipeHash: RecipeIdHash = RecipeIdHash.RECIPE,
    val nowMillis: () -> Long = System::currentTimeMillis,
)

internal suspend fun refreshLocalCatalogLocked(deps: LocalCatalogRefreshDeps, request: RefreshRequest,): RefreshOutcome {
    if (!request.canProceed()) return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    if (request.podcastIndexId.isBlank() || request.podcastIndexId.startsWith("rss:")) {
        return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    }
    val existing = deps.dao.getFeed(request.podcastIndexId)
    val url =
        LocalEpisodeCatalogRepository.resolveHttps(request.feedUrl, existing?.feedUrl)
            ?: return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    if (url == existing?.feedUrl?.trim() && shouldSkipQuiet(existing, request.reason)) {
        return RefreshOutcome.Unchanged(
            deps.dao.getNewest(request.podcastIndexId)?.toCatalogEpisode(request.meta),
        )
    }
    val forceFetch = request.reason == RefreshReason.NEW_RELEASE || request.reason == RefreshReason.MANUAL
    if (!forceFetch &&
        existing != null &&
        publisherFeedUnchanged(deps, request.podcastIndexId, url)
    ) {
        if (!request.canProceed()) return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
        deps.dao.upsertFeed(existing.copy(fetchedAt = System.currentTimeMillis()))
        return RefreshOutcome.Unchanged(
            deps.dao.getNewest(request.podcastIndexId)?.toCatalogEpisode(request.meta),
        )
    }
    return fetchAndPersist(deps, request, url, existing)
}

private suspend fun publisherFeedUnchanged(deps: LocalCatalogRefreshDeps, podcastId: String, url: String,): Boolean {
    val existing = deps.dao.getFeed(podcastId) ?: return false
    if (!LocalCatalogReadyLogic.isReady(existing)) return false
    if (existing.feedEtag.isNullOrBlank() && existing.feedLastModified.isNullOrBlank()) {
        return false
    }
    val resolved =
        LocalEpisodeCatalogRepository.resolveHttps(url, existing.feedUrl) ?: return false
    if (resolved != existing.feedUrl.trim()) {
        return false
    }
    return try {
        deps.isFeedUnchanged(resolved, existing.feedEtag, existing.feedLastModified)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        false
    }
}

internal fun shouldSkipQuiet(
    existing: LocalEpisodeFeedEntity?,
    reason: RefreshReason = RefreshReason.NORMAL,
    nowMillis: Long = System.currentTimeMillis(),
): Boolean {
    if (reason == RefreshReason.NEW_RELEASE || reason == RefreshReason.MANUAL) return false
    if (existing == null) return false
    if (!LocalCatalogReadyLogic.isReady(existing)) return false
    if (existing.needsFullBackfill) return false
    if (existing.fetchedAt <= 0L) return false
    return nowMillis - existing.fetchedAt in 0 until LocalEpisodeCatalogRepository.QUIET_INTERVAL_MS
}

private suspend fun fetchAndPersist(
    deps: LocalCatalogRefreshDeps,
    request: RefreshRequest,
    url: String,
    existing: LocalEpisodeFeedEntity?,
): RefreshOutcome = try {
    deps.megaGetGate.withPermit {
        if (!request.canProceed()) return@withPermit RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
        val fetched = deps.feedClient.fetch(url)
        if (!request.canProceed()) return@withPermit RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
        val rssNamespaceId = RssIdGenerator.podcastId(fetched.finalUrl)
        val parsed =
            deps.feedClient.parse(
                feedUrl = fetched.finalUrl,
                bytes = fetched.body,
                podcastId = rssNamespaceId,
            )
        if (!request.canProceed()) return@withPermit RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
        persistParsed(deps, request, existing, fetched, rssNamespaceId, parsed)
    }
} catch (error: CancellationException) {
    throw error
} catch (error: Exception) {
    RefreshOutcome.Failure(
        LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE,
        addressGone = FeedReadFailure.isAddressGone(error),
        reportStatus = FeedReadFailure.reportStatus(error),
    )
}

/**
 * How a write records the show's feed row besides its episodes.
 *
 * - [complete]: the read held the whole feed, so the copy is the complete archive
 *   ([LocalEpisodeFeedEntity.archiveComplete]); a read of the newest items only leaves it as it was.
 * - [showPage]: a show-page read ([ShowPageFeedCatalog]). It never makes a copy the followed live
 *   list, and it keeps what follow and unfollow wrote: the stored row's `ready` and unsubscribe TTL
 *   are read again inside the write's transaction and kept, so a recently unfollowed show keeps its
 *   cheap resubscribe and its TTL sweep, and an unfollow that lands during the read stays.
 * - [documentOrder]: the feed's order classified in document order, for a row that has none yet.
 * - [openedAt]: when the show page opened the show (epoch ms), kept from the automatic cleanup.
 * - [recordedAddress]: the address the row records instead of the one read; boxlore's transport
 *   address of an http-only feed is never recorded.
 *
 * The defaults are a followed show's refresh, which reads the whole feed.
 *
 * Added in P4 PR 16 of the own-episode-IDs plan (plan section 11, feed-first show page: `archiveComplete`
 * is separate from ready).
 * COMPAT(own-ids): feed-first catalog state. Permanent: never remove (MIGRATION.md §5).
 */
internal data class FeedRowPolicy(
    val complete: Boolean = true,
    val showPage: Boolean = false,
    val documentOrder: String? = null,
    val openedAt: Long? = null,
    val recordedAddress: String? = null,
) {
    /** A stored order other than mixed stays; else the document order, else the parsed dates. */
    fun feedOrder(existing: LocalEpisodeFeedEntity?, parsed: ParsedRssFeed): String = existing
        ?.feedOrder
        ?.takeIf { it != LocalFeedOrder.MIXED }
        ?: documentOrder
        ?: FeedOrderLogic.classify(parsed.episodes.map { it.publishedDate })

    /**
     * A show-page read keeps the stored row's `ready` ([current], read in the write's transaction);
     * a followed show's refresh flips once the count is complete.
     */
    fun ready(current: LocalEpisodeFeedEntity?, storedCount: Int, copied: Int): Boolean = if (showPage) {
        current?.ready == true
    } else {
        LocalCatalogReadyLogic.isReadyToFlip(feedReady = storedCount >= copied)
    }

    /** A show-page read keeps the stored unsubscribe TTL ([current]); a followed show's refresh clears it. */
    fun ttlExpiresAt(current: LocalEpisodeFeedEntity?): Long? = if (showPage) current?.ttlExpiresAt else null

    /**
     * Whether a read of the newest items left a gap: the copy had rows, yet none of the items read
     * was one of them, so more items than the read holds came out since the last read. The copy is
     * then no longer the whole archive.
     */
    fun leavesGap(existingRows: Int, planned: List<PlannedEpisodeRow>): Boolean =
        !complete && existingRows > 0 && planned.none { it.source == PlannedIdSource.STORED }

    /** A read of the newest items leaves a pending backfill pending. */
    fun needsFullBackfill(existing: LocalEpisodeFeedEntity?): Boolean = !complete && (existing?.needsFullBackfill ?: true)

    /**
     * The v39 columns [LocalEpisodeCatalogDao.upsertFeed] keeps, written on their own: the archive
     * state ([gap]: see [leavesGap]), the opening time, and `followed` for a row this write created
     * ([created]), from `podcasts.isSubscribed`.
     */
    suspend fun writeFeedFirstColumns(dao: LocalEpisodeCatalogDao, podcastId: String, created: Boolean, gap: Boolean) {
        if (complete) dao.setArchiveComplete(podcastId, true)
        if (gap) dao.setArchiveComplete(podcastId, false)
        openedAt?.let { dao.setLastOpenedAt(podcastId, it) }
        if (created) dao.seedFollowed(podcastId)
    }
}

@Suppress("LongParameterList", "kotlin:S107") // One write: the read, its parse and how the row is recorded.
internal suspend fun persistParsed(
    deps: LocalCatalogRefreshDeps,
    request: RefreshRequest,
    existing: LocalEpisodeFeedEntity?,
    fetched: RssFetchResult,
    rssNamespaceId: String,
    parsed: ParsedRssFeed,
    policy: FeedRowPolicy = FeedRowPolicy(),
): RefreshOutcome {
    val identities = deps.dao.listIdentities(request.podcastIndexId)
    val newIds = newEpisodeIds(deps, request, existing)
    val planned =
        LocalEpisodeCatalogPersist.plan(
            podcastIndexId = request.podcastIndexId,
            rssNamespaceId = rssNamespaceId,
            parsed = parsed.items,
            existing = identities,
            newIds = newIds,
            channelImageUrl = parsed.imageUrl,
            showImageUrl = request.meta.imageUrl,
        )
    val rows = planned.map { it.entity }
    if (rows.isEmpty()) {
        // The address answered, but with nothing we can read as episodes.
        return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE, addressGone = true, reportStatus = FeedReadFailure.PARSE)
    }
    val copied = existing?.copiedExtrasCount ?: 0
    val feedOrder = policy.feedOrder(existing, parsed)
    var written = rows
    var resolution: LocalEpisodeCollisionGuard.Resolution? = null
    var storedCount = rows.size
    var ready = false
    var incomplete = false
    deps.runInTransaction {
        // Another show reading the same feed may already hold these IDs, and a stored row of this
        // show may hold the key: never replace either.
        val resolved =
            LocalEpisodeCollisionGuard.resolve(
                rows = rows,
                rssNamespaceId = rssNamespaceId,
                findOwners = { ids -> deps.dao.findOwners(ids) },
                listShowRows = { podcastId -> deps.dao.listIdentities(podcastId) },
                findAliases = { ids -> deps.identity.aliases(ids) },
                fallbackIds = planned.map { it.fallbackIds },
            )
        resolution = resolved
        written = resolved.rows
        if (resolved.unplaced.isNotEmpty()) {
            // A row found no free ID: write nothing rather than mark a partial catalog complete.
            incomplete = true
            return@runInTransaction
        }
        deps.dao.upsertEpisodes(written)
        recordAliases(deps, request.podcastIndexId, planned, resolved, newIds)
        deps.reconcileListenerState(request.podcastIndexId, written)
        val current = deps.dao.getFeed(request.podcastIndexId)
        storedCount = maxOf(deps.dao.count(request.podcastIndexId), written.size)
        ready = policy.ready(current, storedCount, copied)
        deps.dao.upsertFeed(
            LocalEpisodeFeedEntity(
                podcastId = request.podcastIndexId,
                feedUrl = policy.recordedAddress ?: fetched.finalUrl,
                feedEtag = fetched.etag,
                feedLastModified = fetched.lastModified,
                fetchedAt = System.currentTimeMillis(),
                itemCount = storedCount,
                feedOrder = feedOrder,
                ttlExpiresAt = policy.ttlExpiresAt(current),
                needsFullBackfill = policy.needsFullBackfill(current ?: existing),
                copiedExtrasCount = copied,
                ready = ready,
                feedUrlLookupAt = existing?.feedUrlLookupAt ?: 0L,
                epNs = newIds.epNs,
            ),
        )
        newIds.epNs?.let { epNs ->
            // A namespace is set only where none is stored (upsertFeed keeps a stored one).
            deps.dao.setEpNsIfUnset(request.podcastIndexId, epNs)
            deps.identity.keepShowNamespace(request.podcastIndexId, epNs)
        }
        policy.writeFeedFirstColumns(
            deps.dao,
            request.podcastIndexId,
            created = current == null,
            gap = policy.leavesGap(identities.size, planned),
        )
    }
    resolution?.let { logResolution(request.podcastIndexId, it, written = !incomplete) }
    if (incomplete) return RefreshOutcome.Failure(LocalEpisodeCatalogRepository.FEED_LOAD_FAILED_MESSAGE)
    val newestRow = written.maxByOrNull { it.publishedDate }
    return RefreshOutcome.Success(
        newest = newestRow?.toCatalogEpisode(request.meta),
        itemCount = storedCount,
        ready = ready,
        movedTo = if (request.detectMove) confirmedMove(deps.feedClient, request, fetched, parsed) else null,
    )
}

private const val COLLISION_LOG_TAG = "LocalEpisodeCatalog"

/**
 * How this refresh picks IDs for items no stored row holds: the show's namespace
 * ([EpisodeNamespaces.choose]: the catalog's stored epNs, else the show's, else the show ID) and,
 * when the show takes recipe IDs, its aliases and the IDs only its listener rows or unresolved
 * aliases hold ([ListenerEvidence]). A namespace the server reported ([RefreshRequest.serverEpNs])
 * is only compared: a different value changes no ID and is logged (decision D12).
 *
 * Added in P4 PR 9 (own-IDs plan section 4, recipe IDs for new episodes; section 5, unresolved
 * aliases are retried when the feed is read; decision D12).
 * COMPAT(own-ids): new IDs are minted in the stored namespace. Permanent: never remove (MIGRATION.md §5).
 */
private suspend fun newEpisodeIds(deps: LocalCatalogRefreshDeps, request: RefreshRequest, existing: LocalEpisodeFeedEntity?,): NewEpisodeIds {
    val podcastId = request.podcastIndexId
    val showEpNs = deps.identity.showNamespace(podcastId)
    val catalogEpNs = existing?.epNs
    if (catalogEpNs != null && showEpNs != null && catalogEpNs != showEpNs) {
        android.util.Log.w(COLLISION_LOG_TAG, "Show $podcastId: catalog namespace $catalogEpNs differs from the show's $showEpNs; keeping the catalog's")
    }
    val epNs = EpisodeNamespaces.choose(podcastId, catalogEpNs, showEpNs) ?: return NewEpisodeIds.NONE
    if (EpisodeNamespaces.differs(epNs, request.serverEpNs)) {
        android.util.Log.w(COLLISION_LOG_TAG, "Show $podcastId: server namespace ${request.serverEpNs} differs from the stored $epNs; keeping $epNs")
    }
    val showAliases = deps.identity.aliasesForShow(podcastId)
    return NewEpisodeIds(
        epNs = epNs,
        aliases = showAliases,
        hash = deps.recipeHash,
        listeners = listenerEvidence(deps, podcastId, showAliases),
    )
}

/**
 * The IDs only [podcastId]'s listener rows or unresolved aliases hold ([ListenerEvidence]): read
 * before the write's transaction, like the rest of the plan; the guard checks each ID again inside it.
 *
 * Added in P4 PR 9 (own-IDs plan section 4: nothing is re-keyed; section 5: unresolved aliases).
 */
private suspend fun listenerEvidence(deps: LocalCatalogRefreshDeps, podcastId: String, showAliases: List<EpisodeAliasEntity>,): ListenerEvidence {
    val references = deps.identity.listenerReferences(podcastId)
    val candidates = ListenerEvidence.candidateIds(references, showAliases)
    if (candidates.isEmpty()) return ListenerEvidence.NONE
    val catalogHeld = HashSet<String>()
    val aliasesById = HashMap<String, EpisodeAliasEntity>()
    for (chunk in candidates.chunked(ALIAS_LOOKUP_CHUNK)) {
        deps.dao.findOwners(chunk).mapTo(catalogHeld) { it.episodeId }
        deps.identity.aliases(chunk).associateByTo(aliasesById) { it.aliasId }
    }
    return ListenerEvidence.of(podcastId, references, showAliases, catalogHeld, aliasesById)
}

/**
 * Writes the aliases [CatalogAliasRecords] derives from this refresh, in its transaction: one per
 * new recipe, reserved or listener-held ID, one per stored row that lacked one, the provenance of
 * every healed key, and the key of every unresolved alias of this show the read settles (it becomes
 * trusted).
 *
 * Added in P4 PR 9 (own-IDs plan section 4, collisions and synthetic keys; section 5, aliases).
 * COMPAT(own-ids): every catalog ID keeps resolving after eviction. Permanent: never remove (MIGRATION.md §5).
 */
private suspend fun recordAliases(
    deps: LocalCatalogRefreshDeps,
    podcastId: String,
    planned: List<PlannedEpisodeRow>,
    resolution: LocalEpisodeCollisionGuard.Resolution,
    newIds: NewEpisodeIds,
) {
    if (newIds.epNs == null) return
    val aliasesById =
        resolution.rows
            .map { it.episodeId }
            .distinct()
            .chunked(ALIAS_LOOKUP_CHUNK)
            .flatMap { deps.identity.aliases(it) }
            .associateBy { it.aliasId }
    val writes =
        CatalogAliasRecords.of(
            podcastId = podcastId,
            planned = planned,
            resolution = resolution,
            aliasesById = aliasesById,
            showAliases = deps.identity.aliasesForShow(podcastId),
            newIds = newIds,
            nowMillis = deps.nowMillis(),
        )
    if (writes.isEmpty) return
    deps.identity.insertAliases(writes.inserts)
    writes.updates.forEach { deps.identity.updateAlias(it) }
}

/** Stays under SQLite's bound-parameter limit. */
private const val ALIAS_LOOKUP_CHUNK = 500

/**
 * Logs what [LocalEpisodeCollisionGuard] changed in one refresh: stored rows whose key was corrected
 * while they kept their ID (with where the old key came from), and rows left out because every ID
 * they could take belongs to another episode. Nothing is logged for an ordinary refresh.
 *
 * Added in P4 PR 1 (own-IDs plan section 4, legacy keys; section 8, no write replaces another
 * episode). Since P4 PR 9 the corrected keys' provenance is also stored on their aliases, in the
 * refresh transaction ([recordAliases]); this log stays for unplaced rows and diagnosis.
 * COMPAT(own-ids): reports corrected legacy keys and rows the guard could not place. Permanent: never remove (MIGRATION.md §5).
 */
private fun logResolution(podcastId: String, resolution: LocalEpisodeCollisionGuard.Resolution, written: Boolean,) {
    if (resolution.repaired.isNotEmpty()) {
        val byKind = resolution.repaired.groupingBy { it.repair }.eachCount()
        val state = if (written) "corrected" else "not written"
        android.util.Log.i(COLLISION_LOG_TAG, "Show $podcastId: stored keys $state, IDs kept: $byKind")
        for (repaired in resolution.repaired) {
            android.util.Log.d(COLLISION_LOG_TAG, "Show $podcastId: episode ${repaired.episodeId} key ${repaired.repair}")
        }
    }
    for (row in resolution.unplaced) {
        android.util.Log.w(
            COLLISION_LOG_TAG,
            "Show $podcastId: no free local ID for episode ${row.episodeId} after " +
                "${LocalEpisodeCollisionGuard.MAX_RESERVE_ATTEMPTS} reserved IDs; catalog not written",
        )
    }
}

/**
 * The feed's new permanent HTTPS address, or null (P4a). Before, a moved feed stayed on its old
 * address: OkHttp followed the redirect, but the saved address never changed, so every pass
 * downloaded the whole feed again and the address other devices and notifications use stayed old.
 * Adopted only when the new address is HTTPS, differs from the saved one and serves the same show
 * (same title as [RefreshRequest.meta]), as the server's address checks do: from redirects that
 * were all permanent ([RssFetchResult.movedTo]), or from `itunes:new-feed-url`, read once to
 * confirm. Temporary redirects (302, 307) never move a feed.
 */
internal suspend fun confirmedMove(feedClient: RssFeedClient, request: RefreshRequest, fetched: RssFetchResult, parsed: ParsedRssFeed,): String? {
    val showTitle = request.meta.title?.takeIf(String::isNotBlank) ?: return null
    val saved = request.feedUrl.trim()
    fetched.movedTo
        ?.takeIf { isNewHttpsAddress(it, saved) && sameShow(parsed.title, showTitle) }
        ?.let { return it }
    val declared =
        parsed.newFeedUrl
            ?.trim()
            ?.takeIf { isNewHttpsAddress(it, saved) && isNewHttpsAddress(it, fetched.finalUrl) }
            ?: return null
    return try {
        val next = feedClient.fetch(declared)
        val nextParsed = feedClient.parse(next.finalUrl, next.body, RssIdGenerator.podcastId(next.finalUrl))
        next.finalUrl.takeIf { isNewHttpsAddress(it, saved) && sameShow(nextParsed.title, showTitle) }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        null
    }
}

private fun isNewHttpsAddress(candidate: String, current: String): Boolean =
    candidate.startsWith("https://", ignoreCase = true) && !candidate.trim().equals(current.trim(), ignoreCase = true)

/** Same show by title, ignoring case, spacing and punctuation. */
internal fun sameShow(feedTitle: String, showTitle: String): Boolean {
    val normalize = { value: String -> value.lowercase(java.util.Locale.ROOT).filter(Char::isLetterOrDigit) }
    val a = normalize(feedTitle)
    return a.isNotEmpty() && a == normalize(showTitle)
}
