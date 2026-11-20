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
import cx.aswin.boxlore.core.model.Playlist
import cx.aswin.boxlore.core.model.PlaylistOrder
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Playlists travel in the JSON library backup (version 11): exported with their order and show IDs
 * and restored on another phone; restoring onto a phone that has the playlist only adds shows, and
 * an older backup without playlists changes nothing.
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
        val comedy = sourceRepository.createPlaylist("Comedy", listOf("200", "100"))
        sourceRepository.setOrder(comedy.id, PlaylistOrder.OLDEST)
        sourceRepository.createPlaylist("Empty")

        val json = manager(source).exportLibraryAsJson()
        val exported = JsonParser.parseString(json).asJsonObject
        assertEquals(11, exported["version"].asInt)
        val playlists = exported["playlists"].asJsonArray.map { it.asJsonObject }
        assertEquals(listOf("Comedy", "Empty"), playlists.map { it["name"].asString })
        assertEquals(listOf("200", "100"), playlists[0]["showIds"].asJsonArray.map { it.asString })
        assertEquals("oldest", playlists[0]["order"].asString)

        manager(target).importLibraryFromJson(json)

        val targetRepository = RoomPlaylistRepository(target.playlistDao())
        val restored = targetRepository.getPlaylists()
        assertEquals(listOf(comedy.id to "Comedy", restored[1].id to "Empty"), restored.map { it.id to it.name })
        assertEquals(PlaylistOrder.OLDEST, restored[0].order)
        assertEquals(mapOf(comedy.id to listOf("200", "100")), targetRepository.getShowIds())

        // Restoring the same backup again adds nothing.
        manager(target).importLibraryFromJson(json)
        assertEquals(mapOf(comedy.id to listOf("200", "100")), targetRepository.getShowIds())
        assertEquals(2, targetRepository.getPlaylists().size)
    }

    @Test
    fun anOlderBackupLeavesPlaylistsAlone() = runTest {
        val targetRepository = RoomPlaylistRepository(target.playlistDao())
        targetRepository.createPlaylist("Mine", listOf("1"))

        manager(target).importLibraryFromJson("""{"version":10,"subscriptions":[],"history":[]}""")

        assertEquals(listOf("Mine"), targetRepository.getPlaylists().map { it.name })
    }

    @Test
    fun restoringOntoAnExistingPlaylistOnlyAddsShows() = runTest {
        val targetRepository = RoomPlaylistRepository(target.playlistDao())
        targetRepository.importPlaylist(Playlist("p1", "Mine", 1L, 1L, showCount = 0), listOf("a"))
        val json = """{"version":11,"subscriptions":[],"history":[],"playlists":[
            {"id":"p1","name":"Theirs","order":"oldest","showIds":["b","a",null,""]},
            {"id":"p2","showIds":["c"]}
        ]}"""

        manager(target).importLibraryFromJson(json)

        val restored = targetRepository.getPlaylists()
        assertEquals(listOf("Mine"), restored.map { it.name })
        assertEquals(PlaylistOrder.NEWEST, restored.single().order)
        assertEquals(mapOf("p1" to listOf("a", "b")), targetRepository.getShowIds())
    }

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
