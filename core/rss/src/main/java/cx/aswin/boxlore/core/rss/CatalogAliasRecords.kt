package cx.aswin.boxlore.core.rss

import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.EpisodeAliasEntity
import cx.aswin.boxlore.core.database.EpisodeAliasProvenance
import cx.aswin.boxlore.core.database.EpisodeAliasState
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.model.EpisodeRecipeId

/**
 * The episode-identity reads and writes a catalog refresh needs beyond `local_episodes`: the show's
 * stored namespace and its `episode_aliases` rows (Room v39).
 *
 * Added in P4 PR 9 (own-IDs plan section 4, recipe IDs; section 5, aliases).
 * COMPAT(own-ids): the refresh's access to aliases and namespaces. Permanent: never remove (MIGRATION.md §5).
 */
internal interface CatalogIdentityStore {
    /** `podcasts.epNs` of the show, or null. */
    suspend fun showNamespace(podcastId: String): Long?

    /** Sets `podcasts.epNs` when the show has none; never replaces a stored value. */
    suspend fun keepShowNamespace(podcastId: String, epNs: Long,)

    /** Every alias row of one show. */
    suspend fun aliasesForShow(podcastId: String): List<EpisodeAliasEntity>

    /** Alias rows of up to 500 IDs, across every show. */
    suspend fun aliases(ids: List<String>): List<EpisodeAliasEntity>

    /** Adds aliases whose ID has no row yet; an existing alias stays as it is. */
    suspend fun insertAliases(aliases: List<EpisodeAliasEntity>)

    /** Replaces one alias row in place. */
    suspend fun updateAlias(alias: EpisodeAliasEntity)
}

/**
 * [CatalogIdentityStore] on the app's Room database.
 *
 * Added in P4 PR 9 (own-IDs plan section 4).
 */
internal class RoomCatalogIdentityStore(private val database: BoxLoreDatabase) : CatalogIdentityStore {
    override suspend fun showNamespace(podcastId: String): Long? = database.podcastDao().getEpNs(podcastId)

    override suspend fun keepShowNamespace(podcastId: String, epNs: Long,) = database.podcastDao().setEpNsIfUnset(podcastId, epNs)

    override suspend fun aliasesForShow(podcastId: String): List<EpisodeAliasEntity> = database.episodeIdentityDao().aliasesForShow(podcastId)

    override suspend fun aliases(ids: List<String>): List<EpisodeAliasEntity> = database.episodeIdentityDao().getAliases(ids)

    override suspend fun insertAliases(aliases: List<EpisodeAliasEntity>) {
        if (aliases.isNotEmpty()) database.episodeIdentityDao().insertAliasesIfAbsent(aliases)
    }

    override suspend fun updateAlias(alias: EpisodeAliasEntity) = database.episodeIdentityDao().upsertAlias(alias)
}

/**
 * The alias rows a catalog refresh writes in its transaction (docs/EPISODE_IDS.md §4–5), so every
 * catalog ID of a Podcast Index show keeps resolving after eviction:
 * - **New IDs.** A row that took its recipe ID gets a trusted alias ([EpisodeAliasProvenance.FEED_RECIPE]);
 *   a row the guard gave a reserved ID gets one too ([EpisodeAliasProvenance.FEED_RESERVED]).
 * - **Stored rows without an alias** (rows written after the v39 upgrade by a build without
 *   aliases) get one ([EpisodeAliasProvenance.FEED_STORED_ROW]): trusted, unless the ID is in the
 *   recipe range but is not the recipe ID of its key (a served ID minted elsewhere), which is
 *   ambiguous, as the v39 upgrade seeds it.
 * - **Healed keys** ([LocalEpisodeCollisionGuard.KeyRepair]) are recorded as the alias's provenance
 *   ([EpisodeAliasProvenance.HEAL_RETRIMMED_KEY], [EpisodeAliasProvenance.HEAL_SYNTHETIC_KEY],
 *   [EpisodeAliasProvenance.HEAL_AUDIO_URL_KEY]). An unresolved alias of the healed ID moves to
 *   trusted with the feed's key; a missing one is added. A trusted or ambiguous alias is left as it is.
 * - Items outside the identity contract (phone-only keys) and IDs whose alias belongs to another
 *   show get none; an existing alias is never rewritten except by a heal.
 * - **Owner:** a new trusted alias owns its episode only when no trusted alias of the same
 *   (show, key) owns it yet, so ownership never moves away from an ID that may hold listener state.
 *
 * Pure, so every rule is unit-tested; the refresh reads the inputs and writes the result inside
 * the same transaction as the catalog rows.
 *
 * Added in P4 PR 9 (own-IDs plan section 4, collisions and synthetic keys; section 5, aliases).
 * COMPAT(own-ids): every stored ID keeps resolving through its alias. Permanent: never remove (MIGRATION.md §5).
 */
internal object CatalogAliasRecords {
    data class Writes(val inserts: List<EpisodeAliasEntity>, val updates: List<EpisodeAliasEntity>,) {
        val isEmpty: Boolean get() = inserts.isEmpty() && updates.isEmpty()
    }

    /**
     * @param planned the rows the refresh planned, with where each ID came from
     * @param resolution what the guard let the refresh write
     * @param aliasesById current aliases of the written IDs, across every show
     * @param showAliases current aliases of [podcastId] (for owners)
     * @param newIds the namespace and hash new IDs were minted with; nothing is written without a namespace
     */
    @Suppress("LongParameterList")
    fun of(
        podcastId: String,
        planned: List<PlannedEpisodeRow>,
        resolution: LocalEpisodeCollisionGuard.Resolution,
        aliasesById: Map<String, EpisodeAliasEntity>,
        showAliases: List<EpisodeAliasEntity>,
        newIds: NewEpisodeIds,
        nowMillis: Long,
    ): Writes {
        if (newIds.epNs == null || EpisodeRecipeId.showIdOrNull(podcastId) == null) return Writes(emptyList(), emptyList())
        val recorder = Recorder(podcastId, planned, resolution, aliasesById, showAliases, newIds, nowMillis)
        val inserts = ArrayList<EpisodeAliasEntity>()
        val updates = ArrayList<EpisodeAliasEntity>()
        for (row in resolution.rows.distinctBy { it.episodeId }) {
            val write = recorder.writeFor(row) ?: continue
            if (write.isNew) inserts += write.alias else updates += write.alias
        }
        return Writes(inserts, updates)
    }

    private class Write(val alias: EpisodeAliasEntity, val isNew: Boolean)

    @Suppress("LongParameterList")
    private class Recorder(
        private val podcastId: String,
        planned: List<PlannedEpisodeRow>,
        private val resolution: LocalEpisodeCollisionGuard.Resolution,
        private val aliasesById: Map<String, EpisodeAliasEntity>,
        showAliases: List<EpisodeAliasEntity>,
        private val newIds: NewEpisodeIds,
        private val nowMillis: Long,
    ) {
        private val plannedByKey = planned.associateBy { it.entity.guid }
        private val heals = resolution.repaired.associateBy { it.episodeId }

        /** Keys that already have a trusted owner; a new alias takes ownership only of a key not in here. */
        private val ownedKeys =
            showAliases
                .filter { it.owner && it.state == EpisodeAliasState.TRUSTED }
                .mapNotNullTo(HashSet()) { it.catalogKey }

        /** The alias write for one written row, or null when its alias stays as it is. */
        fun writeFor(row: LocalEpisodeEntity): Write? {
            val plan = plannedByKey[row.guid]?.takeIf { it.keyFollowsContract } ?: return null
            val key = EpisodeRecipeId.trimKey(row.guid) ?: return null
            val recipeId = newIds.recipeIdFor(key) ?: return null
            val existing = aliasesById[row.episodeId]
            val heal = heals[row.episodeId]
            return when {
                heal != null -> healed(existing, row.episodeId, key, recipeId, heal.repair)
                existing != null -> null
                else -> Write(newAlias(row.episodeId, plan, key, recipeId), isNew = true)
            }
        }

        private fun newAlias(episodeId: String, plan: PlannedEpisodeRow, key: String, recipeId: Long,): EpisodeAliasEntity {
            val provenance =
                when {
                    episodeId in resolution.reservedIds -> EpisodeAliasProvenance.FEED_RESERVED
                    plan.source == PlannedIdSource.RECIPE && plan.entity.episodeId == episodeId -> EpisodeAliasProvenance.FEED_RECIPE
                    else -> EpisodeAliasProvenance.FEED_STORED_ROW
                }
            val servedElsewhere = episodeId.toLongOrNull()?.let { EpisodeRecipeId.isRecipeId(it) && it != recipeId } == true
            val state = if (servedElsewhere) EpisodeAliasState.AMBIGUOUS else EpisodeAliasState.TRUSTED
            return EpisodeAliasEntity(
                aliasId = episodeId,
                podcastId = podcastId,
                catalogKey = key,
                recipeId = recipeId,
                state = state,
                owner = state == EpisodeAliasState.TRUSTED && ownedKeys.add(key),
                provenance = provenance,
                createdAt = nowMillis,
            )
        }

        /** A healed row's alias: added when missing, made trusted when unresolved; a trusted or ambiguous one stays. */
        private fun healed(existing: EpisodeAliasEntity?, episodeId: String, key: String, recipeId: Long, repair: LocalEpisodeCollisionGuard.KeyRepair,): Write? {
            val provenance =
                when (repair) {
                    LocalEpisodeCollisionGuard.KeyRepair.RETRIMMED -> EpisodeAliasProvenance.HEAL_RETRIMMED_KEY
                    LocalEpisodeCollisionGuard.KeyRepair.SYNTHETIC_KEY -> EpisodeAliasProvenance.HEAL_SYNTHETIC_KEY
                    LocalEpisodeCollisionGuard.KeyRepair.AUDIO_URL_KEY -> EpisodeAliasProvenance.HEAL_AUDIO_URL_KEY
                }
            if (existing == null) {
                val alias =
                    EpisodeAliasEntity(
                        aliasId = episodeId,
                        podcastId = podcastId,
                        catalogKey = key,
                        recipeId = recipeId,
                        state = EpisodeAliasState.TRUSTED,
                        owner = ownedKeys.add(key),
                        provenance = provenance,
                        createdAt = nowMillis,
                    )
                return Write(alias, isNew = true)
            }
            if (existing.state != EpisodeAliasState.UNRESOLVED || existing.podcastId != podcastId) return null
            val alias =
                existing.copy(
                    catalogKey = key,
                    recipeId = recipeId,
                    state = EpisodeAliasState.TRUSTED,
                    owner = ownedKeys.add(key),
                    provenance = provenance,
                )
            return Write(alias, isNew = false)
        }
    }
}
