package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CustomFolderMigration33To34Test {

    @Test
    fun migrate33To34CreatesFoldersAndCrossRefTablesWithCascade() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(null)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(33) {
                        override fun onConfigure(db: SupportSQLiteDatabase) {
                            super.onConfigure(db)
                            db.setForeignKeyConstraintsEnabled(true)
                        }

                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL(
                                """
                                CREATE TABLE IF NOT EXISTS podcasts (
                                    podcastId TEXT NOT NULL PRIMARY KEY,
                                    title TEXT NOT NULL,
                                    author TEXT NOT NULL,
                                    imageUrl TEXT NOT NULL,
                                    description TEXT,
                                    isSubscribed INTEGER NOT NULL DEFAULT 0,
                                    subscribedAt INTEGER NOT NULL DEFAULT 0,
                                    genre TEXT,
                                    type TEXT NOT NULL DEFAULT 'episodic',
                                    lastRefreshed INTEGER NOT NULL DEFAULT 0,
                                    latestEpisode TEXT,
                                    podcastGuid TEXT,
                                    fundingUrl TEXT,
                                    fundingMessage TEXT,
                                    medium TEXT,
                                    hasValue INTEGER NOT NULL DEFAULT 0,
                                    updateFrequency TEXT,
                                    location TEXT,
                                    license TEXT,
                                    isLocked INTEGER NOT NULL DEFAULT 0,
                                    preferredSort TEXT,
                                    notificationsEnabled INTEGER NOT NULL DEFAULT 0,
                                    autoDownloadEnabled INTEGER NOT NULL DEFAULT 0,
                                    skipBeginningOverrideMs INTEGER,
                                    skipEndingOverrideMs INTEGER,
                                    sourceType TEXT NOT NULL DEFAULT 'podcast_index',
                                    feedUrl TEXT,
                                    feedEtag TEXT,
                                    feedLastModified TEXT,
                                    feedDeclaredUpdatedAt INTEGER,
                                    rssRefreshCapability TEXT NOT NULL DEFAULT 'manual',
                                    lastRssSyncAt INTEGER NOT NULL DEFAULT 0,
                                    rssCatalogStale INTEGER NOT NULL DEFAULT 0,
                                    rssHasNewEpisodes INTEGER NOT NULL DEFAULT 0,
                                    linkedPodcastIndexId TEXT,
                                    customGenre TEXT,
                                    customGenreIcon TEXT
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                INSERT INTO podcasts (podcastId, title, author, imageUrl, description, isSubscribed, genre)
                                VALUES ('pod-101', 'Kotlin Devs', 'JetBrains', 'https://example.com/art.jpg', 'Desc', 1, 'Technology')
                                """.trimIndent(),
                            )
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                ).build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = openHelper.writableDatabase
        db.setForeignKeyConstraintsEnabled(true)

        BoxLoreDatabaseMigrations.migrate33To34(db)

        // 1. Insert iconless folder with COMPACT size (default showPodcastGrid = 1)
        db.execSQL(
            """
            INSERT INTO folders (folderId, name, icon, displaySize, linkedGenre, showPodcastGrid, createdAt)
            VALUES ('folder-1', 'Daily Tech', NULL, 'COMPACT', 'Technology', 1, 1000)
            """.trimIndent(),
        )

        // 2. Insert folder with icon and FEATURED size (showPodcastGrid = 0)
        db.execSQL(
            """
            INSERT INTO folders (folderId, name, icon, displaySize, linkedGenre, showPodcastGrid, createdAt)
            VALUES ('folder-2', 'Favorites', 'star', 'FEATURED', NULL, 0, 2000)
            """.trimIndent(),
        )

        // Verify folder rows
        val folderCursor = db.query("SELECT folderId, name, icon, displaySize, linkedGenre, showPodcastGrid FROM folders ORDER BY createdAt ASC")
        assertTrue(folderCursor.moveToFirst())
        assertEquals("folder-1", folderCursor.getString(0))
        assertEquals("Daily Tech", folderCursor.getString(1))
        assertNull(folderCursor.getString(2))
        assertEquals("COMPACT", folderCursor.getString(3))
        assertEquals("Technology", folderCursor.getString(4))
        assertEquals(1, folderCursor.getInt(5))

        assertTrue(folderCursor.moveToNext())
        assertEquals("folder-2", folderCursor.getString(0))
        assertEquals("Favorites", folderCursor.getString(1))
        assertEquals("star", folderCursor.getString(2))
        assertEquals("FEATURED", folderCursor.getString(3))
        assertNull(folderCursor.getString(4))
        assertEquals(0, folderCursor.getInt(5))
        folderCursor.close()

        // 3. Insert cross-ref
        db.execSQL(
            """
            INSERT INTO podcast_folder_cross_ref (podcastId, folderId)
            VALUES ('pod-101', 'folder-1')
            """.trimIndent(),
        )

        val crossRefCursor = db.query("SELECT podcastId, folderId FROM podcast_folder_cross_ref WHERE folderId = 'folder-1'")
        assertTrue(crossRefCursor.moveToFirst())
        assertEquals("pod-101", crossRefCursor.getString(0))
        assertEquals("folder-1", crossRefCursor.getString(1))
        crossRefCursor.close()

        // 4. Test CASCADE delete: deleting folder-1 removes its cross-ref, while keeping podcast intact
        db.execSQL("DELETE FROM folders WHERE folderId = 'folder-1'")

        val emptyRefCursor = db.query("SELECT COUNT(*) FROM podcast_folder_cross_ref WHERE folderId = 'folder-1'")
        assertTrue(emptyRefCursor.moveToFirst())
        assertEquals(0, emptyRefCursor.getInt(0))
        emptyRefCursor.close()

        // Verify podcast in podcasts table is NOT deleted
        val podcastCheck = db.query("SELECT podcastId, title, isSubscribed FROM podcasts WHERE podcastId = 'pod-101'")
        assertTrue(podcastCheck.moveToFirst())
        assertEquals("pod-101", podcastCheck.getString(0))
        assertEquals("Kotlin Devs", podcastCheck.getString(1))
        assertEquals(1, podcastCheck.getInt(2))
        podcastCheck.close()
    }
}
