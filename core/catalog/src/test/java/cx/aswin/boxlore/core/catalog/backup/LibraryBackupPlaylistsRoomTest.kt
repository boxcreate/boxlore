package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.gson.JsonParser
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.RoomPlaylistRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.model.Person
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Playlists travel in the JSON library backup (version 11): exported with their episode copies in
 * order and restored on another phone; restoring onto a phone that has the playlist merges, and an
 * older backup without playlists changes nothing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryBackupPlaylistsRoomTest {
    private lateinit var source: BoxLoreDatabase
    private lateinit var target: BoxLoreDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        source = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        target = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        source.close()
        target.close()
    }

    @Test
    fun playlistsRoundTripThroughTheJsonBackup() = runTest {
        var clock = 1_000L
        val sourceRepository = RoomPlaylistRepository(source.playlistDao(), now = { clock++ })
        val commute = sourceRepository.createPlaylist(
            "Commute",
            listOf(episode("b"), episode("a").copy(persons = listOf(Person(name = "Guest", role = "guest")), episodeNumber = 4)),
        ).playlist
        sourceRepository.createPlaylist("Empty")

        val json = manager(source).exportLibraryAsJson()
        val exported = JsonParser.parseString(json).asJsonObject
        assertEquals(11, exported["version"].asInt)
        val playlists = exported["playlists"].asJsonArray.map { it.asJsonObject }
        assertEquals(listOf("Commute", "Empty"), playlists.map { it["name"].asString })
        assertEquals(listOf("b", "a"), playlists[0]["episodes"].asJsonArray.map { it.asJsonObject["episodeId"].asString })

        manager(target).importLibraryFromJson(json)

        val targetRepository = RoomPlaylistRepository(target.playlistDao())
        val restored = targetRepository.getPlaylists()
        assertEquals(listOf(commute.id to "Commute", restored[1].id to "Empty"), restored.map { it.id to it.name })
        assertEquals(sourceRepository.getEpisodes(commute.id).map { it.episode }, targetRepository.getEpisodes(commute.id).map { it.episode })

        // Restoring the same backup again adds nothing.
        manager(target).importLibraryFromJson(json)
        assertEquals(2, targetRepository.getEpisodes(commute.id).size)
        assertEquals(2, targetRepository.getPlaylists().size)
    }

    @Test
    fun anOlderBackupLeavesPlaylistsAlone() = runTest {
        val targetRepository = RoomPlaylistRepository(target.playlistDao())
        targetRepository.createPlaylist("Mine", listOf(episode("a")))

        manager(target).importLibraryFromJson("""{"version":10,"subscriptions":[],"history":[]}""")

        assertEquals(listOf("Mine"), targetRepository.getPlaylists().map { it.name })
    }

    @Test
    fun unplayableRowsAndNamelessPlaylistsAreSkipped() = runTest {
        val json = """{"version":11,"subscriptions":[],"history":[],"playlists":[
            {"id":"p1","name":"Kept","episodes":[{"episodeId":"x","title":"X","audioUrl":"https://cdn.example/x.mp3"},{"episodeId":"y","title":"No audio"}]},
            {"id":"p2","episodes":[]}
        ]}"""

        manager(target).importLibraryFromJson(json)

        val targetRepository = RoomPlaylistRepository(target.playlistDao())
        assertEquals(listOf("Kept"), targetRepository.getPlaylists().map { it.name })
        assertEquals(listOf("x"), targetRepository.getEpisodes("p1").map { it.episode.id })
        assertTrue(targetRepository.getEpisodes("p2").isEmpty())
    }

    @Test
    fun rowsUnderIdsTheIdentityRestoreRefusedAreSkipped() = runTest {
        val targetRepository = RoomPlaylistRepository(target.playlistDao())
        val backup = listOf(
            PlaylistBackup(
                id = "p1",
                name = "Commute",
                episodes = listOf("a", "taken", "b").map { PlaylistEpisodeBackup(episodeId = it, title = "Episode $it", audioUrl = "https://cdn.example/$it.mp3") },
            ),
        )

        val added = LibraryBackupPlaylists.restore(backup, targetRepository, refusedIds = setOf("taken"))

        assertEquals(2, added)
        assertEquals(listOf("a", "b"), targetRepository.getEpisodes("p1").map { it.episode.id })
    }

    private fun episode(id: String) = Episode(
        id = id,
        title = "Episode $id",
        description = "Notes",
        audioUrl = "https://cdn.example/$id.mp3",
        imageUrl = "https://img.example/$id.jpg",
        podcastImageUrl = "https://img.example/show.jpg",
        podcastTitle = "Show",
        podcastId = "1001",
        duration = 90,
        publishedDate = 7L,
    )

    private fun manager(database: BoxLoreDatabase) = LibraryBackupManager(
        subscriptionRepository = SubscriptionRepository(database.podcastDao()),
        listeningHistory = NoHistory,
        podcastRepository = mock(PodcastRepository::class.java),
        context = ApplicationProvider.getApplicationContext(),
        adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java),
        rssPodcastRepository = mock(RssPodcastRepository::class.java),
        database = database,
        playlistRepository = RoomPlaylistRepository(database.playlistDao()),
    )

    private object NoHistory : ListeningHistoryBackupPort {
        override fun getRecentHistory(): Flow<List<ListeningHistoryEntity>> = emptyFlow()

        override suspend fun exportStoredHistory(): List<ListeningHistoryEntity> = emptyList()

        override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) = Unit

        override suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?) = Unit
    }
}
