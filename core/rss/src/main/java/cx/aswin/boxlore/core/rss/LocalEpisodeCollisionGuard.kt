package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeIdentity
import cx.aswin.boxlore.core.database.LocalEpisodeOwner

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
 * 2. **A free ID.** The incoming ID is used when no other (show, key) holds it, in the database or
 *    earlier in the same batch.
 * 3. **A legacy row holding the item's own ID** is the same episode and is healed to the feed's key
 *    ([KeyRepair.SYNTHETIC_KEY], [KeyRepair.AUDIO_URL_KEY]); see [healedRepair].
 * 4. **Otherwise a reserved local ID** ([StickyEpisodeIdentity.reservedEpisodeId]), stable across
 *    refreshes because the next refresh finds the stored row by its key (step 1).
 *
 * A row that finds no free reserved ID is reported in [Resolution.unplaced] rather than written over
 * another episode. Call inside the write's transaction.
 *
 * Added in P4a (collision guard); P4 PR 1 adds the re-trimmed comparison, the unique-key check, the
 * legacy heal and the reporting of unplaced rows. Own-IDs plan section 4 (legacy keys are re-trimmed;
 * synthetic keys resolve when the feed is read again) and section 8 (no write replaces another
 * episode).
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
     * Where a stored key that a write corrected came from: the provenance of the old key. The v2
     * build records it with the episode's alias; until then the refresh logs it.
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
     */
    data class Resolution(
        val rows: List<LocalEpisodeEntity>,
        val repaired: List<RepairedKey>,
        val unplaced: List<LocalEpisodeEntity>,
    )

    private data class Owner(val podcastId: String, val key: String)

    /** One show's stored rows, read inside the write's transaction. */
    private class ShowRows(rows: List<LocalEpisodeIdentity>) {
        /** Stored key exactly as the unique index sees it → ID. */
        val byExactKey: Map<String, String> = rows.associate { it.guid to it.episodeId }

        /** Recipe-trimmed stored key → IDs, in stored order. */
        val byTrimmedKey: Map<String, List<String>> =
            rows
                .mapNotNull { row -> StickyEpisodeIdentity.catalogKey(row.guid, row.audioUrl)?.let { it to row.episodeId } }
                .groupBy({ it.first }, { it.second })

        val byId: Map<String, LocalEpisodeIdentity> = rows.associateBy { it.episodeId }
    }

    /**
     * Resolves the IDs [rows] may take without replacing another episode (see the class docs).
     *
     * @param findOwners owners of up to 500 episode IDs across every show
     * @param listShowRows every stored (ID, key, audio address) of one show
     */
    suspend fun resolve(
        rows: List<LocalEpisodeEntity>,
        rssNamespaceId: String,
        findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
        listShowRows: suspend (String) -> List<LocalEpisodeIdentity>,
    ): Resolution {
        if (rows.isEmpty()) return Resolution(rows, emptyList(), emptyList())
        val shows = rows.map { it.podcastId }.distinct().associateWith { ShowRows(listShowRows(it)) }
        val stored = storedOwners(rows.map { it.episodeId }, findOwners)
        val state = BatchState()
        val resolved = arrayOfNulls<LocalEpisodeEntity>(rows.size)
        // Rows whose key the show already stores go first, so no other row can take their IDs.
        rows.forEachIndexed { index, row ->
            keepStoredRow(row, shows.getValue(row.podcastId), state)?.let { resolved[index] = it }
        }
        rows.forEachIndexed { index, row ->
            if (resolved[index] != null) return@forEachIndexed
            val mine = ownerOf(row)
            if (mine in state.claimedKeys) return@forEachIndexed // A repeated key: the first row wins.
            val id = placeNewRow(row, mine, rssNamespaceId, shows.getValue(row.podcastId), stored, state, findOwners)
            if (id == null) {
                state.unplaced += row
            } else {
                state.claim(id, mine)
                resolved[index] = if (id == row.episodeId) row else row.copy(episodeId = id)
            }
        }
        return Resolution(resolved.filterNotNull(), state.repaired, state.unplaced)
    }

    /**
     * True when writing [rows] for [podcastId] would replace a row of another show. For writers that
     * must keep their IDs (the legacy RSS upgrade carries listener state on them) and so stop
     * instead of re-minting. Rows already held by [podcastId] itself don't count.
     */
    suspend fun takesAnotherShowsId(
        rows: List<LocalEpisodeEntity>,
        podcastId: String,
        findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
    ): Boolean {
        val stored: Map<String, Owner> = storedOwners(rows.map { it.episodeId }, findOwners)
        return rows.any { row -> stored[row.episodeId]?.let { it.podcastId != podcastId } == true }
    }

    private class BatchState {
        val byId = HashMap<String, Owner>()
        val claimedKeys = HashSet<Owner>()
        val repaired = ArrayList<RepairedKey>()
        val unplaced = ArrayList<LocalEpisodeEntity>()

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
            show.byExactKey[row.guid]
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
        rssNamespaceId: String,
        show: ShowRows,
        stored: MutableMap<String, Owner>,
        state: BatchState,
        findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
    ): String? {
        if (isFree(row.episodeId, mine, stored, state.byId)) return row.episodeId
        if (row.episodeId !in state.byId) {
            healedRepair(row, mine, rssNamespaceId, show, stored[row.episodeId])?.let { repair ->
                state.repaired += RepairedKey(row.episodeId, row.podcastId, show.byId.getValue(row.episodeId).guid, row.guid, repair)
                return row.episodeId
            }
        }
        return reserve(row, mine, rssNamespaceId, stored, state.byId, findOwners)
    }

    /**
     * How a legacy row of the same show that holds [row]'s ID is healed, or null to keep it apart.
     *
     * Room migration 31→32 keyed rows it could not key by GUID with their audio address, or with
     * the synthetic `id:<own ID>`. When the feed is read again and the item mints exactly the stored
     * ID, both came from the same hash input, so the row is the same episode: it keeps its ID (and
     * the listener state on it) and takes the feed's key, as before the collision guard. A row with
     * any other key stays a different episode.
     */
    private fun healedRepair(
        row: LocalEpisodeEntity,
        mine: Owner,
        rssNamespaceId: String,
        show: ShowRows,
        storedOwner: Owner?,
    ): KeyRepair? {
        if (storedOwner == null || storedOwner.podcastId != row.podcastId) return null
        val storedRow = show.byId[row.episodeId] ?: return null
        if (row.episodeId != StickyEpisodeIdentity.ownMintedId(rssNamespaceId, mine.key)) return null
        val itemHasGuid = mine.key != EpisodeRecipeId.trimKey(row.audioUrl)
        return when {
            storedRow.guid == SYNTHETIC_KEY_PREFIX + row.episodeId -> KeyRepair.SYNTHETIC_KEY
            itemHasGuid && storedOwner.key == EpisodeRecipeId.trimKey(storedRow.audioUrl) -> KeyRepair.AUDIO_URL_KEY
            else -> null
        }
    }

    private suspend fun storedOwners(
        ids: List<String>,
        findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
    ): MutableMap<String, Owner> {
        val stored = HashMap<String, Owner>()
        for (chunk in ids.distinct().chunked(LOOKUP_CHUNK)) {
            for (owner in findOwners(chunk)) stored[owner.episodeId] = ownerOf(owner)
        }
        return stored
    }

    private fun ownerOf(row: LocalEpisodeEntity): Owner = Owner(row.podcastId, comparableKey(row.guid))

    private fun ownerOf(owner: LocalEpisodeOwner): Owner = Owner(owner.podcastId, comparableKey(owner.guid))

    /** Stored keys compare after the recipe trim, so a legacy key with a trailing newline is the same key. */
    private fun comparableKey(key: String): String = EpisodeRecipeId.trimKey(key) ?: key

    private fun isFree(id: String, mine: Owner, stored: Map<String, Owner>, batch: Map<String, Owner>,): Boolean =
        (batch[id] ?: stored[id]).let { it == null || it == mine }

    @Suppress("LongParameterList")
    private suspend fun reserve(
        row: LocalEpisodeEntity,
        mine: Owner,
        rssNamespaceId: String,
        stored: MutableMap<String, Owner>,
        batch: Map<String, Owner>,
        findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
    ): String? {
        for (attempt in 1..MAX_RESERVE_ATTEMPTS) {
            val candidate = StickyEpisodeIdentity.reservedEpisodeId(rssNamespaceId, row.podcastId, mine.key, attempt)
            if (candidate !in stored) {
                for (owner in findOwners(listOf(candidate))) stored[owner.episodeId] = ownerOf(owner)
            }
            if (isFree(candidate, mine, stored, batch)) return candidate
        }
        return null
    }
}
