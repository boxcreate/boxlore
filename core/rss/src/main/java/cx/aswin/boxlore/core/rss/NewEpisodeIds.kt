package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * The recipe hash new catalog rows are minted with. Production always uses [RECIPE]
 * ([EpisodeRecipeId.forNamespace]). Collision tests pass a hash that gives two keys the same ID,
 * because a real recipe clash can't be produced on demand (docs/EPISODE_IDS.md §4: clashes are
 * forced with a test-only hash override, through Room).
 *
 * Added in P4 PR 9 (own-IDs plan section 4, recipe IDs and the test-only hash override).
 * COMPAT(own-ids): every new recipe ID goes through it. Permanent: never remove (MIGRATION.md §5).
 */
fun interface RecipeIdHash {
    /** The recipe ID of (namespace [epNs], [catalogKey]); [catalogKey] is already recipe-trimmed. */
    fun recipeId(epNs: Long, catalogKey: String): Long

    companion object {
        /** The shared recipe (docs/EPISODE_IDS.md §2). */
        val RECIPE: RecipeIdHash = RecipeIdHash { epNs, key -> EpisodeRecipeId.forNamespace(epNs, key) }
    }
}

/**
 * How a catalog read picks the ID of an item that no stored row of the show holds
 * (docs/EPISODE_IDS.md §4–5):
 * 1. the ID an evicted row of the same (show, key) held, from its trusted alias (the owner first,
 *    then the oldest);
 * 2. else, for an item that follows the identity contract ([ParsedFeedItem.keyFollowsContract]), an
 *    ID only this show's listener rows or unresolved aliases hold, when the item provably owns it
 *    ([listeners], [ListenerIdAdoption]: its own phone mint, else a unique listener match), so that
 *    listener state stays attached without a re-key;
 * 3. else, for such an item, the recipe ID of (show, key) in the show's namespace [epNs];
 * 4. else a phone-minted negative ID, as before ([StickyEpisodeIdentity.assignEpisodeId]).
 *
 * [LocalEpisodeCollisionGuard] then checks each new ID inside the write's transaction. When a reused
 * ID (1 or 2) belongs to another episode by then, it tries the key's other trusted alias IDs and the
 * recipe ID before it reserves one. Ambiguous aliases never decide an ID; unresolved ones only
 * through 2.
 *
 * Added in P4 PR 9 (own-IDs plan section 4, recipe IDs for new episodes; section 5, aliases).
 * COMPAT(own-ids): decides every new local episode ID of a Podcast Index show. Permanent: never remove (MIGRATION.md §5).
 *
 * @property epNs the show's episode namespace, or null when the show never takes recipe IDs
 *   (`rss:` shows and show IDs that aren't canonical)
 * @property listeners IDs only this show's listener rows or unresolved aliases hold
 */
class NewEpisodeIds internal constructor(
    val epNs: Long?,
    aliases: List<EpisodeAliasEntity>,
    private val hash: RecipeIdHash,
    internal val listeners: ListenerEvidence,
) {
    constructor(
        epNs: Long?,
        aliases: List<EpisodeAliasEntity> = emptyList(),
        hash: RecipeIdHash = RecipeIdHash.RECIPE,
    ) : this(epNs, aliases, hash, ListenerEvidence.NONE)

    /** Trusted alias IDs by catalog key, owner first, then oldest. */
    private val trustedByKey: Map<String, List<String>> =
        aliases
            .filter { it.state == EpisodeAliasState.TRUSTED && it.catalogKey != null }
            .sortedWith(compareByDescending<EpisodeAliasEntity> { it.owner }.thenBy { it.createdAt }.thenBy { it.aliasId })
            .groupBy({ requireNotNull(it.catalogKey) }, { it.aliasId })

    /** True when some evicted episode's ID may be reused. */
    val hasAliases: Boolean get() = trustedByKey.isNotEmpty()

    /** The trusted IDs stored for (show, [catalogKey]), owner first. */
    fun aliasIdsFor(catalogKey: String): List<String> = trustedByKey[catalogKey].orEmpty()

    /** The recipe ID of (show, [catalogKey]), or null when the show takes none. */
    fun recipeIdFor(catalogKey: String): Long? = epNs?.let { hash.recipeId(it, catalogKey) }

    companion object {
        /** No namespace and no aliases: every new item keeps a phone-minted negative ID. */
        val NONE: NewEpisodeIds = NewEpisodeIds(epNs = null)
    }
}

/**
 * Picks the episode namespace a catalog read mints recipe IDs in (decision D12).
 *
 * The phone keeps one namespace per show, seeded as the show ID (Room v39 seeds both
 * `local_episode_feeds.epNs` and `podcasts.epNs`), and never changes it once set: issued recipe IDs
 * depend on it. A namespace the server reports differently is compared, never adopted, so no
 * stored or new ID moves; the refresh logs the difference.
 *
 * Added in P4 PR 9 (own-IDs plan section 4, recipe IDs; decision D12).
 * COMPAT(own-ids): issued recipe IDs depend on the namespace. Permanent: never remove (MIGRATION.md §5).
 */
internal object EpisodeNamespaces {
    /**
     * The namespace for [podcastId]: the catalog's stored value, else the show's, else the show ID.
     * Null for shows that never take recipe IDs, and for a stored value outside the recipe's range
     * (nothing can have been minted under it, and it is never overwritten).
     */
    fun choose(podcastId: String, catalogEpNs: Long?, showEpNs: Long?,): Long? {
        val showId = EpisodeRecipeId.showIdOrNull(podcastId) ?: return null
        val stored = catalogEpNs ?: showEpNs ?: return showId
        return stored.takeIf { it in 1..EpisodeRecipeId.SHOW_ID_MAX }
    }

    /** True when the server reported a namespace other than the one the phone keeps. */
    fun differs(kept: Long?, reported: Long?,): Boolean = kept != null && reported != null && kept != reported
}
