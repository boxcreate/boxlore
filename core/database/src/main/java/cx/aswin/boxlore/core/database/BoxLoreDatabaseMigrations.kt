package cx.aswin.boxlore.core.database

import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Extracted migration SQL so unit tests can verify table creation without Room schema JSON.
 */
object BoxLoreDatabaseMigrations {
    fun migrate29To30(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS listening_sessions (
                sessionId TEXT NOT NULL PRIMARY KEY,
                episodeId TEXT NOT NULL,
                podcastId TEXT NOT NULL,
                startedAt INTEGER NOT NULL,
                endedAt INTEGER NOT NULL,
                consumedMs INTEGER NOT NULL,
                completed INTEGER NOT NULL,
                localDay INTEGER NOT NULL,
                timeBucket INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_sessions_endedAt ON listening_sessions(endedAt)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_sessions_episodeId ON listening_sessions(episodeId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_sessions_podcastId ON listening_sessions(podcastId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_sessions_localDay ON listening_sessions(localDay)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS listening_rollups (
                localDay INTEGER NOT NULL,
                episodeId TEXT NOT NULL,
                podcastId TEXT NOT NULL,
                consumedMs INTEGER NOT NULL,
                sessionCount INTEGER NOT NULL,
                completionCount INTEGER NOT NULL,
                lastListenedAt INTEGER NOT NULL,
                morningMs INTEGER NOT NULL,
                afternoonMs INTEGER NOT NULL,
                eveningMs INTEGER NOT NULL,
                nightMs INTEGER NOT NULL,
                PRIMARY KEY(localDay, episodeId)
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_rollups_podcastId ON listening_rollups(podcastId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_rollups_localDay ON listening_rollups(localDay)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_listening_rollups_episodeId ON listening_rollups(episodeId)",
        )
    }

    fun migrate30To31(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS episode_supplements (
                podcastId TEXT NOT NULL PRIMARY KEY,
                feedUrl TEXT NOT NULL,
                rssNamespaceId TEXT NOT NULL,
                feedEtag TEXT,
                feedLastModified TEXT,
                fetchedAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS episode_supplement_items (
                episodeId TEXT NOT NULL PRIMARY KEY CHECK(CAST(episodeId AS INTEGER) < 0),
                podcastId TEXT NOT NULL,
                guid TEXT,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                audioUrl TEXT NOT NULL,
                imageUrl TEXT,
                duration INTEGER NOT NULL,
                publishedDate INTEGER NOT NULL,
                chaptersUrl TEXT,
                transcriptUrl TEXT,
                transcripts TEXT,
                persons TEXT,
                seasonNumber INTEGER,
                episodeNumber INTEGER,
                episodeType TEXT,
                enclosureType TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_episode_supplement_items_podcastId " +
                "ON episode_supplement_items(podcastId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_episode_supplement_items_podcastId_publishedDate " +
                "ON episode_supplement_items(podcastId, publishedDate)",
        )
    }

    /**
     * Copy-only: create the first-class local catalog and copy Missing-episodes
     * extras **keeping episodeId**. Old supplement tables stay for dual-read.
     */
    fun migrate31To32(db: SupportSQLiteDatabase) {
        createLocalCatalogTables(db)
        copySupplementFeedsIntoLocalCatalog(db)
        copySupplementItemsIntoLocalCatalog(db)
    }

    private fun createLocalCatalogTables(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_episode_feeds (
                podcastId TEXT NOT NULL PRIMARY KEY,
                feedUrl TEXT NOT NULL,
                feedEtag TEXT,
                feedLastModified TEXT,
                fetchedAt INTEGER NOT NULL,
                itemCount INTEGER NOT NULL,
                feedOrder TEXT NOT NULL,
                ttlExpiresAt INTEGER,
                needsFullBackfill INTEGER NOT NULL,
                copiedExtrasCount INTEGER NOT NULL,
                ready INTEGER NOT NULL,
                feedUrlLookupAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS local_episodes (
                episodeId TEXT NOT NULL PRIMARY KEY,
                podcastId TEXT NOT NULL,
                guid TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL,
                audioUrl TEXT NOT NULL,
                imageUrl TEXT,
                duration INTEGER NOT NULL,
                publishedDate INTEGER NOT NULL,
                chaptersUrl TEXT,
                transcriptUrl TEXT,
                transcripts TEXT,
                persons TEXT,
                seasonNumber INTEGER,
                episodeNumber INTEGER,
                episodeType TEXT,
                enclosureType TEXT
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_local_episodes_podcastId_guid " +
                "ON local_episodes(podcastId, guid)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_local_episodes_podcastId_publishedDate_episodeId " +
                "ON local_episodes(podcastId, publishedDate, episodeId)",
        )
    }

    private fun copySupplementFeedsIntoLocalCatalog(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO local_episode_feeds (
                podcastId, feedUrl, feedEtag, feedLastModified, fetchedAt,
                itemCount, feedOrder, ttlExpiresAt, needsFullBackfill,
                copiedExtrasCount, ready, feedUrlLookupAt
            )
            SELECT
                s.podcastId,
                s.feedUrl,
                s.feedEtag,
                s.feedLastModified,
                s.fetchedAt,
                (
                    SELECT COUNT(*) FROM episode_supplement_items i
                    WHERE i.podcastId = s.podcastId
                ),
                'mixed',
                NULL,
                1,
                (
                    SELECT COUNT(*) FROM episode_supplement_items i
                    WHERE i.podcastId = s.podcastId
                ),
                0,
                0
            FROM episode_supplements s
            """.trimIndent(),
        )
    }

    private fun copySupplementItemsIntoLocalCatalog(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            INSERT INTO local_episodes (
                episodeId, podcastId, guid, title, description, audioUrl,
                imageUrl, duration, publishedDate, chaptersUrl, transcriptUrl,
                transcripts, persons, seasonNumber, episodeNumber, episodeType,
                enclosureType
            )
            SELECT
                episodeId,
                podcastId,
                $LOCAL_CATALOG_MIGRATION_KEY,
                title,
                description,
                audioUrl,
                imageUrl,
                duration,
                publishedDate,
                chaptersUrl,
                transcriptUrl,
                transcripts,
                persons,
                seasonNumber,
                episodeNumber,
                episodeType,
                enclosureType
            FROM episode_supplement_items
            """.trimIndent(),
        )
    }

    private const val LOCAL_CATALOG_MIGRATION_KEY = """
                CASE
                  WHEN TRIM(IFNULL(guid, '')) != '' AND (
                    SELECT COUNT(*) FROM episode_supplement_items x
                    WHERE x.podcastId = episode_supplement_items.podcastId
                      AND TRIM(IFNULL(x.guid, '')) = TRIM(episode_supplement_items.guid)
                  ) = 1 THEN TRIM(guid)
                  WHEN TRIM(IFNULL(audioUrl, '')) != '' AND (
                    SELECT COUNT(*) FROM episode_supplement_items x
                    WHERE x.podcastId = episode_supplement_items.podcastId
                      AND TRIM(IFNULL(x.audioUrl, '')) = TRIM(episode_supplement_items.audioUrl)
                  ) = 1 THEN TRIM(audioUrl)
                  ELSE 'id:' || episodeId
                END
    """

    fun migrate32To33(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE podcasts ADD COLUMN customGenre TEXT")
        db.execSQL("ALTER TABLE podcasts ADD COLUMN customGenreIcon TEXT")
    }

    fun migrate33To34(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS folders (
                folderId TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                icon TEXT,
                displaySize TEXT NOT NULL,
                linkedGenre TEXT,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS podcast_folder_cross_ref (
                podcastId TEXT NOT NULL,
                folderId TEXT NOT NULL,
                PRIMARY KEY(podcastId, folderId),
                FOREIGN KEY(folderId) REFERENCES folders(folderId) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_podcast_folder_cross_ref_folderId ON podcast_folder_cross_ref(folderId)",
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_podcast_folder_cross_ref_podcastId ON podcast_folder_cross_ref(podcastId)",
        )
    }

    fun migrate34To35(db: SupportSQLiteDatabase) {
        val cursor = db.query("PRAGMA table_info(folders)")
        var hasColumn = false
        cursor.use {
            val nameIndex = it.getColumnIndex("name")
            while (it.moveToNext()) {
                if (nameIndex != -1 && it.getString(nameIndex) == "showPodcastGrid") {
                    hasColumn = true
                    break
                }
            }
        }
        if (!hasColumn) {
            db.execSQL("ALTER TABLE folders ADD COLUMN showPodcastGrid INTEGER NOT NULL DEFAULT 0")
        }
    }

    fun migrate33To35(db: SupportSQLiteDatabase) {
        migrate33To34(db)
        migrate34To35(db)
    }
}
