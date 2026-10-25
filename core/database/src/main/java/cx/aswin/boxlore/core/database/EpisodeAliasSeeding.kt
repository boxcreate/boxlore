package cx.aswin.boxlore.core.database

import cx.aswin.boxlore.core.model.EpisodeRecipeId

/** What the v39 upgrade records for one stored episode ID: an `episode_aliases` row without owner and time. */
internal data class AliasSeed(
    val aliasId: String,
    val podcastId: String,
    val catalogKey: String?,
    val recipeId: Long?,
    val state: String,
    val provenance: String,
)

/**
 * Decides the alias the v39 upgrade seeds for each stored episode ID (docs/EPISODE_IDS.md §3, §5).
 *
 * Pure functions, so every rule is unit-tested without SQLite:
 * - Only Podcast Index shows (canonical numeric show IDs) get aliases. `rss:` shows and Brief
 *   episodes (`briefing_*`) are skipped.
 * - A catalog row's stored key is re-trimmed with the recipe set before hashing: migration 31→32
 *   trimmed with SQLite `TRIM()`, which keeps newlines, tabs and no-break spaces.
 * - A synthetic key (`id:` plus the row's own ID) is never hashed: the alias is unresolved.
 * - So is a key that is the row's audio address while its saved item had a GUID: migration 31→32
 *   used the address only because the GUID repeated in the show.
 * - A row that already holds a recipe-range ID keeps it. When the ID equals the recipe ID of the
 *   row's (show, key), the row is that episode's alias; otherwise the alias is ambiguous.
 * - IDs found only in history, the queue or downloads have no key yet, so they are unresolved.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 4, legacy and synthetic keys; section 8, Room v39).
 * COMPAT(own-ids): seeds the aliases old IDs resolve through. Permanent: never remove (MIGRATION.md §5).
 */
internal object EpisodeAliasSeeding {
    private const val SYNTHETIC_KEY_PREFIX = "id:"
    private const val BRIEFING_ID_PREFIX = "briefing_"

    /**
     * The alias for one `local_episodes` row, or null when the row's show or ID gets none.
     *
     * @param storedKey the row's `guid` column (its catalog key as stored)
     * @param audioUrl the row's audio address
     * @param savedItemGuid the GUID of the `episode_supplement_items` row with the same ID, when one exists
     */
    fun forCatalogRow(
        episodeId: String,
        podcastId: String,
        storedKey: String,
        audioUrl: String?,
        savedItemGuid: String?,
    ): AliasSeed? {
        if (!isAliasable(episodeId)) return null
        val showId = EpisodeRecipeId.showIdOrNull(podcastId) ?: return null
        if (storedKey == SYNTHETIC_KEY_PREFIX + episodeId) {
            return unresolved(episodeId, podcastId, EpisodeAliasProvenance.UPGRADE_SYNTHETIC_KEY)
        }
        val key = EpisodeRecipeId.trimKey(storedKey)
            ?: return unresolved(episodeId, podcastId, EpisodeAliasProvenance.UPGRADE_CATALOG)
        val itemGuid = EpisodeRecipeId.trimKey(savedItemGuid)
        if (itemGuid != null && itemGuid != key && key == EpisodeRecipeId.trimKey(audioUrl)) {
            return unresolved(episodeId, podcastId, EpisodeAliasProvenance.UPGRADE_AUDIO_URL_KEY)
        }
        val recipeId = EpisodeRecipeId.forNamespace(showId, key)
        val storedId = episodeId.toLongOrNull()
        val ambiguous = storedId != null && EpisodeRecipeId.isRecipeId(storedId) && storedId != recipeId
        return AliasSeed(
            aliasId = episodeId,
            podcastId = podcastId,
            catalogKey = key,
            recipeId = recipeId,
            state = if (ambiguous) EpisodeAliasState.AMBIGUOUS else EpisodeAliasState.TRUSTED,
            provenance = EpisodeAliasProvenance.UPGRADE_CATALOG,
        )
    }

    /** The alias for an ID held by history, the queue or downloads with no catalog row, or null when it gets none. */
    fun forListenerRow(episodeId: String, podcastId: String,): AliasSeed? {
        if (!isAliasable(episodeId)) return null
        EpisodeRecipeId.showIdOrNull(podcastId) ?: return null
        return unresolved(episodeId, podcastId, EpisodeAliasProvenance.UPGRADE_LISTENER_ROW)
    }

    private fun isAliasable(episodeId: String): Boolean =
        episodeId.isNotBlank() && !episodeId.startsWith(BRIEFING_ID_PREFIX)

    private fun unresolved(episodeId: String, podcastId: String, provenance: String,) = AliasSeed(
        aliasId = episodeId,
        podcastId = podcastId,
        catalogKey = null,
        recipeId = null,
        state = EpisodeAliasState.UNRESOLVED,
        provenance = provenance,
    )
}
