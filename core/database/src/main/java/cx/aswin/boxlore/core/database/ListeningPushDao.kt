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

    /**
     * Forgets the richer totals the cloud holds of every pushed day (Room v41, and v42's release dates),
     * keeping the days: each day with totals goes up again, and a day pushed before that is gone still
     * gets its zero day.
     */
    @Query(
        """
        UPDATE listening_days_pushed SET longestSessionMs = 0, speakerMs = 0, headphonesMs = 0, bluetoothMs = 0,
            carMs = 0, castMs = 0, loreMs = 0, smartQueueMs = 0, newReleaseMs = 0, releaseKnownMs = 0
        """,
    )
    suspend fun forgetPushedTotals()

    /**
     * Forgets the time with a known release date the cloud holds of every pushed day (Room v42), keeping
     * the rest: each day that knows it goes up again.
     */
    @Query("UPDATE listening_days_pushed SET releaseKnownMs = 0")
    suspend fun forgetPushedReleaseKnown()
}
