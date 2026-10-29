package cx.aswin.boxlore.core.database

import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * Recipe IDs of stored episode IDs, for analytics (`recipe_id` beside `episode_id`): an ID's own
 * value when it is in the recipe range, else the `recipeId` of its trusted, keyed alias
 * (docs/EPISODE_IDS.md §2 and §5). Ambiguous and unresolved aliases, and values outside the recipe
 * range, give nothing, so no recipe ID is ever guessed. IDs with neither are left out.
 *
 * Reads Room: call off the main thread. At most 500 IDs go into one query.
 *
 * Added in P4 PR 18 of the own-episode-IDs plan (plan section "Identity at every boundary", analytics).
 * COMPAT(own-ids): analytics joins on recipe IDs. Permanent: never remove (MIGRATION.md §5).
 */
suspend fun EpisodeIdentityDao.knownRecipeIds(episodeIds: Collection<String>): Map<String, Long> {
    val ids = episodeIds.map(String::trim).filter(String::isNotEmpty).distinct()
    if (ids.isEmpty()) return emptyMap()
    val known = HashMap<String, Long>()
    ids.forEach { id -> ownRecipeId(id)?.let { known[id] = it } }
    ids.filterNot(known::containsKey).chunked(MAX_IDS_PER_QUERY).forEach { chunk ->
        known.putAll(trustedRecipeIds(getAliases(chunk)))
    }
    return known
}

/**
 * [id] itself when it is a recipe-range ID. IDs in that range are only ever issued as recipe IDs
 * (phone mints and reserved IDs are negative, Podcast Index IDs are far below it), so the analytics
 * façade can use it on any thread without a lookup.
 */
internal fun ownRecipeId(id: String): Long? = id.trim().toLongOrNull()?.takeIf(EpisodeRecipeId::isRecipeId)

/** Alias ID to recipe ID for the trusted, keyed [aliases] that hold a recipe-range value. */
internal fun trustedRecipeIds(aliases: List<EpisodeAliasEntity>): Map<String, Long> = aliases
    .filter { it.state == EpisodeAliasState.TRUSTED && it.catalogKey != null }
    .mapNotNull { alias -> alias.recipeId?.takeIf(EpisodeRecipeId::isRecipeId)?.let { alias.aliasId to it } }
    .toMap()

private const val MAX_IDS_PER_QUERY = 500
