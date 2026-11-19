package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room v44: playlists hold shows instead of episodes.
 *
 * - `playlists` gains `episodeOrder` (`newest` by default), and `playlist_shows`
 *   ([PlaylistShowEntity]) holds each playlist's shows. The SQL is Room's exported v44 schema.
 * - [migrateFrom43] converts the episode playlists of v43 ([ShowPlaybackAndPlaylistsMigration]): each
 *   playlist keeps the shows of its episodes, once each, in the order they first appear, and
 *   `playlist_items` is dropped. Episodes with no show ID are left out.
 * - [migrateFrom42] is the direct path from v42: the per-show speed and trim silence columns of v43,
 *   then the v44 playlist tables, so most phones never create `playlist_items`.
 *
 * Runs inside Room's migration transaction and uses nothing but the database. Never edit it once a
 * phone may have run it; later changes go to v45.
 */
internal object ShowPlaylistsMigration {
    fun migrateFrom42(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `playbackSpeedOverride` REAL")
        db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `skipSilenceOverride` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (`playlistId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `episodeOrder` TEXT NOT NULL DEFAULT 'newest', " +
                "PRIMARY KEY(`playlistId`))",
        )
        createPlaylistShows(db)
    }

    fun migrateFrom43(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `episodeOrder` TEXT NOT NULL DEFAULT 'newest'")
        createPlaylistShows(db)
        convertEpisodePlaylists(db)
        db.execSQL("DROP INDEX IF EXISTS `index_playlist_items_playlistId`")
        db.execSQL("DROP INDEX IF EXISTS `index_playlist_items_playlistId_episodeId`")
        db.execSQL("DROP TABLE IF EXISTS `playlist_items`")
    }

    private fun createPlaylistShows(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlist_shows` (`playlistId` TEXT NOT NULL, `podcastId` TEXT NOT NULL, " +
                "`position` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `podcastId`), " +
                "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE )",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_shows_podcastId` ON `playlist_shows` (`podcastId`)")
    }

    /** Each playlist's distinct shows in first-appearance order, numbered from 0, dated by their first episode. */
    private fun convertEpisodePlaylists(db: SupportSQLiteDatabase) {
        val shows = linkedMapOf<Pair<String, String>, Long>()
        db.query(
            "SELECT i.`playlistId`, i.`podcastId`, i.`addedAt` FROM `playlist_items` i " +
                "INNER JOIN `playlists` p ON p.`playlistId` = i.`playlistId` " +
                "WHERE i.`podcastId` IS NOT NULL AND TRIM(i.`podcastId`) != '' " +
                "ORDER BY i.`playlistId`, i.`position`, i.`id`",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val key = cursor.getString(0) to cursor.getString(1)
                if (key !in shows) shows[key] = cursor.getLong(2)
            }
        }
        val positions = mutableMapOf<String, Int>()
        for ((key, addedAt) in shows) {
            val position = positions.getOrDefault(key.first, 0)
            positions[key.first] = position + 1
            db.execSQL(
                "INSERT OR IGNORE INTO `playlist_shows` (`playlistId`, `podcastId`, `position`, `addedAt`) VALUES (?, ?, ?, ?)",
                arrayOf<Any>(key.first, key.second, position, addedAt),
            )
        }
    }
}
