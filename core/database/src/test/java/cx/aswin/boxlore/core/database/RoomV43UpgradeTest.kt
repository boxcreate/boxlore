package cx.aswin.boxlore.core.database

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Upgrades a v42 database through [BoxLoreDatabase.builder], which takes the direct 42→44 path: every
 * show keeps its stored values and reads no per-show speed or trim silence (the app settings apply),
 * and the show playlist tables work, delete their shows with the playlist, and match a fresh
 * install's. v43's episode playlists ([RoomV44UpgradeTest]) are never created.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomV43UpgradeTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private var database: BoxLoreDatabase? = null

    @Before
    fun clean() {
        context.deleteDatabase(NAME)
        context.deleteDatabase(FRESH)
    }

    @After
    fun close() {
        database?.close()
        context.deleteDatabase(NAME)
        context.deleteDatabase(FRESH)
    }

    private fun upgrade(): BoxLoreDatabase {
        RoomSchemaFixture.createFromSchema(context, NAME, 42) { db ->
            RoomSchemaFixture.insert(
                db,
                "podcasts",
                LegacyRows.podcast(SHOW, subscribed = true) + SHOW_SETTINGS,
            )
            RoomSchemaFixture.insert(db, "podcasts", LegacyRows.podcast(OTHER, subscribed = false))
            RoomSchemaFixture.insert(db, "queue_items", LegacyRows.queueItem("ep-1", SHOW, position = 0))
        }
        return BoxLoreDatabase.builder(context, NAME).allowMainThreadQueries().build().also { database = it }
    }

    @Test
    fun showsKeepTheirValuesAndReadNoPerShowSpeedOrTrimSilence() = runBlocking<Unit> {
        val db = upgrade()

        assertEquals(45, db.openHelper.writableDatabase.version)
        val show = db.podcastDao().getPodcast(SHOW)!!
        assertEquals("Show $SHOW", show.title)
        assertEquals("History", show.genre)
        assertTrue(show.isSubscribed && show.notificationsEnabled)
        assertEquals(30_000L to 0L, show.skipBeginningOverrideMs to show.skipEndingOverrideMs)
        assertEquals("Mine", show.customGenre)
        assertEquals(7L to 55L, show.epNs to show.feedUrlCheckedAt)
        assertNull(show.playbackSpeedOverride)
        assertNull(show.skipSilenceOverride)
        assertNull(db.podcastDao().getPodcast(OTHER)!!.playbackSpeedOverride)
        assertEquals(listOf("ep-1"), db.queueDao().getAllQueueItemsSync().map { it.episodeId })

        db.podcastDao().setPlaybackSpeedOverride(SHOW, 1.5f)
        db.podcastDao().setSkipSilenceOverride(SHOW, false)
        val changed = db.podcastDao().getPodcast(SHOW)!!
        assertEquals(1.5f, changed.playbackSpeedOverride)
        assertEquals(false, changed.skipSilenceOverride)
        assertTrue(changed.isDirty)
    }

    @Test
    fun playlistsWorkAndDeleteTheirShowsWithThem() = runBlocking<Unit> {
        val dao = upgrade().playlistDao()

        dao.insertPlaylist(PlaylistEntity(playlistId = "pl-1", name = "Comedy", createdAt = 1L, updatedAt = 1L))
        assertEquals("newest", dao.getPlaylist("pl-1")!!.episodeOrder)
        assertTrue(dao.appendShow("pl-1", SHOW, addedAt = 5L))
        assertFalse("a show is in a playlist once", dao.appendShow("pl-1", SHOW, addedAt = 6L))
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insertShow(PlaylistShowEntity("missing", OTHER, 0, 7L)) }
        }
        val summary = dao.observePlaylistSummaries().first().single()
        assertEquals(1, summary.showCount)
        assertEquals(listOf(true), dao.getShows("pl-1").map { it.isSubscribed })

        dao.deletePlaylist("pl-1")
        assertTrue(dao.getAllShowRows().isEmpty())
    }

    @Test
    fun theNewTablesMatchAFreshInstall() {
        val upgraded = RoomSchemaFixture.schemaSql(upgrade().openHelper.writableDatabase)
        val fresh = BoxLoreDatabase.builder(context, FRESH).allowMainThreadQueries().build()
        try {
            val freshSql = RoomSchemaFixture.schemaSql(fresh.openHelper.writableDatabase)
            for (name in NEW_OBJECTS) assertEquals(name, freshSql.getValue(name), upgraded.getValue(name))
            assertFalse("playlist_items" in upgraded)
            val columns = { db: BoxLoreDatabase -> RoomSchemaFixture.columns(db.openHelper.writableDatabase, "podcasts").sorted() }
            assertEquals(columns(fresh), columns(database!!))
        } finally {
            fresh.close()
        }
    }

    private companion object {
        const val NAME = "room-v43-upgrade.db"
        const val FRESH = "room-v43-fresh.db"
        const val SHOW = "100"
        const val OTHER = "200"
        val SHOW_SETTINGS = mapOf(
            "genre" to "History",
            "notificationsEnabled" to 1,
            "skipBeginningOverrideMs" to 30_000L,
            "skipEndingOverrideMs" to 0L,
            "customGenre" to "Mine",
            "epNs" to 7L,
            "feedUrlCheckedAt" to 55L,
        )
        val NEW_OBJECTS = listOf(
            "playlists",
            "playlist_shows",
            "index_playlist_shows_podcastId",
        )
    }
}
