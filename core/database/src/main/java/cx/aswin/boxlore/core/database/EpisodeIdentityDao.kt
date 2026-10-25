package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Episode aliases and shared group state (docs/EPISODE_IDS.md §5).
 *
 * Reads and writes only. [chooseOwner] evaluates the owner order the v39 upgrade used; when the
 * owner may change, and which alias states may resolve a lookup, belongs to the callers
 * ([EpisodeListenerStore] for listener state).
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

    /**
     * The trusted aliases among [aliasIds] whose episode has at least one other trusted ID, so the
     * episode's listener state must be projected across them. Callers pass at most 500 IDs per call.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, projected listener state).
     */
    @Query("$SHARED_TRUSTED_ALIASES AND a.aliasId IN (:aliasIds)")
    suspend fun sharedTrustedAliases(aliasIds: List<String>): List<EpisodeAliasEntity>

    /**
     * Every trusted alias whose episode has at least one other trusted ID. Re-emits only when
     * `episode_aliases` changes, so lists can project without a per-emission alias scan.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, projected listener state).
     */
    @Query(SHARED_TRUSTED_ALIASES)
    fun sharedTrustedAliasesFlow(): Flow<List<EpisodeAliasEntity>>

    /**
     * The owner among one episode's trusted aliases, by the order the v39 upgrade seeded with
     * (docs/EPISODE_IDS.md §5): the latest listening (a history row with no play or like time
     * counts as none), else a completed download, else the ID that equals its recipe ID (a served
     * ID), else the oldest catalog row, else the lowest ID. Null when the episode has no trusted ID.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, one owner per episode).
     */
    @Query(
        """
        SELECT a.aliasId FROM episode_aliases a
        LEFT JOIN local_episodes le ON le.episodeId = a.aliasId
        WHERE a.podcastId = :podcastId AND a.catalogKey = :catalogKey AND a.state = 'trusted'
        ORDER BY
            (
                SELECT NULLIF(MAX(h.lastPlayedAt, h.likedAt), 0) FROM listening_history h
                WHERE h.episodeId = a.aliasId
            ) DESC,
            EXISTS (
                SELECT 1 FROM downloaded_episodes d
                WHERE d.episodeId = a.aliasId AND d.status = ${DownloadedEpisodeEntity.STATUS_COMPLETED}
            ) DESC,
            a.aliasId = CAST(a.recipeId AS TEXT) DESC,
            le.rowid IS NULL, le.rowid ASC, a.aliasId ASC
        LIMIT 1
        """,
    )
    suspend fun chooseOwner(podcastId: String, catalogKey: String,): String?

    /**
     * Marks [ownerId] as the one owner of its episode (show, key) and clears the mark on every other
     * ID of that key. Returns the rows changed (0 when the marks were already right).
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, one owner per episode).
     */
    @Query(
        """
        UPDATE episode_aliases SET owner = (aliasId = :ownerId)
        WHERE podcastId = :podcastId AND catalogKey = :catalogKey AND owner != (aliasId = :ownerId)
        """,
    )
    suspend fun setOwner(podcastId: String, catalogKey: String, ownerId: String,): Int

    /**
     * True when [aliasId] holds listener state: a history row with any listening, progress, like or
     * completion, or a completed download. An owner that holds none may hand ownership over.
     *
     * Added in P4 of the own-episode-IDs plan (plan section 8, one owner per episode).
     */
    @Query(
        """
        SELECT EXISTS (
            SELECT 1 FROM listening_history h
            WHERE h.episodeId = :aliasId
              AND (h.lastPlayedAt > 0 OR h.likedAt > 0 OR h.progressMs > 0 OR h.isCompleted = 1 OR h.isLiked = 1)
        ) OR EXISTS (
            SELECT 1 FROM downloaded_episodes d
            WHERE d.episodeId = :aliasId AND d.status = ${DownloadedEpisodeEntity.STATUS_COMPLETED}
        )
        """,
    )
    suspend fun holdsListenerState(aliasId: String): Boolean
}

/** Trusted aliases that share their (show, key) with another trusted alias. */
private const val SHARED_TRUSTED_ALIASES = """
    SELECT a.* FROM episode_aliases a
    WHERE a.state = 'trusted' AND a.catalogKey IS NOT NULL
      AND EXISTS (
          SELECT 1 FROM episode_aliases b
          WHERE b.podcastId = a.podcastId AND b.catalogKey = a.catalogKey
            AND b.state = 'trusted' AND b.aliasId != a.aliasId
      )
"""
