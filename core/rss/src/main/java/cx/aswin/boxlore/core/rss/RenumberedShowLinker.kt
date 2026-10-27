package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroupStateEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import kotlin.math.abs

/**
 * Links the listener rows of a show Podcast Index renumbered ([RenumberedShows]) to the episodes of
 * the show it now answers as, so those episodes read the old rows' state (played, progress, likes,
 * downloads) on the new show's page. No ID changes: history, queue and download rows keep their ID
 * and their old show; only aliases (and an episode's group settings) are written ([RenumberedShowLinks]):
 * - **IDs with no usable key** (no alias, or an unresolved one under the old show) join the one new
 *   show's episode their rows name by audio address, else title and date.
 * - **Episodes of the old show's catalog** (trusted aliases of an old show's (show, key), the shape
 *   of any show followed since episodes are keyed) join the new show's episode with that key: every
 *   trusted alias of the old (show, key) moves to the new show's (show, key) as a non-owner, and the
 *   old episode's group settings (like and completion decisions) merge into the new one's. The old
 *   show's own catalog copy, once its follow has ended, is dropped first, as its unfollowed
 *   countdown would drop it, so no catalog row of the old show holds those IDs.
 *
 * Linking is idempotent and runs inside every catalog read of the new show (from
 * [LocalEpisodeCatalogRepository]'s refresh transaction) and once when a renumbering is recorded,
 * so rows that arrive later (a cloud pull, a restore) are linked by the next read. It never
 * touches an ambiguous alias, an ID a catalog row holds, or an alias of another show.
 *
 * Added for renumbered shows (docs/EPISODE_IDS.md §5, renumbered shows).
 */
internal class RenumberedShowLinker(private val database: BoxLoreDatabase, private val shows: RenumberedShows,) {
    private val identityDao get() = database.episodeIdentityDao()
    private val catalogDao get() = database.localEpisodeCatalogDao()

    /** Links every show recorded as renumbered into [currentId]. Runs in the caller's transaction. */
    suspend fun linkFormerShows(currentId: String) {
        val formerIds = shows.formerIds(currentId)
        for (formerId in formerIds) link(formerId, currentId)
    }

    /** [link] in its own transaction; the number of IDs linked. */
    suspend fun linkInTransaction(formerId: String, currentId: String): Int = database.withTransaction { link(formerId, currentId) }

    /**
     * Links [formerId]'s history, queue and download IDs to [currentId]'s stored episodes. Runs in the
     * caller's transaction; returns the number of IDs linked.
     */
    suspend fun link(formerId: String, currentId: String): Int {
        if (!RenumberedShowIds.isRenumbering(formerId, currentId)) return 0
        val references = database.listenerReferences(formerId)
        if (references.isEmpty()) return 0
        val ids = references.map { it.episodeId }.distinct()
        val aliasesById = aliasesOf(ids)
        val trustedKeys = RenumberedShowLinks.trustedKeysOf(ids, aliasesById, formerId)
        if (trustedKeys.isEmpty() && ids.none { RenumberedShowLinks.isSettleable(aliasesById[it], formerId) }) return 0
        val rows = catalogDao.listForShow(currentId)
        if (rows.isEmpty()) return 0
        if (trustedKeys.isNotEmpty()) dropEndedCatalog(formerId)
        val catalogHeld = heldIds(ids)
        val rowAliasesById = aliasesOf(rows.map { it.episodeId })
        val epNs = namespaceOf(currentId)
        val nowMs = System.currentTimeMillis()
        val links =
            RenumberedShowLinks.plan(
                formerId = formerId,
                currentId = currentId,
                references = references,
                aliasesById = aliasesById,
                catalogRows = rows,
                rowAliasesById = rowAliasesById,
                catalogHeldIds = catalogHeld,
                epNs = epNs,
                nowMs = nowMs,
            )
        links.forEach { identityDao.upsertAlias(it) }
        var joined = 0
        for (key in trustedKeys) joined += joinGroup(formerId, currentId, key, rows, rowAliasesById, epNs)
        val linked = links.size + joined
        if (linked > 0) android.util.Log.i(LOG_TAG, "Show $currentId: linked $linked IDs of renumbered show $formerId")
        return linked
    }

    /**
     * Moves the trusted aliases of [formerId]'s episode [key] onto [currentId]'s episode with that
     * key, and merges its group settings there; the number of aliases moved (0 when nothing qualifies).
     */
    @Suppress("LongParameterList")
    private suspend fun joinGroup(
        formerId: String,
        currentId: String,
        key: String,
        rows: List<LocalEpisodeEntity>,
        rowAliasesById: Map<String, EpisodeAliasEntity>,
        epNs: Long?,
    ): Int {
        val group = identityDao.aliasesForKey(formerId, key).filter { it.state == EpisodeAliasState.TRUSTED }
        val moved =
            RenumberedShowLinks.joinedGroup(
                formerGroup = group,
                key = key,
                currentId = currentId,
                catalogRows = rows,
                rowAliasesById = rowAliasesById,
                catalogHeldIds = heldIds(group.map { it.aliasId }),
                epNs = epNs,
            )
        if (moved.isEmpty()) return 0
        moved.forEach { identityDao.upsertAlias(it) }
        val formerState = identityDao.getGroupState(formerId, key)
        RenumberedShowLinks.mergedGroupState(formerState, identityDao.getGroupState(currentId, key), currentId, key)?.let { identityDao.upsertGroupState(it) }
        if (formerState != null) identityDao.deleteGroupState(formerId, key)
        return moved.size
    }

    /** Drops [formerId]'s catalog copy once its follow ended, as its unfollowed countdown would; a followed show keeps it. */
    private suspend fun dropEndedCatalog(formerId: String) {
        if (database.podcastDao().getPodcast(formerId)?.isSubscribed == true) return
        if (catalogDao.getFeed(formerId) == null && catalogDao.listForShow(formerId).isEmpty()) return
        catalogDao.deleteCatalog(formerId)
    }

    private suspend fun heldIds(ids: List<String>): Set<String> =
        ids.distinct().chunked(MAX_IDS_PER_QUERY).flatMap { catalogDao.findOwners(it) }.mapTo(HashSet()) { it.episodeId }

    private suspend fun namespaceOf(podcastId: String): Long? =
        EpisodeNamespaces.choose(podcastId, catalogDao.getFeed(podcastId)?.epNs, database.podcastDao().getEpNs(podcastId))

    private suspend fun aliasesOf(ids: List<String>): Map<String, EpisodeAliasEntity> =
        ids.distinct().chunked(MAX_IDS_PER_QUERY).flatMap { identityDao.getAliases(it) }.associateBy { it.aliasId }

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
        const val LOG_TAG = "RenumberedShowLinker"
    }
}

/**
 * Which old IDs of a renumbered show join which episodes of the show it now answers as
 * ([RenumberedShowLinker]). An old ID joins one episode when:
 * - it has no alias, or an unresolved one under the old show (a v39 listener-only ID, an episode
 *   played before the show was followed), and no catalog row of any show holds it;
 * - its rows name exactly one of the new show's stored episodes: by exact audio address, else (for
 *   queue and download rows, which carry a date) by title and a date within two days, the rules a
 *   catalog read uses to keep a listener-held ID ([ListenerIdAdoption]);
 * - no other old ID names that episode; and
 * - that episode's own ID is a trusted, keyed alias of the new show, so there is an episode to join.
 * The old ID's alias becomes trusted under the new show with that episode's key and recipe ID,
 * provenance [EpisodeAliasProvenance.RENUMBERED_SHOW_MATCH], as a non-owner: ownership then follows
 * the owner rule (the ID with the latest listening owns). Anything weaker leaves the alias as it was.
 *
 * Added for renumbered shows (docs/EPISODE_IDS.md §5, renumbered shows).
 */
internal object RenumberedShowLinks {
    /** True when [alias] (an old ID's) may be settled: none yet, or unresolved under [formerId]. */
    fun isSettleable(alias: EpisodeAliasEntity?, formerId: String): Boolean =
        alias == null || (alias.state == EpisodeAliasState.UNRESOLVED && alias.podcastId == formerId)

    /** The keys of [formerId]'s episodes that [ids] (its listener rows' IDs) hold through a trusted alias. */
    fun trustedKeysOf(ids: Collection<String>, aliasesById: Map<String, EpisodeAliasEntity>, formerId: String): List<String> =
        ids
            .mapNotNull { id -> aliasesById[id]?.takeIf { it.state == EpisodeAliasState.TRUSTED && it.podcastId == formerId }?.catalogKey }
            .distinct()

    /**
     * The aliases of [formerGroup] (every trusted alias of the old show's episode [key]) moved onto
     * [currentId]'s episode with that key: trusted, with that episode's recipe ID, as non-owners,
     * provenance [EpisodeAliasProvenance.RENUMBERED_SHOW_MATCH], each keeping its ID and creation
     * time. Empty when a catalog row still holds one of the IDs ([catalogHeldIds]: the old show's
     * live copy), or [currentId] has no single stored episode with [key] whose own ID is a trusted
     * alias of it.
     */
    @Suppress("LongParameterList")
    fun joinedGroup(
        formerGroup: List<EpisodeAliasEntity>,
        key: String,
        currentId: String,
        catalogRows: List<LocalEpisodeEntity>,
        rowAliasesById: Map<String, EpisodeAliasEntity>,
        catalogHeldIds: Set<String>,
        epNs: Long?,
    ): List<EpisodeAliasEntity> {
        val group = formerGroup.filter { it.state == EpisodeAliasState.TRUSTED && it.catalogKey == key }
        if (group.isEmpty() || group.any { it.aliasId in catalogHeldIds }) return emptyList()
        val row = catalogRows.filter { keyOf(it) == key }.singleOrNull() ?: return emptyList()
        val rowAlias =
            rowAliasesById[row.episodeId]
                ?.takeIf { it.state == EpisodeAliasState.TRUSTED && it.podcastId == currentId && it.catalogKey == key }
                ?: return emptyList()
        val recipeId = rowAlias.recipeId ?: epNs?.let { runCatching { EpisodeRecipeId.forNamespace(it, key) }.getOrNull() }
        return group.map {
            it.copy(podcastId = currentId, recipeId = recipeId, owner = false, provenance = EpisodeAliasProvenance.RENUMBERED_SHOW_MATCH)
        }
    }

    /**
     * The old episode's group settings ([former]) merged into the new one's ([current]): each like
     * and completion decision keeps its latest time, as the projected state reads them. Null when
     * the old episode has none.
     */
    fun mergedGroupState(former: EpisodeGroupStateEntity?, current: EpisodeGroupStateEntity?, currentId: String, key: String): EpisodeGroupStateEntity? {
        former ?: return null
        val base = current ?: EpisodeGroupStateEntity(podcastId = currentId, catalogKey = key)
        return base.copy(
            likedAt = maxOf(base.likedAt, former.likedAt),
            likeClearedAt = maxOf(base.likeClearedAt, former.likeClearedAt),
            completedAt = maxOf(base.completedAt, former.completedAt),
            completeClearedAt = maxOf(base.completeClearedAt, former.completeClearedAt),
            version = maxOf(base.version, former.version) + 1L,
        )
    }

    /** [row]'s recipe-trimmed catalog key; null for a legacy row's synthetic `id:<its own ID>` key. */
    private fun keyOf(row: LocalEpisodeEntity): String? {
        if (row.legacy && row.guid == "id:${row.episodeId}") return null
        return EpisodeRecipeId.trimKey(row.guid)
    }

    @Suppress("LongParameterList")
    fun plan(
        formerId: String,
        currentId: String,
        references: List<LocalCatalogListenerReference>,
        aliasesById: Map<String, EpisodeAliasEntity>,
        catalogRows: List<LocalEpisodeEntity>,
        rowAliasesById: Map<String, EpisodeAliasEntity>,
        catalogHeldIds: Set<String>,
        epNs: Long?,
        nowMs: Long,
    ): List<EpisodeAliasEntity> {
        val targets =
            references
                .groupBy { it.episodeId }
                .filterKeys { id -> id !in catalogHeldIds && isSettleable(aliasesById[id], formerId) }
                .mapNotNull { (oldId, rows) -> targetOf(rows, catalogRows)?.let { oldId to it } }
        val claimants = targets.groupingBy { it.second.episodeId }.eachCount()
        return targets
            .filter { (_, row) -> claimants[row.episodeId] == 1 }
            .mapNotNull { (oldId, row) -> linkOf(oldId, row, currentId, rowAliasesById, aliasesById[oldId], epNs, nowMs) }
    }

    /** The one catalog row [rows] name: by audio address, else by title and date; null when none or several. */
    private fun targetOf(rows: List<LocalCatalogListenerReference>, catalogRows: List<LocalEpisodeEntity>,): LocalEpisodeEntity? {
        val byAudio =
            rows
                .mapNotNull { row -> row.enclosureUrl?.trim()?.takeIf(String::isNotEmpty) }
                .mapNotNull { audio -> catalogRows.singleOrNull { it.audioUrl.trim() == audio } }
                .distinctBy { it.episodeId }
        if (byAudio.isNotEmpty()) return byAudio.singleOrNull()
        return rows
            .mapNotNull { row -> byTitleAndDate(row, catalogRows) }
            .distinctBy { it.episodeId }
            .singleOrNull()
    }

    private fun byTitleAndDate(row: LocalCatalogListenerReference, catalogRows: List<LocalEpisodeEntity>,): LocalEpisodeEntity? {
        val title = row.title?.trim().orEmpty()
        val date = row.publishedDate ?: return null
        if (title.isEmpty()) return null
        return catalogRows.singleOrNull { it.title.trim() == title && abs(it.publishedDate - date) <= LocalCatalogOrphanRematch.DATE_WINDOW_SECONDS }
    }

    @Suppress("LongParameterList")
    private fun linkOf(
        oldId: String,
        row: LocalEpisodeEntity,
        currentId: String,
        rowAliasesById: Map<String, EpisodeAliasEntity>,
        existing: EpisodeAliasEntity?,
        epNs: Long?,
        nowMs: Long,
    ): EpisodeAliasEntity? {
        if (row.legacy && row.guid == "id:${row.episodeId}") return null
        val key = EpisodeRecipeId.trimKey(row.guid) ?: return null
        val rowAlias =
            rowAliasesById[row.episodeId]
                ?.takeIf { it.state == EpisodeAliasState.TRUSTED && it.podcastId == currentId && it.catalogKey == key }
                ?: return null
        val recipeId = rowAlias.recipeId ?: epNs?.let { runCatching { EpisodeRecipeId.forNamespace(it, key) }.getOrNull() }
        return EpisodeAliasEntity(
            aliasId = oldId,
            podcastId = currentId,
            catalogKey = key,
            recipeId = recipeId,
            state = EpisodeAliasState.TRUSTED,
            owner = false,
            provenance = EpisodeAliasProvenance.RENUMBERED_SHOW_MATCH,
            createdAt = existing?.createdAt ?: nowMs,
        )
    }
}
