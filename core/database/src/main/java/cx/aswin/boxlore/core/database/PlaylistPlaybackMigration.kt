package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration 45→46: each playlist's mix, episodes per show and icon ([PlaylistEntity]).
 *
 * Adds `mix` (`take_turns` by default), `perShowLimit` (3 by default, 0 means no limit) and the
 * nullable `iconKey` to `playlists`. Playlists made before keep playing as they did: an `oldest`
 * order becomes `one_show` and any other `newest_first`, both with no limit. `episodeOrder` stays
 * as a column nothing reads. Runs inside Room's migration transaction and uses nothing but the
 * database. Never edit it once a phone may have run it; later changes go to v47.
 */
internal object PlaylistPlaybackMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `mix` TEXT NOT NULL DEFAULT 'take_turns'")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `perShowLimit` INTEGER NOT NULL DEFAULT 3")
        db.execSQL("ALTER TABLE `playlists` ADD COLUMN `iconKey` TEXT")
        db.execSQL(
            "UPDATE `playlists` SET `perShowLimit` = 0, " +
                "`mix` = CASE WHEN `episodeOrder` = 'oldest' THEN 'one_show' ELSE 'newest_first' END",
        )
    }
}
