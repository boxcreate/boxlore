package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration 45→46: each playlist's mix, episodes per show and icon ([PlaylistEntity]).
 *
 * Adds `mix` (`take_turns` by default), `perShowLimit` (3 by default, 0 means no limit) and the
 * nullable `iconKey` to `playlists`. Playlists made before get no limit and the nearest mix: any
 * `newest` order becomes `newest_first`, which plays as it did. An `oldest` order becomes `one_show`,
 * which changes its order: no mix merges every show oldest first by release date as `oldest` did,
 * so these play one show at a time from its oldest unheard episode. `episodeOrder` stays as a column
 * nothing reads. Runs inside Room's migration transaction and uses nothing but the database. Never
 * edit its SQL once a phone may have run it; later changes take a new version.
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
