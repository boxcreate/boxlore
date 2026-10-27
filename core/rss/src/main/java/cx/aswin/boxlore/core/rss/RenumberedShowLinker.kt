package cx.aswin.boxlore.core.rss

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import kotlin.math.abs

/**
 * Links the listener rows of a show Podcast Index renumbered ([RenumberedShows]) to the episodes of
 * the show it now answers as, so those episodes read the old rows' state (played, progress, likes,
 * downloads) on the new show's page. Nothing moves and no ID changes: history, queue and download
 * rows keep their ID and their old show; only aliases are written ([RenumberedShowLinks]).
 *
 * Linking is idempotent and runs inside every catalog read of the new show (from
 * [LocalEpisodeCatalogRepository]'s refresh transaction) and once when a renumbering is recorded,
 * so rows that arrive later (a cloud pull, a restore) are linked by the next read. It never
 * touches a trusted or ambiguous alias, an ID a catalog row holds, or an alias of another show.
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
        if (ids.none { RenumberedShowLinks.isSettleable(aliasesById[it], formerId) }) return 0
        val rows = catalogDao.listForShow(currentId)
        if (rows.isEmpty()) return 0
        val catalogHeld = ids.chunked(MAX_IDS_PER_QUERY).flatMap { catalogDao.findOwners(it) }.mapTo(HashSet()) { it.episodeId }
        val links =
            RenumberedShowLinks.plan(
                formerId = formerId,
                currentId = currentId,
                references = references,
                aliasesById = aliasesById,
                catalogRows = rows,
                rowAliasesById = aliasesOf(rows.map { it.episodeId }),
                catalogHeldIds = catalogHeld,
                epNs = namespaceOf(currentId),
                nowMs = System.currentTimeMillis(),
            )
        links.forEach { identityDao.upsertAlias(it) }
        if (links.isNotEmpty()) android.util.Log.i(LOG_TAG, "Show $currentId: linked ${links.size} IDs of renumbered show $formerId")
        return links.size
    }

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
