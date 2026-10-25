package cx.aswin.boxlore.core.catalog.sync

import androidx.room.withTransaction
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.EpisodeGroup
import cx.aswin.boxlore.core.database.entities.QueueRemovalIdentity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.EpisodeRecipeId
import cx.aswin.boxlore.core.network.model.SyncEpisodeIdentityFields

/**
 * Episode identity on the cloud sync wire (docs/EPISODE_IDS.md §5, cloud sync).
 *
 * Every synced history and queue row keeps the ID it is stored under; the identity fields only say
 * which episode (show, catalog key) the ID points at, so devices that store the episode under
 * other IDs, and the server's rich queue removals, can tell it is the same episode.
 * - **Out** ([outgoing]): a row carries its catalog key and recipe ID only when its ID has a
 *   trusted alias under the row's own show. A recipe ID that the alias table says names another
 *   episode (another key of the show has it, or the local ID equal to it belongs to another
 *   episode) is left out, and so are both fields when the row's own ID is in the recipe range but
 *   is not the episode's recipe ID: the server compares a recipe-range row's key with the copy
 *   other devices stored and silently refuses a different one, while a write without a key is
 *   stored. Rich queue removals follow the same recipe ID rule ([outgoingRemovals]).
 * - **In** ([recordIncoming]): a pulled row whose ID has no alias gets one under the row's show:
 *   trusted when the row names, by catalog key (else by a recipe ID exactly one key of the show
 *   has), an episode this phone already holds under a trusted ID, so the row joins that episode's
 *   listener state; unresolved otherwise, so it is never a new owner and the show's next feed read
 *   can still attribute it. An unresolved alias of the same show is settled the same way. Nothing
 *   changes ID, and existing trusted or ambiguous aliases stay as they are.
 * - **Matching** ([tokens], [removalTokens]): one token per episode, so a queue merge and its
 *   removals treat an episode queued under two IDs as one.
 * - **Catch-up** (`identityCatchUp`, in SyncIdentityCatchUp.kt): after a pull from the start, the
 *   cloud copies that lack the identity this phone knows, as the cloud sent them plus the missing
 *   fields, so they can be pushed back without changing anything else.
 *
 * Added in P4 PR 11 of the own-episode-IDs plan (plan section 8, cloud sync identity; decision D14).
 * COMPAT(own-ids): cloud sync carries one episode across its IDs and old builds. Permanent: never remove (MIGRATION.md §5).
 */
class SyncEpisodeIdentity(
    private val database: BoxLoreDatabase,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val aliases get() = database.episodeIdentityDao()

    /** A history or queue row about to be pushed: its stored ID and the show it is stored under. */
    data class OutgoingRow(val episodeId: String, val podcastId: String)

    /** The identity fields a pushed row carries; both null when it carries none. */
    data class WireIdentity(val recipeId: Long?, val catalogKey: String?) {
        companion object {
            /** No identity: the row is sent as older builds send it. */
            val NONE = WireIdentity(recipeId = null, catalogKey = null)
        }
    }

    /** A pulled history or queue row and the identity the server sent with it (null when unknown). */
    data class IncomingRow(val episodeId: String, val podcastId: String, val recipeId: Long?, val catalogKey: String?,) {
        internal val hasIdentity: Boolean get() = recipeId != null || catalogKey != null
    }

    /**
     * The identity each of [rows] carries on the wire ([WireIdentity.NONE] when it carries none).
     * A row whose own ID is a recipe ID that another key of the show also has (a recipe clash) keeps
     * its catalog key: another device may store that ID for the other episode, and the server then
     * refuses the write instead of mixing the two episodes' progress.
     */
    suspend fun outgoing(rows: Collection<OutgoingRow>): Map<OutgoingRow, WireIdentity> {
        val distinct = rows.distinct()
        if (distinct.isEmpty()) return emptyMap()
        val trusted = trustedAliases(distinct.map { it.episodeId })
        val conflicts = HashMap<RecipeUse, Boolean>()
        return distinct.associateWith { row ->
            val alias = trusted[row.episodeId]?.takeIf { it.podcastId == row.podcastId } ?: return@associateWith WireIdentity.NONE
            val key = alias.catalogKey ?: return@associateWith WireIdentity.NONE
            val recipeId = alias.recipeId?.takeIf(EpisodeRecipeId::isRecipeId)
            if (isRecipeRange(row.episodeId) && row.episodeId != recipeId?.toString()) return@associateWith WireIdentity.NONE
            val sent = recipeId?.takeUnless { conflicts.getOrPut(RecipeUse(row.podcastId, it, key)) { recipeNamesAnotherEpisode(row.podcastId, it, key) } }
            WireIdentity(recipeId = sent, catalogKey = key)
        }
    }

    /**
     * [entries] (the stored rich queue removals) as they are pushed: an entry's recipe ID is left
     * out when it names another episode of the show, so the server's removal by recipe ID can't
     * reach that episode. The show and catalog key still remove the right one.
     */
    suspend fun outgoingRemovals(entries: List<QueueRemovalIdentity>): List<QueueRemovalIdentity> {
        val conflicts = HashMap<RecipeUse, Boolean>()
        return entries.map { entry ->
            val show = entry.show
            val recipeId = entry.recipeId
            if (show == null || recipeId == null) return@map entry
            val conflict = conflicts.getOrPut(RecipeUse(show, recipeId, entry.catalogKey)) {
                recipeNamesAnotherEpisode(show, recipeId, entry.catalogKey)
            }
            if (conflict) entry.copy(recipeId = null) else entry
        }
    }

    /**
     * Records aliases for pulled rows, in one transaction, before they are merged (see the class
     * documentation). Rows of `rss:` shows, Brief IDs and IDs a catalog row holds are left alone,
     * and so is an ID this phone's history stores under another show.
     */
    suspend fun recordIncoming(rows: Collection<IncomingRow>) {
        val candidates =
            rows
                .filter { isStoredEpisodeId(it.episodeId) && EpisodeRecipeId.showIdOrNull(it.podcastId) != null }
                .groupBy { it.episodeId }
                .map { (_, same) -> same.firstOrNull { it.hasIdentity } ?: same.first() }
        if (candidates.isEmpty()) return
        database.withTransaction {
            val stored = readStoredHolders(candidates.map { it.episodeId })
            val shows = HashMap<String, ShowKeys>()
            val now = nowMillis()
            val writes = candidates.mapNotNull { row -> aliasWrite(row, stored, now) { shows.getOrPut(row.podcastId) { ShowKeys.of(aliases.aliasesForShow(row.podcastId)) } } }
            val inserts = writes.filter { it.isNew }.map { it.alias }
            if (inserts.isNotEmpty()) aliases.insertAliasesIfAbsent(inserts)
            writes.filterNot { it.isNew }.forEach { aliases.upsertAlias(it.alias) }
        }
    }

    /**
     * The aliases of [ids], and for IDs without one, which a catalog row holds (a Podcast Index
     * show's, or for a negative ID an RSS show's) and the show their history row is stored under.
     */
    private suspend fun readStoredHolders(ids: List<String>): StoredHolders {
        val existing = ids.chunked(MAX_IDS_PER_QUERY).flatMap { aliases.getAliases(it) }.associateBy { it.aliasId }
        val unaliased = ids.filter { it !in existing }
        val catalogHeld = unaliased.chunked(MAX_IDS_PER_QUERY).flatMap { database.localEpisodeCatalogDao().findOwners(it) }.mapTo(HashSet()) { it.episodeId }
        unaliased.filter { it !in catalogHeld && it.startsWith('-') && database.rssEpisodeDao().getEpisode(it) != null }.toCollection(catalogHeld)
        val historyShows = unaliased.chunked(MAX_IDS_PER_QUERY).flatMap { database.listeningHistoryDao().getHistoryItems(it) }.associate { it.episodeId to it.podcastId }
        return StoredHolders(existing, catalogHeld, historyShows)
    }

    /**
     * The alias write for one pulled row: a new alias (trusted when [showKeys] has the episode the
     * row names, else unresolved), a settled unresolved alias of the row's show, or null.
     */
    private suspend fun aliasWrite(row: IncomingRow, stored: StoredHolders, now: Long, showKeys: suspend () -> ShowKeys,): AliasWrite? {
        val alias = stored.aliases[row.episodeId]
        if (!stored.mayRecord(row, alias)) return null
        val match = showKeys().match(row)?.takeUnless { isRecipeRange(row.episodeId) && row.episodeId != it.recipeId?.toString() }
        return when {
            alias == null -> AliasWrite(newAlias(row, match, now), isNew = true)
            match != null -> AliasWrite(alias.copy(catalogKey = match.key, recipeId = match.recipeId, state = EpisodeAliasState.TRUSTED, owner = false, provenance = EpisodeAliasProvenance.CLOUD_SYNC), isNew = false)
            else -> null
        }
    }

    /**
     * A token per ID that is equal for every trusted ID of one episode and different between
     * episodes; an ID without a trusted alias maps to itself. Unlike
     * `EpisodeListenerStore.groupTokens`, an episode stored under one trusted ID also gets its
     * (show, key) token, so a removal that names the episode by show and key matches it.
     */
    suspend fun tokens(ids: Collection<String>): Map<String, String> {
        val distinct = ids.filter { it.isNotBlank() }.distinct()
        if (distinct.isEmpty()) return emptyMap()
        val trusted = trustedAliases(distinct)
        return distinct.associateWith { id -> trusted[id]?.let { EpisodeGroup.tokenOf(it.podcastId, it.catalogKey) } ?: id }
    }

    /**
     * The tokens ([tokens]) of the episodes [removals] name: each removal's own ID, its show and
     * catalog key, else the one key of its show that has its recipe ID.
     */
    suspend fun removalTokens(removals: Collection<QueueRemovalIdentity>): Set<String> {
        if (removals.isEmpty()) return emptySet()
        val result = tokens(removals.map { it.id }).values.toHashSet()
        for (removal in removals) {
            val show = removal.show ?: continue
            val key = removal.catalogKey ?: removal.recipeId?.let { uniqueTrustedKey(show, it) }
            EpisodeGroup.tokenOf(show, key)?.let(result::add)
        }
        return result
    }

    /** The ID that holds [episodeId]'s episode's listener state (its owner), read without storing anything. */
    suspend fun localOwner(episodeId: String): String = database.episodeListenerStore().group(episodeId).ownerId

    /**
     * True when [recipeId], used for ([showId], [catalogKey]), names another episode on this phone:
     * an alias of another key of the show has it, or the local ID equal to it belongs to another
     * show or key (an alias or a catalog row). [catalogKey] null means the key is unknown, so only
     * another show's use, or two keys of the show, count.
     */
    private suspend fun recipeNamesAnotherEpisode(showId: String, recipeId: Long, catalogKey: String?,): Boolean {
        val keysWithRecipe = aliases.aliasesForRecipeId(showId, recipeId).mapNotNullTo(HashSet()) { it.catalogKey }
        if (if (catalogKey == null) keysWithRecipe.size > 1 else keysWithRecipe.any { it != catalogKey }) return true
        val localId = recipeId.toString()
        val holder = aliases.getAlias(localId)
        if (holder != null && (holder.podcastId != showId || differentKey(holder.catalogKey, catalogKey))) return true
        val row = database.localEpisodeCatalogDao().findOwners(listOf(localId)).firstOrNull() ?: return false
        return row.podcastId != showId || differentKey(EpisodeRecipeId.trimKey(row.guid), catalogKey)
    }

    private fun differentKey(stored: String?, mine: String?): Boolean = stored != null && mine != null && stored != mine

    private suspend fun uniqueTrustedKey(showId: String, recipeId: Long): String? =
        aliases.aliasesForRecipeId(showId, recipeId)
            .filter { it.state == EpisodeAliasState.TRUSTED }
            .mapNotNull { it.catalogKey }
            .distinct()
            .singleOrNull()

    private suspend fun trustedAliases(ids: Collection<String>): Map<String, EpisodeAliasEntity> =
        ids.filter { it.isNotBlank() }.distinct().chunked(MAX_IDS_PER_QUERY)
            .flatMap { aliases.getAliases(it) }
            .filter { it.state == EpisodeAliasState.TRUSTED && it.catalogKey != null }
            .associateBy { it.aliasId }

    /** A new alias for [row]: trusted with [match]'s key, else unresolved with no key; never the owner. */
    private fun newAlias(row: IncomingRow, match: KeyMatch?, now: Long,) = EpisodeAliasEntity(
        aliasId = row.episodeId,
        podcastId = row.podcastId,
        catalogKey = match?.key,
        recipeId = match?.recipeId,
        state = if (match != null) EpisodeAliasState.TRUSTED else EpisodeAliasState.UNRESOLVED,
        owner = false,
        provenance = EpisodeAliasProvenance.CLOUD_SYNC,
        createdAt = now,
    )

    /** One alias row to insert ([isNew]) or to replace in place. */
    private class AliasWrite(val alias: EpisodeAliasEntity, val isNew: Boolean)

    /** What already holds pulled IDs: their aliases, catalog rows, and the shows of history rows of unaliased IDs. */
    private class StoredHolders(val aliases: Map<String, EpisodeAliasEntity>, private val catalogHeld: Set<String>, private val historyShows: Map<String, String>,) {
        /**
         * True when [row] may get an alias here: a new one when its ID has none, no catalog row holds
         * it and its history row (if any) is under the row's show; a settled one when its alias is
         * an unresolved alias of the row's show.
         */
        fun mayRecord(row: IncomingRow, alias: EpisodeAliasEntity?): Boolean = if (alias == null) {
            row.episodeId !in catalogHeld && historyShows[row.episodeId].let { it == null || it == row.podcastId }
        } else {
            alias.state == EpisodeAliasState.UNRESOLVED && alias.podcastId == row.podcastId
        }
    }

    /** One use of a recipe ID: (show, recipe ID, the key it is used for). */
    private data class RecipeUse(val showId: String, val recipeId: Long, val catalogKey: String?)

    /** A key of the show that a pulled row named, with the recipe ID this phone stores for it. */
    private data class KeyMatch(val key: String, val recipeId: Long?)

    /** One show's trusted keys, read once per pull. */
    private class ShowKeys(private val recipeByKey: Map<String, Long?>, private val keysByRecipe: Map<Long, Set<String>>,) {
        /** The trusted key [row] names: its catalog key when given (and nothing else), else the one key with its recipe ID. */
        fun match(row: IncomingRow): KeyMatch? {
            val sentKey = SyncEpisodeIdentityFields.catalogKeyOrNull(row.catalogKey)?.let(EpisodeRecipeId::trimKey)
            if (sentKey != null) return if (sentKey in recipeByKey) KeyMatch(sentKey, recipeByKey[sentKey]) else null
            val recipeId = SyncEpisodeIdentityFields.recipeIdOrNull(row.recipeId) ?: return null
            val key = keysByRecipe[recipeId]?.singleOrNull() ?: return null
            return KeyMatch(key, recipeByKey[key])
        }

        companion object {
            fun of(showAliases: List<EpisodeAliasEntity>): ShowKeys {
                val trusted = showAliases.filter { it.state == EpisodeAliasState.TRUSTED && it.catalogKey != null }
                val recipeByKey = trusted.groupBy { it.catalogKey!! }.mapValues { (_, keyAliases) -> keyAliases.firstNotNullOfOrNull { it.recipeId } }
                val keysByRecipe = trusted.filter { it.recipeId != null }.groupBy({ it.recipeId!! }, { it.catalogKey!! }).mapValues { (_, keys) -> keys.toSet() }
                return ShowKeys(recipeByKey, keysByRecipe)
            }
        }
    }

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
        const val BRIEFING_ID_PREFIX = "briefing_"

        /** A stored episode ID that can have an alias: a non-zero number, never a Brief. */
        fun isStoredEpisodeId(id: String): Boolean = !id.startsWith(BRIEFING_ID_PREFIX) && id.toLongOrNull()?.let { it != 0L } == true

        fun isRecipeRange(id: String): Boolean = id.toLongOrNull()?.let(EpisodeRecipeId::isRecipeId) == true
    }
}
