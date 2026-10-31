package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration 41→42: the daily summaries' time of episodes with a known release date
 * ([ListeningDayTotals.releaseKnownMs]), so new releases are a share of that time and an episode
 * without a date no longer counts as older.
 *
 * Adds the column to `listening_rollups` and `listening_days_pushed`, NOT NULL with the schema default
 * 0, so every existing summary reads "not known" without being rewritten (its sessions are gone, so
 * nothing can tell it). Sessions still held store their episode's release date and fold it in when
 * summed, so a day still held in sessions differs from the day the cloud holds and goes up with it.
 * Runs inside Room's migration transaction and uses nothing but the database. Never edit it once a
 * phone may have run it; later changes go to v43.
 */
internal object ListeningReleaseKnownMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        for (table in listOf("listening_rollups", "listening_days_pushed")) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `releaseKnownMs` INTEGER NOT NULL DEFAULT 0")
        }
    }
}
