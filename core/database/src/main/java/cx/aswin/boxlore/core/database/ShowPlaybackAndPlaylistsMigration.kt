package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration 42→43: per-show playback speed and trim silence, and playlists.
 *
 * - `podcasts.playbackSpeedOverride` (REAL) and `podcasts.skipSilenceOverride` (INTEGER, a Boolean),
 *   both nullable with no default: every existing show reads null, which uses the app setting, so no
 *   row is rewritten.
 * - `playlists` ([PlaylistEntity]) and `playlist_items` ([PlaylistItemEntity]): new and empty. Items
 *   carry the queue's episode snapshot, are deleted with their playlist, and hold an episode once per
 *   playlist. The SQL is Room's exported v43 schema, so a migrated file matches a fresh install.
 *
 * Runs inside Room's migration transaction and uses nothing but the database. Never edit it once a
 * phone may have run it; later changes go to v44.
 */
internal object ShowPlaybackAndPlaylistsMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `playbackSpeedOverride` REAL")
        db.execSQL("ALTER TABLE `podcasts` ADD COLUMN `skipSilenceOverride` INTEGER")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `playlists` (`playlistId` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`playlistId`))",
        )
        db.execSQL(PLAYLIST_ITEMS_SQL)
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_playlist_items_playlistId` ON `playlist_items` (`playlistId`)")
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_playlist_items_playlistId_episodeId` " +
                "ON `playlist_items` (`playlistId`, `episodeId`)",
        )
    }

    private const val PLAYLIST_ITEMS_SQL =
        "CREATE TABLE IF NOT EXISTS `playlist_items` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
            "`playlistId` TEXT NOT NULL, `position` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, " +
            "`episodeId` TEXT NOT NULL, `title` TEXT NOT NULL, `podcastId` TEXT NOT NULL, `podcastTitle` TEXT NOT NULL, " +
            "`podcastGenre` TEXT NOT NULL, `podcastArtist` TEXT NOT NULL, `podcastImageUrl` TEXT, `imageUrl` TEXT, " +
            "`audioUrl` TEXT NOT NULL, `duration` INTEGER NOT NULL, `pubDate` INTEGER NOT NULL, `description` TEXT, " +
            "`chaptersUrl` TEXT, `transcriptUrl` TEXT, `personsJson` TEXT, `transcriptsJson` TEXT, `episodeType` TEXT, " +
            "`seasonNumber` INTEGER, `episodeNumber` INTEGER, `enclosureType` TEXT, " +
            "FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`playlistId`) ON UPDATE NO ACTION ON DELETE CASCADE )"
}
