package cx.aswin.boxlore.core.catalog

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.LocalEpisodeEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.database.RssEpisodeEntity
import cx.aswin.boxlore.core.database.episodeListenerStore
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
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
class RoomPlaylistRepositoryTest {
    private lateinit var database: BoxLoreDatabase
    private lateinit var repository: RoomPlaylistRepository
    private val hidden = MutableStateFlow(emptySet<String>())
    private var clock = 1_000L
    private var ids = 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        val listeners = database.episodeListenerStore()
        repository = RoomPlaylistRepository(
            dao = database.playlistDao(),
            projectedHistory = listeners::projectedHistoryOf,
            withAliases = listeners::withAllAliases,
            hiddenShowIds = hidden,
            now = { clock++ },
            newId = { "list-${++ids}" },
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun follow(id: String, subscribed: Boolean = true) =
        database.podcastDao().upsert(PodcastEntity(podcastId = id, title = "Show $id", author = "Host $id", imageUrl = "https://img.example/$id.jpg", description = null, genre = "Comedy", isSubscribed = subscribed))

    private suspend fun episodes(show: String, vararg dates: Long, type: String? = null, duration: Int = 1_800) =
        database.localEpisodeCatalogDao().upsertEpisodes(
            dates.map { date ->
                LocalEpisodeEntity(
                    episodeId = "$show-$date",
                    podcastId = show,
                    guid = "key-$show-$date",
                    title = "Episode $date",
                    description = "",
                    audioUrl = "https://cdn.example/$show-$date.mp3",
                    imageUrl = null,
                    duration = duration,
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
            },
        )

    private suspend fun history(id: String, show: String, progressMs: Long, completed: Boolean, playedAt: Long = 1L) =
        database.listeningHistoryDao().upsert(
            ListeningHistoryEntity(
                episodeId = id,
                podcastId = show,
                episodeTitle = "Played $id",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://cdn.example/$id.mp3",
                podcastName = "Show $show",
                progressMs = progressMs,
                durationMs = 60_000L,
                isCompleted = completed,
                lastPlayedAt = playedAt,
            ),
        )

    @Test
    fun `a new playlist holds its shows in order, once each`() = runTest {
        follow("a")
        follow("b")
        val created = repository.createPlaylist("  Comedy  ", listOf("b", "a", "b", ""))

        assertEquals("Comedy", created.name)
        assertEquals(2, created.showCount)
        assertEquals(PlaylistOrder.NEWEST, created.order)
        assertEquals(listOf("b", "a"), repository.shows(created.id).first().map { it.podcastId })
        assertEquals(listOf("https://img.example/b.jpg", "https://img.example/a.jpg"), repository.playlist(created.id).first()!!.coverUrls)
        assertEquals(setOf(created.id), repository.playlistIdsForShow("a").first())
    }

    @Test
    fun `add skips shows it holds, remove then undo puts a show back in its place`() = runTest {
        val id = repository.createPlaylist("History", listOf("a", "b", "c")).id
        assertEquals(1, repository.addShows(id, listOf("a", "d")))

        val removed = repository.removeShow(id, "b")!!
        assertEquals(listOf("a", "c", "d"), repository.shows(id).first().map { it.podcastId })
        assertNull("removing it twice does nothing", repository.removeShow(id, "b"))

        assertTrue(repository.restoreShow(removed))
        assertEquals(listOf("a", "b", "c", "d"), repository.shows(id).first().map { it.podcastId })
        assertFalse("it's in the playlist again", repository.restoreShow(removed))
    }

    @Test
    fun `undo does nothing once the playlist is gone`() = runTest {
        val id = repository.createPlaylist("Gone", listOf("a")).id
        val removed = repository.removeShow(id, "a")!!
        repository.deletePlaylist(id)
        assertFalse(repository.restoreShow(removed))
        assertTrue(repository.getPlaylists().isEmpty())
    }

    @Test
    fun `rename, order and a blank name`() = runTest {
        val id = repository.createPlaylist("   ").id
        assertEquals(RoomPlaylistRepository.DEFAULT_NAME, repository.playlist(id).first()!!.name)
        assertFalse(repository.renamePlaylist(id, "  "))
        assertTrue(repository.renamePlaylist(id, "Mornings"))
        assertTrue(repository.setOrder(id, PlaylistOrder.OLDEST))
        val playlist = repository.playlist(id).first()!!
        assertEquals("Mornings" to PlaylistOrder.OLDEST, playlist.name to playlist.order)
        assertFalse(repository.setOrder("missing", PlaylistOrder.OLDEST))
    }

    @Test
    fun `shows that aren't followed or are hidden are marked`() = runTest {
        follow("a")
        follow("b", subscribed = false)
        val id = repository.createPlaylist("Mix", listOf("a", "b")).id
        hidden.value = setOf("a")

        val shows = repository.shows(id).first()
        assertEquals(listOf(true, false), shows.map { it.isFollowed })
        assertEquals(listOf(true, false), shows.map { it.isHidden })
        assertTrue(shows.none { it.isPlayable })
    }

    @Test
    fun `next episodes play newest first across shows, skipping finished, trailers and no audio`() = runTest {
        follow("a")
        follow("b")
        episodes("a", 10L, 30L, 50L)
        episodes("b", 20L, 40L)
        episodes("b", 45L, type = "trailer", duration = 120)
        history("a-50", "a", progressMs = 60_000L, completed = true)
        val id = repository.createPlaylist("Mix", listOf("a", "b")).id

        assertEquals(listOf("b-40", "a-30", "b-20", "a-10"), repository.nextEpisodes(id, limit = 10).map { it.id })
        val first = repository.nextEpisodes(id, limit = 1).single()
        assertEquals("Show b" to "Comedy", first.podcastTitle to first.podcastGenre)
        assertEquals("https://img.example/b.jpg", first.podcastImageUrl)
    }

    @Test
    fun `archived episodes are never queued nor coming up, started or not`() = runTest {
        follow("a")
        episodes("a", 10L, 30L, 50L)
        history("a-30", "a", progressMs = 60_000L, completed = false, playedAt = 9L)
        database.listeningHistoryDao().setDismissedForIds(listOf("a-30"), 100L)
        // A trailer-like episode never played: archiving gave it a row with only the decision.
        history("a-50", "a", progressMs = 0L, completed = false, playedAt = 0L)
        database.listeningHistoryDao().setDismissedForIds(listOf("a-50"), 101L)
        val id = repository.createPlaylist("Mix", listOf("a")).id

        assertEquals(listOf("a-10"), repository.nextEpisodes(id, limit = 10).map { it.id })

        database.listeningHistoryDao().setDismissedForIds(listOf("a-50"), -102L)
        assertEquals(listOf("a-50", "a-10"), repository.nextEpisodes(id, limit = 10).map { it.id })
    }

    @Test
    fun `oldest first, with an episode midway through first and the batch limit`() = runTest {
        follow("a")
        follow("b")
        episodes("a", 10L, 30L, 50L)
        episodes("b", 20L, 40L)
        history("b-40", "b", progressMs = 60_000L, completed = false)
        val id = repository.createPlaylist("Mix", listOf("a", "b")).id
        repository.setOrder(id, PlaylistOrder.OLDEST)

        assertEquals(listOf("b-40", "a-10", "b-20", "a-30", "a-50"), repository.nextEpisodes(id, limit = 10).map { it.id })
        assertEquals(listOf("b-40", "a-10"), repository.nextEpisodes(id, limit = 2).map { it.id })
    }

    @Test
    fun `the episode last played comes first, and a few seconds' sample doesn't jump ahead`() = runTest {
        follow("a")
        follow("b")
        episodes("a", 10L, 30L, 50L)
        episodes("b", 20L, 40L)
        history("a-50", "a", progressMs = 3_000L, completed = false, playedAt = 10L)
        history("b-40", "b", progressMs = 60_000L, completed = false, playedAt = 8L)
        history("a-10", "a", progressMs = 1_200_000L, completed = false, playedAt = 9L)
        val id = repository.createPlaylist("Mix", listOf("a", "b")).id

        assertEquals(listOf("a-10", "b-40", "a-50", "a-30", "b-20"), repository.nextEpisodes(id, limit = 10).map { it.id })
    }

    @Test
    fun `an episode midway through is read from its own show's row when both episode tables hold its ID`() = runTest {
        follow("a")
        follow("rss:other", subscribed = false)
        database.localEpisodeCatalogDao().upsertEpisodes(
            listOf(
                LocalEpisodeEntity(
                    episodeId = "-77",
                    podcastId = "a",
                    guid = "own",
                    title = "Own episode",
                    description = "",
                    audioUrl = "https://cdn.example/own.mp3",
                    imageUrl = null,
                    duration = 60,
                    publishedDate = 10L,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeType = null,
                    enclosureType = null,
                ),
            ),
        )
        database.rssEpisodeDao().upsertAll(
            listOf(
                RssEpisodeEntity(
                    episodeId = "-77",
                    podcastId = "rss:other",
                    guid = "other",
                    title = "Another show's episode",
                    description = "",
                    audioUrl = "https://cdn.example/other.mp3",
                    imageUrl = null,
                    duration = 60,
                    publishedDate = 99L,
                    chaptersUrl = null,
                    transcriptUrl = null,
                    transcripts = null,
                    persons = null,
                    seasonNumber = null,
                    episodeNumber = null,
                    episodeType = null,
                    enclosureType = null,
                ),
            ),
        )
        history("-77", "a", progressMs = 60_000L, completed = false)
        val id = repository.createPlaylist("Mix", listOf("a")).id

        val first = repository.nextEpisodes(id, limit = 1).single()
        assertEquals(Triple("-77", "a", "Own episode"), Triple(first.id, first.podcastId, first.title))
        assertEquals("https://cdn.example/own.mp3", first.audioUrl)
    }

    @Test
    fun `reading goes on past full pages of episodes that don't play`() = runTest {
        follow("a")
        episodes("a", *(1L..450L).toList().toLongArray())
        (31L..450L).forEach { history("a-$it", "a", progressMs = 0L, completed = it % 2L == 0L) }
        val queued = (31L..450L).filter { it % 2L == 1L }.map { "a-$it" }
        val id = repository.createPlaylist("Archive", listOf("a")).id

        assertEquals((30L downTo 26L).map { "a-$it" }, repository.nextEpisodes(id, limit = 5, excludeIds = queued).map { it.id })
        repository.setOrder(id, PlaylistOrder.OLDEST)
        assertEquals((1L..3L).map { "a-$it" }, repository.nextEpisodes(id, limit = 3, excludeIds = queued).map { it.id })
    }

    @Test
    fun `an episode finished or queued under another of its IDs doesn't play`() = runTest {
        follow("a")
        episodes("a", 10L, 20L, 30L)
        val finishedElsewhere = ListeningHistoryEntity(
            episodeId = "a-30",
            podcastId = "a",
            episodeTitle = "Episode 30",
            episodeImageUrl = null,
            podcastImageUrl = null,
            episodeAudioUrl = null,
            podcastName = "Show a",
            progressMs = 0L,
            durationMs = 0L,
            isCompleted = true,
            lastPlayedAt = 1L,
        )
        // a-30 was finished as "old-30", and a-20 is queued as "old-20": the listener store projects both onto these IDs.
        val aliases = RoomPlaylistRepository(
            dao = database.playlistDao(),
            projectedHistory = { ids -> if ("a-30" in ids) mapOf("a-30" to finishedElsewhere) else emptyMap() },
            withAliases = { ids -> ids.toSet() + ids.filter { it == "old-20" }.map { "a-20" } },
            newId = { "aliases" },
        )
        val id = aliases.createPlaylist("Mix", listOf("a")).id

        assertEquals(listOf("a-10"), aliases.nextEpisodes(id, limit = 10, excludeIds = listOf("old-20")).map { it.id })
    }

    @Test
    fun `a playlist of more shows than one query binds still plays them all`() = runTest {
        val shows = (1..401).map { "s$it" }
        shows.forEach { follow(it) }
        episodes("s401", 7L)
        episodes("s1", 5L)
        val id = repository.createPlaylist("Everything", shows).id

        assertEquals(listOf("s401-7", "s1-5"), repository.nextEpisodes(id, limit = 10).map { it.id })
    }

    @Test
    fun `unfollowed and hidden shows are skipped and excluded episodes left out`() = runTest {
        follow("a")
        follow("b", subscribed = false)
        follow("c")
        episodes("a", 10L, 20L)
        episodes("b", 30L)
        episodes("c", 40L)
        hidden.value = setOf("c")
        val id = repository.createPlaylist("Mix", listOf("a", "b", "c")).id

        assertEquals(listOf("a-20", "a-10"), repository.nextEpisodes(id, limit = 10).map { it.id })
        assertEquals(listOf("a-10"), repository.nextEpisodes(id, limit = 10, excludeIds = listOf("a-20")).map { it.id })
        assertTrue(repository.nextEpisodes("missing", limit = 10).isEmpty())
        assertTrue(repository.nextEpisodes(id, limit = 0).isEmpty())
    }

    @Test
    fun `importing adds a missing playlist and only adds shows to one the phone has`() = runTest {
        val id = repository.createPlaylist("Mine", listOf("a")).id
        assertEquals(1, repository.importPlaylist(Playlist(id, "Theirs", 1L, 2L, showCount = 0, order = PlaylistOrder.OLDEST), listOf("a", "b")))
        assertEquals(1, repository.importPlaylist(Playlist("new", "New", 1L, 2L, showCount = 0, order = PlaylistOrder.OLDEST), listOf("c")))

        val playlists = repository.getPlaylists().associateBy { it.id }
        assertEquals("Mine" to PlaylistOrder.NEWEST, playlists.getValue(id).let { it.name to it.order })
        assertEquals("New" to PlaylistOrder.OLDEST, playlists.getValue("new").let { it.name to it.order })
        assertEquals(mapOf(id to listOf("a", "b"), "new" to listOf("c")), repository.getShowIds())
    }

    @Test
    fun `deleteAll clears every playlist`() = runTest {
        repository.createPlaylist("One", listOf("a"))
        repository.createPlaylist("Two")
        repository.deleteAll()
        assertTrue(repository.getPlaylists().isEmpty())
    }
}
