package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlayedShowDaoTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PlayedShowDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.playedShowDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun aFirstPlayStartsTheShowAndLaterPlaysKeepItsFirstPlay() = runTest {
        dao.recordPlay("1", "Show", "https://img/1.jpg", "Comedy", playedAt = 100L)
        dao.recordPlay("1", "Show renamed", null, "Podcast", playedAt = 300L)
        dao.recordPlay("1", "", null, null, playedAt = 200L)

        assertEquals(PlayedShowEntity("1", "Show renamed", "https://img/1.jpg", "Comedy", 100L, 300L, isDirty = true), dao.getPlayedShow("1"))
    }

    @Test
    fun theGenrePlaceholderAndBlankShowsAreNotStored() = runTest {
        dao.recordPlay("2", "Show", null, " Podcast ", playedAt = 1L)
        dao.recordPlay("", "No show", null, "News", playedAt = 1L)

        assertNull(dao.getPlayedShow("2")!!.genre)
        assertEquals(1, dao.getAllPlayedShows().size)
    }

    @Test
    fun aPushClearsTheMarkOnlyOfTheRowAsItWasSent() = runTest {
        dao.recordPlay("1", "Show", null, null, playedAt = 100L)
        val sent = dao.getDirtyPlayedShows(limit = 10).single()
        dao.recordPlay("1", "Show", null, null, playedAt = 200L)

        // A play after the push left: the mark stays.
        dao.markPlayedShowSynced(sent)
        assertTrue(dao.getPlayedShow("1")!!.isDirty)

        // A restore after the push left brought an earlier first play, with the same last play: the mark stays.
        val second = dao.getDirtyPlayedShows(limit = 10).single()
        dao.mergePlayedShows(listOf(PlayedShowEntity("1", "Show", null, null, firstPlayedAt = 10L, lastPlayedAt = 200L)), fromCloud = false)
        dao.markPlayedShowSynced(second)
        assertTrue(dao.getPlayedShow("1")!!.isDirty)

        dao.markPlayedShowSynced(dao.getPlayedShow("1")!!)
        assertFalse(dao.getPlayedShow("1")!!.isDirty)
        assertTrue(dao.getDirtyPlayedShows(limit = 10).isEmpty())
    }

    @Test
    fun aResetMarksEveryRowForThePush() = runTest {
        dao.recordPlay("1", "Show", null, null, playedAt = 100L)
        dao.markPlayedShowSynced(dao.getPlayedShow("1")!!)

        dao.markAllPlayedShowsDirty()

        assertTrue(dao.getPlayedShow("1")!!.isDirty)
    }

    @Test
    fun cloudRowsMergeInAndOnlyLocalNewsIsMarkedForThePush() = runTest {
        dao.recordPlay("1", "Show", null, "Comedy", playedAt = 500L)
        dao.markPlayedShowSynced(dao.getPlayedShow("1")!!)
        dao.recordPlay("3", "Local", null, null, playedAt = 50L)
        dao.markPlayedShowSynced(dao.getPlayedShow("3")!!)

        dao.mergePlayedShows(
            listOf(
                // Another phone played it earlier and has art: merged, and the cloud lacks the later play.
                PlayedShowEntity("1", "Show", "https://img/1.jpg", null, firstPlayedAt = 0L, lastPlayedAt = 400L),
                // New to this phone: stored as the cloud has it, clean.
                PlayedShowEntity("2", "Other", null, "News", firstPlayedAt = 10L, lastPlayedAt = 20L),
                // The cloud is newer in every value: stored clean.
                PlayedShowEntity("3", "Local renamed", "https://img/3.jpg", "Science", firstPlayedAt = 0L, lastPlayedAt = 60L),
            ),
            fromCloud = true,
        )

        assertEquals(PlayedShowEntity("1", "Show", "https://img/1.jpg", "Comedy", 0L, 500L, isDirty = true), dao.getPlayedShow("1"))
        assertEquals(PlayedShowEntity("2", "Other", null, "News", 10L, 20L, isDirty = false), dao.getPlayedShow("2"))
        assertEquals(PlayedShowEntity("3", "Local renamed", "https://img/3.jpg", "Science", 0L, 60L, isDirty = false), dao.getPlayedShow("3"))
    }

    @Test
    fun restoredRowsAreMarkedForThePushAndUnchangedOnesAreLeftAlone() = runTest {
        dao.recordPlay("1", "Show", null, null, playedAt = 100L)
        dao.markPlayedShowSynced(dao.getPlayedShow("1")!!)

        dao.mergePlayedShows(
            listOf(
                PlayedShowEntity("1", "Show", null, null, firstPlayedAt = 100L, lastPlayedAt = 100L),
                PlayedShowEntity("2", "Restored", null, null, firstPlayedAt = 5L, lastPlayedAt = 6L),
            ),
            fromCloud = false,
        )

        assertFalse(dao.getPlayedShow("1")!!.isDirty)
        assertTrue(dao.getPlayedShow("2")!!.isDirty)
    }

    @Test
    fun clearingAllListeningClearsThePlayedShows() = runTest {
        dao.recordPlay("1", "Show", null, null, playedAt = 100L)

        database.listeningInsightsMaintenance().clearAllAnalytics()

        assertTrue(dao.getAllPlayedShows().isEmpty())
    }
}
