package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Per-show progress of the one-time feed address repair ([UpgradeRepairShowEntity]).
 *
 * Added in P4 of the own-episode-IDs plan (plan section 11, upgrade repair).
 * COMPAT(own-ids): someone can upgrade from an old build at any time. Permanent: never remove (MIGRATION.md §5).
 */
@Dao
interface UpgradeRepairDao {
    /** Shows with repair steps left, in show ID order. */
    @Query("SELECT * FROM upgrade_repair_shows WHERE state = 'pending' ORDER BY podcastId ASC")
    suspend fun pendingShows(): List<UpgradeRepairShowEntity>

    /** How many shows still have repair steps left; zero means the repair has nothing to do. */
    @Query("SELECT COUNT(*) FROM upgrade_repair_shows WHERE state = 'pending'")
    suspend fun pendingCount(): Int

    /** One show's repair progress, or null when the show was never part of the repair. */
    @Query("SELECT * FROM upgrade_repair_shows WHERE podcastId = :podcastId LIMIT 1")
    suspend fun getShow(podcastId: String): UpgradeRepairShowEntity?

    /** Records one show's repair progress. */
    @Upsert
    suspend fun upsert(show: UpgradeRepairShowEntity)
}
