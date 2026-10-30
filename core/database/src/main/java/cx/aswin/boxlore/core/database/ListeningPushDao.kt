package cx.aswin.boxlore.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** What the cloud last stored of this phone's daily listening ([PushedListeningDayEntity], Room v40). */
@Dao
interface ListeningPushDao {
    @Query("SELECT * FROM listening_days_pushed WHERE deviceId = :deviceId")
    suspend fun getPushedDays(deviceId: String): List<PushedListeningDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPushedDays(days: List<PushedListeningDayEntity>)

    @Delete
    suspend fun deletePushedDays(days: List<PushedListeningDayEntity>)

    /** Forgets every pushed day (a new account or a deleted cloud copy): the next pushes send them all again. */
    @Query("DELETE FROM listening_days_pushed")
    suspend fun deleteAllPushedDays()
}
