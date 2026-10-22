package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.LocalEpisodeOwner

/**
 * Keeps one episode from replacing another in `local_episodes` (docs/EPISODE_IDS.md §4: no store
 * overwrites a different episode, both survive).
 *
 * `upsertEpisodes` replaces by episode ID, and an incoming row's ID used to be checked only against
 * its own show's rows. Two shows that read the same feed (a feed listed twice in Podcast Index)
 * mint the same IDs from the same feed namespace, so the second show's refresh took the first
 * show's rows: wrong show, title and audio, and a cached download of the other episode.
 *
 * A row whose ID another (show, catalog key) already holds, in the database or earlier in the same
 * batch, gets a reserved local ID instead ([StickyEpisodeIdentity.reservedEpisodeId]); the same
 * (show, key) keeps updating its row. A reserved ID is stable: the next refresh finds the stored
 * row by its key and reuses it. Call inside the write's transaction.
 *
 * COMPAT(own-ids): permanent; the Room alias tables of the v2 app build record these later.
 */
internal object LocalEpisodeCollisionGuard {
    /** Stays under SQLite's bound-parameter limit. */
    private const val LOOKUP_CHUNK = 500

    /** Reserved IDs tried per row before the row is left out of this write. */
    const val MAX_RESERVE_ATTEMPTS = 8

    private data class Owner(val podcastId: String, val key: String)

    /**
     * The rows to write: unchanged when their IDs are free or already theirs, otherwise with a
     * reserved ID. A row with no free ID after [MAX_RESERVE_ATTEMPTS] is left out rather than
     * written over another episode; the refresh then writes nothing and fails.
     */
    suspend fun resolve(
        rows: List<LocalEpisodeEntity>,
        rssNamespaceId: String,
        findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
    ): List<LocalEpisodeEntity> {
        if (rows.isEmpty()) return rows
        val stored = storedOwners(rows.map { it.episodeId }, findOwners)
        val batch = HashMap<String, Owner>()
        val out = ArrayList<LocalEpisodeEntity>(rows.size)
        for (row in rows) {
            val mine = Owner(row.podcastId, row.guid)
            val id =
                if (isFree(row.episodeId, mine, stored, batch)) {
                    row.episodeId
                } else {
                    reserve(row, mine, rssNamespaceId, stored, batch, findOwners)
                } ?: continue
            batch[id] = mine
            out += if (id == row.episodeId) row else row.copy(episodeId = id)
        }
        return out
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

    private suspend fun storedOwners(
        ids: List<String>,
        findOwners: suspend (List<String>) -> List<LocalEpisodeOwner>,
    ): MutableMap<String, Owner> {
        val stored = HashMap<String, Owner>()
        for (chunk in ids.distinct().chunked(LOOKUP_CHUNK)) {
            for (owner in findOwners(chunk)) stored[owner.episodeId] = Owner(owner.podcastId, owner.guid)
        }
        return stored
    }

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
            val candidate = StickyEpisodeIdentity.reservedEpisodeId(rssNamespaceId, row.podcastId, row.guid, attempt)
            if (candidate !in stored) {
                for (owner in findOwners(listOf(candidate))) stored[owner.episodeId] = Owner(owner.podcastId, owner.guid)
            }
            if (isFree(candidate, mine, stored, batch)) return candidate
        }
        return null
    }
}
