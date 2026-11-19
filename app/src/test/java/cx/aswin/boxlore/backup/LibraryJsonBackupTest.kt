package cx.aswin.boxlore.backup

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SharedAppDependencies
import cx.aswin.boxlore.core.catalog.SharedAppDependenciesHolder
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.prefs.UserPreferencesRepository
import cx.aswin.boxlore.core.ranking.AdaptiveRankingBackup
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The automatic backup's file: built from Room without the player, with settings, like Settings' export. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class LibraryJsonBackupTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var database: BoxLoreDatabase
    private var savedHolder: SharedAppDependencies? = null
    private val ranking = mock(AdaptiveRankingRepository::class.java)

    @Before fun setUp() = runBlocking {
        savedHolder = SharedAppDependenciesHolder.instance
        SharedAppDependenciesHolder.instance = null
        database = Room.inMemoryDatabaseBuilder(context, BoxLoreDatabase::class.java).allowMainThreadQueries().build()
        `when`(ranking.exportBackup()).thenReturn(AdaptiveRankingBackup())
        database.podcastDao().upsertRow(
            PodcastEntity(podcastId = "1001", title = "Tech Talk", author = "Host", imageUrl = "https://example.com/art.jpg", description = null, isSubscribed = true),
        )
        listOf(history("ep-old", lastPlayedAt = 1L), history("ep-new", lastPlayedAt = 9L)).forEach { database.listeningHistoryDao().replaceRow(it) }
    }

    @After fun tearDown() {
        database.close()
        SharedAppDependenciesHolder.instance = savedHolder
    }

    @Test fun `the room reader exports every stored history row`() = runBlocking {
        val rows = RoomListeningHistoryReader(database).exportStoredHistory()
        assertEquals(listOf("ep-new", "ep-old"), rows.map { it.episodeId })
    }

    @Test fun `the backup carries subscriptions, every history row and settings`() = runBlocking {
        val prefs = UserPreferencesRepository(context)
        prefs.setRegion("in")
        val manager = LibraryJsonBackup.manager(
            subscriptions = SubscriptionRepository(database.podcastDao()),
            history = RoomListeningHistoryReader(database),
            podcasts = mock(PodcastRepository::class.java),
            userPrefs = prefs,
            context = context,
            ranking = ranking,
            rss = mock(RssPodcastRepository::class.java),
        )
        val json = JSONObject(String(LibraryJsonBackup.bytes(manager), Charsets.UTF_8))
        val subscriptions = json.getJSONArray("subscriptions")
        assertEquals(1, subscriptions.length())
        assertEquals("1001", subscriptions.getJSONObject(0).getString("podcastId"))
        val history = json.getJSONArray("history")
        assertEquals(setOf("ep-new", "ep-old"), (0 until history.length()).map { history.getJSONObject(it).getString("episodeId") }.toSet())
        // A backup without settings would restore a phone with defaults; the shared path always includes them.
        assertEquals("in", json.getJSONObject("globalPreferences").getString("region"))
    }

    @Test fun `the reader never writes history`() {
        val reader = RoomListeningHistoryReader(database)
        val upsert = runCatching { runBlocking { reader.upsertHistoryEntity(history("ep-x", 1L)) } }
        val complete = runCatching { runBlocking { reader.markAllEpisodesCompleted(emptyList(), "1001", "Tech Talk", null) } }
        assertTrue(upsert.exceptionOrNull() is UnsupportedOperationException)
        assertTrue(complete.exceptionOrNull() is UnsupportedOperationException)
    }

    private fun history(id: String, lastPlayedAt: Long) = ListeningHistoryEntity(
        episodeId = id,
        podcastId = "1001",
        episodeTitle = id,
        episodeImageUrl = null,
        podcastImageUrl = null,
        episodeAudioUrl = "https://example.com/$id.mp3",
        podcastName = "Tech Talk",
        progressMs = 0L,
        durationMs = 60_000L,
        isCompleted = false,
        lastPlayedAt = lastPlayedAt,
    )
}
