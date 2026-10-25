package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasState
import kotlin.math.abs

/**
 * IDs of one show that its listener rows (history, the queue, downloads) or its unresolved aliases
 * hold, and that nothing else identifies: no catalog row of any show holds them, and no alias gives
 * them a key or puts them under another show. Restored history, a played feed-only extra, a
 * catalog evicted before the v39 upgrade, and an episode played from search before the show was
 * followed all leave such IDs.
 *
 * Before P4 PR 9 a catalog read gave an item that no stored row held its Podcast Index match or its
 * phone mint, so listener rows on either ID stayed attached without a re-key. Now such an item takes
 * its recipe ID unless [ListenerIdAdoption] proves one of these IDs is its own.
 *
 * Added in P4 PR 9 (own-IDs plan section 4: nothing is re-keyed; section 5: unresolved aliases are
 * retried when the show's feed is read).
 * COMPAT(own-ids): listener state recorded under older IDs stays attached. Permanent: never remove (MIGRATION.md §5).
 *
 * @property heldIds the IDs an item may keep
 * @property references the listener rows that hold [heldIds]
 */
internal class ListenerEvidence(val heldIds: Set<String>, val references: List<LocalCatalogListenerReference>,) {
    val isEmpty: Boolean get() = heldIds.isEmpty()

    companion object {
        /** No listener-held IDs. */
        val NONE: ListenerEvidence = ListenerEvidence(emptySet(), emptyList())

        private const val BRIEFING_ID_PREFIX = "briefing_"

        /** The IDs to look up (catalog owners, aliases) before calling [of]. */
        fun candidateIds(references: List<LocalCatalogListenerReference>, showAliases: List<EpisodeAliasEntity>,): List<String> =
            (references.map { it.episodeId } + showAliases.filter { it.state == EpisodeAliasState.UNRESOLVED }.map { it.aliasId })
                .filter(::isEpisodeId)
                .distinct()

        /**
         * @param podcastId the show being read
         * @param references the show's history, queue and download rows
         * @param showAliases the show's aliases (its unresolved ones join [heldIds])
         * @param catalogHeldIds which of [candidateIds] a catalog row of any show holds
         * @param aliasesById the aliases of [candidateIds], across every show
         */
        fun of(
            podcastId: String,
            references: List<LocalCatalogListenerReference>,
            showAliases: List<EpisodeAliasEntity>,
            catalogHeldIds: Set<String>,
            aliasesById: Map<String, EpisodeAliasEntity>,
        ): ListenerEvidence {
            val held =
                candidateIds(references, showAliases)
                    .filter { id ->
                        val alias = aliasesById[id]
                        id !in catalogHeldIds &&
                            (alias == null || (alias.podcastId == podcastId && alias.state == EpisodeAliasState.UNRESOLVED))
                    }.toSet()
            if (held.isEmpty()) return NONE
            return ListenerEvidence(held, references.filter { it.episodeId in held })
        }

        /** A stored episode ID: a non-zero number, never a Brief. */
        private fun isEpisodeId(id: String): Boolean = !id.startsWith(BRIEFING_ID_PREFIX) && id.trim().toLongOrNull()?.let { it != 0L } == true
    }
}

/**
 * Decides which [ListenerEvidence] ID a feed item no stored row or trusted alias holds keeps,
 * instead of its recipe ID (docs/EPISODE_IDS.md §4: nothing is re-keyed):
 * 1. **Its own phone mint.** When the item's own minted ID (the parser's, or
 *    [StickyEpisodeIdentity.ownMintedId] of its key or an older key) is held, it is the same hash
 *    input, so the same episode (as the guard's legacy heal reasons).
 * 2. **A unique listener match.** Otherwise a held ID whose listener rows match exactly one feed
 *    item, by audio address, else by title and a date within two days ([LocalCatalogOrphanRematch]'s
 *    rules), is that item's when no other held ID matches the same item. Matching runs over every
 *    item of the feed, so an ID that matches a stored row is left to the reconciler.
 * Each ID is kept by one item at most, in document order.
 *
 * Added in P4 PR 9 (own-IDs plan section 4: nothing is re-keyed; section 5: unresolved aliases).
 * COMPAT(own-ids): listener state recorded under older IDs stays attached. Permanent: never remove (MIGRATION.md §5).
 */
internal object ListenerIdAdoption {
    /** Why an item keeps a held ID. */
    enum class Kind {
        OWN_MINT,
        LISTENER_MATCH,
    }

    data class Adopted(val episodeId: String, val kind: Kind,)

    /**
     * One winning feed item.
     *
     * @property open the item may keep a held ID (no stored row or trusted alias holds it, and it follows the identity contract)
     * @property mints its own minted IDs, for open items
     * @property audioKeys its trimmed audio addresses: the parser's pick, its enclosures, the other parser's pick
     */
    data class Candidate(
        val position: Int,
        val open: Boolean,
        val mints: List<String>,
        val audioKeys: Set<String>,
        val title: String,
        val publishedDate: Long,
    )

    /**
     * Held IDs by item position. [candidates] are in document order; [claimed] are IDs a stored
     * row or an alias match already took.
     */
    fun adopt(candidates: List<Candidate>, evidence: ListenerEvidence, claimed: Set<String>,): Map<Int, Adopted> {
        if (evidence.isEmpty) return emptyMap()
        val result = LinkedHashMap<Int, Adopted>()
        val taken = HashSet(claimed)
        for (candidate in candidates.filter { it.open }) {
            val id = candidate.mints.firstOrNull { it in evidence.heldIds && it !in taken } ?: continue
            taken += id
            result[candidate.position] = Adopted(id, Kind.OWN_MINT)
        }
        val targetByHeldId =
            evidence.references
                .filter { it.episodeId !in taken }
                .groupBy { it.episodeId }
                .mapNotNull { (id, references) ->
                    references.mapNotNull { match(it, candidates) }.distinct().singleOrNull()?.let { id to it }
                }
        val open = candidates.filter { it.open }.mapTo(HashSet()) { it.position }
        for ((position, ids) in targetByHeldId.groupBy({ it.second }, { it.first })) {
            if (position !in open || position in result || ids.size != 1) continue
            result[position] = Adopted(ids.single(), Kind.LISTENER_MATCH)
        }
        return result
    }

    private fun match(reference: LocalCatalogListenerReference, candidates: List<Candidate>,): Int? {
        val audio = reference.enclosureUrl?.trim().orEmpty()
        if (audio.isNotEmpty()) {
            candidates.singleOrNull { audio in it.audioKeys }?.let { return it.position }
        }
        val title = reference.title?.trim().orEmpty()
        val date = reference.publishedDate ?: return null
        if (title.isEmpty()) return null
        return candidates
            .singleOrNull { it.title.trim() == title && abs(it.publishedDate - date) <= LocalCatalogOrphanRematch.DATE_WINDOW_SECONDS }
            ?.position
    }
}
