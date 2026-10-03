package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

internal object AutoDownloadMigration {
    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE downloaded_episodes ADD COLUMN downloadOrigin TEXT NOT NULL DEFAULT 'unknown'")
        db.execSQL("UPDATE downloaded_episodes SET downloadOrigin = 'smart' WHERE isSmartDownloaded = 1")
        db.execSQL("CREATE TABLE IF NOT EXISTS auto_download_shows (podcastId TEXT NOT NULL PRIMARY KEY, enabledAt INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS auto_download_releases (episodeId TEXT NOT NULL PRIMARY KEY, podcastId TEXT NOT NULL, state TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_auto_download_releases_podcastId_state ON auto_download_releases(podcastId, state)")
        db.execSQL(
            """
            INSERT INTO auto_download_shows(podcastId, enabledAt)
            SELECT podcastId, CAST(strftime('%s', 'now') AS INTEGER) FROM podcasts
            WHERE isSubscribed = 1 AND autoDownloadEnabled = 1 AND sourceType != 'rss'
        """.trimIndent()
        )
        db.execSQL(
            """
            INSERT OR IGNORE INTO auto_download_releases(episodeId, podcastId, state)
            SELECT episodeId, podcastId, 'handled' FROM local_episodes
            WHERE podcastId IN (SELECT podcastId FROM auto_download_shows)
        """.trimIndent()
        )
    }
}
