package cx.aswin.boxlore.core.ranking

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.ranking.database.AdaptiveRankingDatabase
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
 * Regression: moving from a newer beta back to production found a newer ranking schema on disk and
 * crashed on open ("A migration from 2 to 1 was required but not found"). The ranking database now
 * drops and recreates its tables on a downgrade; the state is relearned.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdaptiveRankingDatabaseDowngradeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        resetInstance()
        context.deleteDatabase(DATABASE_NAME)
    }

    @After
    fun tearDown() {
        resetInstance()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun `a newer schema on disk is recreated instead of crashing`() {
        // A newer build's database: version 2, with a table this version does not know.
        val path = context.getDatabasePath(DATABASE_NAME).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(path, null).use { newer ->
            newer.execSQL("CREATE TABLE future_table (id INTEGER PRIMARY KEY)")
            newer.version = 2
        }

        val database = AdaptiveRankingDatabase.getDatabase(context)
        val sqlite = database.openHelper.writableDatabase
        val tables = sqlite.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.getString(0)) }
        }

        assertEquals(1, sqlite.version)
        assertFalse("the newer build's table was dropped", "future_table" in tables)
        assertTrue(tables.containsAll(listOf("adaptive_models", "preference_facets", "ranking_exposures")))
    }

    /** The database is a process singleton; each test opens it fresh. */
    private fun resetInstance() {
        val field = AdaptiveRankingDatabase::class.java.getDeclaredField("instance").apply { isAccessible = true }
        (field.get(null) as? AdaptiveRankingDatabase)?.close()
        field.set(null, null)
    }

    private companion object {
        const val DATABASE_NAME = "adaptive_ranking_database"
    }
}
