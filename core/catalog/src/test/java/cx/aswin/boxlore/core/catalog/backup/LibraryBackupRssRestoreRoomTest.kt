package cx.aswin.boxlore.core.catalog.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import cx.aswin.boxlore.core.catalog.PodcastRepository
import cx.aswin.boxlore.core.catalog.SubscriptionRepository
import cx.aswin.boxlore.core.catalog.ports.ListeningHistoryBackupPort
import cx.aswin.boxlore.core.database.BoxLoreDatabase
import cx.aswin.boxlore.core.database.ListeningHistoryEntity
import cx.aswin.boxlore.core.database.ListeningSessionEntity
import cx.aswin.boxlore.core.database.PlayedShowEntity
import cx.aswin.boxlore.core.database.PodcastEntity
import cx.aswin.boxlore.core.domain.ports.DeviceIdentityPort
import cx.aswin.boxlore.core.model.Episode
import cx.aswin.boxlore.core.ranking.AdaptiveRankingRepository
import cx.aswin.boxlore.core.rss.RssPodcastRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockingDetails
import org.mockito.Mockito.`when`
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A full backup restore keeps an `rss:` show, and everything listened to on it, when its feed can't be read. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryBackupRssRestoreRoomTest {
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
    fun anRssShowWhoseFeedCantBeReadKeepsItsFollowHistoryAndListening() = runTest {
        seedSource()
        val json = manager(source, mock(RssPodcastRepository::class.java)).exportLibraryAsJson()
        val unreachable = mock(RssPodcastRepository::class.java)
        `when`(unreachable.restoreSubscription(FEED, SHOW)).thenAnswer { throw IOException("feed timed out") }

        val (count, _) = manager(target, unreachable).importLibraryFromJson(json)

        assertEquals(1, count)
        val show = target.podcastDao().getPodcast(SHOW)!!
        assertTrue(show.isSubscribed)
        assertEquals(PodcastEntity.SOURCE_RSS, show.sourceType)
        assertEquals(FEED, show.feedUrl)
        assertEquals("Members Feed" to "https://example.com/art.jpg", show.title to show.imageUrl)
        assertEquals(1_000L, show.subscribedAt)
        // The restore reads the feed once, never again in its refresh step, and leaves the show due
        // for the normal RSS refresh (no sync time stamped by a second failed read).
        val feedCalls = mockingDetails(unreachable).invocations.map { it.method.name }
        assertEquals(listOf("restoreSubscription"), feedCalls.filter { it == "restoreSubscription" })
        assertFalse(feedCalls.contains("refreshCatalogIfNeeded"))
        assertTrue(show.rssCatalogStale)
        assertEquals(0L, show.lastRssSyncAt)
        assertEquals(40_000L, target.listeningHistoryDao().getHistoryItem(EPISODE)!!.progressMs)
        assertEquals(listOf("mine"), target.listeningSessionDao().getAllSessions().map { it.sessionId })
        assertEquals(listOf(SHOW), target.playedShowDao().getAllPlayedShows().map { it.podcastId })
    }

    private suspend fun seedSource() {
        source.podcastDao().upsert(
            PodcastEntity(
                podcastId = SHOW,
                title = "Members Feed",
                author = "Host",
                imageUrl = "https://example.com/art.jpg",
                description = null,
                isSubscribed = true,
                subscribedAt = 1_000L,
                sourceType = PodcastEntity.SOURCE_RSS,
                feedUrl = FEED,
                rssRefreshCapability = PodcastEntity.RSS_REFRESH_AUTOMATIC,
            ),
        )
        source.listeningHistoryDao().upsert(
            ListeningHistoryEntity(
                episodeId = EPISODE,
                podcastId = SHOW,
                episodeTitle = "Episode",
                episodeImageUrl = null,
                podcastImageUrl = null,
                episodeAudioUrl = "https://cdn.example.com/e.mp3",
                podcastName = "Members Feed",
                progressMs = 40_000L,
                durationMs = 60_000L,
                isCompleted = false,
                lastPlayedAt = 200L,
            ),
        )
        source.listeningSessionDao().upsertSessions(
            listOf(
                ListeningSessionEntity(
                    sessionId = "mine", episodeId = EPISODE, podcastId = SHOW, startedAt = 100L, endedAt = 40_100L,
                    consumedMs = 40_000L, completed = false, localDay = 1L, timeBucket = 1, deviceId = null,
                ),
            ),
        )
        source.playedShowDao().mergePlayedShows(listOf(PlayedShowEntity(SHOW, "Members Feed", null, null, 0L, 200L)), fromCloud = true)
    }

    private fun manager(database: BoxLoreDatabase, rss: RssPodcastRepository) = LibraryBackupManager(
        subscriptionRepository = SubscriptionRepository(database.podcastDao()),
        listeningHistory = RoomHistory(database),
        podcastRepository = mock(PodcastRepository::class.java),
        context = ApplicationProvider.getApplicationContext(),
        adaptiveRankingRepository = mock(AdaptiveRankingRepository::class.java),
        rssPodcastRepository = rss,
        database = database,
        deviceIdentity = DeviceIdentityPort { "phone-a" },
        legacyListeningOrigin = DeviceIdentityPort { "legacy-phone-a" },
    )

    private class RoomHistory(private val database: BoxLoreDatabase) : ListeningHistoryBackupPort {
        override fun getRecentHistory(): Flow<List<ListeningHistoryEntity>> = database.listeningHistoryDao().getRecentHistory()

        override suspend fun exportStoredHistory(): List<ListeningHistoryEntity> = database.listeningHistoryDao().getAllStoredHistory()

        override suspend fun upsertHistoryEntity(entity: ListeningHistoryEntity) = database.listeningHistoryDao().upsert(entity)

        override suspend fun markAllEpisodesCompleted(episodes: List<Episode>, podcastId: String, podcastTitle: String, podcastImageUrl: String?) = Unit
    }

    private companion object {
        const val SHOW = "rss:private"
        const val FEED = "https://example.com/private.xml"
        const val EPISODE = "-111"
    }
}
