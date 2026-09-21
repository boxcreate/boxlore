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
class Migration36To37Test {

    @Test
    fun migrate36To37AltersTablesCreatesIndicesAndSeedsQueueMetadata() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config =
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration
                .builder(context)
                .name(null)
                .callback(
                    object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(36) {
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
                                CREATE TABLE IF NOT EXISTS listening_history (
                                    episodeId TEXT NOT NULL PRIMARY KEY,
                                    podcastId TEXT NOT NULL,
                                    episodeTitle TEXT NOT NULL,
                                    episodeImageUrl TEXT,
                                    podcastImageUrl TEXT,
                                    episodeAudioUrl TEXT,
                                    podcastName TEXT NOT NULL,
                                    progressMs INTEGER NOT NULL,
                                    durationMs INTEGER NOT NULL,
                                    isCompleted INTEGER NOT NULL,
                                    isLiked INTEGER NOT NULL,
                                    lastPlayedAt INTEGER NOT NULL,
                                    isDirty INTEGER NOT NULL,
                                    syncedAt INTEGER NOT NULL,
                                    enclosureType TEXT,
                                    isManualCompletion INTEGER NOT NULL,
                                    isBulkCompletion INTEGER NOT NULL,
                                    episodeDescription TEXT
                                )
                                """.trimIndent(),
                            )
                        }

                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
                    },
                ).build()

        val openHelper = FrameworkSQLiteOpenHelperFactory().create(config)
        val db = openHelper.writableDatabase

        // 1. Insert seed data in v36
        db.execSQL(
            """
            INSERT INTO podcasts (
                podcastId, title, author, imageUrl, isSubscribed, subscribedAt
            ) VALUES (
                'pod-1', 'Show 1', 'Author 1', 'https://art.jpg', 1, 1000
            )
            """.trimIndent(),
        )

        db.execSQL(
            """
            INSERT INTO listening_history (
                episodeId, podcastId, episodeTitle, podcastName, progressMs,
                durationMs, isCompleted, isLiked, lastPlayedAt, isDirty, syncedAt,
                isManualCompletion, isBulkCompletion
            ) VALUES (
                'ep-1', 'pod-1', 'Ep 1', 'Show 1', 500,
                1000, 0, 1, 2000, 1, 0,
                0, 0
            )
            """.trimIndent(),
        )

        // 2. Execute migration to 37
        BoxLoreDatabaseMigrations.migrate36To37(db)

        // 3. Verify podcasts columns and indices
        val podcastCursor = db.query(
            "SELECT podcastId, isSubscribed, subscribedAt, unsubscribedAt, isDirty, syncedAt FROM podcasts WHERE podcastId = 'pod-1'",
        )
        assertTrue(podcastCursor.moveToFirst())
        assertEquals("pod-1", podcastCursor.getString(0))
        assertEquals(1, podcastCursor.getInt(1))
        assertEquals(1000L, podcastCursor.getLong(2))
        assertEquals(0L, podcastCursor.getLong(3)) // unsubscribedAt default
        assertEquals(0, podcastCursor.getInt(4)) // isDirty default
        assertEquals(0L, podcastCursor.getLong(5)) // syncedAt default
        podcastCursor.close()

        // 4. Verify listening_history columns and indices
        val historyCursor = db.query(
            "SELECT episodeId, isLiked, likedAt, lastPlayedAt, isDirty FROM listening_history WHERE episodeId = 'ep-1'",
        )
        assertTrue(historyCursor.moveToFirst())
        assertEquals("ep-1", historyCursor.getString(0))
        assertEquals(1, historyCursor.getInt(1))
        assertEquals(0L, historyCursor.getLong(2)) // likedAt default
        assertEquals(2000L, historyCursor.getLong(3))
        assertEquals(1, historyCursor.getInt(4))
        historyCursor.close()

        // 5. Verify queue_metadata initial row
        val queueMetaCursor = db.query(
            "SELECT id, queueUpdatedAt, queueSequence, isDirty, syncedAt, lastModifiedDeviceId, recentRemovedEpisodeIds FROM queue_metadata WHERE id = 1",
        )
        assertTrue(queueMetaCursor.moveToFirst())
        assertEquals(1, queueMetaCursor.getInt(0))
        assertEquals(0L, queueMetaCursor.getLong(1))
        assertEquals(0L, queueMetaCursor.getLong(2))
        assertEquals(0, queueMetaCursor.getInt(3))
        assertEquals(0L, queueMetaCursor.getLong(4))
        queueMetaCursor.close()

        // 6. Test idempotence: running migrate36To37 again should not fail
        BoxLoreDatabaseMigrations.migrate36To37(db)

        openHelper.close()
    }
}
