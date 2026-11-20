package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a v45 database to v46 through [BoxLoreDatabase.builder]: each playlist keeps its name,
 * dates and shows and plays as it did (an `oldest` order one show at a time, any other newest first,
 * both with no limit per show and no icon); new playlists take the v46 defaults, the settings can be
 * changed on upgraded rows, and the file matches the exported v46 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomV46UpgradeTest {
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
        RoomSchemaFixture.createFromSchema(context, NAME, 45) { db ->
            RoomSchemaFixture.insert(db, "podcasts", LegacyRows.podcast(SHOW_A, subscribed = true))
            playlist(db, "pl-newest", "Evenings", order = "newest")
            playlist(db, "pl-oldest", "From the start", order = "oldest")
            // Written by v44's default: no order given.
            playlist(db, "pl-default", "Default", order = null)
            RoomSchemaFixture.insert(db, "playlist_shows", mapOf("playlistId" to "pl-oldest", "podcastId" to SHOW_A, "position" to 0, "addedAt" to 10L))
            RoomSchemaFixture.insert(db, "playlist_shows", mapOf("playlistId" to "pl-oldest", "podcastId" to SHOW_B, "position" to 1, "addedAt" to 20L))
        }
        return BoxLoreDatabase.builder(context, NAME).allowMainThreadQueries().build().also { database = it }
    }

    @Test
    fun playlistsKeepHowTheyPlayedAndTheirShows() = runBlocking<Unit> {
        val db = upgrade()
        val dao = db.playlistDao()

        assertEquals(46, db.openHelper.writableDatabase.version)
        val playlists = dao.getAllPlaylists().associateBy { it.playlistId }
        val settings = playlists.mapValues { (_, it) -> Triple(it.mix, it.perShowLimit, it.iconKey) }
        assertEquals(
            mapOf(
                "pl-newest" to Triple("newest_first", 0, null),
                "pl-oldest" to Triple("one_show", 0, null),
                "pl-default" to Triple("newest_first", 0, null),
            ),
            settings,
        )
        val oldest = playlists.getValue("pl-oldest")
        assertEquals(listOf("From the start", "oldest"), listOf(oldest.name, oldest.episodeOrder))
        assertEquals(1L to 2L, oldest.createdAt to oldest.updatedAt)
        assertEquals(listOf(SHOW_A to 0, SHOW_B to 1), dao.getShows("pl-oldest").map { it.podcastId to it.position })
        assertEquals(mapOf("pl-oldest" to 2, "pl-newest" to 0, "pl-default" to 0), dao.observePlaylistSummaries().first().associate { it.playlist.playlistId to it.showCount })
    }

    @Test
    fun newPlaylistsTakeTheDefaultsAndUpgradedOnesChange() = runBlocking<Unit> {
        val dao = upgrade().playlistDao()

        dao.insertPlaylist(PlaylistEntity(playlistId = "pl-new", name = "New", createdAt = 3L, updatedAt = 3L))
        val fresh = dao.getPlaylist("pl-new")!!
        assertEquals(Triple("take_turns", 3, null), Triple(fresh.mix, fresh.perShowLimit, fresh.iconKey))
        // A row written without the new columns (SQL defaults) reads the same.
        upgraded().execSQL("INSERT INTO `playlists` (`playlistId`, `name`, `createdAt`, `updatedAt`) VALUES ('pl-sql', 'SQL', 4, 4)")
        val bySql = dao.getPlaylist("pl-sql")!!
        assertEquals(Triple("take_turns", 3, null), Triple(bySql.mix, bySql.perShowLimit, bySql.iconKey))

        assertEquals(1, dao.setPlayback("pl-oldest", "take_turns", 2, 9L))
        assertEquals(1, dao.setIcon("pl-oldest", "history", 10L))
        val changed = dao.getPlaylist("pl-oldest")!!
        assertEquals(Triple("take_turns", 2, "history"), Triple(changed.mix, changed.perShowLimit, changed.iconKey))
        assertEquals(10L, changed.updatedAt)
        assertEquals(1, dao.setIcon("pl-oldest", null, 11L))
        assertNull(dao.getPlaylist("pl-oldest")!!.iconKey)
        assertTrue(dao.appendShow("pl-newest", SHOW_A, addedAt = 12L))
    }

    @Test
    fun theUpgradedFileMatchesTheExportedV46Schema() {
        val upgraded = upgrade().openHelper.writableDatabase
        RoomSchemaFixture.createFromSchema(context, EXPORTED, 46) {}
        RoomSchemaFixture.open(context, EXPORTED, 46).use { helper ->
            val exported = helper.readableDatabase
            assertEquals(RoomSchemaFixture.tables(exported), RoomSchemaFixture.tables(upgraded))
            for (table in listOf("playlists", "playlist_shows")) {
                assertEquals(table, tableInfo(exported, table), tableInfo(upgraded, table))
            }
        }
        val identity = JsonParser.parseString(schemaJson(46).readText()).asJsonObject
            .getAsJsonObject("database").get("identityHash").asString
        assertEquals(identity, RoomSchemaFixture.string(upgraded, "SELECT identity_hash FROM room_master_table WHERE id = 42"))
    }

    private fun upgraded(): SupportSQLiteDatabase = database!!.openHelper.writableDatabase

    private fun playlist(db: SupportSQLiteDatabase, id: String, name: String, order: String?) {
        val values = mapOf("playlistId" to id, "name" to name, "createdAt" to 1L, "updatedAt" to 2L)
        RoomSchemaFixture.insert(db, "playlists", if (order == null) values else values + ("episodeOrder" to order))
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
        const val NAME = "room-v46-upgrade.db"
        const val EXPORTED = "room-v46-exported.db"
        const val SHOW_A = "100"
        const val SHOW_B = "200"
    }
}
