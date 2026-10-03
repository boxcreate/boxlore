package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoDownloadMigrationTest {
    @Test fun version37UpgradeValidatesSchemaAndProtectsExistingDownloads() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "auto-migration-test"
        context.deleteDatabase(name)
        val relative = "schemas/cx.aswin.boxlore.core.database.BoxLoreDatabase/37.json"
        val schemaFile = listOf(File(relative), File("core/database/$relative")).first { it.exists() }
        val schema = JsonParser.parseString(schemaFile.readText()).asJsonObject.getAsJsonObject("database")
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(name).callback(object : SupportSQLiteOpenHelper.Callback(37) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    for (entity in schema.getAsJsonArray("entities")) {
                        val table = entity.asJsonObject
                        val tableName = table.get("tableName").asString
                        db.execSQL(table.get("createSql").asString.replace("\${TABLE_NAME}", tableName))
                        for (index in table.getAsJsonArray("indices") ?: com.google.gson.JsonArray()) {
                            db.execSQL(index.asJsonObject.get("createSql").asString.replace("\${TABLE_NAME}", tableName))
                        }
                    }
                    for (query in schema.getAsJsonArray("setupQueries")) db.execSQL(query.asString)
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }).build()
        )
        helper.writableDatabase.apply {
            execSQL("INSERT INTO podcasts(podcastId,title,author,imageUrl,isSubscribed,subscribedAt,unsubscribedAt,isDirty,syncedAt,type,lastRefreshed,hasValue,isLocked,notificationsEnabled,autoDownloadEnabled,sourceType,rssRefreshCapability,lastRssSyncAt,rssCatalogStale,rssHasNewEpisodes) VALUES('show','Show','Author','',1,0,0,0,0,'episodic',0,0,0,0,1,'podcast_index','manual',0,0,0)")
            for ((id, smart) in listOf("manual-legacy" to 0, "smart-old" to 1)) {
                execSQL(
                    """INSERT INTO downloaded_episodes(episodeId,podcastId,episodeTitle,podcastName,durationMs,publishedDate,localFilePath,downloadId,downloadedAt,sizeBytes,status,isSmartDownloaded)
                    VALUES('$id','show','Episode','Show',1000,100,'CACHED',0,123,456,2,$smart)"""
                )
            }
            execSQL("INSERT INTO local_episodes(episodeId,podcastId,guid,title,description,audioUrl,duration,publishedDate) VALUES('-123','show','old-guid','Old','','https://cdn/old.mp3',60,100)")
        }
        helper.close()
        val database = Room.databaseBuilder(context, BoxLoreDatabase::class.java, name).allowMainThreadQueries()
            .addMigrations(object : Migration(37, 38) {
                override fun migrate(db: SupportSQLiteDatabase) = AutoDownloadMigration.migrate(db)
            }).build()
        try {
            assertEquals(DownloadedEpisodeEntity.ORIGIN_UNKNOWN, database.downloadedEpisodeDao().getDownload("manual-legacy")?.downloadOrigin)
            assertEquals(456L, database.downloadedEpisodeDao().getDownload("manual-legacy")?.sizeBytes)
            assertEquals(DownloadedEpisodeEntity.ORIGIN_SMART, database.downloadedEpisodeDao().getDownload("smart-old")?.downloadOrigin)
            assertNotNull(database.autoDownloadDao().getShow("show"))
            assertEquals(AutoDownloadReleaseEntity.HANDLED, database.autoDownloadDao().getRelease("-123")?.state)
            assertEquals("-123", database.localEpisodeCatalogDao().getByGuid("show", "old-guid")?.episodeId)
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }
}
