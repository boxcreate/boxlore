package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.LocalEpisodeOwner
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * Keeps one episode from replacing another in `local_episodes` (docs/EPISODE_IDS.md §4: no store
 * overwrites a different episode, both survive).
 *
 * `upsertEpisodes` is a REPLACE, which deletes any stored row that shares the incoming row's episode
 * ID (primary key) or its (show, catalog key) (unique index). Two shows that read the same feed (a
 * feed listed twice in Podcast Index) mint the same IDs from the same feed namespace, so without
 * this check the second show's refresh took the first show's rows: wrong show, title and audio, and
 * a cached download of the other episode.
 *
 * For each incoming row, in this order:
 * 1. **Its own stored row.** When the show already stores this catalog key, the row keeps that
 *    stored ID, so REPLACE updates the row instead of deleting it through the unique index. Stored
 *    keys are compared after the recipe trim ([EpisodeRecipeId.trimKey]): Room migration 31→32
 *    trimmed with SQLite `TRIM()` (spaces only), so a legacy key can still end in a newline, tab or
 *    no-break space. Such a row keeps its ID and its key is corrected ([KeyRepair.RETRIMMED]).
 * 2. **A legacy row holding the item's own minted ID** is the same episode: the row keeps that
 *    ID and is healed to the feed's key ([KeyRepair.SYNTHETIC_KEY], [KeyRepair.AUDIO_URL_KEY]);
 *    see [healLegacyRow]. This runs whatever ID the row arrives with (an alias ID, a listener-held ID or a recipe ID),
 *    so a legacy row never ends up beside a second row of the same episode.
 * 3. **A free ID.** The incoming ID (an evicted row's ID, a listener-held ID, a recipe ID or a phone
 *    mint) is used when no other (show, key) holds it: no catalog row in the database or earlier in
 *    the batch, and no alias of another show or a trusted alias of another key of this show
 *    ([isFree]). When a reused ID is taken, the row's fallback IDs are tried in order (the key's
 *    other trusted alias IDs, then its recipe ID; `PlannedEpisodeRow.fallbackIds`).
 * 4. **Otherwise a reserved local ID** ([StickyEpisodeIdentity.reservedEpisodeId], minted in the
 *    show's namespace), reported in [Resolution.reservedIds] so the caller records its alias in
 *    the same transaction. It is stable across refreshes: the next refresh finds the stored row by
 *    its key (step 1), and after eviction by its alias.
 *
 * A row that finds no free reserved ID is reported in [Resolution.unplaced] rather than written over
 * another episode. Call inside the write's transaction.
 *
 * Added in P4a (collision guard); P4 PR 1 adds the re-trimmed comparison, the unique-key check, the
 * legacy heal (keyed on the item's own mint, so it does not depend on how new IDs are assigned) and
 * the reporting of unplaced rows; P4 PR 9 adds the alias check, the fallback IDs and reserves in
 * the show's namespace. Own-IDs plan section 4 (legacy keys are re-trimmed; synthetic keys resolve when the
 * feed is read again; collisions) and section 8 (no write replaces another episode).
 *
 * COMPAT(own-ids): keeps two episodes from merging and legacy rows on their stored IDs. Permanent: never remove (MIGRATION.md §5).
 */
internal object LocalEpisodeCollisionGuard {
    /** Stays under SQLite's bound-parameter limit. */
    private const val LOOKUP_CHUNK = 500

    /** Reserved IDs tried per row before the row is reported as unplaced. */
    const val MAX_RESERVE_ATTEMPTS = 8

    /** Prefix of the synthetic keys Room migration 31→32 gave rows it could not key. */
    private const val SYNTHETIC_KEY_PREFIX = "id:"

    /**
     * Where a stored key that a write corrected came from: the provenance of the old key. The
     * refresh saves it on the episode's alias in the same transaction (P4 PR 9,
     * `CatalogAliasRecords`), so a healed row can be told apart from a native one after its key is
     * rewritten.
     */
    enum class KeyRepair {
        /** The stored key differed only by characters SQLite `TRIM()` keeps (newline, tab, no-break space…). */
        RETRIMMED,

        /** The stored key was the synthetic `id:<its own ID>`. */
        SYNTHETIC_KEY,

        /** The stored key was the row's audio address, and the feed item has a GUID. */
        AUDIO_URL_KEY,
    }

    /** A stored row that keeps [episodeId] while its key changes from [storedKey] to [key]. */
    data class RepairedKey(
        val episodeId: String,
        val podcastId: String,
        val storedKey: String,
        val key: String,
        val repair: KeyRepair,
    )

    /**
     * @property rows the rows to write, in input order, each with the ID it may safely take
     * @property repaired stored rows whose key the write corrects while keeping their ID
     * @property unplaced rows left out because every ID they could take belongs to another episode
     * @property reservedIds IDs of [rows] that are reserved local IDs taken in this batch
     */
    data class Resolution(
        val rows: List<LocalEpisodeEntity>,
        val repaired: List<RepairedKey>,
        val unplaced: List<LocalEpisodeEntity>,
        val reservedIds: Set<String> = emptySet(),
    )

    private data class Owner(val podcastId: String, val key: String)

    /** Who an alias says an ID belongs to; [key] is null while the alias is unresolved. */
    private data class AliasHold(val podcastId: String, val key: String?, val trusted: Boolean)

    /** One show's stored rows, read inside the write's transaction. */
    private class ShowRows(rows: List<LocalEpisodeIdentity>) {
        /** Stored key exactly as the unique index sees it → ID. */
        val byExactKey: Map<String, String> = rows.associate { it.guid to it.episodeId }

        /** Recipe-trimmed stored key → IDs, in stored order. */
        val byTrimmedKey: Map<String, List<String>> =
            rows
                .mapNotNull { row ->
                    StickyEpisodeIdentity.catalogKey(row.guid, row.audioUrl)?.let { EpisodeRecipeId.comparableKey(it) to row.episodeId }
                }
                .groupBy({ it.first }, { it.second })

        val byId: Map<String, LocalEpisodeIdentity> = rows.associateBy { it.episodeId }
    }

    /** Who already holds IDs: catalog rows (all shows) and aliases, looked up as the batch needs them. */
    private class Holders(
        private val findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
        private val findAliases: suspend (List<String>) -> List<EpisodeAliasEntity>,
    ) {
        val stored = HashMap<String, Owner>()
        val aliases = HashMap<String, AliasHold>()
        private val looked = HashSet<String>()

        suspend fun load(ids: Collection<String>) {
            val missing = ids.filter { it !in looked }.distinct()
            if (missing.isEmpty()) return
            looked += missing
            for (chunk in missing.chunked(LOOKUP_CHUNK)) {
                for (owner in findOwners(chunk)) stored[owner.episodeId] = ownerOf(owner)
                for (alias in findAliases(chunk)) {
                    aliases[alias.aliasId] =
                        AliasHold(alias.podcastId, alias.catalogKey?.let(::comparableKey), alias.state == EpisodeAliasState.TRUSTED)
                }
            }
        }
    }

    /**
     * Resolves the IDs [rows] may take without replacing another episode (see the class docs).
     *
     * @param findOwners owners of up to 500 episode IDs across every show
     * @param listShowRows every stored (ID, key, audio address) of one show
     * @param findAliases alias rows of up to 500 episode IDs across every show
     * @param fallbackIds per row of [rows] (same index), the IDs to try in order when the row's own
     *   ID is taken, before a reserved one
     * @param olderNamespaces URL namespaces of addresses the show was read from before (P4 PR 14):
     *   a legacy row minted under one of them heals too, after the saved address changed
     */
    @Suppress("LongParameterList", "kotlin:S107")
    suspend fun resolve(
        rows: List<LocalEpisodeEntity>,
        rssNamespaceId: String,
        findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
        listShowRows: suspend (String) -> List<LocalEpisodeIdentity>,
        findAliases: suspend (List<String>) -> List<EpisodeAliasEntity> = { emptyList() },
        fallbackIds: List<List<String>> = emptyList(),
        olderNamespaces: List<String> = emptyList(),
    ): Resolution {
        if (rows.isEmpty()) return Resolution(rows, emptyList(), emptyList())
        val shows = rows.map { it.podcastId }.distinct().associateWith { ShowRows(listShowRows(it)) }
        val holders = Holders(findOwners, findAliases)
        holders.load(rows.map { it.episodeId })
        val state = BatchState()
        val namespaces = (listOf(rssNamespaceId) + olderNamespaces).distinct()
        val resolved = arrayOfNulls<LocalEpisodeEntity>(rows.size)
        // Rows whose key the show already stores go first, so no other row can take their IDs.
        rows.forEachIndexed { index, row ->
            keepStoredRow(row, shows.getValue(row.podcastId), state)?.let { resolved[index] = it }
        }
        rows.forEachIndexed { index, row ->
            if (resolved[index] != null) return@forEachIndexed
            val mine = ownerOf(row)
            if (mine in state.claimedKeys) return@forEachIndexed // A repeated key: the first row wins.
            val fallbacks = fallbackIds.getOrNull(index).orEmpty()
            val id = placeNewRow(row, mine, namespaces, shows.getValue(row.podcastId), holders, state, fallbacks)
            if (id == null) {
                state.unplaced += row
            } else {
                state.claim(id, mine)
                resolved[index] = if (id == row.episodeId) row else row.copy(episodeId = id)
            }
        }
        return Resolution(resolved.filterNotNull(), state.repaired, state.unplaced, state.reserved)
    }

    /**
     * True when writing [rows] for [podcastId] would take an ID another show holds: a catalog row of
     * another show, or, with no catalog row, an alias of another show (an evicted episode whose
     * listener state belongs to that show). For writers that must keep their IDs (the legacy RSS
     * upgrade carries listener state on them) and so stop instead of re-minting. IDs [podcastId]
     * itself holds don't count.
     *
     * The alias check was added in P4 PR 9 (own-IDs plan section 4, collisions; section 5, aliases).
     */
    suspend fun takesAnotherShowsId(
        rows: List<LocalEpisodeEntity>,
        podcastId: String,
        findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
        findAliases: suspend (List<String>) -> List<EpisodeAliasEntity> = { emptyList() },
    ): Boolean {
        val holders = Holders(findOwners, findAliases)
        holders.load(rows.map { it.episodeId })
        return rows.any { row ->
            val holder = holders.stored[row.episodeId]?.podcastId ?: holders.aliases[row.episodeId]?.podcastId
            holder != null && holder != podcastId
        }
    }

    private class BatchState {
        val byId = HashMap<String, Owner>()
        val claimedKeys = HashSet<Owner>()
        val repaired = ArrayList<RepairedKey>()
        val unplaced = ArrayList<LocalEpisodeEntity>()
        val reserved = LinkedHashSet<String>()

        fun claim(id: String, owner: Owner) {
            byId[id] = owner
            claimedKeys += owner
        }
    }

    /**
     * The row under its own stored ID when the show already stores its key, else null. An exact
     * stored key wins over a re-trimmed one: writing any other ID under that key would make REPLACE
     * delete the stored row through the unique (show, key) index.
     */
    private fun keepStoredRow(row: LocalEpisodeEntity, show: ShowRows, state: BatchState): LocalEpisodeEntity? {
        val mine = ownerOf(row)
        if (mine in state.claimedKeys) return null
        val storedId =
            show.byExactKey[EpisodeRecipeId.storedForm(row.guid)]
                ?: show.byTrimmedKey[mine.key]?.let { ids -> if (row.episodeId in ids) row.episodeId else ids.first() }
                ?: return null
        if (storedId in state.byId) return null
        state.claim(storedId, mine)
        val storedKey = show.byId[storedId]?.guid
        if (storedKey != null && storedKey != row.guid) {
            state.repaired += RepairedKey(storedId, row.podcastId, storedKey, row.guid, KeyRepair.RETRIMMED)
        }
        return if (storedId == row.episodeId) row else row.copy(episodeId = storedId)
    }

    @Suppress("LongParameterList")
    private suspend fun placeNewRow(
        row: LocalEpisodeEntity,
        mine: Owner,
        namespaces: List<String>,
        show: ShowRows,
        holders: Holders,
        state: BatchState,
        fallbacks: List<String>,
    ): String? {
        namespaces.firstNotNullOfOrNull { namespace -> healLegacyRow(row, mine, namespace, show, state) }?.let { healed ->
            state.repaired += healed
            return healed.episodeId
        }
        if (isFree(row.episodeId, mine, holders, state.byId)) return row.episodeId
        for (candidate in fallbacks) {
            holders.load(listOf(candidate))
            if (isFree(candidate, mine, holders, state.byId)) return candidate
        }
        return reserve(mine, holders, state)
    }

    /**
     * The same-show legacy row that holds the item's own minted ID, healed to the feed's key, or
     * null to keep any such row apart.
     *
     * Room migration 31→32 keyed rows it could not key by GUID with their audio address, or with
     * the synthetic `id:<own ID>`. When the feed is read again and the item's own mint
     * ([StickyEpisodeIdentity.ownMintedId]) equals the stored ID, both came from the same hash input,
     * so the row is the same episode: it keeps its ID (and the listener state on it) and takes the
     * feed's key. The own mint is looked up whatever ID [row] arrives with: a new item now carries
     * a recipe ID or a listener-held ID (before P4 PR 9, on a catalog's first read, a Podcast Index match), and writing
     * that would leave the legacy row beside a second row of the same episode. A row with any other
     * key, or whose ID an earlier row of the batch took (for example a GUID-less item still keyed by
     * that audio address), stays a different episode. The caller tries this read's namespace first,
     * then those of addresses the show was read from before (P4 PR 14).
     */
    private fun healLegacyRow(
        row: LocalEpisodeEntity,
        mine: Owner,
        rssNamespaceId: String,
        show: ShowRows,
        state: BatchState,
    ): RepairedKey? {
        val ownId = StickyEpisodeIdentity.ownMintedId(rssNamespaceId, mine.key)
        if (ownId in state.byId) return null
        val storedRow = show.byId[ownId] ?: return null
        val itemHasGuid = mine.key != EpisodeRecipeId.trimKey(row.audioUrl)
        val repair =
            when {
                storedRow.guid == SYNTHETIC_KEY_PREFIX + ownId -> KeyRepair.SYNTHETIC_KEY
                itemHasGuid && comparableKey(storedRow.guid) == EpisodeRecipeId.trimKey(storedRow.audioUrl) -> KeyRepair.AUDIO_URL_KEY
                else -> return null
            }
        return RepairedKey(ownId, row.podcastId, storedRow.guid, row.guid, repair)
    }

    private fun ownerOf(row: LocalEpisodeEntity): Owner = Owner(row.podcastId, comparableKey(row.guid))

    private fun ownerOf(owner: LocalEpisodeOwner): Owner = Owner(owner.podcastId, comparableKey(owner.guid))

    /** Stored keys compare after the recipe trim, so a legacy key with a trailing newline is the same key. */
    private fun comparableKey(key: String): String = EpisodeRecipeId.comparableKey(key)

    /**
     * True when [mine] may take [id]:
     * - a catalog row (in the database or earlier in the batch) that holds [id] must be [mine];
     * - with no such row, an alias that holds [id] must belong to [mine]'s show and must not be a
     *   trusted alias of another key. Another show's alias always blocks: its listener state belongs
     *   to that show's episode. An unresolved or ambiguous alias of the same show doesn't: a recipe
     *   ID or phone mint of this key that equals it is that same episode's ID (an ID older builds
     *   were served or minted for it).
     */
    private fun isFree(id: String, mine: Owner, holders: Holders, batch: Map<String, Owner>,): Boolean {
        val holder = batch[id] ?: holders.stored[id]
        if (holder != null) return holder == mine
        val alias = holders.aliases[id] ?: return true
        return alias.podcastId == mine.podcastId && (!alias.trusted || alias.key == null || alias.key == mine.key)
    }

    private suspend fun reserve(mine: Owner, holders: Holders, state: BatchState,): String? {
        for (attempt in 1..MAX_RESERVE_ATTEMPTS) {
            val candidate = StickyEpisodeIdentity.reservedEpisodeId(mine.podcastId, mine.key, attempt)
            holders.load(listOf(candidate))
            if (isFree(candidate, mine, holders, state.byId)) {
                state.reserved += candidate
                return candidate
            }
        }
        return null
    }
}
