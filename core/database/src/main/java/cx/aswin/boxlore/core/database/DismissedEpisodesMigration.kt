package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration 44→45: archived episodes ([ListeningHistoryEntity.dismissedAt]).
 *
 * Adds `dismissedAt` to `listening_history`, and `dismissedAt` and `dismissClearedAt` to
 * `episode_group_state`, each NOT NULL with the schema default 0, so every stored row reads "never
 * archived" without being rewritten. Only adds columns; runs inside Room's migration transaction and
 * uses nothing but the database. Never edit it once a phone may have run it; later changes go to v46.
 */
internal object DismissedEpisodesMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `listening_history` ADD COLUMN `dismissedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `episode_group_state` ADD COLUMN `dismissedAt` INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE `episode_group_state` ADD COLUMN `dismissClearedAt` INTEGER NOT NULL DEFAULT 0")
    }
}
