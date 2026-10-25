package cx.aswin.boxlore.core.catalog

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * One episode (show, catalog key) as the phone's trusted aliases know it (docs/EPISODE_IDS.md §5).
 *
 * @property podcastId the episode's show
 * @property catalogKey its recipe-trimmed catalog key
 * @property recipeId its recipe ID in the show's namespace, when recorded
 * @property memberIds every trusted stored ID of the episode, the owner first
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links, pushes and Auto).
 */
data class EpisodeAliasKey(val podcastId: String, val catalogKey: String, val recipeId: Long?, val memberIds: List<String>,)

/**
 * The alias reads that resolve an episode a link, push, share or Android Auto item names by an ID
 * no local row holds. Only trusted aliases resolve: ambiguous and unresolved ones never do, and a
 * recipe ID resolves only when exactly one key of the show has it (docs/EPISODE_IDS.md §4).
 *
 * [NONE] knows no aliases (tests, and callers built without a database).
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links, pushes and Auto).
 * COMPAT(own-ids): old and shared IDs resolve to the phone's own rows. Permanent: never remove (MIGRATION.md §5).
 */
interface EpisodeAliasReads {
    /** The episode stored ID [episodeId] is a trusted alias of, or null. */
    suspend fun trustedKeyOf(episodeId: String): EpisodeAliasKey? = null

    /** The one episode of show [podcastId] whose trusted aliases carry [recipeId], or null (none, or a collision). */
    suspend fun trustedKeyForRecipe(podcastId: String, recipeId: Long,): EpisodeAliasKey? = null

    /** The episode namespace the phone mints show [podcastId]'s recipe IDs in: the stored one, else the show ID. */
    suspend fun namespaceOf(podcastId: String): Long? = EpisodeRecipeId.showIdOrNull(podcastId)

    companion object {
        val NONE: EpisodeAliasReads = object : EpisodeAliasReads {}
    }
}

/**
 * [EpisodeAliasReads] over the main Room database. [database] is read on first use, so building it
 * opens nothing.
 *
 * Added in P4 PR 13 of the own-episode-IDs plan (plan section 9, links, pushes and Auto).
 * COMPAT(own-ids): old and shared IDs resolve to the phone's own rows. Permanent: never remove (MIGRATION.md §5).
 */
class RoomEpisodeAliasReads(private val database: () -> BoxLoreDatabase,) : EpisodeAliasReads {
    private val aliases get() = database().episodeIdentityDao()

    override suspend fun trustedKeyOf(episodeId: String): EpisodeAliasKey? {
        val alias = aliases.getAlias(episodeId)?.takeIf(EpisodeAliasEntity::isTrusted) ?: return null
        val key = alias.catalogKey ?: return null
        return keyOf(alias.podcastId, key, alias.recipeId, fallbackMember = episodeId)
    }

    override suspend fun trustedKeyForRecipe(podcastId: String, recipeId: Long,): EpisodeAliasKey? {
        if (!EpisodeRecipeId.isRecipeId(recipeId)) return null
        val key = aliases.aliasesForRecipeId(podcastId, recipeId)
            .filter(EpisodeAliasEntity::isTrusted)
            .mapNotNull(EpisodeAliasEntity::catalogKey)
            .distinct()
            .singleOrNull() ?: return null
        return keyOf(podcastId, key, recipeId, fallbackMember = null)
    }

    override suspend fun namespaceOf(podcastId: String): Long? {
        val showId = EpisodeRecipeId.showIdOrNull(podcastId) ?: return null
        val db = database()
        val stored = db.localEpisodeCatalogDao().getFeed(podcastId)?.epNs ?: db.podcastDao().getPodcast(podcastId)?.epNs
        return when {
            stored == null -> showId
            stored in 1..EpisodeRecipeId.SHOW_ID_MAX -> stored
            else -> null
        }
    }

    private suspend fun keyOf(podcastId: String, catalogKey: String, recipeId: Long?, fallbackMember: String?,): EpisodeAliasKey? {
        val members = aliases.aliasesForKey(podcastId, catalogKey).filter(EpisodeAliasEntity::isTrusted).map(EpisodeAliasEntity::aliasId)
        val ids = members.ifEmpty { listOfNotNull(fallbackMember) }
        if (ids.isEmpty()) return null
        return EpisodeAliasKey(podcastId, catalogKey, recipeId, ids)
    }
}

private fun EpisodeAliasEntity.isTrusted(): Boolean = state == EpisodeAliasState.TRUSTED && catalogKey != null
