package cx.aswin.boxlore.core.rss

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The record of shows Podcast Index renumbered ([RenumberedShows]): what counts, chains, loops and storage. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenumberedShowsTest {
    @Test
    fun onlyAnotherCanonicalNumericShowIdIsARenumbering() {
        assertTrue(RenumberedShowIds.isRenumbering("1258562", "7976624"))
        assertFalse(RenumberedShowIds.isRenumbering("1258562", "1258562"))
        assertFalse("rss: shows are never renumbered", RenumberedShowIds.isRenumbering("rss:123", "7976624"))
        assertFalse(RenumberedShowIds.isRenumbering("1258562", "rss:7976624"))
        assertFalse("non-canonical IDs are rejected", RenumberedShowIds.isRenumbering("01258562", "7976624"))
        assertFalse(RenumberedShowIds.isRenumbering("url:https%3A%2F%2Ffeeds.example", "7976624"))
    }

    @Test
    fun chainsResolveToTheLatestShowAndLoopsStop() {
        val shows = MemoryRenumberedShows()
        assertTrue(shows.record("1", "2"))
        assertTrue(shows.record("2", "3"))

        assertEquals("3", shows.currentId("1"))
        assertEquals("3", shows.currentId("2"))
        assertNull(shows.currentId("3"))
        assertEquals(setOf("1", "2"), shows.formerIds("3"))

        // Numbered back: the newest answer replaces the reverse entry, so nothing loops.
        assertTrue(shows.record("3", "2"))
        assertEquals("2", shows.currentId("3"))
        assertNull(shows.currentId("2"))
        assertEquals(setOf("1", "3"), shows.formerIds("2"))
    }

    @Test
    fun theStoredRecordSurvivesANewInstanceAndRejectsNonRenumberings() {
        val prefs =
            ApplicationProvider
                .getApplicationContext<Context>()
                .getSharedPreferences(SharedPreferencesRenumberedShows.FILE_NAME, Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val shows = SharedPreferencesRenumberedShows(prefs)

        assertTrue(shows.record("1258562", "7976624"))
        assertFalse(shows.record("7976624", "7976624"))
        assertFalse(shows.record("rss:1", "7976624"))

        val reopened = SharedPreferencesRenumberedShows(prefs)
        assertEquals(mapOf("1258562" to "7976624"), reopened.all())
        assertEquals("7976624", reopened.currentId("1258562"))
        assertEquals(setOf("1258562"), reopened.formerIds("7976624"))
    }
}
