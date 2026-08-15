package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LocalCatalogMigration31To32Test {
    @Test
    fun copiesExtrasKeepingEpisodeIdsAndLeavesOldTables() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(null)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(31) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            BoxLoreDatabaseMigrations.migrate30To31(db)
                            db.execSQL(
                                """
                                INSERT INTO episode_supplements
                                (podcastId, feedUrl, rssNamespaceId, feedEtag, feedLastModified, fetchedAt)
                                VALUES ('100', 'https://example.com/feed.xml', 'rss:ns', 'etag', 'lm', 9)
                                """.trimIndent(),
                            )
                            db.execSQL(
                                """
                                INSERT INTO episode_supplement_items
                                (episodeId, podcastId, guid, title, description, audioUrl,
                                 imageUrl, duration, publishedDate, chaptersUrl, transcriptUrl,
                                 transcripts, persons, seasonNumber, episodeNumber, episodeType,
                                 enclosureType)
                                VALUES (
                                  '-99', '100', 'guid-1', 'Extra', 'desc',
                                  'https://cdn.example/a.mp3', NULL, 60, 50,
                                  NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL
                                )
                                """.trimIndent(),
                            )
                        }

                        override fun onUpgrade(
                            db: SupportSQLiteDatabase,
                            oldVersion: Int,
                            newVersion: Int,
                        ) = Unit
                    },
                ).build()
        val openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = openHelper.writableDatabase
        BoxLoreDatabaseMigrations.migrate31To32(db)

        val feeds =
            db.query("SELECT podcastId, needsFullBackfill, ready, copiedExtrasCount FROM local_episode_feeds")
        assertTrue(feeds.moveToFirst())
        assertEquals("100", feeds.getString(0))
        assertEquals(1, feeds.getInt(1))
        assertEquals(0, feeds.getInt(2))
        assertEquals(1, feeds.getInt(3))
        feeds.close()

        val items = db.query("SELECT episodeId, guid FROM local_episodes")
        assertTrue(items.moveToFirst())
        assertEquals("-99", items.getString(0))
        assertEquals("guid-1", items.getString(1))
        items.close()

        val old =
            db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='episode_supplement_items'")
        assertTrue(old.moveToFirst())
        old.close()
        db.close()
    }
}
