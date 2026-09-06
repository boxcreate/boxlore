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
class CustomGenreMigration32To33Test {

    @Test
    fun migrate32To33AddsCustomGenreAndIconColumns() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(null)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(32) {
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
                                    linkedPodcastIndexId TEXT
                                )
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                INSERT INTO podcasts (podcastId, title, author, imageUrl, description, isSubscribed, genre)
                                VALUES ('pod-1', 'Tech Talk', 'Jane', 'https://example.com/art.jpg', 'Desc', 1, 'Technology')
                                """.trimIndent(),
                            )
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                ).build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = openHelper.writableDatabase
        BoxLoreDatabaseMigrations.migrate32To33(db)

        val cursor = db.query("SELECT podcastId, genre, customGenre, customGenreIcon FROM podcasts WHERE podcastId = 'pod-1'")
        assertTrue(cursor.moveToFirst())
        assertEquals("pod-1", cursor.getString(0))
        assertEquals("Technology", cursor.getString(1))
        assertNull(cursor.getString(2))
        assertNull(cursor.getString(3))
        cursor.close()

        db.execSQL("UPDATE podcasts SET customGenre = 'Dev Talks', customGenreIcon = 'code' WHERE podcastId = 'pod-1'")
        val updatedCursor = db.query("SELECT customGenre, customGenreIcon FROM podcasts WHERE podcastId = 'pod-1'")
        assertTrue(updatedCursor.moveToFirst())
        assertEquals("Dev Talks", updatedCursor.getString(0))
        assertEquals("code", updatedCursor.getString(1))
        updatedCursor.close()
    }
}
