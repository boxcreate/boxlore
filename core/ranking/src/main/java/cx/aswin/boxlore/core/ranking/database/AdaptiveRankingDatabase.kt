package cx.aswin.boxlore.core.ranking.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        AdaptiveModelEntity::class,
        PreferenceFacetEntity::class,
        RankingExposureEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class AdaptiveRankingDatabase : RoomDatabase() {
    abstract fun adaptiveRankingDao(): AdaptiveRankingDao

    companion object {
        private const val DATABASE_NAME = "adaptive_ranking_database"

        @Volatile
        private var instance: AdaptiveRankingDatabase? = null

        fun getDatabase(context: Context): AdaptiveRankingDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AdaptiveRankingDatabase::class.java,
                DATABASE_NAME,
            )
                // Moving from a newer beta back to production must not crash on open; ranking
                // state is relearned (or restored from an explicit backup) instead.
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
                .also { instance = it }
        }
    }
}
