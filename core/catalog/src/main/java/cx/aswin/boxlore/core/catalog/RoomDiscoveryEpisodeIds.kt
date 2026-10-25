package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * [DiscoveryEpisodeIds] over the phone's alias table (`episode_aliases`, docs/EPISODE_IDS.md §5).
 *
 * - **Out:** a stored ID with a trusted alias is sent as that alias's recipe ID; a stored ID in the
 *   recipe range without one is its own recipe ID. Anything else (an unresolved or ambiguous alias,
 *   a Podcast Index ID whose show's feed was never read) has none.
 * - **In:** a (show, recipe ID) maps to the owner of its episode when the trusted aliases that hold
 *   that recipe ID under the show, or the stored ID equal to it, all name one catalog key. Two keys
 *   with one recipe ID (a collision) or no trusted alias leave the recipe ID as it is.
 *
 * [database] is read on first use, so building this opens nothing.
 *
 * Added in P4 PR 12 of the own-episode-IDs plan (plan section 9, v2 discovery routes).
 * COMPAT(own-ids): discovery reads listener state under older IDs. Permanent: never remove (MIGRATION.md §5).
 */
class RoomDiscoveryEpisodeIds(private val database: () -> BoxLoreDatabase,) : DiscoveryEpisodeIds {
    override suspend fun recipeIds(episodeIds: Collection<String>): Map<String, Long> {
        val ids = episodeIds.filter(String::isNotBlank).distinct()
        if (ids.isEmpty()) return emptyMap()
        val result = DiscoveryEpisodeIds.ownRecipeIds(ids).toMutableMap()
        val dao = database().episodeIdentityDao()
        for (chunk in ids.chunked(MAX_IDS_PER_QUERY)) {
            for (alias in dao.getAliases(chunk)) {
                val recipeId = alias.recipeId?.takeIf { alias.isTrustedKeyed() && EpisodeRecipeId.isRecipeId(it) } ?: continue
                result[alias.aliasId] = recipeId
            }
        }
        return result
    }

    override suspend fun localOwners(refs: Collection<DiscoveryEpisodeRef>): Map<DiscoveryEpisodeRef, String> {
        val wanted = refs.distinct()
        if (wanted.isEmpty()) return emptyMap()
        val db = database()
        val dao = db.episodeIdentityDao()
        val store = db.episodeListenerStore()
        val storedUnderOwnId =
            wanted
                .map { it.recipeId.toString() }
                .chunked(MAX_IDS_PER_QUERY)
                .flatMap { dao.getAliases(it) }
                .associateBy(EpisodeAliasEntity::aliasId)
        val result = LinkedHashMap<DiscoveryEpisodeRef, String>()
        for (ref in wanted) {
            val sameId = storedUnderOwnId[ref.recipeId.toString()]?.takeIf { it.podcastId == ref.podcastId }
            val trusted = (dao.aliasesForRecipeId(ref.podcastId, ref.recipeId) + listOfNotNull(sameId)).filter { it.isTrustedKeyed() }
            if (trusted.isEmpty() || trusted.distinctBy(EpisodeAliasEntity::catalogKey).size != 1) continue
            result[ref] = store.group(trusted.first().aliasId).ownerId
        }
        return result
    }

    private fun EpisodeAliasEntity.isTrustedKeyed(): Boolean = state == EpisodeAliasState.TRUSTED && catalogKey != null

    private companion object {
        const val MAX_IDS_PER_QUERY = 500
    }
}
