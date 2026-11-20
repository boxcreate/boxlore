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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a v43 database (episode playlists, as a test build installed it) to v44 through
 * [BoxLoreDatabase.builder]: each playlist keeps its name and gets the shows of its episodes, once each,
 * in the order they first appear; episodes without a show are dropped with `playlist_items`, and the
 * file matches the exported v44 schema.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomV44UpgradeTest {
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
        RoomSchemaFixture.createFromSchema(context, NAME, 43) { db ->
            RoomSchemaFixture.insert(
                db,
                "podcasts",
                LegacyRows.podcast(SHOW_A, subscribed = true) + mapOf("playbackSpeedOverride" to 1.5, "skipSilenceOverride" to 1),
            )
            RoomSchemaFixture.insert(db, "podcasts", LegacyRows.podcast(SHOW_B, subscribed = true))
            playlist(db, "pl-1", "Road trip")
            playlist(db, "pl-2", "Evenings")
            playlist(db, "pl-empty", "Empty")
            // pl-1: A, B, A again, an episode with no show, B again.
            item(db, "pl-1", "ep-1", SHOW_A, position = 0, addedAt = 10L)
            item(db, "pl-1", "ep-2", SHOW_B, position = 1, addedAt = 20L)
            item(db, "pl-1", "ep-3", SHOW_A, position = 2, addedAt = 30L)
            item(db, "pl-1", "ep-4", "", position = 3, addedAt = 40L)
            item(db, "pl-1", "ep-5", SHOW_B, position = 4, addedAt = 50L)
            // pl-2: stored out of position order; B comes first by position.
            item(db, "pl-2", "ep-6", SHOW_A, position = 2, addedAt = 60L)
            item(db, "pl-2", "ep-7", SHOW_B, position = 0, addedAt = 70L)
            item(db, "pl-2", "ep-8", SHOW_A, position = 1, addedAt = 80L)
        }
        return BoxLoreDatabase.builder(context, NAME).allowMainThreadQueries().build().also { database = it }
    }

    @Test
    fun episodePlaylistsBecomeTheirShowsInFirstAppearanceOrder() = runBlocking<Unit> {
        val db = upgrade()
        val dao = db.playlistDao()

        assertEquals(46, db.openHelper.writableDatabase.version)
        val playlists = dao.getAllPlaylists().associateBy { it.playlistId }
        assertEquals(setOf("pl-1", "pl-2", "pl-empty"), playlists.keys)
        assertEquals("Road trip", playlists.getValue("pl-1").name)
        assertEquals(1L to 2L, playlists.getValue("pl-1").createdAt to playlists.getValue("pl-1").updatedAt)
        assertTrue(playlists.values.all { it.episodeOrder == "newest" })

        val rows = dao.getAllShowRows().sortedWith(compareBy({ it.playlistId }, { it.position }))
        assertEquals(
            listOf(
                PlaylistShowEntity("pl-1", SHOW_A, 0, 10L),
                PlaylistShowEntity("pl-1", SHOW_B, 1, 20L),
                PlaylistShowEntity("pl-2", SHOW_B, 0, 70L),
                PlaylistShowEntity("pl-2", SHOW_A, 1, 80L),
            ),
            rows,
        )
        assertEquals(listOf(SHOW_A, SHOW_B), dao.getShows("pl-1").map { it.podcastId })
        assertTrue(dao.getShows("pl-empty").isEmpty())

        val show = db.podcastDao().getPodcast(SHOW_A)!!
        assertEquals(1.5f, show.playbackSpeedOverride)
        assertEquals(true, show.skipSilenceOverride)
        assertNull(db.podcastDao().getPodcast(SHOW_B)!!.playbackSpeedOverride)

        assertTrue(dao.appendShow("pl-empty", SHOW_A, addedAt = 90L))
        dao.deletePlaylist("pl-1")
        assertEquals(setOf("pl-2", "pl-empty"), dao.getAllShowRows().map { it.playlistId }.toSet())
    }

    @Test
    fun theUpgradedFileMatchesTheExportedV44Schema() {
        val upgraded = upgrade().openHelper.writableDatabase
        val sql = RoomSchemaFixture.schemaSql(upgraded)
        assertFalse("playlist_items" in sql)
        assertFalse(sql.keys.any { it.startsWith("index_playlist_items") })

        RoomSchemaFixture.createFromSchema(context, EXPORTED, 44) {}
        RoomSchemaFixture.open(context, EXPORTED, 44).use { helper ->
            val exported = helper.readableDatabase
            val exportedSql = RoomSchemaFixture.schemaSql(exported)
            for (name in listOf("playlist_shows", "index_playlist_shows_podcastId")) {
                assertEquals(name, exportedSql.getValue(name), sql.getValue(name))
            }
            assertEquals(RoomSchemaFixture.tables(exported), RoomSchemaFixture.tables(upgraded))
            for (table in listOf("playlist_shows", "podcasts")) {
                assertEquals(table, tableInfo(exported, table), tableInfo(upgraded, table))
            }
            // v46 adds the playback and icon columns ([RoomV46UpgradeTest]); the rest is v44's.
            assertEquals(tableInfo(exported, "playlists"), tableInfo(upgraded, "playlists").filterNot { it[0] in V46_PLAYLIST_COLUMNS }.toSet())
        }
        // Room opens at the latest version: v45 only adds archive columns ([RoomV45UpgradeTest]), v46 playlist columns.
        val identity = JsonParser.parseString(schemaJson(46).readText()).asJsonObject
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

    private fun playlist(db: SupportSQLiteDatabase, id: String, name: String) {
        RoomSchemaFixture.insert(db, "playlists", mapOf("playlistId" to id, "name" to name, "createdAt" to 1L, "updatedAt" to 2L))
    }

    private fun item(
        db: SupportSQLiteDatabase,
        playlistId: String,
        episodeId: String,
        podcastId: String,
        position: Int,
        addedAt: Long,
    ) {
        RoomSchemaFixture.insert(
            db,
            "playlist_items",
            mapOf(
                "playlistId" to playlistId,
                "position" to position,
                "addedAt" to addedAt,
                "episodeId" to episodeId,
                "title" to "Episode $episodeId",
                "podcastId" to podcastId,
                "podcastTitle" to "Show $podcastId",
                "podcastGenre" to "",
                "podcastArtist" to "",
                "audioUrl" to "https://cdn.example/$episodeId.mp3",
                "duration" to 60,
                "pubDate" to 0L,
            ),
        )
    }

    private companion object {
        const val NAME = "room-v44-upgrade.db"
        const val EXPORTED = "room-v44-exported.db"
        const val SHOW_A = "100"
        const val SHOW_B = "200"
        val V46_PLAYLIST_COLUMNS = setOf("mix", "perShowLimit", "iconKey")
    }
}
