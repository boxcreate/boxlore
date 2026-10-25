package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

/**
 * Episode aliases and shared group state (docs/EPISODE_IDS.md §5).
 *
 * Plain reads and writes only. Deciding which ID owns an episode, and which alias states may
 * resolve a lookup, belongs to the callers; nothing in the app reads these tables yet.
 *
 * Added in P4 of the own-episode-IDs plan (plan section 8, Room v39 aliases).
 * COMPAT(own-ids): old local episode IDs keep resolving through aliases. Permanent: never remove (MIGRATION.md §5).
 */
@Dao
interface EpisodeIdentityDao {
    /** Adds [aliases] whose ID has no row yet; an existing alias is left exactly as it is. Returns -1 per skipped row. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAliasesIfAbsent(aliases: List<EpisodeAliasEntity>): List<Long>

    /** Writes [alias], replacing the stored row with the same ID in place. */
    @Upsert
    suspend fun upsertAlias(alias: EpisodeAliasEntity)

    /** The alias row for one stored episode ID, or null when the ID has none. */
    @Query("SELECT * FROM episode_aliases WHERE aliasId = :aliasId LIMIT 1")
    suspend fun getAlias(aliasId: String): EpisodeAliasEntity?

    /** Alias rows for these stored IDs, in no particular order. Callers pass at most 500 IDs per call. */
    @Query("SELECT * FROM episode_aliases WHERE aliasId IN (:aliasIds)")
    suspend fun getAliases(aliasIds: List<String>): List<EpisodeAliasEntity>

    /** Every stored ID of one episode (show, catalog key), owner first, then oldest first. */
    @Query(
        """
        SELECT * FROM episode_aliases
        WHERE podcastId = :podcastId AND catalogKey = :catalogKey
        ORDER BY owner DESC, createdAt ASC, aliasId ASC
        """,
    )
    suspend fun aliasesForKey(podcastId: String, catalogKey: String,): List<EpisodeAliasEntity>

    /** Alias rows of one show whose (show, key) has this recipe ID; more than one key means a collision. */
    @Query(
        """
        SELECT * FROM episode_aliases
        WHERE podcastId = :podcastId AND recipeId = :recipeId
        ORDER BY owner DESC, createdAt ASC, aliasId ASC
        """,
    )
    suspend fun aliasesForRecipeId(podcastId: String, recipeId: Long,): List<EpisodeAliasEntity>

    /** IDs of one show still waiting for a key, to retry when the show's feed is read. */
    @Query(
        """
        SELECT * FROM episode_aliases
        WHERE podcastId = :podcastId AND state = 'unresolved'
        ORDER BY aliasId ASC
        """,
    )
    suspend fun unresolvedAliases(podcastId: String): List<EpisodeAliasEntity>

    /** Shared like and completion settings of one episode, or null when none were written. */
    @Query("SELECT * FROM episode_group_state WHERE podcastId = :podcastId AND catalogKey = :catalogKey LIMIT 1")
    suspend fun getGroupState(podcastId: String, catalogKey: String,): EpisodeGroupStateEntity?

    /** Writes one episode's shared like and completion settings. */
    @Upsert
    suspend fun upsertGroupState(state: EpisodeGroupStateEntity)
}
