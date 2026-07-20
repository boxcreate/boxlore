package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListeningRollupDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRollup(rollup: ListeningRollupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRollups(rollups: List<ListeningRollupEntity>)

    @Query("SELECT * FROM listening_rollups WHERE localDay >= :sinceDay ORDER BY localDay DESC")
    fun observeRollupsSince(sinceDay: Long): Flow<List<ListeningRollupEntity>>

    @Query("SELECT * FROM listening_rollups ORDER BY localDay DESC")
    fun observeAllRollups(): Flow<List<ListeningRollupEntity>>

    @Query("SELECT * FROM listening_rollups WHERE localDay >= :sinceDay ORDER BY localDay DESC")
    suspend fun getRollupsSince(sinceDay: Long): List<ListeningRollupEntity>

    @Query("SELECT * FROM listening_rollups ORDER BY localDay DESC")
    suspend fun getAllRollups(): List<ListeningRollupEntity>

    @Query("SELECT * FROM listening_rollups WHERE localDay = :localDay AND episodeId = :episodeId LIMIT 1")
    suspend fun getRollup(
        localDay: Long,
        episodeId: String,
    ): ListeningRollupEntity?

    @Query("SELECT * FROM listening_rollups WHERE episodeId = :episodeId")
    suspend fun getRollupsForEpisode(episodeId: String): List<ListeningRollupEntity>

    @Query("SELECT MIN(lastListenedAt) FROM listening_rollups")
    suspend fun getEarliestRollupListenedAt(): Long?

    @Query("DELETE FROM listening_rollups WHERE episodeId = :episodeId")
    suspend fun deleteRollupsForEpisode(episodeId: String)

    @Query("DELETE FROM listening_rollups")
    suspend fun deleteAllRollups()
}
