package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration 39→40: listening dates, timed-play details, daily summaries per phone and the
 * played shows.
 *
 * Runs once on every upgrading phone, inside Room's migration transaction, and may run from the
 * playback service's first database open, so it uses nothing but the database. Tables are created
 * with exactly the SQL Room exports (`schemas/…/40.json`); Room checks every table against the
 * entities after the migration and refuses to open on any difference.
 *
 * 1. `listening_history` gets `firstPlayedAt` and `finishedAt` with the schema default 0, so every
 *    existing row reads "before boxlore recorded it" ([ListeningDates]) without being rewritten.
 *    Rows not finished, and rows only marked played (by hand or in bulk), get `finishedAt` NULL (no
 *    finish by listening yet), so a later finish by listening is dated.
 * 2. `listening_sessions` gets `deviceId`, `wallMs`, `source`, `output` and `episodePublishedAt`,
 *    NULL on every existing session (no phone yet: the installation claims them, details not recorded).
 * 3. `listening_rollups` is rebuilt with the phone in its key (`deviceId`, '' until the installation
 *    claims it) and `wallMs` 0; every existing summary is copied unchanged.
 * 4. `played_shows` is created and filled from listening history: one row per show with a row
 *    played or finished by listening (not one only marked played or liked), with its latest name
 *    and art, the followed show's genre when stored, a first play before recording (0) and the
 *    latest play time, marked for the next cloud sync push.
 * 5. `listening_days_pushed` (what this phone knows the cloud holds of each phone's daily listening)
 *    is created empty, so the first push after the upgrade sends every day.
 *
 * Never edit this migration once a phone may have run it; later schema changes go to v41.
 */
internal object ListeningDataMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        addListeningDates(db)
        addSessionDetails(db)
        rebuildRollupsPerPhone(db)
        createPlayedShows(db)
        createPushedDays(db)
    }

    private fun createPushedDays(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `listening_days_pushed` (`deviceId` TEXT NOT NULL, `localDay` INTEGER NOT NULL, " +
                "`episodeId` TEXT NOT NULL, `podcastId` TEXT NOT NULL, `consumedMs` INTEGER NOT NULL, `wallMs` INTEGER NOT NULL, " +
                "`sessionCount` INTEGER NOT NULL, `completionCount` INTEGER NOT NULL, `morningMs` INTEGER NOT NULL, " +
                "`afternoonMs` INTEGER NOT NULL, `eveningMs` INTEGER NOT NULL, `nightMs` INTEGER NOT NULL, " +
                "`lastListenedAt` INTEGER NOT NULL, PRIMARY KEY(`deviceId`, `localDay`, `episodeId`))",
        )
    }

    private fun addListeningDates(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `listening_history` ADD COLUMN `firstPlayedAt` INTEGER DEFAULT 0")
        db.execSQL("ALTER TABLE `listening_history` ADD COLUMN `finishedAt` INTEGER DEFAULT 0")
        db.execSQL("UPDATE `listening_history` SET `finishedAt` = NULL WHERE `isCompleted` = 0 OR `isManualCompletion` = 1 OR `isBulkCompletion` = 1")
    }

    private fun addSessionDetails(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `listening_sessions` ADD COLUMN `deviceId` TEXT")
        db.execSQL("ALTER TABLE `listening_sessions` ADD COLUMN `wallMs` INTEGER")
        db.execSQL("ALTER TABLE `listening_sessions` ADD COLUMN `source` TEXT")
        db.execSQL("ALTER TABLE `listening_sessions` ADD COLUMN `output` TEXT")
        db.execSQL("ALTER TABLE `listening_sessions` ADD COLUMN `episodePublishedAt` INTEGER")
    }

    private fun rebuildRollupsPerPhone(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `listening_rollups_v40` (`localDay` INTEGER NOT NULL, `episodeId` TEXT NOT NULL, " +
                "`podcastId` TEXT NOT NULL, `consumedMs` INTEGER NOT NULL, `sessionCount` INTEGER NOT NULL, " +
                "`completionCount` INTEGER NOT NULL, `lastListenedAt` INTEGER NOT NULL, `morningMs` INTEGER NOT NULL, " +
                "`afternoonMs` INTEGER NOT NULL, `eveningMs` INTEGER NOT NULL, `nightMs` INTEGER NOT NULL, " +
                "`deviceId` TEXT NOT NULL DEFAULT '', `wallMs` INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(`deviceId`, `localDay`, `episodeId`))",
        )
        db.execSQL(
            "INSERT INTO `listening_rollups_v40` (`localDay`, `episodeId`, `podcastId`, `consumedMs`, `sessionCount`, " +
                "`completionCount`, `lastListenedAt`, `morningMs`, `afternoonMs`, `eveningMs`, `nightMs`, `deviceId`, `wallMs`) " +
                "SELECT `localDay`, `episodeId`, `podcastId`, `consumedMs`, `sessionCount`, `completionCount`, `lastListenedAt`, " +
                "`morningMs`, `afternoonMs`, `eveningMs`, `nightMs`, '', 0 FROM `listening_rollups`",
        )
        db.execSQL("DROP TABLE `listening_rollups`")
        db.execSQL("ALTER TABLE `listening_rollups_v40` RENAME TO `listening_rollups`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_rollups_podcastId` ON `listening_rollups` (`podcastId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_rollups_localDay` ON `listening_rollups` (`localDay`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_listening_rollups_episodeId` ON `listening_rollups` (`episodeId`)")
    }

    private fun createPlayedShows(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `played_shows` (`podcastId` TEXT NOT NULL, `podcastName` TEXT NOT NULL, " +
                "`imageUrl` TEXT, `genre` TEXT, `firstPlayedAt` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, " +
                "`isDirty` INTEGER NOT NULL, PRIMARY KEY(`podcastId`))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_played_shows_isDirty` ON `played_shows` (`isDirty`)")
        // SQLite takes the bare columns of a MAX() group from the row holding the maximum: the latest play.
        db.execSQL(
            """
            INSERT OR IGNORE INTO `played_shows` (`podcastId`, `podcastName`, `imageUrl`, `genre`, `firstPlayedAt`, `lastPlayedAt`, `isDirty`)
            SELECT h.`podcastId`, h.`podcastName`, COALESCE(h.`podcastImageUrl`, h.`episodeImageUrl`),
                   (SELECT CASE WHEN LOWER(TRIM(p.`genre`)) IN ('', 'podcast') THEN NULL ELSE TRIM(p.`genre`) END
                    FROM `podcasts` p WHERE p.`podcastId` = h.`podcastId`),
                   0, MAX(h.`lastPlayedAt`), 1
            FROM `listening_history` h
            WHERE h.`podcastId` != ''
              AND (h.`progressMs` > 0 OR (h.`isCompleted` = 1 AND h.`isManualCompletion` = 0 AND h.`isBulkCompletion` = 0))
            GROUP BY h.`podcastId`
            """.trimIndent(),
        )
    }
}
