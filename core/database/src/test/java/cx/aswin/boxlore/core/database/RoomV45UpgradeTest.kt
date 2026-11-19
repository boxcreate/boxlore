package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a v44 database to v45 through [BoxLoreDatabase.builder]: every history row and shared
 * episode setting keeps its listening, like and completion and reads "never archived", archiving then
 * works on the upgraded rows, and the file matches the exported v45 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomV45UpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: BoxLoreDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(NAME)
        context.deleteDatabase(EXPORTED)
    }

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(NAME)
        context.deleteDatabase(EXPORTED)
    }

    private fun upgrade(): BoxLoreDatabase {
        RoomSchemaFixture.createFromSchema(context, NAME, 44) { db ->
            RoomSchemaFixture.insert(
                db,
                "listening_history",
                LegacyRows.history("ep-1", SHOW, lastPlayedAt = 5_000L, likedAt = 4_000L) + mapOf("isCompleted" to 1, "firstPlayedAt" to 3_000L),
            )
            RoomSchemaFixture.insert(db, "listening_history", LegacyRows.history("ep-2", SHOW, lastPlayedAt = 6_000L))
            RoomSchemaFixture.insert(
                db,
                "episode_group_state",
                mapOf(
                    "podcastId" to SHOW,
                    "catalogKey" to "key-1",
                    "likedAt" to 4_000L,
                    "likeClearedAt" to 0L,
                    "completedAt" to 5_000L,
                    "completeClearedAt" to 0L,
                    "version" to 2L,
                ),
            )
        }
        return BoxLoreDatabase.builder(context, NAME).allowMainThreadQueries().build().also { database = it }
    }

    @Test
    fun rowsKeepTheirStateAndReadNeverArchived() = runBlocking<Unit> {
        val db = upgrade()
        val dao = db.listeningHistoryDao()

        assertEquals(45, db.openHelper.writableDatabase.version)
        val first = dao.getHistoryItem("ep-1")!!
        assertTrue(first.isCompleted && first.isLiked)
        assertEquals(1_234L to 3_000L, first.progressMs to first.firstPlayedAt)
        assertEquals(0L, first.dismissedAt)
        assertFalse(first.isDismissed)
        val state = db.episodeIdentityDao().getGroupState(SHOW, "key-1")!!
        assertEquals(listOf(4_000L, 5_000L, 2L, 0L, 0L), listOf(state.likedAt, state.completedAt, state.version, state.dismissedAt, state.dismissClearedAt))
        assertTrue(dao.getDismissedEpisodeIds().isEmpty())

        // Archiving works on an upgraded row and leaves its listening as it was.
        assertEquals(1, dao.setDismissedForIds(listOf("ep-2"), 7_000L))
        assertEquals(listOf("ep-2"), dao.getDismissedEpisodeIds())
        val archived = dao.getHistoryItem("ep-2")!!
        assertEquals(1_234L to 6_000L, archived.progressMs to archived.lastPlayedAt)
        assertFalse(archived.isCompleted)
        // The push batch carries the archive.
        assertEquals(7_000L, dao.getDirtyListeningHistoryBatch(10).single { it.episodeId == "ep-2" }.dismissedAt)
    }

    @Test
    fun theUpgradedFileMatchesTheExportedV45Schema() {
        val upgraded = upgrade().openHelper.writableDatabase
        RoomSchemaFixture.createFromSchema(context, EXPORTED, 45) {}
        RoomSchemaFixture.open(context, EXPORTED, 45).use { helper ->
            val exported = helper.readableDatabase
            assertEquals(RoomSchemaFixture.tables(exported), RoomSchemaFixture.tables(upgraded))
            for (table in listOf("listening_history", "episode_group_state")) {
                assertEquals(table, tableInfo(exported, table), tableInfo(upgraded, table))
            }
        }
        val identity = JsonParser.parseString(schemaJson(45).readText()).asJsonObject
            .getAsJsonObject("database").get("identityHash").asString
        assertEquals(identity, RoomSchemaFixture.string(upgraded, "SELECT identity_hash FROM room_master_table WHERE id = 42"))
    }

    private fun tableInfo(db: SupportSQLiteDatabase, table: String): Set<List<Any?>> =
        db.query("PRAGMA table_info(`$table`)").use { cursor ->
            buildSet {
                while (cursor.moveToNext()) {
                    add(listOf(cursor.getString(1), cursor.getString(2), cursor.getInt(3), cursor.getString(4), cursor.getInt(5)))
                }
            }
        }

    private fun schemaJson(version: Int): File {
        val relative = "schemas/cx.aswin.boxlore.core.database.BoxLoreDatabase/$version.json"
        return listOf(File(relative), File("core/database/$relative")).first { it.exists() }
    }

    private companion object {
        const val NAME = "room-v45-upgrade.db"
        const val EXPORTED = "room-v45-exported.db"
        const val SHOW = "100"
    }
}
