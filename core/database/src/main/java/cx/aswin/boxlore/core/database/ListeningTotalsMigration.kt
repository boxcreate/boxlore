package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration 40→41: the daily summaries' richer totals ([ListeningDayTotals]).
 *
 * Adds one column per total to `listening_rollups`, NOT NULL with the schema default 0, so every
 * existing summary reads "not known" without being rewritten. Sessions keep their own details and
 * fold their totals in when they roll up. Runs inside Room's migration transaction and uses nothing
 * but the database. Never edit it once a phone may have run it; later changes go to v42.
 */
internal object ListeningTotalsMigration {
    private val COLUMNS = listOf(
        "longestSessionMs",
        "speakerMs",
        "headphonesMs",
        "bluetoothMs",
        "carMs",
        "castMs",
        "loreMs",
        "smartQueueMs",
        "newReleaseMs",
    )

    fun migrate(db: SupportSQLiteDatabase) {
        for (column in COLUMNS) db.execSQL("ALTER TABLE `listening_rollups` ADD COLUMN `$column` INTEGER NOT NULL DEFAULT 0")
    }
}
