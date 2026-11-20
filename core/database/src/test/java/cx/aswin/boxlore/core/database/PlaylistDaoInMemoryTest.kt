package cx.aswin.boxlore.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaylistDaoInMemoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var dao: PlaylistDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        dao = database.playlistDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun playlist(id: String, createdAt: Long = 1L) =
        dao.insertPlaylist(PlaylistEntity(playlistId = id, name = "List $id", createdAt = createdAt, updatedAt = createdAt))

    private suspend fun show(id: String, subscribed: Boolean = true, image: String = "https://img.example/$id.jpg") =
        database.podcastDao().upsert(PodcastEntity(podcastId = id, title = "Show $id", author = "Host", imageUrl = image, description = null, isSubscribed = subscribed))

    private fun local(id: String, show: String, date: Long, audio: String = "https://cdn.example/$id.mp3", type: String? = null) = LocalEpisodeEntity(
        episodeId = id,
        podcastId = show,
        guid = "key-$id",
        title = "Ep $id",
        description = "",
        audioUrl = audio,
        imageUrl = null,
        duration = 1_800,
        publishedDate = date,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = type,
        enclosureType = null,
    )

    private fun rss(id: String, show: String, date: Long) = RssEpisodeEntity(
        episodeId = id,
        podcastId = show,
        guid = "g$id",
        title = "Rss $id",
        description = "",
        audioUrl = "https://cdn.example/$id.mp3",
        imageUrl = null,
        duration = 1_800,
        publishedDate = date,
        chaptersUrl = null,
        transcriptUrl = null,
        transcripts = null,
        persons = null,
        seasonNumber = null,
        episodeNumber = null,
        episodeType = null,
        enclosureType = null,
    )

    @Test
    fun appendAddsAShowOnceAfterTheLastAndTouchesThePlaylist() = runTest {
        playlist("a")
        assertTrue(dao.appendShow("a", "s1", addedAt = 5L))
        assertTrue(dao.appendShow("a", "s2", addedAt = 6L))
        assertFalse("a show is in a playlist once", dao.appendShow("a", "s1", addedAt = 7L))
        assertFalse("no playlist, no row", dao.appendShow("missing", "s1", addedAt = 7L))

        assertEquals(listOf("s1" to 0, "s2" to 1), dao.getShows("a").map { it.podcastId to it.position })
        assertEquals(6L, dao.getPlaylist("a")!!.updatedAt)
        assertEquals(2, dao.observePlaylistSummaries().first().single().showCount)
        assertEquals("newest", dao.getPlaylist("a")!!.episodeOrder)
    }

    @Test
    fun showsCarryTheStoredShowAndItsFollowState() = runTest {
        show("s1")
        show("s2", subscribed = false)
        playlist("a")
        dao.appendShow("a", "s1", 1L)
        dao.appendShow("a", "s2", 2L)
        dao.appendShow("a", "gone", 3L)

        val rows = dao.observeShows("a").first()
        assertEquals(listOf(true, false, false), rows.map { it.isSubscribed })
        assertEquals(listOf("Show s1", "Show s2", null), rows.map { it.title })
        assertEquals(listOf("a"), dao.observePlaylistIdsForShow("s1").first())
    }

    @Test
    fun coversAreTheFirstFourShowsArtworkInOrder() = runTest {
        playlist("a")
        (1..5).forEach { show("s$it") }
        (1..5).forEach { dao.appendShow("a", "s$it", it.toLong()) }

        assertEquals((1..4).map { "https://img.example/s$it.jpg" }, dao.observePlaylistCovers().first().map { it.imageUrl })
    }

    @Test
    fun deletingAPlaylistDeletesItsShowsAndOrderIsStored() = runTest {
        playlist("a")
        dao.appendShow("a", "s1", 1L)
        assertEquals(1, dao.setEpisodeOrder("a", "oldest", 9L))
        assertEquals("oldest", dao.getPlaylist("a")!!.episodeOrder)

        dao.deletePlaylist("a")
        assertTrue(dao.getAllShowRows().isEmpty())
    }

    @Test
    fun reassignMovesRowsAndKeepsAShowOncePerPlaylist() = runTest {
        playlist("a")
        playlist("b", createdAt = 2L)
        dao.appendShow("a", "rss:old", 1L)
        dao.appendShow("b", "rss:old", 1L)
        dao.appendShow("b", "new", 2L)

        assertEquals(2, dao.reassignPodcast("rss:old", "new"))

        assertEquals(listOf("new"), dao.getShows("a").map { it.podcastId })
        assertEquals(listOf("new"), dao.getShows("b").map { it.podcastId })
    }

    @Test
    fun storedEpisodesReadBothTablesInEitherOrderAndSkipNoAudio() = runTest {
        show("pi")
        show("rss:feed")
        database.localEpisodeCatalogDao().upsertEpisodes(
            listOf(local("1", "pi", 10L), local("2", "pi", 30L), local("3", "pi", 40L, audio = ""), local("9", "other", 50L)),
        )
        database.rssEpisodeDao().upsertAll(listOf(rss("-5", "rss:feed", 20L)))

        val ids = listOf("pi", "rss:feed")
        assertEquals(listOf("2", "-5", "1"), dao.newestEpisodes(ids, Long.MAX_VALUE, "", limit = 10).map { it.episodeId })
        assertEquals(listOf("-5", "1"), dao.newestEpisodes(ids, 30L, "2", limit = 10).map { it.episodeId })
        assertEquals(listOf("1", "-5"), dao.oldestEpisodes(ids, Long.MIN_VALUE, "", limit = 2).map { it.episodeId })
        assertEquals("the next page starts after the last row", listOf("2"), dao.oldestEpisodes(ids, 20L, "-5", limit = 2).map { it.episodeId })
        assertEquals(setOf("1", "-5"), dao.storedEpisodes(listOf("1", "-5", "nope")).map { it.episodeId }.toSet())
    }

    @Test
    fun inProgressHistoryIsTheShowsUnfinishedPlays() = runTest {
        fun row(id: String, show: String, progress: Long, done: Boolean, at: Long) = ListeningHistoryEntity(
            episodeId = id,
            podcastId = show,
            episodeTitle = "Episode $id",
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = "https://cdn.example/$id.mp3",
            podcastName = "Show",
            progressMs = progress,
            durationMs = 10_000L,
            isCompleted = done,
            lastPlayedAt = at,
        )
        database.listeningHistoryDao().upsert(row("a", "pi", 1_000L, false, 1L))
        database.listeningHistoryDao().upsert(row("b", "pi", 2_000L, false, 2L))
        database.listeningHistoryDao().upsert(row("c", "pi", 3_000L, true, 3L))
        database.listeningHistoryDao().upsert(row("d", "pi", 0L, false, 4L))
        database.listeningHistoryDao().upsert(row("e", "other", 1_000L, false, 5L))

        assertEquals(listOf("b", "a"), dao.inProgressHistory(listOf("pi"), minProgressMs = 0L, limit = 10).map { it.episodeId })
        assertEquals("a short sample isn't midway through", listOf("b"), dao.inProgressHistory(listOf("pi"), minProgressMs = 1_500L, limit = 10).map { it.episodeId })
    }

    @Test
    fun releasedPagesLeaveOutEpisodesFinishedUnderTheirOwnIdAndBreakDateTiesById() = runTest {
        database.localEpisodeCatalogDao().upsertEpisodes(listOf(local("b", "pi", 10L), local("a", "pi", 10L), local("done", "pi", 20L), local("c", "pi", 30L)))
        database.listeningHistoryDao().upsert(
            ListeningHistoryEntity(
                episodeId = "done",
                podcastId = "pi",
                episodeTitle = "Done",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = null,
                podcastName = "Show",
                progressMs = 0L,
                durationMs = 0L,
                isCompleted = true,
                lastPlayedAt = 1L,
            ),
        )

        assertEquals(listOf("c", "b", "a"), dao.newestEpisodes(listOf("pi"), Long.MAX_VALUE, "", limit = 10).map { it.episodeId })
        assertEquals(listOf("a", "b", "c"), dao.oldestEpisodes(listOf("pi"), Long.MIN_VALUE, "", limit = 10).map { it.episodeId })
        assertEquals(listOf("a"), dao.newestEpisodes(listOf("pi"), 10L, "b", limit = 10).map { it.episodeId })
    }

    @Test
    fun deleteAllClearsEverything() = runTest {
        playlist("a")
        dao.appendShow("a", "s1", 1L)
        dao.deleteAll()
        assertTrue(dao.getAllPlaylists().isEmpty() && dao.getAllShowRows().isEmpty())
    }
}
